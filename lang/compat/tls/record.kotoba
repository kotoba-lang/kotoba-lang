(ns tls.record
  "The TLS 1.3 record layer -- RFC 8446 section 5.

  Three shapes, and keeping them distinct is most of the work:

      TLSPlaintext      what an unprotected record looks like on the wire
      TLSInnerPlaintext content || real_content_type || zero padding
      TLSCiphertext     opaque_type=23, version=0x0303, AEAD(TLSInnerPlaintext)

  The content type of a protected record is *inside* the ciphertext. The
  `23` in the header is a decoy, and a reader that trusts it thinks every
  protected handshake message is application data.

  ## The nonce is the dangerous part

  Section 5.3: pad the 64-bit sequence number on the left to the IV length,
  XOR with the static IV. Nothing about that is negotiated, nothing is
  transmitted, and if the two sides disagree the failure is a tag mismatch
  with no diagnostic. Worse, if *one* side repeats a nonce under one key, the
  Poly1305/GHASH authentication key is recoverable and confidentiality of both
  records is gone. So this namespace treats the sequence number as a
  correctness-critical value: it refuses above 2^53-1 (see
  `tls.codec/max-exact-integer`), rather than letting ClojureScript round it.

  ## Nothing here decides trust

  `open` returns a plaintext or an error. It never returns both, and it never
  returns a plaintext it could not authenticate."
  (:require [tls.codec :as c]
            [tls.result :as r :refer [ok error]]))

(def content-types
  {:invalid 0 :change_cipher_spec 20 :alert 21 :handshake 22 :application_data 23})

(def ^:private content-type-by-code (into {} (map (fn [[k v]] [v k])) content-types))

(def legacy-record-version [0x03 0x03])

(def max-plaintext
  "2^14. RFC 8446 section 5.1: `TLSPlaintext.length MUST NOT exceed 2^14`."
  16384)

(def max-ciphertext
  "2^14 + 256. Section 5.2: the extra 256 covers the content-type byte, the
   AEAD expansion and any padding. A record longer than this is a
   `record_overflow` and must be refused **before** it is decrypted, so a peer
   cannot make this process allocate an arbitrary buffer by lying in a header."
  (+ 16384 256))

(defn nonce
  "Section 5.3. `iv` is the static write IV; `seq` the record sequence number.

   The padded sequence number is XORed into the *right-hand* end of the IV.
   Left-padding is what makes the leading IV bytes constant, and getting the
   direction wrong produces a nonce that is valid-looking and wrong for every
   record."
  [iv seq]
  (if (> seq c/max-exact-integer)
    (error :internal_error :sequence-number-not-exact
           {:tls/sequence seq :tls/max c/max-exact-integer})
    (let [iv (vec iv)
          padded (into (vec (repeat (- (count iv) 8) 0)) (c/u64 seq))]
      (ok (mapv bit-xor iv padded)))))

;; ------------------------------------------------------------- unprotected

(defn plaintext-record
  "A TLSPlaintext record: type, legacy version, uint16 length, fragment.

   Used for the ClientHello and (as a peer) the ServerHello -- the only two
   records in a 1-RTT handshake that are not protected."
  [content-type fragment]
  (let [n (count fragment)]
    (cond
      (nil? (content-types content-type))
      (error :internal_error :unknown-content-type {:tls/content-type content-type})
      (> n max-plaintext)
      (error :record_overflow :plaintext-too-long {:tls/length n :tls/max max-plaintext})
      :else
      (ok (vec (concat [(content-types content-type)] legacy-record-version
                       (c/u16 n) (vec fragment)))))))

(defn parse-record
  "Split one record off the front of `bytes`.

   -> `[:ok {:tls/content-type k :tls/fragment bytes :tls/rest bytes}]`.
   The length bound is checked against `max-ciphertext` for a protected record
   and `max-plaintext` otherwise -- checked from the *header*, before the body
   is read, which is the only ordering that bounds the allocation."
  [bytes]
  (let [cur (c/cursor bytes)]
    (r/let-ok [[t cur] (c/read-u8 cur :content-type)
               [_v cur] (c/read-bytes cur 2 :legacy-record-version)
               [n cur] (c/read-u16 cur :length)]
      (let [kind (content-type-by-code t)
            limit (if (= kind :application_data) max-ciphertext max-plaintext)]
        (cond
          (nil? kind) (error :unexpected_message :unknown-content-type {:tls/code t})
          (> n limit) (error :record_overflow :record-too-long
                             {:tls/length n :tls/max limit :tls/content-type kind})
          :else
          (r/let-ok [[frag cur] (c/read-bytes cur n :fragment)]
            (ok {:tls/content-type kind
                 :tls/fragment frag
                 :tls/rest (subvec (:bytes cur) (:pos cur))})))))))

;; --------------------------------------------------------------- protected

