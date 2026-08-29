(ns kotoba.lang.capability-catalog-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-catalog :as catalog]))

(deftest semantic-capability-authority-is-closed
  ;; The count is a deliberate tripwire: adding a capability must be a reviewed
  ;; act, not something that slips in. It went stale when 68e5fb5 ("record W5
  ;; family-3 HTTP ingress dual-runtime") added :http/accept and :http/reply
  ;; without updating it, and CI was red on main from 2026-07-27 09:57 until
  ;; this commit. The wire-id assertion below is what makes the tripwire useful
  ;; rather than merely annoying -- a bump to the count alone cannot hide a
  ;; duplicated or skipped wire identity.
  (let [authority (catalog/validate! (catalog/read-authority))
        entries (:capabilities authority)
        wire-ids (sort (map :compiler-wire-id (vals entries)))]
    (is (= 26 (count entries)))
    (is (= (range 1 (inc (count entries))) wire-ids)
        "wire ids stay contiguous from 1 with no duplicates or gaps")
    (is (= [4 11 12]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:http/post :llm/generate :storage/transact])))
    (is (= ['http/post 'llm/generate 'storage/transact]
           (mapv #(get-in entries [% :source-operation])
                 [:http/post :llm/generate :storage/transact])))
    ;; T8.3 ops kits — wire ids match provider kit :capability :id (ADR-t83-ops-catalog-19-23)
    (is (= [19 20 21 22 23 24]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:fs/transact :process/spawn :secret/get :git/run :entropy/draw
                  :dataspace/transact])))
    (is (= ['fs/transact 'process/spawn 'secret/get 'git/run 'entropy/draw
            'dataspace/transact]
           (mapv #(get-in entries [% :source-operation])
                 [:fs/transact :process/spawn :secret/get :git/run :entropy/draw
                  :dataspace/transact])))
    (is (= [25 26]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:stream/accept :stream/send])))))

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
