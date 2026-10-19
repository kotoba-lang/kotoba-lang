(ns kudaki.contact
  "Penalty contact — the non-penetration constraint as a one-sided spring.

  Explicit codes enforce contact with a PENALTY: there is no constraint solve,
  just a stiff restoring force that switches on the instant a node penetrates a
  surface. For S0 the master surface is a rigid half-space (a plane): a node may
  not pass to the back side of the plane. Given the plane through point p₀ with
  outward unit normal n̂ (pointing into the allowed half-space), the signed gap of
  a node at x is

      gap = (x − p₀) · n̂          (>0 separated, <0 penetrated)

  and the restoring force is a spring on the penetration only:

      f = k · max(0, −gap) · n̂

  — zero while separated, pushing the node back out along +n̂ once it crosses.
  Pure: it reads positions and returns forces, no mutation. The penalty stiffness
  k trades penetration depth against the critical step (a stiffer wall needs a
  smaller Δt), so it is a model parameter, not a hidden constant."
  (:require [kudaki.linalg :as la]))

(defn plane-gap
  "Signed gap of point `x` against the plane {:point p₀ :normal n̂}."
  [{:keys [point normal]} x]
  (la/dot (la/v- x point) normal))

(defn plane-force
  "Penalty restoring force on a node at `x` from a rigid plane
  {:point :normal :stiffness k}. Zero unless the node has penetrated."
  [{:keys [normal stiffness] :as plane} x]
  (let [gap (plane-gap plane x)]
    (if (< gap 0.0)
      (la/vscale (* stiffness (- gap)) normal)   ; (-gap)>0 ⇒ push out along +n̂
      [0.0 0.0 0.0])))

(defn forces
  "Accumulate contact forces over a list of contacts into a map node-id →
  force. Each contact is {:type :plane :point :normal :stiffness :nodes [ids]}
  (slave nodes tested against the rigid plane). Returns {} when nothing touches."
  [contacts pos]
  (reduce
   (fn [acc {:keys [nodes] :as c}]
     (reduce
      (fn [acc id]
        (let [f (plane-force c (pos id))]
          (if (= f [0.0 0.0 0.0])
            acc
            (update acc id (fnil la/v+ [0.0 0.0 0.0]) f))))
      acc
      nodes))
   {}
   contacts))

;; ---------------------------------------------------------------------------
;; Coulomb friction (tangential traction on a penetrating, sliding node)
;; ---------------------------------------------------------------------------

(defn tangential-velocity
  "The component of velocity `v` tangent to the plane: v − (v·n̂) n̂. Friction
  acts only against this sliding part; the normal component is resisted by the
  penalty spring, not by friction."
  [normal v]
  (la/v- v (la/vscale (la/dot v normal) normal)))

(defn plane-traction
  "Normal penalty force PLUS Coulomb friction on a node at `x` moving at velocity
  `v` against a rigid plane {:point :normal :stiffness k :friction μ}. Returns
  {:normal fₙ :friction f_t :total f}.

  Friction is the kinetic Coulomb law, regularized by the sliding speed: it
  opposes the tangential velocity with magnitude μ|fₙ| (the edge of the friction
  cone), and vanishes when the node is separated (gap ≥ 0) or has no tangential
  motion. This is the explicit-code idealization — no stick state machine; below
  a velocity floor the tangential force is simply released."
  [{:keys [normal stiffness friction] :as plane} x v]
  (let [gap (plane-gap plane x)
        zero [0.0 0.0 0.0]]
    (if (>= gap 0.0)
      {:normal zero :friction zero :total zero}
      (let [fn-mag (* stiffness (- gap))
            fnv    (la/vscale fn-mag normal)
            mu     (double (or friction 0.0))
            vt     (tangential-velocity normal v)
            vtn    (la/norm vt)
            ft     (if (or (zero? mu) (< vtn 1.0e-12))
                     zero
                     (la/vscale (/ (* (- mu) fn-mag) vtn) vt))]  ; −μ|fₙ| · v_t/|v_t|
        {:normal fnv :friction ft :total (la/v+ fnv ft)}))))

(defn forces-with-friction
  "Like `forces` but each plane contact may carry `:friction μ`, and the node
  velocities `vel` (id → v) drive the tangential traction. Returns node-id →
  total (normal + friction) force, omitting nodes that are not in contact."
  [contacts pos vel]
  (reduce
   (fn [acc {:keys [nodes] :as c}]
     (reduce
      (fn [acc id]
        (let [{:keys [total]} (plane-traction c (pos id) (vel id))]
          (if (= total [0.0 0.0 0.0])
            acc
            (update acc id (fnil la/v+ [0.0 0.0 0.0]) total))))
      acc
      nodes))
   {}
   contacts))

;; ---------------------------------------------------------------------------
;; Tied / spotweld constraints (penalty link between two nodes)
;; ---------------------------------------------------------------------------

(defn tie-force
  "Penalty TIE (spotweld / tied contact) holding nodes `a` and `b` at a fixed
  relative offset `rest` (vec3, default coincident). Like a stiff bilateral spring
  on their separation: with Δ = x_a − x_b,

      f_a = −k (Δ − rest),   f_b = +k (Δ − rest)

  equal and opposite, vanishing at the rest offset. A spotweld is the rest=0 case
  (the two nodes are pulled together); this is the explicit-code idealization of a
  rigid link, the penalty stiffness `k` trading residual stretch against Δt.
  `tie` is {:k :a :b [:rest]}. Returns {a f_a b f_b}."
  [{:keys [k a b rest]} pos]
  (let [d  (la/v- (la/v- (pos a) (pos b)) (or rest [0.0 0.0 0.0]))
        fa (la/vscale (- (double k)) d)]
    {a fa b (la/vscale -1.0 fa)}))

(defn tie-forces
  "Accumulate spotweld/tie forces over a list of ties into node-id → force."
  [ties pos]
  (reduce
   (fn [acc t]
     (reduce-kv (fn [m id f] (update m id (fnil la/v+ [0.0 0.0 0.0]) f))
                acc (tie-force t pos)))
   {}
   ties))
