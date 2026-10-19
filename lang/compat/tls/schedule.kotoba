(ns tls.schedule
  "The TLS 1.3 key schedule -- RFC 8446 section 7 -- and HKDF (RFC 5869).

  Pure `.cljc`. The hash and HMAC arrive through the injected provider, so
  this namespace contains no cryptography of its own: it is the *derivation*,
  and the derivation is where TLS 1.3 puts most of its security argument.

  ## HKDF here is not Noise's HKDF

  `kotoba-lang/noise` has an `hkdf` and it is the wrong one for TLS. Noise
  (rev 34 section 4.3) defines a fixed 2-or-3-output construction whose
  outputs are each exactly one hash length. RFC 5869's `HKDF-Expand` produces
  an arbitrary `L` bytes by counter-chaining, and TLS needs it for 16-byte
  AEAD keys and 12-byte IVs. Reusing Noise's variant would produce plausible
  32-byte values that no peer on earth agrees with, which is why this is
  written out rather than imported.

  ## Every step is checkable

  RFC 8448 prints the `PRK`, the serialized `HkdfLabel` info, and the output
  of every single derivation in a real handshake. The functions here are cut
  at exactly those boundaries -- `hkdf-label` returns the info block on its
  own -- so a test can compare each step to the trace instead of comparing
  only the end and guessing where a mismatch came from."
  (:require [tls.codec :as c]
            [tls.result :as r :refer [ok error]]))

;; ------------------------------------------------------------ hash bundle

(def hash-lengths {:sha256 32 :sha384 48})

(defn hashes
  "Bind a provider and a hash algorithm into the `{:length :hash :hmac}` bundle
   the rest of this namespace uses.

   Returns an error rather than `nil` if the provider does not carry that
   hash. A provider missing SHA-384 and a provider whose SHA-384 returns
   nothing must not look the same to the caller."
  [provider hash-kw]
  (let [f (get-in provider [:hash hash-kw])
        h (:hmac provider)
        len (get hash-lengths hash-kw)]
    (cond
      (nil? len) (error :internal_error :unknown-hash {:tls/hash hash-kw})
      (not (fn? f)) (error :internal_error :provider-missing-hash {:tls/hash hash-kw})
      (not (fn? h)) (error :internal_error :provider-missing-hmac {})
      :else (ok {:tls/hash hash-kw
                 :length len
                 :hash (fn [bs] (vec (f (vec bs))))
                 :hmac (fn [k m] (vec (h hash-kw (vec k) (vec m))))}))))

(defn zeros [h] (vec (repeat (:length h) 0)))

;; ------------------------------------------------------------------- HKDF

(defn hkdf-extract
  "RFC 5869 section 2.2: `HKDF-Extract(salt, IKM) = HMAC-Hash(salt, IKM)`.

   An absent salt becomes `HashLen` zero bytes, per the RFC. That is why
   RFC 8448 prints the early-secret salt as `0 (all zero octets)` and its IKM
   as thirty-two zeros: with no PSK, both inputs are zero-filled."
  [h salt ikm]
  ((:hmac h) (if (seq salt) (vec salt) (zeros h)) (vec ikm)))

(def max-expand-blocks 255)

(defn hkdf-expand
  "RFC 5869 section 2.3, with the `L <= 255 * HashLen` bound enforced.

   The bound matters more than it looks: the counter is one byte, so an
   implementation that does not check does not error -- it wraps to zero and
   begins re-emitting the first block. The failure mode of skipping this check
   is *repeating key material*, silently."
  [h prk info length]
  (let [hash-len (:length h)]
    (cond
      (neg? length) (error :internal_error :negative-length {:tls/length length})
      (> length (* max-expand-blocks hash-len))
      (error :internal_error :hkdf-expand-too-long
             {:tls/length length :tls/max (* max-expand-blocks hash-len)})
      :else
      (ok (loop [i 1, prev [], out []]
            (if (>= (count out) length)
              (subvec out 0 length)
              (let [block ((:hmac h) prk (conj (into (vec prev) (vec info)) i))]
                (recur (inc i) block (into out block)))))))))

;; ------------------------------------------------- HkdfLabel / Derive-Secret

(def label-prefix "tls13 ")

