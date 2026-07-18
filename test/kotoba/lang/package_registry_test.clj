(ns kotoba.lang.package-registry-test
  "Package registry resolve: name+version → CID-pinned lock entry."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.package-contract :as contract]
            [kotoba.lang.package-registry :as registry]))

(def example-path "lang/package-registry/example-registry.edn")

(defn read-edn [path]
  (edn/read-string (slurp (io/file path))))

(deftest example-registry-validates
  (let [r (registry/validate (read-edn example-path))]
    (is (:ok? r) (str (:problems r)))))

(deftest resolve-known-package
  (let [reg (read-edn example-path)
        resolved (registry/resolve-record reg "kotoba-lang/json" "0.1.0")]
    (is (:ok? resolved))
    (is (= "kotoba-lang/json" (get-in resolved [:record :registry/name])))
    (let [dep (registry/record->lock-dep (:record resolved))]
      (is (nil? (contract/lockfile-error
                 {:kotoba.lock/version 1 :deps [dep]}
                 {:declared-capabilities [:graph-read]})))
      (is (contract/cid? (:dep/repo-rid dep)))
      (is (contract/cid? (:dep/manifest-cid dep)))
      (is (contract/cid? (:dep/tree-cid dep))))))

(deftest resolve-missing-package-fails-closed
  (let [reg (read-edn example-path)
        r (registry/resolve-record reg "kotoba-lang/missing" "9.9.9")]
    (is (false? (:ok? r)))
    (is (= :registry/not-found (:problem (first (:problems r)))))))

(deftest lock-from-requests-builds-admissible-lock
  (let [reg (read-edn example-path)
        out (registry/lock-from-requests
             reg
             [{:name "kotoba-lang/json" :version "0.1.0"
               :capabilities [:graph-read]}])]
    (is (:ok? out) (str (:problems out)))
    (is (= 1 (count (:deps out))))
    (is (nil? (contract/lockfile-error
               (:lock out)
               {:declared-capabilities [:graph-read]})))))

(deftest lock-from-requests-rejects-unknown
  (let [reg (read-edn example-path)
        out (registry/lock-from-requests
             reg
             [{:name "kotoba-lang/json" :version "0.1.0"}
              {:name "nope/pkg" :version "1.0.0"}])]
    (is (false? (:ok? out)))
    (is (some #(= :registry/not-found (:problem %)) (:problems out)))))

(deftest version-only-request-predicate
  (is (true? (registry/version-only-request?
              {:dep/name "kotoba-lang/json" :dep/version "0.1.0"})))
  (is (false? (registry/version-only-request?
               {:dep/name "kotoba-lang/json"
                :dep/version "0.1.0"
                :dep/repo-rid "bafyreic6qxprbpcffqfxcsch6betglxeipy7o46ylum3fss6gizt3vxjoy"
                :dep/manifest-cid "bafyreihimm4ay6fhbteh7eob6lgqez762bsr6nkw2req67h3kqm6dfuioe"}))))

(deftest invalid-cid-in-record-rejected
  (let [reg {:kotoba.registry/version 1
             :records [{:registry/name "x/y"
                        :registry/version "1.0.0"
                        :registry/repo-rid "not-a-cid"
                        :registry/commit "abc"
                        :registry/tree-cid "also-bad"
                        :registry/manifest-cid "nope"
                        :registry/signers ["did:key:z"]
                        :registry/capabilities []}]}
        v (registry/validate reg)]
    (is (false? (:ok? v)))
    (is (some #(= :registry/cid-invalid (:problem %)) (:problems v)))))
