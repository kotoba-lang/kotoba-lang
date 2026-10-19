(ns vrm.math
  "Minimal Vec3/Quat/Mat4 math, matching glam's conventions (right-handed,
  column-major Mat4, quaternion stored `[x y z w]`), needed to port kami-vrm's
  spring-bone physics and node-constraint solving to CLJC without a Rust
  `glam` dependency. Restored as part of the kami-vrm port (kami-engine,
  deleted PR #82), ADR-2607010930. Mirrors `kotoba-lang/skeleton`'s
  `skeleton/math.cljc` conventions (implemented locally — no cross-repo
  dependency).

  Vec3 = `[x y z]`. Quat = `[x y z w]`. Mat4 = 16-element vector,
  column-major (`m[12] m[13] m[14]` = translation), matching glam's
  `to_cols_array`.")

;; ── Vec3 ──────────────────────────────────────

(def vec3-zero [0.0 0.0 0.0])
(def vec3-one [1.0 1.0 1.0])
(def vec3-y [0.0 1.0 0.0])

(defn vec3+ [[ax ay az] [bx by bz]] [(+ ax bx) (+ ay by) (+ az bz)])
(defn vec3- [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
(defn vec3-scale [[x y z] s] [(* x s) (* y s) (* z s)])
(defn vec3-dot [[ax ay az] [bx by bz]] (+ (* ax bx) (* ay by) (* az bz)))
(defn vec3-length-squared [v] (vec3-dot v v))
(defn vec3-length [v] (Math/sqrt (vec3-length-squared v)))
(defn vec3-distance [a b] (vec3-length (vec3- a b)))
(defn vec3-normalize [v] (let [l (vec3-length v)] (if (zero? l) vec3-zero (vec3-scale v (/ 1.0 l)))))
(defn vec3-normalize-or-zero [v] (let [l (vec3-length v)] (if (< l 1e-10) vec3-zero (vec3-scale v (/ 1.0 l)))))
(defn vec3-lerp [a b t] (vec3+ a (vec3-scale (vec3- b a) t)))
(defn vec3-cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])

;; ── Quat (`[x y z w]`) ───────────────────────────

(def quat-identity [0.0 0.0 0.0 1.0])

(defn quat-mul
  "Hamilton product; `(quat-mul a b)` composes as glam's `a * b` (apply `b`'s
  rotation, then `a`'s)."
  [[x1 y1 z1 w1] [x2 y2 z2 w2]]
  [(+ (* w1 x2) (* x1 w2) (* y1 z2) (- (* z1 y2)))
   (+ (- (* w1 y2) (* x1 z2)) (* y1 w2) (* z1 x2))
   (+ (* w1 z2) (* x1 y2) (- (* y1 x2)) (* z1 w2))
   (- (* w1 w2) (* x1 x2) (* y1 y2) (* z1 z2))])

(defn quat-dot [[x1 y1 z1 w1] [x2 y2 z2 w2]] (+ (* x1 x2) (* y1 y2) (* z1 z2) (* w1 w2)))
(defn quat-length-squared [q] (quat-dot q q))
(defn quat-neg [[x y z w]] [(- x) (- y) (- z) (- w)])
(defn quat-conjugate [[x y z w]] [(- x) (- y) (- z) w])
(defn quat-xyz [[x y z _w]] [x y z])
(defn quat-scale [[x y z w] s] [(* x s) (* y s) (* z s) (* w s)])
(defn quat-add [[x1 y1 z1 w1] [x2 y2 z2 w2]] [(+ x1 x2) (+ y1 y2) (+ z1 z2) (+ w1 w2)])

(defn quat-normalize
  [q]
  (let [l (Math/sqrt (quat-length-squared q))]
    (if (zero? l) quat-identity (mapv #(/ % l) q))))

(defn quat-inverse
  "Inverse of a (assumed unit) quaternion — equal to its conjugate."
  [q]
  (quat-conjugate q))

(defn- vec4-lerp [a b t] (mapv (fn [ai bi] (+ ai (* (- bi ai) t))) a b))

(defn quat-slerp
  "Spherical linear interpolation from `a` to `b` by `t` in [0,1],
  hemisphere-aligned (shortest path)."
  [a b t]
  (let [d0 (quat-dot a b)
        [b d0] (if (< d0 0.0) [(quat-neg b) (- d0)] [b d0])]
    (if (> d0 0.9995)
      (quat-normalize (vec4-lerp a b t))
      (let [theta0 (Math/acos (min 1.0 (max -1.0 d0)))
            theta (* theta0 t)
            sin-theta0 (Math/sin theta0)
            sin-theta (Math/sin theta)
            w0 (- (Math/cos theta) (* d0 (/ sin-theta sin-theta0)))
            w1 (/ sin-theta sin-theta0)]
        (mapv (fn [ai bi] (+ (* ai w0) (* bi w1))) a b)))))

(defn quat-from-axis-angle [axis angle]
  (let [h (/ angle 2.0)
        s (Math/sin h)]
    (conj (vec3-scale axis s) (Math/cos h))))

(defn quat-from-rotation-x [angle] (let [h (/ angle 2.0)] [(Math/sin h) 0.0 0.0 (Math/cos h)]))
(defn quat-from-rotation-y [angle] (let [h (/ angle 2.0)] [0.0 (Math/sin h) 0.0 (Math/cos h)]))
(defn quat-from-rotation-z [angle] (let [h (/ angle 2.0)] [0.0 0.0 (Math/sin h) (Math/cos h)]))

(defn quat-from-rotation-arc
  "Shortest-path rotation quaternion mapping unit vector `from` onto unit
  vector `to`."
  [from to]
  (let [d (vec3-dot from to)]
    (if (< d -0.999999)
      ;; Opposite vectors: pick any orthogonal axis.
      (let [axis (if (< (Math/abs (double (first from))) 0.9)
                   (vec3-normalize (vec3-cross from [1.0 0.0 0.0]))
                   (vec3-normalize (vec3-cross from [0.0 1.0 0.0])))]
        (quat-normalize (conj (vec axis) 0.0)))
      (let [c (vec3-cross from to)
            w (+ 1.0 d)]
        (quat-normalize (conj (vec c) w))))))

;; ── Mat4 (column-major 16-vector) ────────────────

(def mat4-identity
  [1.0 0.0 0.0 0.0
   0.0 1.0 0.0 0.0
   0.0 0.0 1.0 0.0
   0.0 0.0 0.0 1.0])

(declare mat4-from-scale-rotation-translation
         mat4-to-scale-rotation-translation)

(defn mat4-mul
  "`(mat4-mul a b)` = glam's `a * b` (column-major, applies `b` first)."
  [a b]
  (let [ai (fn [col row] (nth a (+ (* col 4) row)))
        bi (fn [col row] (nth b (+ (* col 4) row)))]
    (vec
     (for [col (range 4) row (range 4)]
       (reduce + (for [k (range 4)] (* (ai k row) (bi col k))))))))

(defn mat4-inverse-affine
  "Inverse of an affine TRS matrix. Handles non-uniform scale by composing
  S^-1 * R^-1 * T^-1 explicitly instead of pretending the inverse is another
  T*R*S with independently inverted fields."
  [m]
  (let [[scale rotation translation] (mat4-to-scale-rotation-translation m)
        inv-scale (mapv (fn [x]
                          (when (< (Math/abs (double x)) 1e-12)
                            (throw (ex-info "singular affine matrix" {:matrix m})))
                          (/ 1.0 x))
                        scale)
        s (mat4-from-scale-rotation-translation inv-scale quat-identity vec3-zero)
        r (mat4-from-scale-rotation-translation vec3-one (quat-inverse rotation) vec3-zero)
        t (mat4-from-scale-rotation-translation vec3-one quat-identity (mapv - translation))]
    (mat4-mul s (mat4-mul r t))))

(defn quat-rotate-vec3
  "Rotate Vec3 `v` by quaternion `q` (`q * v`)."
  [[x y z w] [vx vy vz]]
  (let [uvx (- (* y vz) (* z vy)) uvy (- (* z vx) (* x vz)) uvz (- (* x vy) (* y vx))
        uuvx (- (* y uvz) (* z uvy)) uuvy (- (* z uvx) (* x uvz)) uuvz (- (* x uvy) (* y uvx))]
    [(+ vx (* 2.0 (+ (* w uvx) uuvx)))
     (+ vy (* 2.0 (+ (* w uvy) uuvy)))
     (+ vz (* 2.0 (+ (* w uvz) uuvz)))]))

(defn quat-to-mat3-cols
  "Rotation quaternion -> 3 columns of 3 (9 values), each column a Vec3."
  [[x y z w]]
  (let [x2 (+ x x) y2 (+ y y) z2 (+ z z)
        xx (* x x2) xy (* x y2) xz (* x z2)
        yy (* y y2) yz (* y z2) zz (* z z2)
        wx (* w x2) wy (* w y2) wz (* w z2)]
    [[(- 1.0 (+ yy zz)) (+ xy wz) (- xz wy)]
     [(- xy wz) (- 1.0 (+ xx zz)) (+ yz wx)]
     [(+ xz wy) (- yz wx) (- 1.0 (+ xx yy))]]))

(defn mat4-from-scale-rotation-translation
  [[sx sy sz] q [tx ty tz]]
  (let [[c0 c1 c2] (quat-to-mat3-cols q)
        [c0 c1 c2] [(vec3-scale c0 sx) (vec3-scale c1 sy) (vec3-scale c2 sz)]]
    (vec (concat c0 [0.0] c1 [0.0] c2 [0.0] [tx ty tz 1.0]))))

(defn- mat3-col [m col] (subvec m (* col 4) (+ (* col 4) 3)))

(defn- mat3-to-quat
  "Rotation matrix (3 orthonormal columns, each length-3) -> unit quaternion."
  [c0 c1 c2]
  (let [[m00 m10 m20] c0
        [m01 m11 m21] c1
        [m02 m12 m22] c2
        trace (+ m00 m11 m22)]
    (if (> trace 0.0)
      (let [s (* 0.5 (/ 1.0 (Math/sqrt (+ trace 1.0))))]
        (quat-normalize [(* (- m21 m12) s) (* (- m02 m20) s) (* (- m10 m01) s) (/ 0.25 s)]))
      (cond
        (and (> m00 m11) (> m00 m22))
        (let [s (* 2.0 (Math/sqrt (max 1e-12 (+ 1.0 m00 (- m11) (- m22)))))]
          (quat-normalize [(* 0.25 s) (/ (+ m01 m10) s) (/ (+ m02 m20) s) (/ (- m21 m12) s)]))

        (> m11 m22)
        (let [s (* 2.0 (Math/sqrt (max 1e-12 (+ 1.0 m11 (- m00) (- m22)))))]
          (quat-normalize [(/ (+ m01 m10) s) (* 0.25 s) (/ (+ m12 m21) s) (/ (- m02 m20) s)]))

        :else
        (let [s (* 2.0 (Math/sqrt (max 1e-12 (+ 1.0 m22 (- m00) (- m11)))))]
          (quat-normalize [(/ (+ m02 m20) s) (/ (+ m12 m21) s) (* 0.25 s) (/ (- m10 m01) s)]))))))

(defn mat4-to-scale-rotation-translation
  "Decompose `m` -> `[scale quat translation]`."
  [m]
  (let [c0 (mat3-col m 0) c1 (mat3-col m 1) c2 (mat3-col m 2)
        sx (vec3-length c0) sy (vec3-length c1) sz (vec3-length c2)
        n0 (if (zero? sx) c0 (vec3-scale c0 (/ 1.0 sx)))
        n1 (if (zero? sy) c1 (vec3-scale c1 (/ 1.0 sy)))
        n2 (if (zero? sz) c2 (vec3-scale c2 (/ 1.0 sz)))
        q (mat3-to-quat n0 n1 n2)
        t [(nth m 12) (nth m 13) (nth m 14)]]
    [[sx sy sz] q t]))

(defn mat4-translation [m] (nth (mat4-to-scale-rotation-translation m) 2))
(defn mat4-rotation [m] (nth (mat4-to-scale-rotation-translation m) 1))

(defn mat4-transform-point3
  "Apply `m` (assumed affine) to a Vec3 point (implicit w=1), returning Vec3."
  [m [x y z]]
  [(+ (* (nth m 0) x) (* (nth m 4) y) (* (nth m 8) z) (nth m 12))
   (+ (* (nth m 1) x) (* (nth m 5) y) (* (nth m 9) z) (nth m 13))
   (+ (* (nth m 2) x) (* (nth m 6) y) (* (nth m 10) z) (nth m 14))])

(defn mat4-to-cols-array-2d [m] [(vec (subvec m 0 4)) (vec (subvec m 4 8)) (vec (subvec m 8 12)) (vec (subvec m 12 16))])
(defn mat4-from-cols-array [m] (vec m))
(defn mat4-from-cols-array-2d [cols] (vec (apply concat cols)))
