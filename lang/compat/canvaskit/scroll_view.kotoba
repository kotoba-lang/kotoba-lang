(ns canvaskit.scroll-view
  "UIScrollView-semantics zoomable/pannable viewport as pure data.

  Apple の座標語彙に揃える(HIG / UIKit):
    view space    = 画面座標(screen / canvas px)。UIScrollView の bounds 座標系。
    content space = ドキュメント座標(world)。board のアイテムが住む座標系。
    content-offset = bounds 原点に見えている content の位置(view-space 単位、
                     つまり zoom-scale 適用後)。 view = content * zoom - offset
    zoom-scale     = 拡大率(minimum-zoom-scale..maximum-zoom-scale にクランプ)。
    bounds         = 表示領域サイズ [w h](view space)。
    content-size   = スクロール可能な content の大きさ [w h]。**content space で
                     宣言する**(UIKit は zoom 済みサイズを持つが、宣言的 state
                     としては world 固定の方が扱いやすい)。nil = 無限キャンバス
                     (クランプなし。Freeform/freeboard の既定)。

  既存実装との等価性(このライブラリはそれらの正本化):
    freeboard.board / kami.mangaka.genko-render の viewport {:x :y :zoom} とは
      offset = [x*zoom y*zoom]
    の関係(canvaskit.viewport にブリッジあり)。screen=(world-pan)*zoom と
    view=content*zoom-offset は同じ式。")

(def default-minimum-zoom-scale 0.05)
(def default-maximum-zoom-scale 64.0)

(defn scroll-view
  "Create scroll-view state.
   opts: :bounds [w h](rect 系 op に必須) :content-size [w h](content space、nil=無限)
         :content-offset :zoom-scale :minimum-zoom-scale :maximum-zoom-scale"
  ([] (scroll-view {}))
  ([opts]
   (merge {:content-offset     [0.0 0.0]
           :zoom-scale         1.0
           :minimum-zoom-scale default-minimum-zoom-scale
           :maximum-zoom-scale default-maximum-zoom-scale
           :content-size       nil
           :bounds             nil}
          opts)))

(defn clamp-zoom-scale [{:keys [minimum-zoom-scale maximum-zoom-scale]} z]
  (max minimum-zoom-scale (min maximum-zoom-scale z)))

(defn- clamp-offset [{:keys [content-size bounds zoom-scale]} [ox oy]]
  (if (and content-size bounds)
    (let [[cw ch] content-size
          [bw bh] bounds
          mx (max 0.0 (- (* cw zoom-scale) bw))
          my (max 0.0 (- (* ch zoom-scale) bh))]
      [(-> ox (max 0.0) (min mx)) (-> oy (max 0.0) (min my))])
    [ox oy]))

;; ---- UICoordinateSpace: convert(_:to:) / convert(_:from:) -------------------
(defn convert-point-to-view
  "content(world) → view(screen)。view = content * zoom - offset"
  [{:keys [content-offset zoom-scale]} [cx cy]]
  (let [[ox oy] content-offset]
    [(- (* cx zoom-scale) ox) (- (* cy zoom-scale) oy)]))

(defn convert-point-from-view
  "view(screen) → content(world)。content = (view + offset) / zoom"
  [{:keys [content-offset zoom-scale]} [vx vy]]
  (let [[ox oy] content-offset]
    [(/ (+ vx ox) zoom-scale) (/ (+ vy oy) zoom-scale)]))

(defn convert-rect-to-view [{:keys [zoom-scale] :as sv} [x y w h]]
  (let [[vx vy] (convert-point-to-view sv [x y])]
    [vx vy (* w zoom-scale) (* h zoom-scale)]))

(defn convert-rect-from-view [{:keys [zoom-scale] :as sv} [x y w h]]
  (let [[cx cy] (convert-point-from-view sv [x y])]
    [cx cy (/ w zoom-scale) (/ h zoom-scale)]))

;; ---- scrolling ---------------------------------------------------------------
(defn set-content-offset
  "offset を直接置く(content-size があればクランプ)。UIScrollView.setContentOffset。"
  [sv [ox oy]]
  (assoc sv :content-offset (clamp-offset sv [ox oy])))

(defn scroll-by
  "プログラム的スクロール: offset += [dx dy](view space)。
   指ドラッグ追従(content が指についてくる = offset -= delta)は
   canvaskit.gesture/apply-pan を使う。"
  [{:keys [content-offset] :as sv} [dx dy]]
  (set-content-offset sv [(+ (first content-offset) dx) (+ (second content-offset) dy)]))

(defn visible-rect
  "いま見えている content-space の矩形 [x y w h]。:bounds 必須。"
  [{:keys [bounds zoom-scale] :as sv}]
  (let [[bw bh] bounds
        [cx cy] (convert-point-from-view sv [0.0 0.0])]
    [cx cy (/ bw zoom-scale) (/ bh zoom-scale)]))

(defn scroll-rect-to-visible
  "content rect が bounds に入るまでの最小 offset 変化。UIScrollView.scrollRectToVisible。"
  [{:keys [bounds] :as sv} content-rect]
  (let [[bw bh] bounds
        [vx vy vw vh] (convert-rect-to-view sv content-rect)
        dx (cond (< vx 0.0)           vx
                 (> (+ vx vw) bw)     (- (+ vx vw) bw)
                 :else                0.0)
        dy (cond (< vy 0.0)           vy
                 (> (+ vy vh) bh)     (- (+ vy vh) bh)
                 :else                0.0)]
    (scroll-by sv [dx dy])))

;; ---- zooming -----------------------------------------------------------------
(defn zoom-to-point
  "view 点 anchor(通常カーソル/ピンチ中心)の下の content 点を固定したまま
   new-zoom(クランプ)へズームする。freeboard の zoom-at / genko の zoom-viewport。"
  [sv new-zoom [ax ay :as anchor]]
  (let [z (clamp-zoom-scale sv new-zoom)
        [cx cy] (convert-point-from-view sv anchor)]
    (-> sv
        (assoc :zoom-scale z)
        (set-content-offset [(- (* cx z) ax) (- (* cy z) ay)]))))

(defn set-zoom-scale
  "bounds 中心を固定してズーム(UIScrollView.setZoomScale の見た目)。bounds が
   無ければ view 原点固定。"
  [{:keys [bounds] :as sv} new-zoom]
  (zoom-to-point sv new-zoom (if bounds [(/ (first bounds) 2.0) (/ (second bounds) 2.0)] [0.0 0.0])))

(defn reset-zoom
  "HIG: actual size / reset zoom = 100%。"
  [sv]
  (set-zoom-scale sv 1.0))

(defn zoom-to-rect
  "content rect 全体が bounds に収まり中央に来る zoom + offset。
   UIScrollView.zoom(to:) / HIG: fit bounds / zoom to selection。:bounds 必須。"
  [{:keys [bounds] :as sv} [x y w h]]
  (let [[bw bh] bounds
        z  (clamp-zoom-scale sv (min (/ bw (max w 1e-9)) (/ bh (max h 1e-9))))
        cx (+ x (/ w 2.0))
        cy (+ y (/ h 2.0))]
    (-> sv
        (assoc :zoom-scale z)
        (set-content-offset [(- (* cx z) (/ bw 2.0)) (- (* cy z) (/ bh 2.0))]))))

(def zoom-to-fit
  "HIG: fit to screen — content 全体の bbox を渡す zoom-to-rect の別名。"
  zoom-to-rect)
