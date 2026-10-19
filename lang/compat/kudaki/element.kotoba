(ns kudaki.element
  "Element internal-force kernels and per-element critical time step.

  In an explicit code there is NO global stiffness matrix. The whole solver is a
  gather of element internal forces F_int = Σₑ ∫ Bᵀσ dV from the current
  deformed configuration; nonlinearity (large rotation, plastic flow) is just a
  different σ. Each kernel is a PURE function

      (internal-force model el pos dpos state)
        → {:fint {node-id [fx fy fz] …}   nodal internal force (resisting)
           :state state'                  carried stress + plastic state
           :dt-crit Δtₑ                   CFL limit for this element
           :plastic-work Δwₚ}

  where `pos` are current nodal positions and `dpos` the displacement INCREMENT
  over the step (Δu = v^{n+½}Δt); the strain increment Δε = B·Δu drives the
  incremental material update so plasticity threads correctly.

  Two element families ship in S0:

    • truss/rod — 2-node 1-D axial member. Force is the axial stress × area
      acting along the CURRENT bar axis (large-displacement / corotational), so
      a rotating bar stays in equilibrium. Δt_crit = L/c, c = √(E/ρ).

    • hex — 8-node solid with ONE-POINT (centroid) integration: B is evaluated
      once at the centroid, F_int = V·Bᵀσ. One-point integration is cheap and
      locking-free but admits zero-energy HOURGLASS modes (deformations the
      single Gauss point cannot see). A stiffness-form hourglass force on the
      four hex hourglass base vectors Γ resists them. The Γ are orthogonal to
      every linear displacement field, so a uniform-strain PATCH TEST excites no
      hourglass force and recovers Young's modulus exactly."
  (:require [kudaki.linalg :as la]
            [kudaki.material :as mat]
            [kudaki.mesh :as mesh]))

(defn init-state
  "Fresh element state: zero stress, zero equivalent plastic strain. Truss
  carries a scalar axial stress; hex carries a Voigt 6-vector."
  [el]
  {:stress (if (= (:type el) :truss) 0.0 la/zero6)
   :mat-state {:eq-plastic-strain 0.0}})

;; ---------------------------------------------------------------------------
;; truss / rod
;; ---------------------------------------------------------------------------

(defn truss-force
  "Internal force of a 2-node axial bar. Reference length L₀ fixes the wave
  speed / critical step; the current axis n̂ and length L give the corotational
  axial strain increment Δε = n̂·(Δu₁−Δu₀)/L. The axial stress is advanced by the
  1-D material update and N = σ·A; tension (N>0) pulls the two ends together."
  [model el pos dpos state]
  (let [[a b]  (:nodes el)
        m      (get-in model [:materials (:mat el)])
        A      (get-in el [:section :area] 1.0)
        rho    (:density m 1.0)
        E      (:E m)
        x0     (pos a), x1 (pos b)
        ax     (la/v- x1 x0)
        L      (la/norm ax)
        n      (la/normalize ax)
        L0     (la/norm (la/v- (mesh/coord model b) (mesh/coord model a)))
        du     (la/v- (dpos b) (dpos a))
        deps   (/ (la/dot n du) L)
        s0     (:stress state)
        ;; axial-update returns the advanced material state under :state — bind it as
        ;; mat-state so the truss THREADS plastic strain (hence isotropic hardening)
        ;; across steps. (Same `:mat-state` destructure slip the hex had; dissipation
        ;; still flowed, so the Taylor-bar test never caught the lost hardening.)
        {:keys [stress plastic-work] mat-state :state}
        (mat/axial-update m s0 deps (:mat-state state))
        N      (* stress A)
        fn1    (la/vscale N n)          ; force on node b (+axis)
        c      (Math/sqrt (/ E rho))]
    {:fint  {a (la/vscale -1.0 fn1) b fn1}
     :state {:stress stress :mat-state mat-state}
     :dt-crit (/ L c)
     ;; trapezoidal internal-energy increment ΔU = ½(σⁿ+σⁿ⁺¹)·Δε·V (drift-free)
     :energy-incr (* 0.5 (+ s0 stress) deps A L)
     :plastic-work (* (or plastic-work 0.0) A L0)}))

