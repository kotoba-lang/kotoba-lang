(ns echem.solver
  "Reduced-order PEM fuel-cell solver (:rom-fc) — a single-cell polarization
  curve V(i) = E0 − activation − ohmic − concentration, summed over the stack.
  Cell voltage sets LHV efficiency (η = V / 1.254 V thermoneutral), and the
  operating current density sets stack power. This is the right 'combustion'
  analog for an FCEV: electrochemistry, not chemistry. Turns the design's fixed
  stack-efficiency assumption into a computed η → real H2 consumption.

  A detailed kami-echem (channel CFD + electrochem) registers :pemfc-cfd on the
  same cae-solver contract for higher fidelity."
  (:require [datom.core :as d]
            [cae.solver :as cae]))

(def ^:const V-thermoneutral-LHV 1.254)   ; V; η_LHV = V_cell / 1.254

(defn solve
  "case: {:cells :area-cm2 :i-density(A/cm²) :T-K :pt-loading
          :E0 :tafel-b :i0 :R-ohm :i-lim :conc-c}"
  [{:keys [cells area-cm2 i-density T-K pt-loading
           E0 tafel-b i0 R-ohm i-lim conc-c]
    :or {cells 370 area-cm2 300 i-density 1.2 T-K 353 pt-loading 0.2
         E0 1.00 tafel-b 0.045 i0 1.0e-2 R-ohm 0.08 i-lim 2.0 conc-c 0.04}}]
  (let [i      i-density
        i0eff  (* i0 (/ pt-loading 0.2))               ; exchange current ∝ Pt
        act    (* tafel-b (Math/log (/ i i0eff)))      ; activation overpotential
        ohm    (* i R-ohm)                             ; ohmic (R in Ω·cm²)
        conc   (if (< i i-lim) (* (- conc-c) (Math/log (- 1.0 (/ i i-lim)))) 0.5)
        v-cell (max 0.0 (- E0 act ohm conc))
        eff    (/ v-cell V-thermoneutral-LHV)
        p-w    (* v-cell i area-cm2 cells)             ; W = V·(A/cm²)·cm²·cells
        p-kw   (/ p-w 1000.0)]
    {:v-cell v-cell :eff-LHV eff :stack-kW p-kw
     :cells cells :i-density i :T-K T-K
     :overpotentials {:act act :ohm ohm :conc conc}
     :solver :rom-fc}))

(defmethod cae/solve :rom-fc [case] (solve case))

(defn run [case]
  (let [r   (solve case)
        cid (or (:case/id case) "fc-0")
        ent (d/entity "echem" :FuelCellRun cid
                      {:cells   (:cells r)
                       :vCellMv (Math/round (* 1000.0 (:v-cell r)))
                       :effPct  (Math/round (* 1000.0 (:eff-LHV r)))
                       :stackKW (Math/round (double (:stack-kW r)))})
        led (d/log [ent])]
    (assoc r :datoms (:datoms led) :datom-count (:count led))))
