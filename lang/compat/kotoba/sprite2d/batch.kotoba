(ns kotoba.sprite2d.batch
  "Sprite-instance batching — pure data, no GPU-API dependency.

   Given a collection of sprite instances, each shaped:

     {:texture-id :hero-sheet   ;; groups draw calls (one texture bind per group)
      :frame :walk-2            ;; atlas frame name/index (see kotoba.sprite2d.atlas)
      :x 10 :y 20               ;; world/screen position
      :rotation 0.0              ;; radians, optional (default 0)
      :scale 1.0                 ;; uniform or [sx sy], optional (default 1)
      :tint [1 1 1 1]            ;; optional RGBA multiplier, default opaque white
      :layer 0}                  ;; depth/paint-order key, optional (default 0)

   `build` groups instances by :texture-id (so a renderer can bind one texture per group and
   issue one draw call per group — the point of 'batching') and sorts instances *within* each
   group by :layer (ties broken by original input order, i.e. a stable sort) so painter-order
   is preserved. The output is plain data — vectors/maps of numbers and keywords — deliberately
   not tied to any GPU API (WebGL/WebGPU/etc.), so it can feed a future render-IR layer such as
   the parallel `kotoba-lang/webgl` work without this namespace depending on it.

   Sibling to (not a replacement for) `kotoba.sprite2d`/`kotoba.sprite2d.layout`, the existing
   vector-shape hiccup painter and its camera/depth-sort layout — this module is about
   texture-atlas sprite instancing/batching, a separate concern.")

(defn- normalize-instance
  [{:keys [texture-id frame x y rotation scale tint layer] :as inst}]
  {:texture-id texture-id
   :frame frame
   :x (or x 0)
   :y (or y 0)
   :rotation (or rotation 0.0)
   :scale (or scale 1.0)
   :tint (or tint [1 1 1 1])
   :layer (or layer 0)})

(defn build
  "Group `instances` by :texture-id (grouping order = order of first appearance) and, within
   each group, stable-sort by :layer ascending. Returns a vector of
   {:texture-id tid :instances [instance ...]}."
  [instances]
  (let [normalized (map normalize-instance instances)
        order (distinct (map :texture-id normalized))
        by-tex (group-by :texture-id normalized)]
    (mapv (fn [tid]
            {:texture-id tid
             :instances (vec (sort-by :layer (by-tex tid)))})
          order)))

(defn instance-count
  "Total number of sprite instances across every group of a batch (as returned by `build`)."
  [batches]
  (reduce + (map (comp count :instances) batches)))

(defn draw-call-count
  "Number of texture groups (== number of draw calls a naive renderer would issue) in a
   batch."
  [batches]
  (count batches))
