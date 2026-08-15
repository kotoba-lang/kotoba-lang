(ns kotoba.lang.incidence-replication
  "Bounded anti-entropy replication for content-addressed incidence blocks.

  Replication is monotonic set union, not consensus. Hash-verified orphan
  blocks may be retained while parents are fetched; projection is available
  only after the local parent closure is complete."
  (:require [clojure.set :as set]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.code-identity :as identity]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.signed-readback :as readback]))

(def version 1)
(def default-max-batch 256)
(def default-max-block-bytes (* 1024 1024))

(defprotocol ^:private ReplicationCertificateValue
  (-certificate-info [value]))

(deftype ^:private ReplicationCertificate [info]
  ReplicationCertificateValue
  (-certificate-info [_] info))

(defn- positive-int? [x]
  (and (integer? x) (pos? x)))

(defn replica
  "Create inert replica state for one exact dataspace. Bounds are part of the
  state so replay and anti-entropy apply the same admission policy."
  ([dataspace] (replica dataspace {}))
  ([dataspace {:keys [max-batch max-block-bytes]
               :or {max-batch default-max-batch
                    max-block-bytes default-max-block-bytes}}]
   (when-not (capabilities/non-empty-string? dataspace)
     (throw (ex-info "replica dataspace is invalid"
                     {:problem :replication/dataspace-invalid})))
   (when-not (and (positive-int? max-batch)
                  (positive-int? max-block-bytes))
     (throw (ex-info "replica bounds are invalid"
                     {:problem :replication/bounds-invalid})))
   {:replica/version version
    :replica/dataspace dataspace
    :replica/max-batch max-batch
    :replica/max-block-bytes max-block-bytes
    :replica/blocks {}
    :replica/frontier #{}
    :replica/missing #{}}))

(declare indexes)

(defn state-error
  [state]
  (cond
    (not (map? state))
    {:problem :replication/state-not-a-map}

    (not= #{:replica/version :replica/dataspace :replica/max-batch
            :replica/max-block-bytes :replica/blocks :replica/frontier
            :replica/missing}
          (set (keys state)))
    {:problem :replication/state-fields}

    (not= version (:replica/version state))
    {:problem :replication/version-unsupported}

    (not (capabilities/non-empty-string? (:replica/dataspace state)))
    {:problem :replication/dataspace-invalid}

    (not (and (positive-int? (:replica/max-batch state))
              (positive-int? (:replica/max-block-bytes state))))
    {:problem :replication/bounds-invalid}

    (not (and (map? (:replica/blocks state))
              (every? (fn [[cid entry]]
                        (let [verified (incidence/verify-addressed entry)]
                          (and (:ok? verified)
                               (= cid (:cid verified))
                               (<= (count (incidence/canonical-bytes
                                           (:incidence/block entry)))
                                   (:replica/max-block-bytes state)))))
                      (:replica/blocks state))))
    {:problem :replication/blocks-invalid}

    (not (and (set? (:replica/frontier state))
              (set? (:replica/missing state))))
    {:problem :replication/index-invalid}

    :else
    (let [{:keys [frontier missing]} (indexes (:replica/blocks state))]
      (when-not (and (= frontier (:replica/frontier state))
                     (= missing (:replica/missing state)))
        {:problem :replication/index-mismatch}))))

(defn- indexes
  [blocks]
  (let [known (set (keys blocks))
        parents (into #{} (mapcat #(get-in % [:incidence/block
                                               :incidence/parents]))
                      (vals blocks))]
    {:frontier (set/difference known parents)
     :missing (set/difference parents known)}))