(defn hkdf-label
  "The serialized `HkdfLabel` of RFC 8446 section 7.1:

       struct {
         uint16 length = Length;
         opaque label<7..255> = \"tls13 \" + Label;
         opaque context<0..255> = Context;
       } HkdfLabel;

   Both bounds are enforced. `<7..255>` is what makes an empty label illegal --
   `\"tls13 \"` is exactly six bytes, so the lower bound of seven says the
   caller must contribute at least one."
  [length label context]
  (let [full (c/ascii (str label-prefix label))]
    (r/let-ok [lbl (c/write-vector 1 7 255 :hkdf-label full)
               ctx (c/write-vector 1 0 255 :hkdf-context (vec context))]
      (ok (vec (concat (c/u16 length) lbl ctx))))))

(defn hkdf-expand-label
  "`HKDF-Expand-Label(Secret, Label, Context, Length)` -- section 7.1."
  [h secret label context length]
  (r/let-ok [info (hkdf-label length label context)]
    (hkdf-expand h secret info length)))

(defn derive-secret
  "`Derive-Secret(Secret, Label, Messages)` -- section 7.1, taking the
   transcript *hash* rather than the messages.

   TLS derives some secrets over a transcript and others (`derived`,
   `finished`) over the empty string. Taking the hash makes both the same call
   and makes each one directly comparable to the `hash (N octets):` line
   RFC 8448 prints beside it."
  [h secret label transcript-hash]
  (hkdf-expand-label h secret label transcript-hash (:length h)))

;; -------------------------------------------------------------- the ladder

(defn early-secret
  ([h] (early-secret h nil))
  ([h psk] (hkdf-extract h (zeros h) (or psk (zeros h)))))

(defn derived
  "The `Derive-Secret(., \"derived\", \"\")` step that sits between every
   Extract in section 7.1's diagram."
  [h secret]
  (derive-secret h secret "derived" ((:hash h) [])))

(defn handshake-secret [h early dhe]
  (r/let-ok [salt (derived h early)] (ok (hkdf-extract h salt dhe))))

(defn master-secret [h hs]
  (r/let-ok [salt (derived h hs)] (ok (hkdf-extract h salt (zeros h)))))

(defn traffic-keys
  "`[sender]_write_key` / `[sender]_write_iv` -- section 7.3.

   The two lengths come from the *AEAD*, not the hash: AES-128-GCM is 16/12,
   AES-256-GCM and ChaCha20-Poly1305 are 32/12."
  [h secret {:keys [key-length iv-length] :as suite}]
  (if (or (nil? key-length) (nil? iv-length))
    (error :internal_error :suite-missing-aead-geometry {:tls/suite (:tls/suite suite)})
    (r/let-ok [k (hkdf-expand-label h secret "key" [] key-length)
               iv (hkdf-expand-label h secret "iv" [] iv-length)]
      (ok {:tls/key k :tls/iv iv}))))

(defn finished-key
  "`HKDF-Expand-Label(BaseKey, \"finished\", \"\", Hash.length)` -- 4.4.4."
  [h base-key]
  (hkdf-expand-label h base-key "finished" [] (:length h)))

(defn verify-data
  "`HMAC(finished_key, Transcript-Hash(...))` -- section 4.4.4."
  [h base-key transcript-hash]
  (r/let-ok [fk (finished-key h base-key)]
    (ok ((:hmac h) fk transcript-hash))))

(defn check-finished
  "Verify a peer's Finished, in constant time.

   The alert is `:decrypt_error`, which is what section 4.4.4 names for this
   specific failure -- not the `handshake_failure` a reasonable person would
   guess. Getting it wrong is not fatal to interop but it is a fingerprint."
  [h base-key transcript-hash received]
  (r/let-ok [expected (verify-data h base-key transcript-hash)]
    (if (c/constant-time-eq? expected (vec received))
      (ok true)
      (error :decrypt_error :finished-mismatch {}))))

(defn update-traffic-secret
  "`application_traffic_secret_N+1` -- section 7.2 (KeyUpdate)."
  [h secret]
  (hkdf-expand-label h secret "traffic upd" [] (:length h)))

(defn resumption-psk
  "The PSK a NewSessionTicket names -- section 4.6.1."
  [h res-master ticket-nonce]
  (hkdf-expand-label h res-master "resumption" (vec ticket-nonce) (:length h)))

(defn exporter
  "RFC 8446 section 7.5, two-stage."
  [h exporter-master label context length]
  (r/let-ok [s (derive-secret h exporter-master label ((:hash h) []))]
    (hkdf-expand-label h s "exporter" ((:hash h) (vec context)) length)))
