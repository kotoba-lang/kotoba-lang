(ns kagi.kotobase-seal
  "Kagi implementation of `kotobase.sealed-store`'s host callbacks.

  This namespace deliberately depends only on Kagi. Consumers pass the result
  of `sealed-store-options` to Kotobase, avoiding a dependency cycle."
  (:require [kagi.crypto :as crypto]
            [kagi.persist :as persist])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(def algorithms [:x25519 :ml-kem-768 :aes-256-gcm])

(defn- sha256 [^bytes value]
  (.digest (MessageDigest/getInstance "SHA-256") value))

(defn- hex [^bytes value]
  (apply str (map #(format "%02x" (bit-and (int %) 0xff)) value)))

(defn ciphertext-digest [^bytes ciphertext]
  (str "sha256:" (hex (sha256 ciphertext))))

(defn- aad ^bytes [compartment]
  (.getBytes ^String (str "kagi/kotobase-sealed-store/v1\u0000" compartment)
             StandardCharsets/UTF_8))

(defn sealed-store-options
  "Build fail-closed seal/unseal callbacks for one opaque user compartment.
  VMK remains captured in device-local memory and is never part of an envelope."
  [{:keys [provider vmk compartment key-epoch]
    :or {key-epoch 1}}]
  (when-not (and provider vmk (string? compartment) (seq compartment)
                 (pos-int? key-epoch))
    (throw (ex-info "incomplete Kagi Kotobase sealing context"
                    {:type :kagi/incomplete-kotobase-seal-context})))
  (let [compartment-id compartment
        kek (crypto/compartment-key provider vmk compartment-id)
        aad* (aad compartment-id)]
    {:crypto-policy {:kotoba.security/crypto-policy-version 1
                     :mode :hybrid-required
                     :hybrid-epoch-floor 1}
     :ciphertext-digest-fn ciphertext-digest
     :seal-fn
     (fn [plaintext]
       (let [{:keys [dek nonce ciphertext]}
             (crypto/seal-item
              provider (.getBytes ^String (persist/->edn plaintext)
                                  StandardCharsets/UTF_8)
              aad*)]
         {:envelope/algorithms algorithms
          :envelope/provider {:provider/id :kagi
                              :provider/fips-validated false}
          :envelope/epoch key-epoch
          :envelope/kem? true
          :envelope/hybrid? true
          :sealed/ciphertext ciphertext
          :sealed/ciphertext-digest (ciphertext-digest ciphertext)
          :sealed/nonce nonce
          :sealed/wrap (crypto/wrap-dek provider kek dek)
          :sealed/compartment compartment-id}))
     :unseal-fn
     (fn [{:sealed/keys [ciphertext nonce wrap]
           sealed-compartment :sealed/compartment}]
       (when-not (= compartment-id sealed-compartment)
         (throw (ex-info "Kagi sealed compartment mismatch"
                         {:type :kagi/sealed-compartment-mismatch})))
       (let [dek (crypto/unwrap-dek provider kek wrap)
             plaintext (crypto/open-item provider dek nonce ciphertext aad*)]
         (persist/<-edn (String. plaintext StandardCharsets/UTF_8))))}))
