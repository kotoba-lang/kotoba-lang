(ns vdesign.proposer
  "DesignProposer — the *contained concept generator*.

  This is the design-side analogue of robotaxi's sealed AR1 node: a
  capable but UNTRUSTED proposer. Given requirements it sketches a vehicle
  concept — glider mass, aero, peak power, package envelope, and an
  optimistic target curb mass. In production this is an LLM / generative
  CAD agent; here it is a deterministic heuristic so the actor runs
  offline and the closure contract is exercised end-to-end.

  CRITICAL: the proposer does NOT respect conservation laws. It will
  happily target a curb mass that no battery or tank can actually hit at
  the requested range. Every concept it emits is a *proposal*, never a
  spec — `vdesign.physics` (the PhysicsGovernor) censors it. So the
  proposer is free to be bold; the governor makes it honest."
  (:require [kotoba.lang.text :as str]))

;; Class library: propulsion-agnostic glider + envelope priors. A glider
;; is the vehicle MINUS its energy store and motor — body, chassis,
;; interior, suspension, wheels, brakes. Volume is what's left for the
;; energy store after cabin/cargo are carved out.
(def classes
  {:city    {:glider-mass 820  :crr 0.009 :cd 0.29 :frontal-area 2.20
             :avg-speed 13.9 :p-aux-w 700  :avail-volume-L {:bev 320 :fcev 160}
             :gross-limit 1700}
   :sedan   {:glider-mass 1080 :crr 0.009 :cd 0.24 :frontal-area 2.30
             :avg-speed 18.0 :p-aux-w 900  :avail-volume-L {:bev 420 :fcev 220}
             :gross-limit 2400}
   :suv     {:glider-mass 1320 :crr 0.010 :cd 0.30 :frontal-area 2.85
             :avg-speed 18.0 :p-aux-w 1100 :avail-volume-L {:bev 520 :fcev 290}
             :gross-limit 3000}
   :truck   {:glider-mass 2400 :crr 0.0065 :cd 0.36 :frontal-area 9.5
             :avg-speed 22.2 :p-aux-w 2500 :avail-volume-L {:bev 1400 :fcev 1150}
             :gross-limit 26000}})

(defn- peak-power-kW
  "Size traction power from a power-to-mass target for the class and a
  flat top-speed aero margin. Optimistic but physical enough to seed the
  spiral; the governor never trusts it on its own."
  [class-key glider payload-kg top-speed-ms]
  (let [base (get {:city 70 :sedan 110 :suv 150 :truck 350} class-key 110)
        aero (* 0.5 0.5 1.225 (:cd glider) (:frontal-area glider)
                (* top-speed-ms top-speed-ms top-speed-ms) 1e-3)] ; kW to hold Vmax
    (max base aero)))

(defn propose
  "Sketch a concept for `requirements` + `powertrain`. Returns a map the
  PhysicsGovernor will try (and may fail) to close into a real design.

  requirements: {:class :range-km :payload-kg :top-speed-kmh}
  The :target-curb-kg the proposer emits is its OPTIMISTIC guess — the
  number marketing wants — and is exactly what physics must validate."
  [{:keys [class range-km payload-kg top-speed-kmh]
    :or {class :sedan range-km 500 payload-kg 150 top-speed-kmh 160}}
   powertrain]
  (let [glider     (get classes class (:sedan classes))
        top-speed  (/ top-speed-kmh 3.6)
        p-peak     (peak-power-kW class glider payload-kg top-speed)
        ;; the proposer's bold guess: a curb mass it *hopes* holds, padded
        ;; only ~22% over the bare glider for the whole energy system.
        target     (Math/round (* (:glider-mass glider) 1.22))]
    {:class        class
     :powertrain   powertrain
     :range-km     range-km
     :payload-kg   payload-kg
     :top-speed-ms top-speed
     :crr          (:crr glider)
     :cd           (:cd glider)
     :frontal-area (:frontal-area glider)
     :avg-speed    (:avg-speed glider)
     :p-aux-w      (:p-aux-w glider)
     :p-peak-kw    p-peak
     :glider-mass  (:glider-mass glider)
     :gross-limit  (:gross-limit glider)
     :avail-volume-L (get-in glider [:avail-volume-L powertrain])
     :target-curb-kg target
     :rationale    (str "Clean-sheet " (name powertrain) " "
                        (name class) ", " range-km " km target. Glider "
                        (:glider-mass glider) " kg; proposer targets "
                        target " kg curb and " (Math/round (double p-peak))
                        " kW peak — pending physics closure.")}))
