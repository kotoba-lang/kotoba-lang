(ns kotoba.render.meshopt
  "Pure decoders for `EXT_meshopt_compression` (glTF), ported from
   `kami-render/src/meshopt.rs`. A faithful scalar port of the canonical
   meshoptimizer reference decoders (`vertexcodec.cpp`, `indexcodec.cpp`,
   `vertexfilter.cpp`, zeux/meshoptimizer, MIT) — genuinely portable pure
   CPU byte-transform code with **zero GPU dependency** even in the Rust
   source (the Rust crate only gates this behind the `gltf-loader` Cargo
   feature, not `wgpu-backend`).

   Byte buffers are plain Clojure vectors (or any `nth`-able indexed
   collection) of unsigned ints 0..255, same convention as
   [[kotoba.render.bits]]. Internally this port uses mutable JVM/JS arrays
   (`int-array`/`aset`/`aget`) for the block-decode scratch buffers,
   matching the imperative structure of the reference decoder — this is
   asset-load-time code, not the per-frame hot loop the migration ADR
   restricts to WGSL.

   Ported and validated bit-exact (see `meshopt_test.cljc`) against real
   `zeux/meshoptimizer` C++-encoder output, embedded as fixtures in the
   Rust source: [[decode-vertex-buffer]], [[decode-index-buffer]],
   [[decode-index-sequence]], [[decode-filter-oct]], [[decode-filter-quat]],
   [[decode-filter-exp]].

   NOT ported: `decode_meshopt_glb` — the GLB-container/glTF-JSON
   orchestration that finds `EXT_meshopt_compression` buffer views and
   rewrites the JSON. That's plumbing around this codec (needs a JSON
   library this zero-dep repo doesn't take on), not the codec itself; the
   codec functions above are the actual `EXT_meshopt_compression` port."
  (:require [kotoba.render.bits :as bits]))

(def ^:private vertex-header 0xa0)
(def ^:private decode-vertex-version 1)
(def ^:private vertex-block-size-bytes 8192)
(def ^:private vertex-block-max-size 256)
(def ^:private byte-group-size 16)
(def ^:private byte-group-decode-limit 24)
(def ^:private tail-min-size-v0 32)
(def ^:private tail-min-size-v1 24)
(def ^:private bits-v0 [0 2 4 8])
(def ^:private bits-v1 [0 1 2 4 8])

(defn- meshopt-error [msg type]
  (throw (ex-info msg {:type type})))

(defn- sign-extend
  "Sign-extend the low `bit-width` bits of `x` to a full (Clojure long)
   signed integer, matching Rust's `(v << (32-n)) as i32 >> (32-n)` idiom
   without relying on 32-bit wraparound (Clojure longs are 64-bit)."
  [x bit-width]
  (let [mask (dec (bit-shift-left 1 bit-width))
        sign-bit (bit-shift-left 1 (dec bit-width))
        x (bit-and x mask)]
    (if (not (zero? (bit-and x sign-bit)))
      (bit-or x (bit-not mask))
      x)))

(defn- vertex-block-size [vertex-size]
  (let [result (bit-and (quot vertex-block-size-bytes vertex-size) (bit-not (dec byte-group-size)))]
    (min result vertex-block-max-size)))

(defn- unzigzag32 [v]
  (bit-xor (if (odd? v) 0xffffffff 0) (unsigned-bit-shift-right v 1)))

(defn- reverse-bits8 [x]
  (reduce (fn [r i] (bit-or (bit-shift-left r 1) (bit-and (bit-shift-right x i) 1))) 0 (range 8)))

;; ---------------------------------------------------------------------------
;; Byte-group / byte-stream decode (vertexcodec.cpp decodeBytesGroup/decodeBytes)
;; ---------------------------------------------------------------------------

(defn- decode-bytes-group-0! [^ints out out-off]
  (dotimes [i byte-group-size] (aset out (+ out-off i) (int 0))))

