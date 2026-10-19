(ns docs.wire
  "Transit wire helpers for Kotoba Docs documents.

  ## Out is lossy, back is explicit

  `transit.core/write-json` projects onto plain JSON: keywords become bare
  strings and map keys become strings. A document carries three keyword
  *values* the schema has to put back — `:docs/type`, a block's `:docs/kind`,
  and a suggestion's `:docs/op` — and one map whose keys are the author's
  rather than the schema's, the style on a text run.

  A generic keywordizer cannot tell those apart from an id, which is why
  `rehydrate-document` is here next to the model that defines them rather
  than in `transit`. The wire layer does not know what a block is.

  `read-document-envelope` returns the projection unchanged, for callers
  that only want to look at a value. `rehydrate-document` returns something
  `docs.model` and `docs.validate` will accept."
  (:require [transit.core :as transit]))

(defn document-envelope
  ([doc] (document-envelope doc {}))
  ([doc opts]
   (transit/office-envelope :docs/document doc opts)))

(defn read-document-envelope [body]
  (let [envelope (transit/read-office-envelope-body body)]
    (when-not (= :docs/document (:kotoba.resource/kind envelope))
      (throw (ex-info "not a Docs document Transit envelope"
                      {:kind (:kotoba.resource/kind envelope)})))
    (:kotoba.resource/payload envelope)))

;; ── back from plain JSON ────────────────────────────────────────────────────

;; Anything of the wrong shape passes straight through. This converter exists
;; to hand a value to `docs.validate`, and one that throws is one the
;; validator never gets to answer — the caller gets a crash where it should
;; have got the list of what is wrong. Measured: `{"docs/blocks" "nope"}`
;; threw `Don't know how to create ISeq from: java.lang.Character`.

(defn- each [f v]
  (if (sequential? v) (mapv f v) v))

(defn- keywordize
  "Every key to a keyword, recursively. For maps whose keys belong to whoever
  wrote them — a text-run style, a comment anchor — and never for a map keyed
  by id."
  [m]
  (if-not (map? m)
    m
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword k) (if (map? v) (keywordize v) v)))
               {} m)))

(defn- rehydrate-text-run [run]
  (if-not (map? run)
    run
    (reduce-kv (fn [acc k v]
                 (if (= "docs/style" k)
                   (assoc acc :docs/style (keywordize v))
                   (assoc acc (keyword k) v)))
               {} run)))

(defn- rehydrate-block [block]
  (if-not (map? block)
    block
    (reduce-kv
     (fn [acc k v]
       (case k
         "docs/kind" (assoc acc :docs/kind (if (string? v) (keyword v) v))
         "docs/text-runs" (assoc acc :docs/text-runs (each rehydrate-text-run v))
         ;; :docs/rows and :docs/items are vectors of values, not of maps —
         ;; nothing in them was ever a keyword, so they pass through.
         (assoc acc (keyword k) v)))
     {} block)))

(defn- rehydrate-comment [comment]
  (if-not (map? comment)
    comment
    (reduce-kv (fn [acc k v]
                 (if (= "docs/anchor" k)
                   (assoc acc :docs/anchor (keywordize v))
                   (assoc acc (keyword k) v)))
               {} comment)))

(defn- rehydrate-suggestion [suggestion]
  (if-not (map? suggestion)
    suggestion
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword k)
                        (if (and (= "docs/op" k) (string? v)) (keyword v) v)))
               {} suggestion)))

(defn rehydrate-document
  "A plain-JSON payload back into a document."
  [payload]
  (if-not (map? payload)
    payload
    (reduce-kv
     (fn [acc k v]
       (case k
         "docs/type" (assoc acc :docs/type (if (string? v) (keyword v) v))
         "docs/blocks" (assoc acc :docs/blocks (each rehydrate-block v))
         "docs/comments" (assoc acc :docs/comments (each rehydrate-comment v))
         "docs/suggestions" (assoc acc :docs/suggestions (each rehydrate-suggestion v))
         (assoc acc (keyword k) v)))
     {} payload)))

(defn document-of-envelope
  "Read an envelope body and rehydrate it in one step."
  [body]
  (rehydrate-document (read-document-envelope body)))
