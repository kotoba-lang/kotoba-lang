(ns kotoba.codebase.authoring
  "Hash-native authoring: scratch source in, namespace commit out.

  Authoring against a content-addressed codebase is not `edit the file that
  holds the definition`, because no file holds it. The loop is:

    1. write definitions in a scratch buffer, referring to existing work BY
       NAME;
    2. compile them against the names the namespace currently selects, which
       turns every such reference into a dependency CID;
    3. classify each result as added, updated, or unchanged by comparing the
       computed CID with the one the name selects today;
    4. propagate: rewrite every dependent so it points at the new CID, which
       necessarily gives each dependent a new identity too;
    5. commit the resulting name -> CID map as one immutable namespace commit.

  Step 4 is why definitions are rewritten rather than recompiled. A dependent's
  meaning lives in its stored IR, and its dependencies are already CIDs, so
  updating it is a substitution on the DAG. Recompiling dependent source would
  require the source to still exist and to still resolve the same names -- the
  two properties a content-addressed codebase specifically does not rely on."
  (:require [kotoba.codebase.ir :as ir]
            [kotoba.codebase.semantic-code :as semantic]
            [kotoba.codebase.store :as store]))

(def max-propagation-rounds
  "Each round rewrites the dependents of everything rewritten so far, so the
  depth of the dependency graph bounds the count. The limit exists so a
  malformed store cannot spin here forever."
  64)

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn- current-bindings [root namespace]
  (if-let [head (store/head root namespace)]
    {:head head :bindings (:bindings (store/namespace-view root head))}
    {:head nil :bindings {}}))

(defn- staged-blocks
  "Every block one compiled definition needs in the store, dependencies first.

  Two shapes are accepted because two identity layers produce them: the
  surface `semantic-code` block carries a `type`, and the KIR-derived
  `typed-code` block carries an `interface`. Everything downstream of here --
  classification, propagation, commit -- is identical for both, which is the
  point of keeping them one plan."
  [{:keys [cid block type-cid type-block interface-cid interface-block
           group-cid group-block]}]
  (cond-> []
    type-cid (conj [type-cid type-block])
    interface-cid (conj [interface-cid interface-block])
    group-cid (conj [group-cid group-block])
    true (conj [cid block])))

(defn- reachable-blocks
  "Load the blocks reachable from ROOTS that this store can evaluate.

  Type, profile and hash-contract identities are reachable too but are not
  rewritten by propagation, so they are deliberately not walked: pulling them
  in would only widen the set of blocks a substitution pass has to consider."
  [root roots]
  (loop [pending (vec roots) seen {}]
    (if-let [cid (first pending)]
      (if (contains? seen cid)
        (recur (subvec pending 1) seen)
        (let [block (try (store/get-block root cid)
                         (catch clojure.lang.ExceptionInfo error
                           (if (= :codebase/block-not-found (:problem (ex-data error)))
                             nil
                             (throw error))))]
          (if block
            (recur (into (subvec pending 1) (ir/outbound-cids block))
                   (assoc seen cid block))
            (recur (subvec pending 1) (assoc seen cid nil)))))
      (into {} (remove (comp nil? val)) seen))))

(defn- compose
  "Extend SUBSTITUTIONS with ADDED, keeping existing entries pointing at the
  final CID.

  Without this a two-level rewrite leaves the first level stale: `a -> a'` is
  recorded, then `a'` is itself rewritten to `a''`, and a caller following
  `a` lands on the intermediate block that no longer matches anything."
  [substitutions added]
  (into (into {} (map (fn [[k v]] [k (get added v v)])) substitutions)
        added))

