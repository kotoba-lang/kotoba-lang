(ns tls.alert
  "TLS 1.3 alert descriptions -- RFC 8446 section 6.

  These exist so that a refusal in this library is the refusal a peer would
  actually see. `:bad_record_mac` and `:decrypt_error` are different answers to
  a network observer, and collapsing them into one generic error is how a
  distinguishing oracle gets built by accident.")

(def descriptions
  {:close_notify 0
   :unexpected_message 10
   :bad_record_mac 20
   :record_overflow 22
   :handshake_failure 40
   :bad_certificate 42
   :unsupported_certificate 43
   :certificate_revoked 44
   :certificate_expired 45
   :certificate_unknown 46
   :illegal_parameter 47
   :unknown_ca 48
   :access_denied 49
   :decode_error 50
   :decrypt_error 51
   :protocol_version 70
   :insufficient_security 71
   :internal_error 80
   :inappropriate_fallback 86
   :user_canceled 90
   :missing_extension 109
   :unsupported_extension 110
   :unrecognized_name 112
   :bad_certificate_status_response 113
   :unknown_psk_identity 115
   :certificate_required 116
   :no_application_protocol 120})

(def ^:private by-code (into {} (map (fn [[k v]] [v k])) descriptions))

(def levels {:warning 1 :fatal 2})

(defn encode
  "An Alert record body: two bytes, level then description.

   RFC 8446 section 6: `close_notify` and `user_canceled` are the only alerts
   that may be a warning; everything else is fatal regardless of what the
   caller asks for, because a peer that treats a fatal condition as a warning
   keeps a broken connection open."
  ([description] (encode description :fatal))
  ([description level]
   (let [code (get descriptions description)
         level (if (#{:close_notify :user_canceled} description) level :fatal)]
     (when (nil? code) (throw (ex-info "unknown alert description" {:description description})))
     [(get levels level) code])))

(defn describe [code] (get by-code code))
