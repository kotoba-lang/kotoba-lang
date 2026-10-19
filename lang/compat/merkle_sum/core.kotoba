(ns merkle-sum.core
  "Merkle **sum** tree (Maxwell-style) + inclusion proofs — the shared
  primitive behind proof-of-liabilities / proof-of-reserves. Every
  internal node commits to the SUM of its children as well as their
  hashes, so the root's `:sum` IS the total, and any leaf's inclusion
  proof re-derives a slice of it. Hiding the total is therefore
  impossible without contradicting some leaf's proof, and the verifier
  rejects any negative intermediate sum (the classic sum-shrinking
  trick).

  The hasher is INJECTED — a `hash-hex : String -> hex String` fn
  (e.g. SHA-256) — so this lib has zero crypto dependencies and stays
  portable `.cljc` (JVM / ClojureScript / kotoba-WASM), the same
  host-injected-digest style as `kotoba.lang.crypto`. The DOMAIN owns
  the leaf preimage (it supplies each leaf's `:hash`); this lib owns
  the NODE preimage `\"node|<lh>|<ls>|<rh>|<rs>\"` and the tree / proof
  / verify algorithms.

  Leaf shape: `{:id <sortable> :hash <hex String> :sum <int >= 0>}`
  (leaves may carry extra domain keys; only `:id`/`:hash`/`:sum` are
  read here). Determinism: leaves are sorted by `:id` via a supplied or
  default string comparator, and an odd node is carried UP unchanged —
  never duplicated, since duplication would double-count its sum.

  Extracted from `cryptoexchange.attest` (ADR-2607141200) so any actor
  needing PoR/PoL reuses one audited implementation.")

;; ------------------------------ hashing ------------------------------

(defn node-hash
  "Canonical internal-node hash: `hash-hex` over the node preimage. The
  preimage format is part of the tree's public spec (a third-party
  verifier reconstructs it), so it lives here, not in the caller."
  [hash-hex left-hash left-sum right-hash right-sum]
  (hash-hex (str "node|" left-hash "|" left-sum "|" right-hash "|" right-sum)))

(defn empty-root-hash
  "Root hash of an empty tree (no leaves)."
  [hash-hex]
  (hash-hex "empty"))

;; ------------------------------ building -----------------------------

(defn- level-up
  "Combine one level pairwise; an odd trailing node carries up as-is."
  [hash-hex nodes]
  (loop [nodes nodes acc []]
    (cond
      (empty? nodes) acc
      (= 1 (count nodes)) (conj acc (first nodes))
      :else
      (let [[l r & more] nodes]
        (recur more
               (conj acc {:hash (node-hash hash-hex (:hash l) (:sum l) (:hash r) (:sum r))
                          :sum (+ (:sum l) (:sum r))}))))))

(defn build-tree
  "Build a Merkle-sum tree from `leaves` (`[{:id .. :hash .. :sum ..}]`)
  using the injected `hash-hex`. `opts` may supply `:id->str` (default
  `str`) to shape the deterministic leaf ordering key.

  Returns `{:leaves <sorted> :levels [[leaf-level] .. [root]] :root
  {:hash h :sum total}}`. An empty leaf set gets a defined empty root."
  ([hash-hex leaves] (build-tree hash-hex leaves {}))
  ([hash-hex leaves {:keys [id->str] :or {id->str str}}]
   (let [sorted (vec (sort-by (comp id->str :id) leaves))]
     (if (empty? sorted)
       {:leaves [] :levels [] :root {:hash (empty-root-hash hash-hex) :sum 0}}
       (let [levels (loop [level sorted acc [sorted]]
                      (if (= 1 (count level))
                        acc
                        (let [next-level (level-up hash-hex level)]
                          (recur next-level (conj acc next-level)))))]
         {:leaves sorted
          :levels levels
          :root (first (peek levels))})))))

;; ------------------------------ proofs -------------------------------

(defn inclusion-proof
  "Sibling path for the leaf whose `:id` equals `id`:
  `[{:side :left|:right :hash h :sum s} ...]` from the leaf level
  upward (a carried-up level contributes no step). `nil` when no leaf
  has that id."
  [tree id]
  (when-let [idx (first (keep-indexed
                         (fn [i leaf] (when (= (:id leaf) id) i))
                         (:leaves tree)))]
    (loop [i idx
           levels (:levels tree)
           proof []]
      (let [level (first levels)]
        (if (or (nil? level) (= 1 (count level)))
          proof
          (let [sibling-i (if (even? i) (inc i) (dec i))
                carried? (>= sibling-i (count level))
                proof' (if carried?
                         proof
                         (let [s (nth level sibling-i)]
                           (conj proof {:side (if (even? i) :right :left)
                                        :hash (:hash s)
                                        :sum (:sum s)})))]
            (recur (quot i 2) (rest levels) proof')))))))

(defn verify
  "Third-party verification: recompute from a claimed leaf
  (`leaf-hash`, `leaf-sum`) through `proof` to `root`, using the same
  injected `hash-hex`. Rejects a negative leaf or intermediate sum
  (sum-shrinking) and any root mismatch in HASH or SUM."
  [hash-hex leaf-hash leaf-sum proof root]
  (if (or (not (integer? leaf-sum)) (neg? leaf-sum))
    false
    (loop [h leaf-hash
           s leaf-sum
           steps proof]
      (if (empty? steps)
        (and (= h (:hash root)) (= s (:sum root)))
        (let [{:keys [side] :as step} (first steps)]
          (if (or (nil? (:sum step)) (neg? (:sum step)) (nil? (:hash step)))
            false
            (let [[nh nsum] (if (= side :left)
                              [(node-hash hash-hex (:hash step) (:sum step) h s)
                               (+ (:sum step) s)]
                              [(node-hash hash-hex h s (:hash step) (:sum step))
                               (+ s (:sum step))])]
              (recur nh nsum (rest steps)))))))))
