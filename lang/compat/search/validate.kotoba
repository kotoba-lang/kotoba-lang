(ns search.validate)

(defn problem [severity code id msg]
  {:search/severity severity :search/code code :search/id id :search/msg msg})

(defn problems [idx]
  (vec
   (concat
    (for [[id doc] (:search/docs idx)
          :when (not= id (:search/id doc))]
      (problem :error :doc/id-key-mismatch id "document key must equal :search/id"))
    (for [[id doc] (:search/docs idx)
          :when (or (nil? (:search/title doc)) (= "" (:search/title doc)))]
      (problem :warning :doc/missing-title id "document has no title")))))

(defn valid? [idx]
  (not-any? #(= :error (:search/severity %)) (problems idx)))
