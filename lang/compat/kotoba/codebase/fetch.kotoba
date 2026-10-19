(ns kotoba.codebase.fetch
  "Bounded, verified hydration of a definition closure from an outside source.

  The transport is injected, not implemented here: a block may arrive from a
  peer, a gateway, a delegated router, a USB stick. What matters is that every
  received block is checked before it is persisted, and that the check is on
  the RECEIVED BYTES.

  Two checks, not one:

  - the bytes must hash to the CID that was asked for. This is what makes an
    untrusted provider harmless -- it can serve the wrong thing, and it will be
    rejected rather than believed;
  - the bytes must be the canonical encoding of what they decode to. Without
    this a provider could ship a non-canonical CBOR encoding that decodes to
    the same block, and the store would hold bytes that re-encode to a
    different CID than the name they are filed under.

  Traversal is bounded because the links to follow come from the fetched blocks
  themselves: an adversarial DAG must not be able to turn one request into an
  unbounded fetch."
  (:require [cbor.core :as cbor]
            [kotoba.codebase.ir :as ir]
            [kotoba.codebase.store :as store]
            [multiformats.core :as mf]))

(def default-max-blocks 4096)

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn verify-bytes
  "Decode BYTES as the block named by CID, or fail closed."
  [cid bytes]
  (when-not (= cid (mf/cidv1-dag-cbor bytes))
    (fail! :codebase/fetched-cid-mismatch {:cid cid}))
  (let [block (cbor/decode bytes)]
    (when-not (= (seq bytes) (seq (cbor/encode block)))
      (fail! :codebase/fetched-block-not-canonical {:cid cid}))
    block))

(defn verify-raw-bytes
  "Verify raw artifact BYTES against the requested CID."
  [cid bytes]
  (when-not (= cid (mf/cidv1-raw bytes))
    (fail! :codebase/fetched-cid-mismatch {:cid cid}))
  bytes)

(defn- links-of
  "What to fetch next: every link in the block that could be a stored block.

  Enumerating `dependencies` and `type` by name was not enough and could not
  be: hydrating from a NAMESPACE COMMIT -- which is what following a published
  namespace does -- fetched exactly one block and stopped, because a commit
  links its parents and bindings and neither was on the list. `ir/block-links`
  reads the links that are present instead of the ones a reader remembered."
  [block]
  (ir/block-links block))

(defn contract-identities
  "The profile and hash-contract identities BLOCK commits to.

  Returned rather than fetched: a receiver checks that these match the contract
  it implements, and a mismatch means the block was hashed under rules this
  runtime does not have -- not that a block is missing."
  [block]
  (into {} (keep (fn [[key field]]
                   (when-let [link (get block field)]
                     [key (ir/link->cid link)])))
        {:profile "profile" :hash-contract "hashContract"}))

(defn hydrate!
  "Fetch the closure rooted at ROOTS through FETCH-BLOCK and persist it.

  FETCH-BLOCK takes a CID and returns its canonical bytes, or nil when the
  source does not have them. A CID already held locally is not refetched, so
  hydration is incremental across calls."
  [root roots {:keys [fetch-block max-blocks] :or {max-blocks default-max-blocks}}]
  (when-not (ifn? fetch-block)
    (fail! :codebase/fetch-source-required {}))
  (loop [pending (vec roots) seen #{} fetched [] missing []]
    (if-let [cid (first pending)]
      (cond
        (contains? seen cid)
        (recur (subvec pending 1) seen fetched missing)

        (> (count seen) max-blocks)
        (fail! :codebase/fetch-budget-exceeded {:limit max-blocks})

        :else
        (let [raw? (store/raw-cid? cid)
              local (if raw?
                      (store/get-artifact root cid)
                      (try (store/get-block root cid)
                           (catch clojure.lang.ExceptionInfo error
                             (if (= :codebase/block-not-found (:problem (ex-data error)))
                               nil
                               (throw error)))))]
          (if local
            (recur (if raw?
                     (subvec pending 1)
                     (into (subvec pending 1) (links-of local)))
                   (conj seen cid) fetched missing)
            (if-let [bytes (fetch-block cid)]
              (if raw?
                (do
                  (verify-raw-bytes cid bytes)
                  (store/put-artifact! root bytes)
                  (recur (subvec pending 1) (conj seen cid)
                         (conj fetched cid) missing))
                (let [block (verify-bytes cid bytes)]
                  (store/put-block! root cid block)
                  (recur (into (subvec pending 1) (links-of block))
                         (conj seen cid) (conj fetched cid) missing)))
              (recur (subvec pending 1) (conj seen cid) fetched (conj missing cid))))))
      {:roots (vec roots)
       :fetched fetched
       :missing missing
       :complete? (empty? missing)})))
