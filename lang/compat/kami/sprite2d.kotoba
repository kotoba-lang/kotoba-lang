(ns kami.sprite2d
  "2D characters as EDN — 'hiccup for sprites'. A sprite is a vector of primitive shapes in
   world-unit offsets:
     [[:ellipse {:dx :dy :rx :ry :fill}]
      [:circle  {:dx :dy :r :fill}]
      [:rect    {:dx :dy :w :h :fill}]
      [:arc     {:dx :dy :r :a0 :a1 :w :stroke}]]   ;; stroked arc — crescents, brims
   The renderer draws a top-down view: sky→ground wash + scattered trees + entity sprites,
   the camera centred on the player. No WebGPU — this is the canvas-2D twin for 2D games,
   so the characters and title art are data you can read and fork like everything else.

   The *layout* (which sprite, where, in what order) is a pure draw list in
   kami.sprite2d.layout — CLJ-tested on the JVM (camera, W/S orientation, variant swap,
   depth). This painter only turns that list + the trees into canvas ops."
  (:require [kami.sprite2d.layout :as layout]))

(defn- css [c] (str "rgba(" (js/Math.round (* 255 (nth c 0))) ","
                    (js/Math.round (* 255 (nth c 1))) ","
                    (js/Math.round (* 255 (nth c 2))) "," (nth c 3 1) ")"))

(defn- draw-shape!
  "Draw one EDN primitive at its :dx/:dy within the sprite (no animation)."
  [ctx kind o]
  (case kind
    :circle  (do (set! (.-fillStyle ctx) (css (:fill o)))
                 (.beginPath ctx) (.arc ctx (:dx o 0) (:dy o 0) (:r o 10) 0 (* 2 js/Math.PI)) (.fill ctx))
    :ellipse (do (set! (.-fillStyle ctx) (css (:fill o)))
                 (.beginPath ctx) (.ellipse ctx (:dx o 0) (:dy o 0) (:rx o 10) (:ry o 10) 0 0 (* 2 js/Math.PI)) (.fill ctx))
    :rect    (do (set! (.-fillStyle ctx) (css (:fill o)))
                 (.fillRect ctx (- (:dx o 0) (/ (:w o 10) 2)) (- (:dy o 0) (/ (:h o 10) 2)) (:w o 10) (:h o 10)))
    :arc     (do (set! (.-strokeStyle ctx) (css (:stroke o))) (set! (.-lineWidth ctx) (:w o 8))
                 (set! (.-lineCap ctx) "round")
                 (.beginPath ctx) (.arc ctx (:dx o 0) (:dy o 0) (:r o 10) (:a0 o 0) (:a1 o js/Math.PI)) (.stroke ctx))
    nil))

