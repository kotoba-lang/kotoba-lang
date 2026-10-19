(ns kotoba.render.facade
  "Semantic, portable facade articulation for stylized settlement archetypes."
  (:require [kotoba.render.mesh :as mesh]
            [kotoba.render.procedural :as procedural]))

(def schema :kotoba.render/facade-articulation-v1)
(def families #{:stylized :photoreal})
(def tiers #{:hero :mid :background})
(def archetypes #{:depot :habitat :industrial :utility :landmark})
(def material-contract :kotoba.render/material-preset-v1)

(def tier-policy
  {:hero {:detail :high :max-parts 120 :draw-budget 120 :triangle-budget 1800
          :blank-wall-ratio-cap 0.52}
   :mid {:detail :medium :max-parts 64 :draw-budget 64 :triangle-budget 900
         :blank-wall-ratio-cap 0.60}
   :background {:detail :low :max-parts 28 :draw-budget 28 :triangle-budget 400
                :blank-wall-ratio-cap 0.72}})

(def ^:private patterns
  {:depot {:floors 1 :bays 4 :entry-bay 1 :window-aspect 1.45 :rhythm :wide-service}
   :habitat {:floors 3 :bays 3 :entry-bay 1 :window-aspect 0.78 :rhythm :stacked-domestic}
   :industrial {:floors 2 :bays 5 :entry-bay 2 :window-aspect 1.90 :rhythm :factory-grid}
   :utility {:floors 1 :bays 2 :entry-bay 0 :window-aspect 0.72 :rhythm :compact-offset}
   :landmark {:floors 4 :bays 3 :entry-bay 1 :window-aspect 0.62 :rhythm :vertical-civic}})

(def ^:private semantic-role
  {:base :wall :plinth :trim :corner :trim :floor-band :trim
   :window-bay :window :window-frame :trim :recess-panel :wall
   :door :utility :canopy :roof :signage :trim :roof-parapet :roof
   :vent :utility :pipe :utility})

(defn- unit [seed salt]
  (/ (double (bit-and (procedural/coordinate-hash seed salt 13 227) 65535)) 65535.0))

(defn- material-ref [family entity-id semantic index]
  (let [role (semantic-role semantic)]
    {:contract material-contract
     :preset-id (keyword (name family) (str "architecture-" (name role)))
     :family family :domain :architecture :role role
     :entity-id (str entity-id "/facade/" (name semantic) "/" index)}))

(defn- part [family entity-id index semantic offset size & [geometry]]
  (let [geometry (or geometry :box)]
    {:part/id (keyword (str (name entity-id) "-facade-" index))
     :semantic semantic :role (semantic-role semantic)
     :material-ref (material-ref family entity-id semantic index)
     :geometry-ref (if (= geometry :cylinder)
                     :kotoba.render.mesh/cylinder-pipe
                     :kotoba.render.mesh/cube)
     :transform {:offset offset :scale size :rotation [0.0 0.0 0.0]}
     :triangles (if (= geometry :cylinder) 32 12)
     :collision {:mode :none :visual-only? true}}))

(defn- window-parts [family entity-id seed width height depth {:keys [floors bays window-aspect]} tier]
  (let [floors' (case tier :hero floors :mid (min floors 2) 1)
        bays' (case tier :hero bays :mid (min bays 4) (min bays 3))
        bay-width (/ width bays') floor-height (/ height floors')
        window-width (* bay-width (min 0.64 (/ 0.58 window-aspect)))
        window-height (* floor-height (min 0.55 (* 0.42 window-aspect)))
        z (+ (/ depth 2.0) 0.045)]
    (vec
     (mapcat
      (fn [[index [floor bay]]]
        (let [x (+ (- (/ width 2.0)) (* (+ bay 0.5) bay-width))
              y (* (+ floor 0.34) floor-height)
              glow (+ 0.28 (* 0.62 (unit seed (+ 100 index))))
              window (assoc (part family entity-id (* 2 index) :window-bay
                                  [x y z] [window-width window-height 0.07])
                            :material-overrides {:emissive-strength glow
                                                 :emissive-enabled? (> glow 0.46)})
              frame (part family entity-id (inc (* 2 index)) :window-frame
                          [x y (+ z 0.012)]
                          [(+ window-width 0.13) (+ window-height 0.13) 0.035])]
          (if (= tier :background) [window] [frame window])))
      (map-indexed vector (for [floor (range floors') bay (range bays')] [floor bay]))))))

(defn- fixed-parts [family entity-id width height depth pattern tier]
  (let [z (+ (/ depth 2.0) 0.04)
        corner-width (* width 0.035)
        band-count (case tier :hero (max 1 (dec (:floors pattern))) :mid 1 0)
        common [(part family entity-id 700 :base [0.0 0.0 z] [width height 0.08])
                (part family entity-id 701 :plinth [0.0 (* height 0.035) (+ z 0.055)]
                      [width (* height 0.07) 0.12])
                (part family entity-id 702 :corner [(- (/ width 2.0) (/ corner-width 2.0))
                                                    0.0 (+ z 0.06)]
                      [corner-width height 0.13])
                (part family entity-id 703 :corner [(+ (- (/ width 2.0)) (/ corner-width 2.0))
                                                    0.0 (+ z 0.06)]
                      [corner-width height 0.13])
                (part family entity-id 704 :door
                      [0.0 0.0 (+ z 0.075)] [(* width 0.16) (* height 0.32) 0.15])
                (part family entity-id 705 :roof-parapet
                      [0.0 height (+ z 0.04)] [(* width 1.03) (* height 0.055) 0.16])]
        bands (mapv (fn [i] (part family entity-id (+ 720 i) :floor-band
                                  [0.0 (* height (/ (inc i) (inc (:floors pattern)))) (+ z 0.07)]
                                  [width (* height 0.022) 0.14]))
                    (range band-count))
        hero [(part family entity-id 740 :recess-panel
                    [(* width -0.32) (* height 0.22) (+ z 0.05)]
                    [(* width 0.17) (* height 0.20) 0.09])
              (part family entity-id 741 :canopy
                    [0.0 (* height 0.31) (+ z (* depth 0.035))]
                    [(* width 0.25) (* height 0.035) (* depth 0.07)])
              (part family entity-id 742 :signage
                    [(* width 0.26) (* height 0.68) (+ z 0.10)]
                    [(* width 0.24) (* height 0.10) 0.08])
              (part family entity-id 743 :vent
                    [(* width -0.31) (* height 0.83) (+ z 0.12)]
                    [(* width 0.10) (* height 0.10) 0.10] :cylinder)
              (assoc (part family entity-id 744 :pipe
                           [(* width 0.40) (* height 0.50) (+ z 0.13)]
                           [(* width 0.025) (* height 0.70) (* width 0.025)] :cylinder)
                     :transform {:offset [(* width 0.40) (* height 0.15) (+ z 0.13)]
                                 :scale [(* width 0.025) (* height 0.70) (* width 0.025)]
                                 :rotation [0.0 0.0 0.0]})]
        mid (take 3 hero)]
    (vec (concat common bands (case tier :hero hero :mid mid [])))))

(defn- coverage [parts width height]
  (let [facade-area (* width height)
        covered (reduce + 0.0
                        (for [{:keys [semantic transform]} parts
                              :when (not= semantic :base)
                              :let [[w h _] (:scale transform)]]
                          (* w h)))
        ratio (max 0.0 (- 1.0 (min 1.0 (/ covered facade-area))))]
    {:facade-area facade-area :articulated-area (min facade-area covered)
     :blank-wall-ratio ratio}))

(defn- budget [parts tier]
  (let [triangles (reduce + (map :triangles parts)) policy (tier-policy tier)]
    {:parts (count parts) :part-budget (:max-parts policy)
     :draws (count parts) :draw-budget (:draw-budget policy)
     :triangles triangles :triangle-budget (:triangle-budget policy)
     :within-budget? (and (<= (count parts) (:max-parts policy))
                          (<= (count parts) (:draw-budget policy))
                          (<= triangles (:triangle-budget policy)))}))

(defn facade-kit
  [{:keys [family tier archetype entity-id seed width depth height]
    :or {family :stylized tier :mid archetype :habitat entity-id :facade
         seed 0 width 9.0 depth 7.0 height 8.0}}]
  (when-not (families family)
    (throw (ex-info "unsupported facade family" {:family family :supported families})))
  (when-not (tiers tier)
    (throw (ex-info "unsupported facade tier" {:tier tier :supported tiers})))
  (when-not (archetypes archetype)
    (throw (ex-info "unsupported facade archetype" {:archetype archetype :supported archetypes})))
  (when-not (and (integer? seed) (<= 0 seed 4294967295))
    (throw (ex-info "facade seed must be unsigned 32-bit" {:seed seed})))
  (when-not (every? #(and (number? %) (pos? %)) [width depth height])
    (throw (ex-info "facade dimensions must be positive" {:width width :depth depth :height height})))
  (if (= family :photoreal)
    {:schema schema :family family :tier tier :archetype archetype :entity-id entity-id
     :implementation-status :boundary-only :quality-claim :unimplemented
     :parts [] :mesh-library {} :rhythm {} :budget {:within-budget? true}}
    (let [pattern (patterns archetype)
          fixed (fixed-parts family entity-id width height depth pattern tier)
          windows (window-parts family entity-id seed width height depth pattern tier)
          parts (vec (concat fixed windows))
          coverage' (coverage parts width height)
          cap (get-in tier-policy [tier :blank-wall-ratio-cap])]
      {:schema schema :family family :tier tier :archetype archetype :entity-id entity-id
       :implementation-status :implemented :quality-claim :stylized-authored
       :settlement-link {:archetype archetype :detail-tier (:detail (tier-policy tier))}
       :detail-kit-link {:contract :kotoba.render/detail-kit-v1
                         :material-contract material-contract
                         :roles (set (map :role parts))}
       :pattern pattern
       :mesh-library {:box {:mesh (mesh/cube) :source :kotoba.render.mesh/cube}
                      :cylinder {:mesh (mesh/cylinder-pipe 0.5 0.0 1.0 8)
                                 :source :kotoba.render.mesh/cylinder-pipe}}
       :parts parts
       :rhythm (assoc coverage' :blank-wall-ratio-cap cap
                      :no-blank-wall-violation? (<= (:blank-wall-ratio coverage') cap)
                      :bay-count (:bays pattern) :floor-count (:floors pattern))
       :budget (budget parts tier)})))

(defn facade-lods [spec]
  (mapv #(facade-kit (assoc spec :tier %)) [:hero :mid :background]))

(defn for-settlement-instance
  "Resolve facade dimensions/archetype directly from a settlement instance.
   The caller supplies a stable unsigned seed so settlement and facade variation
   can share an authored identity without hidden global RNG state."
  [settlement instance seed]
  (let [[width height depth] (get-in instance [:collision :size])]
    (facade-kit {:family (:family settlement)
                 :tier (:tier settlement)
                 :archetype (:archetype instance)
                 :entity-id (:instance/id instance)
                 :seed seed :width width :height height :depth depth})))
