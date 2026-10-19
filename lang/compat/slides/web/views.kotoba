(ns slides.web.views
  "Pure-hiccup views for the slides web editor (.cljc, no reagent import).

  The SAME hiccup data can be rendered by a browser host adapter and to HTML by
  shitsuke.hiccup/->html for SSR (clj) — the dual-render contract. Views take
  the app-db map and derive; they carry no side-effects.

  Interaction is via stable data-attributes (not cljs callbacks) so the SSR HTML
  is identical to the host-rendered DOM and an external enhancer can drive
  re-frame dispatch:
    :data-act      button actions (new-deck, add-slide, ...)
    :data-slide    slide thumbnail selection (idx)
    :data-shape    shape selection (idx)
    :data-field    property input (prefixed: shape.x / slide.title / ...)
  This preserves the document-level dispatch model while keeping the rendering
  surface as portable hiccup data.

  Chrome is built on the kotoba-lang design-system paved road (ADR-2607122200):
  kotoba-ui.core (single entry — shell layout, glass toolbar/buttons/fields)
  + appkit.core (desktop panes: thick/flat panels). Every chrome control keeps
  its stable #id + data-* enhancer hooks (see `with-attrs`). The slide CANVAS
  and thumbnails render user deck data (colors/sizes from the deck EDN) —
  that stays inline user data, untouched by the design system."
  (:require [clojure.string :as str]
            [slides.design :as design]
            [kotoba-ui.core :as ui]
            [appkit.core :as appkit]))

;; ---------------------------------------------------------------------------
;; pure helpers
;; ---------------------------------------------------------------------------

(defn numeric [x fallback]
  (if (number? x) x fallback))

(defn positive [x fallback]
  (if (and (number? x) (pos? x)) x fallback))

