(ns docs.validate
  (:require [clojure.string :as str]
            [docs.model :as model]))

(defn problem [severity code id msg]
  {:docs/severity severity :docs/code code :docs/id id :docs/msg msg})

(defn problems [doc]
  (let [ids (map :docs/id (:docs/blocks doc))]
    (vec
     (concat
      (when (or (nil? (:docs/title doc)) (= "" (:docs/title doc)))
        [(problem :warning :document/missing-title (:docs/id doc) "document has no title")])
      (for [b (:docs/blocks doc)
            :when (not (contains? model/block-kinds (:docs/kind b)))]
        (problem :error :block/unknown-kind (:docs/id b) "unknown block kind"))
      (for [[id n] (frequencies ids)
            :when (> n 1)]
        (problem :error :block/duplicate-id id "duplicate block id"))
      (for [b (:docs/blocks doc)
            run (:docs/text-runs b)
            :when (or (nil? (:docs/from run))
                      (nil? (:docs/to run))
                      (neg? (:docs/from run))
                      (< (:docs/to run) (:docs/from run)))]
        (problem :error :text-run/invalid (:docs/id b) "invalid text run range"))
      ;; A picture nothing can draw. Not a broken document — the block is
      ;; still a block and the rest of it is fine — but every writer will
      ;; skip it, and a reader who put a photograph in and got nothing back
      ;; should be told which one.
      (for [b (:docs/blocks doc)
            :when (and (= :image (:docs/kind b)) (nil? (model/image-data b)))]
        (problem :warning :image/undrawable (:docs/id b)
                 "image has no data, or a media type this does not carry"))
      (for [b (:docs/blocks doc)
            :when (and (= :image (:docs/kind b))
                       (model/image-data b)
                       (str/blank? (str (:docs/alt b))))]
        (problem :warning :image/no-alt-text (:docs/id b)
                 "image has no alternative text"))
      (for [comment (:docs/comments doc)
            :when (or (nil? (:docs/id comment))
                      (nil? (:docs/anchor comment)))]
        (problem :error :comment/invalid (:docs/id comment) "comment requires id and anchor"))
      (for [suggestion (:docs/suggestions doc)
            :when (or (nil? (:docs/id suggestion))
                      (nil? (:docs/op suggestion)))]
        (problem :error :suggestion/invalid (:docs/id suggestion) "suggestion requires id and op"))))))

(defn valid? [doc]
  (not-any? #(= :error (:docs/severity %)) (problems doc)))