(defn inner-plaintext
  "TLSInnerPlaintext -- section 5.2: content, then the real content type, then
   `padding` zero bytes."
  ([content content-type] (inner-plaintext content content-type 0))
  ([content content-type padding]
   (if (nil? (content-types content-type))
     (error :internal_error :unknown-content-type {:tls/content-type content-type})
     (ok (vec (concat (vec content) [(content-types content-type)] (repeat padding 0)))))))

(defn strip-padding
  "Section 5.4: scan back from the end for the last non-zero byte; that byte is
   the real content type.

   An all-zero inner plaintext has no content type at all. The RFC says the
   receiver `MUST` send `unexpected_message` -- so this refuses rather than
   defaulting to `:application_data`, which is the tempting shortcut and is
   how a zero-length record gets processed as data."
  [inner]
  (let [i (loop [i (dec (count inner))]
            (cond (neg? i) nil
                  (zero? (nth inner i)) (recur (dec i))
                  :else i))]
    (if (nil? i)
      (error :unexpected_message :all-zero-inner-plaintext {})
      (let [kind (content-type-by-code (nth inner i))]
        (cond
          (nil? kind) (error :unexpected_message :unknown-content-type {:tls/code (nth inner i)})
          (= kind :change_cipher_spec)
          ;; Section 5.4: change_cipher_spec MUST NOT be an encrypted record's
          ;; content type. It is legal only as an unprotected middlebox-
          ;; compatibility record, and accepting it here would let a peer
          ;; smuggle one inside the protected stream.
          (error :unexpected_message :encrypted-change-cipher-spec {})
          :else (ok {:tls/content-type kind :tls/content (subvec (vec inner) 0 i)}))))))

(defn- additional-data
  "The AAD for a protected record is its own five-byte header (section 5.2).
   `length` is the length of the *ciphertext*, tag included."
  [length]
  (vec (concat [(content-types :application_data)] legacy-record-version (c/u16 length))))

(defn seal
  "Protect one record.

     (seal suite {:key .. :iv ..} seq :handshake fragment)  ; padding optional

   -> `[:ok complete-record-bytes]`."
  ([suite keys seq content-type fragment] (seal suite keys seq content-type fragment 0))
  ([suite {:keys [tls/key tls/iv]} seq content-type fragment padding]
   (r/let-ok [inner (inner-plaintext fragment content-type padding)
              nce (nonce iv seq)]
     (let [ct-len (+ (count inner) (:tag-length suite))]
       (if (> ct-len max-ciphertext)
         (error :record_overflow :ciphertext-too-long
                {:tls/length ct-len :tls/max max-ciphertext})
         (let [aad (additional-data ct-len)
               ct (vec ((:aead-seal suite) key nce aad inner))]
           (if (not= (count ct) ct-len)
             ;; The injected AEAD disagreed with the tag length this suite
             ;; declared. Refusing beats emitting a record whose header lies
             ;; about its own body -- and beats "it worked in testing".
             (error :internal_error :aead-length-mismatch
                    {:tls/expected ct-len :tls/actual (count ct)})
             (ok (vec (concat aad ct))))))))))

(defn open
  "Unprotect one record. `record` is the complete record including its header.

   -> `[:ok {:tls/content-type k :tls/content bytes}]`, or an error. On
   authentication failure there is no plaintext in the result and none in the
   error data -- `:bad_record_mac` and nothing else."
  [suite {:keys [tls/key tls/iv]} seq record]
  (let [cur (c/cursor record)]
    (r/let-ok [[t cur] (c/read-u8 cur :opaque-type)
               [_v cur] (c/read-bytes cur 2 :legacy-record-version)
               [n cur] (c/read-u16 cur :length)]
      (cond
        (not= t (content-types :application_data))
        (error :unexpected_message :protected-record-wrong-opaque-type {:tls/code t})
        (> n max-ciphertext)
        (error :record_overflow :record-too-long {:tls/length n :tls/max max-ciphertext})
        (< n (inc (:tag-length suite)))
        ;; Shorter than tag+1 cannot hold even an empty inner plaintext.
        (error :bad_record_mac :ciphertext-too-short
               {:tls/length n :tls/min (inc (:tag-length suite))})
        :else
        (r/let-ok [[ct cur] (c/read-bytes cur n :encrypted-record)
                   _ (c/end cur :record)
                   nce (nonce iv seq)]
          (let [aad (additional-data n)
                res ((:aead-open suite) key nce aad ct)]
            ;; The provider contract is `[:ok pt] | [:error reason]`. Anything
            ;; else -- nil, a bare vector, a thrown value caught upstream -- is
            ;; treated as failure. A provider that returns a plaintext in a
            ;; shape this does not recognise must not have it used.
            (if (and (vector? res) (= :ok (first res)) (some? (second res)))
              (strip-padding (vec (second res)))
              (error :bad_record_mac :aead-authentication-failed {}))))))))
