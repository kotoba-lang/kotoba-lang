(ns vdesign.process
  "ProcessPlanner — (b) explode a verified spec into a BILL OF MATERIALS,
  generate a CAM process (toolpaths → G-code, kami-cam vocabulary) for the
  machined parts, and lay out the 4D ASSEMBLY ORDER (the giemon-factory
  `construction.order.json :seq` pattern). Everything is emitted as kotoba
  datoms, so the manufacturing process is queryable facts — the literal
  '組み立て工程まで全てデータ化' answer for this vehicle.

  CAM runs on the real `kotoba-lang/cnc` (`kotoba.cam.*`) engine — stock,
  tool library, toolpath generation (zigzag pocket + peck drill) and
  G-code post-processing — not a hand-rolled string template. The
  packaging-envelope BREP from `vdesign.cad` also gets its own CAM job
  (a design-review 'buck'), and every generated file is classified
  through `kotoba-lang/cad`'s artifact registry. The assembly order
  mirrors giemon-factory's staged reveal — here the BEV and FCEV diverge
  exactly where their energy systems do (pack-skateboard vs H2-tank +
  leak/fill stations)."
  (:require [vdesign.datom :as d]
            [vdesign.cad :as vcad]
            [kotoba.cad.core :as cad-core]
            [kotoba.cam.stock :as kstock]
            [kotoba.cam.tool :as ktool]
            [kotoba.cam.toolpath :as ktoolpath]
            [kotoba.cam.gcode :as kgcode]
            [kotoba.cam.vec3 :as kvec3]
            [kotoba.lang.text :as str]))

(def ^:const module-kWh 5.0)    ; battery module granularity
(def ^:const tank-kg    3.0)    ; usable H2 per 700-bar cylinder

;; ───────────────────────────── BOM ─────────────────────────────

(defn- bom
  "Major-assembly bill of materials with quantities + apportioned mass (kg)."
  [{:keys [powertrain energy mass-budget]}]
  (let [g  (:glider-kg mass-budget)
        p  (:propulsion-kg mass-budget)
        s  (:energy-store-kg mass-budget)
        ;; :propulsion-kg mirrors size-fcev's :propulsion-mass-kg, which for
        ;; FCEV is stack+buffer+motor combined (not motor-only like BEV).
        ;; The stack/buffer masses already get their own BOM lines below
        ;; (fc-stack / buffer-battery), so only the motor-only remainder of
        ;; `p` should be split into traction-motor/inverter+reducer here --
        ;; otherwise the stack/buffer mass is counted twice in the BOM.
        motor-p (if (= :fcev powertrain)
                  (- p (:stack-mass-kg energy 0) (:buffer-mass-kg energy 0))
                  p)
        common
        [{:part "body-in-white"      :qty 1 :mass (* g 0.42) :make :weld}
         {:part "closure-panel"      :qty 4 :mass (* g 0.10) :make :stamp}
         {:part "suspension-corner"  :qty 4 :mass (* g 0.16) :make :machine}
         {:part "wheel+tire"         :qty 4 :mass (* g 0.12) :make :buy}
         {:part "brake-corner"       :qty 4 :mass (* g 0.06) :make :buy}
         {:part "interior+seats"     :qty 1 :mass (* g 0.14) :make :buy}
         {:part "traction-motor"     :qty 1 :mass (* motor-p 0.55) :make :machine}
         {:part "inverter+reducer"   :qty 1 :mass (* motor-p 0.45) :make :machine}
         {:part "hv-harness"         :qty 1 :mass 18         :make :buy}
         {:part "thermal-loop"       :qty 1 :mass 22         :make :buy}]
        store
        (case powertrain
          :bev  (let [n (long (Math/ceil (/ (:nominal-kWh energy) module-kWh)))]
                  [{:part "battery-module" :qty n :mass (/ (* s 0.85) n) :make :buy}
                   {:part "pack-tray"      :qty 1 :mass (* s 0.15) :make :machine}])
          :fcev (let [n (long (Math/ceil (/ (:h2-kg energy) tank-kg)))]
                  [{:part "h2-tank-700bar" :qty n :mass (/ (:tank-mass-kg energy) n) :make :buy}
                   {:part "fc-stack"       :qty 1 :mass (:stack-mass-kg energy) :make :buy}
                   {:part "buffer-battery" :qty 1 :mass (:buffer-mass-kg energy) :make :buy}
                   {:part "tank-saddle"    :qty 2 :mass 9 :make :machine}]))]
    (vec (concat common store))))

