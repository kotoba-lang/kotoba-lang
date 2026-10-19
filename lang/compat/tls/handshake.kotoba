(ns tls.handshake
  "Handshake messages -- RFC 8446 section 4.

  Every handshake message is `HandshakeType msg_type; uint24 length; body`.
  The four-byte header is part of the transcript, which is why `parse` returns
  the raw message alongside the parsed one: hashing the body and forgetting the
  header produces a transcript that is wrong in a way nothing local can see --
  only the peer's Finished disagrees, and by then there is no diagnostic left.

  ## The ServerHello that is not a ServerHello

  A HelloRetryRequest is a ServerHello whose `random` is a specific
  thirty-two byte constant (section 4.1.3): SHA-256 of the ASCII string
  `HelloRetryRequest`. It is not a distinct message type. An implementation
  that does not check the constant treats the retry as a real ServerHello,
  derives handshake secrets from a key share that does not exist, and fails
  later with a MAC error. So `parse-server-hello` checks, and says so."
  (:require [tls.codec :as c]
            [tls.extension :as ext]
            [tls.result :as r :refer [ok error]]))

(def types
  {:client_hello 1 :server_hello 2 :new_session_ticket 4 :end_of_early_data 5
   :encrypted_extensions 8 :certificate 11 :certificate_request 13
   :certificate_verify 15 :finished 20 :key_update 24 :message_hash 254})

(def type-by-code (into {} (map (fn [[k v]] [v k])) types))

(def hello-retry-request-random
  "SHA-256(\"HelloRetryRequest\") -- RFC 8446 section 4.1.3. Written out
   because it is a protocol constant, not a computed value; the RFC prints it."
  (c/unhex "CF21AD74E59A6111BE1D8C021E65B891C2A211167ABB8C5E079E09E2C8A8339C"))

(def legacy-version [0x03 0x03])

(defn message
  "Frame a body as a handshake message."
  [t body]
  (let [code (get types t)]
    (cond
      (nil? code) (error :internal_error :unknown-handshake-type {:tls/type t})
      (> (count body) 0xffffff) (error :internal_error :handshake-too-long
                                       {:tls/length (count body)})
      :else (ok (vec (concat [code] (c/u24 (count body)) (vec body)))))))

(defn split
  "Peel one handshake message off the front of a byte vector.

   -> `[:ok {:tls/type k :tls/body bytes :tls/raw bytes :tls/rest bytes}]`.
   `:tls/raw` includes the header, for the transcript."
  [bytes]
  (let [cur (c/cursor bytes)]
    (r/let-ok [[code cur] (c/read-u8 cur :msg_type)
               [n cur] (c/read-u24 cur :length)
               [body cur] (c/read-bytes cur n :body)]
      (let [t (get type-by-code code)]
        (if (nil? t)
          (error :unexpected_message :unknown-handshake-type {:tls/code code})
          (ok {:tls/type t
               :tls/body body
               :tls/raw (vec (concat [code] (c/u24 n) body))
               :tls/rest (subvec (:bytes cur) (:pos cur))}))))))

(defn split-all
  "All handshake messages in a buffer. Refuses a trailing partial message --
   the caller must buffer until a message is whole rather than being handed a
   silent truncation."
  [bytes]
  (loop [b (vec bytes), acc []]
    (if (empty? b)
      (ok acc)
      (let [res (split b)]
        (if (r/error? res)
          res
          (let [m (r/val res)]
            (recur (:tls/rest m) (conj acc (dissoc m :tls/rest)))))))))

;; ---------------------------------------------------------- ClientHello

(defn client-hello
  "RFC 8446 section 4.1.2.

     {:random 32-bytes :session-id bytes :cipher-suites [ids] :extensions [exts]}

   `legacy_session_id` is echoed by the server in TLS 1.3 and otherwise
   unused; section 4.1.2 says a client that is not in compatibility mode sends
   an empty one, and a client in compatibility mode sends 32 random bytes. We
   send 32, because a middlebox that sees an empty session id in a ClientHello
   is more likely to drop the connection than one that sees a full one, and
   the cost is thirty-two bytes."
  [{:keys [random session-id cipher-suites extensions]}]
  (r/let-ok [sid (c/write-vector 1 0 32 :legacy_session_id (vec session-id))
             cs (c/write-vector 2 2 65534 :cipher_suites
                                (vec (mapcat c/u16 cipher-suites)))
             comp (c/write-vector 1 1 255 :legacy_compression_methods [0])
             exts (ext/encode-block 8 extensions)]
    (message :client_hello
             (vec (concat legacy-version (vec random) sid cs comp exts)))))

