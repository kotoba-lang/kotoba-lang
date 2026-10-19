(ns slides.pptx.import
  "PPTX PresentationML/DrawingML to slides EDN adapter."
  (:require [clojure.string :as str]
            [presentationml.parse :as pml-parse]))

(defn- file-title [file-name]
  (some-> file-name (str/replace #"\.pptx$" "")))

(defn- slides-color-key [[k v]]
  [(keyword "office-style.color" (name k)) v])

(defn- slides-font-key [[k v]]
  [(keyword "office-style.font" (name k)) v])

(defn- theme->slides [theme]
  (when (seq theme)
    (cond-> {}
      (:presentationml/colors theme)
      (assoc :slides/colors (into {} (map slides-color-key) (:presentationml/colors theme)))

      (:presentationml/fonts theme)
      (assoc :slides/fonts (into {} (map slides-font-key) (:presentationml/fonts theme)))

      (:presentationml/source theme)
      (assoc :slides/source (:presentationml/source theme)))))

(defn- shape-kind [kind]
  (case kind
    :rect :rect
    :pic :image
    :table :table
    :chart :chart
    :connector :connector
    :text))

(defn- shape->slides [shape]
  (cond-> {:slides/id (:drawingml/id shape)
           :slides/shape (shape-kind (:drawingml/kind shape))
           :slides/x (:drawingml/x shape)
           :slides/y (:drawingml/y shape)
           :slides/w (:drawingml/w shape)
           :slides/h (:drawingml/h shape)}
    (:drawingml/text shape) (assoc :slides/text (:drawingml/text shape))
    (:drawingml/paragraphs shape) (assoc :slides/paragraphs (:drawingml/paragraphs shape))
    (:drawingml/rows shape) (assoc :slides/rows (:drawingml/rows shape))
    (:drawingml/cells shape) (assoc :slides/cells (:drawingml/cells shape))
    (:drawingml/table-style-flags shape) (assoc :slides/table-style-flags (:drawingml/table-style-flags shape))
    (:drawingml/column-widths shape) (assoc :slides/column-widths (:drawingml/column-widths shape))
    (:drawingml/row-heights shape) (assoc :slides/row-heights (:drawingml/row-heights shape))
    (:drawingml/geometry shape) (assoc :slides/geometry (:drawingml/geometry shape))
    (:drawingml/rotation shape) (assoc :slides/rotation (:drawingml/rotation shape))
    (:drawingml/flip-h shape) (assoc :slides/flip-h (:drawingml/flip-h shape))
    (:drawingml/flip-v shape) (assoc :slides/flip-v (:drawingml/flip-v shape))
    (:drawingml/font-size shape) (assoc :slides/font-size (:drawingml/font-size shape))
    (:drawingml/color shape) (assoc :slides/color (:drawingml/color shape))
    (:drawingml/fill shape) (assoc :slides/fill (:drawingml/fill shape))
    (:drawingml/line shape) (assoc :slides/line (:drawingml/line shape))
    (:drawingml/line-dash shape) (assoc :slides/line-dash (:drawingml/line-dash shape))
    (:drawingml/line-width shape) (assoc :slides/line-width (:drawingml/line-width shape))
    (:drawingml/line-cap shape) (assoc :slides/line-cap (:drawingml/line-cap shape))
    (:drawingml/line-join shape) (assoc :slides/line-join (:drawingml/line-join shape))
    (:drawingml/bold shape) (assoc :slides/bold (:drawingml/bold shape))
    (:drawingml/italic shape) (assoc :slides/italic (:drawingml/italic shape))
    (:drawingml/underline shape) (assoc :slides/underline (:drawingml/underline shape))
    (:drawingml/underline-style shape) (assoc :slides/underline-style (:drawingml/underline-style shape))
    (:drawingml/underline-color shape) (assoc :slides/underline-color (:drawingml/underline-color shape))
    (:drawingml/strikethrough shape) (assoc :slides/strikethrough (:drawingml/strikethrough shape))
    (:drawingml/baseline shape) (assoc :slides/baseline (:drawingml/baseline shape))
    (:drawingml/char-spacing shape) (assoc :slides/char-spacing (:drawingml/char-spacing shape))
    (:drawingml/highlight shape) (assoc :slides/highlight (:drawingml/highlight shape))
    (:drawingml/hyperlink shape) (assoc :slides/hyperlink (:drawingml/hyperlink shape))
    (:drawingml/hyperlink-slide-part shape) (assoc :slides/hyperlink-slide-part (:drawingml/hyperlink-slide-part shape))
    (:drawingml/hyperlink-action shape) (assoc :slides/hyperlink-action (:drawingml/hyperlink-action shape))
    (:drawingml/fill-image-rel-id shape) (assoc :slides/fill-image-rel-id (:drawingml/fill-image-rel-id shape))
    (:drawingml/fill-image-part shape) (assoc :slides/fill-image-part (:drawingml/fill-image-part shape))
    (:drawingml/adjustments shape) (assoc :slides/adjustments (:drawingml/adjustments shape))
    (:drawingml/custom-geometry shape) (assoc :slides/custom-geometry (:drawingml/custom-geometry shape))
    (:drawingml/shadow shape) (assoc :slides/shadow (:drawingml/shadow shape))
    (:drawingml/glow shape) (assoc :slides/glow (:drawingml/glow shape))
    (:drawingml/reflection shape) (assoc :slides/reflection (:drawingml/reflection shape))
    (:drawingml/body-props shape) (assoc :slides/body-props (:drawingml/body-props shape))
    (:drawingml/gradient shape) (assoc :slides/gradient (:drawingml/gradient shape))
    (:drawingml/image-rel-id shape) (assoc :slides/image-rel-id (:drawingml/image-rel-id shape))
    (:drawingml/image-part shape) (assoc :slides/image-part (:drawingml/image-part shape))
    (:drawingml/video-rel-id shape) (assoc :slides/video-rel-id (:drawingml/video-rel-id shape))
    (:drawingml/video-part shape) (assoc :slides/video-part (:drawingml/video-part shape))
    (:drawingml/audio-rel-id shape) (assoc :slides/audio-rel-id (:drawingml/audio-rel-id shape))
    (:drawingml/audio-part shape) (assoc :slides/audio-part (:drawingml/audio-part shape))
    (:drawingml/crop shape) (assoc :slides/crop (:drawingml/crop shape))
    (:drawingml/recolor shape) (assoc :slides/recolor (:drawingml/recolor shape))
    (:drawingml/connections shape) (assoc :slides/connections (:drawingml/connections shape))
    (:drawingml/hidden shape) (assoc :slides/hidden (:drawingml/hidden shape))
    (:drawingml/locks shape) (assoc :slides/locks (:drawingml/locks shape))
    (:drawingml/source-kind shape) (assoc :slides/source-kind (:drawingml/source-kind shape))
    (:drawingml/group shape) (assoc :slides/group (:drawingml/group shape))
    (:drawingml/placeholder shape) (assoc :slides/placeholder (:drawingml/placeholder shape))
    (:drawingml/chart-rel-id shape) (assoc :slides/chart-rel-id (:drawingml/chart-rel-id shape))
    (:drawingml/chart-part shape) (assoc :slides/chart-part (:drawingml/chart-part shape))
    (:drawingml/workbook-part shape) (assoc :slides/workbook-part (:drawingml/workbook-part shape))
    (:ooxml/source shape) (assoc :ooxml/source (:ooxml/source shape))))

(defn- shape-inventory
  "The [kind index] locator of every shape the slide's source XML actually
  held at import time, regardless of whether it survives later EDN edits.
  update-pptx-bytes diffs this against the deck's current shapes to detect
  deletions (a shape absent from the deck but present here) safely, without
  requiring editors to leave tombstones behind."
  [shapes]
  (into #{}
        (keep (fn [shape]
                (when-let [source (:ooxml/source shape)]
                  [(:ooxml/kind source) (:ooxml/index source)])))
        shapes))

(defn- slide->slides [slide]
  (let [shapes (mapv shape->slides (:presentationml/shapes slide))]
    (cond-> {:slides/id (:presentationml/id slide)
             :slides/title (:presentationml/title slide)
             :slides/source (:presentationml/source slide)
             :slides/shapes shapes
             :slides/shape-inventory (shape-inventory shapes)}
      (:presentationml/notes slide) (assoc :slides/notes (:presentationml/notes slide))
      (:presentationml/master-ref slide) (assoc :slides/master-ref (:presentationml/master-ref slide))
      (:presentationml/layout-ref slide) (assoc :slides/layout-ref (:presentationml/layout-ref slide))
      (:presentationml/transition slide) (assoc :slides/transition (:presentationml/transition slide))
      (:presentationml/comments slide) (assoc :slides/comments (:presentationml/comments slide))
      (:presentationml/background slide) (assoc :slides/slide-background (:presentationml/background slide)))))

(defn- master->slides [master]
  (cond-> {:slides/id (:presentationml/id master)}
    (:presentationml/background master) (assoc :slides/background (:presentationml/background master))))

(defn- layout->slides [layout]
  (cond-> {:slides/id (:presentationml/id layout)}
    (:presentationml/layout-type layout) (assoc :slides/layout-type (:presentationml/layout-type layout))))

(def ^:private doc-properties-keys
  "presentationml.parse/doc-properties' fields carry the SAME names on the
  :slides side, just under the :slides/ namespace -- a plain rekey, no
  transformation."
  [:author :subject :keywords :category :last-modified-by :created :modified :company :manager])

(defn- doc-properties->slides [parsed]
  (into {}
        (keep (fn [k]
                (when-let [v (get parsed (keyword "presentationml" (name k)))]
                  [(keyword "slides" (name k)) v])))
        doc-properties-keys))

(defn deck-from-entries
  ([entries file-name] (deck-from-entries entries file-name {}))
  ([entries file-name opts]
   (let [parsed (pml-parse/deck entries opts)
         title (:presentationml/title parsed (or (file-title file-name) "Imported deck"))
         theme (theme->slides (:presentationml/theme parsed))
         masters (mapv master->slides (:presentationml/masters parsed))
         layouts (mapv layout->slides (:presentationml/layouts parsed))]
     (merge {:slides/id (:presentationml/id parsed "imported-pptx")
             :slides/title title
             :slides/width (:presentationml/width parsed)
             :slides/height (:presentationml/height parsed)
             :slides/import {:slides/source file-name
                             :slides/format :pptx
                             :slides/text-extraction :drawingml-xml}
             :slides/slides (mapv slide->slides (:presentationml/slides parsed))}
            (when (seq theme) {:slides/theme theme})
            (when (seq masters) {:slides/masters masters})
            (when (seq layouts) {:slides/layouts layouts})
            (when (seq (:presentationml/sections parsed)) {:slides/sections (:presentationml/sections parsed)})
            (when (:presentationml/handout-master? parsed) {:slides/handout-master? true})
            (when (seq (:presentationml/custom-xml-parts parsed)) {:slides/custom-xml-parts (:presentationml/custom-xml-parts parsed)})
            (when (seq (:presentationml/embedded-fonts parsed)) {:slides/embedded-fonts (:presentationml/embedded-fonts parsed)})
            (doc-properties->slides parsed)))))

(defn useful-deck? [deck]
  (boolean (seq (:slides/slides deck))))

(defn reconcile-decks [sidecar actual]
  (cond
    (and sidecar (useful-deck? actual))
    (-> (merge sidecar actual)
        (assoc :slides/id (:slides/id sidecar (:slides/id actual))
               :slides/title (:slides/title sidecar (:slides/title actual)))
        (update :slides/import merge {:slides/text-extraction :reconciled-pptx}))

    sidecar
    (assoc-in sidecar [:slides/import :slides/text-extraction] :causal-edn)

    :else actual))
