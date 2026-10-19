(ns kotoba.render.environment-bake
  "Deterministic offline split-sum IBL baker.

   The production renderer consumes RGBA8 `pbr-environment-v1` data.  This
   namespace creates that exact contract from an analytic studio sky using a
   fixed Hammersley sequence, then stores it as gzip EDN.  Runtime hosts only
   load/upload the result; no convolution happens during a frame."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kotoba.render.environment :as environment]
            [kotoba.render.texture :as texture])
  (:import [java.util.zip GZIPInputStream GZIPOutputStream]))

(def production-config
  {:irradiance-size 32
   :specular-size 128
   :brdf-size 128
   ;; Low-discrepancy samples, not random samples. These counts keep a clean
   ;; analytic studio source stable while making the reference JVM bake a
   ;; practical build step; consumers can raise them in a custom config.
   :irradiance-samples 16
   :specular-samples 24
   :brdf-samples 48})

(defn- clamp [x lo hi] (max lo (min hi x)))
(defn- dot [a b] (reduce + (map * a b)))
(defn- add [a b] (mapv + a b))
(defn- mul [a s] (mapv #(* % s) a))
(defn- cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])
(defn- normalize [v]
  (let [length (Math/sqrt (max 1.0e-20 (dot v v)))] (mul v (/ length))))

(defn- radical-inverse-vdc [bits]
  (loop [n (long bits) denominator 2.0 result 0.0]
    (if (zero? n)
      result
      (recur (unsigned-bit-shift-right n 1) (* denominator 2.0)
             (+ result (if (odd? n) (/ denominator) 0.0))))))

(defn- hammersley [i n] [(/ (+ i 0.5) n) (radical-inverse-vdc i)])

(defn- basis [n]
  (let [up (if (< (Math/abs (nth n 1)) 0.999) [0.0 1.0 0.0] [1.0 0.0 0.0])
        tangent (normalize (cross up n))]
    [tangent (cross n tangent)]))

(defn- tangent->world [[x y z] n]
  (let [[t b] (basis n)] (normalize (add (add (mul t x) (mul b y)) (mul n z)))))

(defn- cube-direction [face size x y]
  (let [u (- (* 2.0 (/ (+ x 0.5) size)) 1.0)
        v (- 1.0 (* 2.0 (/ (+ y 0.5) size)))]
    (normalize
     (case face
       :+x [1.0 v (- u)] :-x [-1.0 v u]
       :+y [u 1.0 (- v)] :-y [u -1.0 v]
       :+z [u v 1.0] :-z [(- u) v -1.0]))))

(defn studio-radiance
  "Analytic, seam-free daylight environment: cool sky, warm ground and a broad
   key light. The ambient floor is 3.5x the original dim studio bake so the
   default exposure retains shadow detail. Values remain bounded linear RGB."
  [[x y z]]
  (let [sky-t (clamp (* 0.5 (+ y 1.0)) 0.0 1.0)
        base (mapv + (mul [0.1925 0.2625 0.42] sky-t)
                   (mul [0.315 0.2275 0.1575] (- 1.0 sky-t)))
        sun (Math/pow (max 0.0 (dot [x y z] (normalize [-0.48 0.72 0.50]))) 96.0)]
    (mapv #(clamp % 0.0 1.0) (add base (mul [1.0 0.72 0.42] (* 0.72 sun))))))

(defn- cosine-direction [xi]
  (let [r (Math/sqrt (first xi)) theta (* 2.0 Math/PI (second xi))]
    [(* r (Math/cos theta)) (* r (Math/sin theta)) (Math/sqrt (- 1.0 (first xi)))]))

(defn- irradiance-at [normal samples]
  ;; Cosine importance sampling: integral/pi is simply the average radiance.
  (mul (reduce add [0.0 0.0 0.0]
               (for [i (range samples)]
                 (studio-radiance (tangent->world (cosine-direction (hammersley i samples)) normal))))
       (/ samples)))

(defn- ggx-half [xi roughness]
  (let [a (max 0.0025 (* roughness roughness))
        phi (* 2.0 Math/PI (first xi))
        cos-theta (Math/sqrt (/ (- 1.0 (second xi))
                                (+ 1.0 (* (- (* a a) 1.0) (second xi)))))
        sin-theta (Math/sqrt (max 0.0 (- 1.0 (* cos-theta cos-theta))))]
    [(* sin-theta (Math/cos phi)) (* sin-theta (Math/sin phi)) cos-theta]))

(defn- reflect [v n] (add (mul n (* 2.0 (dot v n))) (mul v -1.0)))

(defn- specular-at [normal roughness samples]
  (let [view normal
        [sum weight]
        (reduce (fn [[acc w] i]
                  (let [half (tangent->world (ggx-half (hammersley i samples) roughness) normal)
                        light (normalize (reflect view half))
                        ndl (max 0.0 (dot normal light))]
                    (if (pos? ndl) [(add acc (mul (studio-radiance light) ndl)) (+ w ndl)] [acc w])))
                [[0.0 0.0 0.0] 0.0] (range samples))]
    (if (pos? weight) (mul sum (/ weight)) [0.0 0.0 0.0])))

(defn- geometry-schlick [ndot roughness]
  (let [k (/ (* (+ roughness 1.0) (+ roughness 1.0)) 8.0)]
    (/ ndot (+ (* ndot (- 1.0 k)) k))))

(defn- brdf-at [ndv samples]
  (let [view [(Math/sqrt (max 0.0 (- 1.0 (* ndv ndv)))) 0.0 ndv]]
    ;; The LUT's y axis supplies roughness; caller replaces it below per row.
    (fn [roughness]
      (mul
       (reduce
        (fn [[a b] i]
          (let [half (ggx-half (hammersley i samples) roughness)
                light (normalize (reflect view half))
                ndl (max 0.0 (nth light 2)) ndh (max 0.0 (nth half 2))
                vdh (max 0.0 (dot view half))]
            (if (pos? ndl)
              (let [g (* (geometry-schlick ndv roughness) (geometry-schlick ndl roughness))
                    gv (/ (* g vdh) (max 1.0e-6 (* ndh ndv)))
                    fc (Math/pow (- 1.0 vdh) 5.0)]
                [(+ a (* (- 1.0 fc) gv)) (+ b (* fc gv))])
              [a b]))) [0.0 0.0] (range samples))
       (/ samples)))))

(defn- rgba [rgb]
  (conj (mapv #(long (Math/round (* 255.0 (clamp % 0.0 1.0)))) rgb) 255))

(defn- bake-face [size pixel-fn face]
  (vec (mapcat (fn [y] (mapcat (fn [x] (rgba (pixel-fn (cube-direction face size x y))))
                                (range size)))
               (range size))))

(defn- bake-cube-level [size pixel-fn]
  (environment/cube-level size
    (into {} (for [face environment/cube-faces]
               [face (bake-face size pixel-fn face)]))))

(defn bake-environment
  "Bake a complete `pbr-environment-v1`. Config sizes must be powers of two."
  [{:keys [irradiance-size specular-size brdf-size irradiance-samples specular-samples brdf-samples]
    :as _config}]
  (let [mip-count (texture/mip-level-count specular-size specular-size)
        irradiance (bake-cube-level irradiance-size #(irradiance-at % irradiance-samples))
        spec-levels (mapv (fn [level]
                            (let [size (max 1 (bit-shift-right specular-size level))
                                  roughness (if (= mip-count 1) 0.0 (/ level (dec mip-count)))]
                              (bake-cube-level size #(specular-at % roughness specular-samples))))
                          (range mip-count))
        lut-data (vec
                  (mapcat (fn [y]
                            (let [roughness (/ (+ y 0.5) brdf-size)]
                              (mapcat (fn [x]
                                        (let [ndv (/ (+ x 0.5) brdf-size)
                                              [a b] ((brdf-at ndv brdf-samples) roughness)]
                                          (rgba [a b 0.0])))
                                      (range brdf-size))))
                          (range brdf-size)))]
    (environment/pbr-environment
     {:irradiance (environment/cube-rgba8 [irradiance] :linear)
      :prefiltered-specular (environment/cube-rgba8 spec-levels :linear)
      :brdf-lut (texture/rgba8 brdf-size brdf-size lut-data :linear)})))

(defn write-baked! [path environment]
  (io/make-parents path)
  (with-open [out (GZIPOutputStream. (io/output-stream path))
              writer (io/writer out)]
    (.write writer (pr-str environment))
    (.write writer "\n"))
  path)

(defn read-baked [path]
  (with-open [in (GZIPInputStream. (io/input-stream path))
              reader (java.io.PushbackReader. (io/reader in))]
    (environment/pbr-environment (edn/read reader))))

(defn -main [& args]
  (let [opts (apply hash-map args)
        out (or (get opts "--out") "target/ibl/studio-pbr-environment.edn.gz")]
    (println "Baking deterministic production IBL" production-config)
    (write-baked! out (bake-environment production-config))
    (println "Wrote" out)))
