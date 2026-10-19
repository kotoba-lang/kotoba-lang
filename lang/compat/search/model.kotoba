(ns search.model
  (:require [clojure.string :as str]))

(def default-weights
  {:title 4
   :tags 3
   :body 1})

(defn index
  ([] (index {}))
  ([attrs]
   (merge {:search/id "gftd-search"
           :search/type :index
           :search/docs {}
           :search/weights default-weights}
          attrs)))

(defn document [id attrs]
  (merge {:search/id id
          :search/title id
          :search/body ""
          :search/tags #{}}
         attrs))

(defn add-document [idx doc]
  (assoc-in idx [:search/docs (:search/id doc)] doc))

(defn remove-document [idx id]
  (update idx :search/docs dissoc id))

(defn tokenize [s]
  (->> (str/lower-case (str s))
       (re-seq #"[a-z0-9\u3040-\u30ff\u3400-\u9fff]+")
       (remove str/blank?)
       vec))

(defn field-text [doc field]
  (case field
    :tags (str/join " " (:search/tags doc))
    :title (:search/title doc)
    :body (:search/body doc)
    ""))

(defn term-count [term text]
  (count (filter #(= term %) (tokenize text))))

(defn score-doc [weights terms doc]
  (reduce
   (fn [score term]
     (+ score
        (reduce-kv
         (fn [s field weight]
           (+ s (* weight (term-count term (field-text doc field)))))
         0
         weights)))
   0
   terms))

(defn search [idx q]
  (let [terms (tokenize q)
        weights (:search/weights idx)]
    (->> (vals (:search/docs idx))
         (map (fn [doc] (assoc doc :search/score (score-doc weights terms doc))))
         (filter #(pos? (:search/score %)))
         (sort-by (juxt (comp - :search/score) :search/id))
         vec)))

(defn seed-index []
  (-> (index)
      (add-document (document "slides" {:search/title "GFTD Slides"
                                        :search/body "Decks scenes notes publishing"
                                        :search/tags #{"slides" "deck" "pptx"}}))
      (add-document (document "docs" {:search/title "GFTD Docs"
                                      :search/body "Documents outlines decisions"
                                      :search/tags #{"docs" "document" "memo"}}))
      (add-document (document "drive" {:search/title "GFTD Drive"
                                       :search/body "Files folders object refs"
                                       :search/tags #{"drive" "file" "object"}}))
      (add-document (document "sheets" {:search/title "GFTD Sheets"
                                        :search/body "Tables ranges formulas facts"
                                        :search/tags #{"sheets" "table" "formula"}}))))
