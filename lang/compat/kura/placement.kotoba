(ns kura.placement
  "Where shards go — the keystone of ADR-2607299200 section 3.

  **Two levels, not one.** An object does not record which node holds which
  shard. It records which *placement group* it belongs to, and the group
  records the node set:

      object-id --(hash)--> placement group    ; a function, never stored
      placement group --(table)--> node set    ; ~10^5 rows for a petabyte

  Storing shard->node directly costs `objects x n` rows: at 64 MiB objects and
  n=26 that is 420 million rows for one petabyte. That does not fit the one
  ref the query plane gives us, and splitting the ref is exactly what makes
  the repair scheduler's central question — *node N died, which groups are now
  below threshold?* — unanswerable. Through groups it is a scan of ~10^5 rows
  and the answer is a join away.

  **Rendezvous, not a ring.** Group -> node set is highest-random-weight
  hashing: score every node for the group, take the top n. Adding or removing
  a node moves only the placements that node wins or loses — no neighbour
  reshuffle, no ring bookkeeping to keep consistent between coordinator and
  client. Weight is expressed as virtual ids rather than a score multiplier,
  because the usual weighted-HRW formula needs a logarithm and this has to
  stay integer arithmetic a `.kotoba` guest can reproduce exactly.

  **Failure domains beat score.** Selection walks nodes in score order and
  skips any that would exceed a per-domain cap. This is the whole durability
  argument of section 1: the independent-failure model that gets 1.625x to ten
  nines is worth nothing if a rack, an operator or a region can take out more
  shards than the code tolerates. The cap is policy, not a constant, because
  section 1's open question — bonded invite-only versus permissionless — moves
  it and nothing else here.

  Selection is deterministic and total: same node set plus same policy gives
  the same placement on every host, and a group that cannot be filled under
  the cap is reported as underfilled rather than quietly filled anyway."
  (:require [kura.hash :as h]))

;; --- nodes ----------------------------------------------------------------

