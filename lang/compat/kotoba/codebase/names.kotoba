(ns kotoba.codebase.names
  "Names as a lookup layer over hashes.

  Everything here is convenience: the codebase is addressed by CID, and a name
  or a short hash is only a way for a person to say one. That has one hard
  consequence -- an abbreviation must be REJECTED when it is ambiguous rather
  than resolved to whichever candidate sorts first, because the whole point of
  the hash is that it names exactly one thing."
  (:require [clojure.string :as str]
            [kotoba.codebase.index :as index]
            [kotoba.codebase.ir :as ir]
            [kotoba.codebase.store :as store]))

(def min-short-hash-length
  "Below this an abbreviation is refused outright: a one- or two-character
  prefix is not a way of saying a specific definition, and accepting it invites
  a caller to build a habit that silently becomes ambiguous as the store grows."
  6)

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn- bindings-of [root namespace]
  (if-let [head (and namespace (store/head root namespace))]
    (:bindings (store/namespace-view root head))
    {}))

(defn- present? [root cid]
  (try (store/get-block root cid) true
       (catch clojure.lang.ExceptionInfo _ false)))

(defn- searchable-cids
  "The CIDs a `#`-abbreviation may resolve against, and the scope that was
  actually searched.

  A local directory can list every block it holds, which is the widest and
  best answer: a hash is usable before any name points at it. A store with no
  directory to read cannot, and since ADR-2608580000 S1 it says so instead of
  reporting an empty codebase. Rather than let abbreviation resolution simply
  stop working there, fall back to the definitions the namespace reaches — and
  RETURN the scope, so a caller that got `:namespace-closure` knows the search
  was narrower than `:store` and a miss is not proof of absence."
  [root bindings]
  (try
    {:cids (store/block-cids root) :scope :store}
    (catch clojure.lang.ExceptionInfo error
      (if (= :codebase/enumeration-unsupported (:problem (ex-data error)))
        {:cids (vec (:definitions (index/scan root (vals bindings))))
         :scope :namespace-closure}
        (throw error)))))

(defn resolve-token
  "Resolve a user-supplied TOKEN to one definition CID.

  Accepted, in order: a name selected by NAMESPACE, a full CID present in the
  store, and a `#`-prefixed hash abbreviation."
  [root namespace token]
  (when-not (and (string? token) (seq token))
    (fail! :codebase/invalid-token {:token token}))
  (let [bindings (bindings-of root namespace)]
    (cond
      (contains? bindings token)
      {:cid (get bindings token) :name token :resolved-by :name}

      (str/starts-with? token "#")
      (let [prefix (subs token 1)]
        (when (< (count prefix) min-short-hash-length)
          (fail! :codebase/hash-abbreviation-too-short
                 {:token token :minimum min-short-hash-length}))
        (let [{:keys [cids scope]} (searchable-cids root bindings)
              candidates (filterv #(str/starts-with? % prefix) cids)]
          (case (count candidates)
            0 (fail! :codebase/hash-not-found {:token token :scope scope})
            1 (let [cid (first candidates)]
                {:cid cid
                 :name (some (fn [[name bound]] (when (= bound cid) name)) bindings)
                 :resolved-by :short-hash
                 :scope scope})
            (fail! :codebase/ambiguous-hash
                   {:token token :candidates candidates :scope scope}))))

      (present? root token)
      {:cid token
       :name (some (fn [[name cid]] (when (= cid token) name)) bindings)
       :resolved-by :cid}

      :else
      (fail! :codebase/unresolved-token {:token token :namespace namespace}))))

(defn names-of
  "Every name NAMESPACE selects for CID. A definition may have many, or none."
  [root namespace cid]
  (vec (sort (keep (fn [[name bound]] (when (= bound cid) name))
                   (bindings-of root namespace)))))

(defn search
  "Names in NAMESPACE containing QUERY, with the CID each selects."
  [root namespace query]
  (let [query (str/lower-case (str query))]
    (into (sorted-map)
          (filter (fn [[name _]] (str/includes? (str/lower-case name) query)))
          (bindings-of root namespace))))

(defn dependents
  "Names in NAMESPACE whose definition transitively depends on CID.

  This is the set an update to CID would carry along, so it answers `what does
  changing this affect` before the change is made.

  One pass over the namespace's closure, then a walk over edges already in
  memory. The previous shape asked the store the same question once per
  binding, which re-read every shared dependency once per dependent — see
  `kotoba.codebase.index`."
  [root namespace cid]
  (let [bindings (bindings-of root namespace)
        affected (index/depending-on (index/scan root (vals bindings)) cid)]
    (vec (sort (keep (fn [[name bound]]
                       (when (and (not= bound cid) (contains? affected bound))
                         name))
                     bindings)))))

(defn dependencies
  "Definition CIDs the definition at CID depends on, with any selected names."
  [root namespace cid]
  (let [bindings (bindings-of root namespace)
        block (store/get-block root cid)]
    (mapv (fn [dependency]
            {:cid dependency
             :names (vec (sort (keep (fn [[name bound]]
                                       (when (= bound dependency) name))
                                     bindings)))})
          (ir/outbound-cids block))))
