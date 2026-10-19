(ns slides.validate
  (:require [slides.design :as design]
            [slides.model :as model]
            [slides.routes :as routes]))

(defn problem [severity code id msg]
  {:slides/severity severity
   :slides/code code
   :slides/id id
   :slides/msg msg})

(def shape-kinds
  "The shapes this deck's own model makes.

  `:image` was missing, and it is one `slides.model/image` constructs,
  `slides.pptx` embeds as a `p:pic` with its own media part, `slides.svg`
  draws as a data URI, and `slides.office` produces when it reads a
  PowerPoint file containing a picture. So every deck with a photograph in
  it — including every one imported from a .pptx — warned `unknown shape
  kind will use renderer fallback` on every save, about a kind three of
  this library's four writers know by name."
  ;; `:table` was missing for the same reason `:image` was: `slides.pptx`
  ;; writes one as a native `<a:tbl>` and `slides.office` produces the shape
  ;; when it reads a PowerPoint file with a table in it, so every deck with
  ;; one — including every imported deck — warned about an unknown shape on
  ;; every save.
  #{:text :rect :image :table})

(def design-override-keys
  [:slides/master :slides/guides
   :slides/text-styles :slides/components :slides/design])

(defn- valid-theme-override? [x]
  (or (map? x) (keyword? x)))

(defn workspace-problems [ws]
  (cond-> []
    (not (map? ws))
    (conj (problem :error :workspace/not-map nil "workspace must be an EDN map"))

    (and (map? ws) (nil? (:slides/id ws)))
    (conj (problem :error :workspace/missing-id nil "workspace must include :slides/id"))

    (and (map? ws) (not (map? (:slides/items ws))))
    (conj (problem :error :workspace/items-not-map (:slides/id ws) ":slides/items must be a map"))

    (and (map? ws) (not (sequential? (:slides/links ws))))
    (conj (problem :error :workspace/links-not-sequential (:slides/id ws) ":slides/links must be sequential"))))

(defn item-problems [ws]
  (if-not (map? (:slides/items ws))
    []
    (mapcat
     (fn [[id it]]
       (cond-> []
         (not (map? it))
         (conj (problem :error :item/not-map id "item must be an EDN map"))

         (and (map? it) (not= id (:slides/id it)))
         (conj (problem :error :item/id-key-mismatch id "item key must equal :slides/id"))

         (and (map? it) (not (contains? model/item-kinds (:slides/kind it))))
         (conj (problem :error :item/unknown-kind id "unknown item kind"))

         (and (map? it) (or (nil? (:slides/title it)) (= "" (:slides/title it))))
         (conj (problem :warning :item/missing-title id "item has no title"))))
     (:slides/items ws))))

(defn- shape-problems [deck-id slide-id shape-idx components shape]
  (cond-> []
    (not (map? shape))
    (conj (problem :error :shape/not-map (str deck-id "/" slide-id "#" shape-idx)
                   "shape must be an EDN map"))

    (and (map? shape) (nil? (:slides/id shape)))
    (conj (problem :warning :shape/missing-id (str deck-id "/" slide-id "#" shape-idx)
                   "shape has no id"))

    (and (map? shape)
         (:slides/component shape)
         (not (contains? components (:slides/component shape))))
    (conj (problem :warning :shape/unknown-component (str deck-id "/" slide-id "#" shape-idx)
                   "unknown component id will use renderer fallback"))

    (and (map? shape)
         (nil? (:slides/component shape))
         (not (contains? shape-kinds (:slides/shape shape))))
    (conj (problem :warning :shape/unknown-kind (str deck-id "/" slide-id "#" shape-idx)
                   "unknown shape kind will use renderer fallback"))))

(defn- slide-problems [deck-id components slide-idx slide]
  (if-not (map? slide)
    [(problem :error :slide/not-map (str deck-id "#" slide-idx)
              "slide must be an EDN map")]
    (let [slide-id (or (:slides/id slide) (str "slide-" slide-idx))
          shapes (:slides/shapes slide)]
      (concat
       (cond-> []
         (nil? (:slides/id slide))
         (conj (problem :warning :slide/missing-id (str deck-id "#" slide-idx)
                        "slide has no id"))

         (or (nil? (:slides/title slide)) (= "" (:slides/title slide)))
         (conj (problem :warning :slide/missing-title slide-id
                        "slide has no title"))

         (not (sequential? shapes))
         (conj (problem :error :slide/shapes-not-sequential slide-id
                        ":slides/shapes must be sequential")))
       (when (sequential? shapes)
         (mapcat (fn [[idx shape]]
                   (shape-problems deck-id slide-id idx components shape))
                 (map-indexed vector shapes)))))))

(defn deck-problems [ws]
  (if-not (map? (:slides/items ws))
    []
    (mapcat
     (fn [[id it]]
       (if (and (map? it) (= :slides/deck (:slides/kind it)))
         (let [slides (:slides/slides it)
               components (set (keys (:slides/components (design/deck-design it))))]
           (concat
            (when (and (contains? it :slides/theme)
                       (not (valid-theme-override? (:slides/theme it))))
              [(problem :warning :design/theme-override-invalid (str id "/theme")
                        "theme override must be a map or keyword shorthand")])
            (map (fn [k]
                   (problem :warning :design/override-not-map (str id "/" (name k))
                            "design override must be an EDN map"))
                 (filter #(and (contains? it %)
                               (not (map? (get it %))))
                         design-override-keys))
            (cond-> []
              (not (sequential? slides))
              (conj (problem :error :deck/slides-not-sequential id
                             ":slides/slides must be sequential")))
            (when (sequential? slides)
              (mapcat (fn [[idx slide]]
                        (slide-problems id components idx slide))
                      (map-indexed vector slides)))))
         []))
     (:slides/items ws))))

(defn link-problems [ws]
  (if-not (and (map? (:slides/items ws))
               (sequential? (:slides/links ws)))
    []
    (let [ids (set (keys (:slides/items ws)))]
      (mapcat
       (fn [link]
         (if-not (map? link)
           [(problem :error :link/not-map nil "link must be an EDN map")]
           (let [{:slides/keys [from to link-kind]} link]
             (cond-> []
               (not (contains? ids from))
               (conj (problem :error :link/dangling-from from "link source does not exist"))

               (not (contains? ids to))
               (conj (problem :error :link/dangling-to to "link target does not exist"))

               (not (contains? model/link-kinds link-kind))
               (conj (problem :error :link/unknown-kind (str from "->" to) "unknown link kind"))))))
       (:slides/links ws)))))

(defn route-problems []
  (let [required #{"kotoba-lang.github.io/slides"
                   "kotoba-lang.github.io/docs"
                   "kotoba-lang.github.io/drive"
                   "kotoba-lang.github.io/sheets"}
        present (set (keys routes/hosts))]
    (for [host (sort (remove present required))]
      (problem :error :route/missing-host host "required host route is missing"))))

(defn problems [ws]
  (vec (concat (workspace-problems ws)
               (item-problems ws)
               (deck-problems ws)
               (link-problems ws)
               (route-problems))))

(defn valid? [ws]
  (not-any? #(= :error (:slides/severity %)) (problems ws)))
