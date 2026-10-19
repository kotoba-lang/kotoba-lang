(ns kotobase-peer.datalog-materialization
  "Deprecated engine-bound facade for `kotobase.projection.datalog`."
  (:require [kotobase-peer.core :as peer]
            [kotobase.projection.datalog :as impl]))

(def frontier-work-format impl/frontier-work-format)
(def frontier-work-version impl/frontier-work-version)
(def datalog-var? impl/datalog-var?)
(def positive-conjunctive-query? impl/positive-conjunctive-query?)
(def bounded-single-clause-query? impl/bounded-single-clause-query?)
(def change-bindings impl/change-bindings)
(def change-frontier-seeds impl/change-frontier-seeds)
(def clause-lookup impl/clause-lookup)
(def unify-datom impl/unify-datom)
(def frontier-next-bindings impl/frontier-next-bindings)
(def binding->wire impl/binding->wire)
(def wire->binding impl/wire->binding)
(def decode-frontier-work impl/decode-frontier-work)
(def build-frontier-work-chain impl/build-frontier-work-chain)
(def frontier-step-plan impl/frontier-step-plan)

(defn affected-query-results [options]
  (impl/affected-query-results (assoc options :query-fn peer/query)))

(defn maintain-query-delta [options]
  (impl/maintain-query-delta (assoc options :query-fn peer/query)))

(defn refresh-plan [options]
  (impl/refresh-plan (assoc options
                            :query-fn peer/query
                            :transact-effective-fn peer/transact-effective)))
