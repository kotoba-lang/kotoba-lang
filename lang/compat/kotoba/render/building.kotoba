(ns kotoba.render.building
  "Deterministic, portable building silhouettes with bounded LOD forms."
  (:require [kotoba.render.mesh :as mesh]
            [kotoba.render.procedural :as procedural]))

(def variants #{:stepped-tower :industrial-block})
(def details #{:high :medium :low})

(defn- hash-unit [seed salt]
  (/ (double (bit-and (procedural/coordinate-hash seed salt 0 71) 255)) 255.0))

(defn- validate-spec!
  [{:keys [variant width depth height seed]} detail]
  (when-not (variants variant)
    (throw (ex-info "unsupported building variant"
                    {:variant variant :supported variants})))
  (when-not (details detail)
    (throw (ex-info "unsupported building detail" {:detail detail :supported details})))
  (when-not (and (number? width) (pos? width)
                 (number? depth) (pos? depth)
                 (number? height) (pos? height))
    (throw (ex-info "building dimensions must be positive numbers"
                    {:width width :depth depth :height height})))
  (when-not (and (integer? seed) (<= 0 seed 4294967295))
    (throw (ex-info "building seed must be an unsigned 32-bit integer" {:seed seed}))))

(defn- box-component [width height depth center-y center-x center-z]
  (let [[positions normals uvs indices] (mesh/cube)]
    [(vec (mapcat (fn [[x y z]]
                    [(+ center-x (* x width))
                     (+ center-y (* y height))
                     (+ center-z (* z depth))])
                  (partition 3 positions)))
     normals uvs indices]))

(defn- combine [meshes]
  (reduce
   (fn [[positions normals uvs indices] [p n uv idx]]
     (let [base (quot (count positions) 3)]
       [(into positions p) (into normals n) (into uvs uv)
        (into indices (map #(+ base %) idx))]))
   [[] [] [] []] meshes))

(defn- stepped-components [{:keys [width depth height seed]} detail]
  (let [count-by-detail {:high 4 :medium 2 :low 1}
        tier-count (count-by-detail detail)
        tier-height (/ height tier-count)
        taper (+ 0.12 (* 0.08 (hash-unit seed 1)))]
    (mapv (fn [tier]
            (let [scale (max 0.36 (- 1.0 (* tier taper)))]
              (box-component (* width scale) tier-height (* depth scale)
                             (+ (* tier tier-height) (/ tier-height 2.0))
                             0.0 0.0)))
          (range tier-count))))

(defn- industrial-components [{:keys [width depth height seed]} detail]
  (if (= :low detail)
    [(box-component width height depth (/ height 2.0) 0.0 0.0)]
    (let [body-ratio (if (= :high detail) 0.74 0.82)
          body-height (* height body-ratio)
          roof-height (- height body-height)
          monitor-width (* width (+ 0.38 (* 0.12 (hash-unit seed 2))))
          monitor-depth (* depth 0.72)
          core [(box-component width body-height depth (/ body-height 2.0) 0.0 0.0)
                (box-component monitor-width roof-height monitor-depth
                               (+ body-height (/ roof-height 2.0)) 0.0 0.0)]]
      (if (= :medium detail)
        core
        (let [vent-width (* width 0.11)
              vent-height (* height 0.08)
              vent-depth (* depth 0.16)
              vent-y (+ body-height (* roof-height 0.62))
              vent-x (* width (+ 0.20 (* 0.05 (hash-unit seed 3))))]
          (into core
                [(box-component vent-width vent-height vent-depth vent-y (- vent-x) 0.0)
                 (box-component vent-width vent-height vent-depth vent-y vent-x 0.0)]))))))

(defn building-mesh
  "Generate `[positions normals uvs indices]` for one building LOD.

   The tuple is directly accepted by `mesh/loaded-mesh`, `compute-tangents`
   and `interleave-with-tangents`. Dimensions are world units; seed only
   changes bounded silhouette proportions, never topology at a given detail."
  ([spec] (building-mesh spec :high))
  ([{:keys [variant] :as spec} detail]
   (let [spec (assoc spec :seed (or (:seed spec) 0))]
     (validate-spec! spec detail)
     (combine
      (case variant
        :stepped-tower (stepped-components spec detail)
        :industrial-block (industrial-components spec detail))))))

(defn building-lods
  "Generate high/medium/low forms compatible with `kotoba.render.lod`.
   Levels are ordered from highest to lowest screen-space threshold."
  [spec]
  (mapv (fn [[id min-pixels]]
          (let [[_positions _normals _uvs indices :as generated]
                (building-mesh spec id)]
            {:id id
             :min-pixels min-pixels
             :triangle-count (quot (count indices) 3)
             :mesh generated
             :bounds {:width (:width spec) :depth (:depth spec) :height (:height spec)}}))
        [[:high 96.0] [:medium 32.0] [:low 0.0]]))
