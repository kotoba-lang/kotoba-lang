(ns provider.dataspace-kgraph
  "kgraph/incidence backing for provider.dataspace.

  Guest ABI is unchanged (`:dataspace/transact`, wire 24, `:document`).
  Assertions, observers, and live facets are EAV datoms through
  `kotoba.kgraph` (`assert-datom` / `retract-datom` / `query` /
  `get-objects`). `get-objects` is the entity incidence index; `query`
  is the attribute/value incidence join. No new graph layer. No D1.
  Identity CID is not used; these datoms are in-process only.

  Entity ids are tagged so assertion 1 and facet 1 do not share an EAV
  entity: `[:assertion n]`, `[:observer n]`, `[:facet n]`. Guest facet
  handles stay numeric i64.

  kgraph-assert! the guest i64 host-op is not this adapter. This ns is
  the host inject that talks to the kgraph library the host-op also uses."
  (:require [kotoba.kgraph :as kgraph]
            [provider.dataspace-match :as match]
            [provider.dataspace-store :as store]))

(def value-attr :dataspace.assertion/value)
(def facet-attr :dataspace.assertion/facet)
(def observer-pattern-attr :dataspace.observer/pattern)
(def observer-facet-attr :dataspace.observer/facet)
(def observer-mailbox-attr :dataspace.observer/mailbox)
(def facet-live-attr :dataspace.facet/live)

(defn- assertion-e [n] [:assertion n])
(defn- observer-e [n] [:observer n])
(defn- facet-e [n] [:facet n])

(defn- retract-entity [datoms e]
  (reduce kgraph/retract-datom datoms (kgraph/get-objects datoms e)))

(defn- query-eav [datoms find where]
  (kgraph/query datoms {:find find :where where}))

