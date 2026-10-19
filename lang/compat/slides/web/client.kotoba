(ns slides.web.client
  "Browser hydration adapter — the live side of the shitsuke dual-render
  contract (reference implementation).

  docs/index.html ships the SSR render of slides.web.views inside
  `#app[data-kotoba-render=ssr]`. This entry point mounts the SAME .cljc
  views over real reagent + re-frame, seeded from the SAME initial db the
  SSR used, then flips the marker to `data-kotoba-render=live`. Hydration
  semantics are replace-render: reagent's React root re-renders the app
  container; because views, db, and CSS are identical, the swap is visually
  a no-op (byte-perfect React hydrate is not the contract).

  Interaction wiring, per the views' data-attribute contract:
    - ONE delegated click listener drives :data-act / :data-slide /
      :data-shape → re-frame dispatch (slides.web.dispatch is the pure
      mapping; mirrors kami-mangaka-reader's wire-lang-switch! pattern).
    - Text controls carrying :data-field get a real React :on-change via
      slides.web.enhance (reagent's async-safe controlled-input path needs
      the prop — a DOM-delegated listener cannot engage it).
    - Pointer drag moves/resizes canvas shapes (:slides/set-shape-position /
      :slides/set-shape-frame, with one :slides/mark-undo per gesture).
    - Arrow keys nudge, Delete removes, Cmd/Ctrl+Z / +Shift+Z undo/redo.
    - Host effects the events can't own (per the events ns docstring):
      Apply EDN reads the live #deck-edn value; EDN/SVGraph downloads build
      Blobs; EDN file open imports. PPTX import/export needs the JVM zip
      host (slides.pptx is #?(:clj) there) and reports a clear error.
  No persistence: reload restores the sample deck by design (browser
  storage would be another host adapter, out of scope here)."
  (:require [cljs.reader :as reader]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]
            [shitsuke.re-frame.core :as rf]
            [slides.design :as design]
            [slides.svgraph :as svgraph]
            [slides.web.dispatch :as dispatch]
            [slides.web.enhance :as enhance]
            [slides.web.events :as events]
            [slides.web.sample :as sample]
            [slides.web.views :as views]))

;; ---------------------------------------------------------------------------
;; root component (views + live enhancement)
;; ---------------------------------------------------------------------------

(def ^:private field-handler
  "Per-field :on-change handler (memoized so prop identity is stable across
  re-renders). dispatch-SYNC + r/flush, per re-frame's controlled-input
  guidance: the :value prop must advance in the same tick as the keystroke —
  with async dispatch (or with sync dispatch but rAF-deferred re-render) a
  fast burst races the queue and a stale render clobbers the DOM value
  (reproduced under 5ms-interval typing: \"Textcdefghij\" with plain
  dispatch, \"abefghij\" with dispatch-sync alone). Flushing reagent's render
  queue synchronously closes the race window entirely."
  (memoize
   (fn [field]
     (fn [e]
       (when-let [ev (dispatch/field->event field (.. e -target -value))]
         (rf/dispatch-sync ev)
         (r/flush))))))

(defn root []
  (enhance/enhance (views/root @(rf/subscribe [:slides/db])) field-handler))

;; ---------------------------------------------------------------------------
;; host effects (apply-edn / downloads / file open)
;; ---------------------------------------------------------------------------

(defn- set-error! [msg] (rf/dispatch [:slides/set-error msg]))

(defn- read-deck-edn
  "Parse an EDN deck string; returns the deck map or dispatches an error and
  returns nil."
  [text]
  (try
    (let [deck (reader/read-string text)]
      (if (map? deck)
        deck
        (do (set-error! "EDN must be a deck map") nil)))
    (catch :default err
      (set-error! (str "EDN parse error: " (or (ex-message err) err)))
      nil)))

(defn- apply-edn! []
  (when-let [ta (.getElementById js/document "deck-edn")]
    (when-let [deck (read-deck-edn (.-value ta))]
      (rf/dispatch [:slides/apply-edn deck]))))

(defn- download! [filename text]
  (let [blob (js/Blob. #js [text] #js {:type "application/edn"})
        url (.createObjectURL js/URL blob)
        a (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild (.-body js/document) a)
    (.click a)
    (.remove a)
    (.revokeObjectURL js/URL url)))

(def ^:private pptx-jvm-only
  "PPTX needs the JVM zip host — run `clojure -M:cli` from kotoba-lang/slides. Browser PPTX is a follow-up.")

(defn- import-edn-file! [input]
  (when-let [file (aget (.-files input) 0)]
    (let [fr (js/FileReader.)]
      (set! (.-onload fr)
            (fn [_]
              (when-let [deck (read-deck-edn (.-result fr))]
                (rf/dispatch [:slides/import-deck deck]))))
      (.readAsText fr file)))
  (set! (.-value input) ""))

;; ---------------------------------------------------------------------------
;; delegated listeners (data-act / data-slide / data-shape / file inputs)
;; ---------------------------------------------------------------------------

(defn- parse-idx [el attr]
  (js/parseInt (.getAttribute el attr) 10))

(defn- handle-act! [act]
  (case act
    "apply-edn" (apply-edn!)
    "download-edn" (download! "deck.edn" (pr-str (:deck @rf/app-db)))
    "download-svgraph" (download! "deck.svgraph.edn"
                                  (pr-str (svgraph/presentation (:deck @rf/app-db))))
    "download-pptx" (set-error! pptx-jvm-only)
    (when-let [ev (dispatch/act->event act @rf/app-db)]
      (rf/dispatch ev))))

(defn- on-click [e]
  (let [t (.-target e)]
    (if-let [el (.closest t "[data-act]")]
      (handle-act! (.getAttribute el "data-act"))
      (if-let [el (.closest t "[data-slide]")]
        (rf/dispatch [:slides/select-slide (parse-idx el "data-slide")])
        (when-let [el (.closest t "[data-shape]")]
          (rf/dispatch [(if (.-shiftKey e)
                          :slides/toggle-shape-selection
                          :slides/select-shape)
                        (parse-idx el "data-shape")]))))))

(defn- on-change [e]
  (let [el (.-target e)]
    (case (.-id el)
      "edn-file" (import-edn-file! el)
      "pptx-file" (do (set-error! pptx-jvm-only)
                      (set! (.-value el) ""))
      nil)))

;; ---------------------------------------------------------------------------
;; pointer drag: move + corner resize on the canvas
;; ---------------------------------------------------------------------------

(defonce ^:private drag-state (atom nil))

(def ^:private drag-threshold-px 3)
(def ^:private min-shape-size 0.1)

(defn- shape-frame
  "Resolved {:x :y :w :h} of shape `idx` on the current slide (deck units)."
  [db idx]
  (let [deck (:deck db)
        raw (get-in deck [:slides/slides (events/slide-index db) :slides/shapes idx])]
    (when raw
      (let [s (design/resolve-shape deck raw)]
        {:x (views/numeric (:slides/x s) 0)
         :y (views/numeric (:slides/y s) 0)
         :w (views/positive (:slides/w s) 1)
         :h (views/positive (:slides/h s) 1)}))))

(defn- px->deck
  "[dx-px dy-px] → [dx dy] in deck units, using the live #canvas rect (which
  already includes the CSS zoom scale)."
  [db dx dy]
  (when-let [canvas (.getElementById js/document "canvas")]
    (let [rect (.getBoundingClientRect canvas)
          deck (:deck db)
          dw (views/positive (:slides/width deck) 10)
          dh (views/positive (:slides/height deck) 5.625)]
      (when (pos? (.-width rect))
        [(/ (* dx dw) (.-width rect))
         (/ (* dy dh) (.-height rect))]))))

(defn- on-pointer-down [e]
  (when (zero? (.-button e))
    (let [t (.-target e)
          resize-el (.closest t "[data-resize]")
          shape-el (.closest t "[data-shape]")]
      (when (and shape-el (.closest shape-el "#canvas"))
        (let [idx (parse-idx shape-el "data-shape")]
          (when-not (.-shiftKey e)
            (rf/dispatch-sync [:slides/select-shape idx]))
          (when-let [frame (shape-frame @rf/app-db idx)]
            (reset! drag-state
                    {:idx idx
                     :mode (if resize-el
                             (keyword (.getAttribute resize-el "data-resize"))
                             :move)
                     :x0 (.-clientX e) :y0 (.-clientY e)
                     :frame frame
                     :moved? false})))))))

(defn- resize-frame [{:keys [x y w h]} corner du dv]
  (case corner
    :se {:x x :y y :w (+ w du) :h (+ h dv)}
    :ne {:x x :y (+ y dv) :w (+ w du) :h (- h dv)}
    :sw {:x (+ x du) :y y :w (- w du) :h (+ h dv)}
    :nw {:x (+ x du) :y (+ y dv) :w (- w du) :h (- h dv)}
    nil))

(defn- on-pointer-move [e]
  (when-let [{:keys [idx mode x0 y0 frame moved?]} @drag-state]
    (let [dx (- (.-clientX e) x0)
          dy (- (.-clientY e) y0)]
      (when (or moved? (> (+ (js/Math.abs dx) (js/Math.abs dy)) drag-threshold-px))
        (when-not moved?
          (rf/dispatch [:slides/mark-undo])
          (swap! drag-state assoc :moved? true))
        (.preventDefault e)
        (when-let [[du dv] (px->deck @rf/app-db dx dy)]
          (if (= :move mode)
            (rf/dispatch [:slides/set-shape-position idx
                          (+ (:x frame) du) (+ (:y frame) dv)])
            (when-let [{:keys [x y w h]} (resize-frame frame mode du dv)]
              (rf/dispatch [:slides/set-shape-frame idx x y
                            (max min-shape-size w)
                            (max min-shape-size h)]))))))))

(defn- on-pointer-up [_]
  (reset! drag-state nil))

;; ---------------------------------------------------------------------------
;; keyboard: nudge / delete / undo / redo
;; ---------------------------------------------------------------------------

(defn- editing-target? [t]
  (let [tag (some-> (.-tagName t) (.toLowerCase))]
    (or (contains? #{"input" "textarea" "select"} tag)
        (.-isContentEditable t))))

(defn- selection? [db]
  (or (seq (:selected-shapes db)) (some? (:selected-shape db))))

(defn- on-key-down [e]
  (let [k (.-key e)
        undo-combo? (and (or (.-metaKey e) (.-ctrlKey e))
                         (= "z" (.toLowerCase k)))]
    (cond
      (editing-target? (.-target e))
      nil ; native field editing (incl. the browser's own text undo) wins

      undo-combo?
      (do (.preventDefault e)
          (rf/dispatch [(if (.-shiftKey e) :slides/redo :slides/undo)]))

      (and (selection? @rf/app-db)
           (contains? #{"ArrowLeft" "ArrowRight" "ArrowUp" "ArrowDown"
                        "Delete" "Backspace"} k))
      (let [step (if (.-shiftKey e) 0.5 0.1)]
        (.preventDefault e)
        (case k
          "ArrowLeft" (rf/dispatch [:slides/nudge-shape (- step) 0])
          "ArrowRight" (rf/dispatch [:slides/nudge-shape step 0])
          "ArrowUp" (rf/dispatch [:slides/nudge-shape 0 (- step)])
          "ArrowDown" (rf/dispatch [:slides/nudge-shape 0 step])
          ("Delete" "Backspace") (rf/dispatch [:slides/delete-shape])))

      :else nil)))

;; ---------------------------------------------------------------------------
;; mount
;; ---------------------------------------------------------------------------

(defonce ^:private react-root (atom nil))

(defn- wire-listeners! [el]
  (.addEventListener el "click" on-click)
  (.addEventListener el "change" on-change)
  (.addEventListener el "pointerdown" on-pointer-down)
  (.addEventListener js/document "pointermove" on-pointer-move)
  (.addEventListener js/document "pointerup" on-pointer-up)
  (.addEventListener js/document "keydown" on-key-down))

(defn ^:export mount []
  (when-let [el (.getElementById js/document "app")]
    (events/register!)
    ;; same initial db the SSR page was rendered from → no visual flash
    (rf/dispatch-sync [:slides/init sample/sample-deck])
    (wire-listeners! el)
    (let [r (or @react-root (reset! react-root (rdc/create-root el)))]
      (rdc/render r [root]))
    (.setAttribute el "data-kotoba-render" "live")))

(defn ^:export init [] (mount))
