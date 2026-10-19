(ns provider.dataspace-store
  "Injectable persistence for provider.dataspace.

  Smallest host inject: `{:q :transact!}` (capability host, not an actor).
  Default is this in-memory reference. A second store (kgraph EAV) must
  honour the same ops so guest `:dataspace/transact` ABI does not change.

  `:q` requests:
    {:op :counts}
    {:op :facet-live? :id n}
    {:op :assertions}  ; [{:id :value :facet} ...]
    {:op :observers}   ; [{:id :pattern :facet :mailbox} ...]

  `:transact!` requests:
    {:op :assert :value v :facet f} -> {:id n :notices [binding-map ...]}
    {:op :retract :value v :facet f} -> {:removed n}
      ;; matching retract enqueues a :retract notice on observers.
    {:op :observe :pattern p :facet f} -> {:id n :notices [notice ...]}
      ;; upsert by facet+pattern. First observe replays already-present
      ;; matching assertions as :assert :document notices (Syndicate current-set).
      ;; Re-observe drains that observer's mailbox of later asserts/retracts.
    {:op :facet-enter} -> {:id n}
    {:op :facet-leave :id n} -> {:removed n}

  Facet 0 is the implicit root and is always live. Positive facet ids are
  created by :facet-enter. :facet-leave retracts that facet's assertions
  and drops its observers including undelivered notice mailboxes.

  Matching asserts enqueue `{:kind :assert :assertion v :bindings m}`.
  Matching retracts enqueue `{:kind :retract :assertion v :bindings m}`
  on the same mailbox. First observe of a facet+pattern also returns
  `:assert` notices for each already-present match (current-set replay),
  without leaving those notices in the mailbox. Delivery is in-process
  and inert; there is no guest callback."
  (:require [provider.dataspace-match :as match]))