(defn parse-client-hello
  "Parse a ClientHello body. Present so the RFC 8448 trace can be parsed and
   re-encoded byte for byte -- a round-trip against someone else's encoder is
   the only parser test that is not circular."
  [body]
  (let [cur (c/cursor body)]
    (r/let-ok [[ver cur] (c/read-bytes cur 2 :legacy_version)
               [rnd cur] (c/read-bytes cur 32 :random)
               [sid cur] (c/read-vector cur 1 0 32 :legacy_session_id)
               [cs cur] (c/read-vector cur 2 2 65534 :cipher_suites)
               [cm cur] (c/read-vector cur 1 1 255 :legacy_compression_methods)
               [eb cur] (c/read-vector cur 2 8 65535 :extensions)
               _ (c/end cur :client_hello)
               exts (ext/parse-block eb :generic)]
      (cond
        (odd? (count cs))
        (error :decode_error :odd-cipher-suites-length {:tls/length (count cs)})
        (not= cm [0])
        ;; Section 4.1.2: `legacy_compression_methods` MUST be a single zero
        ;; byte. Compression in TLS is CRIME; there is no such thing as a
        ;; benign non-zero value here.
        (error :illegal_parameter :compression-offered {:tls/methods cm})
        :else
        (ok {:tls/legacy-version ver
             :tls/random rnd
             :tls/session-id sid
             :tls/cipher-suites (mapv (fn [[a b]] (+ (* 256 a) b)) (partition 2 cs))
             :tls/extensions exts})))))

;; ---------------------------------------------------------- ServerHello

(defn parse-server-hello
  "RFC 8446 section 4.1.3, plus the three checks that make it TLS 1.3."
  [body]
  (let [cur (c/cursor body)]
    (r/let-ok [[ver cur] (c/read-bytes cur 2 :legacy_version)
               [rnd cur] (c/read-bytes cur 32 :random)
               [sid cur] (c/read-vector cur 1 0 32 :legacy_session_id_echo)
               [cs cur] (c/read-u16 cur :cipher_suite)
               [cm cur] (c/read-u8 cur :legacy_compression_method)
               [eb cur] (c/read-vector cur 2 6 65535 :extensions)
               _ (c/end cur :server_hello)]
      (let [hrr? (= rnd hello-retry-request-random)]
        (r/let-ok [exts (ext/parse-block eb (if hrr? :hello-retry-request :server-hello))]
          (cond
            (not= cm 0)
            (error :illegal_parameter :compression-selected {:tls/method cm})
            :else
            (ok {:tls/legacy-version ver
                 :tls/random rnd
                 :tls/session-id-echo sid
                 :tls/cipher-suite cs
                 :tls/extensions exts
                 :tls/hello-retry-request hrr?})))))))

(defn check-server-hello
  "The negotiation checks section 4.1.3 requires a client to make, kept apart
   from parsing so each has a name and each has a test.

   Every one of these is a place where an omitted check is invisible in a
   successful handshake and only shows up under attack:

   - `supported_versions` absent, or not 0x0304 -> this is not TLS 1.3, and a
     client that proceeds on `legacy_version` alone is downgradeable.
   - `legacy_session_id_echo` mismatched -> section 4.1.3 requires abort.
   - a cipher suite the client did not offer -> section 4.1.3 requires abort.
   - a `key_share` group the client did not offer -> nothing to agree with."
  [sh {:keys [session-id offered-suites offered-groups]}]
  (let [exts (:tls/extensions sh)
        sv (ext/find-ext exts :supported_versions)
        ks (ext/find-ext exts :key_share)]
    (cond
      (nil? sv)
      (error :missing_extension :no-supported-versions {})
      (not= 0x0304 (get-in sv [:tls/value :tls/version]))
      (error :protocol_version :not-tls13
             {:tls/version (get-in sv [:tls/value :tls/version])})
      (not= (vec session-id) (vec (:tls/session-id-echo sh)))
      (error :illegal_parameter :session-id-echo-mismatch {})
      (not (contains? (set offered-suites) (:tls/cipher-suite sh)))
      (error :illegal_parameter :cipher-suite-not-offered
             {:tls/selected (:tls/cipher-suite sh)})
      (:tls/hello-retry-request sh)
      (error :handshake_failure :hello-retry-request-not-implemented
             {:tls/group (get-in ks [:tls/value :tls/group])
              :tls/note "the server asked for a different group; this client sends one key share and does not retry"})
      (nil? ks)
      (error :missing_extension :no-key-share {})
      (not (contains? (set offered-groups) (get-in ks [:tls/value :tls/group])))
      (error :illegal_parameter :key-share-group-not-offered
             {:tls/group (get-in ks [:tls/value :tls/group])})
      :else
      (ok {:tls/cipher-suite (:tls/cipher-suite sh)
           :tls/group (get-in ks [:tls/value :tls/group])
           :tls/peer-key-share (get-in ks [:tls/value :tls/key-exchange])}))))

