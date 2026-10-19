(ns drawingml.core
  "EDN-first DrawingML XML builders."
  (:require [clojure.string :as str]))

(def ns-a "http://schemas.openxmlformats.org/drawingml/2006/main")
(def emu-per-px 9525)

(defn emu [px] (long (Math/round (double (* px emu-per-px)))))

(defn esc [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn render-attrs [m]
  (apply str (for [[k v] (sort-by (comp name key) m)
                   :when (some? v)]
               (str " " (name k) "=\"" (esc v) "\""))))

(declare render)

(defn el [tag attrs children]
  {:tag tag :attrs (or attrs {}) :children (vec children)})

(defn render [node]
  (cond
    (nil? node) ""
    (string? node) (esc node)
    (number? node) (str node)
    (map? node) (let [{:keys [tag attrs children]} node
                      tag-name (name tag)]
                  (if (seq children)
                    (str "<" tag-name (render-attrs attrs) ">"
                         (apply str (map render children))
                         "</" tag-name ">")
                    (str "<" tag-name (render-attrs attrs) "/>")))
    (sequential? node) (apply str (map render node))
    :else (esc node)))

(defn xfrm [{:keys [x y cx cy rot flip-h flip-v]}]
  (el :a:xfrm (cond-> {}
                rot (assoc :rot rot)
                flip-h (assoc :flipH 1)
                flip-v (assoc :flipV 1))
      [(el :a:off {:x (emu (or x 0)) :y (emu (or y 0))} [])
       (el :a:ext {:cx (emu (or cx 0)) :cy (emu (or cy 0))} [])]))

(defn srgb [hex]
  (el :a:srgbClr {:val (str/upper-case (str/replace (str hex) #"^#" ""))} []))

(defn solid-fill [hex]
  (el :a:solidFill {} [(srgb hex)]))

(defn no-fill [] (el :a:noFill {} []))

(defn line
  [{:keys [color width cap]}]
  (el :a:ln (cond-> {}
              width (assoc :w (emu width))
              cap (assoc :cap (name cap)))
      [(if color (solid-fill color) (no-fill))]))

(defn preset-geometry [prst]
  (el :a:prstGeom {:prst (name prst)} [(el :a:avLst {} [])]))

(defn text-run [text & [{:keys [size color]}]]
  (el :a:r {}
      [(el :a:rPr (cond-> {}
                   size (assoc :sz (long (* size 100))))
           (cond-> []
             color (conj (solid-fill color))))
       (el :a:t {} [text])]))

(defn paragraph [& runs]
  (el :a:p {} runs))

(defn text-body [& paragraphs]
  (el :a:txBody {} (concat [(el :a:bodyPr {} []) (el :a:lstStyle {} [])] paragraphs)))

(defn shape-properties [{:keys [x y width height geometry fill stroke]}]
  (el :p:spPr {}
      (cond-> [(xfrm {:x x :y y :cx width :cy height})
               (preset-geometry (or geometry :rect))]
        fill (conj (solid-fill fill))
        stroke (conj (line stroke)))))

(defn table-cell [text & [{:keys [fill]}]]
  (el :a:tc {}
      [(text-body (paragraph (text-run text)))
       (el :a:tcPr {} (cond-> [] fill (conj (solid-fill fill))))]))

(defn table-row [height cells]
  (el :a:tr {:h (emu height)} cells))

(defn valid-node? [node]
  (cond
    (nil? node) true
    (string? node) true
    (number? node) true
    (sequential? node) (every? valid-node? node)
    (map? node) (and (:tag node)
                     (map? (:attrs node))
                     (sequential? (:children node))
                     (every? valid-node? (:children node)))
    :else false))
