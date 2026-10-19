(ns kotodama.inference.ggml-kdot
  "Scalar reference port of ggml's K-quant matvec contract.

  ggml does not dequantize Q4_K/Q6_K weights and multiply them by an F32
  activation. It first quantizes each 256-value activation block to Q8_K, then
  evaluates Q4_K×Q8_K or Q6_K×Q8_K with a specific integer reduction and
  float32 accumulation order. These functions mirror the generic C kernels;
  optimized/native backends can replace them while retaining this oracle."
  (:require [kotodama.inference.gguf :as gguf]))

(def ^:const qk-k 256)
(def ^:const q4-k-block-bytes 144)
(def ^:const q6-k-block-bytes 210)

(defn- f32 [x] (float x))
(defn- u8 ^long [^bytes b i] (bit-and 0xff (long (aget b i))))
(defn- i8 ^long [^bytes b i]
  (let [x (u8 b i)] (if (> x 127) (- x 256) x)))
(defn- le-u16 ^long [^bytes b i]
  (+ (u8 b i) (bit-shift-left (u8 b (inc i)) 8)))

(defn- nearest-int
  "Bit-exact port of ggml-quants.c nearest_int for its supported range."
  ^long [x]
  (let [v (f32 (+ (f32 x) (f32 12582912.0)))
        bits (Float/floatToRawIntBits v)]
    (- (bit-and bits 0x007fffff) 0x00400000)))

(defn quantize-q8-k-block
  "Quantize exactly 256 values using ggml quantize_row_q8_K_ref."
  [values]
  (when-not (= qk-k (count values))
    (throw (ex-info "Q8_K block requires 256 values" {:count (count values)})))
  (let [^floats xs (float-array values)
        [max-value amax]
        (loop [j (int 0) max-value (f32 0.0) amax (f32 0.0)]
          (if (< j qk-k)
            (let [x (aget xs j)
                  ax (f32 (Math/abs x))]
              (if (> ax amax)
                (recur (unchecked-inc-int j) x ax)
                (recur (unchecked-inc-int j) max-value amax)))
            [max-value amax]))
        ^bytes qs (byte-array qk-k)
        ^shorts bsums (short-array 16)]
    (if (zero? amax)
      {:d (f32 0.0) :qs qs :bsums bsums}
      (let [iscale (f32 (/ (f32 -127.0) max-value))]
        (dotimes [j qk-k]
          (let [v (min 127 (nearest-int (f32 (* iscale (aget xs j)))))]
            (aset-byte qs j (unchecked-byte v))))
        (dotimes [j 16]
          (let [base (* j 16)
                sum (loop [i (int 0) sum (long 0)]
                      (if (< i 16)
                        (recur (unchecked-inc-int i) (+ sum (long (aget qs (+ base i)))))
                        sum))]
            (aset-short bsums j (unchecked-short sum))))
        {:d (f32 (/ (f32 1.0) iscale)) :qs qs :bsums bsums}))))

(defn quantize-q8-k
  "Quantize a vector whose length is divisible by 256 into Q8_K blocks."
  [values]
  (let [v (vec values)]
    (when-not (zero? (mod (count v) qk-k))
      (throw (ex-info "Q8_K row length must be divisible by 256" {:count (count v)})))
    (mapv #(quantize-q8-k-block (subvec v % (+ % qk-k)))
          (range 0 (count v) qk-k))))

(defn q8-k-block-bytes
  "Serialize a Q8_K block in ggml struct layout (little endian)."
  [{:keys [d ^bytes qs ^shorts bsums]}]
  (let [out (byte-array 292)
        bits (Float/floatToRawIntBits (f32 d))]
    (dotimes [i 4] (aset-byte out i (unchecked-byte (bit-and 0xff (bit-shift-right bits (* 8 i))))))
    (System/arraycopy qs 0 out 4 qk-k)
    (dotimes [i 16]
      (let [v (bit-and 0xffff (long (aget bsums i)))
            o (+ 260 (* i 2))]
        (aset-byte out o (unchecked-byte (bit-and v 0xff)))
        (aset-byte out (inc o) (unchecked-byte (bit-shift-right v 8)))))
    out))

(defn- q4-scale ^long [^bytes b scales-off j]
  (if (< j 4)
    (bit-and (u8 b (+ scales-off j)) 63)
    (bit-or (bit-and (u8 b (+ scales-off j 4)) 0x0f)
            (bit-shift-left (bit-shift-right (u8 b (+ scales-off (- j 4))) 6) 4))))

(defn- q4-min ^long [^bytes b scales-off j]
  (if (< j 4)
    (bit-and (u8 b (+ scales-off j 4)) 63)
    (bit-or (bit-shift-right (u8 b (+ scales-off j 4)) 4)
            (bit-shift-left (bit-shift-right (u8 b (+ scales-off j)) 6) 4))))

