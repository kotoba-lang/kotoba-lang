(ns vdesign.simphysics
  "Time-stepped rigid-body simulation of the end-of-line frontal-crash
  dispatch event, built directly on `kotoba-lang/physics-2d`'s real,
  tested impulse-based `world-step` solver (ADR-2607151600). This ADDS a
  second, independent physical model of the SAME crash event ALONGSIDE
  `vdesign.simverify` (`crash.solver`'s closed-form energy-balance
  model) — it replaces nothing there; `simverify/check` is unchanged.

  What is REAL here: the vehicle body and the crash barrier are actual
  `physics_2d` `Body2D`/AABB `Collider2D` entities; `physics_2d/world-step`
  actually integrates velocity/position and actually runs its brute-force
  collision detection + impulse resolution + positional correction over
  N discrete ticks — `:trajectory` below is the ACTUAL per-tick output of
  that solver, read back tick by tick, not synthesized after the fact.

  Deliberate modeling simplifications (disclosed, not hidden):

  - 2D projection only (`physics_2d` has no 3D solver) — x is the
    direction of travel, y is lateral; world gravity is [0 0] (this is a
    top-down/bird's-eye projection of a frontal impact, not the vertical
    plane, so there is no meaningful vertical drop to model).
  - the vehicle's real BREP packaging-envelope footprint
    (`vdesign.cad/envelope-solid`'s tessellated `:dims`, mm) becomes ONE
    axis-aligned bounding box (length × width) — no crumple-zone
    deformation geometry.
  - the barrier/fixture is a second, STATIC (mass 0) AABB — `physics_2d`
    treats a mass-0 body as having zero inverse mass, i.e. an immovable
    anchor, matching how a real frontal-barrier crash-test rig is bolted
    to the ground (`resolve-contact` never special-cases this; it falls
    out of `inv-mass-b` being exactly `0.0`).
  - `curb-mass-kg` is passed straight through as the vehicle body's
    `physics_2d` mass (`physics_2d` has no real units — it is
    unit-agnostic, per that namespace's own docstring). `:friction` is
    set to `0.0` because `physics_2d`'s own `resolve-contact` never
    reads a body's `:friction` field at all — a real, verified property
    of the restored engine, not an oversight introduced here.
  - `physics_2d`'s impulse resolver has no progressive crush
    stiffness/force-deflection model: whatever tick first detects ANY
    AABB overlap fully zeroes the closing velocity in that ONE tick
    (given `restitution` 0) — a discrete, instantaneous 'boxcar' stop,
    not a continuous force ramp. Left at an arbitrary fixed timestep,
    the resulting `:sim-decel-g` would be dominated by whatever `dt`
    happened to be chosen (`decel-g = impact-mps/dt/g`, unrelated to any
    design parameter) — not a meaningful physical reading. To keep it in
    the same ballpark as `crash.solver`'s closed form, `dt` here is
    deliberately derived from THIS design's own crush-length/impact-speed
    (`crush-len-m / impact-mps`, the nominal transit time across the
    crush zone) — a principled, not arbitrary, choice, but one that
    couples the two models through a shared physical assumption rather
    than measuring them independently. By exact kinematic identity, a
    boxcar (instantaneous, constant) stop over that transit time is
    ALWAYS 2x the closed form's own averaged/ramp deceleration for the
    same impact speed and crush length (v²=2·a·d vs a=v/dt with
    dt=d/v ⇒ a=v²/d, exactly double v²/(2d)) — this is why `crosscheck`
    below centers its tolerance band on a ~2x ratio, not a ~1x
    'these should match' assumption, and why it is documented as a
    coarse SANITY crosscheck between two related-but-distinct
    idealizations, never as validation of either one.
  - MASS DOES NOT change `:sim-decel-g` in this scenario, and that is a
    genuine, verified property of BOTH models, not a `physics_2d`-only
    limitation: colliding with an immovable (mass-0 / infinite-
    effective-mass) anchor, the impulse's velocity change is independent
    of the moving body's own mass (mass cancels algebraically in
    `physics_2d`'s `resolve-contact`, exactly as it cancels in
    `crash.solver/crush`'s `a-g = (F/m)/g` where `F = KE/crush-len ∝ m`).
    Impact SPEED is the lever that actually moves `:sim-decel-g` in both
    models; `simphysics_test.cljc` asserts the speed relationship and
    separately asserts+documents the mass invariance, rather than
    asserting a heavier-implies-higher-decel-g relationship that would
    not be true of either model."
  (:require [physics-2d :as p2d]
            [vdesign.cad :as cad]
            [vdesign.simverify :as simverify]
            [crash.solver :as crash]))

(def ^:const default-gap-m
  "Standoff distance (m) the vehicle starts behind the barrier, so the
  trajectory captures a real pre-contact approach phase, not just the
  collision tick itself."
  2.0)

(def ^:const barrier-half-w-m
  "Barrier AABB half-width along the travel axis (m) — a thin, fixed
  rigid wall (the immovable test-rig barrier), not a modeled second
  vehicle."
  0.10)

(def ^:const barrier-half-h-m
  "Barrier AABB half-height (m), lateral — wide enough that the
  vehicle's full width always overlaps it head-on; no offset/oblique
  impact is modeled."
  4.0)

(def ^:const settle-ticks
  "Extra ticks appended after the vehicle is expected to reach the
  barrier, so the trajectory also captures post-contact settling.
  `physics_2d`'s positional correction removes 80% of any remaining
  overlap per tick (`resolve-contact`'s `0.8` factor), so residual
  overlap after `settle-ticks` further ticks is `0.2^settle-ticks` of
  whatever it was at first contact — 15 ticks converges to ~3e-11."
  15)

(defn- kmh->mps [kmh] (* kmh (/ 1000.0 3600.0)))

(defn- vehicle-half-extents-m
  "AABB half-extents (m) from `vdesign.cad/envelope-solid`'s REAL
  tessellated `:dims` (mm) — travel-axis half-width = length/2, lateral
  half-height = width/2."
  [geometry]
  (let [{:keys [dims]} (cad/envelope-solid geometry)]
    {:half-w (/ (:length-mm dims) 2000.0)
     :half-h (/ (:width-mm dims) 2000.0)}))

(defn- crush-len-m
  [class]
  (:crush-len (get simverify/geom class (:sedan simverify/geom))))

(defn simulate
  "Time-steps a `physics_2d` world for `design`'s frontal-crash dispatch
  event and returns:

    {:trajectory [{:tick :position :velocity} ...]   ; vehicle body only
     :sim-decel-g n :sim-crush-distance-m n
     :ticks n :dt n :impact-mps n}

  opts (all optional, for tuning/testing):
    :impact-mps  override impact speed, m/s (default: `simverify`'s real
                 56 km/h frontal-crash-test speed, converted)
    :dt          override the per-tick timestep, seconds (default:
                 `crush-len-m / impact-mps` — see namespace docstring)
    :ticks       override the tick count (default: enough to cross
                 `default-gap-m` at `impact-mps` plus `settle-ticks`)
    :gap-m       override the initial vehicle/barrier standoff (m)

  `:sim-decel-g` is the PEAK magnitude of tick-to-tick velocity change
  (along the travel axis) divided by `dt`, converted to g — derived from
  the actual simulated velocity trajectory, not invented.
  `:sim-crush-distance-m` is the largest AABB penetration depth (m)
  actually observed between the vehicle's leading face and the
  barrier's near face across the whole trajectory — derived from the
  actual simulated positions, not invented."
  [{:keys [class curb-mass-kg geometry]}
   & [{:keys [impact-mps dt ticks gap-m]}]]
  (let [v0    (double (or impact-mps (kmh->mps simverify/impact-kmh)))
        cl    (crush-len-m class)
        dt    (double (or dt (/ cl v0)))
        gap   (double (or gap-m default-gap-m))
        {:keys [half-w half-h]} (vehicle-half-extents-m geometry)
        approach-m (+ gap half-w barrier-half-w-m)
        ticks (long (or ticks (+ settle-ticks (long (Math/ceil (/ approach-m (* v0 dt)))))))
        barrier-x 0.0
        vehicle-x (- barrier-x barrier-half-w-m half-w gap)
        vehicle (p2d/make-body {:position [vehicle-x 0.0]
                                 :velocity [v0 0.0]
                                 :mass curb-mass-kg
                                 :restitution 0.0
                                 :friction 0.0
                                 :collider (p2d/make-aabb-collider half-w half-h)
                                 :user-data :vehicle})
        barrier (p2d/make-body {:position [barrier-x 0.0]
                                 :velocity [0.0 0.0]
                                 :mass 0.0
                                 :restitution 0.0
                                 :friction 0.0
                                 :collider (p2d/make-aabb-collider barrier-half-w-m barrier-half-h-m)
                                 :user-data :barrier})
        w0 (p2d/world-new [0.0 0.0])
        [w1 vid] (p2d/world-add w0 vehicle)
        [w2 _bid] (p2d/world-add w1 barrier)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w2 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) vid)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vxs (mapv (comp first :velocity) trajectory)
        peak-decel-mps2 (->> (map (fn [va vb] (Math/abs (/ (- vb va) dt))) vxs (rest vxs))
                              (reduce max 0.0))
        contact-plane-x (- barrier-x barrier-half-w-m)
        penetrations-m (mapv (fn [{:keys [position]}]
                                (max 0.0 (- (+ (first position) half-w) contact-plane-x)))
                              trajectory)]
    {:trajectory trajectory
     :sim-decel-g (/ peak-decel-mps2 simverify/g)
     :sim-crush-distance-m (reduce max 0.0 penetrations-m)
     :ticks (count trajectory)
     :dt dt
     :impact-mps v0}))

(defn closed-form-decel-g
  "Independently invokes the SAME real `crash.solver/solve` energy-balance
  model `vdesign.simverify`'s private `crash-sf` uses (mass, impact
  speed, crush length, rail area, material yield -> deceleration g) — a
  genuine second call to the real closed-form solver, not a
  re-derivation from `simulate`'s own trajectory."
  [{:keys [class curb-mass-kg]} & [{:keys [impact-mps]}]]
  (let [gm (get simverify/geom class (:sedan simverify/geom))
        v-kmh (if impact-mps (* impact-mps 3.6) simverify/impact-kmh)]
    (:decel-g (crash/solve {:mass-kg curb-mass-kg :impact-kmh v-kmh
                            :crush-len-m (:crush-len gm) :rail-area-mm2 (:rail-area gm)
                            :material (:material gm)}))))

