(ns deflate.bits
  "Bit-level I/O for DEFLATE (RFC 1951 §3.1.1).

   Two bit orders coexist in DEFLATE and mixing them up is the classic way to
   produce a stream that only your own decoder can read:

   - **Data elements** (block type, LEN/NLEN, extra bits for length and
     distance, code lengths) are packed **least-significant bit first**.
   - **Huffman codes** are packed **most-significant bit of the code first**.

   `read-bits`/`write-bits!` implement the former, `read-code` (via
   `deflate.huffman`) and `write-code!` the latter.

   The cursor is held in `volatile!` cells rather than threaded as a value:
   this keeps the hot loops allocation-free on both runtimes. It also means
   neither a reader nor a writer is a value — do not share one across
   logical streams.")

;; ---------------------------------------------------------------------------
;; Reader
;; ---------------------------------------------------------------------------

(defn reader
  "A bit reader over `data` (anything `vec`-able of unsigned bytes)."
  [data]
  (let [v (vec data)]
    {:data v :len (count v) :bytepos (volatile! 0) :bitpos (volatile! 0)}))

(defn read-bit
  "Next bit, LSB-first within each byte."
  [r]
  (let [bp @(:bytepos r)
        bi @(:bitpos r)]
    (when (>= bp (:len r))
      (throw (ex-info "deflate: unexpected end of input" {:reason :truncated :bytepos bp})))
    (let [b   (nth (:data r) bp)
          bit (bit-and (unsigned-bit-shift-right b bi) 1)]
      (if (= bi 7)
        (do (vreset! (:bitpos r) 0) (vswap! (:bytepos r) inc))
        (vreset! (:bitpos r) (inc bi)))
      bit)))

(defn read-bits
  "Read `n` bits as an integer, LSB-first."
  [r n]
  (loop [i 0 acc 0]
    (if (= i n)
      acc
      (recur (inc i) (bit-or acc (bit-shift-left (read-bit r) i))))))

(defn align-reader!
  "Discard the remaining bits of the current byte."
  [r]
  (when (pos? @(:bitpos r))
    (vreset! (:bitpos r) 0)
    (vswap! (:bytepos r) inc)))

(defn read-byte!
  "Read one byte; the reader must already be byte-aligned."
  [r]
  (let [bp @(:bytepos r)]
    (when (>= bp (:len r))
      (throw (ex-info "deflate: unexpected end of input" {:reason :truncated :bytepos bp})))
    (vswap! (:bytepos r) inc)
    (nth (:data r) bp)))

(defn reader-pos
  "Byte offset of the reader (rounds up if mid-byte)."
  [r]
  (+ @(:bytepos r) (if (pos? @(:bitpos r)) 1 0)))

(defn reader-remaining [r] (- (:len r) (reader-pos r)))

;; ---------------------------------------------------------------------------
;; Writer
;; ---------------------------------------------------------------------------

(defn writer
  "A bit writer accumulating a vector of unsigned bytes."
  []
  {:out (volatile! (transient [])) :cur (volatile! 0) :nbits (volatile! 0)})

(defn- push-byte! [w b]
  (vswap! (:out w) conj! b))

(defn write-bits!
  "Write the low `n` bits of `value`, LSB-first."
  [w value n]
  (loop [i 0]
    (when (< i n)
      (let [bit   (bit-and (unsigned-bit-shift-right value i) 1)
            nbits @(:nbits w)
            cur   (bit-or @(:cur w) (bit-shift-left bit nbits))]
        (if (= nbits 7)
          (do (push-byte! w cur)
              (vreset! (:cur w) 0)
              (vreset! (:nbits w) 0))
          (do (vreset! (:cur w) cur)
              (vreset! (:nbits w) (inc nbits)))))
      (recur (inc i)))))

(defn write-code!
  "Write a Huffman `code` of `n` bits, most-significant bit of the code first."
  [w code n]
  (when (zero? n)
    (throw (ex-info "deflate: attempt to emit a symbol with no code" {:code code})))
  (loop [i (dec n)]
    (when (>= i 0)
      (write-bits! w (bit-and (unsigned-bit-shift-right code i) 1) 1)
      (recur (dec i)))))

(defn align-writer!
  "Pad with zero bits up to the next byte boundary."
  [w]
  (when (pos? @(:nbits w))
    (push-byte! w @(:cur w))
    (vreset! (:cur w) 0)
    (vreset! (:nbits w) 0)))

(defn write-byte!
  "Write one byte; the writer must already be byte-aligned."
  [w b]
  (push-byte! w (bit-and b 0xff)))

(defn write-u16-le! [w v]
  (write-byte! w (bit-and v 0xff))
  (write-byte! w (bit-and (unsigned-bit-shift-right v 8) 0xff)))

(defn write-u32-le! [w v]
  (write-u16-le! w (bit-and v 0xffff))
  (write-u16-le! w (bit-and (unsigned-bit-shift-right v 16) 0xffff)))

(defn write-u32-be! [w v]
  (write-byte! w (bit-and (unsigned-bit-shift-right v 24) 0xff))
  (write-byte! w (bit-and (unsigned-bit-shift-right v 16) 0xff))
  (write-byte! w (bit-and (unsigned-bit-shift-right v 8) 0xff))
  (write-byte! w (bit-and v 0xff)))

(defn finish!
  "Flush any partial byte and return the written bytes as a vector."
  [w]
  (align-writer! w)
  (persistent! @(:out w)))

(defn bits-written
  "Number of bits written so far (used for block-strategy cost comparison)."
  [w]
  (+ (* 8 (count @(:out w))) @(:nbits w)))
