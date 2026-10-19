(ns tls.extension
  "Hello extensions -- RFC 8446 section 4.2, and RFC 6066 for server_name.

  Two rules shape this namespace.

  **Unknown extensions round-trip.** Every extension is parsed into
  `{:tls/type k-or-int :tls/data bytes}` first, and only the ones this client
  understands get an interpreted `:tls/value`. The raw bytes are always kept.
  That is what lets `parse` then `encode` reproduce a foreign ClientHello byte
  for byte -- which is the only honest way to test a parser you did not write
  the encoder for.

  **A repeated extension is an error, not a last-one-wins.** Section 4.2:
  `There MUST NOT be more than one extension of the same type in a given
  extension block.` A parser that silently keeps the last one lets an attacker
  append a second `supported_versions` after the real one and change what the
  peer believes was negotiated."
  (:require [tls.codec :as c]
            [tls.result :as r :refer [ok error]]))

(def types
  {:server_name 0
   :max_fragment_length 1
   :status_request 5
   :supported_groups 10
   :signature_algorithms 13
   :use_srtp 14
   :heartbeat 15
   :application_layer_protocol_negotiation 16
   :signed_certificate_timestamp 18
   :client_certificate_type 19
   :server_certificate_type 20
   :padding 21
   :record_size_limit 28
   :session_ticket 35
   :pre_shared_key 41
   :early_data 42
   :supported_versions 43
   :cookie 44
   :psk_key_exchange_modes 45
   :certificate_authorities 47
   :oid_filters 48
   :post_handshake_auth 49
   :signature_algorithms_cert 50
   :key_share 51
   :renegotiation_info 0xff01})

(def type-by-code (into {} (map (fn [[k v]] [v k])) types))

(def groups
  "NamedGroup -- section 4.2.7. Only `:x25519` is offered by this client:
  it is the one key exchange the workspace has a portable story for
  (`kotoba-lang/noise` injects it the same way, and aiueos has
  `x25519.kotoba`). The P-curves are listed so a server's choice can be
  *named* in an error rather than reported as an opaque number."
  {:secp256r1 0x0017 :secp384r1 0x0018 :secp521r1 0x0019
   :x25519 0x001d :x448 0x001e
   :ffdhe2048 0x0100 :ffdhe3072 0x0101 :ffdhe4096 0x0102})

(def group-by-code (into {} (map (fn [[k v]] [v k])) groups))

(def signature-schemes
  "SignatureScheme -- section 4.2.3. This is the set a client is willing to
   see on a CertificateVerify; whether any given one can be *verified* is the
   provider's answer, not this table's."
  {:rsa_pkcs1_sha256 0x0401 :rsa_pkcs1_sha384 0x0501 :rsa_pkcs1_sha512 0x0601
   :ecdsa_secp256r1_sha256 0x0403 :ecdsa_secp384r1_sha384 0x0503
   :ecdsa_secp521r1_sha512 0x0603
   :rsa_pss_rsae_sha256 0x0804 :rsa_pss_rsae_sha384 0x0805 :rsa_pss_rsae_sha512 0x0806
   :ed25519 0x0807 :ed448 0x0808
   :rsa_pss_pss_sha256 0x0809 :rsa_pss_pss_sha384 0x080a :rsa_pss_pss_sha512 0x080b})

(def signature-scheme-by-code (into {} (map (fn [[k v]] [v k])) signature-schemes))

(defn- code-of [t] (if (keyword? t) (get types t) t))

;; ------------------------------------------------------------------ encode

(defn extension
  "One `Extension`: uint16 type, then `opaque extension_data<0..2^16-1>`."
  [t data]
  (let [code (code-of t)]
    (if (nil? code)
      (error :internal_error :unknown-extension-type {:tls/type t})
      (r/let-ok [body (c/write-vector 2 0 65535 :extension_data (vec data))]
        (ok (vec (concat (c/u16 code) body)))))))

(defn server-name
  "RFC 6066 section 3. A single host_name entry; the list exists in the wire
   format but the RFC allows exactly one name of each type, and TLS 1.3
   servers universally expect one."
  [host]
  (r/let-ok [nm (c/write-vector 2 1 65535 :host_name (c/ascii host))]
    (r/let-ok [lst (c/write-vector 2 1 65535 :server_name_list (vec (concat [0] nm)))]
      (extension :server_name lst))))

(defn supported-versions-client
  "`ProtocolVersion versions<2..254>` -- section 4.2.1. 0x0304 only: this is a
   TLS 1.3 client, and listing 1.2 would invite a downgrade it cannot speak."
  []
  (r/let-ok [v (c/write-vector 1 2 254 :versions (c/u16 0x0304))]
    (extension :supported_versions v)))

(defn supported-versions-server
  "The *server* form of `supported_versions` -- a bare `ProtocolVersion`, not a
   vector. Section 4.2.1 gives the extension two different bodies depending on
   which Hello carries it, and reading the client form in a ServerHello is a
   real interop bug rather than a hypothetical one; having both encoders here
   is what lets the parser be tested against each."
  ([] (supported-versions-server 0x0304))
  ([version] (extension :supported_versions (c/u16 version))))

