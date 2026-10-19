(ns kotoba.codebase.diff
  "Semantic diff, rebase, and conflict resolution over namespace commits.

  Textual diff answers `which lines changed`. In a content-addressed codebase
  that question is already answered by the CIDs, and the interesting ones are
  different:

  - was a definition **authored**, or did it merely move because something it
    depends on moved? Both change the CID, and treating them the same makes
    every review of a one-line change look like a rewrite of everything
    downstream;
  - was a definition **renamed**? The CID is identical and only the namespace
    changed, which is exactly the case a name-based diff cannot see and a
    hash-based one gets for free;
  - did the **interface** change, or only the body? A body change is a fix; an
    interface change is a break, and callers need to be told which one they are
    looking at.

  Rebase follows from the same fact. Replaying an edit onto a new base does not
  mean re-applying a patch -- it means taking the definitions the branch
  authored and propagating them onto the base's graph, which is the operation
  `authoring` already performs for an ordinary update."
  (:require [kotoba.codebase.ir :as ir]
            [kotoba.codebase.semantic-code :as semantic]
            [kotoba.codebase.store :as store]))

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn- bindings-of [root commit]
  (:bindings (store/namespace-view root commit)))

(defn- interface-cid
  "The link a definition commits its type/interface to, whichever layer it is."
  [root cid]
  (let [block (store/get-block root cid)]
    (when-let [link (or (get block "interface") (get block "type"))]
      (ir/link->cid link))))

(defn- dependencies-of [root cid]
  (set (ir/declared-dependencies (store/get-block root cid))))

(defn classify-change
  "Why a name's CID moved between two commits.

  `:authored` vs `:propagated` is the distinction that matters for review, and
  it is decidable without any history: if the two definitions differ only in
  which dependency CIDs they name, nothing about this definition was written --
  something underneath it moved."
  [root before after]
  (let [before-block (store/get-block root before)
        interface-changed? (not= (interface-cid root before) (interface-cid root after))
        before-deps (dependencies-of root before)
        after-deps (dependencies-of root after)
        ;; Rewrite the old block's dependencies to the new ones and see whether
        ;; it becomes the new block. If it does, the body was never touched.
        rewritten (when (= (count before-deps) (count after-deps))
                    (:cid (ir/substitute-dependencies
                           before-block
                           (zipmap (sort before-deps) (sort after-deps)))))]
    {:name-changed? false
     :interface-changed? interface-changed?
     :kind (cond
             interface-changed? :interface-changed
             (= rewritten after) :propagated
             :else :authored)
     :dependencies-added (vec (sort (remove before-deps after-deps)))
     :dependencies-removed (vec (sort (remove after-deps before-deps)))}))

