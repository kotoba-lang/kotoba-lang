;; EXTRACTED (2026-07-25) verbatim from cloud-itonami.edge.cacao-mint — the
;; 2nd consumer (kotobase.net's authn Worker: WebAuthn passkey -> server-held
;; Ed25519 did:key -> CACAO session) appeared, triggering the same extraction
;; rule (ADR-2607141654: extract on 2nd consumer, not before) that moved
;; base58/cbor/verify here on 2026-07-15. cacao.edge.verify's ns docstring
;; already anticipated this file by name ("cacao.edge.verify-mint"); the
;; landed name is `cacao.edge.mint`, since it is the mint half of
;; `cacao.edge.verify`, not a third thing. cloud-itonami's own copy remains
;; canonical-in-use until it migrates here.
(ns cacao.edge.mint
  "CAIP-122 / SIWE (EIP-4361) CACAO mint for the edge — the inverse of
  `cacao.edge.verify`, and the CLJS port of `cacao.core/mint`.

  Why an edge minter exists at all, given that a CACAO is supposed to be
  minted in the holder's OWN runtime: a WebAuthn passkey cannot sign a
  CACAO directly. WebAuthn's assertion signature is over
  `authenticatorData || SHA-256(clientDataJSON)` — a fixed structure the
  platform authenticator controls, not an arbitrary app-chosen SIWE
  plaintext. So a service that wants passwordless passkey login AND a
  CACAO-authorized API surface must verify the assertion independently and
  then mint a CACAO on the user's behalf, backed by a per-user Ed25519 key
  it holds but only unwraps behind a verified passkey assertion.

  State that custody model plainly wherever this is used: a CACAO minted
  here is server-custodied, passkey-gated — NOT a non-custodial wallet. A
  caller who wants true non-custodial did:key control should mint on their
  own device (`cacao.core/mint`) and present the result; both produce the
  same wire format, and `cacao.edge.verify` cannot tell them apart, which
  is exactly why the distinction has to be documented rather than
  detected.

  `sign-fn` is the only host seam: a fn of msg-bytes -> Promise<sig-bytes>
  (e.g. `#(js/crypto.subtle.sign \"Ed25519\" priv-key %)`). Taking a signer
  rather than a private key keeps a non-exportable `CryptoKey` — the shape
  an unwrapped, passkey-gated key actually has — usable without ever
  materializing key bytes.

  CLJS-only (js/crypto.subtle, js/Promise, js/btoa), like every other
  `cacao.edge.*` namespace."
  (:require [cacao.edge.base58 :as base58]
            [cacao.edge.cbor :as cbor]
            [cacao.edge.verify :as verify]))

(defn did-key-from-raw-ed25519-pub
  "did:key:z... (Ed25519, multicodec 0xed01) from a raw 32-byte public key —
  the mint-side inverse of cacao.edge.verify's private `did-key->pubkey`."
  [raw-pub-bytes]
  (str "did:key:z" (base58/encode (js/Uint8Array.from
                                   (into [0xed 0x01] (array-seq (js/Array.from raw-pub-bytes)))))))

(defn bytes->base64
  "Standard base64 (not base64url) — the encoding a CACAO's own wire blob
  and its inner signature both use, matching `cacao.edge.verify/base64->bytes`."
  [bytes]
  (let [arr (js/Array.from bytes)]
    (js/btoa (apply str (map js/String.fromCharCode (array-seq arr))))))

(defn mint
  "Sign `fields` (:domain :aud :version :nonce :iat :exp :resources — all
  strings except :resources, a vector-of-strings or nil) as `iss` using
  `sign-fn`, and assemble the base64 CACAO blob `cacao.edge.verify/verify`
  accepts unmodified. Returns a Promise<{:cacao-b64 :iss}>.

  `iss` is an explicit caller-supplied string rather than something derived
  from a keypair here: the login path signs with an unwrapped
  (decrypted-in-memory, non-exportable) private key whose `did` the caller
  already knows from its own store, so there is no keypair object to export
  a public key from at that point. A registration path, which does hold a
  fresh exportable keypair, derives `iss` itself via
  `did-key-from-raw-ed25519-pub` before calling this.

  The `p` field order below is signature-significant — `verify` reconstructs
  the SIWE plaintext from the decoded map, so mint and verify must agree
  byte-for-byte on both the plaintext and the CBOR key order."
  [iss sign-fn fields]
  (let [payload #js {:iss iss
                     :aud (:aud fields)
                     :iat (:iat fields)
                     :exp (:exp fields)
                     :nonce (:nonce fields)
                     :domain (:domain fields)
                     :version (or (:version fields) "1")
                     :resources (clj->js (or (:resources fields) []))}
        msg (verify/siwe-message payload)
        msg-bytes (.encode (js/TextEncoder.) msg)]
    (-> (sign-fn msg-bytes)
        (.then
         (fn [sig-ab]
           (let [sig-b64 (bytes->base64 (js/Uint8Array. sig-ab))
                 p-pairs (cond-> [["iss" iss]
                                  ["aud" (:aud fields)]
                                  ["iat" (:iat fields)]
                                  ["nonce" (:nonce fields)]
                                  ["domain" (:domain fields)]
                                  ["version" (or (:version fields) "1")]]
                           (:exp fields) (conj ["exp" (:exp fields)])
                           (seq (:resources fields)) (conj ["resources" (vec (:resources fields))]))
                 outer (cbor/encode-cacao-envelope p-pairs sig-b64)]
             {:cacao-b64 (bytes->base64 (js/Uint8Array.from (clj->js outer)))
              :iss iss}))))))
