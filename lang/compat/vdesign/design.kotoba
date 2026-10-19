(ns vdesign.design
  "VehicleDesignActor — one clean-sheet vehicle design = one supervised
  actor, expressed as a langgraph-clj StateGraph. The DesignProposer is
  sealed into a single node; its concept is ALWAYS routed through the
  PhysicsGovernor before anything is emitted as a spec.

  One graph run = one design pass:

    require → propose → govern → decide ─┬─ (closes) ──▶ design-review ──▶ emit
                                         │              [interrupt-before]
                                         │               engineer signs off,
                                         │               resume ─▶ emit
                                         └─ (infeasible) ──────────────▶ emit
                                                          (rejected spec
                                                           + physics reasons)

  Mirrors robotaxi exactly: the proposer never ships a design the
  PhysicsGovernor hasn't closed; an infeasible concept falls back to a
  REJECTED spec (the design-side MRC) carrying the conservation-law
  violations as evidence; and `interrupt-before #{:design-review}` is a
  real human-in-the-loop sign-off, like robotaxi's teleop handoff.

  Run the SAME graph with {:powertrain :bev} and {:powertrain :fcev} to
  clean-sheet both architectures from one requirement set and compare."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [vdesign.proposer :as proposer]
            [vdesign.physics :as physics]
            [vdesign.simverify :as simverify]
            [vdesign.process :as process]
            [aero.case :as aero-case]
            [aero.bridge :as aero]
            [echem.solver :as echem]
            [motor.solver :as motor]))

(defn geometry-of
  "Package-envelope geometry for `concept`'s class — wheelbase and floor
  area come from simverify's per-class geom priors (previously internal to
  the crash/clash checks only); frontal area is the concept's own aero
  descriptor. This is a coarse envelope, not a styled body — it exists so
  a released spec carries SOME geometry for a CAD/CAM bridge to consume,
  where before it carried none (see `vdesign.cad`)."
  [concept]
  (let [gm (get simverify/geom (:class concept) (:sedan simverify/geom))]
    {:wheelbase-m     (:wheelbase gm)
     :floor-area-m2   (:floor-area gm)
     :frontal-area-m2 (:frontal-area concept)}))

(defn- glider-of [concept]
  (select-keys concept [:crr :cd :frontal-area :avg-speed :glider-mass
                        :gross-limit :p-aux-w]))

(defn- spec
  "Assemble the final, physics-closed vehicle spec + a coarse BOM-level
  mass/energy breakdown — the deliverable of a passed design pass."
  [concept verdict]
  (let [{:keys [store curb-mass-kg margins]} verdict]
    {:status        :released
     :class         (:class concept)
     :powertrain    (:powertrain concept)
     :range-km      (:range-km concept)
     :curb-mass-kg  (Math/round curb-mass-kg)
     :p-peak-kW     (:p-peak-kW store)
     :energy        (dissoc store :propulsion-mass-kg)
     :mass-budget   {:glider-kg  (:glider-mass concept)
                     :energy-store-kg (Math/round (double (:store-mass-kg store)))
                     :propulsion-kg   (Math/round (double (:propulsion-mass-kg store)))}
     :margins       margins
     :physics       {:regen-credit (if (= :bev (:powertrain concept)) 0.15 0.0)
                     :aux-power-w (:p-aux-w concept)
                     :body {:crr (:crr concept)
                            :cd (:cd concept)
                            :frontal-area (:frontal-area concept)
                            :avg-speed (:avg-speed concept)}}
     :geometry      (geometry-of concept)}))

