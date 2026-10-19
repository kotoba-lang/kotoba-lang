(ns forms.validate
  (:require [forms.model :as model]))

(defn problem [severity code id msg]
  {:forms/severity severity :forms/code code :forms/id id :forms/msg msg})

(defn form-problems [f]
  (vec
   (concat
    (for [fld (:forms/fields f)
          :when (not (contains? model/field-types (:forms/field-type fld)))]
      (problem :error :field/unknown-type (:forms/id fld) "unknown field type"))
    (for [[id n] (frequencies (map :forms/id (:forms/fields f)))
          :when (> n 1)]
      (problem :error :field/duplicate-id id "duplicate field id"))
    ;; A choice with nothing to choose from cannot be answered at all, and
    ;; it is the shape every `:choice` field had while the model had no
    ;; options: an error rather than a warning, because a form that cannot
    ;; be answered is not a form that is merely untidy.
    (for [fld (:forms/fields f)
          :when (and (model/choice? fld) (empty? (model/options fld)))]
      (problem :error :field/choice-without-options (:forms/id fld)
               "choice field has no options")))))

(defn submission-problems [f sub]
  (vec
   (concat
    (for [id (model/missing-required f sub)]
      (problem :error :submission/missing-required id "required answer is missing"))
    (for [fld (:forms/fields f)
          :when (= :email (:forms/field-type fld))
          :let [answer (get-in sub [:forms/answers (:forms/id fld)])]
          :when (and answer (not (model/valid-email? answer)))]
      (problem :error :submission/invalid-email (:forms/id fld) "invalid email answer"))
    ;; An answer to a choice that is not one of the choices. Checked here
    ;; rather than left to whatever renders the question, because a form is
    ;; answerable over an API as well as through a select box, and a rule
    ;; only the select box knows is a rule anything else can walk past.
    (for [fld (:forms/fields f)
          :when (model/choice? fld)
          :let [answer (get-in sub [:forms/answers (:forms/id fld)])]
          :when (and (some? answer) (not (model/allowed-answer? fld answer)))]
      (problem :error :submission/answer-not-an-option (:forms/id fld)
               "answer is not one of the field's options")))))

(defn valid-form? [f]
  (not-any? #(= :error (:forms/severity %)) (form-problems f)))

(defn valid-submission? [f sub]
  (not-any? #(= :error (:forms/severity %)) (submission-problems f sub)))
