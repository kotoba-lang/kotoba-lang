(ns tls.suite
  "Cipher suites -- RFC 8446 appendix B.4 -- bound to an injected provider.

  A `suite` value here is the geometry (which hash, which AEAD, how long the
  key and nonce are) plus the two functions that actually do the AEAD, taken
  from the provider. Nothing in this namespace implements a cipher.

  ## Why the AEAD is injected rather than written

  Copied deliberately from `kotoba-lang/noise`, which states the argument:
  constant-time field arithmetic and constant-time tag comparison are exactly
  what a hand-rolled portable port loses. So the primitives come from the
  platform (JDK, `@noble/*`) or, later, from aiueos's native `.kotoba`
  objects, and everything that is pure data-shuffling stays portable.

  Say the consequence plainly: **on bare metal, where there is no JDK and no
  npm, these ports are precisely the part that does not exist yet.** This
  library does not close that gap. It reduces it from `a TLS stack` to
  `X25519, AES-GCM, SHA-256 and one signature verify`."
  (:require [tls.result :as r :refer [ok error]]))

(def ids
  {:TLS_AES_128_GCM_SHA256 0x1301
   :TLS_AES_256_GCM_SHA384 0x1302
   :TLS_CHACHA20_POLY1305_SHA256 0x1303
   :TLS_AES_128_CCM_SHA256 0x1304
   :TLS_AES_128_CCM_8_SHA256 0x1305})

(def by-id (into {} (map (fn [[k v]] [v k])) ids))

(def parameters
  "`:iv-length` is the *nonce* length. Section 5.3 builds the per-record nonce
   by XORing the sequence number into the static IV, so the IV is exactly as
   long as the nonce -- it is not a GCM salt with an explicit part."
  {:TLS_AES_128_GCM_SHA256
   {:hash :sha256 :aead :aes-128-gcm :key-length 16 :iv-length 12 :tag-length 16}
   :TLS_AES_256_GCM_SHA384
   {:hash :sha384 :aead :aes-256-gcm :key-length 32 :iv-length 12 :tag-length 16}
   :TLS_CHACHA20_POLY1305_SHA256
   {:hash :sha256 :aead :chacha20-poly1305 :key-length 32 :iv-length 12 :tag-length 16}})

(def supported
  "The suites this client offers, in preference order.

   Both are here because both can be exercised end to end: RFC 8448 pins
   AES-128-GCM byte for byte, and ChaCha20-Poly1305 shares the whole schedule
   and record layer with it, differing only in the injected AEAD. AES-256-GCM
   is *not* offered -- it needs SHA-384, and offering a suite whose hash the
   provider may not carry means negotiating into a handshake that cannot
   finish."
  [:TLS_AES_128_GCM_SHA256 :TLS_CHACHA20_POLY1305_SHA256])

(defn negotiable
  "Which of `supported` this particular provider can actually run. Computed,
   not declared: a provider without ChaCha20-Poly1305 must not have it
   offered on its behalf, because the server may well pick it."
  [provider]
  (vec (filter (fn [s]
                 (let [{:keys [hash aead]} (parameters s)]
                   (and (fn? (get-in provider [:hash hash]))
                        (map? (get-in provider [:aead aead]))
                        (fn? (get-in provider [:aead aead :seal]))
                        (fn? (get-in provider [:aead aead :open])))))
               supported)))

(defn suite
  "Bind `suite-name` to `provider`.

   Refuses at construction rather than at the first record. A half-wired suite
   otherwise surfaces as a `bad_record_mac` on the *peer*, which is the worst
   possible place to debug it."
  [provider suite-name]
  (let [params (get parameters suite-name)]
    (if (nil? params)
      (error :illegal_parameter :unknown-cipher-suite {:tls/suite suite-name})
      (let [{:keys [hash aead]} params
            a (get-in provider [:aead aead])]
        (cond
          (not (fn? (get-in provider [:hash hash])))
          (error :insufficient_security :provider-missing-hash
                 {:tls/suite suite-name :tls/hash hash})
          (or (not (map? a)) (not (fn? (:seal a))) (not (fn? (:open a))))
          (error :insufficient_security :provider-missing-aead
                 {:tls/suite suite-name :tls/aead aead})
          :else
          (ok (merge params
                     {:tls/suite suite-name
                      :tls/id (get ids suite-name)
                      :aead-seal (:seal a)
                      :aead-open (:open a)})))))))