;; ---------------------------------------------------------------------------
;; hex (8-node, 1-point integration) + hourglass control
;; ---------------------------------------------------------------------------

;; Four hourglass base vectors Γα for the 8-node hex (Flanagan–Belytschko),
;; built from products of the corner isoparametric coords: ηζ, ζξ, ξη, ξηζ.
;; Each is orthogonal to constant and linear displacement fields.
(def hg-gamma
  (vec (for [prod [(fn [[x y z]] (* y z))
                   (fn [[x y z]] (* z x))
                   (fn [[x y z]] (* x y))
                   (fn [[x y z]] (* x y z))]]
         (mapv prod mesh/hex-local))))

(defn- hex-gradients
  "∂Nₐ/∂x at the centroid for all 8 nodes: gradN[a] = (J⁻¹)ᵀ·(⅛ ξₐ). Returns
  [grad-vectors det-J]; det-J>0 ⇒ proper orientation, V = 8·detJ."
  [xs]
  (let [J    (mesh/hex-jacobian xs)
        detJ (la/det3 J)
        JiT  (la/transpose3 (la/inv3 J))]
    [(mapv (fn [lc] (la/mat*vec JiT (la/vscale 0.125 lc))) mesh/hex-local)
     detJ]))

(defn hex-force
  "Internal force of an 8-node 1-point hex. Δε = Σ Bₐ·Δuₐ (engineering shear
  Voigt), stress advanced by the 3-D material update, F_intₐ = V·Bₐᵀσ. A
  stiffness-form hourglass force −k_hg Σα Γα(Γα·u) on each Cartesian component
  damps the zero-energy modes; k_hg ∝ (λ+2μ)·V^{1/3}·q_hg."
  [model el pos dpos state]
  (let [ids   (:nodes el)
        m     (get-in model [:materials (:mat el)])
        rho   (:density m 1.0)
        {:keys [lambda mu]} (mat/lame (:E m) (:nu m))
        xs    (mapv pos ids)
        [g detJ] (hex-gradients xs)
        V     (* 8.0 detJ)
        ;; strain increment Δε = Σ Bₐ Δuₐ (Voigt, engineering shear)
        deps  (reduce
               (fn [e a]
                 (let [[gx gy gz] (nth g a)
                       [ux uy uz] (dpos (nth ids a))]
                   [(+ (e 0) (* gx ux))
                    (+ (e 1) (* gy uy))
                    (+ (e 2) (* gz uz))
                    (+ (e 3) (+ (* gy ux) (* gx uy)))
                    (+ (e 4) (+ (* gz uy) (* gy uz)))
                    (+ (e 5) (+ (* gz ux) (* gx uz)))]))
               la/zero6
               (range 8))
        s0    (:stress state)
        ;; stress-update-3d returns the advanced material state under :state — bind it
        ;; as mat-state so the hex actually THREADS plastic strain across steps. (A
        ;; prior `:mat-state` destructure silently dropped it to nil; trusses use the
        ;; separate axial path, so the elastic-only hex tests never exposed it.)
        {:keys [stress plastic-work] mat-state :state}
        (mat/stress-update-3d m s0 deps (:mat-state state))
        ;; trapezoidal internal-energy increment ΔU = ½(σⁿ+σⁿ⁺¹):Δε·V. Voigt work
        ;; conjugacy with engineering shear ⇒ a plain 6-vector dot (no factor 2).
        e-incr (* 0.5 V (reduce + (map (fn [a b c] (* (+ a b) c)) s0 stress deps)))
        [sxx syy szz sxy syz szx] stress
        ;; F_intₐ = V Bₐᵀ σ
        fint-base
        (into {}
              (map (fn [a]
                     (let [[gx gy gz] (nth g a)]
                       [(nth ids a)
                        [(* V (+ (* gx sxx) (* gy sxy) (* gz szx)))
                         (* V (+ (* gy syy) (* gx sxy) (* gz syz)))
                         (* V (+ (* gz szz) (* gy syz) (* gx szx)))]]))
                   (range 8)))
        ;; --- hourglass stabilization (stiffness form) ---
        u-tot (mapv (fn [a id] (la/v- (pos id) (mesh/coord model id))) (range 8) ids)
        khg   (* 0.05 (+ lambda (* 2.0 mu)) (Math/cbrt V))
        fint
        (reduce
         (fn [acc gamma]
           ;; hourglass generalized displacement qα,dir for this base vector
           (let [qx (reduce + (map (fn [a] (* (nth gamma a) ((nth u-tot a) 0))) (range 8)))
                 qy (reduce + (map (fn [a] (* (nth gamma a) ((nth u-tot a) 1))) (range 8)))
                 qz (reduce + (map (fn [a] (* (nth gamma a) ((nth u-tot a) 2))) (range 8)))]
             (reduce
              (fn [acc a]
                (let [id (nth ids a) ga (nth gamma a)
                      [fx fy fz] (acc id)]
                  (assoc acc id [(- fx (* khg ga qx))
                                 (- fy (* khg ga qy))
                                 (- fz (* khg ga qz))])))
              acc
              (range 8))))
         fint-base
         hg-gamma)
        ;; stored hourglass energy ½ k_hg Σα (qα·qα) — the artificial energy the
        ;; stabilization holds; should be a negligible fraction of the real
        ;; internal energy, and is the diagnostic for an under-stabilized mesh.
        hg-energy
        (reduce
         (fn [e gamma]
           (let [qx (reduce + (map (fn [a] (* (nth gamma a) ((nth u-tot a) 0))) (range 8)))
                 qy (reduce + (map (fn [a] (* (nth gamma a) ((nth u-tot a) 1))) (range 8)))
                 qz (reduce + (map (fn [a] (* (nth gamma a) ((nth u-tot a) 2))) (range 8)))]
             (+ e (* 0.5 khg (+ (* qx qx) (* qy qy) (* qz qz))))))
         0.0
         hg-gamma)
        cd    (Math/sqrt (/ (+ lambda (* 2.0 mu)) rho))
        Lc    (Math/cbrt V)]
    {:fint  fint
     :state {:stress stress :mat-state mat-state}
     :dt-crit (/ Lc cd)
     :energy-incr e-incr
     :hg-energy hg-energy
     :plastic-work (* (or plastic-work 0.0) V)}))

