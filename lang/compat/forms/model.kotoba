(ns forms.model
  (:require [clojure.string :as str]))

(def field-types #{:text :textarea :email :number :date :choice :checkbox})

(defn form
  ([id] (form id {}))
  ([id attrs]
   (merge {:forms/id id
           :forms/type :form
           :forms/title id
           :forms/fields []}
          attrs)))

(defn field
  "One question.

  A `:choice` field is answered by picking one of `:forms/options`, which is
  a vector of strings. Nothing here modelled them: `:choice` was in
  `field-types` from the beginning and there was no way to say what the
  choices were, so an application offering the type gave people a question
  whose answer it could not describe, could not render as a list and could
  not check. It is here rather than in an application's own key because
  three things have to agree about it — the editor that sets them, the form
  that offers them and the validator that checks an answer against them."
  [id type attrs]
  (merge {:forms/id id
          :forms/field-type type
          :forms/label id
          :forms/required? false}
         attrs))

(defn options
  "A field's choices, as strings, and always a vector.

  Anything that is not a list of them is no choices: a string typed into the
  wrong box would otherwise become a list of its characters."
  [fld]
  (if (sequential? (:forms/options fld))
    (mapv str (:forms/options fld))
    []))

(defn choice? [fld] (= :choice (:forms/field-type fld)))

(defn allowed-answer?
  "Whether `answer` is one this field can be answered with.

  True for every field that is not a choice: a text answer is whatever was
  typed. For a choice it has to be one of the options, compared as the
  strings they were written as — an answer arrives from JSON and an option
  was typed into an editor, and neither is a keyword."
  [fld answer]
  (or (not (choice? fld))
      (contains? (set (options fld)) (str answer))))

(defn add-field [f fld]
  (update f :forms/fields conj fld))

(defn submission [form-id answers]
  {:forms/form-id form-id
   :forms/answers answers})

(defn missing-required [f sub]
  (->> (:forms/fields f)
       (filter :forms/required?)
       (remove #(contains? (:forms/answers sub) (:forms/id %)))
       (mapv :forms/id)))

(defn valid-email? [s]
  (boolean (re-matches #"[^@\s]+@[^@\s]+\.[^@\s]+" (str s))))

(defn seed-form []
  (-> (form "contact" {:forms/title "Contact"})
      (add-field (field "name" :text {:forms/required? true}))
      (add-field (field "email" :email {:forms/required? true}))))
