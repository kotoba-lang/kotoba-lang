(ns kotoba.lang.malicious-source-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.malicious-source :as malicious]))

(def corpus-root "lang/malicious-source/")
(def manifest
  (edn/read-string (slurp (str corpus-root "manifest.edn"))))

(defn load-case [file]
  (edn/read-string (slurp (str corpus-root file))))

(deftest normative-malicious-corpus-covers-all-five-attack-classes
  (let [result (malicious/validate-corpus manifest load-case)]
    (is (:valid? result) (pr-str result))
    (is (= (:required result) (:covered result)))
    (is (= 5 (count (:cases result))))
    (is (every? :passed? (:cases result)))))

(deftest parser-limits-run-before-reader-allocation
  (testing "source bytes, token length, and nesting have stable reject codes"
    (is (= :parser/source-size
           (:code (malicious/safe-read-decision
                   (apply str (repeat 20 "x"))
                   {:max-source-bytes 10}))))
    (is (= :parser/token-size
           (:code (malicious/safe-read-decision
                   "abcdefghijk"
                   {:max-token-chars 10}))))
    (is (= :parser/nesting
           (:code (malicious/safe-read-decision
                   "[[[0]]]" {:max-nesting-depth 2}))))))

(deftest unknown-attack-classes-and-unregistered-reader-tags-fail-closed
  (is (= :corpus/unknown-attack-class
         (:code (malicious/evaluate-case {:attack-class :invented}))))
  (is (= :reader/tag-escape
         (:code (malicious/safe-read-decision
                 "#evil/tag {:payload 1}" {})))))

(deftest reader-discard-prefixes-cannot-bypass-the-nesting-budget
  (let [source (str (apply str (repeat 5000 "#_ ")) "0")
        result (malicious/safe-read-decision source {})]
    (is (false? (:allowed? result)))
    (is (= :reader/discard (:code result)))))
