(ns webauthn.adapters.edge
  "WebAuthn Level 2 ceremony verification for the edge — the real
  registration/assertion crypto, on WebCrypto only (`crypto.subtle`), with no
  npm dependency and no platform binding.

  ## What this is, and what it deliberately is not

  It is a pure function of its inputs plus WebCrypto: give it the byte blobs a
  browser's `navigator.credentials` produced and the facts you already know
  (the challenge you issued, your RP id and origin, and — for a login — the
  public key you stored at registration), and it tells you whether that
  ceremony really happened.

  It does NOT touch storage, key custody, sessions or CACAO. That separation is
  the point: the prior art this is ported from
  (`cloud-itonami.edge.webauthn`, in production since 2026-07) fused ceremony
  verification, KV schema, an AES-GCM key-wrapping scheme and CACAO minting
  into one namespace, which made all four untestable without Cloudflare
  bindings and unreusable by the second consumer. Here the caller owns
  challenge storage (it must be single-use — see below), credential storage,
  and whatever it decides to do with a verified ceremony.

  ## The checks, and why each one is not optional

  Registration (`verify-registration!`):
    - `clientData.type` is `webauthn.create`, `origin` matches exactly, and
      `challenge` equals the one the caller issued.
    - `authData` carries attested credential data, the user-present flag, and
      the user-VERIFIED flag (see below).
    - `authData`'s `rpIdHash` equals SHA-256(rp-id) — otherwise a credential
      created for another RP could be presented here.
    - the credential public key is COSE EC2 / P-256, returned as a raw
      65-byte uncompressed point for the caller to store.

  Authentication (`verify-authentication!`):
    - the same clientData checks, with type `webauthn.get`.
    - `authData` is PARSED and its user-present, user-verified and `rpIdHash`
      fields checked. Verifying only the signature would accept an assertion
      with UP=0 (no human touched the authenticator) or one made for a
      different RP; the signature is over those bytes but says nothing about
      their contents.

  ## User verification is required unless the caller says otherwise

  `config` takes `:user-verification`, and the default is `:required`.

  UP and UV are different facts. UP=1 says an authenticator was touched. UV=1
  says the human was VERIFIED — a PIN, a biometric, a device unlock. An
  assertion with UP=1 and UV=0 is what a stolen always-on security key
  produces, and its signature verifies perfectly. Returning `:user-verified?`
  for the caller to check, which is what this namespace used to do, is the
  shape of check that gets forgotten exactly once and then never noticed,
  because everything keeps working.

  So the default is fail-closed: `:required` refuses UV=0, and anything other
  than the explicit value `:preferred` is treated as `:required`. A misspelt
  policy therefore over-refuses rather than silently disabling the check —
  the failure mode a policy key exists to prevent. A caller that genuinely
  needs a lower-assurance lane must ask for it in writing, and should keep it
  separate from sessions that can reach payments, credential management or
  administration.
    - the ECDSA P-256 signature over `authData || SHA-256(clientDataJSON)`
      verifies against the stored public key. WebAuthn signatures are
      DER-encoded and `crypto.subtle` wants raw IEEE-P1363, so `webauthn.der`
      converts.
    - the returned `:sign-count` lets the caller apply WebAuthn L2 §7.2 step
      19 (clone detection) against its own stored baseline — a decision that
      needs persistence, so it belongs to the caller, not here.

  Everything is bounds-checked before it is read. At login this is entirely
  attacker-controlled input with no independent signature over the parse
  itself, and `aget`/`.slice` past the end silently yield `undefined`/a
  clamped range rather than throwing.

  ## Single-use challenges are the caller's job, and they are not optional

  A challenge that can be replayed is a replayable sign-in. This namespace
  compares the presented challenge to the one it was handed; making sure that
  value can only be handed over once is a storage property (a Durable Object,
  a transaction, a compare-and-delete), which is exactly why it is not
  implemented here.

  ## Malformed input is a client error, never a server error

  Every parse runs inside a guard that resolves to `{:ok false :status 400}`.
  Ported deliberately: the origin implementation had three separate
  regressions where a bad base64url length or a malformed DER signature threw
  synchronously out of a `.then` and got misclassified as an internal 500.

  CLJS-only (js/crypto.subtle, js/Uint8Array, js/TextDecoder)."
  (:require [cacao.edge.cbor :as cbor]
            [webauthn.der :as der]))

