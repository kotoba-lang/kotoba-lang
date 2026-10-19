(ns noise.kdf
  "HMAC and Noise's HKDF (Noise Protocol Framework rev 34 §4.3, §5.1) over the
  suite's injected `:hash` — pure `.cljc`.

  Noise defines HKDF in terms of HMAC(hash) for *every* hash, including BLAKE2s
  (which has a native keyed mode). We follow the spec, not the shortcut: using
  keyed BLAKE2s here instead of HMAC-BLAKE2s would produce chaining keys that no
  other Noise implementation agrees with."
  (:require [noise.blake2s :as blake2s]))

(defn hmac
  "HMAC-`hash` (RFC 2104) with the suite's hash and block length."
  [{:keys [hash blocklen]} key data]
  (let [k (vec key)
        k (if (> (count k) blocklen) (vec (hash k)) k)
        k (into k (repeat (- blocklen (count k)) 0))
        ipad (mapv #(bit-xor % 0x36) k)
        opad (mapv #(bit-xor % 0x5c) k)]
    (vec (hash (into opad (hash (into ipad (vec data))))))))

(defn hkdf
  "Noise HKDF(chaining-key, ikm) -> a vector of `num-outputs` (2 or 3) keys of
   hashlen bytes each."
  [suite chaining-key ikm num-outputs]
  (when-not (#{2 3} num-outputs)
    (throw (ex-info "noise HKDF emits 2 or 3 outputs" {:num-outputs num-outputs})))
  (let [tk (hmac suite chaining-key ikm)
        o1 (hmac suite tk [0x01])
        o2 (hmac suite tk (conj (vec o1) 0x02))]
    (if (= 2 num-outputs)
      [o1 o2]
      [o1 o2 (hmac suite tk (conj (vec o2) 0x03))])))

(def blake2s-hash
  "The `:hash` port for the BLAKE2s suites."
  blake2s/hash)
