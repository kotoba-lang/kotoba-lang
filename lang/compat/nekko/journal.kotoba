(ns nekko.journal
  "An append-only, hash-chained log of identity events (delegate add/remove,
   sigrefs, ...) for one RID, built directly on kotoba-lang/chain -- chain's
   single-parent, opaque-state design is exactly a linear identity journal,
   as opposed to kotoba-git's commit DAG which needs N parents for merges."
  (:require [chain.core :as chain]))

(defn append!
  "Append entry (an opaque, DAG-CBOR-encodable map) onto the journal whose
   current tip is head-cid (nil starts a new journal). Returns the new
   head-cid."
  [put! get-fn head-cid entry]
  (chain/commit! put! get-fn entry head-cid))

(defn entries
  "Every entry in the journal, oldest-first, each with :cid/:seq attached."
  [get-fn head-cid]
  (if head-cid
    (mapv (fn [{:keys [cid state seq]}] (assoc state :cid cid :seq seq))
          (chain/chain get-fn head-cid))
    []))

(defn verify
  "Hash-chain integrity of the journal (tamper + gapless-seq check). This
   does NOT check per-entry signatures -- see nekko.delegate/verify-journal
   for that."
  [get-fn head-cid]
  (or (nil? head-cid) (chain/verify-chain get-fn head-cid)))
