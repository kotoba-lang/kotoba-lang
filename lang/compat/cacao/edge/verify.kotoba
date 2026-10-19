;; EXTRACTED (2026-07-15) verbatim from cloud-itonami.edge.{base58,cbor,cacao}
;; — the 2nd consumer (cloud-manimani per-user CACAO auth, ADR-2607141753 M2)
;; appeared, triggering the extraction rule (ADR-2607141654: extract on 2nd
;; consumer, not before). cloud-itonami's own copies remain canonical-in-use
;; until it migrates here (follow-up noted in ADR-2607141753 addendum 6).
(ns cacao.edge.verify
  "CAIP-122 / SIWE (EIP-4361) CACAO verify for the edge — CLJS port of
  cacao.core/verify (orgs/kotoba-lang/cacao). Verify-only: this Function
  never mints a CACAO, only checks one a client presents. Ed25519 signature
  verification runs on the platform's native Web Crypto (js/crypto.subtle)
  rather than a hand-rolled curve implementation — the only ported pieces
  are the wire format (base58btc, definite-length CBOR, SIWE
  reconstruction), which have no Web Crypto equivalent. CLJS-only.

  Crypto-valid does not mean valid forever: a signature never expires on
  its own, so temporal-error (mirroring kotobase.cacao.cljc's
  validate-cacao — same tolerances: 300s future-iat skew, exp enforced when
  present, 7-day max-age fallback when absent, and cloud-itonami.auth's JVM
  mirror of the same logic) is applied after crypto verify succeeds. A
  captured-and-replayed CACAO past its window is rejected here even though
  the signature itself still checks out."
  (:require [kotoba.lang.text :as str]
            [cacao.edge.base58 :as base58]
            [cacao.edge.cbor :as cbor]))

(def future-skew-sec
  "How far into the future :iat may be before it's rejected (clock skew tolerance)."
  300)

(def max-age-sec
  "Fallback session lifetime when a CACAO carries no :exp."
  (* 7 24 60 60))

