(ns kotoba.render.splat
  "3D Gaussian Splatting data structures + pure CPU-side cull/bounds
   algorithms. Ported from `kami-render/src/splat.rs`.

   A `GaussianSplat` is a map:
     {:position [x y z] :opacity logit-opacity :scale [sx sy sz] (log-space)
      :rotation [w x y z] :sh-dc [r g b]}
   matching the 64-byte GPU storage-buffer element from the Rust source
   (minus explicit padding fields, which are a GPU-buffer-layout concern).

   NOT ported: `SplatGpuBuffers::upload` (wgpu storage-buffer upload) —
   host-adapter GPU code.")

(defn new-cloud
  "Empty splat cloud: `{:splats [] :sh-degree 0 :sh-rest []}`."
  []
  {:splats [] :sh-degree 0 :sh-rest []})

(defn count-splats [cloud] (count (:splats cloud)))

(defn sh-coef-count
  "Number of SH coefficients per splat for `sh-degree`. K = (d+1)^2."
  [cloud]
  (let [d (inc (:sh-degree cloud))]
    (* d d)))

(defn cull-indices
  "Indices of splats within `max-distance` of `camera-pos`."
  [cloud camera-pos max-distance]
  (let [max-dist-sq (* max-distance max-distance)
        [cx cy cz] camera-pos]
    (vec
     (keep-indexed
      (fn [i s]
        (let [[px py pz] (:position s)
              dx (- px cx) dy (- py cy) dz (- pz cz)]
          (when (<= (+ (* dx dx) (* dy dy) (* dz dz)) max-dist-sq) i)))
      (:splats cloud)))))

(defn bounds
  "`[min max]` bounding box of all splat positions. `[[0 0 0] [0 0 0]]` for
   an empty cloud."
  [cloud]
  (if (empty? (:splats cloud))
    [[0.0 0.0 0.0] [0.0 0.0 0.0]]
    (reduce
     (fn [[mn mx] s]
       [(mapv min mn (:position s)) (mapv max mx (:position s))])
     [(:position (first (:splats cloud))) (:position (first (:splats cloud)))]
     (:splats cloud))))
