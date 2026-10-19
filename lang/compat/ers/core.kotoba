(ns ers.core
  "[RFC 4998](https://www.rfc-editor.org/rfc/rfc4998.html) Evidence Record
  Syntax — the reduced hash tree, and the renewals that keep it meaningful after
  its own cryptography ages. Portable `.cljc`.

  ## The problem it solves, which is not the one signatures solve

  A signature proves who agreed. A timestamp proves when. Neither survives time:
  the TSA's certificate expires, its algorithm weakens, and a SHA-256 that is
  fine today is a SHA-1 in fifteen years. A retention obligation in Japan runs
  seven to ten years, and the signature has to still mean something at the end
  of it.

  RFC 4998 answers with two renewals, and they are different operations that
  people conflate:

  - **Timestamp renewal** (§5.2). The old timestamp is about to expire or its
    signature algorithm is weakening. Timestamp the OLD TIMESTAMP, before it
    stops being verifiable. Cheap: one new token per archive, not per document.
    The chain of tokens is the evidence.
  - **Hash renewal** (§5.3). The *digest* algorithm is weakening, so every hash
    in the tree is suspect. Re-hash the data itself under the new algorithm,
    concatenate with a re-hash of the whole previous ArchiveTimeStampChain, and
    timestamp that. Expensive: it needs the original data back.

  **A hash renewal that does not re-hash the original data is not a hash
  renewal.** It carries the new algorithm's name over the old algorithm's
  digests, and the resulting record claims a strength it does not have.
  `renew-hash` therefore requires the data and refuses without it.

  ## Why the tree, and why it is the storage index

  A reduced hash tree lets one document's evidence be extracted from an archive
  of a million without revealing — or needing — the other 999,999. The path from
  a document's digest to the root is all that travels.

  In a content-addressed store that path is not overhead; it IS the index. Each
  node is named by its own digest, and `cloud.itonami.app.filecoin`'s PieceCID
  reference already has this shape. That is the one place decentralised storage
  genuinely helps an evidence record: availability and tamper-evidence of the
  hash tree, which is a different claim from legal validity and should not be
  confused with it.

  ## Sorted concatenation, and the bug it prevents

  RFC 4998 §4.2: a node's hash is over the **sorted, concatenated** hashes of
  its children. Sorted, so that the verifier does not need to know which sibling
  was on the left. Skip the sort and verification depends on reconstructing an
  order the record does not carry — it will pass for records this implementation
  built and fail for everyone else's, in a way self-consistent tests never
  catch.

  No clock, no network. `digest-fn` is injected, as everywhere in this stack."
  (:require [asn1.core :as asn1]
            [rfc3161.core :as ts]))

(defn fail! [code message data]
  (throw (ex-info message (assoc data :type code))))

;; ── the hash tree ────────────────────────────────────────────────────────────

(defn- octets< [a b] (asn1/octets< (vec a) (vec b)))

(defn node-hash
  "One node: the digest of its children's digests, **sorted then concatenated**
  (§4.2).

  Sorting is what makes a partial path verifiable without knowing which side a
  sibling was on. See the namespace docstring for why skipping it produces an
  implementation that only interoperates with itself."
  [digest-fn algorithm children]
  (asn1/->ints
   (digest-fn algorithm
              (into [] (mapcat identity) (sort octets< (map asn1/->ints children))))))

(defn build-tree
  "A full binary hash tree over `leaves` (each already a digest).

  Returns `{:root ints :levels [[ints …] …]}` with the leaves as level 0. An odd
  node at a level is promoted unchanged rather than paired with a copy of
  itself — duplicating it would make two distinct archives with the same last
  document produce the same root."
  [digest-fn algorithm leaves]
  (when (empty? leaves)
    (fail! :ers/no-leaves "an evidence record needs at least one data object" {}))
  (loop [level (mapv asn1/->ints leaves) levels [(mapv asn1/->ints leaves)]]
    (if (= 1 (count level))
      {:root (first level) :levels levels}
      (let [next-level (->> (partition-all 2 level)
                            (mapv (fn [pair]
                                    (if (= 1 (count pair))
                                      (first pair)
                                      (node-hash digest-fn algorithm pair)))))]
        (recur next-level (conj levels next-level))))))

(defn reduced-path
  "The `partialHashtree` sequence for the leaf at `index` — each level's sibling
  set, from the leaf up.

  This is what travels with one document. The other leaves never appear."
  [{:keys [levels]} index]
  (loop [i index level 0 out []]
    (if (>= (inc level) (count levels))
      out
      (let [nodes (nth levels level)
            sibling (if (even? i) (inc i) (dec i))
            entry (if (< sibling (count nodes))
                    [(nth nodes i) (nth nodes sibling)]
                    ;; Promoted node: no sibling at this level, so the group is
                    ;; the node alone. Recording it rather than omitting it keeps
                    ;; the path's length equal to the tree's height, which is how
                    ;; a verifier knows it saw every level.
                    [(nth nodes i)])]
        (recur (quot i 2) (inc level) (conj out entry))))))

