(ns brep.tessellate
  "Tessellation: BREP solid to triangle mesh conversion, plus volume/
  surface-area estimates derived from the tessellated mesh. Restored from
  kami-cad's `tessellate` module (deleted PR #82). Depends on `brep.kernel`
  for topology + vector math (this direction avoids the circular
  dependency the original Rust crate had via `super::tessellate` calls
  from within `brep.rs` — here `volume`/`surface-area` live alongside the
  tessellator that produces their input mesh)."
  (:require [brep.kernel :as k]
            [brep.config :as config]))

(defn- tessellate-planar-face
  "Fan triangulation for a planar face (convex assumption for outer wire).
  Chains the outer wire's edges into an ordered polygon loop, then fans
  from the first vertex."
  [face edge-map vert-map positions indices]
  (let [wire (first (:wires face))]
    (if (empty? wire)
      [positions indices]
      (let [first-edge (get edge-map (first wire))
            polygon
            (if-not first-edge
              []
              (let [start-p (get vert-map (:start-vertex first-edge))
                    [polygon _cur]
                    (reduce
                     (fn [[polygon current] eid]
                       (if-let [edge (get edge-map eid)]
                         (let [next (if (= (:start-vertex edge) current)
                                      (:end-vertex edge)
                                      (:start-vertex edge))
                               p (get vert-map next)]
                           [(if (and p (or (empty? polygon) (> (k/v-distance (peek polygon) p) config/epsilon-point-merge)))
                              (conj polygon p)
                              polygon)
                            next])
                         [polygon current]))
                     [(if start-p [start-p] []) (:start-vertex first-edge)]
                     wire)
                    polygon (if (and (> (count polygon) 1)
                                      (< (k/v-distance (first polygon) (peek polygon)) config/epsilon-point-merge))
                              (vec (butlast polygon))
                              polygon)]
                polygon))]
        (if (< (count polygon) 3)
          [positions indices]
          (let [base-idx (count positions)
                positions (into positions polygon)
                n (count polygon)
                new-indices (mapcat (fn [i] [base-idx (+ base-idx i) (+ base-idx i 1)]) (range 1 (dec n)))]
            [positions (into indices new-indices)]))))))

(defn- tessellate-cylinder-face
  [origin axis radius height positions indices]
  (let [segments config/cylinder-segments
        ax (k/v-normalize axis)
        u (if (< (Math/abs (first ax)) 0.9)
            (k/v-normalize (k/v-cross [1.0 0.0 0.0] ax))
            (k/v-normalize (k/v-cross [0.0 1.0 0.0] ax)))
        v (k/v-cross ax u)
        base-idx (count positions)
        ring-points (fn [h]
                      (for [i (range segments)]
                        (let [theta (* 2.0 Math/PI (/ (double i) segments))]
                          (k/v+ origin (k/v+ (k/v-scale ax h)
                                              (k/v+ (k/v-scale u (* radius (Math/cos theta)))
                                                    (k/v-scale v (* radius (Math/sin theta)))))))))
        new-positions (concat (ring-points 0.0) (ring-points height))
        positions (into positions new-positions)
        new-indices (mapcat
                     (fn [i]
                       (let [next (mod (inc i) segments)
                             b0 (+ base-idx i) b1 (+ base-idx next)
                             t0 (+ base-idx segments i) t1 (+ base-idx segments next)]
                         [b0 b1 t1 b0 t1 t0]))
                     (range segments))]
    [positions (into indices new-indices)]))

(defn- tessellate-sphere-face
  [center radius positions indices]
  (let [u-segments config/sphere-u-segments v-segments config/sphere-v-segments
        base-idx (count positions)
        new-positions (for [j (range (inc v-segments))
                            :let [phi (* Math/PI (/ (double j) v-segments))]
                            i (range (inc u-segments))
                            :let [theta (* 2.0 Math/PI (/ (double i) u-segments))
                                  x (* radius (Math/sin phi) (Math/cos theta))
                                  y (* radius (Math/sin phi) (Math/sin theta))
                                  z (* radius (Math/cos phi))]]
                        (k/v+ center [x y z]))
        positions (into positions new-positions)
        stride (inc u-segments)
        new-indices (mapcat
                     (fn [[j i]]
                       (let [a (+ base-idx (* j stride) i)
                             b (inc a) c (+ a stride) d (inc c)]
                         [a c b b c d]))
                     (for [j (range v-segments) i (range u-segments)] [j i]))]
    [positions (into indices new-indices)]))

(defn- tessellate-face [face edge-map vert-map positions indices]
  (case (:kind (:surface face))
    :plane (tessellate-planar-face face edge-map vert-map positions indices)
    :cylinder (let [{:keys [origin axis radius height]} (:surface face)]
                (tessellate-cylinder-face origin axis radius
                                          (or height config/cylinder-default-height)
                                          positions indices))
    :sphere (let [{:keys [center radius]} (:surface face)]
              (tessellate-sphere-face center radius positions indices))
    (tessellate-planar-face face edge-map vert-map positions indices)))

(defn tessellate-solid
  "Tessellate `solid` into a triangle mesh. Returns `[positions indices]`
  where each triangle is 3 consecutive indices."
  [solid edges vertices]
  (let [vert-map (into {} (map (fn [v] [(:id v) (:point v)]) vertices))
        edge-map (into {} (map (fn [e] [(:id e) e]) edges))]
    (reduce
     (fn [[positions indices] face]
       (tessellate-face face edge-map vert-map positions indices))
     [[] []]
     (mapcat :faces (:shells solid)))))

(defn volume
  "Approximate volume via signed tetrahedron method on tessellated faces."
  [solid edges vertices]
  (let [[positions indices] (tessellate-solid solid edges vertices)
        tri-count (quot (count indices) 3)]
    (Math/abs
     (/ (reduce
         (fn [vol i]
           (let [a (nth positions (nth indices (* i 3)))
                 b (nth positions (nth indices (inc (* i 3))))
                 c (nth positions (nth indices (+ 2 (* i 3))))]
             (+ vol (k/v-dot a (k/v-cross b c)))))
         0.0 (range tri-count))
        6.0))))

(defn surface-area
  "Approximate surface area from tessellated triangles."
  [solid edges vertices]
  (let [[positions indices] (tessellate-solid solid edges vertices)
        tri-count (quot (count indices) 3)]
    (reduce
     (fn [area i]
       (let [a (nth positions (nth indices (* i 3)))
             b (nth positions (nth indices (inc (* i 3))))
             c (nth positions (nth indices (+ 2 (* i 3))))]
         (+ area (* (k/v-length (k/v-cross (k/v- b a) (k/v- c a))) 0.5))))
     0.0 (range tri-count))))
