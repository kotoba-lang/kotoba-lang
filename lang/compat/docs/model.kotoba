(ns docs.model
  (:require [clojure.string :as str]))

(def block-kinds
  #{:heading :paragraph :quote :code :list :table :image
    :table-ref :file-ref :deck-ref})

(defn document
  ([id] (document id {}))
  ([id attrs]
   (merge {:docs/id id
           :docs/type :document
           :docs/title id
           :docs/blocks []
           :docs/comments []
           :docs/suggestions []}
          attrs)))

(defn block [kind id attrs]
  (merge {:docs/id id :docs/kind kind} attrs))

(defn heading-level
  "A block's heading level as a number between 1 and 6.

  Every writer clamps — Markdown, Word and HTML all have six — and every one
  of them did it with `(max 1 (min 6 (or level 1)))`, which throws when the
  level is not a number. A hand-edited payload can carry `\"two\"`, nothing
  validates the type, and the result was a 500 from the exporter rather than
  a heading.

  Here rather than three times over: it is a fact about a heading, not about
  a file format."
  [block]
  (let [level (:docs/level block)]
    (if (number? level) (max 1 (min 6 (long level))) 1)))

(defn heading [id level text]
  (block :heading id {:docs/level level :docs/text text}))

(defn paragraph [id text]
  (block :paragraph id {:docs/text text}))

(defn list-block [id items attrs]
  (block :list id (merge {:docs/items (vec items)} attrs)))

(defn table [id rows attrs]
  (block :table id (merge {:docs/rows (vec rows)} attrs)))

(defn add-text-style [b from to style]
  (update b :docs/text-runs (fnil conj [])
          {:docs/from from
           :docs/to to
           :docs/style style}))

(def link-schemes
  "The URL schemes a link in a document may use.

  An allowlist and not a denylist. A document is a thing people are sent, it
  is rendered as HTML by the print page and by every preview, and a
  `javascript:` href in one of those is script running in the reader's
  session — so the question a writer has to answer is not \"is this one of
  the bad ones\" but \"is this one of the three I know are a place\".

  Here rather than in each writer because all of them have to agree: a link
  HTML refuses and Word follows is worse than either answer on its own."
  #{"http" "https" "mailto"})

(defn link
  "A run's link, when it is one that may be followed, or nil.

  Nil for anything else — no scheme, an unknown scheme, a non-string —
  rather than a cleaned-up version of it. There is no safe rewriting of
  `javascript:alert(1)` into a place, and a writer that emitted it without
  a scheme would produce a relative link to a file named after the script."
  [style]
  (let [url (str/trim (str (:link style)))
        scheme (second (re-find #"^([A-Za-z][A-Za-z0-9+.-]*):" url))]
    (when (and (seq url) scheme (contains? link-schemes (str/lower-case scheme)))
      url)))

(defn text-spans
  "A block's text cut into pieces, each with the style that covers it.

  `[{:docs/text \"plain \"} {:docs/text \"bold\" :docs/style {:bold true}}]`.
  Every writer needs the same three answers before it can mark anything up —
  which runs are usable, in what order, and what to do when two overlap —
  and each of the three had its own copy: `docs.html` filtered and sorted
  and gave up on overlap, `docs.markdown` did the same in reverse so its
  inserted characters would not move later offsets, and `docs.docx` did not
  try at all and dropped every run. Three copies of a rule is three chances
  to answer differently, which is what happened.

  The rules, in one place:

  - A run needs numeric `:docs/from` and `:docs/to` inside the text with
    `from` before `to`. Anything else is not a range and is ignored.
  - Overlapping runs mark up nothing. `<strong>a<em>b</strong>c</em>` is not
    HTML and `**a *b** c*` is not Markdown; producing either is worse than
    producing the text, and a writer that resolved overlap its own way would
    disagree with its siblings about what the document says.
  - The gaps between runs are spans too, with no style, so a caller can
    write the whole text by walking the result and never has to index back
    into the original."
  [b]
  (let [text (str (:docs/text b))
        n (count text)
        usable (->> (:docs/text-runs b)
                    (filter #(and (number? (:docs/from %)) (number? (:docs/to %))
                                  (<= 0 (:docs/from %)) (<= (:docs/to %) n)
                                  (< (:docs/from %) (:docs/to %))))
                    (sort-by :docs/from))
        overlapping? (some (fn [[a b]] (> (:docs/to a) (:docs/from b)))
                           (partition 2 1 usable))]
    (if (or overlapping? (empty? usable))
      (if (seq text) [{:docs/text text}] [])
      (let [[spans at]
            (reduce (fn [[spans at] {:keys [docs/from docs/to docs/style]}]
                      [(cond-> spans
                         (< at from) (conj {:docs/text (subs text at from)})
                         true (conj {:docs/text (subs text from to)
                                     :docs/style style}))
                       to])
                    [[] 0]
                    usable)]
        (cond-> spans
          (< at n) (conj {:docs/text (subs text at n)}))))))

(def image-media-types
  "The picture formats a document may carry, and what they are called.

  An allowlist, like the link schemes: the bytes end up in an `<img src>`
  and in a Word media part, and a media type nobody checked is a media type
  a browser will sniff. SVG is not among them — it is a document that can
  carry script, and an `<img>` is where a reader would least expect one."
  {"image/png" "png" "image/jpeg" "jpg" "image/gif" "gif" "image/webp" "webp"})

(defn image
  "A picture in the document.

  `image-data` is base64, the same way `slides.model/image` carries one:
  portable across hosts and EDN- and JSON-safe, where a byte array is
  neither. The document travels whole — into a `.docx`, into an EDN export
  — with no second place the bytes live and nothing to re-resolve later.

  `:docs/alt` is not decoration. A picture with no alternative text is a
  hole in the document for anyone reading it aloud, so `unexpressed` and
  the validator both have something to say about one that is missing."
  ([id image-data] (image id image-data {}))
  ([id image-data attrs]
   (merge {:docs/id id
           :docs/kind :image
           :docs/image-data image-data
           :docs/media-type "image/png"
           :docs/alt ""}
          attrs)))

(defn image-data
  "A block's picture as `[media-type base64]`, or nil when it is not one to
  draw — no bytes, or a media type this does not carry."
  [b]
  (let [media (str (:docs/media-type b))
        data (str/trim (str (:docs/image-data b)))]
    (when (and (seq data) (contains? image-media-types media))
      [media data])))

(defn ref-block [kind id target]
  (block kind id {:docs/target target}))

(defn add-block [doc b]
  (update doc :docs/blocks conj b))

(defn add-comment [doc comment]
  (update doc :docs/comments conj comment))

(defn add-suggestion [doc suggestion]
  (update doc :docs/suggestions conj suggestion))

(defn block-by-id [doc id]
  (first (filter #(= id (:docs/id %)) (:docs/blocks doc))))

(defn outline [doc]
  (->> (:docs/blocks doc)
       (filter #(= :heading (:docs/kind %)))
       (mapv #(select-keys % [:docs/id :docs/level :docs/text]))))

(defn seed-document []
  (-> (document "gftd-docs" {:docs/title "GFTD Docs"})
      (add-block (heading "intro" 1 "GFTD Docs"))
      (add-block (paragraph "body" "Documents are EDN blocks with explicit links to sheets, drive files, and slide decks."))
      (add-block (ref-block :deck-ref "slides-link" "slides:intro-deck"))))