;; `cacao.edge.cbor` rather than a fifth copy of a CBOR decoder in this
;; workspace: it is the shared edge implementation (definite-length RFC 8949
;; subset, js/Uint8Array), already used by cacao.edge.verify, cloud-itonami and
;; cloud-manimani. The namespace lives in the cacao repo for historical reasons
;; -- it is not CACAO-specific, and attestationObject/COSE_Key need exactly the
;; same restricted profile.

;; ── bytes ───────────────────────────────────────────────────────────────────

(defn- b64->bytes [b64]
  (let [bin (js/atob b64)
        n (aget bin "length")
        out (js/Uint8Array. n)]
    (dotimes [i n] (aset out i (.charCodeAt bin i)))
    out))

(defn b64url->bytes
  "base64url (WebAuthn's wire encoding) -> bytes. Throws on an impossible
  length rather than silently decoding garbage."
  [s]
  (let [pad (case (mod (count s) 4)
              0 "" 2 "==" 3 "="
              (throw (js/Error. "bad base64url length")))]
    (b64->bytes (-> s
                    (.replace (js/RegExp. "-" "g") "+")
                    (.replace (js/RegExp. "_" "g") "/")
                    (str pad)))))

(defn bytes->b64
  "Standard base64 — the form a caller stores a public key in."
  [bytes]
  (js/btoa (apply str (map js/String.fromCharCode (array-seq (js/Array.from bytes))))))

