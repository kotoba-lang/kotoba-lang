(ns kotoba.lang.definition-patch-portable-test
  "The genuinely portable slice of kotoba.lang.definition-patch-test: the
  apply-patch/apply-share state machine over synthetic definition-CIDs and a
  synthetic pure/effectful definition pair, built locally instead of read from
  `lang/code-identity-vectors.edn`. Nothing here needs the frozen vectors to
  be a SPECIFIC value -- only that patch/share round-trips real
  `identity/definition-cid` output, which it does on both hosts.

  What stays in kotoba.lang.definition-patch-test (.clj):
  - `interchange-contract-pins-the-authority-hasher-only` and
    `measured-parallel-hasher-cids-match-the-identity-contract`, which read
    `lang/code-identity.edn` and `lang/definition-patch.edn`.
  - the `:parallel-hasher` sub-tests that (a) look up the measured
    typed-code CID from `lang/code-identity.edn` and (b) `slurp` this
    namespace's own source file to check it names no `kotoba.codebase`
    require -- a JVM `java.io.FileNotFoundException` idiom with no
    ClojureScript equivalent, appropriately, since the claim being checked is
    about a require form the compiler resolves at JVM classload time.
  - `semantic-code-is-absent-here`, a repo-file-absence check."
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer [deftest is testing] :include-macros true])
            [kotoba.lang.code-identity :as identity]
            [kotoba.lang.definition-patch :as patch]))

(def ^:private pure-definition
  {:definition/profile-version 4
   :definition/desugar-contract-version 1
   :definition/kir {:op :const :value 1}
   :definition/effect-row #{}
   :definition/interface {:arity 0}
   :definition/dependencies []})

(def ^:private http-definition
  (assoc pure-definition :definition/effect-row #{:host/http}))

(def ^:private pure-cid (identity/definition-cid pure-definition))
(def ^:private http-cid (identity/definition-cid http-definition))

(deftest apply-patch-of-definition-cids
  (testing "add, replace, remove are name mappings over frozen authority CIDs"
    (let [added (patch/apply-patch {} {:ops [{:op :add :name "math/one" :definition-cid pure-cid}]})]
      (is (:ok? added))
      (is (= {"math/one" pure-cid} (:bindings added)))
      (let [replaced (patch/apply-patch
                      (:bindings added)
                      {:ops [{:op :replace :name "math/one" :from pure-cid :to http-cid}]})]
        (is (:ok? replaced))
        (is (= {"math/one" http-cid} (:bindings replaced)))
        (is (:ok? (patch/apply-patch
                   (:bindings replaced)
                   {:ops [{:op :remove :name "math/one" :definition-cid http-cid}]}))))))
  (testing "rename is remove plus add of the same CID -- the definition does not move"
    (let [result (patch/apply-patch
                  {"math/one" pure-cid}
                  {:ops [{:op :remove :name "math/one" :definition-cid pure-cid}
                         {:op :add :name "math/unit" :definition-cid pure-cid}]})]
      (is (:ok? result))
      (is (= {"math/unit" pure-cid} (:bindings result)))))
  (testing "replace cannot silently clobber"
    (is (= :patch/replace-from-mismatch
           (:reason (patch/apply-patch
                     {"math/one" pure-cid}
                     {:ops [{:op :replace :name "math/one" :from http-cid :to http-cid}]}))))))

(deftest share-rehashes-payloads-through-the-authority-facade
  (let [share {:patch {:ops [{:op :add :name "math/one" :definition-cid pure-cid}]}
               :definitions {pure-cid pure-definition}}]
    (is (= pure-cid (identity/definition-cid pure-definition))
        "the spike consumes the facade; it does not mint a second CID")
    (is (:ok? (patch/apply-share {} share)))
    (testing "a payload that does not hash to the claimed CID is refused"
      (is (= :patch/share-hash-mismatch
             (:reason (patch/apply-share
                       {}
                       (assoc-in share [:definitions]
                                 {pure-cid (assoc pure-definition :definition/kir {:op :const :value 2})}))))))
    (testing "applying a share is not an eval grant"
      (let [result (patch/apply-share {} share)]
        (is (:ok? result))
        (is (= #{:ok? :bindings} (set (keys result))))))))

(deftest parallel-hasher-fails-closed-on-synthetic-cids
  (testing "an explicit typed-code hasher is refused"
    (is (= :patch/parallel-hasher
           (:reason (patch/apply-patch
                     {}
                     {:hasher :kotoba.codebase/typed-code
                      :ops [{:op :add :name "math/double"
                             :definition-cid pure-cid}]})))))
  (testing "a measured typed-code CID cannot travel as :definition-cid"
    (doseq [cid patch/parallel-hasher-cids]
      (is (= :patch/parallel-hasher-cid
             (:reason (patch/apply-patch
                       {}
                       {:ops [{:op :add :name "math/double" :definition-cid cid}]})))))))

(deftest source-tree-bytes-are-not-the-unit
  (is (= :patch/source-tree-unit
         (:reason (patch/apply-patch
                   {}
                   {:source-tree-cid "bafyreiarrzdga4uwvk6miw6rdndih4z56xgtd4qz25tb3gxld7toolyaiu"
                    :ops [{:op :add :name "math/one" :definition-cid pure-cid}]}))))
  (is (= :patch/source-tree-unit
         (:reason (patch/apply-patch
                   {}
                   {:source-bytes "(defn one [] 1)"
                    :ops [{:op :add :name "math/one" :definition-cid pure-cid}]}))))
  (is (= :patch/cid-invalid
         (:reason (patch/apply-patch
                   {}
                   {:ops [{:op :add :name "math/one"
                           :definition-cid "(defn one [] 1)"}]})))))

(deftest a-patch-does-not-grant-authority
  (is (= :patch/identity-is-not-authority
         (:reason (patch/apply-patch
                   {}
                   {:grants [:code/eval]
                    :ops [{:op :add :name "math/one" :definition-cid pure-cid}]}))))
  (is (= :patch/identity-is-not-authority
         (:reason (patch/apply-share
                   {}
                   {:eval :host
                    :patch {:ops [{:op :add :name "math/one" :definition-cid pure-cid}]}})))))
