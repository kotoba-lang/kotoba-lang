(ns webauthn.der
  "Minimal ASN.1 DER decode for one shape only: an ECDSA signature
  (SEQUENCE of two INTEGERs, r and s). WebAuthn assertion signatures are
  DER-encoded; Web Crypto's `crypto.subtle.verify {:name \"ECDSA\" ...}`
  requires the *raw* IEEE P1363 form instead (r and s each left-padded to
  the curve's coordinate size, concatenated) — this bridges the two, for
  `webauthn.adapters.edge`. No other ASN.1 shape is supported; this is not a
  general DER parser, and it should not become one: every additional shape it
  accepts is another parser exposed to unauthenticated input.

  EXTRACTED (2026-07-25) verbatim from cloud-itonami.edge.der on its 2nd
  consumer, the same rule that moved the cacao edge namespaces to a shared
  home. Every bounds check below is load-bearing — an assertion signature is
  fully attacker-controlled, and `aget` past the end of a Uint8Array yields
  `undefined` rather than throwing.

  CLJS-only (js/Uint8Array interop, no JVM branch)."
  (:refer-clojure :exclude [read]))

(defn- read-length [bytes i]
  (when (>= i (aget bytes "length"))
    (throw (js/Error. "der->raw: unexpected end of input reading length")))
  (let [b (aget bytes i)]
    (if (< b 0x80)
      [b (inc i)]
      (let [n (bit-and b 0x7f)]
        (when (> (+ i 1 n) (aget bytes "length"))
          (throw (js/Error. "der->raw: unexpected end of input reading multi-byte length")))
        (loop [k 0 len 0 j (inc i)]
          (if (< k n)
            (recur (inc k) (+ (* len 256) (aget bytes j)) (inc j))
            [len j]))))))

(defn- read-integer [bytes i]
  (when (not= (aget bytes i) 0x02)
    (throw (js/Error. (str "der->raw: expected INTEGER tag (0x02) at offset " i))))
  (let [[len j] (read-length bytes (inc i))]
    ;; DER's minimal-encoding rule requires at least one content octet --
    ;; 0x02 0x00 (a zero-length INTEGER) isn't valid DER at all (the
    ;; shortest legal encoding of zero is 0x02 0x01 0x00). Without this
    ;; check, left-pad below silently turns a zero-length value into an
    ;; all-zero r/s component -- ECDSA verify would reject that
    ;; mathematically (r=0/s=0 can never be a valid signature), but this
    ;; parser should reject the malformed DER itself, not rely on the
    ;; crypto layer downstream to happen to catch it.
    (when (zero? len)
      (throw (js/Error. "der->raw: zero-length INTEGER is not valid DER")))
    (when (> (+ j len) (aget bytes "length"))
      (throw (js/Error. "der->raw: unexpected end of input reading INTEGER value")))
    (let [val-bytes (.slice bytes j (+ j len))]
      [val-bytes (+ j len)])))

(defn- strip-leading-zero [bytes]
  (if (and (> (aget bytes "length") 1) (zero? (aget bytes 0)))
    (.slice bytes 1)
    bytes))

(defn- left-pad [bytes size]
  (let [n (aget bytes "length")]
    (cond
      (= n size) bytes
      (> n size) (throw (js/Error. (str "der->raw: integer longer than " size " bytes after stripping sign byte")))
      :else (let [out (js/Uint8Array. size)]
              (.set out bytes (- size n))
              out))))

(defn raw
  "DER-encoded ECDSA signature (js/Uint8Array) -> raw r||s (js/Uint8Array,
  2*coord-size bytes, default coord-size 32 for P-256). Throws on anything
  that isn't a well-formed SEQUENCE of exactly two INTEGERs."
  ([der-bytes] (raw der-bytes 32))
  ([der-bytes coord-size]
   (when (not= (aget der-bytes 0) 0x30)
     (throw (js/Error. "der->raw: expected SEQUENCE tag (0x30)")))
   (let [[_seq-len i] (read-length der-bytes 1)
         [r-bytes i2] (read-integer der-bytes i)
         [s-bytes _i3] (read-integer der-bytes i2)
         r (left-pad (strip-leading-zero r-bytes) coord-size)
         s (left-pad (strip-leading-zero s-bytes) coord-size)
         out (js/Uint8Array. (* 2 coord-size))]
     (.set out r 0)
     (.set out s coord-size)
     out)))