(def store-keys #{:q :transact!})

(defn store?
  [x]
  (and (map? x)
       (= store-keys (set (keys x)))
       (ifn? (:q x))
       (ifn? (:transact! x))))

(defn match-observers
  "Bindings for observers whose pattern matches ASSERTION."
  [observers assertion]
  (into []
        (keep (fn [observer]
                (match/match (:pattern observer) assertion)))
        observers))

(defn notice
  "Inert document payload delivered to an observer.

  KIND is `:assert` or `:retract` (Syndicate observed-assertion events).
  Same mailbox; the field distinguishes the event. Default is `:assert`
  so current-set replay stays an assert notice."
  ([assertion bindings]
   (notice :assert assertion bindings))
  ([kind assertion bindings]
   {:kind kind :assertion assertion :bindings (into {} bindings)}))

(defn deliver-to-observers
  "Enqueue a notice on each matching observer. Return [observers' bindings].

  KIND is `:assert` (default) or `:retract`."
  ([observers assertion]
   (deliver-to-observers observers :assert assertion))
  ([observers kind assertion]
   (let [bindings (match-observers observers assertion)
         observers' (mapv (fn [observer]
                            (if-let [b (match/match (:pattern observer) assertion)]
                              (update observer :mailbox (fnil conj [])
                                      (notice kind assertion b))
                              observer))
                          observers)]
     [observers' bindings])))

(defn current-set-notices
  "Already-present matching assertions as observe-time :document notices.

  Syndicate current-set replay: registering an observer delivers the live
  matching set now, not only later asserts. Facet-leave still drops."
  [assertions pattern]
  (into []
        (keep (fn [cell]
                (when-let [b (match/match pattern (:value cell))]
                  (notice (:value cell) b))))
        assertions))

(defn- observer-for
  [observers facet-id pattern]
  (some (fn [observer]
          (when (and (= facet-id (:facet observer))
                     (= pattern (:pattern observer)))
            observer))
        observers))

(defn- empty-state []
  {:assertions []
   :observers []
   :facets {}
   :next-assertion 1
   :next-facet 1
   :next-observer 1})

(defn- retract-owned-value
  "Remove VALUE only from the exact FACET-ID that published it."
  [assertions facet-id value]
  (reduce (fn [[kept removed] cell]
            (if (and (= facet-id (:facet cell))
                     (= value (:value cell)))
              [kept (conj removed (:id cell))]
              [(conj kept cell) removed]))
          [[] #{}]
          assertions))

(defn- apply-tx [state tx]
  (case (:op tx)
    :assert
    (let [assertion-id (:next-assertion state)
          facet-id (:facet tx)
          cell {:id assertion-id :value (:value tx) :facet facet-id}
          [observers' notices] (deliver-to-observers (:observers state)
                                                     (:value tx))]
      {:state (-> state
                  (update :assertions conj cell)
                  (assoc :observers observers')
                  (update :next-assertion inc)
                  (cond-> (pos? facet-id)
                    (update-in [:facets facet-id :assertions]
                               (fnil conj #{}) assertion-id)))
       :result {:id assertion-id :notices notices}})

    :retract
    (let [[kept removed-ids]
          (retract-owned-value (:assertions state) (:facet tx) (:value tx))
          [observers' _]
          (if (seq removed-ids)
            (deliver-to-observers (:observers state) :retract (:value tx))
            [(:observers state) []])]
      {:state (cond-> (assoc state :assertions kept :observers observers')
                (pos? (:facet tx))
                (update-in [:facets (:facet tx) :assertions]
                           #(apply disj (or % #{}) removed-ids)))
       :result {:removed (count removed-ids)}})

    :observe
    (let [facet-id (:facet tx)
          pattern (:pattern tx)]
      (if-let [existing (observer-for (:observers state) facet-id pattern)]
        (let [oid (:id existing)
              notices (vec (:mailbox existing))]
          {:state (assoc state :observers
                         (mapv (fn [observer]
                                 (if (= oid (:id observer))
                                   (assoc observer :mailbox [])
                                   observer))
                               (:observers state)))
           :result {:id oid :notices notices}})
        (let [oid (:next-observer state)
              notices (current-set-notices (:assertions state) pattern)
              observer {:id oid :pattern pattern :facet facet-id :mailbox []}]
          {:state (-> state
                      (update :observers conj observer)
                      (update :next-observer inc)
                      (cond-> (pos? facet-id)
                        (update-in [:facets facet-id :observers]
                                   (fnil conj []) oid)))
           :result {:id oid :notices notices}})))

    :facet-enter
    (let [fid (:next-facet state)]
      {:state (-> state
                  (assoc-in [:facets fid] {:assertions #{} :observers []})
                  (update :next-facet inc))
       :result {:id fid}})

    :facet-leave
    (let [facet-id (:id tx)
          facet (get-in state [:facets facet-id])
          owned (:assertions facet)
          obs-ids (set (:observers facet))
          [kept n]
          (reduce (fn [[acc n] cell]
                    (if (contains? owned (:id cell))
                      [acc (inc n)]
                      [(conj acc cell) n]))
                  [[] 0]
                  (:assertions state))]
      {:state (-> state
                  (assoc :assertions kept)
                  (update :observers
                          (fn [obs]
                            (into [] (remove #(contains? obs-ids (:id %)))
                                  obs)))
                  (update :facets dissoc facet-id))
       :result {:removed n}})

    (throw (ex-info "dataspace store unknown tx"
                    {:phase :dataspace-store :op (:op tx)}))))

(defn- apply-q [state request]
  (case (:op request)
    :counts {:assertions (count (:assertions state))
             :observers (count (:observers state))
             :facets (count (:facets state))}
    :facet-live? (contains? (:facets state) (:id request))
    :assertions (:assertions state)
    :observers (:observers state)
    (throw (ex-info "dataspace store unknown q"
                    {:phase :dataspace-store :op (:op request)}))))

(defn memory-store
  "In-memory reference store. Default backing of provider.dataspace."
  []
  (let [state (atom (empty-state))]
    {:q (fn [request] (apply-q @state request))
     :transact! (fn [tx]
                  (let [out (atom nil)]
                    (swap! state
                           (fn [s]
                             (let [{:keys [state result]} (apply-tx s tx)]
                               (reset! out result)
                               state)))
                    @out))}))
