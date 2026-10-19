(ns bonsai.log
  "History walking over the commit DAG: reachability, first-parent log, and
   the missing-object negotiation a push/pull/pack exchange needs. Reads
   commits/trees straight out of the repo's arrangement db (bonsai.object)."
  (:refer-clojure :exclude [ancestors])
  (:require [bonsai.object :as obj]))

(defn ancestors
  "Set of every commit CID reachable from head-cid (inclusive), following
   all parent edges — i.e. the real DAG, not just first-parent."
  [db head-cid]
  (loop [frontier [head-cid] seen #{}]
    (if (empty? frontier)
      seen
      (let [cid (peek frontier)
            frontier (pop frontier)]
        (if (or (nil? cid) (seen cid))
          (recur frontier seen)
          (recur (into frontier (:parents (obj/read-commit db cid)))
                 (conj seen cid)))))))

(defn log
  "Commits from head-cid walking first-parent history, newest-first."
  [db head-cid]
  (->> head-cid
       (iterate (fn [cid] (when cid (first (:parents (obj/read-commit db cid))))))
       (take-while some?)
       (mapv (fn [cid] (assoc (obj/read-commit db cid) :cid cid)))))

(defn- tree-object-cids
  "All CIDs reachable from a tree (its entries, recursively through subtrees)."
  [db tree-cid]
  (mapcat (fn [{:keys [cid kind]}]
            (cons cid (when (= kind :tree) (tree-object-cids db cid))))
          (:entries (obj/read-tree db tree-cid))))

(defn missing-since
  "Every CID (commit, tree, or blob) reachable from head-cid's history that
   is not already in `have` (a set of CIDs the requester reports holding).
   This is the object-level negotiation a pack/pull exchange needs to decide
   what to actually transfer."
  [db head-cid have]
  (reduce
   (fn [acc commit-cid]
     (let [commit (obj/read-commit db commit-cid)
           reachable (cons (:tree commit) (tree-object-cids db (:tree commit)))]
       (into acc (remove have (cons commit-cid reachable)))))
   #{}
   (ancestors db head-cid)))
