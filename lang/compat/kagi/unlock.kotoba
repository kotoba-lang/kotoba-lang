(ns kagi.unlock
  "VMK unlock envelopes.

  The vault stores wrapped VMK envelopes. Device-local unlock secrets live in a
  SecretStore, not in vault EDN, stdout, logs, or manimani state."
  (:require [kagi.crypto :as crypto]
            [kagi.secret-store :as secret-store])
  (:import [java.util Base64]))

(def ^:private info-os-keychain (.getBytes "kagi/unlock/os-keychain/v1" "UTF-8"))
(def ^:private info-passkey-prf (.getBytes "kagi/unlock/passkey-prf/v1" "UTF-8"))

(defn- passkey-info ^bytes [rp-id credential-id]
  (.getBytes (str "kagi/unlock/passkey-prf/v1\u0000" rp-id "\u0000" credential-id) "UTF-8"))

(defn- b64 [^bytes b]
  (.encodeToString (Base64/getEncoder) b))

(defn new-device-secret
  "Return a printable secret suitable for OS keychain storage."
  [p]
  (b64 (crypto/rand-bytes p 32)))

(defn- info-for [method]
  (case method
    :os-keychain info-os-keychain
    :passkey-prf info-passkey-prf
    info-os-keychain))

(defn wrap-vmk
  [p ^bytes vmk method secret-material fields]
  (let [salt (crypto/rand-bytes p 16)
        kek (crypto/hkdf p secret-material salt (info-for method) 32)
        nonce (crypto/rand-bytes p 12)]
    (merge {:method method
            :salt salt
            :nonce nonce
            :wrapped (crypto/aead-seal p kek nonce vmk (byte-array 0))}
           fields)))

(defn unwrap-vmk
  [p {:keys [method salt nonce wrapped]} secret-material]
  (let [kek (crypto/hkdf p secret-material salt (info-for method) 32)]
    (crypto/aead-open p kek nonce wrapped (byte-array 0))))

(defn os-keychain-wrap
  [p vmk store ref]
  (let [secret (new-device-secret p)]
    (secret-store/put-secret! store ref secret {:content-type "text/plain"
                                                :purpose "kagi-vmk-unlock"})
    (wrap-vmk p vmk :os-keychain (.getBytes ^String secret "UTF-8")
              {:ref ref
               :provider :apple-keychain
               :created-by :kagi-unlock-enable})))

(defn passkey-prf-wrap
  "Create a VMK envelope from WebAuthn PRF output supplied by the browser/host."
  [p vmk ^bytes prf-output {:keys [rp-id credential-id] :as fields}]
  (when-not (and (seq rp-id) (seq credential-id) (pos? (alength prf-output)))
    (throw (ex-info "incomplete passkey PRF registration" {})))
  (let [salt (crypto/rand-bytes p 16)
        info (passkey-info rp-id credential-id)
        kek (crypto/hkdf p prf-output salt info 32)
        nonce (crypto/rand-bytes p 12)]
    (merge fields {:method :passkey-prf :salt salt :nonce nonce
                   :wrapped (crypto/aead-seal p kek nonce vmk info)
                   :provider :webauthn-prf :created-by :passkey-registration})))

(defn unlock-with-passkey-prf [p {:keys [method salt nonce wrapped rp-id credential-id]} ^bytes prf-output]
  (when-not (= :passkey-prf method)
    (throw (ex-info "not a passkey PRF envelope" {:method method})))
  (let [info (passkey-info rp-id credential-id)
        kek (crypto/hkdf p prf-output salt info 32)]
    (crypto/aead-open p kek nonce wrapped info)))

(defn add-wrap [meta wrap]
  (update meta :unlock/wraps (fnil conj []) wrap))

(defn os-keychain-wraps [meta]
  (filter #(= :os-keychain (:method %)) (:unlock/wraps meta)))

(defn unlock-with-os-keychain
  ([p meta] (unlock-with-os-keychain p meta nil))
  ([p meta preferred-ref]
   (let [wraps (cond->> (os-keychain-wraps meta)
                 preferred-ref
                 (sort-by #(if (= preferred-ref (:ref %)) 0 1)))]
     (some (fn [{:keys [ref] :as wrap}]
             (try
               (let [store (secret-store/store-for-ref ref)
                     secret (secret-store/get-secret store ref {:purpose "kagi-vmk-unlock"})]
                 (unwrap-vmk p wrap (.getBytes ^String secret "UTF-8")))
               (catch Exception _ nil)))
           wraps))))

(defn passkey-prf-envelope-shape []
  {:method :passkey-prf
   :status :host-adapter-ready
   :fields [:rp-id :credential-id :salt :nonce :wrapped]
   :flow ["navigator.credentials.create with prf extension"
          "navigator.credentials.get returns PRF output"
          "HKDF(PRF output, salt, kagi/unlock/passkey-prf/v1) unwraps VMK"]
   :fallback [:os-keychain :passphrase-recovery]})

(defn status [meta]
  {:wrap-count (count (:unlock/wraps meta))
   :methods (mapv (fn [w]
                    (cond-> {:method (:method w)
                             :provider (:provider w)
                             :ref (secret-store/redact-ref (:ref w))}
                      (:rp-id w) (assoc :rp-id (:rp-id w))))
                  (:unlock/wraps meta))
   :passphrase-recovery? (boolean (and (:salt meta) (:nonce meta) (:wrapped meta)))
   :passkey-prf (passkey-prf-envelope-shape)})
