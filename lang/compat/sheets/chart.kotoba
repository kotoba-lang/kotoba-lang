(ns sheets.chart
  "A chart, drawn.

  `sheets.model` has had `add-chart` from the start and nothing could draw
  one or write one — `sheets.xlsx/unexpressed` reported charts as dropped and
  that was the whole of their existence. A chart you cannot see until you
  export it is a poor deal, so this draws it.

  ## SVG, and here rather than in an application

  Portable `.cljc` producing a string, so the same chart can be tested
  without a browser, rendered server-side, and put in a page by anything
  that can put a string in a page. An application drawing its own would be
  the second implementation of an axis.

  ## What a chart is over

  A range of cells, and this asks `sheets.formula` for what they come to
  rather than reading `:sheets/value` — a chart over a column of `=SUM(…)`
  should plot the totals, and reading the stored value would plot nothing at
  all, since a formula cell has none.

  ## What it refuses to draw

  A range with no numbers in it. An empty plot area with axes is a picture
  of a chart, and it reads as *there is no data here* rather than as *this
  is broken*, which is the wrong answer when the range is simply wrong."
  (:require [clojure.string :as str]
            [sheets.formula :as formula]
            [sheets.model :as model]))

(def chart-kinds
  "What can be drawn. `:bar` is the default because a chart with no kind is
  almost always a comparison of magnitudes."
  #{:bar :line :pie})

(defn parse-range
  "`A1:B9` → `[[r1 c1] [r2 c2]]`, or nil.

  Normalised so that the first corner is the top-left whichever way round it
  was written — `B9:A1` is the same range as `A1:B9` and a chart that drew
  nothing for one of them would be answering a question about typing."
  [range-text]
  (let [[from to] (str/split (str range-text) #":" 2)
        a (formula/parse-ref from)
        b (formula/parse-ref (or to from))]
    (when (and a b)
      [[(min (first a) (first b)) (min (second a) (second b))]
       [(max (first a) (first b)) (max (second a) (second b))]])))

(defn series
  "The numbers a chart is over, with their labels.

  `{:labels [..] :values [..]}`. The first column of the range is taken as
  labels when it holds no numbers — a range selected by dragging across a
  table almost always includes the row headings, and plotting `四半期` as
  zero is worse than using it to name the bar."
  [tab chart]
  (when-let [[[r1 c1] [r2 c2]] (parse-range (:sheets/data-range chart))]
    (let [at (fn [r c] (formula/value-at tab r c))
          number (fn [x] (let [n (formula/as-number x)] (when n n)))
          column (fn [c] (mapv #(at % c) (range r1 (inc r2))))
          first-col (column c1)
          labels? (and (> c2 c1) (not-any? number first-col))
          value-col (if labels? (inc c1) c1)
          values (column value-col)]
      {:labels (if labels? first-col (mapv str (range 1 (inc (count values)))))
       :values (mapv number values)})))

;; ── drawing ─────────────────────────────────────────────────────────────────

(def ^:private palette
  ["#1f6feb" "#e3651d" "#2da44e" "#8250df" "#bf3989" "#9a6700"])

(defn- esc [s]
  (-> (str s) (str/replace "&" "&amp;") (str/replace "<" "&lt;")
      (str/replace ">" "&gt;") (str/replace "\"" "&quot;")))

(defn- round2 [x]
  (/ (double (Math/round (* 100.0 (double x)))) 100.0))

(defn- nice-max
  "An axis top that is a round number at or above the largest value.

  A bar chart whose tallest bar touches the frame reads as clipped. This
  rounds up to one, two or five times a power of ten — the steps a person
  would choose — rather than adding a fixed percentage, which produces axis
  labels like 1,127."
  [largest]
  (if (or (nil? largest) (<= largest 0))
    1
    (let [magnitude (Math/pow 10 (Math/floor (/ (Math/log largest) (Math/log 10))))
          scaled (/ largest magnitude)]
      (* magnitude (cond (<= scaled 1) 1 (<= scaled 2) 2 (<= scaled 5) 5 :else 10)))))

(defn- text-width
  "About how wide `text` is at `size` pixels.

  There is no font here to ask, and the answer only has to be good enough to
  decide whether two labels touch and how much room the widest one needs. A
  CJK character is an em square; the rest average a little over half of one.

  Counted with a regular expression rather than character by character
  because `(int ch)` is a code point on the JVM and 0 in ClojureScript —
  that is the bug that put control characters in every cell reference a
  workbook written from nbb contained, and it is not one to write twice."
  [text size]
  (let [s (str text)
        wide (count (re-seq #"[^ -ÿ]" s))]
    (* size (+ wide (* 0.55 (- (count s) wide))))))

(def ^:private axis-font 10)
(def ^:private tick-font 9)

(defn- plot
  "Where the drawing goes, given the widest thing that has to sit beside it.

  The axis label used to be written at a fixed 24 pixels from the left with
  its end against the axis, which put `4000` two pixels off the left edge of
  the picture: the chart read `000`, and every assertion about it passed,
  because the text was in the string and only outside the frame. The left
  margin is the label's own width now, so it cannot happen for any number."
  [width height top-text]
  (let [left (max 24.0 (+ 8.0 (text-width top-text axis-font)))]
    {:width width :height height :left left :right 24.0
     :pad-top 24.0 :bottom (+ 24.0 tick-font)}))

(defn- plot-width [{:keys [width left right]}] (- width left right))
(defn- plot-height [{:keys [height pad-top bottom]}] (- height pad-top bottom))

(defn- categories
  "The labels along the bottom, at `xs`, as many of them as fit.

  Every k-th when they would touch: twelve months in 320 pixels overlap into
  a grey band, and every other one is a chart you can read. The ones not
  drawn are still in the tooltips — which is where all of them were. The
  chart knew what each bar was called and drew nothing to say so, so a
  quarterly total was four blue rectangles unless you hovered over them."
  [labels xs {:keys [width height bottom] :as box}]
  (let [widest (reduce max 1.0 (map #(text-width % tick-font) labels))
        gap (if (> (count xs) 1)
              (Math/abs (double (- (second xs) (first xs))))
              (plot-width box))
        every-nth (max 1 (long (Math/ceil (/ (+ widest 4.0) (max 1.0 gap)))))
        baseline (+ (- height bottom) tick-font 4.0)]
    (apply str
           (keep-indexed
            (fn [i x]
              (when (zero? (mod i every-nth))
                (let [label (nth labels i (str (inc i)))
                      half (/ (text-width label tick-font) 2.0)
                      ;; Clamped, so an edge label leans in rather than off:
                      ;; the first point of a line chart sits on the axis,
                      ;; and its label is wider than the margin beside it.
                      cx (min (- width 2.0 half) (max (+ 2.0 half) (double x)))]
                  (str "<text x=\"" (round2 cx) "\" y=\"" (round2 baseline)
                       "\" text-anchor=\"middle\" font-size=\"" tick-font
                       "\" fill=\"#57606a\">" (esc label) "</text>"))))
            xs))))

(defn- bars [values labels top {:keys [left height bottom] :as box}]
  (let [n (count values)
        step (/ (plot-width box) (max 1 n))
        bar-width (* step 0.7)
        centres (mapv #(+ left (* % step) (/ step 2.0)) (range n))]
    (str
     (apply str
            (map-indexed
             (fn [i v]
               (let [v (or v 0)
                     h (* (plot-height box) (/ (max 0.0 (double v)) top))
                     x (- (nth centres i) (/ bar-width 2.0))
                     y (- height bottom h)]
                 (str "<rect x=\"" (round2 x) "\" y=\"" (round2 y)
                      "\" width=\"" (round2 bar-width) "\" height=\"" (round2 h)
                      "\" fill=\"" (nth palette (mod i (count palette))) "\">"
                      ;; `format-number`, so the tooltip says 1200 and not
                      ;; 1200.0 — the same text the cell shows.
                      "<title>" (esc (nth labels i (str (inc i)))) ": "
                      (esc (formula/format-number v)) "</title>"
                      "</rect>")))
             values))
     (categories labels centres box))))

(defn- polyline [values labels top {:keys [left height bottom] :as box}]
  (let [n (count values)
        step (if (> n 1) (/ (plot-width box) (dec n)) 0)
        xs (mapv #(+ left (* % step)) (range n))
        point (fn [i v]
                [(nth xs i)
                 (- height bottom (* (plot-height box)
                                     (/ (max 0.0 (double (or v 0))) top)))])]
    (str "<polyline fill=\"none\" stroke=\"" (first palette) "\" stroke-width=\"2\" points=\""
         (str/join " " (map-indexed (fn [i v] (let [[x y] (point i v)]
                                                (str (round2 x) "," (round2 y))))
                                    values))
         "\"/>"
         ;; A dot per reading, and the reading in its tooltip. The line says
         ;; which way things went and nothing said what any of them was.
         (apply str
                (map-indexed
                 (fn [i v]
                   (let [[x y] (point i v)]
                     (str "<circle cx=\"" (round2 x) "\" cy=\"" (round2 y)
                          "\" r=\"2.5\" fill=\"" (first palette) "\">"
                          "<title>" (esc (nth labels i (str (inc i)))) ": "
                          (esc (formula/format-number (or v 0))) "</title></circle>")))
                 values))
         (categories labels xs box))))

(defn- legend
  "The key beside a pie, because a slice has no room to be labelled.

  As many entries as the height allows and then a count of the rest, rather
  than as many as fit and silence about the others: a key that shows four of
  nine categories and does not say so is read as a chart of four."
  [labels width height]
  (let [x (* width 0.62)
        line 14.0
        room (max 1 (long (Math/floor (/ (- height 8.0) line))))
        n (count labels)
        shown (if (> n room) (dec room) n)
        top (max 10.0 (- (/ height 2.0) (* (/ (double (min n room)) 2.0) line)))
        entry (fn [i text swatch]
                (str (when swatch
                       (str "<rect x=\"" (round2 x)
                            "\" y=\"" (round2 (- (+ top (* i line)) 7.0))
                            "\" width=\"8\" height=\"8\" fill=\"" swatch "\"/>"))
                     "<text x=\"" (round2 (+ x (if swatch 12.0 0.0)))
                     "\" y=\"" (round2 (+ top (* i line)))
                     "\" font-size=\"" tick-font "\" fill=\"#57606a\">" (esc text) "</text>"))]
    (apply str
           (concat
            (map-indexed (fn [i label]
                           (entry i label (nth palette (mod i (count palette)))))
                         (take shown labels))
            (when (> n shown)
              [(entry shown (str "ほか " (- n shown) " 件") nil)])))))

(defn- pie [values labels width height]
  (let [total (reduce + 0.0 (map #(max 0.0 (double (or % 0))) values))
        ;; Left of centre, because the key sits on the right. Centred with a
        ;; key beside it, the pie is centred on nothing and the picture leans.
        cx (* width 0.3) cy (/ height 2.0)
        r (* 0.4 (min (* width 0.6) height))]
    (if (zero? total)
      ""
      (str
       (str/join
        (first
         (reduce
          (fn [[out from] [i v]]
            (let [fraction (/ (max 0.0 (double (or v 0))) total)
                  to (+ from fraction)
                  ;; Two decimals of a turn is a third of a degree, which is
                  ;; below what a 320-pixel pie can show.
                  angle (fn [t] (* 2 Math/PI (- t 0.25)))
                  x (fn [t] (round2 (+ cx (* r (Math/cos (angle t))))))
                  y (fn [t] (round2 (+ cy (* r (Math/sin (angle t))))))
                  large (if (> fraction 0.5) 1 0)]
              [(conj out
                     (if (>= fraction 1)
                       ;; A single slice is a circle: an arc from a point
                       ;; back to itself draws nothing.
                       (str "<circle cx=\"" (round2 cx) "\" cy=\"" (round2 cy)
                            "\" r=\"" (round2 r) "\" fill=\"" (first palette) "\">"
                            "<title>" (esc (nth labels i (str (inc i)))) "</title></circle>")
                       (str "<path d=\"M" (round2 cx) "," (round2 cy)
                            " L" (x from) "," (y from)
                            " A" (round2 r) "," (round2 r) " 0 " large ",1 "
                            (x to) "," (y to) " Z\" fill=\""
                            (nth palette (mod i (count palette))) "\">"
                            "<title>" (esc (nth labels i (str (inc i)))) ": "
                            (esc (formula/format-number (or v 0)))
                            "</title></path>")))
               to]))
          [[] 0.0]
          (map-indexed vector values))))
       (legend labels width height)))))

(defn svg
  "One chart as an SVG string, or nil when there is nothing to draw.

  Nil rather than an empty frame: axes around no data read as *there is no
  data here*, which is the wrong answer when the range is simply wrong. A
  caller that gets nil can say which."
  ([tab chart] (svg tab chart {}))
  ([tab chart {:keys [width height] :or {width 320 height 200}}]
   (let [{:keys [labels values]} (series tab chart)
         numbers (keep identity values)]
     (when (seq numbers)
       (let [kind (or (:sheets/chart-type chart) :bar)
             kind (if (contains? chart-kinds kind) kind :bar)
             top (nice-max (apply max numbers))
             top-text (formula/format-number top)
             {:keys [left right pad-top bottom] :as box} (plot width height top-text)
             axis-y (- height bottom)]
         (str "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "
              width " " height "\" role=\"img\" aria-label=\""
              (esc (or (:sheets/title chart) (:sheets/id chart) "グラフ")) "\">"
              (when-not (= :pie kind)
                ;; Two lines, not a grid: the baseline and the axis. A grid
                ;; behind three bars is more chart than data.
                (str "<line x1=\"" (round2 left) "\" y1=\"" (round2 axis-y)
                     "\" x2=\"" (round2 (- width right))
                     "\" y2=\"" (round2 axis-y) "\" stroke=\"#8c959f\"/>"
                     "<line x1=\"" (round2 left) "\" y1=\"" pad-top
                     "\" x2=\"" (round2 left)
                     "\" y2=\"" (round2 axis-y) "\" stroke=\"#8c959f\"/>"
                     ;; The top of the axis and the foot of it. One number
                     ;; tells you the scale; two tell you it starts at zero,
                     ;; which is the thing a bar chart is read as promising.
                     "<text x=\"" (round2 (- left 4.0)) "\" y=\"" (+ pad-top 4.0)
                     "\" text-anchor=\"end\" font-size=\"" axis-font
                     "\" fill=\"#57606a\">" (esc top-text) "</text>"
                     "<text x=\"" (round2 (- left 4.0)) "\" y=\"" (round2 (+ axis-y 3.0))
                     "\" text-anchor=\"end\" font-size=\"" axis-font
                     "\" fill=\"#57606a\">0</text>"))
              (case kind
                :bar (bars values labels top box)
                :line (polyline values labels top box)
                :pie (pie values labels width height))
              "</svg>"))))))

(defn charts-of
  "Every chart in `workbook` that is over `tab-id`, with its SVG.

  `{:id :title :svg}` per chart, and a chart whose range holds no numbers
  appears with `:svg` nil rather than being left out — a chart somebody
  defined and cannot see is a thing to say, not a thing to hide.

  A chart's `:sheets/tab` is matched against the tab's **title**, falling
  back to its id — the same rule `names-of` and `sheets.xlsx` use, and for
  the same reason. The map key a tab is stored under and the name a person
  writes are different things; matching the key resolves only in a workbook
  where they happen to coincide, which is not the workbook an application
  builds. This is the third function to have to choose, and the second to
  get it wrong first."
  [workbook tab-id]
  (let [tab (model/tab-by-id workbook tab-id)
        tab-name (or (:sheets/title tab) tab-id)]
    (->> (:sheets/charts workbook)
         (filter #(contains? #{nil tab-id tab-name} (:sheets/tab %)))
         (mapv (fn [chart]
                 {:id (:sheets/id chart)
                  :title (:sheets/title chart)
                  :svg (when tab (svg tab chart))})))))
