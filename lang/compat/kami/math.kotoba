(ns kami.math
  "Minimal column-major 4×4 matrix + vec math for camera and instance transforms.
  Column-major to match WGSL/wgpu (`mat4x4<f32>` columns). Matrices are flat
  16-element vectors: index = col*4 + row. Pure, .cljc.")

(def identity4
  [1.0 0.0 0.0 0.0
   0.0 1.0 0.0 0.0
   0.0 0.0 1.0 0.0
   0.0 0.0 0.0 1.0])

(defn mul
  "Column-major 4×4 multiply: returns A·B."
  [a b]
  (let [a (vec a) b (vec b)
        el (fn [m c r] (nth m (+ (* c 4) r)))]
    (vec
     (for [c (range 4) r (range 4)]
       (+ (* (el a 0 r) (el b c 0))
          (* (el a 1 r) (el b c 1))
          (* (el a 2 r) (el b c 2))
          (* (el a 3 r) (el b c 3)))))))

(defn from-trs
  "Compose translation [tx ty tz], unit quaternion [qx qy qz qw], and scale
  [sx sy sz] into a column-major model matrix."
  [[tx ty tz] [qx qy qz qw] [sx sy sz]]
  (let [x2 (+ qx qx) y2 (+ qy qy) z2 (+ qz qz)
        xx (* qx x2) xy (* qx y2) xz (* qx z2)
        yy (* qy y2) yz (* qy z2) zz (* qz z2)
        wx (* qw x2) wy (* qw y2) wz (* qw z2)]
    [(* sx (- 1.0 (+ yy zz))) (* sx (+ xy wz))         (* sx (- xz wy))         0.0
     (* sy (- xy wz))         (* sy (- 1.0 (+ xx zz))) (* sy (+ yz wx))         0.0
     (* sz (+ xz wy))         (* sz (- yz wx))         (* sz (- 1.0 (+ xx yy))) 0.0
     (double tx)              (double ty)              (double tz)              1.0]))

(defn perspective
  "Right-handed perspective projection, depth range [0,1] (wgpu/WebGPU NDC).
  `fov-deg` vertical FOV in degrees."
  [fov-deg aspect near far]
  (let [f (/ 1.0 (Math/tan (/ (* fov-deg (/ Math/PI 180.0)) 2.0)))
        nf (/ 1.0 (- near far))]
    [(/ f aspect) 0.0 0.0 0.0
     0.0 f 0.0 0.0
     0.0 0.0 (* far nf) -1.0
     0.0 0.0 (* far near nf) 0.0]))

(defn ortho
  "Right-handed orthographic projection, depth range [0,1] (wgpu/WebGPU NDC),
  column-major. Maps x∈[l,r]→[-1,1], y∈[b,t]→[-1,1], z∈[n,f]→[0,1]. For a 2D
  screen-space board pass `(ortho 0 w h 0 near far)` so pixel (0,0) is top-left
  and y grows downward."
  [l r b t near far]
  [(/ 2.0 (- r l)) 0.0 0.0 0.0
   0.0 (/ 2.0 (- t b)) 0.0 0.0
   0.0 0.0 (/ 1.0 (- near far)) 0.0
   (/ (- (+ r l)) (- r l)) (/ (- (+ t b)) (- t b)) (/ near (- near far)) 1.0])

(defn invert-rigid
  "Inverse of a rigid (rotation+translation, no scale) column-major matrix —
  used to turn a camera's world transform into a view matrix. Transpose the
  rotation 3×3 and negate the rotated translation."
  [m]
  (let [m (vec m)
        el (fn [c r] (nth m (+ (* c 4) r)))
        tx (el 3 0) ty (el 3 1) tz (el 3 2)
        ;; R^T columns are R's rows
        r00 (el 0 0) r01 (el 1 0) r02 (el 2 0)
        r10 (el 0 1) r11 (el 1 1) r12 (el 2 1)
        r20 (el 0 2) r21 (el 1 2) r22 (el 2 2)]
    [r00 r01 r02 0.0
     r10 r11 r12 0.0
     r20 r21 r22 0.0
     (- (+ (* r00 tx) (* r10 ty) (* r20 tz)))
     (- (+ (* r01 tx) (* r11 ty) (* r21 tz)))
     (- (+ (* r02 tx) (* r12 ty) (* r22 tz)))
     1.0]))

;; ---------------------------------------------------------------------------
;; vec3 — minimal pure helpers shared by ray-tracing (kami.rt) and spatial
;; audio (kami.binaural). Vectors are [x y z]; everything is double, .cljc.
;; ---------------------------------------------------------------------------

(defn v- "Component-wise a-b." [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
(defn v+ "Component-wise a+b." [[ax ay az] [bx by bz]] [(+ ax bx) (+ ay by) (+ az bz)])
(defn v* "Scale v by scalar s." [s [x y z]] [(* s x) (* s y) (* s z)])
(defn dot "Dot product a·b." [[ax ay az] [bx by bz]] (+ (* ax bx) (* ay by) (* az bz)))

(defn cross "Cross product a×b."
  [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])

(defn length "Euclidean length of v." [v] (Math/sqrt (dot v v)))

(defn normalize
  "Unit vector in v's direction; returns [0 0 0] for a (near-)zero vector."
  [v]
  (let [l (length v)]
    (if (< l 1e-12) [0.0 0.0 0.0] (v* (/ 1.0 l) v))))

(defn clamp "Clamp x into [lo hi]." [x lo hi] (max lo (min hi x)))