(defn root-from-path
  "Recompute the root from a leaf digest and its reduced path.

  The first group must contain the leaf — a path that does not mention the
  document it is supposed to be about proves nothing, and this is the check that
  turns 'the arithmetic worked out' into 'this document is in this archive'."
  [digest-fn algorithm leaf path]
  (let [leaf (asn1/->ints leaf)]
    (when (seq path)
      (when-not (some #(= leaf (asn1/->ints %)) (first path))
        (fail! :ers/leaf-not-in-path
               "reduced path の最初の group に対象の digest が含まれていません"
               {:leaf (asn1/hex leaf)})))
    (reduce (fn [current group]
              (if (= 1 (count group))
                current
                (node-hash digest-fn algorithm group)))
            leaf
            path)))

;; ── the record ───────────────────────────────────────────────────────────────

(def schema "kotoba-lang.ers.v1")

(defn archive-time-stamp
  "One `ArchiveTimeStamp`: a reduced hash tree plus the token over its root.

  `:digest-algorithm` is recorded next to the tree rather than inferred from the
  digest length. SHA-256 and SHA3-256 are both 32 bytes, and a verifier that
  guessed would compute a different tree and report a valid record as broken —
  or, given a chosen input, the reverse."
  [{:keys [digest-algorithm reduced-path token-der]}]
  {:ers/digest-algorithm digest-algorithm
   :ers/reduced-path (mapv #(mapv asn1/->ints %) reduced-path)
   :ers/token-der (asn1/->ints token-der)})

(defn evidence-record
  "`{:ers/schema … :ers/chains [[archive-time-stamp …] …]}`.

  A **sequence of chains**, not a flat list, and the nesting is the difference
  between the two renewals. Every timestamp renewal appends to the last chain;
  a hash renewal starts a NEW chain, because from that point the digests are
  computed differently and the old ones can no longer be extended."
  [chains]
  {:ers/schema schema :ers/chains (vec chains)})

(defn timestamp-renewal-digest
  "What to send a TSA to renew a chain's timestamp (§5.2).

  The digest of the LAST token's own bytes. Renewing means attesting that the
  previous token existed and was intact before the new time, which is exactly
  what stops its eventual expiry from taking the record with it."
  [digest-fn algorithm chain]
  (asn1/->ints (digest-fn algorithm (:ers/token-der (last chain)))))

(defn hash-renewal-digest
  "What to send a TSA to renew the hash algorithm (§5.3).

  `digest_new(data) ‖ digest_new(previous-chain)`, then hashed. Both halves
  under the NEW algorithm: the first re-establishes the data under it, and the
  second binds everything already proven.

  `data` is REQUIRED. A hash renewal computed without it would carry the new
  algorithm's name over the old algorithm's digests, which is the failure this
  operation exists to prevent."
  [digest-fn new-algorithm data chains]
  (when (nil? data)
    (fail! :ers/hash-renewal-needs-data
           "hash renewal は元データを再ハッシュしなければ意味がありません（RFC 4998 §5.3）"
           {}))
  (let [data-digest (asn1/->ints (digest-fn new-algorithm data))
        chain-digest (asn1/->ints
                      (digest-fn new-algorithm
                                 (into [] (mapcat :ers/token-der) (apply concat chains))))]
    (asn1/->ints (digest-fn new-algorithm (into (vec data-digest) chain-digest)))))

;; ── verification ─────────────────────────────────────────────────────────────

(defn- check-archive-time-stamp
  [{:ers/keys [digest-algorithm reduced-path token-der]} leaf
   {:keys [digest-fn verify-fn trusted?]}]
  (let [root (root-from-path digest-fn digest-algorithm leaf reduced-path)
        token (ts/parse-token token-der)
        result (ts/verify-token token {:digest root
                                       :digest-fn digest-fn
                                       :verify-fn verify-fn
                                       :trusted? trusted?})]
    (assoc result :root (asn1/hex root))))

(defn verify
  "Verify that `leaf` is covered by `record`, chain by chain.

  Returns `{:verified bool :trusted (true|false|:unknown) :oldest \"…\"
  :timestamps [...]}`.

  Three things worth stating about what this does and does not answer:

  1. **`:oldest` is the claim.** An evidence record proves the data existed
     before the EARLIEST verified timestamp; later ones exist to keep the older
     ones checkable, not to move the date forward.
  2. **`:trusted` is `:unknown` unless every timestamp's TSA was vouched for.**
     `rfc3161.core` keeps `:verified` and `:trusted` apart for a reason and
     collapsing them here would undo it.
  3. **A chain after the first is verified against the previous chain's tokens,
     not against `leaf`.** That is what a hash renewal means — and it is why
     `verify` needs the data to check a hash-renewed record fully, which it
     reports rather than skips."
  [record leaf {:keys [digest-fn data] :as opts}]
  (let [chains (:ers/chains record)
        results
        (vec (map-indexed
              (fn [chain-index chain]
                (vec (map-indexed
                      (fn [i stamp]
                        (cond
                          ;; The first chain's first stamp covers the data itself.
                          (and (zero? chain-index) (zero? i))
                          (check-archive-time-stamp stamp leaf opts)

                          ;; Within a chain, each renewal covers the previous token.
                          (pos? i)
                          (check-archive-time-stamp
                           stamp
                           (timestamp-renewal-digest digest-fn
                                                     (:ers/digest-algorithm stamp)
                                                     (subvec chain 0 i))
                           opts)

                          ;; A new chain is a hash renewal: it covers
                          ;; digest_new(data) ‖ digest_new(previous chains).
                          :else
                          (if (nil? data)
                            {:verified false
                             :reason :hash-renewal-needs-data
                             :detail "hash renewal を検証するには元データが必要です"}
                            (check-archive-time-stamp
                             stamp
                             (hash-renewal-digest digest-fn
                                                  (:ers/digest-algorithm stamp)
                                                  data
                                                  (subvec chains 0 chain-index))
                             opts))))
                      chain)))
              chains))
        flat (vec (apply concat results))]
    {:verified (boolean (and (seq flat) (every? :verified flat)))
     :trusted (cond
                (every? #(true? (:trusted %)) flat) true
                (some #(false? (:trusted %)) flat) false
                :else :unknown)
     ;; The earliest verified time is the claim the record makes.
     :oldest (->> flat (filter :verified) (keep :gen-time) sort first)
     :newest (->> flat (filter :verified) (keep :gen-time) sort last)
     :chains results
     :timestamps flat}))