;; --------------------------------------------------- other message bodies

(defn parse-encrypted-extensions [body]
  (let [cur (c/cursor body)]
    (r/let-ok [[eb cur] (c/read-vector cur 2 0 65535 :extensions)
               _ (c/end cur :encrypted_extensions)
               exts (ext/parse-block eb :generic)]
      (ok {:tls/extensions exts}))))

(defn parse-certificate
  "RFC 8446 section 4.4.2.

       opaque certificate_request_context<0..2^8-1>;
       CertificateEntry certificate_list<0..2^24-1>;

   Each entry is `opaque cert_data<1..2^24-1>` followed by its own extension
   block. A non-empty `certificate_request_context` in a server Certificate is
   an error (section 4.4.2: it `SHALL be zero length` unless answering a
   CertificateRequest)."
  [body]
  (let [cur (c/cursor body)]
    (r/let-ok [[ctx cur] (c/read-vector cur 1 0 255 :certificate_request_context)
               [lst cur] (c/read-vector cur 3 0 16777215 :certificate_list)
               _ (c/end cur :certificate)]
      (if (seq ctx)
        (error :illegal_parameter :unsolicited-certificate-context {})
        (loop [cc (c/cursor lst), acc []]
          (if (c/exhausted? cc)
            (if (empty? acc)
              (error :decode_error :empty-certificate-list {})
              (ok {:tls/certificates acc}))
            (let [res (r/let-ok [[der cc] (c/read-vector cc 3 1 16777215 :cert_data)
                                 [eb cc] (c/read-vector cc 2 0 65535 :extensions)]
                        (ok [der eb cc]))]
              (if (r/error? res)
                res
                (let [[der _eb cc] (r/val res)]
                  (recur cc (conj acc der)))))))))))

(defn parse-certificate-verify
  "Section 4.4.3: `SignatureScheme algorithm; opaque signature<0..2^16-1>`."
  [body]
  (let [cur (c/cursor body)]
    (r/let-ok [[alg cur] (c/read-u16 cur :algorithm)
               [sig cur] (c/read-vector cur 2 0 65535 :signature)
               _ (c/end cur :certificate_verify)]
      (ok {:tls/scheme (get ext/signature-scheme-by-code alg alg)
           :tls/scheme-code alg
           :tls/signature sig}))))

(def certificate-verify-context
  "The signed content for a *server* CertificateVerify -- section 4.4.3:

       64 spaces
       \"TLS 1.3, server CertificateVerify\"
       0x00
       Transcript-Hash(Handshake Context, Certificate)

   The sixty-four spaces and the separator are what stop a signature made for
   TLS 1.3 being replayed as a signature over something else, and vice versa.
   They are not padding."
  {:server (vec (concat (repeat 64 0x20)
                        (c/ascii "TLS 1.3, server CertificateVerify")
                        [0]))
   :client (vec (concat (repeat 64 0x20)
                        (c/ascii "TLS 1.3, client CertificateVerify")
                        [0]))})

(defn certificate-verify-content [role transcript-hash]
  (vec (concat (get certificate-verify-context role) (vec transcript-hash))))

(defn parse-finished [body] (ok {:tls/verify-data (vec body)}))

(defn finished [verify-data] (message :finished (vec verify-data)))

(defn parse-new-session-ticket
  "Section 4.6.1. Parsed but not used: this client does not resume. It is here
   because a NewSessionTicket arrives *after* the handshake on almost every
   real server, and a client that cannot parse it reports a protocol error on
   a perfectly good connection."
  [body]
  (let [cur (c/cursor body)]
    (r/let-ok [[lifetime cur] (c/read-u32 cur :ticket_lifetime)
               [age-add cur] (c/read-u32 cur :ticket_age_add)
               [nonce cur] (c/read-vector cur 1 0 255 :ticket_nonce)
               [ticket cur] (c/read-vector cur 2 1 65535 :ticket)
               [eb cur] (c/read-vector cur 2 0 65535 :extensions)
               _ (c/end cur :new_session_ticket)]
      (ok {:tls/lifetime lifetime :tls/age-add age-add
           :tls/nonce nonce :tls/ticket ticket :tls/extension-bytes eb}))))

(defn parse-key-update
  "Section 4.6.3. `update_requested` is 0 or 1 and nothing else -- section
   4.6.3 makes any other value an `illegal_parameter`."
  [body]
  (if (and (= 1 (count body)) (#{0 1} (first body)))
    (ok {:tls/update-requested (= 1 (first body))})
    (error :illegal_parameter :bad-key-update {:tls/body (vec body)})))
