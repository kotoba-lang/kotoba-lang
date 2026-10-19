(ns hanmen.svg
  "A `hanmen.page` drawn as SVG — safe to put inside a `default-src 'none'`
  page.

  ## Why SVG, and why that is not the contradiction it looks like

  The host this was built for serves uploaded bytes with
  `Content-Disposition: attachment` and keeps SVG out of its inline allowlist
  on purpose: an SVG is XML, it may contain `<script>`, and a browser runs it
  when it is a document rather than an `<img>` source. Emitting SVG to show a
  PDF therefore has to answer that objection rather than ignore it, and the
  answer is that these are two different things wearing one file extension.

  The refused one is **bytes a user uploaded**, whose element set is whatever
  they wrote. This one is **markup this namespace generates** from a value
  whose vocabulary is `hanmen.page/item-kinds` — three kinds, each emitting a
  fixed element with a fixed attribute list. There is no path from document
  content to an element name or an attribute name; content only ever becomes
  an escaped text node or an escaped attribute *value*.

  That is a property of the code rather than a promise about it, and
  `serialize` enforces it a second time at the boundary: an element not in
  `allowed-elements` or an attribute not in `allowed-attributes` throws
  instead of being written. A single check would be enough if the emitter
  never changed. Two are enough when it does.

  Inline in the page rather than a `data:` URI in an `<img>` for the same
  reason: a `data:` URI is a *load*, and a page whose CSP is `default-src
  'none'` has to be widened to permit it. Markup in the document is not a
  load and needs no widening. Deciding what a page may load is a decision on
  its own, not a side effect of adding a viewer.

  ## Nothing here picks a colour, a font or a size

  Everything paints in `currentColor` and carries a class. The host's
  stylesheet — its `--hig-*` tokens, its theme — decides what the ink looks
  like, which is the only way one rendering can be right in both light and
  dark. A document's own black is not the reader's black on a dark page, and
  a viewer that emitted `#000` would be unreadable in exactly the mode people
  read long documents in.

  Ink *density* is preserved: a 60% grey rule stays lighter than a black one,
  through `fill-opacity`, because that difference is the document's meaning
  and the absolute colour is not."
  (:require [clojure.string :as str]
            [hanmen.page :as page]))