(defn valid-hex [x fallback]
  (let [s (-> (or x fallback) str (str/replace #"^#" "") str/upper-case)]
    (if (re-matches #"[0-9A-F]{6}" s) s fallback)))

(defn slide-index [db]
  (let [idx (:selected-slide db)
        slides (vec (:slides/slides (:deck db)))
        max-idx (max 0 (dec (count slides)))]
    (min max-idx (max 0 idx))))

(defn selected-slide [db]
  (let [ss (vec (:slides/slides (:deck db)))]
    (get ss (slide-index db))))

(defn selected-shape [db]
  (when-let [idx (:selected-shape db)]
    (let [slide (selected-slide db)]
      (get (:slides/shapes slide) idx))))

(defn selected-shapes-set [db]
  (set (or (:selected-shapes db)
           (when-let [idx (:selected-shape db)] #{idx})
           #{})))

(defn with-attrs
  "Merge extra root attrs (the stable :id / data-* hooks of the SSR enhancer
  contract) onto a kotoba-ui component's hiccup ([tag attrs & children]).
  The component's own generated attrs win on key conflict."
  [node extra]
  (update node 1 #(merge extra %)))

(defn chrome-button
  "Glass chrome button carrying the editor's stable #id + data-act contract.
  opts pass through to kotoba-ui.core/button (:disabled :class :title :type)."
  ([id label act] (chrome-button id label act nil))
  ([id label act opts]
   (with-attrs (ui/button label (assoc opts :act act)) {:id id})))

;; ---------------------------------------------------------------------------
;; slide list / canvas
;; ---------------------------------------------------------------------------

(defn thumb-shape [deck idx shape]
  (let [resolved (design/resolve-shape deck shape)
        width (positive (:slides/width deck) 10)
        height (positive (:slides/height deck) 5.625)
        style {:left (str (/ (* 100 (numeric (:slides/x resolved) 0)) width) "%")
               :top (str (/ (* 100 (numeric (:slides/y resolved) 0)) height) "%")
               :width (str (/ (* 100 (positive (:slides/w resolved) 1)) width) "%")
               :height (str (/ (* 100 (positive (:slides/h resolved) 1)) height) "%")}]
    [:i {:class (str "thumb-shape " (name (:slides/shape resolved :text)))
         :style (cond-> style
                  (= :rect (:slides/shape resolved))
                  (assoc :background (str "#" (valid-hex (:slides/fill resolved) "EAF0F8")))
                  (not= :rect (:slides/shape resolved))
                  (assoc :background (if (zero? idx)
                                       "var(--hig-color-label)"
                                       "var(--hig-color-secondary-label)")))}]))

(defn thumb-preview [deck slide]
  [:div.thumb-preview
   (map-indexed (fn [i shape] (thumb-shape deck i shape))
                (take 5 (:slides/shapes slide)))])

(defn slide-thumb [deck idx slide selected?]
  [:button.thumb {:class (when selected? "active") :data-slide idx :type "button"}
   [:small (inc idx)]
   (thumb-preview deck slide)
   [:span (:slides/title slide (:slides/id slide))]
   [:em (str (count (:slides/shapes slide)))]])

(defn slide-list [db]
  (let [deck (:deck db)
        ss (vec (:slides/slides deck))
        sel (slide-index db)]
    (into [:div] (map-indexed (fn [i s] (slide-thumb deck i s (= i sel))) ss))))

(defn shape-style [deck-width deck-height shape selected?]
  (let [x (/ (* 100 (numeric (:slides/x shape) 0)) deck-width)
        y (/ (* 100 (numeric (:slides/y shape) 0)) deck-height)
        w (/ (* 100 (positive (:slides/w shape) 1)) deck-width)
        h (/ (* 100 (positive (:slides/h shape) 1)) deck-height)]
    (cond-> {:left (str x "%")
             :top (str y "%")
             :width (str w "%")
             :height (str h "%")
             :font-size (str (positive (:slides/font-size shape) 24) "px")
             :color (str "#" (valid-hex (:slides/color shape) "17202A"))}
      selected? (assoc :outline "2px solid var(--hig-color-tint)"
                       :outline-offset "2px"))))

(defn shape-node [db idx shape]
  (let [d (:deck db)
        resolved (design/resolve-shape d shape)
        width (positive (:slides/width d) 10)
        height (positive (:slides/height d) 5.625)
        selected? (contains? (selected-shapes-set db) idx)
        base (str "shape " (name (:slides/shape resolved :text)) (when selected? " selected"))
        style (shape-style width height resolved selected?)]
    (case (:slides/shape resolved)
      :rect
      [:button {:class base :data-shape idx :type "button"
                :style (assoc style
                              :background (str "#" (valid-hex (:slides/fill resolved) "EAF0F8"))
                              :border-color (str "#" (valid-hex (:slides/line resolved) "496B9A")))}
       (when selected?
         [:span.resize-handles
          [:span.resize-handle.nw {:data-resize "nw"}]
          [:span.resize-handle.ne {:data-resize "ne"}]
          [:span.resize-handle.sw {:data-resize "sw"}]
          [:span.resize-handle.se {:id "resize-se" :data-resize "se"}]])]
      [:button {:class base :data-shape idx :type "button" :style style}
       (:slides/text resolved "")
       (when selected?
         [:span.resize-handles
          [:span.resize-handle.nw {:data-resize "nw"}]
          [:span.resize-handle.ne {:data-resize "ne"}]
          [:span.resize-handle.sw {:data-resize "sw"}]
          [:span.resize-handle.se {:id "resize-se" :data-resize "se"}]])])))

(defn canvas [db]
  (let [d (:deck db)
        width (positive (:slides/width d) 10)
        height (positive (:slides/height d) 5.625)
        slide (selected-slide db)]
    [:div#canvas.canvas {:style {:aspect-ratio (str width "/" height)}}
     (map-indexed (fn [i shape] (shape-node db i shape)) (:slides/shapes slide))]))

;; ---------------------------------------------------------------------------
;; properties panel
;; ---------------------------------------------------------------------------

(defn property-input [label id field value opts]
  (let [{:keys [type step]} opts]
    [:label [:span label]
     (ui/text-field {:id id
                     :value (if (nil? value) "" (str value))
                     :data-field field
                     :type (or type "text")
                     :step step})]))

(defn select-options [values selected]
  (cons [:option {:value ""}]
        (for [value values
              :let [s (name value)]]
          [:option {:value s :selected (= value selected)} s])))

(defn shape-properties [db]
  (let [d (:deck db)
        raw-shape (selected-shape db)
        shape (design/resolve-shape d raw-shape)
        dd (design/deck-design d)]
    [:div
     [:div.panel-title "Shape"]
     (property-input "ID" "shape-id" "shape.id" (:slides/id raw-shape (:slides/id shape "")) {})
     [:label [:span "Component"]
      [:select#shape-component {:data-field "shape.component"}
       (select-options (keys (:slides/components dd)) (:slides/component raw-shape))]]
     [:label [:span "Text style"]
      [:select#shape-text-style {:data-field "shape.text-style"}
       (select-options (keys (:slides/text-styles dd)) (:slides/text-style raw-shape))]]
     [:label [:span "Kind"]
      [:select#shape-kind {:data-field "shape.kind"}
       [:option {:value "text" :selected (= :text (:slides/shape shape))} "Text"]
       [:option {:value "rect" :selected (= :rect (:slides/shape shape))} "Rect"]]]
     (when (not= :rect (:slides/shape shape))
       [:label [:span "Text"]
        (ui/text-area {:id "shape-text"
                       :data-field "shape.text"
                       :rows 4
                       :value (:slides/text shape "")})])
     [:div.grid2
      (property-input "X" "shape-x" "shape.x" (:slides/x shape 0) {:type "number" :step "0.1"})
      (property-input "Y" "shape-y" "shape.y" (:slides/y shape 0) {:type "number" :step "0.1"})
      (property-input "W" "shape-w" "shape.w" (:slides/w shape 1) {:type "number" :step "0.1"})
      (property-input "H" "shape-h" "shape.h" (:slides/h shape 1) {:type "number" :step "0.1"})]
     (if (= :rect (:slides/shape shape))
       [:div.grid2
        (property-input "Fill" "shape-fill" "shape.fill" (:slides/fill shape "EAF0F8") {})
        (property-input "Line" "shape-line" "shape.line" (:slides/line shape "496B9A") {})]
       [:div.grid2
        (property-input "Font" "shape-font-size" "shape.font-size" (:slides/font-size shape 24) {:type "number" :step "1"})
        (property-input "Color" "shape-color" "shape.color" (:slides/color shape "17202A") {})])
     [:div.inspector-actions
      (chrome-button "duplicate-shape" "Duplicate" :duplicate-shape)
      (chrome-button "delete-shape" "Delete" :delete-shape {:class "danger"})]]))

(defn slide-properties [db]
  (let [slide (selected-slide db)]
    [:div
     [:div.panel-title "Slide"]
     (property-input "ID" "slide-id" "slide.id" (:slides/id slide "") {})
     (property-input "Title" "slide-title" "slide.title" (:slides/title slide "") {})
     (chrome-button "delete-slide" "Delete Slide" :delete-slide {:class "danger"})]))

(defn selection-properties [db]
  (let [n (count (selected-shapes-set db))]
    [:div
     [:div.panel-title "Selection"]
     [:div.selection-count
      [:strong (str n)]
      [:span "shapes selected"]]
     [:div.panel-title "Align"]
     [:div.align-grid
      (chrome-button "align-left" "Left" :align-left)
      (chrome-button "align-center" "Center" :align-center)
      (chrome-button "align-right" "Right" :align-right)
      (chrome-button "align-top" "Top" :align-top)
      (chrome-button "align-middle" "Middle" :align-middle)
      (chrome-button "align-bottom" "Bottom" :align-bottom)]]))

(defn properties-panel [db]
  (cond
    (> (count (selected-shapes-set db)) 1)
    (selection-properties db)

    (some? (:selected-shape db))
    (shape-properties db)

    :else
    (slide-properties db)))

;; ---------------------------------------------------------------------------
;; workspace / toolbar / rail / root
;; ---------------------------------------------------------------------------

(defn mode-tabs [mode]
  [:div.mode-tabs
   (chrome-button "mode-visual" "Visual" :mode-visual
                  {:class (when (= :visual mode) "active")})
   (chrome-button "mode-edn" "EDN" :mode-edn
                  {:class (when (= :edn mode) "active")})])

(defn insert-bar []
  [:div.insert-bar
   (chrome-button "add-text" "Text" :add-text)
   (chrome-button "add-rect" "Rect" :add-rect)
   (chrome-button "add-title" "Title" :add-title)
   (chrome-button "add-panel" "Panel" :add-panel)])

(defn zoom-controls [zoom]
  [:div.zoom-controls
   (chrome-button "zoom-out" "-" :zoom-out)
   (chrome-button "zoom-reset" (str (long (* 100 (positive zoom 1))) "%") :zoom-reset)
   (chrome-button "zoom-in" "+" :zoom-in)])

(defn workspace [db]
  (let [mode (:mode db)
        deck (:deck db)
        slide (selected-slide db)
        zoom (positive (:zoom db) 1)]
    [:section.workspace
     [:div.workspace-head
      [:div
       [:h2 {:class "hig-title3"} (:slides/title deck "Untitled deck")]
       [:p {:class "hig-footnote"} (str (:slides/title slide (:slides/id slide "Slide")))]]
      [:div.workspace-tools
       (mode-tabs mode)
       (insert-bar)
       (zoom-controls zoom)]]
     [:div#visual-pane.stage {:hidden (not= :visual mode)}
      [:div.canvas-shell {:style {:transform (str "scale(" zoom ")")}}
       (canvas db)]]
     [:div#edn-pane {:hidden (not= :edn mode)}
      ;; Uncontrolled textarea keyed by deck content: React won't revert user
      ;; edits (no :on-change / no :value), and it remounts with the current
      ;; deck's pr-str whenever the deck changes — mirroring the legacy
      ;; imperative `set! (.-value …)`. Apply EDN reads the live DOM value via
      ;; the enhancer. kotoba-ui/text-area passes :key/:default-value/
      ;; :spellcheck through to the <textarea> untouched and leaves an
      ;; absent :value absent (the control stays uncontrolled).
      (ui/text-area {:id "deck-edn"
                     :spellcheck "false"
                     :key (hash deck)
                     :rows 24
                     :default-value (pr-str deck)})
      [:div.edn-actions
       (chrome-button "apply-edn" "Apply EDN" :apply-edn {:class "primary"})]]
     [:div#error (:error db)]]))

(defn rail [db]
  (let [ss (vec (:slides/slides (:deck db)))
        n (count ss)
        slide (selected-slide db)
        shape-count (count (:slides/shapes slide))]
    ;; body as a list (seq) so both render hosts treat it as children, not as
    ;; a single (invalid) vector-of-vectors element under reagent.
    (appkit/panel
     (list
      [:div.aside-title "Slides"]
      [:div.rail-actions
       (chrome-button "add-slide" "Add" :add-slide)
       (chrome-button "duplicate-slide" "Copy" :duplicate-slide)]
      (slide-list db)
      [:div#status.status
       [:strong (str n)]
       [:span "slides"]
       [:strong (str shape-count)]
       [:span "shapes"]])
     {:class "rail"})))

(defn toolbar [db]
  (let [deck (:deck db)
        slide-count (count (:slides/slides deck))]
    (ui/toolbar
     [[:div.brand
       [:h1 {:class "hig-headline"} "kotoba-lang/slides"]
       [:p {:class "hig-caption1"} (:slides/title deck "Untitled deck")]]
      [:div.deck-meta
       (ui/badge (:slides/id deck "deck"))
       (ui/badge (str slide-count " slides"))
       (ui/badge "causal PPTX")]
      [:div.toolbar-actions
       (chrome-button "new-deck" "New" :new-deck)
       (chrome-button "undo" "Undo" :undo {:disabled (empty? (:undo-stack db))})
       (chrome-button "redo" "Redo" :redo {:disabled (empty? (:redo-stack db))})
       [:label.file-label "Open EDN"
        [:input#edn-file {:type "file" :accept ".edn,text/plain"}]]
       [:label.file-label "Open PPTX"
        [:input#pptx-file {:type "file" :accept ".pptx,application/vnd.openxmlformats-officedocument.presentationml.presentation"}]]
       (chrome-button "download-edn" "EDN" :download-edn)
       (chrome-button "download-svgraph" "SVGraph" :download-svgraph)
       (chrome-button "download-pptx" "PPTX + causal" :download-pptx {:class "primary"})
       [:a.github {:href "https://github.com/kotoba-lang/slides"} "GitHub"]]]
     {:class "editor-toolbar"})))

(defn root [db]
  (ui/app-shell
   {:nav (toolbar db)
    :sidebar (rail db)
    :class "editor"}
   [:div.editor-main
    (workspace db)
    (appkit/panel (properties-panel db) {:id "properties" :class "props"})]))
