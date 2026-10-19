(ns kura.node.audit
  "The node side of `kura.audit`: commit to what you hold, then open the
  leaves you are asked for.

  The coordinator picks the challenges and judges the answers. This namespace
  only builds the tree over the shards actually on disk and answers. That
  split matters — a node that could influence which leaves it is asked about
  has not been audited, so nothing here takes the challenge set as anything
  other than input.

  **The tree is built from the store, not from a manifest.** A node that
  commits to what it was *supposed* to have, rather than to what it *has*,
  passes its own audit while missing shards. `commit!` lists the store and
  hashes what comes back, so a shard that silently vanished shows up as a
  changed root before any challenge is issued.

  **Two hashers, because there are two domains.** `hash-bytes` digests a
  shard's body; `hash-string` digests `merkle-sum`'s internal node preimage,
  which is text (`\"node|<lh>|<ls>|<rh>|<rs>\"`) and part of the tree's public
  spec so a third party can recompute it. Passing one function for both is the
  obvious mistake and it fails loudly on the JVM, quietly elsewhere — a
  buffer-shaped hasher handed a string. Real deployments back both with
  SHA-256 over UTF-8; they are still two parameters, because a node that
  hashes text and bytes through the same code path has to be sure the encoding
  is pinned, and making it explicit is cheaper than assuming."
  (:require [kura.audit :as audit]
            [kura.node.gf :as gf]
            [kura.node.store :as store]))

(defn leaf-for
  "The audit leaf for one shard the node holds."
  [s hash-bytes shard-id]
  (when-let [body (store/-get-shard s shard-id)]
    (audit/leaf shard-id (hash-bytes body) (gf/blength body))))

(defn commit!
  "Build the node's audit tree over every shard under `prefix`.

  Returns `{:tree :root :leaf-count :claimed-bytes :shard-ids}`. `:shard-ids`
  is positional and is what a challenge index refers to — the coordinator
  challenges by index into the committed ordering, so the node cannot reorder
  its way out of a question."
  ([s hash-bytes hash-string] (commit! s hash-bytes hash-string ""))
  ([s hash-bytes hash-string prefix]
   (let [ids (vec (store/-list-shards s prefix))
         leaves (into [] (keep #(leaf-for s hash-bytes %)) ids)
         tree (audit/commit hash-string leaves)]
     {:tree tree
      :root (:root tree)
      :leaf-count (count leaves)
      :claimed-bytes (audit/claimed-bytes tree)
      :shard-ids (mapv :id (:leaves tree))})))

(defn answer
  "Answer one challenge index against a commitment."
  [commitment index]
  (audit/respond (:tree commitment) index))

(defn answer-all
  "Answer a whole challenge set, positionally.

  A challenge the node cannot answer yields `nil` in place rather than being
  dropped: `kura.audit/verdict` counts a missing answer against the node, and
  silently shortening the vector would turn a failure into a shorter clean
  run."
  [commitment challenge-indices]
  (mapv #(answer commitment %) challenge-indices))

(defn self-check
  "What a node should run before presenting a commitment: does every shard the
  tree claims still read back with the hash the tree committed to?

  Answering a challenge wrong is a slashing event, so discovering rot from the
  coordinator is the most expensive way to learn it. This is the cheap way,
  and it is a full scrub — the thing sampling replaces for the *coordinator*,
  not for the node itself, which has local bandwidth and every reason to use
  it."
  [s hash-bytes commitment]
  (let [bad (into (sorted-set)
                  (keep (fn [{:keys [id hash sum]}]
                          (let [body (store/-get-shard s id)]
                            (when (or (nil? body)
                                      (not= sum (gf/blength body))
                                      (not= hash (hash-bytes body)))
                              id))))
                  (get-in commitment [:tree :leaves]))]
    {:ok? (empty? bad)
     :checked (count (get-in commitment [:tree :leaves]))
     :damaged bad}))