(defn- decode-bytes-group-1! [input pos ^ints out out-off]
  (let [sentinel 1]
    (loop [data pos data-var (+ pos 2) oi 0 byte-val 0]
      (if (= oi byte-group-size)
        data-var
        (let [in-byte-idx (mod oi 8)
              [data' byte0] (if (zero? in-byte-idx)
                               [(inc data) (reverse-bits8 (nth input data))]
                               [data byte-val])
              enc (bit-and (unsigned-bit-shift-right byte0 7) 1)
              byte1 (bit-and (bit-shift-left byte0 1) 0xff)
              encv (nth input data-var)
              [val dv'] (if (= enc sentinel) [encv (inc data-var)] [enc data-var])]
          (aset out (+ out-off oi) (int val))
          (recur data' dv' (inc oi) byte1))))))

(defn- decode-bytes-group-k! [input pos ^ints out out-off k]
  (let [codes-per-byte (quot 8 k)
        sentinel (dec (bit-shift-left 1 k))
        header-bytes (* 2 k)
        shift0 (- 8 k)]
    (loop [data pos data-var (+ pos header-bytes) oi 0 byte-val 0]
      (if (= oi byte-group-size)
        data-var
        (let [in-byte-idx (mod oi codes-per-byte)
              [data' byte0] (if (zero? in-byte-idx) [(inc data) (nth input data)] [data byte-val])
              enc (bit-and (unsigned-bit-shift-right byte0 shift0) sentinel)
              byte1 (bit-and (bit-shift-left byte0 k) 0xff)
              encv (nth input data-var)
              [val dv'] (if (= enc sentinel) [encv (inc data-var)] [enc data-var])]
          (aset out (+ out-off oi) (int val))
          (recur data' dv' (inc oi) byte1))))))

(defn- decode-bytes-group! [input pos ^ints out out-off bits]
  (case (int bits)
    0 (do (decode-bytes-group-0! out out-off) pos)
    1 (decode-bytes-group-1! input pos out out-off)
    2 (decode-bytes-group-k! input pos out out-off 2)
    4 (decode-bytes-group-k! input pos out out-off 4)
    8 (do (dotimes [i byte-group-size] (aset out (+ out-off i) (int (nth input (+ pos i)))))
          (+ pos byte-group-size))
    (meshopt-error "meshopt: bad byte-group width" :malformed)))

(defn- decode-bytes! [input pos ^ints out out-off buffer-size bits-table]
  (let [header-size (quot (+ (quot buffer-size byte-group-size) 3) 4)]
    (when (< (- (count input) pos) header-size)
      (meshopt-error "meshopt: unexpected eof (decode-bytes header)" :unexpected-eof))
    (let [header-start pos]
      (loop [i 0 pos (+ pos header-size)]
        (if (>= i buffer-size)
          pos
          (do
            (when (< (- (count input) pos) byte-group-decode-limit)
              (meshopt-error "meshopt: unexpected eof (decode-bytes group)" :unexpected-eof))
            (let [header-offset (quot i byte-group-size)
                  hb (nth input (+ header-start (quot header-offset 4)))
                  bitsk (bit-and (unsigned-bit-shift-right hb (* (mod header-offset 4) 2)) 3)
                  bits-val (nth bits-table bitsk)
                  pos' (decode-bytes-group! input pos out (+ out-off i) bits-val)]
              (recur (+ i byte-group-size) pos'))))))))

;; ---------------------------------------------------------------------------
;; decodeDeltas1 — transpose 4 parallel byte lanes into vertex layout
;; ---------------------------------------------------------------------------

(defn- decode-deltas1! [^ints buffer ^ints transposed transposed-off vertex-count vertex-size
                         ^ints last-vertex last-vertex-off n xor? rot]
  (let [mask (if (>= n 4) 0xffffffff (dec (bit-shift-left 1 (* 8 n))))]
    (loop [buf-base 0 lv-off last-vertex-off k 0]
      (when (< k 4)
        (let [p0 (loop [p 0 j 0] (if (= j n) p (recur (bit-or p (bit-shift-left (aget last-vertex (+ lv-off j)) (* 8 j))) (inc j))))]
          (loop [i 0 vertex-offset (+ transposed-off k) p p0]
            (if (= i vertex-count)
              nil
              (let [v0 (loop [v 0 j 0] (if (= j n) v (recur (bit-or v (bit-shift-left (aget buffer (+ buf-base i (* vertex-count j))) (* 8 j))) (inc j))))
                    v (bit-and
                       (if xor?
                         (bit-xor (let [r (mod rot 32)]
                                    (bit-and (bit-or (bit-shift-left v0 r) (unsigned-bit-shift-right v0 (- 32 r))) 0xffffffff))
                                  p)
                         (bit-and (+ (unzigzag32 v0) p) 0xffffffff))
                       mask)]
                (dotimes [j n] (aset transposed (+ vertex-offset j) (bit-and (unsigned-bit-shift-right v (* j 8)) 0xff)))
                (recur (inc i) (+ vertex-offset vertex-size) v))))
          (recur (+ buf-base (* vertex-count n)) (+ lv-off n) (+ k n)))))))

;; ---------------------------------------------------------------------------
;; decodeVertexBlock — one block of up to 256 vertices
;; ---------------------------------------------------------------------------

(defn- decode-vertex-block! [input pos ^ints vertex-data vertex-data-off vertex-count vertex-size
                              ^ints last-vertex channels version]
  (let [buffer (int-array (* vertex-block-max-size 4))
        transposed (int-array vertex-block-size-bytes)
        vertex-count-aligned (bit-and (+ vertex-count (dec byte-group-size)) (bit-not (dec byte-group-size)))
        control-size (if (zero? version) 0 (quot vertex-size 4))]
    (when (< (- (count input) pos) control-size)
      (meshopt-error "meshopt: unexpected eof (vertex block control)" :unexpected-eof))
    (let [control-start pos]
      (loop [k 0 pos (+ pos control-size)]
        (if (>= k vertex-size)
          (do
            (dotimes [i (* vertex-count vertex-size)] (aset vertex-data (+ vertex-data-off i) (aget transposed i)))
            (dotimes [i vertex-size] (aset last-vertex i (aget transposed (+ (* vertex-size (dec vertex-count)) i))))
            pos)
          (let [ctrl-byte (if (zero? version) 0 (nth input (+ control-start (quot k 4))))
                pos-after-lanes
                (loop [j 0 pos pos]
                  (if (= j 4)
                    pos
                    (let [ctrl (bit-and (unsigned-bit-shift-right ctrl-byte (* j 2)) 3)
                          lane (* j vertex-count)]
                      (cond
                        (= ctrl 3)
                        (do (when (< (- (count input) pos) vertex-count)
                              (meshopt-error "meshopt: unexpected eof (literal)" :unexpected-eof))
                            (dotimes [i vertex-count] (aset buffer (+ lane i) (int (nth input (+ pos i)))))
                            (recur (inc j) (+ pos vertex-count)))
                        (= ctrl 2)
                        (do (dotimes [i vertex-count] (aset buffer (+ lane i) (int 0)))
                            (recur (inc j) pos))
                        :else
                        (let [tbl (if (zero? version) bits-v0 (subvec bits-v1 ctrl))
                              pos' (decode-bytes! input pos buffer lane vertex-count-aligned tbl)]
                          (recur (inc j) pos'))))))
                channel (if (zero? version) 0 (nth channels (quot k 4)))]
            (case (bit-and channel 3)
              0 (decode-deltas1! buffer transposed k vertex-count vertex-size last-vertex k 1 false 0)
              1 (decode-deltas1! buffer transposed k vertex-count vertex-size last-vertex k 2 false 0)
              2 (let [rot (bit-and (- 32 (unsigned-bit-shift-right channel 4)) 31)]
                  (decode-deltas1! buffer transposed k vertex-count vertex-size last-vertex k 4 true rot))
              (meshopt-error "meshopt: bad channel" :malformed))
            (recur (+ k 4) pos-after-lanes)))))))

(defn decode-vertex-buffer
  "Decode a meshopt-compressed vertex buffer (`mode = ATTRIBUTES`). Returns
   a vector of `vertex-count * vertex-size` unsigned bytes."
  [vertex-count vertex-size data]
  (when (or (zero? vertex-size) (> vertex-size 256) (not (zero? (mod vertex-size 4))))
    (meshopt-error (str "meshopt: unsupported vertex_size=" vertex-size) :unsupported))
  (when (empty? data)
    (meshopt-error "meshopt: unexpected eof" :unexpected-eof))
  (let [header (nth data 0)]
    (when (not= (bit-and header 0xf0) vertex-header)
      (meshopt-error "meshopt: bad vertex header" :bad-vertex-header))
    (let [version (bit-and header 0x0f)]
      (when (> version decode-vertex-version)
        (meshopt-error (str "meshopt: unsupported vertex version " version) :unsupported))
      (let [tail-size (+ vertex-size (if (zero? version) 0 (quot vertex-size 4)))
            tail-size-min (if (zero? version) tail-min-size-v0 tail-min-size-v1)
            tail-size-pad (max tail-size tail-size-min)]
        (when (< (- (count data) 1) tail-size-pad)
          (meshopt-error "meshopt: unexpected eof (tail)" :unexpected-eof))
        (let [tail (- (count data) tail-size)
              last-vertex (int-array 256)
              _ (dotimes [i vertex-size] (aset last-vertex i (int (nth data (+ tail i)))))
              channels (if (zero? version) []
                           (vec (map int (subvec (vec data) (+ tail vertex-size) (+ tail vertex-size (quot vertex-size 4))))))
              block (vertex-block-size vertex-size)
              out (int-array (* vertex-count vertex-size))]
          (loop [vertex-offset 0 pos 1]
            (if (>= vertex-offset vertex-count)
              (do (when (not= (- (count data) pos) tail-size-pad)
                    (meshopt-error "meshopt: malformed (trailing bytes)" :malformed))
                  (vec out))
              (let [block-size (if (< (+ vertex-offset block) vertex-count) block (- vertex-count vertex-offset))
                    pos' (decode-vertex-block! data pos out (* vertex-offset vertex-size) block-size vertex-size
                                                last-vertex channels version)]
                (recur (+ vertex-offset block-size) pos')))))))))

;; ---------------------------------------------------------------------------
;; Index codec (indexcodec.cpp)
;; ---------------------------------------------------------------------------

(def ^:private index-header 0xe0)
(def ^:private sequence-header 0xd0)
(def ^:private decode-index-version 1)

(defn- decode-vbyte [data pos0]
  (let [lead (nth data pos0)]
    (if (< lead 128)
      [lead (inc pos0)]
      (loop [result (bit-and lead 127) shift 7 pos (inc pos0) n 0]
        (let [group (nth data pos)
              pos' (inc pos)
              result' (bit-or result (bit-shift-left (bit-and group 127) shift))]
          (if (or (< group 128) (>= n 3))
            [result' pos']
            (recur result' (+ shift 7) pos' (inc n))))))))

(defn- decode-index [data pos last]
  (let [[v pos'] (decode-vbyte data pos)
        d (unzigzag32 v)]
    [(bit-and (+ last d) 0xffffffff) pos']))

(defn- write-index! [^ints out i index-size value]
  (aset out i (int (if (= index-size 2) (bit-and value 0xffff) (bit-and value 0xffffffff)))))

(defn- write-triangle! [^ints out i index-size a b c]
  (write-index! out i index-size a)
  (write-index! out (inc i) index-size b)
  (write-index! out (+ i 2) index-size c))

(defn- push-edge-fifo! [^ints edge-a ^ints edge-b offset a b]
  (aset edge-a @offset (int a))
  (aset edge-b @offset (int b))
  (vswap! offset #(bit-and (inc %) 15)))

(defn- push-vertex-fifo! [^ints fifo offset v cond]
  (aset fifo @offset (int v))
  (vswap! offset #(bit-and (+ % cond) 15)))

(defn decode-index-buffer
  "Decode a meshopt-compressed triangle index buffer (`mode = TRIANGLES`).
   `index-count` must be a multiple of 3. Returns a vector of decoded
   indices (u32, or masked to u16 range when `index-size` is 2, matching
   `write_index`'s truncation)."
  [index-count index-size buffer]
  (when (not (zero? (mod index-count 3)))
    (meshopt-error "meshopt: index_count must be a multiple of 3" :malformed))
  (when (not (contains? #{2 4} index-size))
    (meshopt-error (str "meshopt: unsupported index_size=" index-size) :unsupported))
  (when (< (count buffer) (+ 1 (quot index-count 3) 16))
    (meshopt-error "meshopt: unexpected eof" :unexpected-eof))
  (when (not= (bit-and (nth buffer 0) 0xf0) index-header)
    (meshopt-error "meshopt: bad index header" :bad-index-header))
  (let [version (bit-and (nth buffer 0) 0x0f)]
    (when (> version decode-index-version)
      (meshopt-error (str "meshopt: unsupported index version " version) :unsupported))
    (let [edge-a (int-array 16 (int -1)) edge-b (int-array 16 (int -1)) vertexfifo (int-array 16 (int -1))
          edgefifooffset (volatile! 0) vertexfifooffset (volatile! 0)
          fecmax (if (>= version 1) 13 15)
          data-safe-end (- (count buffer) 16)
          out (int-array index-count)]
      (loop [i 0 code 1 data (+ 1 (quot index-count 3)) next 0 last 0]
        (if (>= i index-count)
          (do (when (not= data data-safe-end)
                (meshopt-error "meshopt: malformed (trailing index bytes)" :malformed))
              (vec out))
          (do
            (when (> data data-safe-end)
              (meshopt-error "meshopt: malformed (data overrun)" :malformed))
            (let [codetri (nth buffer code)]
              (cond
                ;; --- regular path: reconstruct third vertex from an edge ---
                (< codetri 0xf0)
                (let [fe (unsigned-bit-shift-right codetri 4)
                      idx0 (bit-and (- @edgefifooffset (+ 1 fe)) 15)
                      a (aget edge-a idx0) b (aget edge-b idx0)
                      fec (bit-and codetri 15)
                      [c next' last' data']
                      (if (< fec fecmax)
                        (let [cf (aget vertexfifo (bit-and (- @vertexfifooffset (+ 1 fec)) 15))
                              c (if (zero? fec) next cf)
                              fec0 (if (zero? fec) 1 0)]
                          (push-vertex-fifo! vertexfifo vertexfifooffset c fec0)
                          [c (+ next fec0) last data])
                        (if (not= fec 15)
                          (let [c (bit-and (+ last (- fec (bit-xor fec 3))) 0xffffffff)]
                            (push-vertex-fifo! vertexfifo vertexfifooffset c 1)
                            [c next c data])
                          (let [[c data2] (decode-index buffer data last)]
                            (push-vertex-fifo! vertexfifo vertexfifooffset c 1)
                            [c next c data2])))]
                  (push-edge-fifo! edge-a edge-b edgefifooffset c b)
                  (push-edge-fifo! edge-a edge-b edgefifooffset a c)
                  (write-triangle! out i index-size a b c)
                  (recur (+ i 3) (inc code) data' next' last'))

                ;; --- fast path: codeaux from the fixed table near buffer end ---
                (< codetri 0xfe)
                (let [codeaux (nth buffer (+ data-safe-end (bit-and codetri 15)))
                      feb (unsigned-bit-shift-right codeaux 4)
                      fec (bit-and codeaux 15)
                      a next
                      next1 (inc next)
                      bf (aget vertexfifo (bit-and (- @vertexfifooffset feb) 15))
                      b (if (zero? feb) next1 bf)
                      feb0 (if (zero? feb) 1 0)
                      next2 (+ next1 feb0)
                      cf (aget vertexfifo (bit-and (- @vertexfifooffset fec) 15))
                      c (if (zero? fec) next2 cf)
                      fec0 (if (zero? fec) 1 0)
                      next3 (+ next2 fec0)]
                  (write-triangle! out i index-size a b c)
                  (push-vertex-fifo! vertexfifo vertexfifooffset a 1)
                  (push-vertex-fifo! vertexfifo vertexfifooffset b feb0)
                  (push-vertex-fifo! vertexfifo vertexfifooffset c fec0)
                  (push-edge-fifo! edge-a edge-b edgefifooffset b a)
                  (push-edge-fifo! edge-a edge-b edgefifooffset c b)
                  (push-edge-fifo! edge-a edge-b edgefifooffset a c)
                  (recur (+ i 3) (inc code) data next3 last))

                ;; --- slow path: full codeaux byte + explicit indices ---
                :else
                (let [codeaux (nth buffer data)
                      data1 (inc data)
                      fea (if (= codetri 0xfe) 0 15)
                      feb (unsigned-bit-shift-right codeaux 4)
                      fec (bit-and codeaux 15)
                      next0 (if (zero? codeaux) 0 next)
                      [a next1] (if (zero? fea) [next0 (inc next0)] [0 next0])
                      [b next2] (if (zero? feb) [next1 (inc next1)]
                                    [(aget vertexfifo (bit-and (- @vertexfifooffset feb) 15)) next1])
                      [c next3] (if (zero? fec) [next2 (inc next2)]
                                    [(aget vertexfifo (bit-and (- @vertexfifooffset fec) 15)) next2])
                      [a data2 last2] (if (= fea 15) (let [[v d] (decode-index buffer data1 last)] [v d v]) [a data1 last])
                      [b data3 last3] (if (= feb 15) (let [[v d] (decode-index buffer data2 last2)] [v d v]) [b data2 last2])
                      [c data4 last4] (if (= fec 15) (let [[v d] (decode-index buffer data3 last3)] [v d v]) [c data3 last3])]
                  (write-triangle! out i index-size a b c)
                  (push-vertex-fifo! vertexfifo vertexfifooffset a 1)
                  (push-vertex-fifo! vertexfifo vertexfifooffset b (if (or (zero? feb) (= feb 15)) 1 0))
                  (push-vertex-fifo! vertexfifo vertexfifooffset c (if (or (zero? fec) (= fec 15)) 1 0))
                  (push-edge-fifo! edge-a edge-b edgefifooffset b a)
                  (push-edge-fifo! edge-a edge-b edgefifooffset c b)
                  (push-edge-fifo! edge-a edge-b edgefifooffset a c)
                  (recur (+ i 3) (inc code) data4 next3 last4))))))))))

(defn decode-index-sequence
  "Decode a meshopt-compressed index sequence (`mode = INDICES`). Returns a
   vector of `index-count` decoded indices."
  [index-count index-size buffer]
  (when (not (contains? #{2 4} index-size))
    (meshopt-error (str "meshopt: unsupported index_size=" index-size) :unsupported))
  (when (< (count buffer) (+ 1 index-count 4))
    (meshopt-error "meshopt: unexpected eof" :unexpected-eof))
  (when (not= (bit-and (nth buffer 0) 0xf0) sequence-header)
    (meshopt-error "meshopt: bad index header" :bad-index-header))
  (let [version (bit-and (nth buffer 0) 0x0f)]
    (when (> version decode-index-version)
      (meshopt-error (str "meshopt: unsupported seq version " version) :unsupported))
    (let [data-safe-end (- (count buffer) 4)
          out (int-array index-count)]
      (loop [i 0 data 1 last0 0 last1 0]
        (if (>= i index-count)
          (do (when (not= data data-safe-end)
                (meshopt-error "meshopt: malformed (trailing seq bytes)" :malformed))
              (vec out))
          (do
            (when (>= data data-safe-end)
              (meshopt-error "meshopt: malformed (seq data overrun)" :malformed))
            (let [[v data'] (decode-vbyte buffer data)
                  current (bit-and v 1)
                  v2 (unsigned-bit-shift-right v 1)
                  d (unzigzag32 v2)
                  last-cur (if (zero? current) last0 last1)
                  idx (bit-and (+ last-cur d) 0xffffffff)]
              (write-index! out i index-size idx)
              (if (zero? current)
                (recur (inc i) data' idx last1)
                (recur (inc i) data' last0 idx)))))))))

;; ---------------------------------------------------------------------------
;; Vertex filters (vertexfilter.cpp) — applied after vertex decode
;; ---------------------------------------------------------------------------

(defn- round-f2i [x] (long (+ x (if (>= x 0.0) 0.5 -0.5))))

(defn decode-filter-oct
  "`OCTAHEDRAL` filter — decode octahedral-encoded normals/tangents in
   place. `data` is a byte vector; `stride` 4 → i8 components (per-4-byte
   group), `stride` 8 → i16 components (per-8-byte group). Returns the
   filtered byte vector (same length as `data`)."
  [data count stride]
  (let [out (int-array (vec data))]
    (if (= stride 4)
      (let [maxv 127.0]
        (dotimes [i count]
          (let [base (* i 4)
                to-i8 (fn [b] (if (>= b 128) (- b 256) b))
                x0 (double (to-i8 (aget out base)))
                y0 (double (to-i8 (aget out (+ base 1))))
                z0 (- (double (to-i8 (aget out (+ base 2)))) (Math/abs x0) (Math/abs y0))
                t (if (>= z0 0.0) 0.0 z0)
                x (+ x0 (if (>= x0 0.0) t (- t)))
                y (+ y0 (if (>= y0 0.0) t (- t)))
                l (Math/sqrt (+ (* x x) (* y y) (* z0 z0)))
                s (/ maxv l)]
            (aset out base (bit-and (round-f2i (* x s)) 0xff))
            (aset out (+ base 1) (bit-and (round-f2i (* y s)) 0xff))
            (aset out (+ base 2) (bit-and (round-f2i (* z0 s)) 0xff)))))
      (let [maxv 32767.0]
        (dotimes [i count]
          (let [base (* i 8)
                rd (fn [o] (double (bits/i16-le data (+ base o))))
                x0 (rd 0) y0 (rd 2)
                z0 (- (rd 4) (Math/abs x0) (Math/abs y0))
                t (if (>= z0 0.0) 0.0 z0)
                x (+ x0 (if (>= x0 0.0) t (- t)))
                y (+ y0 (if (>= y0 0.0) t (- t)))
                l (Math/sqrt (+ (* x x) (* y y) (* z0 z0)))
                s (/ maxv l)
                wr (fn [o v] (let [iv (bit-and (round-f2i v) 0xffff)]
                                (aset out (+ base o) (bit-and iv 0xff))
                                (aset out (+ base o 1) (bit-and (unsigned-bit-shift-right iv 8) 0xff))))]
            (wr 0 (* x s)) (wr 2 (* y s)) (wr 4 (* z0 s))))))
    (vec out)))

(defn decode-filter-quat
  "`QUATERNION` filter — decode compact quaternion encoding in place.
   `stride` must be 8 (4 x i16). Returns the filtered byte vector."
  [data count _stride]
  (let [out (int-array (vec data))
        scale (/ 32767.0 (Math/sqrt 2.0))
        rd (fn [o] (bits/i16-le data o))]
    (dotimes [i count]
      (let [base (* i 8)
            c3 (rd (+ base 6))
            sf (bit-or c3 3)
            s (double sf)
            x (double (rd base)) y (double (rd (+ base 2))) z (double (rd (+ base 4)))
            ws (* s s)
            ww (- (* ws 2.0) (* x x) (* y y) (* z z))
            w (Math/sqrt (max 0.0 ww))
            ss (/ scale s)
            xf (round-f2i (* x ss)) yf (round-f2i (* y ss)) zf (round-f2i (* z ss))
            wf (long (+ (* w ss) 0.5))
            qc (bit-and c3 3)
            put (fn [comp v] (let [o (+ base (* comp 2)) iv (bit-and v 0xffff)]
                                (aset out o (bit-and iv 0xff))
                                (aset out (inc o) (bit-and (unsigned-bit-shift-right iv 8) 0xff))))]
        (put (bit-and (+ qc 1) 3) xf)
        (put (bit-and (+ qc 2) 3) yf)
        (put (bit-and (+ qc 3) 3) zf)
        (put (bit-and qc 3) wf)))
    (vec out)))

(defn decode-filter-exp
  "`EXPONENTIAL` filter — decode shared-exponent fixed point into IEEE754
   f32 bytes, in place. `stride` must be a multiple of 4; operates on
   `count * stride/4` u32 words. Returns the filtered byte vector."
  [data count stride]
  (let [out (int-array (vec data))
        words (* count (quot stride 4))]
    (dotimes [i words]
      (let [o (* i 4)
            v (bits/u32-le data o)
            m (sign-extend v 24) ;; sign-extend 24-bit mantissa
            e (sign-extend (unsigned-bit-shift-right v 24) 8) ;; signed exponent (top byte)
            ui (bit-and (bit-shift-left (+ e 127) 23) 0xffffffff)
            f (* (bits/i32-bits->f32 ui) (double m))
            bits32 (bits/f32-bits f)]
        (aset out o (bit-and bits32 0xff))
        (aset out (inc o) (bit-and (unsigned-bit-shift-right bits32 8) 0xff))
        (aset out (+ o 2) (bit-and (unsigned-bit-shift-right bits32 16) 0xff))
        (aset out (+ o 3) (bit-and (unsigned-bit-shift-right bits32 24) 0xff))))
    (vec out)))
