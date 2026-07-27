(ns kotoba.lang.capability-catalog-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-catalog :as catalog]))

(deftest semantic-capability-authority-is-closed
  (let [authority (catalog/validate! (catalog/read-authority))
        entries (:capabilities authority)]
    (is (= 16 (count entries)))
    (is (= [4 11 12]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:http/post :llm/generate :storage/transact])))
    (is (= ['http/post 'llm/generate 'storage/transact]
           (mapv #(get-in entries [% :source-operation])
                 [:http/post :llm/generate :storage/transact])))))

(deftest duplicate-wire-id-fails-closed
  (let [authority (catalog/read-authority)]
    (testing "a semantic alias cannot acquire an existing wire identity"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"wire IDs must be unique"
           (catalog/validate!
            (assoc-in authority
                      [:capabilities :storage/transact :compiler-wire-id]
                      11)))))))
