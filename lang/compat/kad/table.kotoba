(ns kad.table
  "The k-bucket routing table — what a DHT *node* keeps, as opposed to the
  shortlist a single lookup keeps.

  `kad.lookup` maintains the peers relevant to one query and forgets them.
  A node has to remember the network between queries, and the structure
  Kademlia uses for that is 256 buckets, one per possible common-prefix length
  with our own ID, each holding at most `k` peers.

  ## Why one bucket per prefix length

  The table holds `k` peers at every distance scale rather than `k` peers
  overall. Bucket 0 covers half the keyspace, bucket 1 a quarter, bucket 255 a
  single point. So the node knows a *few* peers very far away and *many* nearby
  — which is exactly what makes an iterative lookup converge in O(log n) hops
  instead of scanning.

  A table that kept the k globally-closest peers instead would know nothing
  about the far half of the keyspace and could not route a query there at all.

  ## The eviction rule is the interesting part

  When a bucket is full, Kademlia §2.2 does **not** evict the oldest peer to
  make room for the new one. It pings the *least recently seen* peer, and:

  - if it responds, it stays and the **new peer is discarded**
  - only if it fails to respond is it replaced

  That looks backwards until you see what it defends against. Long-lived peers
  are empirically far more likely to stay up another hour than fresh ones, so
  preferring them makes the table more stable. And it makes the table
  **hard to flood**: an attacker who can mint identities cannot push out
  established peers just by showing up, because showing up is not enough — the
  incumbent has to actually be gone.

  A table that evicted on arrival would let anyone replace a node's entire view
  of the network with peers they control. That is the eclipse attack, and this
  rule is the defence, so `note-seen` never evicts on its own: it returns a
  `:probe` instruction and waits to be told the outcome."
  (:require [kad.key :as kkey]))

(def ^:const default-k 20)

(defn create
  "A table for a node whose own DHT key is `self-key` (32 octets — the SHA-256
  of its peer ID, see `kad.key/dht-key`)."
  [self-key & [{:keys [k] :or {k default-k}}]]
  {:table/self self-key
   :table/k k
   :table/buckets {}})

(defn bucket-index
  "Which bucket a peer belongs in: the number of leading bits it shares with
  our own key. Identical keys — us — have no bucket, and `nil` says so rather
  than returning 256 and quietly putting the node in its own table."
  [table peer-key]
  (let [n (kkey/common-prefix-length (:table/self table) peer-key)]
    (when (< n kkey/key-bits) n)))

(defn peers-in
  [table idx]
  (get-in table [:table/buckets idx] []))

(defn all-peers
  [table]
  (into [] (mapcat val) (:table/buckets table)))

(defn find-peer
  [table peer-id]
  (first (filter #(= peer-id (:peer/id %)) (all-peers table))))

;; ── seeing a peer ─────────────────────────────────────────────────────────

(defn note-seen
  "Record contact with a peer at `now-ms`.

  Returns `{:table t}` when the table absorbed it, or
  `{:table t :probe peer}` when the bucket was full — the caller must ping
  that peer and report back with `probe-alive` or `probe-dead`.

  It does **not** evict on its own. Eviction on arrival is what makes a routing
  table floodable, and the whole point of Kademlia's rule is that a newcomer
  cannot displace an incumbent that is still answering."
  [table {:keys [peer/id] :as peer} now-ms]
  (let [idx (bucket-index table (:peer/dht-key peer))]
    (cond
      (nil? idx) {:table table}                    ; that is us

      :else
      (let [bucket (peers-in table idx)
            existing (first (filter #(= id (:peer/id %)) bucket))]
        (cond
          ;; Already known: move it to the most-recently-seen end. The order in
          ;; the bucket IS the recency record, which is why there is no
          ;; timestamp to fall out of sync with it.
          existing
          {:table (assoc-in table [:table/buckets idx]
                            (conj (vec (remove #(= id (:peer/id %)) bucket))
                                  (assoc peer :peer/last-seen now-ms)))}

          (< (count bucket) (:table/k table))
          {:table (assoc-in table [:table/buckets idx]
                            (conj (vec bucket) (assoc peer :peer/last-seen now-ms)))}

          :else
          {:table table
           :probe (first bucket)                   ; least recently seen
           :pending (assoc peer :peer/last-seen now-ms)})))))

(defn probe-alive
  "The probed peer answered. It moves to the most-recently-seen end and the
  **newcomer is discarded** — Kademlia §2.2. Preferring the incumbent is what
  makes the table stable and hard to flood."
  [table peer now-ms]
  (let [idx (bucket-index table (:peer/dht-key peer))
        bucket (peers-in table idx)]
    (assoc-in table [:table/buckets idx]
              (conj (vec (remove #(= (:peer/id peer) (:peer/id %)) bucket))
                    (assoc peer :peer/last-seen now-ms)))))

(defn probe-dead
  "The probed peer did not answer, so it is replaced by the pending one."
  [table dead pending now-ms]
  (let [idx (bucket-index table (:peer/dht-key pending))
        bucket (peers-in table idx)]
    (assoc-in table [:table/buckets idx]
              (conj (vec (remove #(= (:peer/id dead) (:peer/id %)) bucket))
                    (assoc pending :peer/last-seen now-ms)))))

(defn remove-peer
  [table peer-id]
  (update table :table/buckets
          (fn [bs] (into {} (map (fn [[i ps]]
                                   [i (vec (remove #(= peer-id (:peer/id %)) ps))]))
                         bs))))

;; ── routing ───────────────────────────────────────────────────────────────

(defn closest
  "The `n` peers in the table closest to `target`.

  Searches the whole table rather than only the target's own bucket. The bucket
  index tells you where a peer *lives*, not that it is the closest thing we
  know — a nearly-empty bucket next to a full one is ordinary, and answering
  from one bucket returns fewer peers than we have and sends the lookup
  wandering."
  [table target & [n]]
  (vec (take (or n (:table/k table))
             (kkey/sort-closest target :peer/dht-key (all-peers table)))))

(defn size [table] (count (all-peers table)))

(defn bucket-sizes
  "Occupancy per bucket — the shape of the table. Useful in an operator's
  status page, where a table that is full at bucket 0 and empty everywhere else
  means the node is only talking to distant peers and its lookups will be slow."
  [table]
  (into (sorted-map)
        (keep (fn [[i ps]] (when (seq ps) [i (count ps)])))
        (:table/buckets table)))

(defn stale-peers
  "Peers not seen since `cutoff-ms`. Kademlia refreshes a bucket by looking up
  a random key inside it; this is the input to deciding which buckets need it."
  [table cutoff-ms]
  (filterv #(< (or (:peer/last-seen %) 0) cutoff-ms) (all-peers table)))
