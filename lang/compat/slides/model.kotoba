(ns slides.model
  "Pure EDN model for the GFTD slides/docs/drive/sheets workspace."
  (:require [clojure.string :as str]))

(def item-kinds
  #{:slides/deck
    :slides/doc
    :slides/file
    :slides/folder
    :slides/sheet})

(def link-kinds
  #{:contains
    :uses
    :embeds
    :derived-from
    :publishes
    :mentions})

(defn now-placeholder []
  "host-time")

(defn workspace
  ([id] (workspace id {}))
  ([id attrs]
   (merge {:slides/id id
           :slides/type :workspace
           :slides/items {}
           :slides/links []}
          attrs)))

(defn item
  ([kind id] (item kind id {}))
  ([kind id attrs]
   (merge {:slides/id id
           :slides/kind kind
           :slides/title id}
          attrs)))

(defn deck [id attrs]
  (item :slides/deck id (merge {:slides/slides [] :slides/theme :gftd} attrs)))

(defn slide
  ([id] (slide id {}))
  ([id attrs]
   (merge {:slides/id id
           :slides/title id
           :slides/shapes []}
          attrs)))

(defn text-box
  ([id text] (text-box id text {}))
  ([id text attrs]
   (merge {:slides/id id
           :slides/shape :text
           :slides/text text
           :slides/x 0.8
           :slides/y 0.8
           :slides/w 8.4
           :slides/h 1.0
           :slides/font-size 28}
          attrs)))

(defn rect
  ([id] (rect id {}))
  ([id attrs]
   (merge {:slides/id id
           :slides/shape :rect
           :slides/x 0.8
           :slides/y 2.1
           :slides/w 8.4
           :slides/h 2.0
           :slides/fill "EAF0F8"
           :slides/line "496B9A"}
          attrs)))

(def link-schemes
  "The URL schemes a shape's link may use.

  An allowlist. A deck is rendered as SVG by every preview here, and an
  an anchor with a `javascript:` target around a shape is script in the
  reader's session — so the question is not \"is this one of the bad ones\"
  but \"is this one of the three I know are a place\".

  The twin of `docs.model/link-schemes`, deliberately and not by accident.
  The two libraries share nothing that a URL policy belongs in — `ooxml` is
  packaging and `transit` is a wire — so the rule is written twice rather
  than pushed into a place where it would be surprising to find. If one of
  them ever learns a fourth scheme, the other has to be told."
  #{"http" "https" "mailto"})

(defn shape-link
  "A shape's link, when it is one that may be followed, or nil.

  Nil for anything else — no scheme, an unknown scheme, a non-string —
  rather than a cleaned-up version of it. There is no safe rewriting of
  `javascript:alert(1)` into a place."
  [shape]
  (let [url (str/trim (str (:slides/hyperlink shape)))
        scheme (second (re-find #"^([A-Za-z][A-Za-z0-9+.-]*):" url))]
    (when (and (seq url) scheme (contains? link-schemes (str/lower-case scheme)))
      url)))

(defn image
  "A picture shape. `image-data` is a base64-encoded string (portable across
  JVM/JS and EDN/transit-safe, unlike a raw byte array) -- `slides.pptx`
  decodes it into an embedded media part on export."
  ([id image-data] (image id image-data {}))
  ([id image-data attrs]
   (merge {:slides/id id
           :slides/shape :image
           :slides/x 0.8
           :slides/y 0.8
           :slides/w 4.0
           :slides/h 3.0
           :slides/image-data image-data
           :slides/media-type "image/png"}
          attrs)))

(defn table
  "A table shape. `rows` is a vector of rows, each a vector of cell text.

  `slides.pptx` has written one as a native `<a:tbl>` inside a
  `<p:graphicFrame>` from the start — it round-trips a table imported from
  PowerPoint and regenerates one from scratch — and there was no way to make
  the shape it writes. A deck could only have a table if a .pptx brought one.

  Ragged rows are allowed here and padded by the writer to the widest, which
  is the same choice `docs.model/table` makes: a row somebody has not
  finished typing is input, not an impossibility."
  ([id rows] (table id rows {}))
  ([id rows attrs]
   (merge {:slides/id id
           :slides/shape :table
           :slides/x 0.8
           :slides/y 1.5
           :slides/w 8.4
           :slides/h 2.0
           :slides/rows (mapv #(mapv str %) rows)}
          attrs)))

(defn add-slide [deck slide]
  (update deck :slides/slides conj slide))

(defn add-shape [slide shape]
  (update slide :slides/shapes conj shape))

(defn doc [id attrs]
  (item :slides/doc id (merge {:slides/blocks []} attrs)))

(defn file [id attrs]
  (item :slides/file id (merge {:slides/object-ref nil :slides/media-type "application/octet-stream"} attrs)))

(defn folder [id attrs]
  (item :slides/folder id (merge {:slides/children []} attrs)))

(defn sheet [id attrs]
  (item :slides/sheet id (merge {:slides/tables []} attrs)))

(defn add-item [ws it]
  (assoc-in ws [:slides/items (:slides/id it)] it))

(defn remove-item [ws id]
  (-> ws
      (update :slides/items dissoc id)
      (update :slides/links
              (fn [links]
                (vec (remove #(or (= id (:slides/from %))
                                  (= id (:slides/to %)))
                             links))))))

(defn link
  ([ws from to kind] (link ws from to kind {}))
  ([ws from to kind attrs]
   (update ws :slides/links conj
           (merge {:slides/from from
                   :slides/to to
                   :slides/link-kind kind}
                  attrs))))

(defn items [ws]
  (vals (:slides/items ws)))

(defn item-by-id [ws id]
  (get-in ws [:slides/items id]))

(defn items-by-kind [ws kind]
  (->> (items ws)
       (filter #(= kind (:slides/kind %)))
       (sort-by :slides/id)
       vec))

(defn outgoing [ws id]
  (->> (:slides/links ws)
       (filter #(= id (:slides/from %)))
       vec))

(defn incoming [ws id]
  (->> (:slides/links ws)
       (filter #(= id (:slides/to %)))
       vec))

(defn seed-workspace []
  (-> (workspace "gftd" {:slides/title "GFTD Workspace"})
      (add-item
       (-> (deck "intro-deck" {:slides/title "GFTD intro deck"})
           (add-slide
            (-> (slide "slide-1" {:slides/title "GFTD intro"})
                (add-shape (text-box "title" "GFTD intro"))
                (add-shape (text-box "body" "EDN-native CLJC workspace graph"
                                      {:slides/y 2.0 :slides/font-size 20}))))))
      (add-item (doc "narrative-doc" {:slides/title "Narrative source"}))
      (add-item (folder "shared-drive" {:slides/title "Shared drive"}))
      (add-item (sheet "planning-sheet" {:slides/title "Planning sheet"}))
      (link "intro-deck" "narrative-doc" :uses)
      (link "intro-deck" "planning-sheet" :embeds)
      (link "shared-drive" "intro-deck" :contains)
      (link "shared-drive" "narrative-doc" :contains)
      (link "shared-drive" "planning-sheet" :contains)))
