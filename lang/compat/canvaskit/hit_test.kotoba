(ns canvaskit.hit-test
  "UIView.hitTest(_:with:) / point(inside:with:) 相当の純関数。

  view(ヒット対象)は {:frame [x y w h] :z-position 0 ...} の平文 map。
  :frame は content(world) space、:z-position は CALayer.zPosition の意味
  (大きいほど手前。省略時 0.0)。同 z は列の後の要素が手前
  (UIKit の subviews 順 = 後に追加したものが上、と同じ規約)。"
  (:require [canvaskit.scroll-view :as sv]))

(defn point-inside?
  "UIView.point(inside:with:) — frame [x y w h] が点を含むか。"
  [[x y w h] [px py]]
  (and (<= x px (+ x w)) (<= y py (+ y h))))

(defn hit-test
  "content 点に当たる最前面の view(map)を返す。無ければ nil。"
  [views content-point]
  (->> views
       (map-indexed (fn [i v] (assoc v ::order i)))
       (filter #(point-inside? (:frame %) content-point))
       (sort-by (juxt #(:z-position % 0.0) ::order))
       last
       ((fn [v] (when v (dissoc v ::order))))))

(defn hit-test-in-view
  "view(screen) 点でヒットテストする(scroll-view で content へ変換してから)。"
  [scroll-view views view-point]
  (hit-test views (sv/convert-point-from-view scroll-view view-point)))
