(ns kotoba.lang.dataspace-cap-test
  "Dataspace authority is a capability, not an EDN tag or a copied assertion."
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [kotoba.lang.capability-values :as caps]))

(def now "2026-08-15")
(def resource "dataspace:rooms/a")

(deftest tagged-cap-reader-does-not-mint-a-dataspace
  (is (thrown-with-msg?
       Exception #"No reader function for tag cap"
       (edn/read-string "#cap \"dataspace\"")))
  (is (thrown-with-msg?
       Exception #"No reader function for tag cap-ref"
       (edn/read-string "#cap-ref \"dataspace\""))))

(deftest cap-shaped-map-without-grant-is-denied
  (let [requested (caps/make-cap :dataspace/transact resource
                                 {:provenance ["forged"]})
        denied (caps/intersect-grants
                {:requested requested
                 :cacao-grants []
                 :local-policy {:policy/allow {:dataspace/transact #{resource}}
                                :policy/forbid-wildcard true}
                 :now now})]
    (is (caps/capability? requested))
    (is (= {:denied :empty-intersection} denied))))

(deftest copied-assertion-vector-is-not-a-capability
  (let [assertion [:temperature :room/a 21]
        copied (vec assertion)]
    (is (false? (caps/capability? copied)))
    (is (false? (caps/capability? {:cap/kind :dataspace/transact})))
    (is (false? (caps/capability? resource)))))

(deftest live-grant-intersects-to-the-named-dataspace
  (let [requested (caps/make-cap :dataspace/transact resource)
        granted (caps/intersect-grants
                 {:requested requested
                  :cacao-grants [{:grant/kind :dataspace/transact
                                  :grant/resources #{resource}
                                  :grant/id "ds-1"}]
                  :local-policy {:policy/allow {:dataspace/transact #{resource}}
                                 :policy/forbid-wildcard true}
                  :now now})]
    (is (not (caps/denied? granted)))
    (is (= resource (:cap/resource granted)))
    (is (= :dataspace/transact (:cap/kind granted)))))
