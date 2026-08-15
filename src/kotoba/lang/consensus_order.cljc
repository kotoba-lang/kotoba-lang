(ns kotoba.lang.consensus-order
  "Adapter boundary between incidence replication and external total order.

  Incidence replication remains monotonic set union. An Inga or other consensus
  implementation is admitted only through an injected verifier which binds a
  certificate to one exact dataspace, height, parent, commit ID, and CID order."
  (:require [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.code-identity :as identity]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-replication :as replication]))

(def profile "kotoba-consensus-order/v1")

(defprotocol ^:private CommitRegistryValue
  (-commit-state [registry]))

(deftype ^:private CommitRegistry [state]
  CommitRegistryValue
  (-commit-state [_] state))

(defprotocol ^:private OrderedCommitValue
  (-commit-info [commit]))

(deftype ^:private OrderedCommit [info]
  OrderedCommitValue
  (-commit-info [_] info))

(defn commit-registry [] (CommitRegistry. (atom {})))
(defn commit-registry? [x] (satisfies? CommitRegistryValue x))
(defn ordered-commit? [x] (satisfies? OrderedCommitValue x))
(defn commit-description [x] (when (ordered-commit? x) (-commit-info x)))

(def required-fields
  #{:consensus/profile :consensus/dataspace :consensus/height
    :consensus/parent-id :consensus/commit-id :consensus/entry-cids
    :consensus/certificate})

(defn- envelope-valid? [envelope]
  (and (map? envelope)
       (= required-fields (set (keys envelope)))
       (= profile (:consensus/profile envelope))
       (capabilities/non-empty-string? (:consensus/dataspace envelope))
       (int? (:consensus/height envelope))
       (pos? (:consensus/height envelope))
       (or (nil? (:consensus/parent-id envelope))
           (capabilities/non-empty-string? (:consensus/parent-id envelope)))
       (capabilities/non-empty-string? (:consensus/commit-id envelope))
       (vector? (:consensus/entry-cids envelope))
       (seq (:consensus/entry-cids envelope))
       (= (count (:consensus/entry-cids envelope))
          (count (set (:consensus/entry-cids envelope))))
       (every? identity/cid? (:consensus/entry-cids envelope))))

(defn admit-commit!
  "Verify and atomically admit the next commit for one dataspace.

  VERIFY! is the live adapter to Inga/QC verification. It must return the same
  closed binding with `:consensus/valid? true`; a truthy value is insufficient."
  [registry verify! envelope]
  (when-not (and (commit-registry? registry) (fn? verify!)
                 (envelope-valid? envelope))
    (throw (ex-info "consensus commit envelope is invalid"
                    {:problem :consensus/envelope-invalid})))
  (let [expected (select-keys envelope
                              [:consensus/profile :consensus/dataspace
                               :consensus/height :consensus/parent-id
                               :consensus/commit-id :consensus/entry-cids])
        verified (verify! envelope)]
    (when-not (= (assoc expected :consensus/valid? true) verified)
      (throw (ex-info "consensus verifier did not bind the commit"
                      {:problem :consensus/verification-invalid})))
    (let [dataspace (:consensus/dataspace envelope)
          claimed? (atom false)]
      (swap! (-commit-state registry)
             (fn [state]
               (let [{previous-height :height previous-id :commit-id}
                     (get state dataspace)
                     expected-height (inc (or previous-height 0))
                     expected-parent previous-id]
                 (if (and (= expected-height (:consensus/height envelope))
                          (= expected-parent (:consensus/parent-id envelope)))
                   (do (reset! claimed? true)
                       (assoc state dataspace
                              {:height (:consensus/height envelope)
                               :commit-id (:consensus/commit-id envelope)}))
                   state))))
      (when-not @claimed?
        (throw (ex-info "consensus commit is forked or out of order"
                        {:problem :consensus/order-invalid})))
      (OrderedCommit. expected))))

(defn apply-commit
  "Hash-check and ingest exactly the entries named by an ordered commit.

  The result carries the total order beside the replica; it does not relabel
  set-union replication itself as consensus."
  [replica commit entries]
  (when-not (and (ordered-commit? commit) (vector? entries))
    (throw (ex-info "ordered commit and entry vector required"
                    {:problem :consensus/apply-input-invalid})))
  (let [info (-commit-info commit)
        actual (mapv (fn [entry]
                       (let [verified (incidence/verify-addressed entry)]
                         (when-not (:ok? verified)
                           (throw (ex-info "consensus entry is not addressed"
                                           {:problem :consensus/entry-invalid})))
                         (:cid verified)))
                     entries)]
    (when-not (= (:consensus/entry-cids info) actual)
      (throw (ex-info "entries do not match the certified total order"
                      {:problem :consensus/entry-order-mismatch})))
    (let [result (replication/ingest replica entries)]
      (when-not (:ok? result)
        (throw (ex-info "certified entries violate replica admission"
                        {:problem :consensus/replica-rejected
                         :reason (:reason result)})))
      {:replica (:replica result)
       :consensus/order actual
       :consensus/height (:consensus/height info)
       :consensus/commit-id (:consensus/commit-id info)})))
