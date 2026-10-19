(ns kami.ui
  "hiccup for the HUD — a UI described as EDN data, rendered as a DOM overlay over the
   game canvas. The 3D world is WebGPU (kami.webgpu); the HUD is plain DOM, which is what
   hiccup is actually for. The spec is data: store it as datoms, fork it, bind it to game
   state. `mount!` makes an overlay sized to the canvas; `render!` (re)builds it from EDN.

   A spec is a vector of widgets:

     [[:panel {:at :bottom-left}
       [:bar  {:label \"HP\" :value 0.8 :color [0.2 0.9 0.4]}]
       [:text {:value \"AMMO 24 / 120\"}]]
      [:panel {:at :top-right}
       [:minimap {:size 132 :dots [[0.5 0.5 [0.3 0.62 1.0]] ...]}]]]")

(defn- css [m] (reduce-kv (fn [s k v] (str s (name k) ":" v ";")) "" m))
(defn- rgb [[r g b]] (str "rgb(" (int (* 255 r)) "," (int (* 255 g)) "," (int (* 255 b)) ")"))
(defn- el [tag style] (doto (js/document.createElement tag) (-> .-style .-cssText (set! (css style)))))

(def ^:private corner
  {:top-left    {:top "12px" :left "12px"}
   :top-right   {:top "12px" :right "12px"}
   :bottom-left {:bottom "12px" :left "12px"}
   :bottom-right{:bottom "12px" :right "12px"}})

;; --- widgets: EDN node → DOM element ----------------------------------------

(defmulti ^:private widget (fn [node] (first node)))

(defmethod widget :text [[_ {:keys [value color]}]]
  (doto (el "div" {:color (if color (rgb color) "#eaeef6") :font "600 14px ui-monospace,monospace"
                   :text-shadow "0 1px 2px rgba(0,0,0,.6)" :margin "2px 0"})
    (-> .-textContent (set! (str value)))))

(defmethod widget :bar [[_ {:keys [label value color]}]]
  (let [wrap (el "div" {:margin "3px 0" :min-width "150px"})
        lab  (doto (el "div" {:color "#cdd6e4" :font "600 11px ui-monospace,monospace"
                              :text-shadow "0 1px 2px rgba(0,0,0,.6)"})
               (-> .-textContent (set! (str label))))
        track (el "div" {:background "rgba(0,0,0,.45)" :border-radius "5px" :height "10px"
                         :overflow "hidden" :box-shadow "inset 0 0 0 1px rgba(255,255,255,.15)"})
        fill (el "div" {:width (str (* 100 (max 0 (min 1 value))) "%") :height "100%"
                        :background (rgb (or color [0.3 0.8 0.4])) :transition "width .15s"})]
    (.appendChild track fill) (.appendChild wrap lab) (.appendChild wrap track) wrap))

(defmethod widget :minimap [[_ {:keys [size dots]}]]
  (let [s (or size 128)
        box (el "div" {:position "relative" :width (str s "px") :height (str s "px")
                       :background "rgba(8,14,22,.55)" :border-radius "8px"
                       :box-shadow "inset 0 0 0 1px rgba(255,255,255,.18)"})]
    (doseq [[x y col] dots]
      (.appendChild box (el "div" {:position "absolute"
                                   :left (str (* 100 (max 0 (min 1 x))) "%")
                                   :top  (str (* 100 (max 0 (min 1 y))) "%")
                                   :width "6px" :height "6px" :margin "-3px 0 0 -3px"
                                   :border-radius "50%" :background (rgb col)})))
    box))

(defmethod widget :panel [[_ opts & children]]
  (let [p (el "div" (merge {:position "absolute" :display "flex" :flex-direction "column"
                            :gap "2px" :padding "10px 12px" :background "rgba(10,16,26,.38)"
                            :border-radius "10px" :backdrop-filter "blur(3px)"}
                           (corner (:at opts :bottom-left))))]
    (doseq [c children] (when c (.appendChild p (widget c))))
    p))

(defmethod widget :default [_] nil)

;; --- mount + render ---------------------------------------------------------

(defn mount!
  "Create (or reuse) an overlay div sized to the canvas. Returns the overlay element."
  [canvas]
  (let [parent (.-parentElement canvas)
        ov (or (.querySelector parent "[data-kami-ui]") (js/document.createElement "div"))]
    (set! (.. parent -style -position) "relative")
    (.setAttribute ov "data-kami-ui" "")
    (set! (.. ov -style -cssText)
          (css {:position "absolute" :pointer-events "none" :overflow "hidden"
                :left (str (.-offsetLeft canvas) "px") :top (str (.-offsetTop canvas) "px")
                :width (str (.-clientWidth canvas) "px") :height (str (.-clientHeight canvas) "px")}))
    (.appendChild parent ov)
    ov))

(defn render!
  "(Re)build the overlay's DOM from the EDN UI spec (a vector of :panel nodes)."
  [overlay spec]
  (set! (.-innerHTML overlay) "")
  (doseq [node spec] (when-let [e (widget node)] (.appendChild overlay e))))