(defn dot-q4-k-q8-k
  "Bit/reduction-order port of ggml_vec_dot_q4_K_q8_K_generic."
  [^bytes q4-row q8-blocks]
  (let [nb (count q8-blocks)]
    (when-not (= (alength q4-row) (* nb q4-k-block-bytes))
      (throw (ex-info "Q4_K row/block count mismatch"
                      {:row-bytes (alength q4-row) :q8-blocks nb})))
    (let [^floats sums (float-array 8)]
      (loop [block (int 0) sumf (f32 0.0)]
        (if (< block nb)
          (let [bo (* block q4-k-block-bytes)
                scales-off (+ bo 4)
                qs-off (+ bo 16)
                {:keys [d ^bytes qs ^shorts bsums]} (nth q8-blocks block)
                ^bytes aux (byte-array qk-k)
                ^ints aux32 (int-array 8)]
            (dotimes [group 4]
              (let [qbase (+ qs-off (* group 32))
                    abase (* group 64)]
                (dotimes [l 32]
                  (let [q (u8 q4-row (+ qbase l))]
                    (aset-byte aux (+ abase l) (unchecked-byte (bit-and q 0x0f)))
                    (aset-byte aux (+ abase 32 l) (unchecked-byte (bit-shift-right q 4)))))))
            (let [sumi (loop [j (int 0) s (long 0)]
                         (if (< j 16)
                           (recur (unchecked-inc-int j)
                                  (+ s (* (long (aget bsums j))
                                          (q4-min q4-row scales-off (quot j 2)))))
                           s))]
              (dotimes [j 8]
                (let [scale (q4-scale q4-row scales-off j)
                      base (* j 32)]
                  (dotimes [chunk 4]
                    (dotimes [lane 8]
                      (let [i (+ base (* chunk 8) lane)]
                        (aset-int aux32 lane
                                  (+ (aget aux32 lane)
                                     (* scale (long (aget qs i)) (long (aget aux i))))))))))
              (let [block-d (f32 (* (f32 (gguf/fp16->double (le-u16 q4-row bo))) (f32 d)))
                    block-dmin (f32 (* (f32 (gguf/fp16->double (le-u16 q4-row (+ bo 2)))) (f32 d)))]
                (dotimes [lane 8]
                  (aset-float sums lane
                              (f32 (+ (aget sums lane)
                                      (f32 (* block-d (aget aux32 lane)))))))
                (recur (unchecked-inc-int block)
                       (f32 (- sumf (f32 (* block-dmin sumi))))))))
          (double
           (loop [lane (int 0) s sumf]
             (if (< lane 8)
               (recur (unchecked-inc-int lane) (f32 (+ s (aget sums lane))))
               s))))))))

(defn dot-q6-k-q8-k
  "Bit/reduction-order port of ggml_vec_dot_q6_K_q8_K_generic."
  [^bytes q6-row q8-blocks]
  (let [nb (count q8-blocks)]
    (when-not (= (alength q6-row) (* nb q6-k-block-bytes))
      (throw (ex-info "Q6_K row/block count mismatch"
                      {:row-bytes (alength q6-row) :q8-blocks nb})))
    (let [^floats sums (float-array 8)]
      (dotimes [block nb]
        (let [bo (* block q6-k-block-bytes)
              {:keys [d ^bytes qs]} (nth q8-blocks block)
              ^bytes aux (byte-array qk-k)
              ^ints aux32 (int-array 8)]
          (dotimes [group 2]
            (let [base (* group 128)
                  ql (+ bo (* group 64))
                  qh (+ bo 128 (* group 32))]
              (dotimes [l 32]
                (let [ql0 (u8 q6-row (+ ql l))
                      ql32 (u8 q6-row (+ ql 32 l))
                      qhv (u8 q6-row (+ qh l))]
                  (aset-byte aux (+ base l) (unchecked-byte (- (bit-or (bit-and ql0 15) (bit-shift-left (bit-and qhv 3) 4)) 32)))
                  (aset-byte aux (+ base 32 l) (unchecked-byte (- (bit-or (bit-and ql32 15) (bit-shift-left (bit-and (bit-shift-right qhv 2) 3) 4)) 32)))
                  (aset-byte aux (+ base 64 l) (unchecked-byte (- (bit-or (bit-shift-right ql0 4) (bit-shift-left (bit-and (bit-shift-right qhv 4) 3) 4)) 32)))
                  (aset-byte aux (+ base 96 l) (unchecked-byte (- (bit-or (bit-shift-right ql32 4) (bit-shift-left (bit-and (bit-shift-right qhv 6) 3) 4)) 32)))))))
          (dotimes [j 16]
            (let [scale (i8 q6-row (+ bo 192 j))
                  base (* j 16)]
              (dotimes [chunk 2]
                (dotimes [lane 8]
                  (let [i (+ base (* chunk 8) lane)]
                    (aset-int aux32 lane
                              (+ (aget aux32 lane)
                                 (* scale (long (aget qs i)) (long (aget aux i))))))))))
          (let [block-d (f32 (* (f32 (gguf/fp16->double (le-u16 q6-row (+ bo 208)))) (f32 d)))]
            (dotimes [lane 8]
              (aset-float sums lane
                          (f32 (+ (aget sums lane)
                                  (f32 (* block-d (aget aux32 lane))))))))))
      (double
       (loop [lane (int 0) s (f32 0.0)]
         (if (< lane 8)
           (recur (unchecked-inc-int lane) (f32 (+ s (aget sums lane))))
           s))))))
