(ns forms.wire
  "Transit wire helpers for Kotoba Forms forms.

  ## Out is lossy, back is explicit

  `transit.core/write-json` projects onto plain JSON: keywords become bare
  strings and map keys become strings. A form carries two keyword *values*
  the schema has to put back — `:forms/type` and each field's
  `:forms/field-type` — and nothing else that the projection changes.

  A generic keywordizer cannot tell a field type from a label, which is why
  `rehydrate-form` is here next to the model that defines them rather than in
  `transit`. `read-form-envelope` returns the projection unchanged, for
  callers that only want to look at a value; `rehydrate-form` returns
  something `forms.model` and `forms.validate` will accept."
  (:require [transit.core :as transit]))

(defn form-envelope
  ([form] (form-envelope form {}))
  ([form opts]
   (transit/office-envelope :forms/form form opts)))

(defn read-form-envelope [body]
  (let [envelope (transit/read-office-envelope-body body)]
    (when-not (= :forms/form (:kotoba.resource/kind envelope))
      (throw (ex-info "not a Forms form Transit envelope"
                      {:kind (:kotoba.resource/kind envelope)})))
    (:kotoba.resource/payload envelope)))

;; ── back from plain JSON ────────────────────────────────────────────────────

;; Anything of the wrong shape passes straight through. This converter exists
;; to hand a value to `forms.validate`, and one that throws is one the
;; validator never gets to answer — the caller gets a crash where it should
;; have got the list of what is wrong. Measured: `{"forms/fields" "nope"}`
;; threw `Don't know how to create ISeq from: java.lang.Character`.

(defn- rehydrate-field [field]
  (if-not (map? field)
    field
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword k)
                        (if (and (= "forms/field-type" k) (string? v)) (keyword v) v)))
               {} field)))

(defn rehydrate-form
  "A plain-JSON payload back into a form."
  [payload]
  (if-not (map? payload)
    payload
    (reduce-kv
     (fn [acc k v]
       (case k
         "forms/type" (assoc acc :forms/type (if (string? v) (keyword v) v))
         "forms/fields" (assoc acc :forms/fields
                               (if (sequential? v) (mapv rehydrate-field v) v))
         (assoc acc (keyword k) v)))
     {} payload)))

(defn form-of-envelope
  "Read an envelope body and rehydrate it in one step."
  [body]
  (rehydrate-form (read-form-envelope body)))
