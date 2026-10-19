(ns kura.audit
  "Proving a node still holds what it was paid to hold.

  ADR-2607299200 section 2 threw out the high-dimensional-expander scheme this
  network was originally sketched with, for two reasons worth restating where
  the replacement lives. Local testability is a property of a word *you* hold;
  in a storage network the word is held by the node, and a node that deleted
  it can answer a handful of queries by fetching from a peer. And a proximity
  test does not say WHICH symbol is wrong, so the Merkle layer it was supposed
  to replace is still needed to localise the damage.

  What is here instead is sampling, which is boring and works:

      q = ceil( ln(delta) / ln(1 - f) )

  challenges detect deletion of a fraction `f` with probability `1 - delta`,
  independent of how much the node stores. At f=1% and delta=1e-3 that is 688
  challenges — about 44 MiB against a 64 KiB leaf — so a 10 TB node audited
  monthly reads 528 MiB a year, 0.005% of its capacity. A monthly full scrub
  is 1200%. The scheme the ADR discarded was aiming at a 90-99.9% reduction;
  plain sampling gets 99.9996% and, unlike local testability, means something
  against a node that is trying to cheat.

  **Challenges must be unpredictable and verifiable.** They are derived from
  an epoch seed the coordinator publishes only when the epoch opens, so a node
  cannot know in advance which leaves to keep; and they are derived by a pure
  function of that seed, so any third party can recompute the challenge set
  and check the coordinator did not target a node unfairly. Both properties
  matter, and they pull in opposite directions unless the seed is committed
  first and revealed second.

  **The tree is a Merkle SUM tree, on purpose.** Each leaf carries its shard's
  byte length, so the root's sum is the total volume the node claims to hold.
  A node cannot overstate its holdings for payment without a leaf proof
  contradicting it, and cannot understate them to dodge an audit without the
  same contradiction. That is `merkle-sum`, already in this workspace as the
  proof-of-liabilities primitive; it is reused rather than reimplemented."
  (:require [kura.hash :as h]
            [merkle-sum.core :as ms]))

;; --- how many challenges ---------------------------------------------------

(defn samples-needed
  "Challenges required to catch deletion of fraction `f` with probability
  `1 - delta`.

  `ceil(ln(delta) / ln(1-f))`. Independent of stored volume — a 100 TB node
  and a 1 TB node need the same number, which is what makes the scheme cheap
  at scale and why per-node audit cost does not grow with the network."
  [f delta]
  (assert (< 0 f 1) "f must be a fraction in (0,1)")
  (assert (< 0 delta 1) "delta must be a probability in (0,1)")
  ;; `int` rather than `long`: ClojureScript has no `long`, and a challenge
  ;; count that overflows an int is not a scheme anyone would run.
  (int (Math/ceil (/ (Math/log delta) (Math/log (- 1.0 f))))))

(defn detection-probability
  "The other direction: given `q` challenges, the chance of catching a node
  that deleted fraction `f`. Useful for reporting what an audit that was
  actually run is worth, rather than what it was designed for."
  [q f]
  (- 1.0 (Math/pow (- 1.0 f) q)))

;; --- which challenges ------------------------------------------------------

(defn challenge
  "The `i`-th challenge of `epoch` for `node-id`, over `leaf-count` leaves.

  A pure function of the published seed, so a third party recomputes it
  exactly. Successive challenges must not be adjacent leaves or a node could
  keep contiguous runs, hence the avalanche on every component."
  [epoch-seed node-id epoch i leaf-count]
  (assert (pos? leaf-count) "leaf-count must be positive")
  (let [k (h/mix32 (h/u32 (bit-xor (h/key32 epoch-seed)
                                   (h/mix32 (h/u32 (bit-xor (h/key32 node-id)
                                                            (h/mix32 (+ (* epoch 2654435761) i))))))))]
    (mod k leaf-count)))

(defn challenge-set
  "The `q` distinct leaf indices `node-id` must open this epoch.

  Distinct because repeating a leaf costs a round trip and proves nothing new;
  the draw walks forward until it has `q` of them, or exhausts the leaves."
  [epoch-seed node-id epoch q leaf-count]
  (loop [i 0 acc (sorted-set)]
    (if (or (= q (count acc)) (= (count acc) leaf-count) (> i (* 8 (+ q leaf-count))))
      (vec acc)
      (recur (inc i)
             (conj acc (challenge epoch-seed node-id epoch i leaf-count))))))

;; --- the tree --------------------------------------------------------------

(defn leaf
  "An audit leaf: one shard the node holds.

  `:id` orders the tree deterministically, `:hash` is the domain's commitment
  to the shard's bytes (the caller hashes; this library injects), `:sum` is
  the shard's byte length so the root totals the node's holdings."
  [shard-id shard-hash byte-length]
  (assert (and (integer? byte-length) (not (neg? byte-length)))
          "byte-length must be a non-negative integer")
  {:id shard-id :hash shard-hash :sum byte-length})

(defn commit
  "Build a node's audit tree. `hash-hex` is the injected digest."
  [hash-hex leaves]
  (ms/build-tree hash-hex leaves))

(defn claimed-bytes
  "Total volume the node's committed root asserts it holds. Payment and audit
  read the same number, which is the point of a sum tree."
  [tree]
  (get-in tree [:root :sum]))

(defn respond
  "What a node returns for one challenge: the leaf plus its sibling path."
  [tree leaf-index]
  (let [lf (nth (:leaves tree) leaf-index nil)]
    (when lf
      {:leaf lf :proof (ms/inclusion-proof tree (:id lf))})))

(defn verify-response
  "Check one response against the committed root."
  [hash-hex root {:keys [leaf proof]}]
  (boolean
   (and leaf proof
        (ms/verify hash-hex (:hash leaf) (:sum leaf) proof root))))

;; --- the verdict -----------------------------------------------------------

(defn verdict
  "Judge an epoch's audit of one node.

  `responses` is positionally aligned with the challenge set; a `nil` entry is
  a challenge the node did not answer, which counts against it exactly as a
  wrong answer does. A node that can choose which challenges to skip has not
  been audited.

  Returns the pass/fail plus what the run is actually worth: with `q` answered
  challenges the audit detects `f`-scale deletion with
  `detection-probability`, and reporting that alongside the verdict keeps a
  short run from being read as a clean bill of health."
  [hash-hex root responses {:keys [f] :or {f 0.01}}]
  (let [q (count responses)
        results (mapv #(and (some? %) (verify-response hash-hex root %)) responses)
        failed (into (sorted-set) (keep-indexed (fn [i ok] (when-not ok i)) results))]
    {:pass? (empty? failed)
     :challenges q
     :failed failed
     :answered (count (filter some? responses))
     :detects-fraction f
     :detection-probability (if (pos? q) (detection-probability q f) 0.0)}))