(defn diff
  "Compare two namespace commits.

  Returns `{:added :removed :changed :renamed :unchanged}`. A rename is
  reported separately from an add plus a remove, because the definition did not
  change at all -- only what this namespace calls it did, and that is the one
  edit content addressing makes free."
  [root before-commit after-commit]
  (let [before (bindings-of root before-commit)
        after (bindings-of root after-commit)
        before-cids (into {} (map (fn [[name cid]] [cid name])) before)
        after-cids (into {} (map (fn [[name cid]] [cid name])) after)
        added (into (sorted-map) (remove (fn [[name _]] (contains? before name))) after)
        removed (into (sorted-map) (remove (fn [[name _]] (contains? after name))) before)
        renamed (into (sorted-map)
                      (keep (fn [[name cid]]
                              (when-let [old-name (get before-cids cid)]
                                (when-not (contains? after old-name)
                                  [old-name {:to name :cid cid}]))))
                      added)
        renamed-new-names (into #{} (map (comp :to val)) renamed)
        changed (into (sorted-map)
                      (keep (fn [[name cid]]
                              (let [previous (get before name)]
                                (when (and previous (not= previous cid))
                                  [name (assoc (classify-change root previous cid)
                                               :previous previous :cid cid)]))))
                      after)]
    {:before before-commit
     :after after-commit
     :added (into (sorted-map) (remove (fn [[name _]] (renamed-new-names name))) added)
     :removed (into (sorted-map)
                    (remove (fn [[name _]] (contains? renamed name)))
                    removed)
     :renamed renamed
     :changed changed
     :unchanged (into (sorted-map)
                      (filter (fn [[name cid]] (= cid (get before name))))
                      after)
     :authored (vec (sort (keep (fn [[name change]]
                                  (when (not= :propagated (:kind change)) name))
                                changed)))
     :propagated (vec (sort (keep (fn [[name change]]
                                    (when (= :propagated (:kind change)) name))
                                  changed)))
     :unaffected-definitions (vec (sort (keys (select-keys after-cids (keys before-cids)))))}))

;; ---------------------------------------------------------------------------
;; Conflict resolution

(defn conflicts
  "Three-way merge conflicts between two commits over a base, as data."
  [root base-commit left-commit right-commit]
  (:conflicts (store/three-way-merge (bindings-of root base-commit)
                                     (bindings-of root left-commit)
                                     (bindings-of root right-commit))))

(defn resolve-conflicts
  "Apply an explicit CHOICES map to a three-way merge and return the bindings.

  Choices are `:left`, `:right`, `:base`, `:delete`, or a CID. Nothing is
  guessed: a conflict left unresolved keeps the merge unresolved, because
  silently picking a side is how a merge tool loses work that nobody notices
  until much later."
  [root base-commit left-commit right-commit choices]
  (let [base (bindings-of root base-commit)
        left (bindings-of root left-commit)
        right (bindings-of root right-commit)
        {:keys [bindings conflicts]} (store/three-way-merge base left right)
        unresolved (remove #(contains? choices (:name %)) conflicts)]
    (if (seq unresolved)
      {:resolved? false :conflicts (vec unresolved)}
      {:resolved? true
       :bindings (reduce (fn [acc {:keys [name]}]
                           (let [choice (get choices name)
                                 cid (case choice
                                       :left (get left name)
                                       :right (get right name)
                                       :base (get base name)
                                       :delete nil
                                       choice)]
                             (when (and (string? cid) (not= choice :delete))
                               ;; A chosen CID must be a definition this store
                               ;; actually holds; resolving to a hash nobody has
                               ;; would produce a head pointing at nothing.
                               (store/get-block root cid))
                             (if cid (assoc acc name cid) (dissoc acc name))))
                         bindings
                         conflicts)})))

;; ---------------------------------------------------------------------------
;; Rebase

(defn rebase-plan
  "Replay the definitions BRANCH authored on top of BASE.

  Not a patch replay. The branch's authored definitions are already complete
  values, so moving them onto another base means binding them there and
  propagating -- the same operation an ordinary update performs, which is why
  a rebase cannot produce a state an update could not.

  `:propagate` is supplied by the caller (`authoring/propagate`) so this
  namespace stays free of the authoring cycle."
  [root base-commit branch-commit propagate]
  (let [merge-base (or base-commit (fail! :diff/base-required {}))
        changes (diff root merge-base branch-commit)
        ;; Only what the branch AUTHORED moves. A definition it merely carried
        ;; along will be re-derived by propagation on the new base, and taking
        ;; the branch's version instead would pin a dependent to a graph that
        ;; no longer exists.
        authored (into {}
                       (concat (map (fn [name] [name (get-in changes [:changed name :cid])])
                                    (:authored changes))
                               (:added changes)))]
    {:base merge-base
     :branch branch-commit
     :authored (into (sorted-map) authored)
     :dropped (vec (:propagated changes))
     :apply (fn [onto-commit]
              (let [onto (bindings-of root onto-commit)
                    replaced (into {}
                                   (keep (fn [[name cid]]
                                           (when-let [previous (get onto name)]
                                             (when (not= previous cid) [previous cid]))))
                                   authored)
                    carried (vec (keep (fn [[name cid]]
                                         (when-not (contains? authored name) cid))
                                       onto))
                    {:keys [substitutions blocks]} (if (seq replaced)
                                                     (propagate root carried replaced)
                                                     {:substitutions {} :blocks []})]
                {:bindings (into (sorted-map)
                                 (merge (into {} (map (fn [[name cid]]
                                                        [name (get substitutions cid cid)]))
                                              onto)
                                        authored))
                 :blocks blocks
                 :substitutions substitutions}))}))

(defn definition-diff
  "Compare two definitions directly, without a namespace.

  The reason `diff` needs no history: two hashes are comparable on their own."
  [root before after]
  (if (= before after)
    {:identical? true}
    (assoc (classify-change root before after)
           :identical? false
           :before before
           :after after
           :before-interface (interface-cid root before)
           :after-interface (interface-cid root after))))

(defn describe
  "A short, stable rendering of a diff for a terminal."
  [changes]
  (->> (concat (map (fn [[name _]] (str "+ " name)) (:added changes))
               (map (fn [[name _]] (str "- " name)) (:removed changes))
               (map (fn [[old {:keys [to]}]] (str "~ " old " -> " to " (rename)")) (:renamed changes))
               (map (fn [[name {:keys [kind]}]] (str "* " name " (" (clojure.core/name kind) ")"))
                    (:changed changes)))
       sort
       vec))

(defn commit-cid
  "Identity of a diff itself, so a review can be referred to by hash."
  [changes]
  (semantic/source-cid (pr-str (describe changes))))