(defn- assertion-rows [datoms]
  (query-eav datoms
             ['?e '?v '?f]
             [['?e value-attr '?v]
              ['?e facet-attr '?f]]))

(defn- observer-rows [datoms]
  (query-eav datoms
             ['?e '?p '?f]
             [['?e observer-pattern-attr '?p]
              ['?e observer-facet-attr '?f]]))

(defn- tagged-id [e]
  (when (and (vector? e) (= 2 (count e)))
    (second e)))

(defn- attr-value [datoms e attr]
  (some (fn [[_ a v]] (when (= a attr) v))
        (kgraph/get-objects datoms e)))

(defn- replace-attr [datoms e attr value]
  (let [old (filterv (fn [[_ a]] (= a attr))
                     (kgraph/get-objects datoms e))]
    (-> (reduce kgraph/retract-datom datoms old)
        (kgraph/assert-datom [e attr value]))))

(defn- observer-mailbox [datoms e]
  (vec (or (attr-value datoms e observer-mailbox-attr) [])))

(defn- apply-q [datoms request]
  (case (:op request)
    :counts {:assertions (count (assertion-rows datoms))
             :observers (count (observer-rows datoms))
             :facets (count (query-eav datoms ['?e] [['?e facet-live-attr true]]))}
    :facet-live?
    (boolean (seq (kgraph/get-objects datoms (facet-e (:id request)))))
    :assertions (mapv (fn [[e value facet]]
                        {:id (tagged-id e) :value value :facet facet})
                      (assertion-rows datoms))
    :observers (mapv (fn [[e pattern facet]]
                       {:id (tagged-id e)
                        :pattern pattern
                        :facet facet
                        :mailbox (observer-mailbox datoms e)})
                     (observer-rows datoms))
    (throw (ex-info "dataspace kgraph store unknown q"
                    {:phase :dataspace-kgraph :op (:op request)}))))

(defn- apply-tx [datoms ids tx]
  (case (:op tx)
    :assert
    (let [n (:next-assertion ids)
          e (assertion-e n)
          facet-id (:facet tx)
          assertion (:value tx)
          notices (store/match-observers
                   (mapv (fn [[_ pattern facet]]
                           {:pattern pattern :facet facet})
                         (observer-rows datoms))
                   assertion)
          datoms (-> datoms
                     (kgraph/assert-datom [e value-attr assertion])
                     (kgraph/assert-datom [e facet-attr facet-id]))
          datoms (reduce (fn [ds [oe pattern _facet]]
                           (if-let [b (match/match pattern assertion)]
                             (replace-attr ds oe observer-mailbox-attr
                                           (conj (observer-mailbox ds oe)
                                                 (store/notice assertion b)))
                             ds))
                         datoms
                         (observer-rows datoms))]
      {:datoms datoms
       :ids (update ids :next-assertion inc)
       :result {:id n :notices notices}})

    :retract
    (let [assertion (:value tx)
          rows (query-eav datoms
                          ['?e]
                          [['?e value-attr assertion]
                           ['?e facet-attr (:facet tx)]])
          eids (mapv first rows)
          datoms (reduce retract-entity datoms eids)
          datoms (if (seq eids)
                   (reduce (fn [ds [oe pattern _facet]]
                             (if-let [b (match/match pattern assertion)]
                               (replace-attr ds oe observer-mailbox-attr
                                             (conj (observer-mailbox ds oe)
                                                   (store/notice :retract assertion b)))
                               ds))
                           datoms
                           (observer-rows datoms))
                   datoms)]
      {:datoms datoms
       :ids ids
       :result {:removed (count eids)}})

    :observe
    (let [facet-id (:facet tx)
          pattern (:pattern tx)
          existing (query-eav datoms
                              ['?e]
                              [['?e observer-pattern-attr pattern]
                               ['?e observer-facet-attr facet-id]])]
      (if (seq existing)
        (let [e (ffirst existing)
              notices (observer-mailbox datoms e)]
          {:datoms (replace-attr datoms e observer-mailbox-attr [])
           :ids ids
           :result {:id (tagged-id e) :notices notices}})
        (let [n (:next-observer ids)
              e (observer-e n)
              notices (store/current-set-notices
                       (mapv (fn [[_e value facet]]
                               {:value value :facet facet})
                             (assertion-rows datoms))
                       pattern)]
          {:datoms (-> datoms
                       (kgraph/assert-datom [e observer-pattern-attr pattern])
                       (kgraph/assert-datom [e observer-facet-attr facet-id])
                       (kgraph/assert-datom [e observer-mailbox-attr []]))
           :ids (update ids :next-observer inc)
           :result {:id n :notices notices}})))

    :facet-enter
    (let [n (:next-facet ids)
          e (facet-e n)]
      {:datoms (kgraph/assert-datom datoms [e facet-live-attr true])
       :ids (update ids :next-facet inc)
       :result {:id n}})

    :facet-leave
    (let [facet-id (:id tx)
          owned (mapv first
                      (query-eav datoms
                                 ['?e]
                                 [['?e facet-attr facet-id]]))
          obs (mapv first
                    (query-eav datoms
                               ['?e]
                               [['?e observer-facet-attr facet-id]]))
          datoms (reduce retract-entity datoms owned)
          datoms (reduce retract-entity datoms obs)
          datoms (retract-entity datoms (facet-e facet-id))]
      {:datoms datoms
       :ids ids
       :result {:removed (count owned)}})

    (throw (ex-info "dataspace kgraph store unknown tx"
                    {:phase :dataspace-kgraph :op (:op tx)}))))

(defn store
  "In-process kgraph EAV store. Inject as `:store` into provider.dataspace."
  []
  (let [state (atom {:datoms []
                     :ids {:next-assertion 1 :next-facet 1 :next-observer 1}})]
    {:q (fn [request] (apply-q (:datoms @state) request))
     :transact! (fn [tx]
                  (let [out (atom nil)]
                    (swap! state
                           (fn [s]
                             (let [applied (apply-tx (:datoms s) (:ids s) tx)]
                               (reset! out (:result applied))
                               {:datoms (:datoms applied)
                                :ids (:ids applied)})))
                    @out))}))

(defn store?
  [x]
  (store/store? x))
