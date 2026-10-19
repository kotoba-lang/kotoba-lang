(ns kotoba.render.uastc
  "UASTC (Universal ASTC) LDR 4x4 block decoder — a faithful port of the
   Basis Universal reference transcoder (`basisu_transcoder.cpp`
   `unpack_uastc`, BinomialLLC/basis_universal) via
   `kami-render/src/basisu.rs`.

   Decodes a single 16-byte UASTC LDR block into 16 RGBA8 pixels (raster
   order). Pure CPU, no GPU dependency, fully portable `.cljc`: the only
   inputs are the block bytes and the constant lookup tables in
   [[kotoba.render.uastc-tables]]. All ~19 UASTC block modes are handled
   (Huffman mode selection, subset/partition patterns, dual-plane, trit/quint
   BISE endpoint decode, ASTC endpoint unquantization + weight interpolation).

   Block bytes are a `nth`-able indexed collection of unsigned ints 0..255
   (this repo's byte convention, see [[kotoba.render.bits]]). The decoded
   result is a vector of 16 `[r g b a]` pixel vectors (values 0..255)."
  (:require [kotoba.render.uastc-tables :as t]))

(def ^:private mode-solid 8)

(defn- rd
  "Read `cnt` bits LSB-first from `block` at the bit position held in the
   1-element long-array `p`, advancing `p`. Returns the value (long).
   Mirrors `basisu.rs`'s `BitReader::read`."
  [block ^longs p cnt]
  (loop [i 0 v 0 pos (aget p 0)]
    (if (= i cnt)
      (do (aset p 0 pos) v)
      (let [byte (bit-and (long (nth block (bit-shift-right pos 3))) 0xff)
            bit (bit-and (bit-shift-right byte (bit-and pos 7)) 1)]
        (recur (inc i) (bit-or v (bit-shift-left bit i)) (inc pos))))))

(defn- astc-interpolate
  "ASTC endpoint->weight interpolation (Basis `astc_interpolate`, LDR path).
   `l`,`h` are unquantized 0..255 endpoint components; `w` is 0..64."
  [l h w]
  (let [l (bit-or (bit-shift-left l 8) l)
        h (bit-or (bit-shift-left h 8) h)
        k (bit-shift-right (+ (* l (- 64 w)) (* h w) 32) 6)]
    (bit-shift-right k 8)))

(defn- bad-block [mode]
  (throw (ex-info (str "invalid UASTC block (mode " mode ")")
                  {:type :bad-block :mode mode})))

(defn decode-block
  "Decode a single 16-byte UASTC LDR `block` into a vector of 16 `[r g b a]`
   pixels (raster order, values 0..255). Throws `ex-info` `{:type :bad-block}`
   on an invalid mode or out-of-range partition pattern."
  [block]
  (let [b0 (bit-and (long (nth block 0)) 0xff)
        mode (long (nth t/huff-modes (bit-and b0 127)))]
    (when (>= mode 19) (bad-block mode))
    (let [p (long-array [(long (nth (nth t/huff-codes mode) 1))])] ; skip mode huffman code
      (if (= mode mode-solid)
        (let [r (rd block p 8) g (rd block p 8) bl (rd block p 8) a (rd block p 8)]
          (vec (repeat 16 [r g bl a])))
        (do
          ;; skip the BC1/ETC hint bits (we decode straight to RGBA)
          (aset p 0 (+ (aget p 0) (long (nth t/mode-total-hint-bits mode))))
          (let [[subsets common-pattern]
                (case mode
                  (2 4 7 9 16) [2 (rd block p 5)]
                  3 [3 (rd block p 4)]
                  [1 0])
                [total-planes ccs]
                (case mode
                  (6 11 13) [2 (rd block p 2)]
                  17 [2 3]
                  [1 -1])
                total-comps (long (nth t/mode-comps mode))
                weight-bits (long (nth t/mode-weight-bits mode))
                endpoint-range (long (nth t/mode-endpoint-ranges mode))
                total-values (* total-comps 2 subsets)
                brange (nth t/bise-range endpoint-range)
                ep-bits (long (nth brange 0))
                ep-trits (not (zero? (long (nth brange 1))))
                ep-quints (not (zero? (long (nth brange 2))))
                [total-tqs bundle-size mul]
                (cond ep-trits [(quot (+ total-values 4) 5) 5 3]
                      ep-quints [(quot (+ total-values 2) 3) 3 5]
                      :else [0 0 0])
                base-bits (if ep-trits 8 7)
                tq (long-array 8)]
            ;; ---- trit/quint bundle values ----
            (dotimes [i total-tqs]
              (let [num-bits
                    (if (= i (dec total-tqs))
                      (let [nr (- total-values (* (dec total-tqs) bundle-size))]
                        (cond
                          ep-trits (case (long nr) 1 2 2 4 3 5 4 7 base-bits)
                          ep-quints (case (long nr) 1 3 2 5 base-bits)
                          :else base-bits))
                      base-bits)]
                (aset tq i (long (rd block p num-bits)))))
            ;; ---- endpoint BISE decode ----
            (let [endpoints (int-array 18)]
              (loop [ep 0 accum 0 accum-remaining 0 next-tq 0]
                (when (< ep total-values)
                  (let [value0 (long (rd block p ep-bits))]
                    (if (not= total-tqs 0)
                      (let [[accum accum-remaining next-tq]
                            (if (zero? accum-remaining)
                              [(aget tq next-tq) bundle-size (inc next-tq)]
                              [accum accum-remaining next-tq])
                            v (mod accum mul)
                            value (bit-or value0 (bit-shift-left v ep-bits))]
                        (aset endpoints ep (int (bit-and value 0xff)))
                        (recur (inc ep) (quot accum mul) (dec accum-remaining) next-tq))
                      (do (aset endpoints ep (int (bit-and value0 0xff)))
                          (recur (inc ep) accum accum-remaining next-tq))))))
              ;; ---- partition pattern + subset anchors ----
              (let [[pattern anchors]
                    (cond
                      (= subsets 1) [nil [0 0 0]]
                      (= subsets 3)
                      (let [pp (long common-pattern)]
                        (when (>= pp (count t/patterns3)) (bad-block mode))
                        [(nth t/patterns3 pp) (nth t/patterns3-anchors pp)])
                      (= mode 7)
                      (let [pp (long common-pattern)]
                        (when (>= pp (count t/patterns2-bc7m3)) (bad-block mode))
                        [(nth t/patterns2-bc7m3 pp) (nth t/patterns2-bc7m3-anchors pp)])
                      :else
                      (let [pp (long common-pattern)]
                        (when (>= pp (count t/patterns2)) (bad-block mode))
                        [(nth t/patterns2 pp) (nth t/patterns2-anchors pp)]))
                    ;; ---- weight BISE decode (plain binary; anchors drop high bit) ----
                    weights (int-array 64)
                    total-weights (* 16 total-planes)
                    wb weight-bits
                    wb1 (dec weight-bits)]
                (cond
                  (= total-planes 2)
                  (do (aset weights 0 (int (rd block p wb1)))
                      (aset weights 1 (int (rd block p wb1)))
                      (loop [w 2] (when (< w total-weights)
                                    (aset weights w (int (rd block p wb))) (recur (inc w)))))
                  (= subsets 1)
                  (do (aset weights 0 (int (rd block p wb1)))
                      (loop [w 1] (when (< w 16)
                                    (aset weights w (int (rd block p wb))) (recur (inc w)))))
                  :else
                  (let [a0 (long (nth anchors 0))
                        a1 (long (nth anchors 1))
                        a2 (long (nth anchors 2))]
                    (dotimes [i 16]
                      (let [is-anchor (or (= i a0)
                                          (and (>= subsets 2) (= i a1))
                                          (and (>= subsets 3) (= i a2)))]
                        (aset weights i (int (rd block p (if is-anchor wb1 wb))))))))
                ;; ---- unquantize endpoints per subset ----
                (let [unq (nth t/astc-unquant endpoint-range)
                      subset-eps
                      (mapv
                       (fn [s]
                         (if (= total-comps 2)
                           ;; luminance+alpha: L in rgb, A in w
                           (let [ll (long (nth unq (aget endpoints (* s 4))))
                                 lh (long (nth unq (aget endpoints (+ (* s 4) 1))))
                                 al (long (nth unq (aget endpoints (+ (* s 4) 2))))
                                 ah (long (nth unq (aget endpoints (+ (* s 4) 3))))]
                             [[ll ll ll al] [lh lh lh ah]])
                           (let [base (* s total-comps 2)
                                 lo (mapv (fn [c] (if (< c total-comps)
                                                    (long (nth unq (aget endpoints (+ base (* c 2))))) 255))
                                          (range 4))
                                 hi (mapv (fn [c] (if (< c total-comps)
                                                    (long (nth unq (aget endpoints (+ base (* c 2) 1)))) 255))
                                          (range 4))]
                             [lo hi])))
                       (range subsets))
                      wtab (nth t/weight-tables weight-bits)]
                  ;; ---- assemble pixels ----
                  (if (= total-planes 1)
                    (mapv
                     (fn [i]
                       (let [s (if (= subsets 1) 0 (long (nth pattern i)))
                             [lo hi] (nth subset-eps s)
                             w (long (nth wtab (aget weights i)))]
                         (mapv (fn [c] (astc-interpolate (nth lo c) (nth hi c) w)) (range 4))))
                     (range 16))
                    (let [[lo hi] (nth subset-eps 0)]
                      (mapv
                       (fn [i]
                         (let [w0 (long (nth wtab (aget weights (* i 2))))
                               w1 (long (nth wtab (aget weights (+ (* i 2) 1))))]
                           (mapv (fn [c] (astc-interpolate (nth lo c) (nth hi c) (if (= c ccs) w1 w0)))
                                 (range 4))))
                       (range 16)))))))))))))

(defn decode-block-rgba
  "Decode a 16-byte UASTC LDR `block` into a flat vector of 64 bytes
   (16 pixels x RGBA, raster order). Convenience over [[decode-block]]."
  [block]
  (into [] (mapcat identity) (decode-block block)))
