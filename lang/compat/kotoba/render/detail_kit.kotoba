(ns kotoba.render.detail-kit
  "Portable modular architecture and foreground-prop detail kits.

   The result is pure CLJC/EDN: executors resolve geometry references, transforms,
   material-preset-v1 role references and collision policy without engine handles."
  (:require [kotoba.render.architecture :as architecture]
            [kotoba.render.building :as building]
            [kotoba.render.procedural :as procedural]))

(def schema :kotoba.render/detail-kit-v1)
(def tiers #{:hero :gameplay :crowd})
(def families #{:stylized :photoreal})
(def material-contract :kotoba.render/material-preset-v1)

(def tier-policy
  {:hero {:architecture-detail :high :min-pixels 96.0
          :triangle-budget 560 :prop-budget 8 :bevel-segments 2}
   :gameplay {:architecture-detail :medium :min-pixels 28.0
              :triangle-budget 240 :prop-budget 4 :bevel-segments 1}
   :crowd {:architecture-detail :low :min-pixels 0.0
           :triangle-budget 72 :prop-budget 1 :bevel-segments 0}})

(def ^:private prop-kinds [:crate :barrel :bollard :barrier :utility-box :planter])
(def ^:private role-alias {:door :utility})

(defn- hash-unit [seed salt]
  (/ (double (bit-and (procedural/coordinate-hash seed salt 0 211) 65535)) 65535.0))

(defn- signed [seed salt] (- (* 2.0 (hash-unit seed salt)) 1.0))

(defn- material-ref [family role entity-id]
  {:contract material-contract
   :preset-id (keyword (name family) (str "architecture-" (name role)))
   :family family :domain :architecture :role role :entity-id entity-id})

(defn- enrich-part [family entity-id tier seed index part]
  (let [role (get role-alias (:role part) (:role part))
        bevel (* 0.018 (apply min (:size part)) (get-in tier-policy [tier :bevel-segments]))]
    (-> part
        (dissoc :material)
        (assoc :part/id (keyword (str (name entity-id) "-part-" index))
               :role role
               :material-ref (material-ref family role entity-id)
               :geometry {:generator :kotoba.render.mesh/cube
                          :primitive :box
                          :bevel {:width bevel
                                  :segments (get-in tier-policy [tier :bevel-segments])}}
               :variation {:seed seed
                           :scale (+ 1.0 (* 0.025 (signed seed (+ 100 index))))}))))

(defn- prop-geometry [kind tier]
  (case kind
    (:barrel :bollard)
    {:generator :kotoba.render.mesh/cylinder-pipe
     :arguments {:sectors (case tier :hero 12 :gameplay 8 6)}
     :triangle-count (case tier :hero 48 :gameplay 32 24)}
    {:generator :kotoba.render.mesh/cube :primitive :box
     :bevel {:width (case tier :hero 0.06 :gameplay 0.025 0.0)
             :segments (get-in tier-policy [tier :bevel-segments])}
     :triangle-count 12}))

(defn- prop-size [kind]
  (case kind
    :crate [0.8 0.75 0.8] :barrel [0.55 0.92 0.55]
    :bollard [0.24 0.85 0.24] :barrier [1.8 0.72 0.28]
    :utility-box [0.72 1.05 0.42] :planter [1.1 0.48 0.55]))

(defn- foreground-props [family entity-id tier seed width depth]
  (let [n (get-in tier-policy [tier :prop-budget])]
    (mapv
     (fn [index]
       (let [kind (nth prop-kinds (mod (bit-and (procedural/coordinate-hash seed index 9 37)
                                                0x7fffffff)
                                         (count prop-kinds)))
             side (if (even? index) -1.0 1.0)
             role (if (= kind :planter) :trim :utility)
             geometry (prop-geometry kind tier)]
         {:prop/id (keyword (str (name entity-id) "-prop-" index))
          :kind kind :foreground? true
          :offset [(* side (+ (* width 0.56) (* 0.75 (hash-unit seed (+ 20 index)))))
                   0.0
                   (+ (* depth -0.42) (* depth 0.84 (hash-unit seed (+ 40 index))))]
          :yaw (* 0.35 (signed seed (+ 60 index)))
          :size (mapv #(* % (+ 0.92 (* 0.16 (hash-unit seed (+ 80 index)))))
                      (prop-size kind))
          :role role :material-ref (material-ref family role entity-id)
          :geometry geometry :triangles (:triangle-count geometry)
          ;; Foreground dressing must never alter navigation or combat collision.
          :collision {:mode :none :visual-only? true}
          :variation {:seed seed :variant-index index}}))
     (range n))))

(defn- budget [parts props tier]
  (let [part-tris (reduce + 0 (map #(or (:triangles %) 12) parts))
        prop-tris (reduce + 0 (map :triangles props))
        total (+ part-tris prop-tris)
        cap (get-in tier-policy [tier :triangle-budget])]
    {:triangle-count total :triangle-budget cap :within-budget? (<= total cap)
     :part-count (count parts) :prop-count (count props)}))

(defn detail-kit
  "Resolve a deterministic architecture/prop kit.

   Stylized is implemented. Photoreal intentionally returns the identical API
   boundary with `:boundary-only` and no parts; it must not be presented as an
   authored photoreal asset set before that quality work exists."
  [{:keys [family tier entity-id seed variant width depth height]
    :or {family :stylized tier :gameplay entity-id :building seed 0
         variant :depot width 10.0 depth 7.0 height 6.0}}]
  (when-not (families family)
    (throw (ex-info "unsupported detail-kit family" {:family family :supported families})))
  (when-not (tiers tier)
    (throw (ex-info "unsupported detail tier" {:tier tier :supported tiers})))
  (when-not (and (integer? seed) (<= 0 seed 4294967295))
    (throw (ex-info "detail-kit seed must be an unsigned 32-bit integer" {:seed seed})))
  (if (= family :photoreal)
    {:schema schema :family family :tier tier :entity-id entity-id
     :implementation-status :boundary-only
     :quality-claim :unimplemented :parts [] :foreground-props []
     :material-contract material-contract :tier-policy (get tier-policy tier)
     :budget {:triangle-count 0
              :triangle-budget (get-in tier-policy [tier :triangle-budget])
              :within-budget? true :part-count 0 :prop-count 0}
     :geometry-lods []
     :geometry-generators {:box :kotoba.render.mesh/cube
                           :cylinder :kotoba.render.mesh/cylinder-pipe}}
    (let [raw (architecture/building-parts
               {:variant variant :width width :depth depth :height height}
               (get-in tier-policy [tier :architecture-detail]))
          parts (mapv #(enrich-part family entity-id tier seed %1 %2) (range) raw)
          props (foreground-props family entity-id tier seed width depth)
          budget' (budget parts props tier)
          ;; Reuse the existing authored building generator for executor-ready LOD meshes.
          geometry-lods (building/building-lods
                         {:variant :industrial-block :width width :depth depth
                          :height height :seed seed})]
      {:schema schema :family family :tier tier :entity-id entity-id
       :implementation-status :implemented :quality-claim :stylized-authored
       :material-contract material-contract
       :tier-policy (get tier-policy tier)
       :parts parts :foreground-props props :budget budget'
       :geometry-lods geometry-lods
       :geometry-generators {:box :kotoba.render.mesh/cube
                             :cylinder :kotoba.render.mesh/cylinder-pipe}})))

(defn lod-kits
  "Return hero/gameplay/crowd kits in descending screen-detail order."
  [spec]
  (mapv #(detail-kit (assoc spec :tier %)) [:hero :gameplay :crowd]))
