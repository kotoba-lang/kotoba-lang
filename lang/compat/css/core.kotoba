(ns css.core
  "CSS as EDN data."
  (:require [clojure.string :as str]))

(def unitless
  #{:animation-iteration-count :border-image-outset :border-image-slice
    :border-image-width :box-flex :box-flex-group :box-ordinal-group
    :column-count :columns :flex :flex-grow :flex-positive :flex-shrink
    :flex-negative :flex-order :grid-column :grid-row :font-weight
    :line-clamp :line-height :opacity :order :orphans :scale :tab-size
    :widows :z-index :zoom})

(defn- assert-no-css-breakout!
  "Property values and selectors are spliced into `prop: VALUE;` / `SELECTOR {
  ... }` text with no other escaping. `{` `}` let attacker-controlled data
  close the current declaration/rule block and inject entirely new,
  arbitrary sibling CSS rules (verified against tinycss2, a real CSS
  parser: a value like \"red; } .evil[href^=http] { background: url(...)\"
  parses as TWO distinct rules, not one -- genuine rule injection, not a
  cosmetic issue); `;` lets a value inject extra declarations within the
  same rule; `/*` starts a comment that can swallow the rest of the
  intended rule. Throw rather than silently producing that output."
  [what s]
  (when (re-find #"[{};]|/\*" s)
    (throw (ex-info (str "css: " what " contains a character ({ } ; or a /* "
                          "comment start) that could break out of the "
                          "current rule and inject arbitrary sibling CSS")
                     {:value s})))
  s)

(defn css-name [x]
  (cond
    (keyword? x) (name x)
    (symbol? x) (name x)
    :else (str x)))

(defn value-str [prop v]
  (cond
    (nil? v) nil
    (number? v) (if (or (unitless prop) (zero? v)) (str v) (str v "px"))
    (vector? v) (str/join " " (keep #(value-str prop %) v))
    (keyword? v) (assert-no-css-breakout! (str "value for " (css-name prop)) (name v))
    :else (assert-no-css-breakout! (str "value for " (css-name prop)) (str v))))

(defn declarations [m]
  (str/join " "
            (keep (fn [[k v]]
                    (when-some [s (value-str k v)]
                      (str (css-name k) ": " s ";")))
                  m)))

(def style declarations)

(defn rule [selector decls]
  (str (assert-no-css-breakout! "selector" (str selector)) " { " (declarations decls) " }"))

(defn media [query rules]
  (str "@media " query " { "
       (str/join " " (for [[sel decls] rules] (rule sel decls)))
       " }"))

(defn keyframes
  [nm frames]
  (str "@keyframes " (css-name nm) " { "
       (str/join " " (for [[at decls] frames]
                       (str at "% { " (declarations decls) " }")))
       " }"))

(def kf keyframes)

(defn css
  [{:keys [rules keyframes] :as sheet}]
  (str/join "\n"
            (concat
             (for [[sel decls] rules] (rule sel decls))
             (for [[nm frames] keyframes] (kf nm frames))
             (for [[query rules] (:media sheet)] (media query rules)))))

(defn style-node [sheet]
  [:style [:hiccup/raw (css sheet)]])

;; ---------------------------------------------------------------------------
;; Operator-console theme — shared by every cloud-itonami capability UI
;; ---------------------------------------------------------------------------

(def operator-theme
  "Base CSS rules shared by every cloud-itonami operator console: page
  chrome (body, header.bar, main), cards, tables, status badges and muted
  text. Capability UIs merge domain-specific rules on top."
  {"body"              {:font-family "system-ui,-apple-system,sans-serif"
                        :margin 0 :color "#1a1a1a" :background "#fafafa"}
   "header.bar"        {:display :flex :align-items :center :gap 12
                        :padding "12px 20px" :background "#fff"
                        :border-bottom "1px solid #e5e5e5"}
   "header.bar h1"     {:font-size 18 :margin 0 :font-weight 600}
   "header.bar .badge" {:margin-left :auto :font-size 12 :color "#666"}
   "main"              {:max-width 980 :margin "24px auto" :padding "0 20px"}
   ".card"             {:background "#fff" :border "1px solid #e5e5e5"
                        :border-radius 8 :padding 16 :margin-bottom 16}
   "h2"                {:margin-top 0 :font-size 15}
   "table"             {:width "100%" :border-collapse :collapse :font-size 14}
   "th, td"            {:text-align :left :padding "8px 10px"
                        :border-bottom "1px solid #f0f0f0"}
   "th"                {:font-weight 600 :color "#555" :font-size 12
                        :text-transform :uppercase :letter-spacing "0.04em"}
   "td.amt"            {:font-variant-numeric :tabular-nums :text-align :right}
   ".ok"               {:color "#137a3f"}
   ".warn"             {:color "#b25c00" :background "#fff8e1"
                        :padding "2px 6px" :border-radius 4}
   ".err"              {:color "#b3261e" :background "#fbe9e7"
                        :padding "2px 6px" :border-radius 4}
   ".critical"         {:color "#fff" :background "#b3261e"
                        :padding "2px 6px" :border-radius 4 :font-weight 600}
   ".muted"            {:color "#888"}})

(defn merge-theme
  "Merge a domain sheet's extra rules over the operator-theme base. Returns
  a sheet map {:rules ...} suitable for css/style-node."
  [extra-rules]
  {:rules (merge operator-theme extra-rules)})
