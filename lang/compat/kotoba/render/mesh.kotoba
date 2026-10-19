(ns kotoba.render.mesh
  "Procedural mesh generation + vertex-buffer interleaving.
   Ported from `kami-render/src/mesh.rs`.

   All meshes are returned as `[positions normals uvs indices]` — flat
   vectors of floats/ints, exactly like the Rust `(Vec<f32>, Vec<f32>,
   Vec<f32>, Vec<u32>)` tuples — so callers can interleave/upload however
   their host adapter wants.

   NOT ported: `instances_to_frame` (Rust) built a `kami_core::ipc::Frame`
   — that type lives in the separate `kami-core` crate (KAMI IPC substrate,
   out of scope for this port). `grid_instances` (the pure transform-matrix
   generator upstream of it) IS ported below."
  (:refer-clojure :exclude [interleave]))

;; ---------------------------------------------------------------------------
;; Interleaving
;; ---------------------------------------------------------------------------

(defn interleave
  "Interleave separate position/normal/uv arrays into a 32B/vertex (8 floats)
   flat vector: [pos3 norm3 uv2] × N."
  [positions normals uvs]
  (let [vertex-count (quot (count positions) 3)]
    (vec
     (mapcat
      (fn [i]
        (concat (subvec positions (* i 3) (+ (* i 3) 3))
                (subvec normals (* i 3) (+ (* i 3) 3))
                (subvec uvs (* i 2) (+ (* i 2) 2))))
      (range vertex-count)))))

(defn interleave-with-tangents
  "Interleave position + normal + uv + tangent into a 48B/vertex (12 floats)
   flat vector."
  [positions normals uvs tangents]
  (let [vertex-count (quot (count positions) 3)]
    (vec
     (mapcat
      (fn [i]
        (concat (subvec positions (* i 3) (+ (* i 3) 3))
                (subvec normals (* i 3) (+ (* i 3) 3))
                (subvec uvs (* i 2) (+ (* i 2) 2))
                (subvec tangents (* i 4) (+ (* i 4) 4))))
      (range vertex-count)))))

(defn loaded-mesh
  "Build a `{:vertices :indices :vertex-count :index-count}` map from
   separate arrays (interleaves automatically). Matches Rust `LoadedMesh`."
  [positions normals uvs indices]
  {:vertices (interleave positions normals uvs)
   :indices (vec indices)
   :vertex-count (quot (count positions) 3)
   :index-count (count indices)})

;; ---------------------------------------------------------------------------
;; Tangent computation (MikkTSpace-lite)
;; ---------------------------------------------------------------------------

