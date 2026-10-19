(ns docs.html
  "A document as HTML.

  Written for printing. A PDF writer would have to embed a CJK font — the
  reason this Drive has no PDF export — and a browser already has one, so
  the honest way to get a PDF of a Japanese document out of here is a page
  the browser can print. This is that page's body.

  ## Semantic, not styled

  `<h2>`, `<blockquote>`, `<ol>`, `<table>` — the elements the blocks
  actually are. A document rendered as styled `<div>`s prints the same and
  reads as nothing to a screen reader, and the block kinds are already the
  distinctions HTML has names for.

  ## The same three asymmetries Markdown has

  Block ids do not appear, because HTML has nowhere for them that is not an
  invention. Comments and suggestions are not text in the document.
  And a run style HTML has no element for is dropped rather than guessed
  at — `<strong>`, `<em>` and `<code>` are what there is."
  (:require [clojure.string :as str]
            [docs.model :as model]))

(defn esc
  "Text as HTML. Every one of these matters somewhere: `<` and `&` for
  correctness, `\"` because this text also lands in attributes, and `'`
  because an attribute may be single-quoted."
  [s]
  (-> (str s)
      (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;") (str/replace "'" "&#39;")))

(def ^:private run-elements
  "The run styles HTML has an element for, and which one.

  `:code` last for the same reason Markdown puts it last: its content is
  literal, so a run that is both bold and code should have the `<strong>`
  outside."
  [[:bold "strong"] [:italic "em"] [:code "code"]])

(defn- safe-runs
  "A block's text marked up, escaped everywhere.

  Emitted in one pass — each span escaped as it is written — rather than by
  splicing tags into the raw string and escaping afterwards. Splicing first
  means the escape pass eats the tags just inserted; escaping first means
  the offsets no longer point where they did, because `&lt;` is four
  characters where `<` was one. The one pass is the only order that is
  right, which is worth saying because the other two look right.

  Which runs are usable, and what happens when two overlap, is
  `model/text-spans`'s business — this had its own copy of that rule and so
  did the Markdown writer, and the docx writer had none and dropped every
  run."
  [b]
  (apply str
         (for [{:keys [docs/text docs/style]} (model/text-spans b)]
           (let [marked (reduce (fn [s [k el]]
                                  (if (get style k) (str "<" el ">" s "</" el ">") s))
                                (esc text)
                                run-elements)]
             (if-let [url (model/link style)]
               ;; `rel` on every one: a document is a thing people are sent,
               ;; and the page it opens should not be handed a window
               ;; object it can navigate back, nor this page's URL.
               (str "<a href=\"" (esc url) "\" rel=\"noreferrer noopener\">"
                    marked "</a>")
               marked)))))

(defn- block->html [b]
  (let [text (safe-runs b)]
    (case (:docs/kind b)
      :heading (let [level (model/heading-level b)]
                 (str "<h" level ">" text "</h" level ">"))
      :paragraph (str "<p>" text "</p>")
      ;; A data URI, because the bytes are in the block: one document, one
      ;; file, nothing to fetch and nothing that can 404 later. `alt` is
      ;; always written, empty when there is none, because an `<img>` with
      ;; no `alt` attribute at all is read aloud as its file name.
      :image (if-let [[media data] (model/image-data b)]
               (str "<figure><img src=\"data:" (esc media) ";base64," (esc data)
                    "\" alt=\"" (esc (str (:docs/alt b))) "\"/></figure>")
               "")
      :quote (str "<blockquote><p>" text "</p></blockquote>")
      ;; `<pre>` holds the raw text, not the run-marked one: inside a
      ;; preformatted block a `<strong>` is markup somebody is reading, not
      ;; emphasis they meant.
      :code (str "<pre><code>" (esc (:docs/text b)) "</code></pre>")
      :list (let [tag (if (:docs/ordered? b) "ol" "ul")]
              (str "<" tag ">"
                   (apply str (map #(str "<li>" (esc %) "</li>") (:docs/items b)))
                   "</" tag ">"))
      :table (let [rows (vec (:docs/rows b))
                   width (apply max 0 (map count rows))
                   cells (fn [row tag]
                           (apply str (for [c (take width (concat row (repeat "")))]
                                        (str "<" tag ">" (esc c) "</" tag ">"))))]
               (when (seq rows)
                 ;; The first row is the header. Every table this Drive
                 ;; produces has one, and a table printed with no `<th>` is
                 ;; a grid rather than a table to anything reading it aloud.
                 (str "<table><thead><tr>" (cells (first rows) "th") "</tr></thead>"
                      "<tbody>"
                      (apply str (for [row (rest rows)]
                                   (str "<tr>" (cells row "td") "</tr>")))
                      "</tbody></table>")))
      (:table-ref :file-ref :deck-ref)
      ;; A reference names a document in a Drive. On a printed page there is
      ;; nothing to follow, so it says what it points at rather than
      ;; pretending to be a link.
      (str "<p class=\"docs-ref\">" (esc (name (:docs/kind b))) ": "
           (esc (:docs/target b)) "</p>")
      (str "<p>" text "</p>"))))

(defn body
  "A document's blocks as HTML.

  The title becomes an `<h1>` unless the document already opens with one —
  otherwise a document whose first block is its title prints the title
  twice, which is what a naive renderer does and what a reader notices
  immediately."
  [doc]
  (let [blocks (filter map? (:docs/blocks doc))
        opens-with-h1? (and (seq blocks)
                            (= :heading (:docs/kind (first blocks)))
                            (= 1 (:docs/level (first blocks))))
        title (:docs/title doc)]
    (str (when (and (not (str/blank? (str title))) (not opens-with-h1?))
           (str "<h1>" (esc title) "</h1>"))
         (apply str (keep block->html blocks)))))