(defn prim!
  "Draw one EDN primitive, applying its optional `:anim` — a data-declared, tick-driven motion
   around a pivot (default the part's :dx/:dy):
     {:rot [amp freq] :pulse [amp freq] :bob [amp freq] :sway [amp freq] :pivot [px py]}
   so a sprite can say its arms heave or its legs swing as DATA, no renderer code. `ph` is a
   per-entity phase so identical sprites don't move in lockstep."
  [ctx [kind o] tick ph]
  (if-let [an (:anim o)]
    (let [px (nth (:pivot an) 0 (:dx o 0))
          py (nth (:pivot an) 1 (:dy o 0))
          w  (fn [[a f]] (* a (js/Math.sin (+ (* tick f) ph))))]
      (.save ctx)
      (.translate ctx px py)
      (when (:rot an)   (.rotate ctx (w (:rot an))))
      (when (:pulse an) (let [s (+ 1 (w (:pulse an)))] (.scale ctx s s)))
      (when (:bob an)   (.translate ctx 0 (w (:bob an))))
      (when (:sway an)  (.translate ctx (w (:sway an)) 0))
      (.translate ctx (- px) (- py))
      (draw-shape! ctx kind o)
      (.restore ctx))
    (draw-shape! ctx kind o)))

(defn draw-sprite!
  "Draw a sprite (vector of prims) centred at screen (sx,sy), scaled by k (with a soft shadow).
   `tick` drives any per-part `:anim`; `ph` desyncs identical sprites. The 5-arg form is static."
  ([ctx sprite sx sy k] (draw-sprite! ctx sprite sx sy k 0 0))
  ([ctx sprite sx sy k tick ph]
   (.save ctx)
   (.translate ctx sx sy)
   ;; ground shadow
   (set! (.-fillStyle ctx) "rgba(0,0,0,.22)")
   (.beginPath ctx) (.ellipse ctx 0 (* 260 k) (* 230 k) (* 70 k) 0 0 (* 2 js/Math.PI)) (.fill ctx)
   (.scale ctx k k)
   (doseq [p sprite] (prim! ctx p tick ph))
   (.restore ctx)))

;; deterministic tree scatter (top-down): a fixed pseudo-random field, memoised once.
(defonce ^:private trees* (atom nil))
(defn- trees [spread n]
  (or @trees*
      (reset! trees*
        (loop [i 0 s 2654435769 acc []]
          (if (>= i n) acc
            (let [s1 (bit-and (+ (* 1103515245 s) 12345) 0x7fffffff)
                  s2 (bit-and (+ (* 1103515245 s1) 12345) 0x7fffffff)
                  s3 (bit-and (+ (* 1103515245 s2) 12345) 0x7fffffff)
                  x (- (* (/ s1 0x7fffffff) (* 2 spread)) spread)
                  y (- (* (/ s2 0x7fffffff) (* 2 spread)) spread)
                  r (+ 70 (* (/ s3 0x7fffffff) 120))]
              (recur (inc i) s3 (conj acc [x y r]))))))))

(defn draw-2d!
  "Render one frame of the top-down 2D view onto ctx from the scene EDN + entity snapshot.
   `shake` is an optional [dx dy] camera offset (the slap kick) applied to the world layer.
   `fx` is an optional seq of screen-space juice effects (floating text + collect particles),
   each {:kind :text|:part :ox :oy :life :max :color (:text) (:size)} placed relative to centre.
   `tick` is a frame counter driving idle animation (breathing scale + bob, desynced per entity)."
  [ctx scene snap shake fx tick]
  (let [cv (.-canvas ctx) W (.-width cv) H (.-height cv)
        sky (:render/sky scene)
        cfg (:render/sprite2d scene)
        k   (* (get cfg :scale 0.34) (/ W 900.0))          ;; world→screen, density-independent
        sprites (:sprites scene)
        [px py] (layout/camera scene snap)   ;; side-scroller camera supported (EDN :camera)
        sx (fn [x] (+ (/ W 2) (* (- x px) k)))
        sy (fn [y] (- (/ H 2) (* (- y py) k)))]   ;; world +y = screen up (W moves you up)
    ;; sky → ground wash
    (let [g (.createLinearGradient ctx 0 0 0 H)]
      (.addColorStop g 0 (css (or (:zenith sky) [0.04 0.06 0.12])))
      (.addColorStop g 1 (css (or (:ground sky) [0.10 0.20 0.12])))
      (set! (.-fillStyle ctx) g) (.fillRect ctx 0 0 W H))
    ;; world layer (trees + characters) shakes on impact; the sky/ground wash above stays put
    (.save ctx)
    (when shake (.translate ctx (nth shake 0) (nth shake 1)))
    ;; side-scroller level geometry: the EDN :platformer :solids (ground + one-way platforms)
    ;; drawn camera-relative as earth blocks with a grass top — the same list the host lands on.
    ;; A solid is [x0 x1 ytop thickness fill? top?]; defaults give brown earth + green grass.
    (doseq [s (get-in scene [:platformer :solids])]
      (let [x0 (nth s 0) x1 (nth s 1) yt (nth s 2) th (nth s 3 4000)
            fill (nth s 4 [0.42 0.30 0.20]) top (nth s 5 [0.40 0.74 0.34])
            L (sx x0) R (sx x1) T (sy yt) B (sy (- yt th))]
        (when (and (< L (+ W 240)) (> R -240))
          (set! (.-fillStyle ctx) (css fill))
          (.fillRect ctx L T (- R L) (- B T))
          (set! (.-fillStyle ctx) (css top))             ;; grass band along the top surface
          (.fillRect ctx L (- T (* 6 k)) (- R L) (* 26 k)))))
    ;; trees (behind characters), faded by the night
    (doseq [[tx ty tr] (trees (get cfg :tree-spread 4200) (get cfg :tree-count 90))]
      (let [s (sx tx) t (sy ty)]
        (when (and (> s -120) (< s (+ W 120)) (> t -120) (< t (+ H 120)))
          (set! (.-fillStyle ctx) "rgba(0,0,0,.18)")
          (.beginPath ctx) (.ellipse ctx s (+ t (* tr k 0.7)) (* tr k 0.8) (* tr k 0.25) 0 0 (* 2 js/Math.PI)) (.fill ctx)
          (set! (.-fillStyle ctx) "rgb(34,74,42)")
          (.beginPath ctx) (.arc ctx s t (* tr k) 0 (* 2 js/Math.PI)) (.fill ctx)
          (set! (.-fillStyle ctx) "rgb(22,52,30)")
          (.beginPath ctx) (.arc ctx (- s (* tr k 0.3)) (- t (* tr k 0.3)) (* tr k 0.5) 0 (* 2 js/Math.PI)) (.fill ctx))))
    ;; entity sprites from the pure, CLJ-tested layout (camera, variant swap, depth order).
    ;; idle animation: a tick-driven breathing scale + vertical bob, phase desynced per entity so
    ;; the world feels alive (the gorilla heaves more; the player has a lighter idle bounce).
    (let [t (or tick 0)]
      (doseq [op (layout/draw-list scene snap W H)]
        (let [tag (:tag op)
              amp (cond (= tag "gorilla") 0.07 (= tag "player") 0.035 :else 0.025)
              ph  (* 0.013 (+ (:sx op) (:sy op)))
              br  (+ 1.0 (* amp (js/Math.sin (+ (* t 0.11) ph))))
              bob (* (if (= tag "gorilla") 5 3) (js/Math.sin (+ (* t 0.09) ph)))]
          ;; whole-sprite breathing/bob (default liveliness) + per-part :anim (declared in EDN)
          (draw-sprite! ctx (:sprite op) (:sx op) (+ (:sy op) bob) (* k br) t ph))))
    (.restore ctx)
    ;; juice layer (screen-space, centred on the player): floating "+N" text + collect particles
    (when (seq fx)
      (let [cx (/ W 2) cy (/ H 2)]
        (set! (.-textAlign ctx) "center")
        (doseq [e fx]
          (let [a (max 0 (/ (:life e) (:max e)))]
            (set! (.-globalAlpha ctx) a)
            (set! (.-fillStyle ctx) (:color e))
            (if (= (:kind e) :text)
              (do (set! (.-font ctx) (str "800 " (:size e 30) "px Nunito, system-ui, sans-serif"))
                  (.fillText ctx (:text e) (+ cx (:ox e)) (+ cy (:oy e))))
              (do (.beginPath ctx)
                  (.arc ctx (+ cx (:ox e)) (+ cy (:oy e)) (* 4 a) 0 (* 2 js/Math.PI))
                  (.fill ctx)))))
        (set! (.-globalAlpha ctx) 1)))))

(defn draw-portrait!
  "Draw a single sprite centred in ctx (for the title card character art)."
  [ctx sprite k]
  (let [cv (.-canvas ctx)]
    (.clearRect ctx 0 0 (.-width cv) (.-height cv))
    (draw-sprite! ctx sprite (/ (.-width cv) 2) (* (.-height cv) 0.62) k)))
