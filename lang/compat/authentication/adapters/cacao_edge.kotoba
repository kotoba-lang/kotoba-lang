(ns authentication.adapters.cacao-edge
  "Edge (Cloudflare Workers / browser) CACAO factor verifier.

  Why this exists next to `authentication.adapters.cacao`: that adapter
  bridges to `cacao.core/verify`, whose `:cljs` branch targets **Node**
  (`js/Buffer`, and `ed25519.core`'s synchronous `node:crypto` path). A
  Cloudflare Workers isolate has neither, so the production CACAO factor
  could not actually run at the edge -- the one place kotobase.net and
  every other CACAO-authorized surface in this workspace verifies them.

  `cacao.edge.verify` is the Worker-native verifier (WebCrypto
  `crypto.subtle` Ed25519, zero npm, hand-ported base58btc + definite-length
  CBOR) already shared by cloud-itonami and cloud-manimani. This adapter is
  its third consumer and the first inside the authentication substrate, so
  the CACAO wire format has ONE verify implementation per host family
  instead of one per product Worker.

  The crypto layer answers exactly one question -- \"is this a
  well-formed, unexpired CACAO whose Ed25519 signature checks out against
  its own issuer DID?\". Everything an *authorization* decision additionally
  needs is enforced here, because it is authentication policy, not wire
  format:

    :audience           the CACAO's `aud` must equal this (a CACAO minted
                        for another service must not authenticate here)
    :required-resources every listed capability URI must appear in the
                        CACAO's `resources` (e.g. \"kotoba://can/kotobase:pin\")
    :expect-issuer      the CACAO's `iss` must equal this DID -- the
                        per-DID structural check (ADR-2607177000): a caller
                        proves control of the DID it claims, and there is
                        no separate notion of a \"correct\" target DID to
                        get wrong

  Assurance tops out at `:single-factor` (ADR-2607050400): SIWE/EIP-4361
  signing has no browser/OS-enforced origin binding the way WebAuthn does,
  so a valid CACAO signature alone is never `:phishing-resistant`.

  CLJS-only (js/crypto.subtle, js/Promise)."
  (:require [authentication.model :as m]
            [authentication.ports :as p]
            [cacao.edge.verify :as edge]
            [clojure.string :as str]))

(defn- resources-seq [payload]
  (let [rs (some-> payload (aget "resources"))]
    (if (and rs (pos? (aget rs "length"))) (vec (array-seq rs)) [])))

(defn policy-error
  "The first policy violation for a crypto-valid CACAO, or nil. Split out
  (and public) so a host can reuse the exact same policy check on a CACAO
  it verified through another path, and so the failure reasons are
  directly testable without minting a signature for each case."
  [result {:keys [audience required-resources expect-issuer]}]
  (let [payload (aget result "payload")
        iss (aget result "iss")
        aud (some-> payload (aget "aud"))
        granted (set (resources-seq payload))]
    (cond
      (and expect-issuer (not= expect-issuer iss))
      (str "CACAO issuer does not match the claimed subject")

      (and audience (not= audience aud))
      (str "CACAO audience mismatch")

      :else
      (when-let [missing (seq (remove granted (or required-resources [])))]
        (str "CACAO is missing required capability: " (str/join ", " missing))))))

(defn verify-claims
  "Verify `cacao-b64` and apply `opts`' policy. Returns a Promise of the
  claim map `cacao-factor-verifier` turns into a factor -- never rejects,
  because this always runs against untrusted client input."
  [cacao-b64 opts]
  (if-not (and (string? cacao-b64) (not (str/blank? cacao-b64)))
    (js/Promise.resolve {:ok? false :error "missing CACAO"})
    (-> (if-let [at (:at-sec opts)] (edge/verify cacao-b64 at) (edge/verify cacao-b64))
        (.then (fn [result]
                 (let [iss (aget result "iss")]
                   (if-not (true? (aget result "valid"))
                     {:ok? false :subject iss
                      :error (or (aget result "error") "invalid CACAO signature")}
                     (if-let [policy (policy-error result opts)]
                       {:ok? false :subject iss :error policy}
                       {:ok? true
                        :subject iss
                        :evidence-ref iss
                        :assurance :single-factor
                        :at (some-> result (aget "payload") (aget "exp"))
                        :resources (resources-seq (aget result "payload"))}))))))))

(defn cacao-factor-verifier
  "An `IAsyncFactorVerifier` over `cacao.edge.verify`.

  `opts` may carry :audience / :required-resources / :expect-issuer (see
  the ns docstring) and :at-sec (inject a fixed instant for deterministic
  tests; omitted means the real wall clock, resolved fresh per call so a
  long-lived verifier never validates against a baked-in `now`).

  When `opts` has no :expect-issuer, the factor-request's own `:subject`
  is used as one if present -- so the per-DID check is on by default
  whenever the caller already knows which DID it is authenticating, and a
  host has to explicitly pass `{:expect-issuer nil}` with a subject-less
  request to accept any issuer (the \"who is this?\" discovery case, e.g.
  first sign-in, where the DID is being learned rather than checked)."
  ([] (cacao-factor-verifier {}))
  ([opts]
   (reify p/IAsyncFactorVerifier
     (verify-factor-async! [_ factor-request response]
       (let [subject (:authn.factor-request/subject factor-request)
             opts (cond-> opts
                    (and subject (not (contains? opts :expect-issuer)))
                    (assoc :expect-issuer subject))]
         (-> (verify-claims (:cacao/cacao-b64 response) opts)
             (.then (fn [claims]
                      (m/factor (:authn.factor-request/id factor-request)
                                :cacao
                                (true? (:ok? claims))
                                {:subject (or (:subject claims) subject)
                                 :evidence-ref (:evidence-ref claims)
                                 :assurance (:assurance claims)
                                 :at (:at claims)})))))))))