;; ---------------------------------------------------------------------------
;; dispatch
;; ---------------------------------------------------------------------------

(defn internal-force
  "Dispatch to the per-type kernel."
  [model el pos dpos state]
  (case (:type el)
    :truss (truss-force model el pos dpos state)
    :hex   (hex-force   model el pos dpos state)))

(defn strain-energy
  "Recoverable (elastic) strain energy currently stored in an element, from its
  stored stress. Truss: ½σ²/E·V. Hex: ½σ:C⁻¹σ·V via the isotropic compliance.
  Subtracting this from the cumulative internal energy gives the (irreversible)
  plastic dissipation — a bound-respecting figure, unlike a running Σσ·Δε̄p which
  inflates under load reversal."
  [model el state]
  (let [m (get-in model [:materials (:mat el)])]
    (case (:type el)
      :truss (let [sig (:stress state)
                   [a b] (:nodes el)
                   V (* (get-in el [:section :area] 1.0)
                        (la/norm (la/v- (mesh/coord model b) (mesh/coord model a))))]
               (* 0.5 (/ (* sig sig) (:E m)) V))
      :hex   (let [[xx yy zz xy yz zx] (:stress state)
                   E (:E m) nu (:nu m) G (/ E (* 2.0 (+ 1.0 nu)))
                   ex (/ (- xx (* nu (+ yy zz))) E)
                   ey (/ (- yy (* nu (+ xx zz))) E)
                   ez (/ (- zz (* nu (+ xx yy))) E)
                   V  (* 8.0 (la/det3 (mesh/hex-jacobian (mapv #(mesh/coord model %) (:nodes el)))))]
               (* 0.5 V (+ (* xx ex) (* yy ey) (* zz ez)
                           (/ (* xy xy) G) (/ (* yz yz) G) (/ (* zx zx) G)))))))
