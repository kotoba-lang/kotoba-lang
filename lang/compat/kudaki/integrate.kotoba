(ns kudaki.integrate
  "The explicit CENTRAL-DIFFERENCE driver — the one module that owns the time
  loop, and it owns it as a pure fold over steps so a host can checkpoint/replay
  every cycle (the same audit-ledger ethos as the actors' StateGraphs).

  With a lumped (diagonal) mass matrix the semi-discrete equation of motion
  M a = F_ext − F_int − F_contact inverts elementwise — no linear solve:

      a^n        = M⁻¹ (F_ext − F_int(σ^n) − F_contact)
      v^{n+½}    = v^{n−½} + Δt · a^n
      u^{n+1}    = u^n + Δt · v^{n+½}
      σ^{n+1}    = material(σ^n, Δε = B·Δu)        (stress lags the move)

  Each step makes TWO element passes: one with a zero increment to read the
  current internal force F_int(σ^n) and the per-element CFL limit, then — after
  the move is known — one with the real Δu to advance the stresses. The two-pass
  split keeps the single element kernel reusable and the cycle textbook-clean.

  STABILITY is conditional: Δt < Δt_crit = L_min/c (the CFL/Courant limit, c the
  wave speed). We take 0.9·min-element-Δt_crit. MASS SCALING trades fidelity for
  speed by adding mass to the sub-critical elements so their Δt_crit rises to a
  target — the classic explicit-code lever for stiff/small elements.

  An ENERGY LEDGER accumulates external work, internal (strain+plastic) energy
  and contact energy by Σ F·Δu each step, alongside the current kinetic energy.
  For an undamped run KE + internal − external − contact ≈ 0: the balance
  residual, normalized by the peak energy, is the integrator's self-check."
  (:require [kudaki.linalg :as la]
            [kudaki.mesh :as mesh]
            [kudaki.element :as el]
            [kudaki.contact :as contact]))

(def ^:const safety 0.9)

(defn- combine
  "Add force map `b` into `a` (node-id → vec3)."
  [a b]
  (reduce-kv (fn [m id f] (update m id (fnil la/v+ [0.0 0.0 0.0]) f)) a b))

(defn- zero-disp [ids] (zipmap ids (repeat [0.0 0.0 0.0])))

(defn- assemble
  "Pass-1 internal forces from the CURRENT stored stresses (zero increment), the
  per-element critical Δt list, and the contact forces at the current geometry."
  [model pos elem-states contacts zerod]
  (let [per (mapv (fn [e st] (el/internal-force model e pos zerod st))
                  (:elements model) elem-states)
        fint (reduce (fn [acc r] (combine acc (:fint r))) {} per)]
    {:fint fint
     :dt-crits (mapv :dt-crit per)
     :fcont (contact/forces contacts pos)}))

(defn- advance-stresses
  "Pass-2: advance each element's stress with the real displacement increment
  `dpos`. Returns [new-states Σplastic-work Σinternal-energy-increment]. The
  internal energy uses the element's trapezoidal stress-work ΔU=½(σⁿ+σⁿ⁺¹):Δε·V,
  which is drift-free (unlike a left-endpoint Fᵢₙₜ·Δu work integral)."
  [model pos dpos elem-states]
  (reduce
   (fn [[states pw ue] [e st]]
     (let [r (el/internal-force model e pos dpos st)]
       [(conj states (:state r)) (+ pw (:plastic-work r)) (+ ue (:energy-incr r 0.0))]))
   [[] 0.0 0.0]
   (map vector (:elements model) elem-states)))

(defn- scaled-mass
  "Lumped nodal mass with optional mass scaling to a target Δt. For every
  element whose Δt_crit < target, its nodal mass share is multiplied by
  (target/Δt_crit)² so its effective critical step rises to the target."
  [model dt-crits target]
  (reduce
   (fn [m [el dte]]
     (let [rho (get-in model [:materials (:mat el) :density] 1.0)
           v   (mesh/element-volume model el)
           nn  (count (:nodes el))
           s   (if (and target (< dte target)) (let [r (/ target dte)] (* r r)) 1.0)
           per (* s (/ (* rho v) nn))]
       (reduce (fn [m id] (update m id (fnil + 0.0) per)) m (:nodes el))))
   {}
   (map vector (:elements model) dt-crits)))

(defn kinetic-energy
  "½ Σ mᵢ |vᵢ|² over the nodes."
  [mass vel]
  (reduce-kv (fn [s id v] (+ s (* 0.5 (mass id) (la/dot v v)))) 0.0 vel))

(defn- ext-map
  "Resolve the external-force spec at time t into a node-id → vec3 map."
  [f-ext t]
  (cond (nil? f-ext) {}
        (fn? f-ext)  (f-ext t)
        :else        f-ext))

(defn run
  "Run the explicit integrator. `opts`:
    :v0            map node-id → initial velocity vec3        (default 0)
    :steps         number of time steps                       (required)
    :dt            fixed step; else 0.9·min Δt_crit           (optional)
    :mass-scale-dt raise sub-critical element masses to this  (optional)
    :f-ext         node→force map, or (fn [t]) → map          (optional)
    :contacts      list of contact specs (see kudaki.contact) (optional)
    :fixed         map node-id → [bx by bz] booleans, fix DOF (optional)
    :sample-every  record a history frame every N steps       (default steps)

  Returns {:t :step :pos :v :elem-states :mass :dt :energy :history}. The
  :energy map is the ledger {:kinetic :internal :contact :external :plastic
  :kinetic0 :residual :residual-rel}."
  [model {:keys [v0 steps dt mass-scale-dt f-ext contacts fixed sample-every]}]
  (let [ids   (mesh/node-ids model)
        zerod (zero-disp ids)
        fixed (or fixed {})
        contacts (or contacts [])
        ;; initial assembly → per-element Δt_crit (base mass)
        elem0 (mapv el/init-state (:elements model))
        a0    (assemble model (into {} (map (fn [id] [id (mesh/coord model id)]) ids))
                        elem0 contacts zerod)
        mass  (scaled-mass model (:dt-crits a0) mass-scale-dt)
        dtmin (reduce min (:dt-crits a0))
        dt    (double (or dt mass-scale-dt (* safety dtmin)))
        sample (or sample-every steps)
        pos0  (into {} (map (fn [id] [id (mesh/coord model id)]) ids))
        vel0  (merge (zero-disp ids) (or v0 {}))
        ke0   (kinetic-energy mass vel0)
        apply-bc (fn [m vmap]
                   (reduce-kv
                    (fn [acc id [bx by bz]]
                      (let [[x y z] (acc id)]
                        (assoc acc id [(if bx 0.0 x) (if by 0.0 y) (if bz 0.0 z)])))
                    vmap fixed))]
    (loop [step 0
           t 0.0
           pos pos0
           vel vel0
           estates elem0
           u-int 0.0
           w-ext 0.0
           w-con 0.0
           plastic 0.0
           hist []]
      (if (>= step steps)
        (let [ke (kinetic-energy mass vel)
              res (- (+ ke u-int w-con) (+ w-ext ke0))
              scale (max 1e-30 ke0 (Math/abs u-int) (Math/abs w-ext))
              elastic (reduce + (map (fn [e st] (el/strain-energy model e st))
                                     (:elements model) estates))
              dissipated (max 0.0 (- u-int elastic))]
          {:t t :step step :pos pos :v vel :elem-states estates :mass mass :dt dt
           :energy {:kinetic ke :internal u-int :contact w-con :external w-ext
                    :elastic elastic :plastic dissipated :kinetic0 ke0
                    :residual res :residual-rel (/ res scale)}
           :history hist})
        (let [{:keys [fint fcont]} (assemble model pos estates contacts zerod)
              fext (ext-map f-ext t)
              ;; a = M⁻¹ (F_ext − F_int − F_contact)
              accel (into {}
                          (map (fn [id]
                                 (let [fe (get fext id [0.0 0.0 0.0])
                                       fi (get fint id [0.0 0.0 0.0])
                                       fc (get fcont id [0.0 0.0 0.0])
                                       m  (mass id)
                                       ;; a = M⁻¹(F_ext − F_int + F_contact): the
                                       ;; penalty force physically pushes the node
                                       ;; back OUT of the surface, so it ADDS.
                                       net (la/v+ (la/v- fe fi) fc)]
                                   [id (la/vscale (/ 1.0 m) net)]))
                               ids))
              vel'  (apply-bc nil (into {} (map (fn [id]
                                                  [id (la/v+ (vel id)
                                                             (la/vscale dt (accel id)))])
                                                ids)))
              dpos  (into {} (map (fn [id] [id (la/vscale dt (vel' id))]) ids))
              pos'  (into {} (map (fn [id] [id (la/v+ (pos id) (dpos id))]) ids))
              [estates' pw dwint] (advance-stresses model pos dpos estates)
              dwext (reduce + (map (fn [id] (la/dot (get fext id [0.0 0.0 0.0]) (dpos id))) ids))
              ;; stored contact (penalty-spring) energy = −∫F_contact·du ≥ 0
              dwcon (- (reduce + (map (fn [id] (la/dot (get fcont id [0.0 0.0 0.0]) (dpos id))) ids)))
              step' (inc step)
              ke    (kinetic-energy mass vel')
              hist' (if (or (zero? (mod step' sample)) (= step' steps))
                      (conj hist {:step step' :t (+ t dt)
                                  :kinetic ke :internal (+ u-int dwint)
                                  :contact (+ w-con dwcon) :external (+ w-ext dwext)
                                  :pos pos'})
                      hist)]
          (recur step' (+ t dt) pos' vel' estates'
                 (+ u-int dwint) (+ w-ext dwext) (+ w-con dwcon) (+ plastic pw)
                 hist'))))))
