(ns erasure.lrc
  "Layout and repair planning for the locally recoverable code.

  **Shard layout.** For parameters `k` data shards, locality `r`, and `g`
  global parities, with `l = ceil(k/r)` groups:

      index          role
      ------------   ----------------------------------------------
      0 .. k-1       data; shard i belongs to group (quot i r)
      k .. k+l-1     local parity; shard k+q is the xor of group q
      k+l .. n-1     global parity; Cauchy rows over ALL k data shards

  Local parity is a plain xor, so repairing one lost shard inside a group
  costs `r` reads and zero multiplies. That is the whole point of the code
  and the reason ADR-2607299200 accepts a worse worst-case distance than a
  plain Reed-Solomon of the same rate: single-node loss is ~99% of repair
  events, and it drops from k reads to r.

  **This namespace holds decisions, not bytes.** Every function here maps
  small values to small values — indices, sets of indices, a plan. That is
  exactly the slice ADR-2607299200 section 5 assigns to the `.kotoba` port,
  and `kotoba/erasure_core.kotoba` mirrors it function for function under a
  parity gate. Keep it that way: if something here starts touching shard
  contents, it belongs in `erasure.codec` instead.

  **Planning is deterministic.** Where a choice exists — which k survivors to
  feed a global solve — the lowest indices win. Determinism is not cosmetic:
  it is what lets the `.kotoba` port be compared for equality rather than for
  plausibility."
  (:require [clojure.set :as set]
            [erasure.matrix :as matrix]))

(defn layout
  "Build a layout from `{:k :r :g}`.

  Throws on parameters the construction cannot honour: a Cauchy matrix over
  GF(2^8) needs `k + g <= 256`, and a group size below 2 makes local parity a
  duplicate rather than a code."
  [{:keys [k r g]}]
  (assert (and (pos? k) (pos? g)) "k and g must be positive")
  (assert (>= r 2) "locality r must be at least 2")
  (assert (<= (+ k g) 256) "k + g must fit GF(2^8): Cauchy needs distinct elements")
  (let [l (quot (+ k r -1) r)
        n (+ k l g)]
    {:k k :r r :g g :l l :n n
     :data-range [0 k]
     :local-range [k (+ k l)]
     :global-range [(+ k l) n]}))

(defn group-of
  "Which local group data shard `i` belongs to."
  [{:keys [r]} i]
  (quot i r))

(defn group-members
  "Data shard indices in local group `q`. The last group is short when `r`
  does not divide `k`."
  [{:keys [k r]} q]
  (vec (range (* q r) (min k (* (inc q) r)))))

(defn local-parity-of
  "The local-parity shard index covering local group `q`."
  [{:keys [k]} q]
  (+ k q))

(defn local-repair-reads-for
  "How many shards a single local repair inside group `q` reads.

  The cover is the group's data plus its local parity, so rebuilding one
  member reads the rest — `r` for a full group, fewer for a short trailing
  one. This is the number ADR-2607299200 quotes as the repair-traffic win,
  and `erasure-core/local-repair-reads` in the `.kotoba` port must agree."
  [lay q]
  (count (group-members lay q)))

(defn global-parity-index
  "Which Cauchy row shard `i` is, or nil when `i` is not a global parity."
  [{:keys [k l]} i]
  (when (>= i (+ k l)) (- i k l)))

(defn role
  "Classify a shard index."
  [{:keys [k l n] :as lay} i]
  (cond
    (or (neg? i) (>= i n)) {:kind :out-of-range}
    (< i k) {:kind :data :group (group-of lay i)}
    (< i (+ k l)) {:kind :local :group (- i k)}
    :else {:kind :global :index (- i k l)}))

(defn data-indices [{:keys [k]}] (set (range k)))

(defn global-indices [{:keys [k l n]}] (set (range (+ k l) n)))

(defn generator-row
  "The row of the generator matrix that shard `i` represents — i.e. the
  coefficients that produce shard `i` from the `k` data shards.

  Every shard has one, which is the point: a local parity is the sum of its
  group's identity rows, so it carries a real equation and belongs in any
  global solve alongside the data and Cauchy rows."
  [{:keys [k l] :as lay} i]
  (cond
    (< i k) (matrix/identity-row k i)
    (< i (+ k l)) (let [members (set (group-members lay (- i k)))]
                    (mapv (fn [j] (if (contains? members j) 1 0)) (range k)))
    :else (nth (matrix/cauchy-rows k (:g lay)) (- i k l))))

