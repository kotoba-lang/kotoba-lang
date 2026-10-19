(ns docs.markdown
  "A document as Markdown, and back.

  ## Why this exists

  A document could only leave as EDN, which is the same as not leaving: the
  format is exact and nothing else reads it. Markdown is what a person can
  open, paste into a mail, or put under version control, and the block model
  here maps onto it almost exactly — a heading is a heading, a list is a
  list, a table is a table.

  ## Almost

  Three things do not survive, and the point of `unexpressed` is that a
  document can be asked what it will lose *before* someone exports it rather
  than after.

  **Block ids are not written and are regenerated on the way back.** There is
  nowhere in Markdown to put them, and the usual trick — an HTML comment
  carrying the id — produces a file that only this application can read back
  faithfully while looking like an ordinary one. Ids here are handles
  (`block-by-id`, a comment's anchor), not content. Round-tripping a document
  through Markdown therefore gives an equal document with different ids, and
  the tests say so rather than comparing whole documents and quietly passing
  because the fixture happened to use `b1`, `b2`.

  **Comments and suggestions are not written.** They are not text in the
  document; they are things said about it.

  **A style Markdown has no syntax for is not written.** `**` and `*` and
  backticks cover bold, italic and code. A run carrying a colour, a font, a
  highlight has no spelling here, so `unexpressed` names it and the text goes
  out unstyled — visibly plain rather than plausibly wrong.

  ## What reading accepts

  More than writing produces, because whatever is pasted in was not written
  by this namespace: ATX headings, `>` quotes, fenced code, `-`/`*`/`+` and
  `1.` lists, GFM pipe tables, and blank-line-separated paragraphs. It never
  throws — malformed input becomes the nearest block, usually a paragraph,
  and the validator is what reports a document it cannot accept. A parser
  that threw would turn a bad paste into a 500."
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str]
            [docs.model :as model]))

;; ── what Markdown cannot carry ──────────────────────────────────────────────

(def expressible-styles
  "The run styles Markdown has a spelling for.

  `:code` last, because it is the one whose content is literal — a run that
  is both bold and code would otherwise get its `**` inside the backticks,
  where Markdown shows the asterisks rather than the emphasis."
  [:bold :italic :code :link])