(def ^:const crosscheck-ratio-low
  "Lower bound of the sim/closed-form `:sim-decel-g` ratio treated as
  'within tolerance'. See namespace docstring: `physics_2d`'s single-tick
  full-stop is, by exact kinematic identity, ~2x the closed form's
  averaged/ramp deceleration for the same speed + crush-length — this
  band is centered on that ~2x relationship (with slack for
  discretization/tick-count effects), not on a ~1x 'these should match'
  assumption."
  1.3)

(def ^:const crosscheck-ratio-high
  "Upper bound — see `crosscheck-ratio-low`."
  3.0)

(defn crosscheck
  "Coarse, HONEST cross-check between `simulate`'s time-stepped
  `:sim-decel-g` and this SAME design's `closed-form-decel-g`. This is
  NOT a validation of either model's absolute accuracy: `physics_2d` has
  no material/stiffness model at all, and `crash.solver` is itself a
  reduced-order energy-balance ESTIMATE, not a measured or FEA-validated
  number. `simulate`'s `dt` is also deliberately derived from the SAME
  crush-length/impact-speed the closed form uses (see namespace
  docstring) — so this crosscheck is intentionally coupled through a
  shared physical assumption, not two fully independent measurements.
  `:within-tolerance?` only means 'these two coarse, related
  idealizations of the same event are within a documented, explainable
  factor of each other' — nothing stronger.
  Returns {:sim-decel-g :closed-decel-g :ratio :within-tolerance?}."
  [design & [opts]]
  (let [sim (simulate design opts)
        closed (closed-form-decel-g design opts)
        ratio (/ (:sim-decel-g sim) closed)]
    {:sim-decel-g (:sim-decel-g sim)
     :closed-decel-g closed
     :ratio ratio
     :within-tolerance? (<= crosscheck-ratio-low ratio crosscheck-ratio-high)}))
