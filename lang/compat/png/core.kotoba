(ns png.core
  "PNG decode (W3C Recommendation): chunk grammar (png.decode) + IDAT zlib
   (org-ietf-deflate) + scanline unfiltering. R0 supports bit-depth 8/16;
   sub-byte depths (1/2/4) and Adam7 interlace are not yet unfiltered.
   Extracted from kotoba-lang/kasane (kasane.png, ADR-2606272100) as
   `org-w3-png`."
  (:require [png.decode :as d]
            [deflate.core :as deflate]))

(def signature [137 80 78 71 13 10 26 10])

(def ^:private channels {:gray 1 :rgb 3 :indexed 1 :gray-alpha 2 :rgba 4})

(defn- paeth [a b c]
  (let [p (- (+ a b) c)
        pa (abs (- p a)) pb (abs (- p b)) pc (abs (- p c))]
    (cond (and (<= pa pb) (<= pa pc)) a
          (<= pb pc) b
          :else c)))

(defn- unfilter
  "Reconstruct raw samples from filtered scanlines (PNG §9). Returns a vector
   of unsigned sample bytes, row-major, filter bytes removed."
  [data {:keys [width color-type bit-depth]}]
  (let [ch     (channels color-type 1)
        bpp    (max 1 (quot (* ch bit-depth) 8))
        stride (quot (+ (* width ch bit-depth) 7) 8)
        out    (volatile! [])]
    (loop [pos 0 prev (vec (repeat stride 0))]
      (if (>= pos (count data))
        @out
        (let [ft  (nth data pos)
              cur (volatile! (vec (repeat stride 0)))]
          (dotimes [x stride]
            (let [raw (nth data (+ pos 1 x))
                  a   (if (>= (- x bpp) 0) (nth @cur (- x bpp)) 0)
                  b   (nth prev x)
                  c   (if (>= (- x bpp) 0) (nth prev (- x bpp)) 0)
                  pred (case ft 0 0, 1 a, 2 b, 3 (quot (+ a b) 2), 4 (paeth a b c)
                         (throw (ex-info "png: bad filter type" {:ft ft})))]
              (vswap! cur assoc x (bit-and (+ raw pred) 0xff))))
          (vswap! out into @cur)
          (recur (+ pos 1 stride) @cur))))))

(defn parse
  "Parse PNG `data` with `grammar` (resources/png/grammar.edn). Returns
   {:ihdr {...} :pixels [...] :chunks [...]}."
  [grammar data]
  (let [raw    (d/decode grammar data)
        sig    (:sig raw)]
    (when (not= sig signature)
      (throw (ex-info "png: bad signature" {:got sig})))
    (let [chunks (:chunks raw)
          of-type (fn [t] (filter #(= (:type %) t) chunks))
          ihdr   (d/decode grammar :ihdr (:data (first (of-type "IHDR"))))
          idat   (vec (mapcat :data (of-type "IDAT")))
          inflated (deflate/inflate idat)]
      {:ihdr   ihdr
       :chunks chunks
       :pixels (unfilter inflated ihdr)})))
