(ns kotoba.protocol.graph
  "Reference algebra for the two link kinds (ADR-2608145200).

  Not a network. Hosts persist nodes (L0) and the action log (L1).
  inga orders the log. This ns is the closed semantics:

    :merkle  — edge lives inside the node. Same CID + different links is
               :cid-mismatch. To add a merkle child the host hashes a
               *new* node (parent CID changes).
    :action  — Holochain CreateLink / datom assertion. Nodes stay equal.
               Only the signed log grows. Parent CID does not change.

  Hashing bytes → CID is the host's job (see kotoba.protocol.cid).
  L2 graph CID is a `chain.core/commit!` of `log-state`. This ns does
  not hash and does not depend on chain — `commit-log` takes the
  hasher as `commit-fn`:

    (fn [prev-cid state]
      (chain.core/commit! put! get-fn state prev-cid))

  That CID is identity (typically dag-cbor). kotobase archive PUT
  accepts only raw CIDv1; Location is the raw CID of the same bytes
  (ADR-2608148200). `:kotoba.graph/head` is naming, not identity."
  (:require [kotoba.protocol.layers :as layers]
            [kotoba.protocol.vocab :as vocab]))

(defn store
  []
  {:nodes {}
   :actions []
   :log-head 0
   :graph-cid nil
   :log-dirty? false})

(defn- cid-set [xs]
  (into #{} xs))

(defn put-node
  "Put an output-addressed node. CID is identity of body + merkle-links.

  Re-putting the same CID with a different body or link set is
  :cid-mismatch — that would be a merkle rewrite pretending not to be one."
  [st {:keys [cid body merkle-links]}]
  (cond
    (:error st) st
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    (some (complement vocab/cid?) (or merkle-links #{}))
    {:error :invalid-merkle-link
     :value (first (remove vocab/cid? merkle-links))}
    :else
    (let [norm {:cid cid
                :body body
                :merkle-links (cid-set merkle-links)}
          existing (get-in st [:nodes cid])]
      (if (and existing (not= existing norm))
        {:error :cid-mismatch :cid cid
         :existing existing :attempted norm}
        (assoc-in st [:nodes cid] norm)))))

(defn merkle-child
  "The node that would result from embedding `to` inside `from`.

  Does not put. Host must hash and `put-node` under a *new* CID.
  `:put-under-same-cid?` is always false — that is the merkle invariant."
  [from-node to]
  (cond
    (not (vocab/cid? to)) {:error :invalid-cid :value to}
    (not (map? from-node)) {:error :not-a-node}
    :else
    {:body (:body from-node)
     :merkle-links (conj (cid-set (:merkle-links from-node)) to)
     :mutates-parent? true
     :parent-before (:cid from-node)
     :put-under-same-cid? false
     :kind :merkle
     :stored-in (get-in layers/link-kinds [:merkle :stored-in])}))

(defn create-link
  "Overlay edge. Parent node bytes (and CID) do not change.

  `from` / `to` are CIDs. `to` need not be in the store (Holochain can
  link to a hash that is not locally held). Author is did:key when present."
  [st {:keys [from to tag author]}]
  (cond
    (:error st) st
    (not (vocab/cid? from)) {:error :invalid-cid :value from}
    (not (vocab/cid? to)) {:error :invalid-cid :value to}
    (and (some? author) (not (vocab/did-key? author)))
    {:error :invalid-author :value author}
    :else
    (let [seq* (inc (:log-head st))
          action {:kind :action
                  :from from
                  :to to
                  :tag tag
                  :author author
                  :seq seq*
                  :mutates-parent? false
                  :stored-in (get-in layers/link-kinds [:action :stored-in])}]
      (-> st
          (update :actions conj action)
          (assoc :log-head seq* :log-dirty? true)))))

(defn node
  [st cid]
  (get-in st [:nodes cid]))

(defn neighbors
  "Edges of both kinds from `cid`. Merkle from the node, action from the log."
  [st cid]
  {:merkle (or (:merkle-links (node st cid)) #{})
   :action (into #{} (comp (filter #(= cid (:from %))) (map :to))
                 (:actions st))})

(defn nodes-unchanged?
  "True iff overlay write left the node map equal. The CreateLink invariant."
  [before after]
  (= (:nodes before) (:nodes after)))

(defn walk
  "BFS from `start`. `:kinds` defaults to both. Returns visited CIDs in
  encounter order, start first. Does not follow an unknown kind."
  ([st start] (walk st start nil))
  ([st start {:keys [kinds] :or {kinds #{:merkle :action}}}]
   (loop [queue (vec [start])
          seen #{}
          out []]
     (if (empty? queue)
       out
       (let [cid (first queue)
             queue (subvec queue 1)]
         (if (contains? seen cid)
           (recur queue seen out)
           (let [nb (neighbors st cid)
                 nxt (into []
                           (comp (filter kinds)
                                 (mapcat (fn [k] (sort (nb k)))))
                           [:merkle :action])]
             (recur (into queue nxt)
                    (conj seen cid)
                    (conj out cid)))))))))

(defn action-datoms
  "Each overlay edge as a :kotoba.link/* entity. L1 fact plane.
  The action itself is output-addressed (ActionHash) once the host hashes it."
  [st]
  (mapv (fn [{:keys [from to tag author seq]}]
          (cond-> {:kotoba.link/from from
                   :kotoba.link/to to
                   :kotoba.link/kind "action"
                   :kotoba.link/seq seq}
            tag (assoc :kotoba.link/tag tag)
            author (assoc :kotoba.link/author author)))
        (:actions st)))

(defn log-state
  "Opaque `state` for chain.core/commit!. String keys so DAG-CBOR encode
  does not depend on keyword round-trip. This is the action log, not the
  L0 node map — merkle puts do not appear here."
  [st]
  {"seq" (:log-head st)
   "actions" (mapv (fn [{:keys [from to tag author seq]}]
                     (cond-> {"from" from "to" to "seq" seq}
                       tag (assoc "tag" tag)
                       author (assoc "author" author)))
                   (:actions st))})

(defn commit-log
  "Seal the dirty action log as an L2 graph CID.

  `commit-fn` is `(fn [prev-cid log-state] cid)`. Production binds
  `chain.core/commit!`. A dirty log that hashes to the previous CID is
  `:graph-cid-unchanged` — the hasher lied or the log did not change.
  Idempotent when not dirty: does not call `commit-fn`."
  [st commit-fn]
  (cond
    (:error st) st
    (not (:log-dirty? st)) st
    (not (ifn? commit-fn)) {:error :commit-fn-required}
    :else
    (let [prev (:graph-cid st)
          cid (commit-fn prev (log-state st))]
      (cond
        (not (vocab/cid? cid)) {:error :invalid-graph-cid :value cid}
        (and (some? prev) (= prev cid)) {:error :graph-cid-unchanged :cid cid}
        :else (assoc st :graph-cid cid :log-dirty? false)))))

(defn snapshot
  "L2 entity for the sealed log. nil until the first successful commit-log."
  [st]
  (when (and (vocab/cid? (:graph-cid st)) (not (:log-dirty? st)))
    {:kotoba.graph/name "overlay"
     :kotoba.graph/cid (:graph-cid st)}))
