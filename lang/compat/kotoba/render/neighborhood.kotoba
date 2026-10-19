(ns kotoba.render.neighborhood
  "Portable stylized T/cross junction neighborhood composition v2."
  (:require [kotoba.render.building :as building]
            [kotoba.render.facade :as facade]
            [kotoba.render.mesh :as mesh]
            [kotoba.render.procedural :as procedural]
            [kotoba.render.road :as road]
            [kotoba.render.vegetation :as vegetation]))

(def schema :kotoba.render/neighborhood-composition-v2)
(def families #{:stylized :photoreal})
(def junctions #{:t :cross})
(def tiers #{:hero :mid :background})

(def tier-policy
  {:hero {:detail :high :draw-budget 180 :triangle-budget 18000 :building-count 4}
   :mid {:detail :medium :draw-budget 120 :triangle-budget 9000 :building-count 4}
   :background {:detail :low :draw-budget 64 :triangle-budget 4200 :building-count 3}})

(def material-palette
  {:road {:base-color [0.075 0.085 0.10 1.0] :metallic 0.02 :roughness 0.90}
   :marking {:base-color [0.88 0.76 0.38 1.0] :metallic 0.0 :roughness 0.72}
   :curb {:base-color [0.40 0.42 0.44 1.0] :metallic 0.0 :roughness 0.88}
   :sidewalk {:base-color [0.31 0.33 0.35 1.0] :metallic 0.0 :roughness 0.92}
   :verge {:base-color [0.18 0.32 0.14 1.0] :metallic 0.0 :roughness 0.96}})

(def ^:private directions
  {:north [0.0 1.0] :south [0.0 -1.0] :east [1.0 0.0] :west [-1.0 0.0]})

(def ^:private archetype-cycle [:depot :habitat :industrial :landmark])
(def ^:private building-profile
  {:depot {:variant :industrial-block :width 10.0 :depth 7.0 :height 6.0}
   :habitat {:variant :stepped-tower :width 7.0 :depth 6.0 :height 8.5}
   :industrial {:variant :industrial-block :width 12.0 :depth 9.0 :height 9.0}
   :landmark {:variant :stepped-tower :width 8.0 :depth 7.0 :height 18.0}})

(defn- quad-mesh [half-size y]
  [[(- half-size) y (- half-size) half-size y (- half-size)
    half-size y half-size (- half-size) y half-size]
   [0.0 1.0 0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0 1.0 0.0]
   [0.0 0.0 1.0 0.0 1.0 1.0 0.0 1.0]
   [0 2 1 0 3 2]])

(defn- active-directions [junction]
  (if (= junction :cross) [:north :east :south :west] [:north :east :west]))

(defn- road-spec [direction road-width junction-half extent terrain seed]
  (let [[dx dz] (directions direction)
        start (+ junction-half 0.02)]
    {:path [[(* dx start) (* dz start)] [(* dx extent) (* dz extent)]]
     :width road-width :shoulder 0.40 :camber 0.04 :shoulder-drop 0.035
     :clearance 0.025 :uv-scale 6.0 :base-subdivisions 8 :miter-limit 1.5
     :terrain (assoc terrain :seed seed)
     :marking (assoc road/default-marking :phase 1.0 :offsets [0.0])}))

(defn- road-library [junction tier road-width extent terrain seed]
  (let [detail (get-in tier-policy [tier :detail])
        half (+ (/ road-width 2.0) 0.5)]
    {:junction {:mesh (quad-mesh half 0.03) :material-role :road}
     :arms
     (into {}
           (for [direction (active-directions junction)
                 :let [spec (road-spec direction road-width half extent terrain seed)
                       parts (road/road-mesh-parts spec detail)]]
             [direction
              (into {} (for [[part generated] parts]
                         [part {:mesh generated
                                :material-role (case part :marking :marking :shoulder :verge :road)
                                :triangle-count (quot (count (nth generated 3)) 3)}]))]))}))

(defn- streetscape [road-width sidewalk-width extent]
  (let [inner (+ (/ road-width 2.0) 0.32)
        strip-length (* extent 1.72)
        strips (for [axis [:x :z] side [-1.0 1.0]
                     band [[:curb 0.20 0.16] [:sidewalk sidewalk-width 0.10]
                           [:verge (* sidewalk-width 0.72) 0.04]]
                     :let [[role breadth height] band
                           band-offset (+ inner (/ breadth 2.0)
                                          (case role :curb 0.0 :sidewalk 0.22 :verge (+ 0.22 sidewalk-width)))
                           offset (if (= axis :x) [0.0 0.0 (* side band-offset)]
                                      [(* side band-offset) 0.0 0.0])
                           size (if (= axis :x) [strip-length height breadth]
                                    [breadth height strip-length])]]
                 {:semantic role :geometry-ref :cube :material-role role
                  :transform {:offset offset :scale size :rotation [0.0 0.0 0.0]}
                  :collision {:mode (if (= role :sidewalk) :shell :none)
                              :visual-only? (not= role :sidewalk)}
                  :triangles 12})]
    (vec strips)))

(defn- building-instance [family tier seed safe-height index position]
  (let [archetype (nth archetype-cycle index)
        profile (building-profile archetype)
        ;; Facade parapets/vents can extend above the shell authoring height.
        ;; Reserve 8% headroom so actual visual extents obey the same safe cap.
        resolved-height (min (:height profile) (/ safe-height 1.08))
        spec (assoc profile :height resolved-height :seed (+ seed index))
        facade-kit (facade/facade-kit {:family family :tier tier :archetype archetype
                                       :entity-id (keyword (str "junction-building-" index))
                                       :seed (+ seed index) :width (:width profile)
                                       :depth (:depth profile) :height resolved-height})]
    {:instance/id (keyword (str "junction-building-" index))
     :archetype archetype :position [(first position) 0.0 (second position)]
     :grounded-y 0.0
     :shell {:collision {:mode :shell :shape :box
                         :size [(:width profile) resolved-height (:depth profile)]}
             :mesh-lods (building/building-lods spec)}
     :facade (assoc facade-kit :visual-only? true)}))

(defn- building-instances [family junction tier seed safe-height road-width sidewalk-width]
  (let [count (get-in tier-policy [tier :building-count])
        d (+ (/ road-width 2.0) sidewalk-width 8.0)
        slots [[(- d) (- d)] [d (- d)] [d d] [(- d) d]]
        ;; The missing south arm of a T junction gets a foreground building,
        ;; not a road-clipped shell.
        slots (if (= junction :t) (vec (cons [0.0 (- d)] slots)) slots)]
    (mapv #(building-instance family tier seed safe-height %1 %2) (range count) slots)))

(defn- seeded-jitter [seed salt extent]
  (* extent (- (/ (double (bit-and (procedural/coordinate-hash seed salt 5 239) 65535))
                    65535.0) 0.5)))

(defn- prop-descriptor [id semantic geometry material transform]
  {:descriptor/id id :semantic semantic :geometry geometry
   :material material :transform transform
   :collision {:mode :none :visual-only? true}})

(defn- anchor-zones [seed road-width sidewalk-width]
  (let [d (+ (/ road-width 2.0) sidewalk-width)
        cube {:geometry-ref :cube :mesh (mesh/cube)}
        shrub-spec {:variant :shrub :width 2.2 :depth 1.9 :height 1.5 :seed seed}
        shrub {:geometry-ref :kotoba.render.vegetation/vegetation-mesh
               :mesh (vegetation/vegetation-mesh shrub-spec :low)}]
    [{:zone/id :foreground-left :kind :foreground-props
      :bounds {:center [(- d) 0.0 (- d)] :size [5.0 0.0 4.0]}
      :collision {:mode :none :visual-only? true}
      :descriptors
      [(prop-descriptor :foreground-crate :crate cube (:curb material-palette)
                        {:offset [(+ (- d) (seeded-jitter seed 1 0.8)) 0.0 (- d)]
                         :scale [0.9 0.8 0.9] :rotation [0.0 (seeded-jitter seed 2 0.4) 0.0]})]}
     {:zone/id :foreground-right :kind :foreground-props
      :bounds {:center [d 0.0 (- d)] :size [5.0 0.0 4.0]}
      :collision {:mode :none :visual-only? true}
      :descriptors
      [(prop-descriptor :junction-bollard :bollard cube (:marking material-palette)
                        {:offset [(+ d (seeded-jitter seed 3 0.7)) 0.0 (- d)]
                         :scale [0.24 0.85 0.24] :rotation [0.0 0.0 0.0]})]}
     {:zone/id :verge-vegetation :kind :vegetation-cluster
      :bounds {:center [d 0.0 d] :size [7.0 0.0 7.0]}
      :collision {:mode :none :visual-only? true}
      :descriptors
      [(prop-descriptor :junction-shrub :shrub shrub (:verge material-palette)
                        {:offset [d 0.0 (+ d (seeded-jitter seed 4 1.2))]
                         :scale [1.0 1.0 1.0] :rotation [0.0 (seeded-jitter seed 5 1.0) 0.0]})]}]))

(defn- facade-max-y [building]
  (reduce max 0.0
          (for [part (get-in building [:facade :parts])
                :let [[_ y _] (get-in part [:transform :offset])
                      [_ h _] (get-in part [:transform :scale])]]
            (+ y h))))

(defn- evidence [buildings safe-height]
  (let [shell-heights (mapv #(get-in % [:shell :collision :size 1]) buildings)
        facade-heights (mapv facade-max-y buildings)
        actual-heights (mapv max shell-heights facade-heights)
        landmarks (filter #(= :landmark (:archetype %)) buildings)]
    {:schema :kotoba.render/neighborhood-evidence-v2
     :grounded-building-count (count (filter #(zero? (:grounded-y %)) buildings))
     :building-count (count buildings)
     :all-shells-grounded? (every? #(zero? (:grounded-y %)) buildings)
     :safe-height safe-height :resolved-shell-heights shell-heights
     :resolved-facade-extents facade-heights
     :max-building-height (reduce max 0.0 actual-heights)
     :skyline-within-safe-height? (every? #(<= % safe-height) actual-heights)
     :landmark-count (count landmarks)
     :no-floating-landmark? (every? #(zero? (:grounded-y %)) landmarks)
     :no-clipped-landmark? (every? #(<= (max (get-in % [:shell :collision :size 1])
                                                (facade-max-y %)) safe-height)
                                   landmarks)
     :framing {:requires-ground? true :junction-context-required? true
               :safe-look-height (* safe-height 0.42)
               :camera-min-height (* safe-height 0.30)
               :camera-max-height (* safe-height 0.88)}}))

(defn- budget [road-library streetscape buildings tier]
  (let [road-triangles (+ 2 (reduce + 0 (for [[_ parts] (:arms road-library)
                                               [_ part] parts] (:triangle-count part))))
        street-triangles (reduce + (map :triangles streetscape))
        detail (get-in tier-policy [tier :detail])
        lod-index ({:high 0 :medium 1 :low 2} detail)
        building-triangles (reduce + 0 (map #(get-in % [:shell :mesh-lods lod-index :triangle-count]) buildings))
        draws (+ 1 (reduce + (map count (vals (:arms road-library))))
                 (count streetscape) (count buildings)
                 (reduce + (map #(count (get-in % [:facade :parts])) buildings)))
        triangles (+ road-triangles street-triangles building-triangles)
        policy (tier-policy tier)]
    {:draws draws :draw-budget (:draw-budget policy)
     :triangles triangles :triangle-budget (:triangle-budget policy)
     :within-budget? (and (<= draws (:draw-budget policy))
                          (<= triangles (:triangle-budget policy)))}))

(defn neighborhood
  [{:keys [family junction tier entity-id seed road-width sidewalk-width extent terrain safe-height]
    :or {family :stylized junction :cross tier :mid entity-id :neighborhood seed 0
         road-width 8.0 sidewalk-width 2.2 extent 48.0
         terrain {:size 128.0 :base-segments 32 :amplitude 0.0 :seed 0}
         safe-height 24.0}}]
  (when-not (families family) (throw (ex-info "unsupported neighborhood family" {:family family})))
  (when-not (junctions junction) (throw (ex-info "unsupported junction" {:junction junction})))
  (when-not (tiers tier) (throw (ex-info "unsupported neighborhood tier" {:tier tier})))
  (if (= family :photoreal)
    {:schema schema :family family :junction junction :tier tier :entity-id entity-id
     :implementation-status :boundary-only :quality-claim :unsupported-future
     :mesh-library {} :streetscape [] :buildings [] :anchor-zones []
     :evidence {:status :not-authored} :budget {:within-budget? true}}
    (let [roads (road-library junction tier road-width extent terrain seed)
          street (streetscape road-width sidewalk-width extent)
          buildings (building-instances family junction tier seed safe-height road-width sidewalk-width)]
      {:schema schema :family family :junction junction :tier tier :entity-id entity-id
       :implementation-status :implemented :quality-claim :stylized-authored
       :mesh-library {:cube {:mesh (mesh/cube) :source :kotoba.render.mesh/cube}
                      :roads roads}
       :streetscape street :buildings buildings
       :anchor-zones (anchor-zones seed road-width sidewalk-width)
       :evidence (evidence buildings safe-height)
       :budget (budget roads street buildings tier)})))

(defn neighborhood-lods [spec]
  (mapv #(neighborhood (assoc spec :tier %)) [:hero :mid :background]))