(defn- local-cover
  "The shard set that reconstructs anything missing from group `q`: the
  group's data shards plus its local parity."
  [lay q]
  (conj (set (group-members lay q)) (local-parity-of lay q)))

(defn- locally-repairable
  "If exactly one member of group `q`'s cover is missing, that member can be
  rebuilt by xoring the rest. Returns `[target reads]` or nil."
  [lay q missing]
  (let [cover (local-cover lay q)
        gone (set/intersection cover missing)]
    (when (= 1 (count gone))
      [(first gone) (vec (sort (disj cover (first gone))))])))

(defn recovery-plan
  "Plan the reconstruction of `erased` (a set of shard indices).

  Returns `{:recoverable? :steps :reads}` where `:steps` is an ordered
  sequence of `{:op :local  :target i :reads [...]}` and
  `{:op :global :targets [...] :reads [...]}`, and `:reads` is the set of
  distinct shards that must actually be fetched.

  Strategy, in order:

  1. Drain every group that is missing exactly one of its cover. Repeat to a
     fixpoint, because rebuilding a group's data shard can make that group's
     local parity rebuildable in the next round.
  2. If data shards are still missing, solve globally — possible exactly when
     at least k shards survive among data and global parity, which the Cauchy
     construction guarantees is enough (see `erasure.matrix`).
  3. Recompute any parity shard still missing from the now-complete data.

  A pattern that leaves fewer than k survivors across data and global parity
  and cannot be finished locally is reported unrecoverable rather than
  attempted."
  [{:keys [k l n] :as lay} erased]
  (let [erased (set erased)]
    (loop [missing erased
           steps []
           guard 0]
      (cond
        (empty? missing)
        {:recoverable? true :steps steps
         :reads (into (sorted-set) (mapcat :reads steps))}

        ;; Defensive: each round must retire at least one shard, so this can
        ;; only trip if the loop below is ever changed to not make progress.
        (> guard n)
        {:recoverable? false :steps steps :reads #{} :reason :no-progress}

        :else
        (if-let [[target reads]
                 (some #(locally-repairable lay % missing)
                       (range l))]
          (recur (disj missing target)
                 (conj steps {:op :local :target target :reads reads})
                 (inc guard))
          ;; No group can finish on its own. Fall through to a global solve
          ;; over EVERY surviving shard — local parities included, since each
          ;; is a genuine equation in the data (see erasure.matrix).
          (let [survivors (vec (sort (set/difference (set (range n)) missing)))
                lost-data (vec (sort (set/intersection (data-indices lay) missing)))
                chosen (when (seq lost-data)
                         (matrix/independent-rows #(generator-row lay %) k survivors))]
            (if (and (seq lost-data) (nil? chosen))
              {:recoverable? false :steps steps :reads #{}
               :reason :rank-deficient
               :survivors (count survivors) :needed k}
              (let [;; Global solve rebuilds every lost data shard at once.
                    steps (cond-> steps
                            (seq lost-data)
                            (conj {:op :global :targets lost-data :reads chosen}))
                    missing (set/difference missing (set lost-data))
                    ;; Data is whole now, so any remaining parity is a
                    ;; recompute, not a solve.
                    parity-left (vec (sort missing))
                    steps (reduce
                           (fn [acc idx]
                             (let [{:keys [kind group]} (role lay idx)]
                               (conj acc
                                     (if (= kind :local)
                                       {:op :local :target idx
                                        :reads (group-members lay group)}
                                       {:op :recompute-global :target idx
                                        :reads (vec (range k))}))))
                           steps
                           parity-left)]
                {:recoverable? true :steps steps
                 :reads (into (sorted-set) (mapcat :reads steps))}))))))))

(defn max-tolerated-erasures
  "Largest t such that EVERY t-subset of shards can be erased and still
  recovered — i.e. the code's minimum distance minus one.

  This is measured, not asserted: `erasure.distance-test` searches erasure
  patterns against `recovery-plan`. The Gopalan et al. upper bound for an
  optimal LRC is `d <= n - k - ceil(k/r) + 2`; this construction is not
  claimed to meet it, and the test reports what it actually achieves."
  [{:keys [n k r]}]
  (dec (+ (- n k (quot (+ k r -1) r)) 2)))