(defn propagate
  "Apply SUBSTITUTIONS across the blocks reachable from ROOTS until a fixpoint.

  Returns the complete substitution map, including the transitive replacements
  discovered along the way, plus the new blocks to persist."
  [root roots substitutions]
  (loop [substitutions substitutions
         blocks (reachable-blocks root roots)
         produced []
         round 0]
    (when (> round max-propagation-rounds)
      (fail! :codebase/propagation-did-not-converge {:rounds round}))
    (let [rewrites (into {}
                         (keep (fn [[cid block]]
                                 (let [result (ir/substitute-dependencies block substitutions)]
                                   (when (:changed? result) [cid result]))))
                         blocks)]
      (if (empty? rewrites)
        {:substitutions substitutions :blocks produced}
        (let [added (into {} (map (fn [[cid {new-cid :cid}]] [cid new-cid])) rewrites)
              rewritten (into {} (map (fn [[_ {:keys [cid block]}]] [cid block])) rewrites)]
          (recur (compose substitutions added)
                 ;; The rewritten block replaces its predecessor in the working
                 ;; set: the old bytes are still in the store and still valid,
                 ;; but nothing reachable from the new head points at them.
                 (into (apply dissoc blocks (keys rewrites)) rewritten)
                 (into produced (map (fn [[_ {:keys [cid block]}]] [cid block])) rewrites)
                 (inc round)))))))

(defn plan-with
  "Describe what COMPILE-FN's definitions would do to NAMESPACE.

  COMPILE-FN receives the name -> CID map the namespace currently selects and
  returns `{:definitions {name {:cid :block ...}}}`. Taking a function rather
  than source forms is what lets one planner serve both identity layers: the
  caller decides whether a definition is hashed from the surface IR or from
  checked KIR, and everything after that -- what counts as added, what an
  update carries along, how the head advances -- is the same either way.

  Nothing is written: the plan is data, so a caller can show what an update
  would do before choosing to commit it."
  [root namespace compile-fn]
  (let [{:keys [head bindings]} (current-bindings root namespace)
        seeded (into {} (map (fn [[name cid]] [(symbol name) cid])) bindings)
        compiled (compile-fn seeded)
        results (:definitions compiled)
        classified
        (into (sorted-map)
              (map (fn [[name {:keys [cid]}]]
                     (let [previous (get bindings (str name))]
                       [(str name) {:cid cid
                                    :previous previous
                                    :status (cond (nil? previous) :added
                                                  (= previous cid) :unchanged
                                                  :else :updated)}])))
              results)
        replaced (into {} (keep (fn [[_ {:keys [status previous cid]}]]
                                  (when (= :updated status) [previous cid])))
                       classified)
        ;; Only names the scratch did NOT redefine can be carried: a name the
        ;; author rewrote by hand must keep their version, not a substituted one.
        authored (set (keys classified))
        carry-roots (vec (keep (fn [[name cid]] (when-not (contains? authored name) cid))
                               bindings))
        {:keys [substitutions blocks]} (if (seq replaced)
                                         (propagate root carry-roots replaced)
                                         {:substitutions {} :blocks []})
        propagated (into (sorted-map)
                         (keep (fn [[name cid]]
                                 (let [next (get substitutions cid)]
                                   (when (and next (not (contains? authored name)))
                                     [name {:previous cid :cid next :status :propagated}]))))
                         bindings)
        next-bindings (reduce (fn [acc [name {:keys [cid]}]] (assoc acc name cid))
                              (into (sorted-map) bindings)
                              (concat classified propagated))]
    {:namespace namespace
     :head head
     :definitions classified
     :propagated propagated
     :substitutions substitutions
     :bindings next-bindings
     :new-blocks (into (vec (mapcat staged-blocks (vals results))) blocks)
     :changed? (or (seq blocks)
                   (some #(not= :unchanged (:status (val %))) classified)
                   false)}))

(defn plan
  "Plan an update whose definitions are hashed from the surface semantic IR."
  [root namespace forms]
  (plan-with root namespace
             #(semantic/compile-definitions forms {:definitions %})))

(defn commit!
  "Persist PLAN's blocks and CAS the namespace head onto the resulting commit.

  The head is only advanced when it is still the head the plan was computed
  against, so a concurrent author cannot be silently overwritten."
  [root {:keys [namespace head bindings new-blocks] :as plan}]
  (doseq [[cid block] new-blocks]
    (store/put-block! root cid block))
  (let [commit (store/commit-namespace! root namespace bindings head)]
    (assoc plan :head (:cid commit) :committed? true)))

(defn update-namespace!
  "Plan and commit in one step."
  [root namespace forms]
  (commit! root (plan root namespace forms)))
