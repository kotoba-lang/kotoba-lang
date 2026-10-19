(ns data-integrity.core
  "W3C Data Integrity proofs over Verifiable Credentials and Presentations,
   using the `eddsa-jcs-2022` cryptosuite.

   `org-w3-vc` builds and shape-checks a credential but treats `proof` as an
   opaque pass-through field; this namespace is what actually puts a signature in
   it. Together they are a VC implementation; separately neither is.

     (require '[data-integrity.core :as di])

     (def secured
       (di/issue-credential
         {\"@context\" [\"https://www.w3.org/ns/credentials/v2\"]
          \"type\" [\"VerifiableCredential\"]
          \"issuer\" \"did:key:z6Mk…\"
          \"credentialSubject\" {\"id\" \"did:example:1\"}}
         {:seed seed-bytes
          :verification-method \"did:key:z6Mk…#z6Mk…\"
          :created \"2026-07-30T00:00:00Z\"}))

     (di/verify-credential secured)   ;=> {:verified true :verification-method … }

   Two deliberate departures from the letter of the spec, both toward
   fail-closed, both documented at their call site:

     1. `verify` REQUIRES `:expected-proof-purpose`. The spec makes the check
        conditional (\"if expectedProofPurpose was given\"), which means the
        default behaviour of a spec-literal verifier is to accept a credential
        signed for `authentication` where `assertionMethod` was meant. Pass
        `:any` to opt back into the spec-literal behaviour.
     2. `verify-presentation` REQUIRES `:challenge`. A presentation proof with no
        challenge to bind it is replayable by anyone who observes it once.

   Reference: https://www.w3.org/TR/vc-data-integrity/ §4.2, §4.4
              https://www.w3.org/TR/vc-di-eddsa/ §3.3.1, §3.3.2"
  (:require [clojure.string :as str]
            [data-integrity.bytes :as b]
            [data-integrity.ecdsa :as ecdsa]
            [data-integrity.eddsa :as eddsa]
            [data-integrity.eddsa-rdfc :as eddsa-rdfc]
            [did.core :as did]
            [ed25519.core :as ed]))

(defn- fail! [code msg data]
  (throw (ex-info msg (assoc data :data-integrity/error code))))

;; ── the JSON data model ──────────────────────────────────────────────────────
;; Everything below operates on string keys. A signature is over exact bytes, and
;; keyword/string ambiguity is precisely how signing bugs happen: `{:type …}` and
;; `{"type" …}` canonicalize to the same JSON but answer `(get m "type")`
;; differently, so a guard can read nil from a field that is demonstrably present
;; in the signed bytes. Normalizing once on entry removes the whole class.
(defn- key->name [k]
  (cond
    (string? k) k
    (keyword? k) (if-let [ns' (namespace k)] (str ns' "/" (name k)) (name k))
    :else (fail! :data-integrity/invalid-key
                 "document keys must be strings or keywords" {:key k})))

(defn stringify
  "Deeply convert map keys to their JSON names. Refuses a collapse (`:a` and
   `\"a\"` in one map) rather than silently dropping one member."
  [x]
  (cond
    (map? x)
    (let [pairs (map (fn [[k v]] [(key->name k) (stringify v)]) x)
          names (map first pairs)]
      (when-not (= (count names) (count (set names)))
        (fail! :data-integrity/duplicate-key
               "distinct keys collapse to the same JSON name"
               {:names (->> names frequencies (keep (fn [[n c]] (when (> c 1) n))) vec)}))
      (into {} pairs))
    (vector? x) (mapv stringify x)
    (sequential? x) (mapv stringify x)
    :else x))

;; ── @context handling (§3.3.2 step 4) ────────────────────────────────────────
(defn- context-vec [c]
  (cond (nil? c) nil
        (vector? c) c
        (sequential? c) (vec c)
        :else [c]))

(defn- context-prefix?
  "The secured document's @context must START WITH every value in the proof's
   @context, in the same order. This is what lets a holder append a context
   without invalidating the issuer's signature, while still preventing a verifier
   from being steered by a context the issuer never signed over."
  [doc-ctx proof-ctx]
  (let [d (or (context-vec doc-ctx) [])
        p (or (context-vec proof-ctx) [])]
    (and (>= (count d) (count p))
         (= p (subvec d 0 (count p))))))

;; ── verification method -> public key ────────────────────────────────────────
(defn- vm->controller
  "Strip the fragment: `did:key:z6Mk…#z6Mk…` -> `did:key:z6Mk…`."
  [vm]
  (if-let [i (str/index-of vm "#")] (subs vm 0 i) vm))

(defn default-resolve-key
  "Resolve a `verificationMethod` to a raw 32-byte Ed25519 public key, WITHOUT
   network access.

   `did:key` is self-describing, so it resolves locally and completely. `did:web`
   deliberately does not: resolving it means fetching
   `https://…/did.json`, and a signature-verification library that reaches the
   network on its own gives every caller an unasked-for outbound dependency and
   an SSRF surface driven by attacker-supplied document content. Supply
   `:resolve-key` to verify `did:web` (and see `did.core/did-web-url` for where
   the document lives)."
  [vm]
  (let [controller (vm->controller vm)]
    (cond
      (str/starts-with? controller "did:key:")
      (do
        ;; For did:key the fragment IS the multibase key, so a mismatch means the
        ;; proof is naming a key other than the one its controller resolves to.
        (when-let [i (str/index-of vm "#")]
          (let [frag (subs vm (inc i))
                expected (subs controller (count "did:key:"))]
            (when-not (= frag expected)
              (fail! :data-integrity/verification-method-mismatch
                     "did:key fragment does not match its own multibase key"
                     {:verification-method vm :fragment frag :expected expected}))))
        (b/->ints (ed/did-key->pubkey controller)))

      (str/starts-with? controller "did:web:")
      (fail! :data-integrity/resolver-required
             (str "did:web resolution needs the network; pass :resolve-key. "
                  "The document is expected at " (did/did-web-url controller))
             {:verification-method vm :url (did/did-web-url controller)})

      :else
      (fail! :data-integrity/unsupported-did-method
             "no local resolution for this DID method; pass :resolve-key"
             {:verification-method vm}))))

;; ── §4.2 Add Proof / §3.3.1 Create Proof ─────────────────────────────────────
(defn issue
  "Attach a Data Integrity proof to `unsecured-document`.

   Required options:
     :verification-method  the proof's `verificationMethod` (e.g. `did:key:z…#z…`)
     :proof-purpose        \"assertionMethod\" for a credential,
                           \"authentication\" for a presentation
     :created              XSD dateTime string
   One of:
     :seed                 raw 32-byte Ed25519 seed (default signer), or
     :sign                 (fn [hash-bytes] -> 64 signature bytes) for an HSM/KMS
                           signer, so key material need never enter this library
   Optional:
     :expires :domain :challenge :suite
     :suite-opts           extra input the suite needs. `eddsa-rdfc-2022` requires
                           `{:contexts {url => context}}`, because it canonicalizes
                           RDF and never fetches a remote `@context` — a fetched one
                           lets its host change what a signature covers. Ignored by
                           the JCS suites.

   `:created` is NOT defaulted from a clock. This library takes no ambient
   authority — the same discipline `kotoba-lang/ao` follows by requiring `now-ms`
   from its caller — so the timestamp that ends up inside a signature is always
   one the caller chose and can reproduce."
  [unsecured-document {:keys [suite suite-opts seed sign verification-method
                              proof-purpose created expires domain challenge]
                       :or {suite eddsa/suite}}]
  (let [doc (stringify unsecured-document)]
    (when-not (map? doc)
      (fail! :data-integrity/bad-document "document must be a map" {}))
    (when (contains? doc "proof")
      ;; §3.3.1 signs the UNSECURED document. Silently dropping an existing proof
      ;; would produce a valid-looking signature over different bytes than the
      ;; caller believes; proof sets / `previousProof` chaining is a separate
      ;; algorithm this library does not implement.
      (fail! :data-integrity/already-secured
             "document already has a `proof`; proof sets and previousProof chaining are not implemented"
             {}))
    (when-not (string? verification-method)
      (fail! :data-integrity/missing-verification-method
             ":verification-method is required" {}))
    (when-not (string? proof-purpose)
      (fail! :data-integrity/missing-proof-purpose ":proof-purpose is required" {}))
    (when-not (and (string? created) (eddsa/xsd-datetime? created))
      (fail! :data-integrity/bad-created
             ":created is required and must be an XSD dateTime string"
             {:created created}))
    (when-not (or seed (fn? sign))
      (fail! :data-integrity/no-signer "pass :seed or :sign" {}))

    ;; §3.3.1 step 1: proof is a clone of the options.
    (let [proof (cond-> {"type" (:type suite)
                         "cryptosuite" (:cryptosuite suite)
                         "created" created
                         "verificationMethod" verification-method
                         "proofPurpose" proof-purpose}
                  expires (assoc "expires" expires)
                  domain (assoc "domain" domain)
                  challenge (assoc "challenge" challenge))
          ;; §3.3.1 step 2: copy the document's @context onto the proof.
          proof (if-let [ctx (get doc "@context")]
                  (assoc proof "@context" ctx)
                  proof)
          ;; step 3
          ;; Every suite is called with the SAME arity. A suite that needs input
          ;; the caller must supply -- `eddsa-rdfc-2022` needs pinned `:contexts`
          ;; -- was otherwise unreachable through this function, which is how a
          ;; correct cryptosuite ends up with no way to use it.
          proof-config ((:proof-configuration suite) proof suite-opts)
          ;; step 4 — the document as given, with no proof in it
          transformed ((:transform suite) doc proof suite-opts)
          ;; step 5
          hash-bytes ((:hash suite) transformed proof-config)
          ;; step 6
          sig (if (fn? sign)
                (b/ints->bytes (b/->ints (sign hash-bytes)))
                ((:sign suite) seed hash-bytes))
          ;; step 7
          proof (assoc proof "proofValue" ((:encode-proof-value suite) sig))]
      ;; §4.2 steps 2-4: the proof must carry type/verificationMethod/proofPurpose,
      ;; and any domain/challenge asked for must be the one that got signed.
      (doseq [k ["type" "verificationMethod" "proofPurpose"]]
        (when-not (get proof k)
          (fail! :data-integrity/incomplete-proof (str "proof is missing " k) {})))
      (when (and (some? domain) (not= domain (get proof "domain")))
        (fail! :data-integrity/domain-mismatch "options domain != proof domain" {}))
      (when (and (some? challenge) (not= challenge (get proof "challenge")))
        (fail! :data-integrity/challenge-mismatch "options challenge != proof challenge" {}))
      ;; §4.2 steps 5-7
      (assoc doc "proof" proof))))

;; ── §4.4 Verify Proof / §3.3.2 ───────────────────────────────────────────────
(defn- same-strings?
  "§4.4 step 6 compares `domain` by whether it \"contains the same strings\",
   which is set equality over a value that may be a single string or a list."
  [a b]
  (= (set (or (context-vec a) [])) (set (or (context-vec b) []))))

(def built-in-suites
  "The suites this library ships, keyed by the `cryptosuite` string a proof declares.

   Provided so a caller can build an `:accept-suites` allowlist by name instead of
   importing each namespace, NOT as a default. See `verify`: there is deliberately
   no mode that accepts whatever a proof names."
  {(:cryptosuite eddsa/suite) eddsa/suite
   (:cryptosuite ecdsa/suite) ecdsa/suite
   (:cryptosuite eddsa-rdfc/suite) eddsa-rdfc/suite})

(defn verify
  "Verify the Data Integrity proof on `secured-document`.

   Returns `{:verified true :verification-method … :proof-purpose … :document …}`
   where `:document` is the unsecured document the signature actually covers, or
   `{:verified false :reason <keyword> …}`.

   A forged or mismatched proof is a `false`, not an exception — only a
   MALFORMED input throws. Callers must therefore branch on `:verified` and never
   treat \"did not throw\" as success.

   Options:
     :expected-proof-purpose  REQUIRED. \"assertionMethod\", \"authentication\",
                              or `:any` for the spec-literal unchecked behaviour.
     :challenge :domain       enforced when given
     :resolve-key             (fn [verification-method] -> 32 public key bytes),
                              required for any DID method other than did:key
     :suite                   verify against exactly this suite; a proof naming a
                              different `cryptosuite` is MALFORMED and throws
     :accept-suites           an ALLOWLIST — a map of cryptosuite-name -> suite, or
                              a collection of suites — from which the suite named by
                              the proof is selected. A proof naming anything else is
                              `{:verified false :reason :unacceptable-cryptosuite}`.

   With neither option the default is `eddsa-jcs-2022`, unchanged — that default
   predates this dispatch and removing it would break every existing caller. A
   compatibility default is a different thing from letting a proof pick its own
   verifier.

   ## Why `:accept-suites` is an allowlist and not \"whatever the proof says\"

   `cryptosuite` is a field of the proof, so it is chosen by whoever produced the
   document — including an attacker. Selecting a suite from it unconditionally would
   let them pick the weakest one a verifier happens to support, which is a downgrade
   attack with no signature forgery required. So dispatch happens only against an
   explicit allowlist, and the empty allowlist accepts nothing.

   The allowlist also makes migration possible at all. `:suite` alone cannot verify a
   corpus containing more than one cryptosuite — a mixed corpus is exactly what any
   change of suite produces — because a mismatch throws rather than being skipped.

   `:suite-opts` applies to whichever suite is selected, so a rdfc suite still needs
   its pinned `:contexts`."
  [secured-document {:keys [suite suite-opts accept-suites expected-proof-purpose
                            challenge domain resolve-key]
                     :or {resolve-key default-resolve-key}}]
  (let [doc (stringify secured-document)]
    (when-not (map? doc)
      (fail! :data-integrity/bad-document "document must be a map" {}))
    (when (nil? expected-proof-purpose)
      (fail! :data-integrity/expected-proof-purpose-required
             (str ":expected-proof-purpose is required. Omitting it would accept a "
                  "credential signed for `authentication` where `assertionMethod` "
                  "was meant. Pass :any for the spec-literal unchecked behaviour.")
             {}))
    (let [proof (get doc "proof")]
      (when-not (map? proof)
        (fail! :data-integrity/missing-proof "document has no `proof` map" {}))
      (when (sequential? proof)
        (fail! :data-integrity/proof-set-unsupported
               "proof sets/chains are not implemented" {}))
      ;; §3.3.2 steps 1-2
      (let [unsecured (dissoc doc "proof")
            proof-options (dissoc proof "proofValue")
            ;; Resolve which suite verifies THIS proof. With :suite the caller has
            ;; named one and a mismatch is malformed (the suite itself raises). With
            ;; :accept-suites the proof selects from an allowlist, and anything
            ;; outside it is a verification failure rather than a crash -- one
            ;; unknown suite in a batch must not stop the batch.
            allow (cond
                    (map? accept-suites) accept-suites
                    (coll? accept-suites) (into {} (map (juxt :cryptosuite identity))
                                               accept-suites)
                    :else nil)
            named (get proof "cryptosuite")
            ;; :accept-suites is opt-in. Without it the long-standing default
            ;; applies unchanged, because removing it would break every existing
            ;; caller -- and a compatibility default is a different thing from
            ;; letting a proof pick its own verifier.
            suite (cond
                    suite suite
                    allow (get allow named)
                    :else eddsa/suite)]
        ;; No suite: either the allowlist excludes what the proof names, or the
        ;; caller passed neither :suite nor :accept-suites.
        (if (nil? suite)
          {:verified false
           :reason :data-integrity/unacceptable-cryptosuite
           :cryptosuite named
           :accepted (vec (sort (keys (or allow {}))))}
        ;; §4.4 step 4
        (if-let [missing (some (fn [k] (when-not (get proof k) k))
                               ["type" "verificationMethod" "proofPurpose"])]
          {:verified false :reason :data-integrity/incomplete-proof :missing missing}
          (let [vm (get proof "verificationMethod")
                purpose (get proof "proofPurpose")]
            (cond
              ;; §4.4 step 5
              (and (not= :any expected-proof-purpose)
                   (not= expected-proof-purpose purpose))
              {:verified false :reason :data-integrity/proof-purpose-mismatch
               :expected expected-proof-purpose :actual purpose}

              ;; §4.4 step 6
              (and (some? domain) (not (same-strings? domain (get proof "domain"))))
              {:verified false :reason :data-integrity/domain-mismatch
               :expected domain :actual (get proof "domain")}

              ;; §4.4 step 7
              (and (some? challenge) (not= challenge (get proof "challenge")))
              {:verified false :reason :data-integrity/challenge-mismatch
               :expected challenge :actual (get proof "challenge")}

              ;; §3.3.2 step 4 — the proof's @context must be a prefix of the
              ;; document's, or a holder could prepend a context the issuer never
              ;; signed over and change how the document is interpreted.
              (and (contains? proof-options "@context")
                   (not (context-prefix? (get doc "@context")
                                         (get proof-options "@context"))))
              {:verified false :reason :data-integrity/context-not-prefix
               :document-context (get doc "@context")
               :proof-context (get proof-options "@context")}

              :else
              (let [;; §3.3.2 step 4 (cont.) — verify against the context the
                    ;; proof was made over, not any longer one now present.
                    unsecured (if (contains? proof-options "@context")
                                (assoc unsecured "@context" (get proof-options "@context"))
                                unsecured)
                    sig ((:decode-proof-value suite) (get proof "proofValue"))
                    transformed ((:transform suite) unsecured proof-options suite-opts)
                    proof-config ((:proof-configuration suite) proof-options suite-opts)
                    hash-bytes ((:hash suite) transformed proof-config)
                    pub (resolve-key vm)
                    ok ((:verify suite) pub hash-bytes sig)]
                (if ok
                  {:verified true
                   :verification-method vm
                   :proof-purpose purpose
                   :document unsecured}
                  {:verified false :reason :data-integrity/bad-signature
                   :verification-method vm}))))))))))

;; ── credential / presentation wrappers ───────────────────────────────────────
;; The proofPurpose distinction is not decoration: `assertionMethod` means "the
;; issuer asserts this is true", `authentication` means "the holder is present
;; right now". Mixing them lets a captured credential stand in for a live login.
(defn issue-credential
  "`issue` with proofPurpose `assertionMethod`."
  [credential opts]
  (issue credential (assoc opts :proof-purpose "assertionMethod")))

(defn verify-credential
  "`verify` expecting proofPurpose `assertionMethod`."
  ([credential] (verify-credential credential {}))
  ([credential opts]
   (verify credential (assoc opts :expected-proof-purpose "assertionMethod"))))

(defn issue-presentation
  "`issue` with proofPurpose `authentication`. `:challenge` is required: a
   presentation proof with nothing binding it to a request is replayable by
   anyone who observes it once."
  [presentation {:keys [challenge] :as opts}]
  (when-not (string? challenge)
    (fail! :data-integrity/challenge-required
           ":challenge is required for a presentation proof, otherwise the proof is replayable"
           {}))
  (issue presentation (assoc opts :proof-purpose "authentication")))

(defn verify-presentation
  "`verify` expecting proofPurpose `authentication`. `:challenge` is required —
   the verifier must check the presentation answers the challenge IT issued."
  [presentation {:keys [challenge] :as opts}]
  (when-not (string? challenge)
    (fail! :data-integrity/challenge-required
           ":challenge is required to verify a presentation, otherwise a captured proof replays"
           {}))
  (verify presentation (assoc opts :expected-proof-purpose "authentication")))
