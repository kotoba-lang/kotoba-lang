(ns kotoba.lang.consensus-order-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.consensus-order :as order]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-replication :as replication]))

(def dataspace "dataspace:consensus/example")
(def org-ref (incidence/typed-ref :did "did:key:z6Mkconsensusorg"))
(def root
  (incidence/addressed
   (incidence/incidence :consensus/root {:organization #{org-ref}} {})))
(def child
  (incidence/addressed
   (incidence/incidence :consensus/child {:organization #{org-ref}}
                        {:parents #{(:incidence/cid root)}})))

(defn envelope [height parent commit entries]
  {:consensus/profile order/profile
   :consensus/dataspace dataspace
   :consensus/height height
   :consensus/parent-id parent
   :consensus/commit-id commit
   :consensus/entry-cids (mapv :incidence/cid entries)
   :consensus/certificate {:inga/qc "inert-test-certificate"}})

(defn verifier [input]
  (assoc (select-keys input
                      [:consensus/profile :consensus/dataspace
                       :consensus/height :consensus/parent-id
                       :consensus/commit-id :consensus/entry-cids])
         :consensus/valid? true))

(deftest external-consensus-order-is-opaque-and-applied-exactly
  (let [registry (order/commit-registry)
        first-envelope (envelope 1 nil "inga:block:1" [root child])
        commit (order/admit-commit! registry verifier first-envelope)
        applied (order/apply-commit
                 (replication/replica dataspace) commit [root child])]
    (is (order/ordered-commit? commit))
    (is (false? (order/ordered-commit? (order/commit-description commit))))
    (is (= [(:incidence/cid root) (:incidence/cid child)]
           (:consensus/order applied)))
    (is (= 2 (count (get-in applied [:replica :replica/blocks]))))
    (is (= :consensus/entry-order-mismatch
           (:problem
            (ex-data
             (try (order/apply-commit
                   (replication/replica dataspace) commit [child root])
                  (catch clojure.lang.ExceptionInfo e e))))))
    (is (= 2 (:consensus/height
              (order/commit-description
               (order/admit-commit!
                registry verifier
                (envelope 2 "inga:block:1" "inga:block:2" [child]))))))))

(deftest verifier-forks-and-skipped-heights-fail-closed
  (let [registry (order/commit-registry)
        first-envelope (envelope 1 nil "inga:block:1" [root])]
    (is (= :consensus/verification-invalid
           (:problem
            (ex-data
             (try (order/admit-commit! registry (constantly true) first-envelope)
                  (catch clojure.lang.ExceptionInfo e e))))))
    (order/admit-commit! registry verifier first-envelope)
    (doseq [bad [(envelope 1 nil "inga:fork" [child])
                 (envelope 3 "inga:block:1" "inga:skip" [child])
                 (envelope 2 "inga:wrong-parent" "inga:fork2" [child])]]
      (is (= :consensus/order-invalid
             (:problem
              (ex-data
               (try (order/admit-commit! registry verifier bad)
                    (catch clojure.lang.ExceptionInfo e e)))))))))
