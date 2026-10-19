(ns kami.sprite2d.layout
  "Pure 2D *layout* — the draw list (what is drawn, where, in what order) as DATA, so the
   rendering decisions are testable in CLJ without a canvas: camera follow, sprite/variant
   pick (the raging-gorilla swap), depth order, and screen orientation (the classic W/S-up bug).
   `.cljs` painter (isekai.sprite2d) turns this list into canvas ops; `bb` golden-tests it.")

(defn- sq [x] (* x x))

(defn scale-k
  "World→screen scale for a viewport of width W (density-independent, matches the painter)."
  [scene W]
  (* (get-in scene [:render/sprite2d :scale] 0.34) (/ W 900.0)))

(defn camera
  "The camera anchor (world [cx cy]) for this snapshot. Default: follow the player in X and
   Y (top-down). With `:render/sprite2d :camera {:mode :side :y x-offset}` it becomes a
   *side-scroller* camera — follow the player in X (optionally offset so they sit left of
   centre for look-ahead), but hold Y fixed so jumping/floating reads as rising on screen.
   2-arg form keeps the legacy (snap-only) call working (follows both axes)."
  ([snap] (camera nil snap))
  ([scene snap]
   (let [cam (get-in scene [:render/sprite2d :camera])
         p   (first (filter #(= (:tag %) "player") snap))
         px  (if p (nth (:pos p) 0) 0)
         py  (if p (nth (:pos p) 1) 0)]
     (if (= (:mode cam) :side)
       [(+ px (or (:x-offset cam) 0)) (or (:y cam) 0)]
       [px py]))))

(defn draw-list
  "Ordered sprite draw ops for a frame, in screen space (painter order: north/far first so
   nearer things layer over them). Each op: {:tag :variant :sprite :sx :sy}. Pure — no canvas.
   Screen y grows downward, but world +y maps UP (so 'up' on the keyboard is up on screen)."
  [scene snap W H]
  (let [cfg (:render/sprite2d scene)
        k (scale-k scene W)
        sprites (:sprites scene)
        [px py] (camera scene snap)
        sx (fn [x] (+ (/ W 2.0) (* (- x px) k)))
        sy (fn [y] (- (/ H 2.0) (* (- y py) k)))   ;; world +y = screen up
        aw (:awake cfg)
        w2 (when aw (sq (:within aw 1000)))]
    (->> snap
         ;; painter order: higher world-y (north, drawn smaller/up) goes behind → sort y desc
         (sort-by #(- (nth (:pos %) 1)))
         (keep (fn [e]
                 (let [tag (:tag e) ex (nth (:pos e) 0) ey (nth (:pos e) 1)
                       near? (boolean (and aw (= tag (:tag aw))
                                           (< (+ (sq (- ex px)) (sq (- ey py))) w2)))
                       spk (if near? (:variant aw) (keyword tag))
                       sp (or (get sprites spk) (get sprites (keyword tag)))]
                   (when sp {:tag tag
                             :variant (when near? (:variant aw))
                             :sprite sp
                             :sx (sx ex) :sy (sy ey)}))))
         vec)))

(defn world->screen
  "Returns [sx sy k] — fns mapping world x/y to screen px for this scene/snapshot/viewport, plus the
   scale k. The same transform draw-2d! and draw-list use, exposed so the GPU path (kami.scene2d) can
   place terrain + trees identically."
  [scene snap W H]
  (let [k (scale-k scene W) c (camera scene snap) px (first c) py (second c)]
    [(fn [x] (+ (/ W 2.0) (* (- x px) k)))
     (fn [y] (- (/ H 2.0) (* (- y py) k)))
     k]))

(defn tree-scatter
  "Deterministic background tree scatter [[x y r]…] — the same seeded positions kami.sprite2d paints,
   as pure data so both Canvas2D and GPU renderers share one source."
  [spread n]
  (loop [i 0 s 2654435769 acc []]
    (if (>= i n) acc
      (let [s1 (bit-and (+ (* 1103515245 s) 12345) 0x7fffffff)
            s2 (bit-and (+ (* 1103515245 s1) 12345) 0x7fffffff)
            s3 (bit-and (+ (* 1103515245 s2) 12345) 0x7fffffff)
            x (- (* (/ s1 0x7fffffff) (* 2 spread)) spread)
            y (- (* (/ s2 0x7fffffff) (* 2 spread)) spread)
            r (+ 70 (* (/ s3 0x7fffffff) 120))]
        (recur (inc i) s3 (conj acc [x y r]))))))