;; ───────────────────────────── CAM ─────────────────────────────

(defn- kcam-tool-type [type-str]
  (case type-str "FaceMill" :face-mill "Drill" :drill :end-mill))

(defn- kcam-material [material-str]
  (case material-str "HSS" :hss "Titanium" :titanium-ti6al4v :carbide))

(defn- gcode
  "Real G-code for one milled part, via kotoba-lang/cnc: a tool library of
  one tool, a block stock sized to the estimated toolpath extent, a
  :pocket profile pass + :drill peck cycle for the part's holes, then
  toolpath generation + G-code post-processing (kotoba.cam.gcode)."
  [{:keys [tool feed-mm-min holes len-mm]}]
  (let [tool-map {:id (:n tool) :name (str (:type tool) " " (:material tool))
                  :tool-type (kcam-tool-type (:type tool))
                  :diameter 10.0 :flute-length 30.0 :overall-length 60.0
                  :flute-count 4 :corner-radius 0.0
                  :material (kcam-material (:material tool)) :coating "TiAlN"}
        [tool-lib _] (ktool/add (ktool/empty-library) tool-map)
        stock    (kstock/stock (kstock/block (max 140.0 (* 0.5 len-mm)) 100.0 20.0)
                                (kstock/aluminum-6061))
        hole-pts (mapv (fn [i] (kvec3/v3 (* 20 (inc i)) 40.0 0.0)) (range holes))
        job      (-> (ktoolpath/new-job stock tool-lib)
                     (ktoolpath/add-operation
                      {:op :pocket :tool-id (:n tool) :depth 2.0 :stepover 0.0
                       :strategy :zigzag :feed-rate (double feed-mm-min)
                       :pocket-min (kvec3/v3 0.0 0.0 0.0)
                       :pocket-max (kvec3/v3 120.0 80.0 0.0)})
                     (ktoolpath/add-operation
                      {:op :drill :tool-id (:n tool) :depth 8.0 :peck-depth 3.0
                       :feed-rate 300.0 :holes hole-pts}))
        segments (ktoolpath/generate-toolpath job)]
    (kgcode/generate-gcode segments)))