(defn unexpressed
  "What `write` will drop from this document, one entry per thing.

  Shaped like `docs.validate/problems` — severity, code, id, message — so a
  caller that already renders problems can render these without learning a
  second shape. All of them are `:info`: losing something on the way to
  Markdown is a property of Markdown, not a fault in the document."
  [doc]
  (let [entry (fn [code id msg]
                {:docs/severity :info :docs/code code :docs/id id :docs/msg msg})]
    (vec
     (concat
      (when (seq (:docs/comments doc))
        [(entry :markdown/comments-dropped (:docs/id doc)
                (str (count (:docs/comments doc))
                     " comment(s) are about the document rather than in it"))])
      (when (seq (:docs/suggestions doc))
        [(entry :markdown/suggestions-dropped (:docs/id doc)
                (str (count (:docs/suggestions doc)) " suggestion(s) have no Markdown"))])
      (for [b (:docs/blocks doc)
            run (:docs/text-runs b)
            [k _] (:docs/style run)
            ;; `:link` is spellable only when it is a link. Adding it to
            ;; `expressible-styles` made every link expressible, including
            ;; the `javascript:` one this writer drops — which turned a
            ;; reported loss into a silent one, the same way it did in the
            ;; docx writer an hour earlier.
            :when (if (= :link k)
                    (nil? (model/link (:docs/style run)))
                    (not (some #{k} expressible-styles)))]
        (entry :markdown/style-dropped (:docs/id b)
               (if (= :link k)
                 "Markdown has no spelling for a link that is not a place"
                 (str "Markdown has no spelling for " k))))
      (for [b (:docs/blocks doc)
            :when (contains? #{:table-ref :file-ref :deck-ref} (:docs/kind b))]
        (entry :markdown/reference-is-a-link (:docs/id b)
               (str (name (:docs/kind b))
                    " becomes a link that only this Drive can follow")))
      ;; A table cell holding 120 comes back holding "120". Markdown has no
      ;; types, and reading one back as a number would be the guess
      ;; `sheets.csv` refuses — 0042 is not forty-two. Reported once per
      ;; table rather than once per cell, because the answer is the same.
      (for [b (:docs/blocks doc)
            :when (and (= :table (:docs/kind b))
                       (some #(not (string? %)) (flatten (seq (:docs/rows b)))))]
        (entry :markdown/table-cells-become-text (:docs/id b)
               "a cell that is not text comes back as text"))
      (for [b (:docs/blocks doc)
            :when (and (= :heading (:docs/kind b))
                       (number? (:docs/level b))
                       (not (<= 1 (:docs/level b) 6)))]
        (entry :markdown/heading-level-clamped (:docs/id b)
               (str "level " (:docs/level b) " has no Markdown; written as "
                    (max 1 (min 6 (:docs/level b))))))))))

;; ── writing ─────────────────────────────────────────────────────────────────

(defn- link-wrap
  "`[text](url)`, or the text when the link is not one to follow.

  Angle brackets when the URL holds a bracket or a space: `[a](b(c))`
  closes at the first `)`, so the link would be `b(c` and the rest would be
  text. Markdown's own answer to that is `<>`, and using it always would
  mean every ordinary link carried them."
  [text style]
  (if-let [url (model/link style)]
    (str "[" text "]("
         (if (re-find #"[()\s]" url) (str "<" url ">") url)
         ")")
    text))

(defn- style-wrap
  "The Markdown around a run of text carrying `style`.

  Returns the wrapped text. A style with no spelling contributes nothing,
  which `unexpressed` has already reported."
  [text style]
  (reduce (fn [acc k]
            (if (get style k)
              (case k
                :bold (str "**" acc "**")
                :italic (str "*" acc "*")
                :code (str "`" acc "`")
                acc)
              acc))
          text
          expressible-styles))

(defn- apply-runs
  "A block's text with its runs spelled out.

  Which runs are usable, and what happens when two overlap, is
  `model/text-spans`'s business — this walked them back to front so an
  earlier one's inserted characters would not move a later one's offsets,
  which is a problem that does not arise once the text arrives already cut
  into pieces."
  [b]
  (apply str (map (fn [{:keys [docs/text docs/style]}]
                    ;; The link outside the emphasis: `[**a**](url)` is a
                    ;; bold link and `**[a](url)**` is a bold thing that
                    ;; happens to be one, and only the first is what a run
                    ;; carrying both means.
                    (if style (link-wrap (style-wrap text style) style) text))
                  (model/text-spans b))))

(defn- escape-cell
  "A table cell's text, with any `|` escaped.

  An unescaped pipe ends the cell, so a cell reading `a|b` would silently
  become two."
  [x]
  (-> (str x) (str/replace "|" "\\|") (str/replace "\n" " ")))

(defn- fence-for
  "A fence long enough to contain `text`.

  Three backticks are the convention, but a code block whose content
  contains ``` needs a longer one — otherwise the block ends in the middle
  of itself and the rest of the document is inside it."
  [text]
  (let [longest (->> (re-seq #"`+" (str text)) (map count) (apply max 0))]
    (apply str (repeat (max 3 (inc longest)) "`"))))

(defn- block->md [b]
  (let [text (apply-runs b)]
    (case (:docs/kind b)
      :heading (str (apply str (repeat (model/heading-level b) "#")) " " text)
      :paragraph text
      ;; A data URI, which Markdown has no other way to carry a picture
      ;; that lives in the document. It is large — base64 is four characters
      ;; per three bytes — and some renderers refuse data URIs, but the
      ;; alternative is emitting a link to a file that does not exist, and a
      ;; picture that silently becomes nothing is the worse of the two.
      :image (if-let [[media data] (model/image-data b)]
               (str "![" (str/replace (str (:docs/alt b)) #"[\[\]]" "") "]"
                    "(data:" media ";base64," data ")")
               "")
      :quote (->> (str/split-lines (if (str/blank? text) " " text))
                  (map #(str "> " %))
                  (str/join "\n"))
      :code (let [fence (fence-for (:docs/text b))]
              ;; The raw text, not the run-applied one: inside a fence
              ;; Markdown shows `**` rather than emphasising.
              (str fence (or (:docs/language b) "") "\n" (:docs/text b) "\n" fence))
      :list (let [ordered (:docs/ordered? b)]
              (->> (:docs/items b)
                   (map-indexed (fn [i item]
                                  (str (if ordered (str (inc i) ".") "-") " "
                                       (str/replace (str item) "\n" " "))))
                   (str/join "\n")))
      :table (let [rows (:docs/rows b)
                   width (apply max 0 (map count rows))
                   row->md (fn [cells]
                             (str "| "
                                  (str/join " | "
                                            (map escape-cell
                                                 (take width (concat cells (repeat "")))))
                                  " |"))]
               (when (seq rows)
                 (str/join "\n"
                           (concat [(row->md (first rows))
                                    (str "| " (str/join " | " (repeat width "---")) " |")]
                                   (map row->md (rest rows))))))
      ;; A reference names a document in a Drive. There is no Markdown for
      ;; that, so it becomes a link whose scheme says what it is and whose
      ;; text says so too — readable anywhere, followable only here.
      (:table-ref :file-ref :deck-ref)
      (str "[" (name (:docs/kind b)) "](drive:" (:docs/target b) ")")
      ;; An unknown kind still has text more often than not. Writing it as a
      ;; paragraph loses the kind, which `unexpressed` cannot warn about
      ;; because the validator has already called it an error.
      (str text))))

(defn write
  "A document as Markdown.

  The title becomes a level-1 heading unless the document already opens with
  one — otherwise a document whose first block is its title comes out with
  the title twice, which is what a naive export does and what a reader
  notices immediately."
  [doc]
  (let [blocks (:docs/blocks doc)
        opens-with-h1? (and (seq blocks)
                            (= :heading (:docs/kind (first blocks)))
                            (= 1 (:docs/level (first blocks))))
        title (:docs/title doc)
        head (when (and (not (str/blank? (str title))) (not opens-with-h1?))
               [(str "# " title)])
        body (keep block->md blocks)]
    (str/join "\n\n" (concat head body))))

;; ── reading ─────────────────────────────────────────────────────────────────

(defn- unescape-cell [s]
  (-> (str s) str/trim (str/replace "\\|" "|")))

(defn- split-row
  "A pipe table row into its cells.

  Split by hand rather than by regex because an escaped `\\|` is a cell's
  content and a split on `|` would cut it in half."
  [line]
  (let [line (str/trim line)
        line (cond-> line (str/starts-with? line "|") (subs 1))
        line (cond-> line (str/ends-with? line "|") (#(subs % 0 (dec (count %)))))]
    (loop [chars (seq line) cur [] out []]
      (cond
        (nil? (seq chars)) (mapv unescape-cell (conj out (apply str cur)))
        (and (= \\ (first chars)) (= \| (second chars)))
        (recur (drop 2 chars) (conj cur \\ \|) out)
        (= \| (first chars)) (recur (rest chars) [] (conj out (apply str cur)))
        :else (recur (rest chars) (conj cur (first chars)) out)))))

(defn- divider-row?
  "Whether a row is a table's `---` divider and not data."
  [line]
  (and (str/includes? line "-")
       (every? #(re-matches #":?-{1,}:?" (str/trim %)) (split-row line))))

(def ^:private ref-kinds {"table-ref" :table-ref "file-ref" :file-ref
                          "deck-ref" :deck-ref})

(defn- ref-line
  "`[deck-ref](drive:x)` as a ref block's parts, or nil.

  Only a line that is *exactly* one such link: a paragraph that merely
  contains one is a paragraph containing a link."
  [line]
  (when-let [[_ label target] (re-matches #"\[([a-z-]+)\]\(drive:(.*)\)" (str/trim line))]
    (when-let [kind (get ref-kinds label)]
      [kind target])))

(defn- list-item
  "A list line as `[ordered? text]`, or nil."
  [line]
  (or (when-let [[_ text] (re-matches #"\s*[-*+]\s+(.*)" line)] [false text])
      (when-let [[_ text] (re-matches #"\s*\d+[.)]\s+(.*)" line)] [true text])))

(defn- strip-emphasis
  "Markdown emphasis removed from a line.

  Reading does not reconstruct `:docs/text-runs`. Doing so would mean
  believing that the `**` in a pasted document is emphasis rather than two
  asterisks somebody typed, and a wrong run range is worse than none — it
  puts the bold in the wrong place. So the markers come off and the text
  stays text, which `write` will then produce unstyled. That asymmetry is
  the honest one: a round trip loses styling rather than inventing it."
  [line]
  (-> line
      (str/replace #"\*\*(.+?)\*\*" "$1")
      (str/replace #"(?<![*\w])\*([^*]+?)\*(?!\*)" "$1")
      (str/replace #"`([^`]+?)`" "$1")))

(defn- paragraph-text [lines]
  (->> lines (map strip-emphasis) (str/join " ") str/trim))

(defn read
  "Markdown as a document.

  Block ids are `b1`, `b2`, … in order: the source has none, and inventing
  stable ones from content would make two identical paragraphs the same
  block. The title is the first level-1 heading if there is one, and that
  heading stays in the body — dropping it would mean a document read and
  written again lost its first line."
  ([text] (read text "doc"))
  ([text id]
   (let [lines (str/split-lines (str/replace (str text) "\r\n" "\n"))
         next-id (fn [n] (str "b" n))]
     (loop [[line & more :as all] lines
            n 1
            blocks []]
       (cond
         (nil? all)
         (let [first-h1 (first (filter #(and (= :heading (:docs/kind %))
                                             (= 1 (:docs/level %)))
                                       blocks))]
           (model/document id (cond-> {:docs/blocks blocks}
                                first-h1 (assoc :docs/title (:docs/text first-h1)))))

         (str/blank? line) (recur more n blocks)

         ;; A fence runs to the next fence of at least the same length, or to
         ;; the end. An unterminated fence is a code block to the end of the
         ;; document, which is what every Markdown renderer does with one.
         (re-matches #"\s*(`{3,}|~{3,}).*" line)
         (let [[_ fence] (re-matches #"\s*(`{3,}|~{3,}).*" line)
               lang (str/trim (subs (str/trim line) (count fence)))
               closes? #(re-matches (re-pattern (str "\\s*" (subs fence 0 1) "{"
                                                     (count fence) ",}\\s*"))
                                    %)
               body (take-while (complement closes?) more)
               rest-lines (drop (inc (count body)) more)]
           (recur rest-lines (inc n)
                  (conj blocks (model/block :code (next-id n)
                                            (cond-> {:docs/text (str/join "\n" body)}
                                              (seq lang) (assoc :docs/language lang))))))

         (re-matches #"\s*(#{1,6})\s+.*" line)
         (let [[_ hashes] (re-matches #"\s*(#{1,6})\s+.*" line)
               text (str/trim (subs (str/trim line) (count hashes)))]
           (recur more (inc n)
                  (conj blocks (model/heading (next-id n) (count hashes)
                                              (strip-emphasis text)))))

         (ref-line line)
         (let [[kind target] (ref-line line)]
           (recur more (inc n) (conj blocks (model/ref-block kind (next-id n) target))))

         (re-matches #"\s*>.*" line)
         (let [quoted (take-while #(re-matches #"\s*>.*" %) all)
               text (->> quoted
                         (map #(str/replace % #"\s*>\s?" ""))
                         (str/join "\n")
                         strip-emphasis)]
           (recur (drop (count quoted) all) (inc n)
                  (conj blocks (model/block :quote (next-id n) {:docs/text text}))))

         (list-item line)
         (let [item-lines (take-while list-item all)
               ordered (first (list-item line))
               items (mapv #(strip-emphasis (second (list-item %))) item-lines)]
           (recur (drop (count item-lines) all) (inc n)
                  (conj blocks (model/list-block (next-id n) items
                                                 {:docs/ordered? ordered}))))

         ;; A table is a run of lines containing a pipe. The divider is
         ;; dropped rather than kept as a row of dashes.
         (str/includes? line "|")
         (let [table-lines (take-while #(str/includes? % "|") all)
               rows (->> table-lines
                         (remove divider-row?)
                         (mapv #(mapv strip-emphasis (split-row %))))]
           (recur (drop (count table-lines) all) (inc n)
                  (conj blocks (model/table (next-id n) rows {}))))

         :else
         (let [para (take-while #(and (not (str/blank? %))
                                      (not (str/includes? % "|"))
                                      (not (list-item %))
                                      (not (re-matches #"\s*>.*" %))
                                      (not (re-matches #"\s*#{1,6}\s+.*" %))
                                      (not (re-matches #"\s*(`{3,}|~{3,}).*" %)))
                                all)]
           (recur (drop (count para) all) (inc n)
                  (conj blocks (model/paragraph (next-id n) (paragraph-text para))))))))))