(defn- parse-utc-seconds
  "Strict `YYYY-MM-DDTHH:MM:SSZ` -> unix seconds, or nil. Round-trips through
  toISOString to reject anything Date.parse accepts more loosely than the
  CACAO wire format allows (matches kotobase.cacao.cljc's parse-utc-seconds)."
  [s]
  (when (and s (re-matches #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$" s))
    (let [t (js/Date.parse s)]
      (when-not (js/isNaN t)
        (when (= (str/replace (.toISOString (js/Date. t)) ".000Z" "Z") s)
          (js/Math.floor (/ t 1000)))))))

(defn- resources-shape-error
  "nil, or an error string, for `resources` (a JS array or nil/undefined).
  siwe-message renders each element as its own \"- <r>\" line, joined by
  \\n with every other line -- the Ed25519 signature only authenticates
  the FINAL joined text, not the array's element boundaries, so a resource
  string containing an embedded \"\\n- \" sequence reconstructs to
  byte-identical signed bytes as if it had been split into two separate
  array elements at that point. A tampered CACAO envelope (same real
  signature, `p.resources` re-encoded with two legitimate elements merged
  into one containing that embedded sequence) would otherwise pass
  signature verification with a `payload.resources` shape that was never
  actually, uniquely, signed. No legitimate resource URI
  (kotoba://...) ever needs an embedded newline, so rejecting any is
  purely a tightening, not a compatibility break."
  [resources]
  (when (and resources (pos? (aget resources "length")))
    (when (some #(str/includes? % "\n") (array-seq resources))
      "CACAO resources must not contain embedded newlines")))

(defn- temporal-error [payload now-sec]
  (let [iat (aget payload "iat")
        exp (aget payload "exp")
        iat-sec (parse-utc-seconds iat)
        exp-sec (parse-utc-seconds exp)]
    (cond
      (nil? iat-sec) "invalid CACAO iat"
      (> (- iat-sec now-sec) future-skew-sec) "CACAO iat is too far in the future"
      (and exp (nil? exp-sec)) "invalid CACAO exp"
      (and exp-sec (> now-sec exp-sec)) "expired CACAO"
      (and (not exp) (> (- now-sec iat-sec) max-age-sec)) "CACAO max age exceeded"
      :else nil)))

(defn now-sec [] (js/Math.floor (/ (js/Date.now) 1000)))

(defn replay-until-sec
  "The last unix second at which this CACAO can still be presented, so a
  replay-protection store knows how long it must remember the nonce.

  A nonce row evicted while the CACAO is still inside its temporal window
  turns replay protection OFF for the rest of that window: the same captured
  envelope verifies again and claims a fresh row. So this is derived from the
  same two rules `temporal-error` enforces -- `exp` when present, otherwise
  `iat` + `max-age-sec` -- and NOT from a TTL the store picks for itself. A
  store that keeps nonces for its own convenient interval (10 minutes, say)
  while accepting a 7-day no-exp CACAO has a replay hole exactly that CACAO's
  remaining lifetime wide.

  nil when `iat` is unparseable; `verify` rejects those before this matters."
  [payload]
  (let [iat-sec (parse-utc-seconds (aget payload "iat"))
        exp-sec (parse-utc-seconds (aget payload "exp"))]
    (cond
      exp-sec exp-sec
      iat-sec (+ iat-sec max-age-sec)
      :else nil)))

(defn base64->bytes
  "Public (not `-`) so cloud-itonami.edge.webauthn can decode its
  standard-base64 KV-stored fields (pubKeyB64/wrappedPrivB64/ivB64, all
  produced by cacao-mint/bytes->base64) with the same decoder this file
  uses for a CACAO's own base64 wire encoding."
  [b64]
  (let [bin (js/atob b64)
        n (aget bin "length")
        out (js/Uint8Array. n)]
    (dotimes [i n]
      (aset out i (.charCodeAt bin i)))
    out))

;; did:key:z6Mk... (Ed25519, multicodec 0xed01) -> raw 32-byte public key.
;; Mirrors ed25519.core/did-key->pubkey.
(defn- did-key->pubkey [did]
  (when-not (and (string? did) (str/starts-with? did "did:key:z"))
    (throw (js/Error. "expected a did:key:z... multibase did")))
  (let [bytes (base58/decode (subs did (count "did:key:z")))]
    (when (or (< (aget bytes "length") 2)
              (not= (aget bytes 0) 0xed)
              (not= (aget bytes 1) 0x01))
      (throw (js/Error. "not an Ed25519 did:key (expected 0xed01 multicodec)")))
    (.slice bytes 2)))

;; The exact EIP-4361 plaintext cacao.core/mint signs and cacao.core/verify
;; reconstructs from the CBOR payload. Mirrors cacao.core/siwe-message
;; line-for-line. This must accept/reject exactly what the canonical JVM
;; verifier would, not a hypothetically "more correct" version.
;;
;; Until 2026-08-31 this comment read "…including that `statement` is accepted
;; by the Clojure function's signature but never round-tripped through the CBOR
;; `p` map — so it never appears here either". That was TRUE when it was
;; written and stopped being true when core started carrying `statement` in
;; `p` (its own comment records that fix: "every CACAO minted with :statement
;; was unverifiable, silently, from the day the option existed"). The fix
;; landed on one side of a mirror, and the mirror kept a comment explaining why
;; it did not need it.
(defn siwe-message
  "Public (not `-`) so cacao.edge.verify-mint can reconstruct the
  identical plaintext when minting — verify and mint must agree byte-for-
  byte on this reconstruction or a freshly-minted CACAO would fail its own
  round-trip through this file's `verify`."
  [payload]
  (let [iss (aget payload "iss")
        addr (last (str/split iss #":"))
        domain (aget payload "domain")
        aud (aget payload "aud")
        version (aget payload "version")
        nonce (aget payload "nonce")
        iat (aget payload "iat")
        exp (aget payload "exp")
        statement (aget payload "statement")
        resources (aget payload "resources")
        lines (atom [(str domain " wants you to sign in with your Ethereum account:")
                     addr ""])]
    ;; `statement` is part of the signed text (`cacao.core/siwe-message` puts
    ;; it here) and this reconstruction did not read it, so a CACAO carrying
    ;; one verified as a BAD SIGNATURE — indistinguishable from a wrong key.
    ;; Measured 2026-08-31: `cacao.core/mint` has carried statement in both the
    ;; signed text and the payload since the day the option existed, and its
    ;; own comment records the equivalent bug being fixed there. Nothing
    ;; round-tripped core's mint against THIS verifier, so the fix never
    ;; reached the half the murakumo Worker calls.
    (when (and statement (not= "" statement))
      (swap! lines conj statement ""))
    (swap! lines conj (str "URI: " aud) (str "Version: " version)
           "Chain ID: 1" (str "Nonce: " nonce) (str "Issued At: " iat))
    (when exp
      (swap! lines conj (str "Expiration Time: " exp)))
    (when (and resources (pos? (aget resources "length")))
      (swap! lines conj "Resources:")
      (doseq [r (array-seq resources)]
        (swap! lines conj (str "- " r))))
    (str/join "\n" @lines)))

(defn verify
  "Verify a base64 CACAO: signature AND temporal window (see temporal-error).
  Returns a Promise of {:valid bool :iss did :payload {...}} on success, or
  {:valid false :error message} on any decode/signature/temporal failure —
  exception-safe, since this always runs against untrusted client input."
  ([cacao-b64] (verify cacao-b64 (now-sec)))
  ([cacao-b64 at-sec]
   (-> (js/Promise.resolve nil)
       (.then
        (fn []
          (let [m (cbor/decode (base64->bytes cacao-b64))
                p (aget m "p")
                s (aget m "s")]
            (when (or (nil? p) (nil? s))
              (throw (js/Error. "malformed CACAO: missing p or s")))
            (let [iss (aget p "iss")
                  payload #js {:iss iss
                               :aud (aget p "aud")
                               :iat (aget p "iat")
                               :exp (aget p "exp")
                               :nonce (aget p "nonce")
                               :domain (aget p "domain")
                               :version (aget p "version")
                               :statement (aget p "statement")
                               :resources (aget p "resources")}
                  sig-bytes (base64->bytes (aget s "s"))
                  pub-bytes (did-key->pubkey iss)]
              (-> (.importKey js/crypto.subtle "raw" pub-bytes #js {:name "Ed25519"} false #js ["verify"])
                  (.then (fn [key]
                           (let [msg-bytes (.encode (js/TextEncoder.) (siwe-message payload))]
                             (.verify js/crypto.subtle "Ed25519" key sig-bytes msg-bytes))))
                  (.then (fn [sig-valid?]
                           (let [resources-err (resources-shape-error (aget payload "resources"))
                                 temporal-err (temporal-error payload at-sec)]
                             (cond
                               (not sig-valid?) #js {:valid false :iss iss}
                               resources-err #js {:valid false :error resources-err :iss iss}
                               temporal-err #js {:valid false :error temporal-err :iss iss}
                               :else #js {:valid true :iss iss :payload payload
                                          :replayUntil (replay-until-sec payload)})))))))))
       (.catch (fn [error]
                 #js {:valid false :error (aget error "message")})))))