(defn ingest
  "Atomically hash-check and union one bounded batch. Unknown parents are
  recorded as anti-entropy wants rather than causing valid descendants to be
  discarded. No entry is admitted when any batch member is invalid."
  [state entries]
  (if-let [error (state-error state)]
    {:ok? false :reason (:problem error) :error error :replica state}
    (cond
      (not (vector? entries))
      {:ok? false :reason :replication/batch-not-a-vector :replica state}

      (empty? entries)
      {:ok? true :replica state :added #{} :duplicate #{}}

      (> (count entries) (:replica/max-batch state))
      {:ok? false :reason :replication/batch-too-large :replica state}

      :else
      (let [checked
            (mapv (fn [entry]
                    (let [verified (when (map? entry)
                                     (incidence/verify-addressed entry))]
                      (cond
                        (not (:ok? verified))
                        {:problem :replication/entry-invalid
                         :verification verified}

                        (> (count (incidence/canonical-bytes
                                   (:incidence/block entry)))
                           (:replica/max-block-bytes state))
                        {:problem :replication/block-too-large
                         :cid (:cid verified)}

                        :else {:cid (:cid verified) :entry entry})))
                  entries)
            failure (first (filter :problem checked))
            cids (mapv :cid checked)]
        (cond
          failure
          {:ok? false :reason (:problem failure) :error failure :replica state}

          (not= (count cids) (count (set cids)))
          {:ok? false :reason :replication/batch-duplicate :replica state}

          :else
          (let [before (:replica/blocks state)
                merged (reduce (fn [blocks {:keys [cid entry]}]
                                 (assoc blocks cid entry))
                               before checked)
                {:keys [frontier missing]} (indexes merged)
                added (set/difference (set cids) (set (keys before)))
                duplicate (set/intersection (set cids) (set (keys before)))]
            {:ok? true
             :replica (assoc state :replica/blocks merged
                             :replica/frontier frontier
                             :replica/missing missing)
             :added added
             :duplicate duplicate}))))))

(defn inventory-page
  "Return a stable bounded page of known CIDs. CURSOR is the last CID from the
  previous page, or nil. This is exact bounded inventory, not a Bloom filter."
  [state cursor limit]
  (when-let [error (state-error state)]
    (throw (ex-info "invalid replica state" error)))
  (when-not (and (or (nil? cursor) (identity/cid? cursor))
                 (positive-int? limit) (<= limit (:replica/max-batch state)))
    (throw (ex-info "inventory limit is invalid"
                    {:problem :replication/inventory-limit-invalid})))
  (let [all (sort (keys (:replica/blocks state)))
        remaining (if cursor (drop-while #(<= (compare % cursor) 0) all) all)
        page (vec (take limit remaining))
        more? (seq (drop limit remaining))]
    {:replication/cids page
     :replication/next-cursor (when more? (peek page))}))

(defn wanted
  "Choose a bounded deterministic pull set from a peer's advertised CIDs.
  Missing parents are prioritized, then other unknown blocks."
  [state remote-cids limit]
  (when-let [error (state-error state)]
    (throw (ex-info "invalid replica state" error)))
  (when-not (and (set? remote-cids) (every? identity/cid? remote-cids)
                 (positive-int? limit) (<= limit (:replica/max-batch state)))
    (throw (ex-info "remote inventory is invalid"
                    {:problem :replication/remote-inventory-invalid})))
  (let [unknown (set/difference remote-cids
                                (set (keys (:replica/blocks state))))
        parents (sort (set/intersection unknown (:replica/missing state)))
        others (sort (set/difference unknown (set parents)))]
    (vec (take limit (concat parents others)))))

(defn export-batch
  "Export exact requested known entries, bounded by local policy. Unknown CIDs
  are omitted and can be retried against another peer."
  [state requested]
  (when-let [error (state-error state)]
    (throw (ex-info "invalid replica state" error)))
  (when-not (and (vector? requested)
                 (<= (count requested) (:replica/max-batch state))
                 (every? identity/cid? requested))
    (throw (ex-info "replication export request is invalid"
                    {:problem :replication/export-request-invalid})))
  (into [] (keep #((:replica/blocks state) %)) requested))

(defn projection
  "Project only a complete local closure. Orphan retention is replication
  progress, not permission to expose partial state as authoritative."
  [state]
  (if-let [error (state-error state)]
    {:ok? false :reason (:problem error) :error error}
    (if (seq (:replica/missing state))
      {:ok? false :reason :replication/incomplete
       :missing (:replica/missing state)}
      (incidence/project (vals (:replica/blocks state))))))

(defn certify-readback-quorum
  "Mint an opaque availability certificate from distinct verified peers that
  signed fresh readback claims for the same org/dataspace/incidence."
  [admissions threshold]
  (when-not (and (vector? admissions) (seq admissions)
                 (positive-int? threshold))
    (throw (ex-info "replication quorum input is invalid"
                    {:problem :replication/quorum-input-invalid})))
  (when-not (every? readback/verified-readback? admissions)
    (throw (ex-info "verified readback admissions required"
                    {:problem :replication/readback-not-verified})))
  (let [infos (mapv readback/verified-readback-description admissions)
        common-keys [:receipt/dataspace :receipt/incidence-cid
                     :receipt/constitution-cid :receipt/issuer]
        common (select-keys (first infos) common-keys)
        peers (set (map :receipt/peer infos))]
    (cond
      (not (every? #(= common (select-keys % common-keys)) infos))
      (throw (ex-info "readback claims do not bind the same subject"
                      {:problem :replication/readback-mismatch}))

      (not= (count peers) (count infos))
      (throw (ex-info "one peer cannot count more than once"
                      {:problem :replication/duplicate-peer}))

      (< (count peers) threshold)
      (throw (ex-info "readback quorum is below threshold"
                      {:problem :replication/below-quorum
                       :required threshold :actual (count peers)}))

      :else
      (ReplicationCertificate.
       (assoc common
              :replication/threshold threshold
              :replication/peers peers
              :replication/receipt-cids
              (set (map :receipt/statement-cid infos)))))))

(defn replication-certificate?
  [x]
  (satisfies? ReplicationCertificateValue x))

(defn certificate-description
  "Return inert audit metadata; it is not itself a certificate on re-read."
  [certificate]
  (when (replication-certificate? certificate)
    (-certificate-info certificate)))
