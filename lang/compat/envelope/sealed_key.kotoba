;; envelope.sealed-key — one hybrid wrap of one key, as one byte string.
;;
;; `wrap-bytes-hybrid` returns a map of base64url fields. A protocol that has
;; to put the wrap somewhere — kotobase's recipient-bound disclosure puts it
;; in `:sealed/ciphertext`, an octet vector whose CID the grant commits to —
;; needs those fields as bytes, in an order both ends agree on before either
;; has done any crypto.
;;
;; Both ends are here on purpose. The sealing end is a key service and the
;; opening end is a query client; if each wrote its own framing, the first
;; disagreement would surface as an AEAD failure, which is the same thing a
;; forged wrap looks like.
;;
;;   byte 0        version, currently 1
;;   1    .. 32    X25519 ephemeral public key
;;   33   .. 1120  ML-KEM-768 ciphertext
;;   1121 .. 1132  AES-GCM IV
;;   1133 ..       AES-GCM ciphertext and tag
;;
;; The recipient's own public keys are NOT carried: the recipient has them,
;; and a wrap that tells the opener which key to check itself is a wrap that
;; can be pointed at a different one. They still enter the KEM transcript,
;; so a wrap made for someone else derives a different key and the AEAD
;; refuses — the binding is cryptographic, not a field to compare.
(ns envelope.sealed-key
  (:require [envelope.kem :as kem]
            [envelope.model :as m]
            [envelope.seal :as seal]))

(def version 1)

(def ^:private eph-offset 1)
(def ^:private pq-offset (+ eph-offset 32))
(def ^:private iv-offset (+ pq-offset kem/ciphertext-bytes))
(def ^:private wrapped-offset (+ iv-offset m/nonce-bytes))

(def ^:private min-length
  "Header plus the smallest AEAD output there can be: an empty plaintext is
  still a 16-byte tag."
  (+ wrapped-offset 16))

(defn- concat-bytes [& arrays]
  (let [out (js/Uint8Array. (reduce + 0 (map #(.-length ^js %) arrays)))]
    (reduce (fn [off ^js a] (.set out a off) (+ off (.-length a))) 0 arrays)
    out))

(defn encode
  "Wrap map -> Uint8Array."
  [{:keys [:wrap/kem :wrap/ephemeral-pub :wrap/pq-ct :wrap/iv :wrap/wrapped]}]
  (when-not (= m/hybrid-kem kem)
    (throw (ex-info "only the hybrid wrap has this encoding"
                    {:wrap/kem kem :expected m/hybrid-kem})))
  (concat-bytes (js/Uint8Array. #js [version])
                (seal/unb64url ephemeral-pub)
                (seal/unb64url pq-ct)
                (seal/unb64url iv)
                (seal/unb64url wrapped)))

(defn decode
  "Uint8Array -> wrap map, or a throw.

  Every length is checked before anything is decapsulated. An ingress that
  decodes first and validates later has already spent a KEM operation on
  whatever it was handed, and the sizes here are fixed, so there is no
  reason to."
  [^js bytes recipient-pub]
  (when-not (instance? js/Uint8Array bytes)
    (throw (ex-info "sealed key is not bytes" {:type (type bytes)})))
  (when (< (.-length bytes) min-length)
    (throw (ex-info "sealed key is too short"
                    {:length (.-length bytes) :minimum min-length})))
  (when-not (= version (aget bytes 0))
    (throw (ex-info "unknown sealed-key version"
                    {:version (aget bytes 0) :expected version})))
  {:wrap/kem m/hybrid-kem
   :wrap/pub (seal/b64url recipient-pub)
   :wrap/ephemeral-pub (seal/b64url (.slice bytes eph-offset pq-offset))
   :wrap/pq-ct (seal/b64url (.slice bytes pq-offset iv-offset))
   :wrap/iv (seal/b64url (.slice bytes iv-offset wrapped-offset))
   :wrap/wrapped (seal/b64url (.slice bytes wrapped-offset))})

(defn seal-key
  "Wrap `key-bytes` to a hybrid recipient under `aad`. -> Promise<Uint8Array>."
  [^js key-bytes {:keys [pub pq-pub]} aad]
  (-> (seal/wrap-bytes-hybrid key-bytes {:pub pub :pq-pub pq-pub} aad)
      (.then encode)))

(defn open-key
  "Inverse of `seal-key`. -> Promise<Uint8Array>.

  Rejects rather than throws, including on a malformed frame: a host that
  attaches only `.catch` must fail closed on a bad length the same way it
  does on a bad tag."
  [^js bytes {:keys [pub priv pq-priv]} aad]
  (try (seal/unwrap-bytes-hybrid (decode bytes pub) {:priv priv :pq-priv pq-priv} aad)
       (catch :default e (js/Promise.reject e))))

(defn octets
  "Uint8Array -> the octet vector kotobase's `:sealed/ciphertext` carries.
  A canonical vector of small integers is portable across the CLJ and CLJS
  value codecs; a typed array is not."
  [^js bytes]
  (vec (array-seq bytes)))

(defn from-octets
  [octets]
  (js/Uint8Array.from (clj->js octets)))
