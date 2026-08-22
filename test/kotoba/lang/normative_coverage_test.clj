(ns kotoba.lang.normative-coverage-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [kotoba.lang.normative-coverage :as coverage]))

(deftest guest-grammar-rules-are-consumer-bound
  (let [authority (slurp "lang/guest-grammar.edn")
        rules (coverage/source-rules
               {:kind :guest-grammar-rules :path "lang/guest-grammar.edn"})]
    (is (> (count rules) 100))
    (is (= authority (slurp "../kotoba/resources/kotoba/lang/guest-grammar.edn")))
    (is (= authority (slurp "../compiler/resources/kotoba/lang/guest-grammar.edn")))))

(deftest adr-2607279200-elaboration-contract-is-bound
  (let [grammar (edn/read-string (slurp "lang/guest-grammar.edn"))
        pipeline (edn/read-string (slurp "lang/elaboration-pipeline.edn"))
        stage-ids (mapv :id (:stages pipeline))]
    (is (= "adr-2607279200-kotoba-clojure-shaped-safety-elaboration-migration"
           (:kotoba.lang.guest-grammar/migration-adr grammar)
           (:kotoba.lang.elaboration-pipeline/adr pipeline)))
    (is (= [:closed-reader
            :bounded-pure-desugar
            :name-and-module-resolution
            :type-and-schema-inference
            :interprocedural-effect-inference
            :implicit-ability-parameter-elaboration
            :typed-hir-kir
            :definition-cid
            :restricted-target
            :host-binding]
           stage-ids))
    (is (= [:source-fidelity :semantic-parity :safety :host-parity
            :performance :release :rollout]
           (:release-gates pipeline)))
    (is (.isFile (io/file "docs/kotoba-centered-migration-plan.md")))
    (is (not (.exists (io/file "resources/kotoba/lang/guest-grammar.edn")))
        "a stale in-repository grammar resource would create a second authority")))

(deftest every-registered-normative-rule-maps-to-an-existing-test
  (let [result (coverage/validate-registry)]
    (is (:valid? result) (pr-str result))
    (is (> (:rule-count result) 150))
    (is (= (:rule-count result) (:mapped-rule-count result)))))

(deftest all-declared-normative-sources-are-attestable
  (let [result (coverage/validate-registry)]
    (is (:attestable? result) (pr-str result))
    (is (empty? (:residual-gaps result)))))
