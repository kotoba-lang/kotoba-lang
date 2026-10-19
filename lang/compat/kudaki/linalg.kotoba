(ns kudaki.linalg
  "Value-level linear algebra for the explicit kernel — no mutable globals, no
  BLAS, no native solver. Three layers, each a small set of pure functions over
  plain Clojure data so the same code runs on JVM / SCI / cljs / WASM:

    1. vec3      — Cartesian 3-vectors as [x y z]. The currency of nodal
                   positions, displacements, velocities and forces.
    2. mat3      — 3×3 matrices as a flat row-major 9-vector. Used for the
                   isoparametric Jacobian J = ∂x/∂ξ and its inverse (the only
                   matrix an explicit code ever inverts — there is NO global
                   stiffness matrix to factor).
    3. sym-tensor — symmetric 2nd-order tensors (stress, strain) in **Voigt
                   6-vector** form with the ordering

                       [σxx σyy σzz σxy σyz σzx]      (indices 0..5)

                   Strains use ENGINEERING shear (γ = 2ε) so that the elastic
                   law and the B-matrix stay simple; the double-contraction here
                   carries the factor-of-2 on the shear block, which is what
                   makes the deviator / von-Mises identities come out right.

  The crash solver lives and dies on getting the deviatoric stress algebra
  exact (it is the heart of J2 return mapping), so that algebra is concentrated
  here, in pure functions, with the conventions stated once.")

;; ---------------------------------------------------------------------------
;; vec3 — 3-vectors as [x y z]
;; ---------------------------------------------------------------------------

(defn v+
  "Vector sum a + b."
  [a b]
  [(+ (a 0) (b 0)) (+ (a 1) (b 1)) (+ (a 2) (b 2))])

(defn v-
  "Vector difference a − b."
  [a b]
  [(- (a 0) (b 0)) (- (a 1) (b 1)) (- (a 2) (b 2))])

(defn vscale
  "Scalar multiple s·a."
  [s a]
  [(* s (a 0)) (* s (a 1)) (* s (a 2))])

(defn dot
  "Euclidean inner product a·b."
  [a b]
  (+ (* (a 0) (b 0)) (* (a 1) (b 1)) (* (a 2) (b 2))))

(defn cross
  "Vector (cross) product a × b."
  [a b]
  [(- (* (a 1) (b 2)) (* (a 2) (b 1)))
   (- (* (a 2) (b 0)) (* (a 0) (b 2)))
   (- (* (a 0) (b 1)) (* (a 1) (b 0)))])

(defn norm
  "Euclidean length |a|."
  [a]
  (Math/sqrt (dot a a)))

(defn normalize
  "Unit vector a/|a|. Returns the zero vector for a zero-length input rather
  than dividing by zero (a degenerate bar axis must not blow up the step)."
  [a]
  (let [n (norm a)]
    (if (zero? n) [0.0 0.0 0.0] (vscale (/ 1.0 n) a))))

;; ---------------------------------------------------------------------------
;; mat3 — 3×3 matrices as a flat row-major 9-vector [a00 a01 a02 a10 …]
;; ---------------------------------------------------------------------------

(def ^:const I3
  "3×3 identity."
  [1.0 0.0 0.0  0.0 1.0 0.0  0.0 0.0 1.0])

(defn mget
  "Element (i,j) of a flat 3×3 matrix."
  [m i j]
  (m (+ (* 3 i) j)))

(defn mat*vec
  "Matrix–vector product M·v (v a vec3)."
  [m v]
  [(+ (* (m 0) (v 0)) (* (m 1) (v 1)) (* (m 2) (v 2)))
   (+ (* (m 3) (v 0)) (* (m 4) (v 1)) (* (m 5) (v 2)))
   (+ (* (m 6) (v 0)) (* (m 7) (v 1)) (* (m 8) (v 2)))])

(defn transpose3
  "Transpose of a flat 3×3 matrix."
  [m]
  [(m 0) (m 3) (m 6)
   (m 1) (m 4) (m 7)
   (m 2) (m 5) (m 8)])

(defn det3
  "Determinant of a flat 3×3 matrix — the Jacobian determinant whose sign tells
  us a hex is not inverted and whose magnitude scales the element volume."
  [m]
  (- (+ (* (m 0) (- (* (m 4) (m 8)) (* (m 5) (m 7))))
        (* (m 2) (- (* (m 3) (m 7)) (* (m 4) (m 6)))))
     (* (m 1) (- (* (m 3) (m 8)) (* (m 5) (m 6))))))

(defn inv3
  "Inverse of a flat 3×3 matrix via the adjugate / determinant. Used only for
  the isoparametric map J⁻¹ (∂ξ/∂x). Throws on a singular Jacobian — a flat or
  inverted element is a modelling error the integrator must not silently eat."
  [m]
  (let [d (det3 m)]
    (when (zero? d)
      (throw (ex-info "singular 3x3 (degenerate/inverted element)" {:m m})))
    (let [id (/ 1.0 d)]
      [(* id (- (* (m 4) (m 8)) (* (m 5) (m 7))))
       (* id (- (* (m 2) (m 7)) (* (m 1) (m 8))))
       (* id (- (* (m 1) (m 5)) (* (m 2) (m 4))))
       (* id (- (* (m 5) (m 6)) (* (m 3) (m 8))))
       (* id (- (* (m 0) (m 8)) (* (m 2) (m 6))))
       (* id (- (* (m 2) (m 3)) (* (m 0) (m 5))))
       (* id (- (* (m 3) (m 7)) (* (m 4) (m 6))))
       (* id (- (* (m 1) (m 6)) (* (m 0) (m 7))))
       (* id (- (* (m 0) (m 4)) (* (m 1) (m 3))))])))

;; ---------------------------------------------------------------------------
;; sym-tensor — symmetric 2nd-order tensors in Voigt 6-vector form
;;   ordering [xx yy zz xy yz zx]  (normals 0..2, shears 3..5)
;; ---------------------------------------------------------------------------

(def ^:const zero6 [0.0 0.0 0.0 0.0 0.0 0.0])

(defn t+ [a b] (mapv + a b))
(defn t- [a b] (mapv - a b))
(defn tscale [s a] (mapv #(* s %) a))

(defn trace
  "Trace (first invariant) σxx+σyy+σzz of a Voigt tensor."
  [t]
  (+ (t 0) (t 1) (t 2)))

(defn mean
  "Mean (hydrostatic) component tr/3."
  [t]
  (/ (trace t) 3.0))

(defn deviator
  "Deviatoric part s = σ − (tr σ/3) I. The shear components pass through
  unchanged; only the three normals lose the hydrostatic mean. The deviator is
  what the von-Mises surface and the J2 flow rule act on — pressure does no
  plastic work."
  [t]
  (let [p (mean t)]
    [(- (t 0) p) (- (t 1) p) (- (t 2) p) (t 3) (t 4) (t 5)]))

(defn ddot
  "Double-contraction a:b of two symmetric Voigt tensors. The shear block is
  counted TWICE because Voigt collapses the symmetric off-diagonal pair (e.g.
  σxy and σyx) into one slot:  a:b = Σ_normals aᵢbᵢ + 2 Σ_shears aⱼbⱼ.
  This single factor-of-2 is what makes |s| and the von-Mises stress correct."
  [a b]
  (+ (* (a 0) (b 0)) (* (a 1) (b 1)) (* (a 2) (b 2))
     (* 2.0 (+ (* (a 3) (b 3)) (* (a 4) (b 4)) (* (a 5) (b 5))))))

(defn tnorm
  "Frobenius norm √(s:s) of a symmetric tensor (shear counted twice)."
  [t]
  (Math/sqrt (ddot t t)))

(defn von-mises
  "Von-Mises equivalent (effective) stress σ̄ = √(3/2 · s:s), with s the
  deviator. Equivalently
      σ̄ = √( ½[(σxx−σyy)²+(σyy−σzz)²+(σzz−σxx)²] + 3(σxy²+σyz²+σzx²) ).
  For a uniaxial stress σ it returns |σ| exactly — the property the yield
  surface is calibrated against. This is the scalar the J2 return map drives
  back onto the yield stress."
  [t]
  (let [s (deviator t)]
    (Math/sqrt (* 1.5 (ddot s s)))))
