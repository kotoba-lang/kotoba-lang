(ns kotoba.lang.semantic-code-contract-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(defn read-edn [path]
  (edn/read-string (slurp (io/file path))))

(defn unblob [value]
  (if (string? value)
    (try
      (let [parsed (edn/read-string value)]
        (if (coll? parsed) parsed value))
      (catch Exception _ value))
    value))

(deftest semantic-code-contract-is-machine-readable
  (let [contract (first (read-edn "lang/semantic-code.edn"))]
    (is (= 1 (:kotoba.lang.semantic/version contract)))
    (is (= :c1 (:kotoba.lang.semantic/status contract)))
    (is (= :cidv1-dag-cbor-sha2-256
           (:kotoba.lang.semantic/hash-suite contract)))
    (is (= [:source-cid :definition-cid :artifact-cid]
           (unblob (:kotoba.lang.semantic/identity-layers contract))))
    (is (some #{:resolved-dependency-cids}
              (unblob (:kotoba.lang.semantic/hash-inputs contract))))
    (is (some #{:definition-name}
              (unblob (:kotoba.lang.semantic/excluded-inputs contract))))))

(deftest semantic-conformance-manifest-and-fixtures-are-complete
  (let [manifest (first (read-edn "lang/semantic-conformance/manifest.edn"))
        cases (unblob (:kotoba.lang.semantic.conformance/cases manifest))]
    (is (= 1 (:kotoba.lang.semantic.conformance/version manifest)))
    (is (= #{:same-cid :same-definition-cids :different-cid :expect-error}
           (set (map :relation cases))))
    (doseq [case cases
            path (keep case [:left :right :file])]
      (testing path
        (is (.isFile (io/file "lang/semantic-conformance" path)))))))
