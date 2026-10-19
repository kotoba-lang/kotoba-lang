(ns drive.validate
  (:require [drive.model :as model]))

(defn problem [severity code id msg]
  {:drive/severity severity :drive/code code :drive/id id :drive/msg msg})

(defn problems [d]
  (let [ids (set (keys (:drive/items d)))]
    (vec
     (concat
      (for [[id item] (:drive/items d)
            :when (not (contains? model/item-kinds (:drive/kind item)))]
        (problem :error :item/unknown-kind id "unknown drive item kind"))
      (for [[id item] (:drive/items d)
            child (:drive/children item)
            :when (not (contains? ids child))]
        (problem :error :folder/dangling-child id "folder child does not exist"))))))

(defn valid? [d]
  (not-any? #(= :error (:drive/severity %)) (problems d)))