(defn supported-groups [gs]
  (r/let-ok [v (c/write-vector 2 2 65534 :named_group_list
                              (vec (mapcat #(c/u16 (get groups % %)) gs)))]
    (extension :supported_groups v)))

(defn signature-algorithms [schemes]
  (r/let-ok [v (c/write-vector 2 2 65534 :supported_signature_algorithms
                              (vec (mapcat #(c/u16 (get signature-schemes % %)) schemes)))]
    (extension :signature_algorithms v)))

(defn key-share-client
  "`KeyShareClientHello` -- section 4.2.8. `entries` is a seq of
   `[group public-key-bytes]` in the client's preference order."
  [entries]
  (r/let-ok [body (reduce (fn [acc [g k]]
                            (if (r/error? acc)
                              acc
                              (r/let-ok [kx (c/write-vector 2 1 65535 :key_exchange (vec k))]
                                (ok (vec (concat (r/val acc) (c/u16 (get groups g g)) kx))))))
                          (ok [])
                          entries)]
    (r/let-ok [v (c/write-vector 2 0 65535 :client_shares body)]
      (extension :key_share v))))

(defn psk-key-exchange-modes [modes]
  (r/let-ok [v (c/write-vector 1 1 255 :ke_modes
                              (mapv {:psk_ke 0 :psk_dhe_ke 1} modes))]
    (extension :psk_key_exchange_modes v)))

(defn encode-block
  "`Extension extensions<n..2^16-1>` -- the length-prefixed block that ends a
   Hello. Refuses a duplicate type on the way out as well as on the way in."
  [lo exts]
  (let [codes (map (fn [e] (code-of (:tls/type e))) exts)
        dupes (->> codes frequencies (filter (fn [[_ n]] (> n 1))) (map first))]
    (if (seq dupes)
      (error :illegal_parameter :duplicate-extension
             {:tls/types (mapv #(get type-by-code % %) dupes)})
      (c/write-vector 2 lo 65535 :extensions
                      (vec (mapcat :tls/raw exts))))))

(defn ->ext
  "Wrap an already-encoded extension for `encode-block`."
  [t raw] {:tls/type t :tls/raw (vec raw)})

;; ------------------------------------------------------------------- parse

(defn- parse-supported-versions-server [data]
  (if (= 2 (count data))
    (ok {:tls/version (+ (* 256 (nth data 0)) (nth data 1))})
    (error :decode_error :bad-supported-versions {:tls/length (count data)})))

(defn- parse-key-share-server [data]
  (let [cur (c/cursor data)]
    (r/let-ok [[g cur] (c/read-u16 cur :group)
               [k cur] (c/read-vector cur 2 1 65535 :key_exchange)
               _ (c/end cur :key_share)]
      (ok {:tls/group (get group-by-code g g) :tls/key-exchange k}))))

(defn- parse-key-share-hrr [data]
  (if (= 2 (count data))
    (ok {:tls/group (let [g (+ (* 256 (nth data 0)) (nth data 1))]
                      (get group-by-code g g))
         :tls/hello-retry-request true})
    (error :decode_error :bad-key-share {:tls/length (count data)})))

(def ^:private server-parsers
  {:supported_versions parse-supported-versions-server
   :key_share parse-key-share-server})

(defn parse-block
  "Parse an extension block. `context` is `:server-hello`, `:hello-retry-request`
   or `:generic`; it selects which extensions get interpreted, because
   `key_share` has three different bodies depending on where it appears and
   guessing from the length is how a HelloRetryRequest gets read as a share."
  [bytes context]
  (let [parsers (case context
                  :server-hello server-parsers
                  :hello-retry-request (assoc server-parsers :key_share parse-key-share-hrr)
                  {})]
    (loop [cur (c/cursor bytes), acc [], seen #{}]
      (if (c/exhausted? cur)
        (ok acc)
        (let [res (r/let-ok [[code cur] (c/read-u16 cur :extension_type)
                             [data cur] (c/read-vector cur 2 0 65535 :extension_data)]
                    (ok [code data cur]))]
          (if (r/error? res)
            res
            (let [[code data cur] (r/val res)
                  t (get type-by-code code code)]
              (if (seen code)
                (error :illegal_parameter :duplicate-extension {:tls/type t})
                (let [p (get parsers t)
                      parsed (when p (p data))]
                  (cond
                    (and parsed (r/error? parsed)) parsed
                    :else
                    (recur cur
                           (conj acc (cond-> {:tls/type t
                                              :tls/data data
                                              :tls/raw (vec (concat (c/u16 code)
                                                                    (c/u16 (count data))
                                                                    data))}
                                       parsed (assoc :tls/value (r/val parsed))))
                           (conj seen code))))))))))))

(defn find-ext [exts t] (first (filter #(= t (:tls/type %)) exts)))
