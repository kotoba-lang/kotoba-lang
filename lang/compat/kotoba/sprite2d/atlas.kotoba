(ns kotoba.sprite2d.atlas
  "Texture-atlas frame lookup — pure data, no canvas/GPU dependency.

   An atlas descriptor is either:
   - a *sheet* atlas: {:image {:w iw :h ih} :frames {frame-name {:x :y :w :h} ...}}
     where each frame is an explicit pixel rect within the sheet, or
   - a *grid* atlas:  {:image {:w iw :h ih} :grid {:cell-w cw :cell-h ch :cols n :rows m}
                        :names [frame-name ...]}  ;; optional row-major name→index mapping
     where every cell has the same size and frames are addressed by [col row] or index
     (row-major) or by name if :names is supplied.

   Sibling to (not a replacement for) `kotoba.sprite2d`, the existing vector-shape hiccup
   painter, and `kotoba.sprite2d.layout`, the existing camera/depth-sort layout module — this
   namespace only concerns itself with resolving *which pixels* a frame occupies within a
   texture-atlas image, for the separate texture-atlas/frame-animation/batching concept.")

(defn sheet-atlas?
  [atlas]
  (contains? atlas :frames))

(defn grid-atlas?
  [atlas]
  (contains? atlas :grid))

(defn frame-count
  "Total number of addressable frames in a grid atlas."
  [{:keys [grid] :as atlas}]
  (when (grid-atlas? atlas)
    (* (:cols grid) (:rows grid))))

(defn- grid-index->rect
  [{:keys [grid]} idx]
  (let [{:keys [cell-w cell-h cols]} grid
        col (mod idx cols)
        row (quot idx cols)]
    {:x (* col cell-w) :y (* row cell-h) :w cell-w :h cell-h}))

(defn- grid-name->index
  [{:keys [names]} frame]
  (when names
    (let [idx (->> names
                   (map-indexed vector)
                   (some (fn [[i n]] (when (= n frame) i))))]
      idx)))

(defn pixel-rect
  "Resolve `frame` (a frame-name keyword/string for a sheet atlas, or a frame-name/index/[col
   row] for a grid atlas) to its pixel rect {:x :y :w :h} within the atlas image. Returns nil
   if the frame cannot be resolved."
  [atlas frame]
  (cond
    (sheet-atlas? atlas)
    (get (:frames atlas) frame)

    (grid-atlas? atlas)
    (let [{:keys [cols rows]} (:grid atlas)
          n (* cols rows)]
      (cond
        (vector? frame)
        (let [[col row] frame]
          (when (and (< -1 col cols) (< -1 row rows))
            (grid-index->rect atlas (+ (* row cols) col))))

        (integer? frame)
        (when (< -1 frame n)
          (grid-index->rect atlas frame))

        :else
        (when-let [idx (grid-name->index atlas frame)]
          (grid-index->rect atlas idx))))

    :else nil))

(defn uv-rect
  "Resolve `frame` to a normalized UV rect {:u0 :v0 :u1 :v1} (0..1, origin top-left, matching
   the pixel-rect convention) suitable for GPU texture sampling. Returns nil if unresolved."
  [{:keys [image] :as atlas} frame]
  (when-let [{:keys [x y w h]} (pixel-rect atlas frame)]
    (let [iw (:w image) ih (:h image)]
      {:u0 (/ x (double iw))
       :v0 (/ y (double ih))
       :u1 (/ (+ x w) (double iw))
       :v1 (/ (+ y h) (double ih))})))
