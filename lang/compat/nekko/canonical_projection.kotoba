(ns nekko.canonical-projection
  "Deterministic canonical-ref materialization over projected peer events."
  (:require [arrangement.core :as arr]
            [ipld.core :as ipld]
            [nekko.ref-event :as ref-event]))

(defn- one [db subject pred]
  (first (get (arr/entity-attrs db subject) pred)))

(defn- replace-one [db subject pred value]
  (let [db (if-let [old (one db subject pred)]
             (arr/retract-quad db {:s subject :p pred :o old})
             db)]
    (if (nil? value) db (arr/assert-quad db {:s subject :p pred :o value}))))

(defn- canonical-subject [rid ref-name]
  (str "rad.canonical/" (pr-str [rid ref-name])))

(defn materialize
  "Derive and project a canonical ref from the current peer namespaces.

   policy: {:rid :ref :delegates #{did...} :threshold n
            :identity-revision <CID>}

   Returns [db' result], where result is
   {:status :canonical|:unresolved|:split-quorum, :commit ..., :receipt-cid ...}.
   Split quorum is data, not an arrival-order exception: both candidates and
   all input event CIDs remain in the immutable receipt."
  [db {:keys [rid ref delegates threshold identity-revision] :as policy}]
  (when-not (and (string? rid) (string? ref) (set? delegates)
                 (pos-int? threshold) (<= threshold (count delegates))
                 (string? identity-revision))
    (throw (ex-info "invalid canonical ref policy"
                    {:reason :invalid-policy :policy policy})))
  (let [events (ref-event/peer-events db rid ref delegates)
        vote-map (reduce (fn [m event]
                           (update m (get event "new") (fnil conj #{})
                                   (get event "signer")))
                         {} events)
        qualified (->> vote-map
                       (keep (fn [[commit signers]]
                               (when (>= (count signers) threshold) commit)))
                       sort vec)
        status (case (count qualified) 0 :unresolved 1 :canonical :split-quorum)
        commit (when (= status :canonical) (first qualified))
        input-cids (->> events (map ref-event/event-cid) (map str) sort vec)
        receipt {"v" 1 "kind" "rad.canonical.receipt" "rid" rid "ref" ref
                 "identity-revision" identity-revision "threshold" threshold
                 "delegates" (vec (sort delegates)) "event-cids" input-cids
                 "votes" (into (sorted-map)
                                (map (fn [[candidate signers]]
                                       [candidate (vec (sort signers))]))
                                vote-map)
                 "status" (name status) "commit" commit}
        receipt-cid (str (ipld/cid (ipld/encode receipt)))
        subject (canonical-subject rid ref)
        db' (-> db
                (arr/assert-quad {:s receipt-cid :p "rad.canonical.receipt/body" :o receipt})
                (arr/assert-quad {:s receipt-cid :p "rad.canonical.receipt/status" :o (name status)})
                (arr/assert-quad {:s receipt-cid :p "rad.canonical.receipt/rid" :o rid})
                (arr/assert-quad {:s receipt-cid :p "rad.canonical.receipt/ref" :o ref})
                (replace-one subject "rad.canonical/receipt" receipt-cid)
                (replace-one subject "rad.canonical/commit" commit)
                (replace-one subject "rad.canonical/status" (name status)))]
    [db' {:status status :commit commit :candidates qualified
          :receipt-cid receipt-cid :event-cids input-cids}]))

(defn current
  "Current materialized canonical view for RID/ref."
  [db rid ref-name]
  (let [subject (canonical-subject rid ref-name)]
    {:status (some-> (one db subject "rad.canonical/status") keyword)
     :commit (one db subject "rad.canonical/commit")
     :receipt-cid (one db subject "rad.canonical/receipt")}))

(defn receipt [db cid]
  (one db cid "rad.canonical.receipt/body"))

