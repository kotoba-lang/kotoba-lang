(ns byoubu.core
  "Single require point for the backdrop catalog.

  Consumers require `byoubu.core` and nothing else, the same discipline
  `kotoba-ui.core` keeps for the design system — the internal split between
  catalog / facts / plate / spec is this library's business, not its
  callers'.

    (byoubu/fetch :purple-desert)        ;; the backdrop
    (byoubu/facts :purple-desert)        ;; ink, appearance, accent, contrast
    (byoubu/plate-layers :purple-desert) ;; tier-0 gradient data

  Every fn accepts either an id or an already-fetched backdrop map, so a
  caller that has one in hand never has to look it up again."
  (:require [byoubu.catalog :as catalog]
            [byoubu.facts :as facts]
            [byoubu.plate :as plate]
            [byoubu.poster :as poster]
            [byoubu.spec :as spec]))

(defn- ->backdrop [x]
  (if (map? x) x (catalog/fetch x)))

;; --- catalog ---------------------------------------------------------------
(def catalog catalog/catalog)
(def generator catalog/generator)
(def ids catalog/ids)
(def lookup catalog/lookup)
(def fetch catalog/fetch)
(def by-tag catalog/by-tag)

;; --- derived ---------------------------------------------------------------
(defn facts
  "Legibility facts for a backdrop (id or map). See byoubu.facts."
  [x]
  (facts/derive-facts (->backdrop x)))

(defn plate-layers
  "Tier-0 gradient layer data for a backdrop (id or map). See byoubu.plate."
  [x]
  (plate/layers (->backdrop x)))

(defn plate-base-color
  "The color to paint under the tier-0 layers."
  [x]
  (plate/base-color (->backdrop x)))

;; --- validation ------------------------------------------------------------
(def wcag-aa-body facts/wcag-aa-body)

(defn problems
  "Validation problems for a backdrop (id or map); empty means valid."
  [x]
  (spec/problems (->backdrop x)))

(defn valid? [x] (spec/valid? (->backdrop x)))

(defn catalog-problems
  "Every problem across the whole catalog — what CI asserts is empty."
  []
  (vec (mapcat #(spec/problems (catalog/fetch %)) (catalog/ids))))

;; --- artifacts -------------------------------------------------------------
(def posters poster/posters)

(defn poster
  "Tier-1 poster manifest entry for a backdrop id: {:path :bytes :sha256}, or
  nil if none has been rendered."
  [id]
  (poster/poster id))

(defn poster-url
  "Poster URL under a base path, e.g. (poster-url :purple-desert \"/assets\")."
  [id base]
  (poster/url id base))