(defn- sha256 [bytes]
  (.then (js/crypto.subtle.digest "SHA-256" bytes) #(js/Uint8Array. %)))

(defn- bytes=
  "Value equality. Plain JS typed arrays fall back to reference identity under
  cljs `=`, so this must compare as Clojure vectors."
  [a b]
  (= (vec (array-seq (js/Array.from a))) (vec (array-seq (js/Array.from b)))))

(defn- concat-bytes [a b]
  (let [out (js/Uint8Array. (+ (aget a "length") (aget b "length")))]
    (.set out a 0)
    (.set out b (aget a "length"))
    out))

(defn- err [status message] {:ok false :status status :error message})

;; ── clientDataJSON ──────────────────────────────────────────────────────────

(defn- check-client-data
  [client-data-bytes expected-type expected-origin expected-challenge]
  (try
    (let [parsed (js/JSON.parse (.decode (js/TextDecoder. "utf-8") client-data-bytes))]
      (cond
        (not= (aget parsed "type") expected-type)
        (err 400 (str "clientData.type must be " expected-type))

        (not= (aget parsed "origin") expected-origin)
        (err 400 "clientData.origin mismatch")

        ;; Compared here rather than merely returned, so a caller cannot forget
        ;; to. The caller still owns making the value single-use.
        (not= (aget parsed "challenge") expected-challenge)
        (err 400 "clientData.challenge mismatch")

        :else {:ok true}))
    (catch :default _ (err 400 "malformed clientDataJSON"))))

;; ── authenticatorData (WebAuthn L2 §6.1) ────────────────────────────────────
;; rpIdHash(32) | flags(1) | signCount(4) | [if AT: aaguid(16) credIdLen(2)
;; credId(credIdLen) credentialPublicKey(COSE_Key CBOR, to end-of-buffer)]

(defn- read-uint32-be
  "`unsigned-bit-shift-right … 0` coerces the result out of JS bitwise ops'
  32-bit SIGNED semantics; a high bit in the top byte would otherwise read
  negative."
  [bytes offset]
  (unsigned-bit-shift-right
   (bit-or (bit-shift-left (aget bytes offset) 24)
           (bit-shift-left (aget bytes (+ offset 1)) 16)
           (bit-shift-left (aget bytes (+ offset 2)) 8)
           (aget bytes (+ offset 3)))
   0))

(defn user-verification-refusal
  "The refusal an unverified ceremony earns, or nil.

  `:preferred` — and ONLY the exact value `:preferred` — opts out. A typo, a
  string instead of a keyword, a nil that came from a missing config key: all
  of those land on `:required`, because the alternative is a policy that
  disables itself when it is written down wrong."
  [{:keys [user-verification]} parsed status]
  (when (and (not= :preferred user-verification)
             (not (:user-verified? parsed)))
    (err status "user-verification flag not set")))

(defn parse-authenticator-data
  "Public so a caller can inspect an assertion it verified. Every read is
  bounds-checked against the declared and available lengths before it is
  taken."
  [bytes]
  (when (< (aget bytes "length") 37)
    (throw (js/Error. "authenticatorData too short (need rpIdHash+flags+signCount, 37 bytes)")))
  (let [flags (aget bytes 32)
        base {:rp-id-hash (.slice bytes 0 32)
              :user-present? (not (zero? (bit-and flags 0x01)))
              :user-verified? (not (zero? (bit-and flags 0x04)))
              ;; Backup Eligibility and Backup State (WebAuthn L3 §6.1). BE=0
              ;; means this credential can never leave the authenticator it was
              ;; born on, so an account holding only such credentials is one
              ;; lost device away from unreachable. Reported, never judged
              ;; here: whether that is acceptable is a policy the caller owns,
              ;; and refusing BE=0 would refuse hardware security keys, which
              ;; are the strongest thing a user can hold.
              :backup-eligible? (not (zero? (bit-and flags 0x08)))
              :backed-up? (not (zero? (bit-and flags 0x10)))
              :sign-count (read-uint32-be bytes 33)}]
    (if (zero? (bit-and flags 0x40))
      (assoc base :has-attested-cred? false)
      (do
        (when (< (aget bytes "length") 55)
          (throw (js/Error. "authenticatorData declares attested credential data but is too short for aaguid+credIdLen")))
        (let [cred-id-len (bit-or (bit-shift-left (aget bytes 53) 8) (aget bytes 54))
              start 55]
          (when (< (aget bytes "length") (+ start cred-id-len))
            (throw (js/Error. "authenticatorData too short for declared credentialId length")))
          (assoc base
                 :has-attested-cred? true
                 :aaguid (.slice bytes 37 53)
                 :credential-id (.slice bytes start (+ start cred-id-len))
                 :cose-key-bytes (.slice bytes (+ start cred-id-len) (aget bytes "length"))))))))

(defn- cose-ec2->uncompressed-pubkey
  "COSE_Key (RFC 9053) EC2/P-256 -> the raw 65-byte uncompressed point
  `crypto.subtle.importKey \"raw\"` takes. COSE integer map keys decode as
  string-keyed props: kty=\"1\" (2=EC2), crv=\"-1\" (1=P-256), x=\"-2\",
  y=\"-3\"."
  [cose-map]
  (when (not= (aget cose-map "1") 2)
    (throw (js/Error. "credentialPublicKey is not COSE kty=EC2")))
  (when (not= (aget cose-map "-1") 1)
    (throw (js/Error. "credentialPublicKey is not COSE crv=P-256")))
  (let [x (aget cose-map "-2")
        y (aget cose-map "-3")]
    (when (or (nil? x) (nil? y)
              (not= 32 (aget x "length")) (not= 32 (aget y "length")))
      (throw (js/Error. "credentialPublicKey x/y must each be 32 bytes")))
    (let [out (js/Uint8Array. 65)]
      (aset out 0 0x04)
      (.set out x 1)
      (.set out y 33)
      out)))

;; ── the two ceremonies ──────────────────────────────────────────────────────

(defn verify-registration!
  "Verify a `navigator.credentials.create()` result.

  `config`  {:rp-id :origin}
  `payload` {:client-data-json-b64url :attestation-object-b64url :challenge}
            where `:challenge` is the base64url challenge the caller issued
            and has already ensured is single-use.

  Promise of
    {:ok true :credential-id <b64url> :public-key-b64 <base64 65-byte P-256>
     :sign-count n :user-verified? bool :aaguid-b64 <base64>}
  or {:ok false :status n :error s}. Never rejects."
  [{:keys [rp-id origin] :as config} {:keys [client-data-json-b64url attestation-object-b64url challenge]}]
  (-> (js/Promise.resolve nil)
      (.then
       (fn []
         (let [cd (check-client-data (b64url->bytes client-data-json-b64url)
                                     "webauthn.create" origin challenge)]
           (if-not (:ok cd)
             cd
             (let [att (cbor/decode (b64url->bytes attestation-object-b64url))
                   parsed (parse-authenticator-data (aget att "authData"))
                   ;; Refused at ENROLMENT as well as at login, because a
                   ;; credential registered without user verification can never
                   ;; produce a verified assertion afterwards. Admitting one
                   ;; mints an account that enrols cleanly and can then never
                   ;; sign in.
                   uv-refusal (user-verification-refusal config parsed 400)]
               (cond
                 (not (:has-attested-cred? parsed))
                 (err 400 "attestationObject has no attested credential data")

                 (not (:user-present? parsed))
                 (err 400 "user-present flag not set")

                 uv-refusal uv-refusal

                 :else
                 (-> (sha256 (.encode (js/TextEncoder.) rp-id))
                     (.then (fn [rp-hash]
                              (if-not (bytes= rp-hash (:rp-id-hash parsed))
                                (err 400 "authData rpIdHash mismatch")
                                {:ok true
                                 :credential-id (-> (bytes->b64 (:credential-id parsed))
                                                    (.replace (js/RegExp. "\\+" "g") "-")
                                                    (.replace (js/RegExp. "/" "g") "_")
                                                    (.replace (js/RegExp. "=+$" "") ""))
                                 :public-key-b64 (bytes->b64 (cose-ec2->uncompressed-pubkey
                                                              (cbor/decode (:cose-key-bytes parsed))))
                                 :aaguid-b64 (bytes->b64 (:aaguid parsed))
                                 :sign-count (:sign-count parsed)
                                 :user-verified? (:user-verified? parsed)
                                 :backup-eligible? (:backup-eligible? parsed)
                                 :backed-up? (:backed-up? parsed)}))))))))))
      (.catch (fn [e] (err 400 (str "malformed registration ceremony: " (aget e "message")))))))

(defn verify-authentication!
  "Verify a `navigator.credentials.get()` result against a stored credential.

  `config`  {:rp-id :origin :user-verification}
  `payload` {:client-data-json-b64url :authenticator-data-b64url
             :signature-b64url :challenge :public-key-b64}

  Promise of {:ok true :sign-count n :user-verified? bool :backup-eligible?
  bool :backed-up? bool} or {:ok false :status n :error s}. Never rejects.

  `:sign-count` is returned, not judged: clone detection (L2 §7.2 step 19)
  compares it against a stored baseline and then persists the new value, which
  only the caller can do. The backup flags are likewise returned rather than
  judged, but they must be re-read on EVERY login and not only at enrolment:
  backup state moves when a user changes provider or turns sync off, so a
  value written once describes a recovery posture the account may no longer
  have."
  [{:keys [rp-id origin] :as config}
   {:keys [client-data-json-b64url authenticator-data-b64url signature-b64url
           challenge public-key-b64]}]
  (-> (js/Promise.resolve nil)
      (.then
       (fn []
         (let [client-data-bytes (b64url->bytes client-data-json-b64url)
               cd (check-client-data client-data-bytes "webauthn.get" origin challenge)]
           (if-not (:ok cd)
             (assoc cd :status 401)
             (let [auth-data-bytes (b64url->bytes authenticator-data-b64url)
                   parsed (parse-authenticator-data auth-data-bytes)
                   raw-sig (der/raw (b64url->bytes signature-b64url))
                   pubkey-bytes (b64->bytes public-key-b64)
                   uv-refusal (user-verification-refusal config parsed 401)]
               (cond
                 (not (:user-present? parsed)) (err 401 "user-present flag not set")
                 uv-refusal uv-refusal
                 :else
                 (-> (sha256 (.encode (js/TextEncoder.) rp-id))
                     (.then (fn [rp-hash]
                              (when-not (bytes= rp-hash (:rp-id-hash parsed))
                                (throw (ex-info "rp" {:authn/deny "authData rpIdHash mismatch"})))
                              (sha256 client-data-bytes)))
                     (.then (fn [cd-hash]
                              (-> (js/crypto.subtle.importKey
                                   "raw" pubkey-bytes
                                   #js {:name "ECDSA" :namedCurve "P-256"} false #js ["verify"])
                                  (.then (fn [key]
                                           (js/crypto.subtle.verify
                                            #js {:name "ECDSA" :hash "SHA-256"} key raw-sig
                                            (concat-bytes auth-data-bytes cd-hash)))))))
                     (.then (fn [sig-ok?]
                              (if-not sig-ok?
                                (err 401 "signature verification failed")
                                {:ok true
                                 :sign-count (:sign-count parsed)
                                 :user-verified? (:user-verified? parsed)
                                 :backup-eligible? (:backup-eligible? parsed)
                                 :backed-up? (:backed-up? parsed)}))))))))))
      (.catch (fn [e]
                (if-let [deny (:authn/deny (ex-data e))]
                  (err 401 deny)
                  (err 400 (str "malformed authentication ceremony: " (aget e "message"))))))))

(defn sign-count-ok?
  "WebAuthn L2 §7.2 step 19, as a pure predicate the caller applies against its
  own stored baseline.

  When BOTH counters are zero the authenticator does not implement a counter
  (common for platform authenticators) and the check is skipped, as every
  mainstream implementation does. Otherwise the count must strictly increase;
  a count that did not is a cloned-authenticator signal."
  [stored-count new-count]
  (let [stored (or stored-count 0)]
    (or (and (zero? stored) (zero? new-count))
        (> new-count stored))))
