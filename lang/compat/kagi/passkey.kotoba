(ns kagi.passkey
  "Narrow browser-to-JVM boundary for WebAuthn PRF results. The returned PRF
  bytes are secret and must be consumed immediately, never persisted or logged."
  (:import [java.util Base64]))

(defn- decode ^bytes [value field]
  (try (.decode (Base64/getUrlDecoder) ^String value)
       (catch Exception e
         (throw (ex-info "invalid passkey bridge encoding" {:field field} e)))))

(defn consume-bridge-input
  [{:keys [rpId credentialId prfSalt prfOutput secret]} expected-rp-id]
  (when-not (and (= true secret) (= expected-rp-id rpId)
                 (string? credentialId) (string? prfSalt) (string? prfOutput))
    (throw (ex-info "invalid passkey bridge input" {:reason :binding-mismatch})))
  (let [credential (decode credentialId :credential-id)
        prf-salt (decode prfSalt :prf-salt)
        prf (decode prfOutput :prf-output)]
    (when-not (and (pos? (alength credential)) (= 32 (alength prf-salt))
                   (>= (alength prf) 32))
      (throw (ex-info "invalid passkey bridge input" {:reason :invalid-length})))
    {:rp-id rpId :credential-id credentialId :prf-salt prfSalt
     :prf-output prf :secret? true}))