(defn- cam-jobs
  "CAM jobs for the make-by-machining parts in the BOM."
  [powertrain bom-lines]
  (let [machined (filter #(= :machine (:make %)) bom-lines)
        tool-for (fn [part]
                   (cond
                     (str/includes? part "motor")  {:n 3 :type "EndMill"  :material "Carbide"}
                     (str/includes? part "tray")   {:n 5 :type "FaceMill" :material "Carbide"}
                     (str/includes? part "saddle") {:n 7 :type "EndMill"  :material "HSS"}
                     :else                          {:n 9 :type "EndMill"  :material "Carbide"}))]
    (mapv (fn [{:keys [part qty mass]}]
            (let [tool   (tool-for part)
                  holes  (cond (str/includes? part "tray") 8
                               (str/includes? part "corner") 6 :else 4)
                  feed   1200
                  len-mm (+ 400 (* holes 30))                   ; toolpath length est.
                  job    {:part part :qty qty :tool tool :holes holes
                          :feed-mm-min feed :machine "Mill3Axis"
                          :stock "Block(AlSi10Mg)" :len-mm len-mm
                          :cycle-s (Math/round (* 60.0 (/ len-mm feed)))}]
              (assoc job :gcode (gcode job))))
          machined)))

;; ─────────────────────── packaging-envelope buck ───────────────────────

(defn- envelope-buck
  "A design-review 'buck' CAM job machined from the vehicle's packaging
  envelope (vdesign.cad's BREP box, tessellated to a mesh and fed
  straight into kotoba.cam.stock/from-mesh) — a single rough :pocket
  clearing pass, not a production part. Returns nil if `geometry` is
  missing (rejected designs never reach process/plan)."
  [geometry]
  (when geometry
    (let [solid (vcad/envelope-solid geometry)
          mesh  (vcad/envelope-mesh solid)
          stock (kstock/stock (kstock/from-mesh (:positions mesh) (:indices mesh))
                               (kstock/aluminum-6061))
          tool-map {:id 1 :name "Rough EndMill" :tool-type :end-mill
                    :diameter 25.0 :flute-length 50.0 :overall-length 100.0
                    :flute-count 3 :corner-radius 0.0 :material :carbide :coating nil}
          [tool-lib _] (ktool/add (ktool/empty-library) tool-map)
          dims  (:dims solid)
          job   (-> (ktoolpath/new-job stock tool-lib)
                    (ktoolpath/add-operation
                     {:op :pocket :tool-id 1 :depth (min 10.0 (:height-mm dims))
                      :stepover 0.0 :strategy :zigzag :feed-rate 800.0
                      :pocket-min (kvec3/v3 0.0 0.0 0.0)
                      :pocket-max (kvec3/v3 (:length-mm dims) (:width-mm dims) 0.0)}))
          segments (ktoolpath/generate-toolpath job)]
      {:dims dims
       :triangle-count (quot (count (:indices mesh)) 3)
       :gcode (kgcode/generate-gcode segments)
       :dxf (vcad/envelope-dxf dims)})))

;; ──────────────────────── artifact classification ────────────────────────

(defn- artifact-manifest
  "Classify every file this process plan produces through kotoba-lang/cad's
  artifact registry (`kotoba.cad.core/classify-artifact`) — the STL and
  DXF envelope exports and one G-code/NC file per CAM job."
  [vid cam-lines has-envelope?]
  (cond-> (mapv (fn [j] (cad-core/classify-artifact (str vid "/" (:part j) ".nc"))) cam-lines)
    has-envelope? (conj (cad-core/classify-artifact (str vid "-envelope.stl"))
                        (cad-core/classify-artifact (str vid "-envelope.dxf")))))

;; ─────────────────────── 4D assembly order ───────────────────────

(defn- assembly-order
  "Staged build sequence (giemon `:seq`), diverging by powertrain at the
  energy-system + EOL stations. Each step has a station, takt time and
  predecessor — a true 4D order, not a flat list."
  [powertrain]
  (let [common-head
        [{:seq 1 :step "floor-platform"   :station "BODY-1" :takt-s 95}
         {:seq 2 :step "underbody-rails"  :station "BODY-2" :takt-s 90}]
        energy
        (case powertrain
          :bev  [{:seq 3 :step "battery-pack-install" :station "EV-PACK" :takt-s 110}]
          :fcev [{:seq 3 :step "h2-tank-install"      :station "H2-TANK" :takt-s 120}
                 {:seq 4 :step "fc-stack-install"     :station "H2-FC"   :takt-s 100}])
        rest-steps
        ["body-on" "motor-subframe" "closures" "interior" "hv-harness"]
        eol
        (case powertrain
          :bev  ["coolant-fill" "charge-to-soc" "eol-roll-test" "commission"]
          :fcev ["coolant-fill" "h2-leak-test" "h2-fill" "eol-roll-test" "commission"])
        tail (concat rest-steps eol)
        start (inc (apply max (map :seq (concat common-head energy))))]
    (vec (concat common-head energy
                 (map-indexed
                  (fn [i s] {:seq (+ start i) :step s
                             :station (str "GA-" (inc i))
                             :takt-s (if (str/includes? s "test") 130 85)})
                  tail)))))

;; ───────────────────────────── plan ─────────────────────────────

(defn plan
  "Full process plan for a released `design`. Returns
  {:bom [..] :cam [..] :assembly [..] :envelope-buck .. :artifacts [..]
  :maturity .. :takt-s n :tx .. :datoms ..}. `:envelope-buck` (a
  design-review CAM job machined from the vehicle's BREP packaging
  envelope) and `:artifacts` (kotoba-lang/cad artifact classifications)
  are nil/empty when `design` carries no `:geometry` (older callers, or a
  design assembled before vdesign.design/geometry-of existed).

  `verification` (vdesign.simverify/check's result) and `review` (the
  StateGraph's human design-review sign-off, present only once its
  interrupt has been resumed) are optional — when supplied, `:maturity`
  is a real kotoba-lang/cad score/coverage/co-sientist-review computed
  from this plan's own artifacts and the pipeline's actual passed gates
  (see vdesign.cad/maturity); when omitted, `:maturity` is nil rather
  than guessed."
  [design & [{:keys [verification review]}]]
  (let [pt      (:powertrain design)
        lines   (bom design)
        cam     (cam-jobs pt lines)
        order   (assembly-order pt)
        takt    (apply max (map :takt-s order))
        vid     (str "veh-" (name (:class design)) "-" (name pt))
        buck    (envelope-buck (:geometry design))
        artifacts (artifact-manifest vid cam (some? buck))
        maturity (when (seq artifacts)
                   (vcad/maturity {:design design :verification verification
                                   :artifacts artifacts :review review}))
        bom-ents (map-indexed
                  (fn [i l] (d/entity :BomLine (str vid "/p" i)
                                      {:vehicle vid :part (:part l) :qty (:qty l)
                                       :massKg (Math/round (double (:mass l)))
                                       :make (name (:make l))}))
                  lines)
        cam-ents (map (fn [j] (d/entity :CamJob (str vid "/cam/" (:part j))
                                        {:vehicle vid :part (:part j)
                                         :machine (:machine j) :stock (:stock j)
                                         :tool (str (get-in j [:tool :type]) "/"
                                                    (get-in j [:tool :material]))
                                         :cycleS (:cycle-s j)
                                         :gcodeLines (count (str/split-lines (:gcode j)))}))
                      cam)
        asm-ents (map (fn [s] (d/entity :AssemblyStep (str vid "/seq/" (:seq s))
                                        {:vehicle vid :seq (:seq s) :step (:step s)
                                         :station (:station s) :taktS (:takt-s s)}))
                      order)
        buck-ents (when buck
                    [(d/entity :EnvelopeBuck (str vid "/envelope")
                               {:vehicle vid
                                :lengthMm (Math/round (get-in buck [:dims :length-mm]))
                                :widthMm  (Math/round (get-in buck [:dims :width-mm]))
                                :heightMm (Math/round (get-in buck [:dims :height-mm]))
                                :triangleCount (:triangle-count buck)})])
        maturity-ents (when maturity
                        [(d/entity :CadMaturity (str vid "/maturity")
                                   {:vehicle vid
                                    :stage (:stage maturity)
                                    :approvals (count (:approvals maturity))
                                    :scoreOverall (get-in maturity [:score :score/overall])
                                    :coverageScore (get-in maturity [:coverage :coverage/score])
                                    :mrl (name (get-in maturity [:review :review/maturity]))})])
        ledger   (d/log (concat bom-ents cam-ents asm-ents buck-ents maturity-ents))]
    {:bom-lines lines
     :cam cam
     :assembly order
     :envelope-buck buck
     :artifacts artifacts
     :maturity maturity
     :takt-s takt
     :tx (:tx ledger)
     :datoms (:datoms ledger)
     :datom-count (:count ledger)}))
