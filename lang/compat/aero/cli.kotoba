(ns aero.cli
  "Demo: compute Cd for the same sedan as BEV and FCEV via the reduced-order
  build-up, show the component decomposition, and feed each Cd back into the
  range loop against vehicle-design-actor's fixed prior (Cd 0.24).

  Run: clojure -M:run"
  (:require [aero.case :as case]
            [aero.solver :as solver]
            [aero.bridge :as bridge]))

(defn- line [& xs] (println (apply str xs)))
(defn- c3 [x] (/ (Math/round (* 1000.0 (double x))) 1000.0))      ; 3 dp
(defn- pct [x] (/ (Math/round (* 10.0 (* 100.0 (double x)))) 10.0))

(def ^:const prior-cd 0.24)   ; the fixed sedan prior in vehicle-design-actor

(defn- report [label spec]
  (let [c   (case/for-vehicle spec)
        s   (solver/solve c)
        r   (bridge/run c prior-cd)
        eff (:effect r)]
    (line "\n" label "  (" (:case/id c) ")")
    (line "   Cd = " (c3 (:Cd s)) "  CdA = " (c3 (:CdA s)) " m²  Re = "
          (format "%.2e" (double (:Re s))) "  | solver " (name (:solver s)))
    (line "   breakdown (drag counts ×1e-4):")
    (doseq [[k v] (sort-by (comp - val) (:breakdown s))]
      (line "      " (format "%-10s" (name k)) " " (Math/round (* 1.0e4 v))
            (apply str (repeat (quot (Math/round (* 1.0e4 v)) 2) "▉"))))
    (line "   range loop vs prior Cd " prior-cd ":  Cd " (c3 (:Cd s))
          " → energy ×" (c3 (:energy-ratio eff)) " → range ×" (c3 (:range-mult eff))
          "  (" (if (>= (:range-mult eff) 1.0) "+" "") (pct (- (:range-mult eff) 1.0))
          "% range)")
    (line "   datafied: " (:datom-count r) " datoms")))

(defn -main [& _]
  (line "── aero-clj — reduced-order Cd (component build-up) → range loop ──")
  (line "same sedan body, BEV vs FCEV; prior Cd in vehicle-design-actor = " prior-cd)
  (report "BEV  sedan" {:class :sedan :powertrain :bev  :v-inf-kmh 120})
  (report "FCEV sedan" {:class :sedan :powertrain :fcev :v-inf-kmh 120})
  (line "\n── why they differ ──")
  (line "  FCEV underbody is rougher (tanks/exhaust) and needs more cooling-open")
  (line "  area than a BEV's flat battery floor → higher Cd → shorter range.")
  (line "\ndone."))