(defn build
  "Compiles a VehicleDesignActor graph.
  opts: {:checkpointer cp}  (defaults: mem checkpointer)."
  [& [{:keys [checkpointer]
       :or   {checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:requirements {:default nil}
         :powertrain   {:default nil}   ; :bev | :fcev — the only branch
         :concept      {:default nil}   ; DesignProposer output (untrusted)
         :verdict      {:default nil}   ; PhysicsGovernor closure result
         :design       {:default nil}   ; released spec OR rejected spec
         :review       {:default nil}   ; engineer sign-off (set on resume)
         :verification {:default nil}   ; (a) genesis/cae-compat sim verdict
         :process      {:default nil}   ; (b) BOM → CAM → 4D assembly order
         :datoms       {:reducer into :default []}  ; kotoba Datom log
         :audit        {:reducer into :default []}}})

      ;; 1. Requirements intake (passthrough; arrives via input).
      (g/add-node :require (fn [s] s))

      ;; 2. DesignProposer — the contained concept generator (untrusted).
      (g/add-node :propose
        (fn [{:keys [requirements powertrain]}]
          (let [c (proposer/propose requirements powertrain)]
            {:concept c
             :audit   [{:t :proposed :powertrain powertrain
                        :target-curb-kg (:target-curb-kg c)
                        :rationale (:rationale c)}]})))

      ;; 2b. Aero — compute the REAL Cd (aero-clj reduced-order solver) and
      ;;     override the proposer's fixed Cd prior, so the energy sizing in
      ;;     :govern reflects this body's drag, not a class default (#2 wiring).
      (g/add-node :aero
        (fn [{:keys [concept]}]
          (let [acase (aero-case/for-vehicle
                       (select-keys concept [:class :powertrain :frontal-area]))
                res   (aero/run acase (:cd concept))
                cd'   (get-in res [:solve :Cd])]
            {:concept (assoc concept :cd cd' :cd-prior (:cd concept))
             :datoms  (:datoms res)
             :audit   [{:t :aero :cd-prior (:cd concept) :cd cd'
                        :range-mult (get-in res [:effect :range-mult])
                        :datoms (:datom-count res)}]})))

      ;; 2c. CAE probe — physics injected into the concept before sizing.
      ;;     motor (:rom-motor) sizes the traction motor mass for the peak-kW
      ;;     target (both powertrains); FCEV additionally gets echem (:rom-fc)
      ;;     stack efficiency from a polarization curve (#3 wiring).
      (g/add-node :cae-probe
        (fn [{:keys [concept]}]
          (let [m  (motor/size-for-power {:p-peak-kW (:p-peak-kw concept)})
                c1 (assoc concept :motor-mass-kg (:mass-kg m))
                a0 [{:t :motor :kW (:p-peak-kw concept)
                     :mass-kg (Math/round (double (:mass-kg m)))
                     :Nm-per-kg (:Nm-per-kg m) :eff (:eff-peak m)}]]
            (if (= :fcev (:powertrain concept))
              (let [r (echem/run {:case/id (str "veh-" (name (:class concept)) "-fcev/fc")})]
                {:concept (assoc c1 :fc-elec-eff (:eff-LHV r))
                 :datoms  (:datoms r)
                 :audit   (conj a0 {:t :echem :eff-LHV (:eff-LHV r)
                                    :v-cell (:v-cell r) :stack-kW (:stack-kW r)
                                    :datoms (:datom-count r)})})
              {:concept c1 :audit a0}))))

      ;; 3. PhysicsGovernor — independent conservation-law censor.
      (g/add-node :govern
        (fn [{:keys [concept powertrain]}]
          (let [v (physics/check powertrain (glider-of concept) concept)]
            {:verdict v
             :audit   [{:t :physics-verdict
                        :closes? (:closes? v)
                        :curb-mass-kg (Math/round (:curb-mass-kg v))
                        :iterations (:iterations v)
                        :spiral (:history v)
                        :violations (mapv :gate (:violations v))}]})))

      ;; 4. Decide: closed → carry a released spec; else → rejected spec
      ;;    (the design-side MRC) with the physics violations as evidence.
      (g/add-node :decide
        (fn [{:keys [concept verdict]}]
          (if (:closes? verdict)
            {:design (spec concept verdict)}
            {:design {:status :rejected
                      :class (:class concept)
                      :powertrain (:powertrain concept)
                      :range-km (:range-km concept)
                      :curb-mass-kg (Math/round (:curb-mass-kg verdict))
                      :violations (:violations verdict)}
             :audit  [{:t :physics-reject
                       :violations (mapv :gate (:violations verdict))}]})))

      ;; 5a. Human design review — paused on by interrupt-before (an
      ;;     engineer signs off the closed spec before release).
      (g/add-node :design-review
        (fn [{:keys [design]}]
          {:review {:status :approved   ; set by the reviewer on resume
                    :curb-mass-kg (:curb-mass-kg design)}
           :audit  [{:t :design-review :released (:status design)}]}))

      ;; 6. (a) SimGovernor — structural + collision verification on the
      ;;    Isaac/genesis-compat surface, hardened by per-env DR. A structural
      ;;    failure downgrades the (closed) design to rejected — a second MRC.
      (g/add-node :verify
        (fn [{:keys [design]}]
          (let [v (simverify/check design)]
            (cond-> {:verification v
                     :datoms (:datoms v)
                     :audit  [{:t :sim-verify :passed? (:passed? v)
                               :min-sf (Math/round (* 100.0 (:min-sf v)))
                               :solver "kami-genesis(isaacsim-compat)"
                               :datoms (:datom-count v)}]}
              (not (:passed? v))
              (assoc :design (assoc design :status :rejected
                                    :violations (->> (:checks v)
                                                     (remove :pass?)
                                                     (mapv #(hash-map :gate (:check %)
                                                                      :detail (:detail %))))))))))

      ;; 7. (b) ProcessPlanner — BOM → CAM (G-code) → 4D assembly order,
      ;;    all written to the kotoba Datom log. Runs only for a design that
      ;;    both closed (physics) and survived (sim).
      (g/add-node :process
        (fn [{:keys [design verification review]}]
          (let [p (process/plan design {:verification verification :review review})]
            {:process p
             :datoms  (:datoms p)
             :audit   [{:t :process-plan
                        :bom-lines (count (:bom-lines p))
                        :cam-jobs (count (:cam p))
                        :assembly-steps (count (:assembly p))
                        :line-takt-s (:takt-s p)
                        :datoms (:datom-count p)
                        :maturity (get-in p [:maturity :review :review/maturity])
                        :maturity-score (get-in p [:maturity :score :score/overall])
                        :maturity-coverage (get-in p [:maturity :coverage :coverage/score])}]})))

      ;; 8. Emit the (released or rejected) design.
      (g/add-node :emit
        (fn [{:keys [design]}]
          {:audit [{:t :emit :status (:status design)}]}))

      (g/set-entry-point :require)
      (g/add-edge :require :propose)
      (g/add-edge :propose   :aero)
      (g/add-edge :aero      :cae-probe)
      (g/add-edge :cae-probe :govern)
      (g/add-edge :govern  :decide)

      ;; Closed designs get human sign-off first; rejected ones emit directly.
      (g/add-conditional-edges :decide
        (fn [{:keys [design]}]
          (if (= :released (:status design))
            :design-review
            :emit)))
      ;; Released path: review → (a) verify → decide whether to plan process.
      (g/add-edge :design-review :verify)
      ;; A sim-rejected design skips manufacturing and emits; a survivor is
      ;; planned (b) then emitted.
      (g/add-conditional-edges :verify
        (fn [{:keys [verification]}]
          (if (:passed? verification) :process :emit)))
      (g/add-edge :process :emit)
      (g/set-finish-point :emit)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:design-review}})))
