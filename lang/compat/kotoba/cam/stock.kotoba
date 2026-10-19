(ns kotoba.cam.stock
  "Workpiece stock definitions and material presets. Ported from kami-cam
   (Rust) `src/stock.rs`."
  (:require [kotoba.cam.vec3 :as vec3]))

;; ---------------------------------------------------------------------
;; StockShape — {:type :block|:cylinder|:from-mesh, ...}
;; ---------------------------------------------------------------------

(defn block
  "Rectangular block, width (X) / height (Y) / depth (Z) in mm."
  [width height depth]
  {:type :block :width (double width) :height (double height) :depth (double depth)})

(defn cylinder
  "Cylindrical billet, diameter / length in mm."
  [diameter length]
  {:type :cylinder :diameter (double diameter) :length (double length)})

(defn from-mesh
  "Arbitrary mesh input: `vertices` a seq of vec3, `indices` a seq of
   `[i0 i1 i2]` triangle index triples."
  [vertices indices]
  {:type :from-mesh :vertices (vec vertices) :indices (vec indices)})

;; ---------------------------------------------------------------------
;; CamMaterial — density (g/cm^3) + Brinell hardness (HB)
;; ---------------------------------------------------------------------

(defn material
  "Construct a CamMaterial: `name`, density (g/cm^3), Brinell hardness (HB)."
  [name density hardness]
  {:name name :density (double density) :hardness (double hardness)})

;; Canonical table lives in resources/kotoba/cam/materials.edn. This literal
;; is kept byte-for-byte in sync with that file (verified by
;; test/kotoba/cam/materials_edn_test.clj) rather than loaded via file IO at
;; namespace init, so this domain namespace stays free of IO and portable to
;; cljs/wasm.
(def material-presets
  {:aluminum-6061    {:name "Aluminum 6061-T6"   :density 2.70 :hardness 95.0}
   :steel-1045       {:name "Steel 1045"         :density 7.87 :hardness 163.0}
   :titanium-ti6al4v {:name "Titanium Ti-6Al-4V" :density 4.43 :hardness 334.0}
   :abs-plastic      {:name "ABS Plastic"        :density 1.04 :hardness 10.0}
   :wood-oak         {:name "Oak (Red)"          :density 0.66 :hardness 6.0}})

(defn aluminum-6061 [] (:aluminum-6061 material-presets))
(defn steel-1045 [] (:steel-1045 material-presets))
(defn titanium-ti6al4v [] (:titanium-ti6al4v material-presets))
(defn abs-plastic [] (:abs-plastic material-presets))
(defn wood-oak [] (:wood-oak material-presets))

;; ---------------------------------------------------------------------
;; Stock
;; ---------------------------------------------------------------------

(defn stock
  "A workpiece (raw stock) to be machined."
  ([shape mat] (stock shape mat vec3/zero))
  ([shape mat origin] {:shape shape :material mat :origin origin}))

(defn with-origin [s origin] (assoc s :origin origin))

(defn dimensions
  "Axis-aligned bounding box dimensions (width, height, depth) in mm. For
   `:from-mesh`, computes from vertex extents."
  [s]
  (let [shape (:shape s)]
    (case (:type shape)
      :block (vec3/v3 (:width shape) (:height shape) (:depth shape))
      :cylinder (let [d (:diameter shape)] (vec3/v3 d d (:length shape)))
      :from-mesh (let [vs (:vertices shape)]
                   (if (empty? vs)
                     vec3/zero
                     (let [mn (reduce vec3/vmin (first vs) (rest vs))
                           mx (reduce vec3/vmax (first vs) (rest vs))]
                       (vec3/sub mx mn)))))))
