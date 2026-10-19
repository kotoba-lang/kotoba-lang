(ns kotoba.lang.pqh.pq-noble
  "Browser-compatible IPq host backed by audited Noble primitives."
  (:require [kotoba.lang.pqh.pq :as pq]
            ["@noble/curves/ed25519.js" :refer [x25519]]
            ["@noble/hashes/hkdf.js" :refer [hkdf]]
            ["@noble/hashes/sha2.js" :refer [sha256]]
            ["@noble/post-quantum/ml-kem.js" :refer [ml_kem768]]
            ["@noble/post-quantum/ml-dsa.js" :refer [ml_dsa65]]))

(def noble-pq
  (reify pq/IPq
    (-x25519-generate [_]
      (let [secret (if (.-randomSecretKey (.-utils x25519))
                     (.randomSecretKey (.-utils x25519))
                     (.randomPrivateKey (.-utils x25519)))]
        [secret (.getPublicKey x25519 secret)]))
    (-x25519-dh [_ secret public]
      (.getSharedSecret x25519 secret public))
    (-mlkem-generate [_]
      (let [kp (.keygen ml_kem768)]
        [(.-secretKey kp) (.-publicKey kp)]))
    (-mlkem-encapsulate [_ public]
      (let [result (.encapsulate ml_kem768 public)]
        [(.-cipherText result) (.-sharedSecret result)]))
    (-mlkem-decapsulate [_ secret ciphertext]
      (.decapsulate ml_kem768 ciphertext secret))
    (-hkdf-sha256 [_ ikm salt info length]
      (hkdf sha256 ikm salt info length))
    (-mldsa-keygen-from-seed [_ seed]
      (.-publicKey (.keygen ml_dsa65 seed)))
    (-mldsa-sign [_ seed message]
      (let [kp (.keygen ml_dsa65 seed)]
        (.sign ml_dsa65 message (.-secretKey kp))))
    (-mldsa-verify [_ public message signature]
      (.verify ml_dsa65 signature message public))))

(defn with-noble
  "Invoke synchronous `f` with pq/*pq* bound to the browser Noble host."
  [f]
  (binding [pq/*pq* noble-pq]
    (f)))
