(ns aero.lbm
  "High-fidelity aero backend — registers `:lbm` on the shared cae-solver
  contract by shelling out to the clean-room `kami-cfd` (Rust D2Q9
  lattice-Boltzmann) binary. So the SAME aero case answered by `:rom-buildup`
  (aero.solver) can instead be answered by a resolved-flow LBM, caller
  unchanged: (cae.solver/solve (assoc case :solver {:kind :lbm})).

  JVM-only (uses clojure.java.shell). The binary is located via the
  KAMI_CFD_BIN env var, else the sibling west-project build path. kami-cfd
  returns a 2D *sectional* drag coefficient; we apply a documented calibration
  constant to map it to a vehicle-scale Cd (full 3D Cd needs the D3Q19
  extension — calibration is the honest interim bridge)."
  (:require [cae.solver :as cae]
            [clojure.edn :as edn]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; kami-cfd returns drag inflated by the coarse domain (2D sectional, or 3D
;; frontal-area-normalised but high-blockage). These constants anchor each mode
;; to the rom-buildup reference (sedan ≈ 0.248); retune against tunnel/large-
;; domain CFD. The RANKING from the LBM is exact; only the scale is calibrated.
(def ^:const calibration-2d 0.081)   ; sectional block ≈ 3.06 → 0.248
;; 3D runs with Smagorinsky LES (turbulent high-Re plateau) and free-slip
;; far-field side/top walls (no-slip only on the road), so confinement no longer
;; inflates the drag. The residual scale gap (~1.8 vs ~0.3) is now mainly the
;; coarse grid + crude box geometry; a fine grid on real geometry (GPU) shrinks
;; it. Anchors fastback3d @Re3000 (≈1.79) to the rom-buildup sedan ref.
(def ^:const calibration-3d 0.139)   ; vehicle fastback3d ≈ 1.79 → 0.249

(defn- resolve-bin []
  (or (System/getenv "KAMI_CFD_BIN")
      (->> ["../kami-cfd/target/aarch64-apple-darwin/release/kami-cfd"
            "../kami-cfd/target/release/kami-cfd"
            "kami-cfd"]
           (filter #(or (= % "kami-cfd") (.exists (io/file %))))
           first)))

(defn- shape-of
  "Map the aero case's afterbody taper to a kami-cfd body profile, per dim.
  3D: fastback (tapered roof) vs squareback box; 2D: teardrop vs block."
  [case dim]
  (let [streamlined? (>= (get-in case [:shape :taper] 0.5) 0.5)]
    (if (= dim 3)
      (if streamlined? "fastback3d" "box3d")
      (if streamlined? "teardrop" "block"))))

(defn solve
  "Run kami-cfd for `case`; return {:Cd .. :solver :lbm ..}. `:solver {:dim 3}`
  selects the D3Q19 vehicle-Cd solver; default is the 2D sectional solver."
  [case]
  (let [bin   (or (resolve-bin)
                  (throw (ex-info "kami-cfd binary not found; set KAMI_CFD_BIN or build it"
                                  {:tried "KAMI_CFD_BIN / sibling kami-cfd build"})))
        dim   (get-in case [:solver :dim] 2)
        shape (shape-of case dim)
        re    (get-in case [:solver :re] (if (= dim 3) 3000 100))   ; 3D: LES high-Re plateau
        steps (get-in case [:solver :steps] (if (= dim 3) 1500 3000))
        {:keys [exit out err]} (sh/sh bin shape (str re) (str steps))
        _     (when-not (zero? exit)
                (throw (ex-info "kami-cfd failed" {:exit exit :err err})))
        m     (edn/read-string (str/trim out))
        raw   (or (:vehicle-cd m) (:sectional-cd m))
        k     (if (= dim 3) calibration-3d calibration-2d)
        cd    (* k raw)]
    {:Cd cd :CdA (* cd (:frontal-area case))
     :raw-cd raw :dim dim :backend :kami-cfd
     :shape (keyword shape) :solver :lbm}))

;; Register the high-fidelity backend on the shared contract.
(defmethod cae/solve :lbm [case] (solve case))