(def availabilities
  "What a node can claim about being awake.

  `:always-on` is rented object storage and machines that stay powered and
  reachable. `:intermittent` is a laptop: the disk is fine, the machine is in
  a bag. The distinction is not cosmetic — **it is the difference between a
  shard that is temporarily unreadable and a shard that is gone**, and the
  whole point of separating them is that the first must not be priced,
  reported, or punished as the second."
  #{:always-on :intermittent})

(defn node
  "A storage node. `:id` is an ASCII identifier, `:weight` a positive integer
  count of virtual ids (capacity, roughly), `:domains` a map of domain kind ->
  value, e.g. `{:rack \"r3\" :operator \"acme\" :region \"apac\"}`.

  `:availability` must be stated. There is no safe default: assume
  `:always-on` and a fleet of sleeping laptops reports durability it does not
  have; assume `:intermittent` and rented buckets — which are always-on
  because that is the product — become unplaceable. Only the operator knows,
  so the operator says, exactly as `:site` must be stated rather than guessed
  from a hostname."
  [{:keys [id weight domains availability] :or {weight 1 domains {}}}]
  (assert (h/ascii? id) "node id must be ASCII")
  (assert (pos? weight) "node weight must be positive")
  (assert (contains? availabilities availability)
          (str ":availability must be one of " availabilities ", got "
               (pr-str availability) ". A node that does not say whether it "
               "stays awake cannot be placed on safely: the code's tolerance "
               "for missing shards is a budget, and shards on machines that "
               "sleep spend it every night."))
  {:id id :weight weight :domains domains :availability availability})

(defn- virtual-key
  "Placement key of the `v`-th virtual id of `node-id`."
  [node-id v]
  (h/mix32 (h/u32 (bit-xor (h/key32 node-id) (h/mix32 v)))))

(defn score
  "Rendezvous score of `node` for placement group `pg`.

  A node's score is the best of its virtual ids, so a node with weight w gets
  w independent draws and wins proportionally more groups. Ties break on the
  node id, which is what keeps the result total-ordered rather than
  set-ordered — two nodes that collide must still land in a defined order or
  two hosts will disagree about the placement."
  [pg node]
  (let [pgk (h/key32 pg)]
    (reduce max 0 (map #(h/mix32 (h/u32 (bit-xor pgk (virtual-key (:id node) %))))
                       (range (:weight node))))))

(defn ranked
  "All nodes ordered by descending score for `pg`, ties broken by id."
  [pg nodes]
  (->> nodes
       (map (fn [n] (assoc n ::score (score pg n))))
       (sort-by (juxt (comp - ::score) :id))
       vec))

;; --- domain constraints ---------------------------------------------------

(defn policy
  "Placement policy. `:caps` maps a domain kind to the maximum number of
  shards allowed to share one value of it.

  A cap must be at most the code's tolerance for the domain to be worth
  declaring: with a code that survives 7 arbitrary losses, `{:rack 7}` means a
  rack failure is exactly survivable and `{:rack 8}` means it is not. Nothing
  here enforces that relationship — `kura.repair/policy-headroom` reports it,
  because the honest answer depends on the code, and the code is a parameter.

  `:max-intermittent` is how many of the n shards may sit on nodes that
  declared `:intermittent`. **It defaults to zero, which is the only safe
  default**: a policy written before this cap existed asked for no sleeping
  nodes, and silently granting them some would change the durability of every
  object already placed under it.

  Set at or below the code's tolerance, the cap means something exact and
  worth having: *the object is readable from always-on nodes alone.* Every
  sleeping node is then a bonus copy rather than a dependency, which is what
  makes consumer hardware safe to accept at all."
  [{:keys [caps max-intermittent] :or {caps {} max-intermittent 0}}]
  (assert (not (neg? max-intermittent)) ":max-intermittent cannot be negative")
  {:caps caps :max-intermittent max-intermittent})

(defn- would-exceed?
  "Whether adding `node` to `chosen` breaks any cap.

  The domain caps are symmetric — every value of a kind gets the same ceiling
  — but the availability cap is deliberately **not**. `:always-on` is
  uncapped; only `:intermittent` is counted. Treating the two symmetrically
  would cap rented buckets at the same small number as laptops, and rented
  buckets are the always-on floor the whole arrangement stands on."
  [{:keys [caps max-intermittent]} chosen node]
  (or (and (= :intermittent (:availability node))
           (>= (count (filter #(= :intermittent (:availability %)) chosen))
               max-intermittent))
      (some (fn [[kind cap]]
              (let [v (get-in node [:domains kind])]
                (and (some? v)
                     (>= (count (filter #(= v (get-in % [:domains kind])) chosen))
                         cap))))
            caps)))

(defn select
  "Choose `n` nodes for placement group `pg` under `pol`.

  Returns `{:nodes [...] :complete? bool :shortfall k}`. Walks the ranked
  nodes and takes each unless a cap forbids it.

  **Underfill is reported, not hidden.** A greedy walk under caps can run out
  of eligible nodes even when `(count nodes) >= n`; the honest response is to
  say the group is short, because a caller that silently accepts n-2 shards
  has silently accepted a different durability than it asked for. Whether to
  relax a cap or refuse the write is the caller's decision, and it needs to
  know one is being made."
  [pg nodes n pol]
  (let [chosen (reduce (fn [acc node]
                         (if (= n (count acc))
                           (reduced acc)
                           (if (would-exceed? pol acc node)
                             acc
                             (conj acc node))))
                       []
                       (ranked pg nodes))]
    {:nodes (mapv #(dissoc % ::score) chosen)
     :complete? (= n (count chosen))
     :shortfall (max 0 (- n (count chosen)))
     ;; Reported because it is the number that decides whether a read needs a
     ;; machine to be awake. A group at the cap is still `:complete?`, and a
     ;; caller reading only that flag would never learn how much of its
     ;; durability is currently in somebody's bag.
     :intermittent (count (filter #(= :intermittent (:availability %)) chosen))
     :always-on (count (filter #(= :always-on (:availability %)) chosen))}))

;; --- object -> group ------------------------------------------------------

(defn group-of
  "Which placement group `object-id` belongs to. A function of the id and the
  group count only — never a stored row, which is what keeps the metadata
  plane O(groups) instead of O(objects x shards)."
  [object-id group-count]
  (assert (pos? group-count) "group-count must be positive")
  (mod (h/key32 object-id) group-count))

(defn group-name
  "Canonical name of group `i` — what `select` is keyed on. Named rather than
  numbered so that the placement key of adjacent groups is not adjacent."
  [i]
  (str "pg-" i))

;; --- shard assignment -----------------------------------------------------

(defn assign
  "Map shard index -> node for a selected group.

  Shard i goes to the i-th selected node, i.e. in descending score order. The
  code's own structure is what makes this safe: `erasure` places data shards
  first, then local parities, then global parities, and `select` has already
  spread the whole set across failure domains, so no local group can be wholly
  contained in one domain unless the caps allowed it.

  A variant worth knowing about and NOT implemented here: deliberately
  co-locating each local group inside one domain makes repair a rack-local
  read instead of a cross-rack one, which is the traffic that dominates once
  the network is large. It costs durability in exactly the case the caps are
  there to prevent — losing that domain loses the entire group at once, which
  this code survives (a whole group plus its local parity is 5 shards and
  `erasure` tolerates 7) but with much less margin than a spread placement.
  Choosing it is a real decision with a real price, so it waits for the
  measurement Phase 0 exists to produce rather than being a default."
  [selection]
  (into {} (map-indexed (fn [i n] [i (:id n)]) (:nodes selection))))

(defn placement
  "The full answer for one object: its group, the chosen nodes, and the shard
  map. `n` is the code's shard count (`(:n layout)` from `erasure.lrc`)."
  [object-id {:keys [group-count nodes n pol]}]
  (let [g (group-of object-id group-count)
        pg (group-name g)
        sel (select pg nodes n pol)]
    {:object-id object-id
     :group g
     :group-name pg
     :complete? (:complete? sel)
     :shortfall (:shortfall sel)
     :shards (assign sel)}))

;; --- churn ----------------------------------------------------------------

(defn groups-touching
  "Which of `group-count` groups place a shard on `node-id`.

  This is the repair scheduler's entry point: a node goes away, and the
  question is which groups just lost a shard. It is a scan of the groups, not
  of the objects — the reason the two-level scheme exists."
  [node-id {:keys [group-count nodes n pol]}]
  (into (sorted-set)
        (filter (fn [g]
                  (some #(= node-id (:id %))
                        (:nodes (select (group-name g) nodes n pol))))
                (range group-count))))

(defn churn
  "Groups whose node set differs between two node populations, and how much.

  Rendezvous hashing's claim is that removing one node only disturbs the
  groups that node held. `kura.placement-test` checks that claim rather than
  repeating it."
  [{:keys [group-count n pol]} before after]
  (let [ids (fn [nodes g] (set (map :id (:nodes (select (group-name g) nodes n pol)))))]
    (into (sorted-map)
          (keep (fn [g]
                  (let [a (ids before g) b (ids after g)]
                    (when (not= a b)
                      [g {:added (into (sorted-set) (remove a b))
                          :removed (into (sorted-set) (remove b a))}]))))
          (range group-count))))