(def allowed-elements
  "Every element this may emit. `foreignObject` is absent for the obvious
  reason and `style` for the less obvious one: a stylesheet in the fragment
  would be a second place styling is decided, and the host's is the only one."
  #{"svg" "g" "rect" "text" "path" "image" "defs" "clipPath" "title" "desc"})

(def allowed-attributes
  "Per element, so that `href` cannot appear on `text` by a typo or on `svg`
  by a future edit.

  `image/href` is the one URL in the file and it is worth saying exactly what
  it is and is not. It appears ONLY when the caller passes `:image-href`, so a
  host that has not decided its CSP gets a fragment that loads nothing. The
  URL is what that function returned; the item it was called with carries an
  integer index and never the `/XObject` name, so there is no path from
  document content into a URL even when images are on. `emit-image` refuses a
  returned value that is not a same-origin path, which is the check that makes
  that a property rather than a convention."
  {"svg" #{"viewBox" "width" "height" "class" "role" "aria-label"
           "preserveAspectRatio" "xmlns"}
   ;; `clip-path` is the second URL in the file and the narrower of the two:
   ;; `url(#…)` is a reference INTO this document, resolved without a
   ;; request, and `clip-ref` builds it from an integer this namespace
   ;; generated. There is no form of it that can name anything outside.
   "g" #{"class" "transform" "clip-path"}
   "defs" #{}
   "clipPath" #{"id" "clip-path" "clipPathUnits"}
   "rect" #{"x" "y" "width" "height" "class" "fill-opacity"}
   "text" #{"x" "y" "class" "font-size" "textLength" "lengthAdjust"
            "fill-opacity" "xml:space"}
   ;; `d` is path data — a closed grammar of letters and numbers with no
   ;; way to name a resource. It is the one attribute here that carries
   ;; document-derived content, and `hanmen.pdf` builds it from numbers it
   ;; computed rather than from anything the file spelled.
   "path" #{"d" "class" "fill-opacity" "stroke-opacity" "stroke-width"}
   "image" #{"x" "y" "width" "height" "class" "href" "preserveAspectRatio"}
   "title" #{}
   "desc" #{}})

;; ── serialization ────────────────────────────────────────────────────────────

(defn escape
  "XML text and attribute values.

  Both apostrophe forms, because the serializer below quotes with `\"` today
  and a future edit that switches to `'` should not open an injection. The
  cost of escaping one character nobody needs escaped is nothing."
  [value]
  (-> (str value)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&apos;")))

(defn- attr-value [v]
  (cond
    (number? v) (page/num->str v)
    (keyword? v) (name v)
    :else (str v)))

(defn serialize
  "Hiccup to an SVG string, refusing anything outside the allowlists.

  The refusal is the point. This is called on markup this namespace just
  built, so a throw here means the emitter changed and the allowlist did
  not — which is exactly when a review would otherwise have to catch it."
  [node]
  (cond
    (string? node) (escape node)
    (number? node) (str node)
    (nil? node) ""
    (vector? node)
    (let [[tag & more] node
          tag (name tag)
          attrs (when (map? (first more)) (first more))
          children (if (map? (first more)) (rest more) more)]
      (when-not (contains? allowed-elements tag)
        (throw (ex-info (str "hanmen.svg would emit a disallowed element: " tag)
                        {:type :hanmen/disallowed-element :element tag})))
      (doseq [k (keys attrs)]
        (when-not (contains? (get allowed-attributes tag #{}) (name k))
          (throw (ex-info (str "hanmen.svg would emit a disallowed attribute: "
                               tag "/" (name k))
                          {:type :hanmen/disallowed-attribute
                           :element tag :attribute (name k)}))))
      (str "<" tag
           (apply str (for [[k v] (sort-by (comp name key) attrs)
                            :when (some? v)]
                        (str " " (name k) "=\"" (escape (attr-value v)) "\"")))
           (if (seq children)
             (str ">" (apply str (map serialize children)) "</" tag ">")
             "/>")))
    (seq? node) (apply str (map serialize node))
    :else (escape (str node))))

;; ── items ────────────────────────────────────────────────────────────────────

(defn- same-origin-path?
  "A root-relative path with no scheme, no authority and no backslash.

  The narrowest thing that can still name a resource on the page's own origin.
  `//host/x` is protocol-relative and reaches another origin; `\\` is a path
  separator to some parsers and not to others, which is exactly the kind of
  disagreement a URL check should refuse rather than resolve."
  [value]
  (boolean (and (string? value)
                (str/starts-with? value "/")
                (not (str/starts-with? value "//"))
                (not (str/includes? value "\\"))
                (not (re-find #"(?i)^[a-z][a-z0-9+.-]*:" value)))))

(defn- emit-image
  "A raster, when the caller said where its pixels are."
  [{:item/keys [x y width height index]} image-href]
  (let [href (when image-href (image-href {:index index}))]
    (if-not (some? href)
      ;; No source: the region, outlined. The same answer a `:frame` gets,
      ;; because it is the same situation — something is there and this
      ;; cannot show it.
      [:g {:class "hanmen-frame"}
       [:title (str "image " index)]
       [:rect {:x x :y y :width width :height height :class "hanmen-frame__box"}]]
      (do
        (when-not (same-origin-path? href)
          (throw (ex-info (str "hanmen.svg was given an image href that is not a "
                               "same-origin path: " (pr-str href))
                          {:type :hanmen/foreign-image-href :href href})))
        [:image {:x x :y y :width width :height height
                 :class "hanmen-image" :href href
                 ;; The XObject's box is where the image goes; a raster that
                 ;; kept its own aspect ratio would leave the page's layout
                 ;; and the image disagreeing about where the edges are.
                 :preserveAspectRatio "none"}]))))

(defn emit-item
  "One mark as hiccup.

  Throws on an unknown kind rather than skipping it: a kind added to
  `hanmen.page/item-kinds` without a case here is a mark that vanishes from
  every rendering, and a page that is quietly missing something looks exactly
  like a page that is complete."
  ([item] (emit-item item nil))
  ([{:item/keys [kind x y width height text size ink label reason direction]
     :as item}
    image-href]
   (case kind
    :text
    (let [invisible? (= direction :invisible)
          ;; Knocked out of a dark panel rather than printed on paper.
          ;;
          ;; A THRESHOLD and not an opacity: text at 0.3 of the ink is not
          ;; faint text, it is unreadable text, and the distinction the
          ;; document is making is between ink and the absence of it. The
          ;; rule under it is already painted, so the paper colour here
          ;; composites the way the page does.
          reversed? (and (number? ink) (< ink 0.5) (not invisible?))]
      [:text (cond-> {:x x :y y
                      :class (cond invisible? "hanmen-text hanmen-text--invisible"
                                   reversed? "hanmen-text hanmen-text--reversed"
                                   :else "hanmen-text")
                      :font-size size
                      :xml:space "preserve"}
               ;; Only when the document shipped widths — see `hanmen.pdf`.
               ;; `spacingAndGlyphs` rather than `spacing`: with the reader's
               ;; font, adjusting gaps alone leaves a long run visibly short
               ;; or crowded, and the run's box is what has to match.
               (and width (pos? width)) (assoc :textLength width
                                               :lengthAdjust "spacingAndGlyphs")
               ;; The text layer under a scan. Present so it can be selected
               ;; and searched, invisible because the image above it is what
               ;; the reader is looking at — drawing both is double vision.
               invisible? (assoc :fill-opacity 0))
       (str text)])

    :rule
    [:rect (cond-> {:x x :y y :width width :height height
                    :class (str "hanmen-rule"
                                (when (:item/pattern item) " hanmen-rule--patterned"))}
             ;; Density, not colour — see the ns docstring. A rule with no
             ;; ink recorded draws at full strength, which is what a page
             ;; that never issued a colour operator means.
             (number? ink) (assoc :fill-opacity ink))]

    :path
    (let [{:item/keys [d fill stroke stroke-width]} item]
      [:path (cond-> {:d d :class (str "hanmen-path"
                                       (when-not fill " hanmen-path--unfilled")
                                       (when (:item/pattern item)
                                         " hanmen-path--patterned"))}
               ;; Painted in `currentColor` like everything else, with the
               ;; document's ink as opacity. A path with no fill needs to
               ;; say so: SVG fills a path black by default, so a stroked
               ;; outline would come out a solid shape.
               (number? fill) (assoc :fill-opacity fill)
               (number? stroke) (assoc :stroke-opacity stroke)
               (number? stroke-width) (assoc :stroke-width
                                             (max 0.1 stroke-width)))])

    :image (emit-image item image-href)

    :frame
    [:g {:class "hanmen-frame"}
     [:title (str label " — " (name (or reason :undecoded)))]
     [:rect {:x x :y y :width width :height height :class "hanmen-frame__box"}]]

    (throw (ex-info (str "hanmen.svg has no case for item kind: " kind)
                    {:type :hanmen/unhandled-item-kind :kind kind})))))

;; ── the page ─────────────────────────────────────────────────────────────────

(defn- clip-id
  "The id for clip `n`. Prefixed, because a page's fragment may sit in a
  document that has ids of its own and `#3` is a collision waiting to be
  somebody else's bug."
  [n]
  (str "hanmen-clip-" n))

(defn- clip-ref [n] (str "url(#" (clip-id n) ")"))

(defn- emit-clips
  "The clip definitions, as `<defs>`, or nil when there are none.

  Each points at the one it narrows through its own `clip-path`, because
  two paths inside one `clipPath` are UNIONED — the opposite of what a
  document that clipped twice meant. Nesting is how SVG intersects."
  [clips]
  (when (seq clips)
    (into [:defs {}]
          (map (fn [{:clip/keys [id d parent]}]
                 [:clipPath (cond-> {:id (clip-id id)}
                              (some? parent) (assoc :clip-path (clip-ref parent)))
                  [:path {:d d :class "hanmen-clip"}]]))
          clips)))

(defn- grouped
  "Marks wrapped in a `<g clip-path>` per run that shares a clip.

  Per RUN and not per clip: marks arrive in painting order and a clip turns
  on and off between them, so gathering all of one clip's marks together
  would reorder the page. Painting order is the one thing a page cannot
  lose."
  [items image-href]
  (into []
        (map (fn [group]
               (let [c (:item/clip (first group))
                     drawn (map #(emit-item % image-href) group)]
                 (if (some? c)
                   (into [:g {:class "hanmen-clipped" :clip-path (clip-ref c)}] drawn)
                   (into [:g {:class "hanmen-marks"}] drawn)))))
        (partition-by :item/clip items)))

(defn emit
  "A page as an SVG fragment, in hiccup.

  The `viewBox` is the page in its own units and `width`/`height` are what it
  occupies on screen, so scaling is the browser's job and the geometry never
  has to be recomputed for a second zoom level. A server that rendered at a
  fixed pixel size would be re-rendering on every resize, and a `re-render`
  is where a viewer stops feeling like paper."
  ([p] (emit p {}))
  ([{:page/keys [width height items label index] :as p}
    {:keys [fit-to class-name] :as opts}]
   (let [scale (or (when fit-to (:fit/scale (page/fit p fit-to :contain)))
                   (:scale opts)
                   1.0)]
     [:svg {:xmlns "http://www.w3.org/2000/svg"
            :viewBox (str "0 0 " (page/num->str width) " " (page/num->str height))
            :width (* width scale)
            :height (* height scale)
            :class (str "hanmen-page" (when class-name (str " " class-name)))
            :role "img"
            ;; Named rather than left to the reader's screen reader to
            ;; announce as "graphic": a document of forty pages read out as
            ;; forty graphics is a document nobody can navigate.
            :aria-label (str (or label (str "Page " (inc (or index 0))))
                             (when (page/scanned? p) " (scanned — no text)"))}
      (emit-clips (:page/clips p))
      (into [:g {:class "hanmen-page__marks"}]
            (grouped items (:image-href opts)))])))

(defn ->svg
  "A page as an SVG string, allowlist-enforced."
  ([p] (->svg p {}))
  ([p opts] (serialize (emit p opts))))

(def stylesheet
  "What the host needs in ITS stylesheet for a rendering to look like a page.

  Shipped as a string rather than emitted into the fragment so that it lands
  in the host's cascade, under the host's tokens, and so a page containing
  forty renderings carries these rules once instead of forty times.

  No colour and no font family: `currentColor` takes the host's text colour
  and `font-family: inherit` takes its font, which is the only way this can
  be right in both themes. A host that wants the document's own serif sets
  `--hanmen-font` and nothing here has to know what it chose."
  (str ".hanmen-page{display:block;max-width:100%;height:auto;"
       "background:var(--hanmen-paper,transparent)}"
       ".hanmen-text{fill:currentColor;font-family:var(--hanmen-font,inherit);"
       "white-space:pre}"
              ;; `canvas` is the CSS system colour for a page background — white
       ;; in light mode and dark in dark mode — so a host that never sets
       ;; `--hanmen-paper` still knocks the text out against the reader's
       ;; own paper rather than against a hex somebody guessed.
       ".hanmen-text--reversed{fill:var(--hanmen-paper,canvas)}"
       ".hanmen-rule{fill:currentColor}"
       ".hanmen-path{fill:currentColor;stroke:currentColor;stroke-opacity:0}"
       ".hanmen-path--unfilled{fill:none}"
       ;; A clip path is never painted — it exists to shape what is.
       ".hanmen-clip{fill:none;stroke:none}"
       ;; A patterned fill is not a colour this knows. Drawn faintly and
       ;; named, so the shape is where the document put it and nobody
       ;; mistakes the tint for the document's own.
       ".hanmen-rule--patterned,.hanmen-path--patterned{fill-opacity:.15}"
       ".hanmen-image{image-rendering:auto}"
       ".hanmen-frame__box{fill:none;stroke:currentColor;stroke-opacity:.35;"
       "stroke-dasharray:4 3;stroke-width:1}"))
