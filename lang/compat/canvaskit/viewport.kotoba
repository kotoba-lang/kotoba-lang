(ns canvaskit.viewport
  "既存 viewport 形とのブリッジ。

  freeboard.board(kotoba-lang/freeboard)と kami.mangaka.genko-render
  (kotoba-lang/kami-genko、freeboard から移植)は viewport を
  {:x :y :zoom}(x y = screen 原点に見えている world 点)で持つ。
  scroll-view とは offset = [x*zoom y*zoom] の関係で等価:
    screen = (world - pan) * zoom  ≡  view = content * zoom - offset
  既存 doc/保存形式を変えずに canvaskit の op 群へ段階移行するための変換。"
  (:require [canvaskit.scroll-view :as sv]))

(defn from-viewport
  "{:x :y :zoom} → scroll-view。opts で :bounds :content-size 等を補える。"
  ([vp] (from-viewport vp {}))
  ([{:keys [x y zoom]} opts]
   (sv/scroll-view (merge opts {:content-offset [(* x zoom) (* y zoom)]
                                :zoom-scale zoom}))))

(defn to-viewport
  "scroll-view → {:x :y :zoom}(freeboard/genko 形)。"
  [{:keys [content-offset zoom-scale]}]
  (let [[ox oy] content-offset]
    {:x (/ ox zoom-scale) :y (/ oy zoom-scale) :zoom zoom-scale}))
