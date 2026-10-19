(ns slides.architecture-site
  "Static GitHub Pages gallery generated from slides.architecture EDN."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [slides.architecture :as architecture]
            [slides.hiccup :as hiccup]))

(def styles
  {:page {:margin 0 :background "#0B1220" :color "#E8EEF7"
          :font-family "Inter, ui-sans-serif, system-ui, sans-serif"}
   :header {:max-width "1440px" :margin "0 auto" :padding "64px 28px 28px"}
   :eyebrow {:color "#7DD3FC" :font-size "12px" :font-weight 800
             :letter-spacing ".15em" :text-transform "uppercase"}
   :title {:font-size "clamp(34px,5vw,66px)" :line-height 1 :margin "14px 0 18px"
           :letter-spacing "-.04em"}
   :lead {:max-width "800px" :color "#AFC0D6" :font-size "18px" :line-height 1.65}
   :chips {:display "flex" :gap "8px" :flex-wrap "wrap" :margin-top "24px"}
   :chip {:padding "8px 12px" :border "1px solid #2A405C" :border-radius "999px"
          :background "#111D2E" :color "#C8D5E5" :font-size "12px"}
   :grid {:display "grid" :gap "28px" :max-width "1440px" :margin "0 auto"
          :padding "20px 28px 80px"}
   :card {:background "#111D2E" :border "1px solid #243A55" :border-radius "24px"
          :padding "18px" :box-shadow "0 24px 70px rgba(0,0,0,.25)"}
   :meta {:display "flex" :justify-content "space-between" :align-items "center"
          :gap "16px" :padding "4px 4px 16px"}
   :sample-title {:font-size "18px" :margin 0}
   :theme {:font-family "ui-monospace, SFMono-Regular, monospace" :font-size "11px"
           :color "#7DD3FC" :text-transform "uppercase" :letter-spacing ".08em"}
   :frame {:overflow "hidden" :border-radius "16px" :background "#fff"}
   :svg {:display "block" :width "100%" :height "auto"}
   :footer {:max-width "1440px" :margin "0 auto" :padding "0 28px 64px"
            :color "#8EA3BC" :font-size "13px"}
   :link {:color "#7DD3FC"}})

(defn- css-name [k]
  (-> k name (str/replace "_" "-")))

(defn- style-text [m]
  (apply str (map (fn [[k v]] (str (css-name k) ":" v ";")) m)))

(defn gallery-hiccup []
  [:html {:lang "ja"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width,initial-scale=1,viewport-fit=cover"}]
    [:meta {:name "theme-color" :content "#0B1220"}]
    [:title "Kotoba Architecture · EDN-first diagram system"]
    [:meta {:name "description" :content "EDN-first AWS-style, C4, executive, and technical architecture diagrams."}]]
   [:body {:style (style-text (:page styles))}
    [:header {:style (style-text (:header styles))}
     [:div {:style (style-text (:eyebrow styles))} "KOTOBA-LANG / ARCHITECTURE"]
     [:h1 {:style (style-text (:title styles))} "Architecture as data."]
     [:p {:style (style-text (:lead styles))}
      "テーマ、アイコン、境界、レーン、ノード、接続、ラベル、そして自動配置まで、すべてEDNを正本として生成した構成図です。SVGは中間成果物であり、同じ意味モデルからSVGraphと編集可能なPPTXへ展開できます。"]
     [:div {:style (style-text (:chips styles))}
      (for [label ["service-card" "database-node" "boundary" "lane" "arrow-label"
                   "Light" "Dark" "Executive" "Technical" "deterministic auto-layout"]]
        [:span {:style (style-text (:chip styles))} label])]]
    [:main {:style (style-text (:grid styles))}
     (for [sample architecture/samples
           :let [theme (get architecture/themes (:theme sample))]]
       [:article {:id (name (:id sample)) :style (style-text (:card styles))}
        [:div {:style (style-text (:meta styles))}
         [:h2 {:style (style-text (:sample-title styles))} (:title sample)]
         [:span {:style (style-text (:theme styles))} (:label theme)]]
        [:div {:style (style-text (:frame styles))}
         [:hiccup/raw (architecture/diagram->svg sample)]]
        [:details
         [:summary "EDN source"]
         [:pre {:style "white-space:pre-wrap;color:#b9c9dc;font-size:11px;overflow:auto;padding:16px"}
          (pr-str sample)]]])]
    [:footer {:style (style-text (:footer styles))}
     "Generated without hand-authored SVG. "
     [:a {:href "./index.html" :style (style-text (:link styles))} "Open slides editor"]
     " · "
     [:a {:href "https://github.com/kotoba-lang/slides" :style (style-text (:link styles))} "GitHub source"]]]])

(defn index-html []
  (str "<!doctype html>\n" (hiccup/->html (gallery-hiccup)) "\n"))

(defn write! []
  (let [out (io/file "docs" "architecture.html")]
    (io/make-parents out)
    (spit out (index-html))
    out))
