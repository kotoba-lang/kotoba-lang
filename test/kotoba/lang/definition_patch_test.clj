(ns kotoba.lang.definition-patch-test
  "Spike tests for definition patch/share.

  The unit under test is a set of :definition-cid ops plus name mappings,
  not source-tree bytes. The parallel typed-code hasher is refused, not
  called."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.code-identity :as identity]
            [kotoba.lang.definition-patch :as patch]))

(defn- read-edn [path]
  (edn/read-string (slurp (io/file path))))

(def ^:private vectors
  (read-edn "lang/code-identity-vectors.edn"))

(def ^:private identity-contract
  (read-edn "lang/code-identity.edn"))

(def ^:private patch-contract
  (read-edn "lang/definition-patch.edn"))

(defn- frozen [id]
  (first (filter #(= id (:id %)) (:vectors vectors))))

(def ^:private pure-cid
  (:definition-cid (frozen :pure-const)))

(def ^:private http-cid
  (:definition-cid (frozen :effect-row-http)))

(deftest interchange-contract-pins-the-authority-hasher-only
  (testing "patch/share consumes payload-v2 :definition-cid; it does not grow a second hash"
    (is (= :kotoba.kir/definition-identity
           (get-in patch-contract [:hasher :authority])))
    (is (= 2 (get-in patch-contract [:hasher :payload-version])))
    (is (= :kotoba.codebase/typed-code
           (get-in patch-contract [:hasher :refused])))
    (is (= :kotoba.kir/definition-identity
           (get-in identity-contract [:identity-implementations :authority])))
    (is (= :implemented (get-in identity-contract [:identities :definition-cid :status])))
    (is (= :not-implemented (get-in identity-contract [:identities :source-tree-cid :status]))
        ":source-tree-cid stays unbuilt; a patch must not pretend it is the unit"))
  (testing "the language contract grew an interchange pointer, not a new identity algorithm"
    (is (= "lang/definition-patch.edn"
           (get-in identity-contract [:interchange :definition-patch :contract])))
    (is (= :kotoba.kir/definition-identity
           (get-in identity-contract [:interchange :definition-patch :hasher])))))

(deftest measured-parallel-hasher-cids-match-the-identity-contract
  (let [measured (into #{}
                       (map :typed-code)
                       (get-in identity-contract
                               [:identity-implementations :measured-difference :cases]))
        contract-denied (:measured-cids-are-not-definition-cids
                         (:parallel-hasher patch-contract))]
    (is (seq measured))
    (is (= measured patch/parallel-hasher-cids contract-denied))))

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
  (testing "rename is remove plus add of the same CID — the definition does not move"
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
  (let [definition (:definition (frozen :pure-const))
        share {:patch {:ops [{:op :add :name "math/one" :definition-cid pure-cid}]}
               :definitions {pure-cid definition}}]
    (is (= pure-cid (identity/definition-cid definition))
        "the spike consumes the facade; it does not mint a second CID")
    (is (:ok? (patch/apply-share {} share)))
    (testing "a payload that does not hash to the claimed CID is refused"
      (is (= :patch/share-hash-mismatch
             (:reason (patch/apply-share
                       {}
                       (assoc-in share [:definitions]
                                 {pure-cid (assoc definition :definition/kir {:op :const :value 2})}))))))
    (testing "applying a share is not an eval grant"
      (let [result (patch/apply-share {} share)]
        (is (:ok? result))
        (is (= #{:ok? :bindings} (set (keys result))))))))

(deftest parallel-hasher-fails-closed
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
                       {:ops [{:op :add :name "math/double" :definition-cid cid}]}))))))
  (testing "the matching authority CID from the same measurement is admitted"
    (let [authority-cid (->> (get-in identity-contract
                                     [:identity-implementations :measured-difference :cases])
                             (map :definition-identity)
                             first)]
      (is (:ok? (patch/apply-patch
                 {}
                 {:ops [{:op :add :name "math/double"
                         :definition-cid authority-cid}]})))))
  (testing "the spike never requires the parallel tree"
    (is (not (re-find #"\[kotoba\.codebase"
                      (slurp (io/file "src/kotoba/lang/definition_patch.cljc"))))
        "the keyword :kotoba.codebase/typed-code is the refuse name, not a require")
    (is (thrown? java.io.FileNotFoundException
                 (requiring-resolve 'kotoba.codebase.typed-code/definition-cid)))))

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

(deftest semantic-code-is-absent-here
  (is (not (.isFile (io/file "lang/semantic-code.edn")))
      "the older content-addressed-codebase artifact is 404; patch/share does not revive it"))
