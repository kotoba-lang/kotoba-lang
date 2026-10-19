(ns slides.wire
  "Plain JSON wire helpers for Kotoba Slides decks.

  `read-deck-envelope` returns the plain JSON payload as written
  (string-keyed maps, arrays), not the original EDN deck shape — see
  ADR-kotoba-json-wire-protocol.md. `rehydrate-deck` converts it back, and
  `deck-of-envelope` does both.

  Rehydrate before validating. `slides.validate` matches on keywords —
  `:slides/kind` against `model/item-kinds`, a shape's `:slides/shape`
  against `shape-kinds` — and on a projected payload it finds strings, so a
  deck that is wrong comes back merely warned about rather than wrong."
  (:require [transit.core :as transit]))

(defn deck-envelope
  ([deck] (deck-envelope deck {}))
  ([deck opts]
   (transit/office-envelope :slides/deck deck opts)))

(defn read-deck-envelope [body]
  (let [envelope (transit/read-office-envelope-body body)]
    (when-not (= :slides/deck (:kotoba.resource/kind envelope))
      (throw (ex-info "not a Slides deck JSON envelope"
                      {:kind (:kotoba.resource/kind envelope)})))
    (:kotoba.resource/payload envelope)))

;; ── back from plain JSON ────────────────────────────────────────────────────
;;
;; The docstring above says callers convert explicitly with `slides.model`.
;; They cannot: `slides.model` builds decks, it does not read projected ones,
;; and every caller writing its own conversion is how a workspace ends up
;; with several that disagree. So the converter is here, next to the model
;; that says which values were keywords.
;;
;; Three are: a deck's `:slides/kind`, its `:slides/theme` — but only when it
;; is a shorthand rather than a design-override map, which `slides.validate`
;; accepts either way — and each shape's `:slides/shape` and `:slides/component`.
;; Everything else is data whose identity the projection preserved.

;; Every step below passes anything of the wrong shape straight through.
;; This converter exists to hand a value to `slides.validate`, and a
;; converter that throws on malformed input is one the validator never gets
;; to see it — the caller gets a crash where it should have got the list of
;; what is wrong. Measured: `{"slides/slides" "nope"}` used to throw
;; `Don't know how to create ISeq from: java.lang.Character`.

(defn- each [f v]
  (if (sequential? v) (mapv f v) v))

(defn- rehydrate-shape [shape]
  (if-not (map? shape)
    shape
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword k)
                        (if (and (string? v) (#{"slides/shape" "slides/component"} k))
                          (keyword v)
                          v)))
               {} shape)))

(defn- rehydrate-slide [slide]
  (if-not (map? slide)
    slide
    (reduce-kv (fn [acc k v]
                 (if (= "slides/shapes" k)
                   (assoc acc :slides/shapes (each rehydrate-shape v))
                   (assoc acc (keyword k) v)))
               {} slide)))

(defn- keywordize [m]
  (if-not (map? m)
    m
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword k) (if (map? v) (keywordize v) v)))
               {} m)))

(defn rehydrate-deck
  "A plain-JSON payload back into a deck.

  `:slides/theme` is left as a map when it is one: the model's shorthand is a
  keyword and a design override is a map, `slides.validate/valid-theme-override?`
  takes either, and turning the map's keys into keywords is right while
  turning the map itself into one would not be."
  [payload]
  (if-not (map? payload)
    payload
    (reduce-kv
     (fn [acc k v]
       (case k
         "slides/kind" (assoc acc :slides/kind (if (string? v) (keyword v) v))
         "slides/theme" (assoc acc :slides/theme
                               (cond (map? v) (keywordize v)
                                     (string? v) (keyword v)
                                     :else v))
         "slides/slides" (assoc acc :slides/slides (each rehydrate-slide v))
         (assoc acc (keyword k) v)))
     {} payload)))

(defn deck-of-envelope
  "Read an envelope body and rehydrate it in one step."
  [body]
  (rehydrate-deck (read-deck-envelope body)))