(defn compute-tangents
  "Compute vec4 tangents per vertex: xyz = tangent direction, w = handedness
   (+1 or -1). Gram-Schmidt orthogonalized against the vertex normal."
  [positions normals uvs indices]
  (let [vertex-count (quot (count positions) 3)
        p (fn [i k] (nth positions (+ (* i 3) k)))
        uv (fn [i k] (nth uvs (+ (* i 2) k)))
        acc (reduce
             (fn [[tans bitans] [i0 i1 i2]]
               (let [p0 [(p i0 0) (p i0 1) (p i0 2)]
                     p1 [(p i1 0) (p i1 1) (p i1 2)]
                     p2 [(p i2 0) (p i2 1) (p i2 2)]
                     uv0 [(uv i0 0) (uv i0 1)]
                     uv1 [(uv i1 0) (uv i1 1)]
                     uv2 [(uv i2 0) (uv i2 1)]
                     edge1 (mapv - p1 p0)
                     edge2 (mapv - p2 p0)
                     duv1 (mapv - uv1 uv0)
                     duv2 (mapv - uv2 uv0)
                     det (- (* (nth duv1 0) (nth duv2 1)) (* (nth duv1 1) (nth duv2 0)))
                     r (if (> (Math/abs (double det)) 1e-8) (/ 1.0 det) 0.0)
                     t (mapv (fn [e1 e2] (* r (- (* (nth duv2 1) e1) (* (nth duv1 1) e2)))) edge1 edge2)
                     b (mapv (fn [e1 e2] (* r (+ (* (- (nth duv2 0)) e1) (* (nth duv1 0) e2)))) edge1 edge2)]
                 [(reduce (fn [m idx] (update m idx #(mapv + % t))) tans [i0 i1 i2])
                  (reduce (fn [m idx] (update m idx #(mapv + % b))) bitans [i0 i1 i2])]))
             [(vec (repeat vertex-count [0.0 0.0 0.0]))
              (vec (repeat vertex-count [0.0 0.0 0.0]))]
             (partition 3 indices))
        [tangents bitangents] acc]
    (vec
     (mapcat
      (fn [i]
        (let [n [(nth normals (* i 3)) (nth normals (+ (* i 3) 1)) (nth normals (+ (* i 3) 2))]
              t (nth tangents i)
              b (nth bitangents i)
              n-dot-t (reduce + (map * n t))
              ot0 (mapv (fn [tc nc] (- tc (* nc n-dot-t))) t n)
              len (Math/sqrt (double (reduce + (map * ot0 ot0))))
              ot (if (> len 1e-8) (mapv #(/ % len) ot0) [1.0 0.0 0.0])
              cross [(- (* (nth n 1) (nth ot 2)) (* (nth n 2) (nth ot 1)))
                     (- (* (nth n 2) (nth ot 0)) (* (nth n 0) (nth ot 2)))
                     (- (* (nth n 0) (nth ot 1)) (* (nth n 1) (nth ot 0)))]
              dot-cb (reduce + (map * cross b))
              w (if (< dot-cb 0.0) -1.0 1.0)]
          [(nth ot 0) (nth ot 1) (nth ot 2) w]))
      (range vertex-count)))))

;; ---------------------------------------------------------------------------
;; Procedural primitives
;; ---------------------------------------------------------------------------

(defn sphere
  "UV sphere. Returns [positions normals uvs indices]."
  [stacks slices]
  (let [rows (for [i (range (inc stacks))
                    :let [phi (/ (* Math/PI i) stacks)
                          y (Math/cos phi)
                          r (Math/sin phi)]
                    j (range (inc slices))
                    :let [theta (/ (* 2.0 Math/PI j) slices)
                          x (* r (Math/cos theta))
                          z (* r (Math/sin theta))]]
                {:pos [(* x 0.5) (* y 0.5) (* z 0.5)]
                 :norm [x y z]
                 :uv [(/ (double j) slices) (/ (double i) stacks)]})
        ring (inc slices)
        indices (vec (mapcat
                      (fn [[i j]]
                        (let [a (+ (* i ring) j) b (+ a ring)]
                          [a b (inc a) (inc a) b (inc b)]))
                      (for [i (range stacks) j (range slices)] [i j])))]
    [(vec (mapcat :pos rows)) (vec (mapcat :norm rows)) (vec (mapcat :uv rows)) indices]))

(defn plane
  "Subdivided plane on the XZ plane. Returns [positions normals uvs indices]."
  [width depth subdivisions]
  (let [segs (inc subdivisions)
        rows (for [iz (range (inc segs)) ix (range (inc segs))
                    :let [u (/ (double ix) segs) v (/ (double iz) segs)]]
                {:pos [(* (- u 0.5) width) 0.0 (* (- v 0.5) depth)]
                 :norm [0.0 1.0 0.0]
                 :uv [u v]})
        row (inc segs)
        indices (vec (mapcat
                      (fn [[iz ix]]
                        (let [a (+ (* iz row) ix) b (+ a row)]
                          [a b (inc a) (inc a) b (inc b)]))
                      (for [iz (range segs) ix (range segs)] [iz ix])))]
    [(vec (mapcat :pos rows)) (vec (mapcat :norm rows)) (vec (mapcat :uv rows)) indices]))

(def cube-positions
  [-0.5 -0.5 0.5 0.5 -0.5 0.5 0.5 0.5 0.5 -0.5 0.5 0.5
   0.5 -0.5 -0.5 -0.5 -0.5 -0.5 -0.5 0.5 -0.5 0.5 0.5 -0.5
   -0.5 0.5 0.5 0.5 0.5 0.5 0.5 0.5 -0.5 -0.5 0.5 -0.5
   -0.5 -0.5 -0.5 0.5 -0.5 -0.5 0.5 -0.5 0.5 -0.5 -0.5 0.5
   0.5 -0.5 0.5 0.5 -0.5 -0.5 0.5 0.5 -0.5 0.5 0.5 0.5
   -0.5 -0.5 -0.5 -0.5 -0.5 0.5 -0.5 0.5 0.5 -0.5 0.5 -0.5])

(def cube-normals
  [0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0 1.0
   0.0 0.0 -1.0 0.0 0.0 -1.0 0.0 0.0 -1.0 0.0 0.0 -1.0
   0.0 1.0 0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0 1.0 0.0
   0.0 -1.0 0.0 0.0 -1.0 0.0 0.0 -1.0 0.0 0.0 -1.0 0.0
   1.0 0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0
   -1.0 0.0 0.0 -1.0 0.0 0.0 -1.0 0.0 0.0 -1.0 0.0 0.0])

(def cube-uvs (vec (take 48 (cycle [0.0 0.0 1.0 0.0 1.0 1.0 0.0 1.0]))))

(def cube-indices
  [0 1 2 0 2 3
   4 5 6 4 6 7
   8 9 10 8 10 11
   12 13 14 12 14 15
   16 17 18 16 18 19
   20 21 22 20 22 23])

(defn cube
  "Unit cube. Returns [positions normals uvs indices]."
  []
  [cube-positions cube-normals cube-uvs cube-indices])

(defn grid-instances
  "Generate N instance transforms (16-float column-major translation
   matrices, XZ plane — Y is always 0, matching the Rust source) arranged
   in a grid. Flat vector, 16 floats per entity."
  [n spacing]
  (let [side (long (Math/ceil (Math/sqrt (double n))))
        offset (/ (* side spacing) 2.0)]
    (vec
     (mapcat
      (fn [i]
        (let [x (- (* (mod i side) spacing) offset)
              z (- (* (quot i side) spacing) offset)]
          [1.0 0.0 0.0 0.0
           0.0 1.0 0.0 0.0
           0.0 0.0 1.0 0.0
           x 0.0 z 1.0]))
      (range n)))))

;; ---------------------------------------------------------------------------
;; GIS / maps mesh generators — hex grid, cylinder pipe, building extrusion
;; Used by maps.etzhayyim.com for KAMI-based infrastructure & spatial rendering
;; ---------------------------------------------------------------------------

;; A small builder record threaded through the generators below, mirroring
;; the Rust functions' `Vec::extend_from_slice` bookkeeping but with plain
;; immutable vectors (`base` = current vertex count, i.e. `positions.len()/3`).

(defn- mesh-builder [] {:positions [] :normals [] :uvs [] :indices []})

(defn- base-idx [b] (quot (count (:positions b)) 3))

(defn- add-vertex [b pos norm uv]
  (-> b
      (update :positions into pos)
      (update :normals into norm)
      (update :uvs into uv)))

(defn- add-indices [b idxs]
  (update b :indices into idxs))

(defn hex-prism
  "Flat-top hexagonal prism, center at origin, extends along Y axis.
   `radius`: circumradius (center to vertex), `height`: Y extent.
   Returns [positions normals uvs indices]."
  [radius height]
  (let [half-h (* height 0.5)
        sides 6
        angles (mapv (fn [i] (/ (* 2.0 Math/PI i) sides)) (range sides))
        b0 (mesh-builder)
        ;; --- top face ---
        top-center (base-idx b0)
        b1 (add-vertex b0 [0.0 half-h 0.0] [0.0 1.0 0.0] [0.5 0.5])
        b2 (reduce (fn [b a]
                     (add-vertex b [(* radius (Math/cos a)) half-h (* radius (Math/sin a))]
                                 [0.0 1.0 0.0]
                                 [(+ 0.5 (* 0.5 (Math/cos a))) (+ 0.5 (* 0.5 (Math/sin a)))]))
                   b1 angles)
        b3 (reduce (fn [b i]
                     (let [nxt (mod (inc i) sides)]
                       (add-indices b [top-center (+ top-center 1 i) (+ top-center 1 nxt)])))
                   b2 (range sides))
        ;; --- bottom face ---
        bot-center (base-idx b3)
        b4 (add-vertex b3 [0.0 (- half-h) 0.0] [0.0 -1.0 0.0] [0.5 0.5])
        b5 (reduce (fn [b a]
                     (add-vertex b [(* radius (Math/cos a)) (- half-h) (* radius (Math/sin a))]
                                 [0.0 -1.0 0.0]
                                 [(+ 0.5 (* 0.5 (Math/cos a))) (+ 0.5 (* 0.5 (Math/sin a)))]))
                   b4 angles)
        b6 (reduce (fn [b i]
                     (let [nxt (mod (inc i) sides)]
                       (add-indices b [bot-center (+ bot-center 1 nxt) (+ bot-center 1 i)])))
                   b5 (range sides))
        ;; --- side faces (6 quads) ---
        b7 (reduce
            (fn [b i]
              (let [nxt (mod (inc i) sides)
                    a0 (nth angles i) a1 (nth angles nxt)
                    mid (* (+ a0 a1) 0.5)
                    nx (Math/cos mid) nz (Math/sin mid)
                    base (base-idx b)
                    x0 (* radius (Math/cos a0)) z0 (* radius (Math/sin a0))
                    x1 (* radius (Math/cos a1)) z1 (* radius (Math/sin a1))
                    u0 (/ (double i) sides) u1 (/ (double (inc i)) sides)]
                (-> b
                    (add-vertex [x0 half-h z0] [nx 0.0 nz] [u0 0.0])
                    (add-vertex [x1 half-h z1] [nx 0.0 nz] [u1 0.0])
                    (add-vertex [x1 (- half-h) z1] [nx 0.0 nz] [u1 1.0])
                    (add-vertex [x0 (- half-h) z0] [nx 0.0 nz] [u0 1.0])
                    (add-indices [base (inc base) (+ base 2) base (+ base 2) (+ base 3)]))))
            b6 (range sides))]
    [(:positions b7) (:normals b7) (:uvs b7) (:indices b7)]))

(defn cylinder-pipe
  "Cylinder pipe along the Y axis (infrastructure rendering: water/gas/electric).
   `radius`: outer radius, `thickness`: wall thickness (0 = solid), `height`:
   Y extent, `segments`: circumference subdivision.
   Returns [positions normals uvs indices]."
  [radius thickness height segments]
  (let [half-h (* height 0.5)
        inner-radius (if (> thickness 0.0) (max (- radius thickness) 0.0) 0.0)
        is-hollow (> inner-radius 0.0)
        b0 (mesh-builder)
        ;; outer wall
        b1 (reduce
            (fn [b ring]
              (let [y (if (zero? ring) half-h (- half-h))
                    v (double ring)]
                (reduce (fn [b i]
                          (let [angle (/ (* 2.0 Math/PI i) segments)
                                x (* radius (Math/cos angle)) z (* radius (Math/sin angle))]
                            (add-vertex b [x y z] [(Math/cos angle) 0.0 (Math/sin angle)]
                                        [(/ (double i) segments) v])))
                        b (range (inc segments)))))
            b0 [0 1])
        row (inc segments)
        b2 (reduce (fn [b i]
                     (let [a i b_ (+ a row)]
                       (add-indices b [a b_ (inc a) (inc a) b_ (inc b_)])))
                   b1 (range segments))]
    (if is-hollow
      (let [inner-base (base-idx b2)
            b3 (reduce
                (fn [b ring]
                  (let [y (if (zero? ring) half-h (- half-h))
                        v (double ring)]
                    (reduce (fn [b i]
                              (let [angle (/ (* 2.0 Math/PI i) segments)
                                    x (* inner-radius (Math/cos angle)) z (* inner-radius (Math/sin angle))]
                                (add-vertex b [x y z] [(- (Math/cos angle)) 0.0 (- (Math/sin angle))]
                                            [(/ (double i) segments) v])))
                            b (range (inc segments)))))
                b2 [0 1])
            b4 (reduce (fn [b i]
                         (let [a (+ inner-base i) b_ (+ a row)]
                           (add-indices b [a (inc a) b_ (inc a) (inc b_) b_])))
                       b3 (range segments))
            top-base (base-idx b4)
            b5 (reduce (fn [b i]
                         (let [angle (/ (* 2.0 Math/PI i) segments)
                               u (/ (double i) segments)]
                           (-> b
                               (add-vertex [(* radius (Math/cos angle)) half-h (* radius (Math/sin angle))]
                                           [0.0 1.0 0.0] [u 0.0])
                               (add-vertex [(* inner-radius (Math/cos angle)) half-h (* inner-radius (Math/sin angle))]
                                           [0.0 1.0 0.0] [u 1.0]))))
                       b4 (range (inc segments)))
            b6 (reduce (fn [b i]
                         (let [a (+ top-base (* i 2))]
                           (add-indices b [a (+ a 2) (inc a) (inc a) (+ a 2) (+ a 3)])))
                       b5 (range segments))
            bot-base (base-idx b6)
            b7 (reduce (fn [b i]
                         (let [angle (/ (* 2.0 Math/PI i) segments)
                               u (/ (double i) segments)]
                           (-> b
                               (add-vertex [(* radius (Math/cos angle)) (- half-h) (* radius (Math/sin angle))]
                                           [0.0 -1.0 0.0] [u 0.0])
                               (add-vertex [(* inner-radius (Math/cos angle)) (- half-h) (* inner-radius (Math/sin angle))]
                                           [0.0 -1.0 0.0] [u 1.0]))))
                       b6 (range (inc segments)))
            b8 (reduce (fn [b i]
                         (let [a (+ bot-base (* i 2))]
                           (add-indices b [a (inc a) (+ a 2) (inc a) (+ a 3) (+ a 2)])))
                       b7 (range segments))]
        [(:positions b8) (:normals b8) (:uvs b8) (:indices b8)])
      (let [top-center (base-idx b2)
            b3 (add-vertex b2 [0.0 half-h 0.0] [0.0 1.0 0.0] [0.5 0.5])
            b4 (reduce (fn [b i]
                         (let [angle (/ (* 2.0 Math/PI i) segments)]
                           (add-vertex b [(* radius (Math/cos angle)) half-h (* radius (Math/sin angle))]
                                       [0.0 1.0 0.0]
                                       [(+ 0.5 (* 0.5 (Math/cos angle))) (+ 0.5 (* 0.5 (Math/sin angle)))])))
                       b3 (range (inc segments)))
            b5 (reduce (fn [b i]
                         (add-indices b [top-center (+ top-center 1 i) (+ top-center 2 i)]))
                       b4 (range segments))
            bot-center (base-idx b5)
            b6 (add-vertex b5 [0.0 (- half-h) 0.0] [0.0 -1.0 0.0] [0.5 0.5])
            b7 (reduce (fn [b i]
                         (let [angle (/ (* 2.0 Math/PI i) segments)]
                           (add-vertex b [(* radius (Math/cos angle)) (- half-h) (* radius (Math/sin angle))]
                                       [0.0 -1.0 0.0]
                                       [(+ 0.5 (* 0.5 (Math/cos angle))) (+ 0.5 (* 0.5 (Math/sin angle)))])))
                       b6 (range (inc segments)))
            b8 (reduce (fn [b i]
                         (add-indices b [bot-center (+ bot-center 2 i) (+ bot-center 1 i)]))
                       b7 (range segments))]
        [(:positions b8) (:normals b8) (:uvs b8) (:indices b8)]))))

(defn building-extrusion
  "Building extrusion from a 2D footprint polygon.
   `footprint`: seq of [x z] pairs forming a closed CCW polygon.
   `height`: building height (Y extent from 0).
   Returns [positions normals uvs indices]."
  [footprint height]
  (let [footprint (vec footprint)
        n (count footprint)]
    (if (< n 3)
      [[] [] [] []]
      (let [b0 (mesh-builder)
            ;; top face (fan triangulation)
            top-base 0
            b1 (reduce (fn [b [x z]] (add-vertex b [x height z] [0.0 1.0 0.0] [x z])) b0 footprint)
            b2 (reduce (fn [b i] (add-indices b [top-base (+ top-base i) (+ top-base i 1)]))
                       b1 (range 1 (dec n)))
            ;; bottom face (reverse winding)
            bot-base (base-idx b2)
            b3 (reduce (fn [b [x z]] (add-vertex b [x 0.0 z] [0.0 -1.0 0.0] [x z])) b2 footprint)
            b4 (reduce (fn [b i] (add-indices b [bot-base (+ bot-base i 1) (+ bot-base i)]))
                       b3 (range 1 (dec n)))
            ;; side walls
            b5 (reduce
                (fn [b i]
                  (let [nxt (mod (inc i) n)
                        [x0 z0] (nth footprint i)
                        [x1 z1] (nth footprint nxt)
                        dx (- x1 x0) dz (- z1 z0)
                        len (Math/sqrt (double (+ (* dx dx) (* dz dz))))
                        [nx nz] (if (> len 1e-8) [(/ dz len) (- (/ dx len))] [0.0 1.0])
                        wall-base (base-idx b)
                        edge-len len]
                    (-> b
                        (add-vertex [x0 height z0] [nx 0.0 nz] [0.0 0.0])
                        (add-vertex [x1 height z1] [nx 0.0 nz] [edge-len 0.0])
                        (add-vertex [x1 0.0 z1] [nx 0.0 nz] [edge-len height])
                        (add-vertex [x0 0.0 z0] [nx 0.0 nz] [0.0 height])
                        (add-indices [wall-base (inc wall-base) (+ wall-base 2)
                                      wall-base (+ wall-base 2) (+ wall-base 3)]))))
                b4 (range n))]
        [(:positions b5) (:normals b5) (:uvs b5) (:indices b5)]))))

(defn hex-grid
  "H3-style hex grid on the XZ plane. `rings` = number of hex rings around
   center. Each hex is a flat hex prism. Returns a `loaded-mesh` map."
  [rings hex-radius hex-height spacing]
  (let [[hex-pos hex-norm hex-uv hex-idx] (hex-prism hex-radius hex-height)
        hex-vert-count (quot (count hex-pos) 3)
        step (* hex-radius 2.0 spacing)
        row-h (* step (Math/sqrt 3.0) 0.5)
        centers (for [q (range (- rings) (inc rings))
                      :let [r-min (max (- rings) (- (- q) rings))
                            r-max (min rings (+ (- q) rings))]
                      r (range r-min (inc r-max))]
                  [(* step (+ q (* r 0.5))) (* row-h r)])
        result (reduce
                (fn [acc [cx cz]]
                  (let [base-idx (:count acc)]
                    {:positions (into (:positions acc)
                                       (mapcat (fn [i] [(+ (nth hex-pos (* i 3)) cx)
                                                        (nth hex-pos (+ (* i 3) 1))
                                                        (+ (nth hex-pos (+ (* i 3) 2)) cz)])
                                               (range hex-vert-count)))
                     :normals (into (:normals acc) hex-norm)
                     :uvs (into (:uvs acc) hex-uv)
                     :indices (into (:indices acc) (map #(+ % (* base-idx hex-vert-count)) hex-idx))
                     :count (inc base-idx)}))
                {:positions [] :normals [] :uvs [] :indices [] :count 0}
                centers)]
    (loaded-mesh (:positions result) (:normals result) (:uvs result) (:indices result))))
