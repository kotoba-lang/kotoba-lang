(ns kami.css
  "CSS as data — 'hiccup for styles'. An EDN map compiles to a CSS string (rules + @keyframes) and
   `style` to an inline cssText, so a game's flow cards / HUD / page chrome are data you read and
   fork like the scene. Property keys are kebab (border-radius), numbers get `px` (except unitless
   props), vectors are space-joined (padding [12 22] → 12px 22px). `.cljc`."
  (:require [clojure.string :as str]))

(def ^:private unitless
  #{:opacity :z-index :flex :flex-grow :flex-shrink :line-height :font-weight
    :order :zoom :scale :columns :grid-row :grid-column})

(defn- valstr [prop v]
  (cond
    (number? v)  (if (or (unitless prop) (zero? v)) (str v) (str v "px"))
    (vector? v)  (str/join " " (map #(valstr prop %) v))   ;; multi-value (margin/padding/transform list)
    (keyword? v) (name v)
    :else        (str v)))                                  ;; strings (colours, urls, calc(), …) pass through

(defn- decls [m] (str/join " " (for [[k v] m] (str (name k) ": " (valstr k v) ";"))))

(defn rule [sel m] (str sel " { " (decls m) " }"))

(defn kf
  "@keyframes from [[pct decls] …] (or a {pct decls} map)."
  [nm frames]
  (str "@keyframes " (name nm) " { "
       (str/join " " (for [[at m] frames] (str at "% { " (decls m) " }")))
       " }"))

(defn css
  "{:rules {selector → decls-map} :keyframes {name → frames}} → a CSS stylesheet string."
  [{:keys [rules keyframes]}]
  (str/join "\n"
            (concat (for [[sel m] rules] (rule sel m))
                    (for [[nm fr] keyframes] (kf nm fr)))))

(defn style
  "Inline cssText from a {prop → value} map — for el.style.cssText or a `style=` attribute."
  [m] (decls m))
