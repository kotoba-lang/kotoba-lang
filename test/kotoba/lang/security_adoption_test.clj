(ns kotoba.lang.security-adoption-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [kotoba.lang.release-admission]))

(def required-security-sha
  "23caaefa6ca35904f9d0c2dc0cbb3bec91ae8956")

(deftest central-security-control-is-an-immutable-runtime-dependency
  (let [deps (edn/read-string (slurp "deps.edn"))
        security (get-in deps [:deps 'io.github.kotoba-lang/security])]
    (is (= "https://github.com/kotoba-lang/security.git" (:git/url security)))
    (is (= required-security-sha (:git/sha security)))
    (is (find-ns 'kotoba.security.capability))
    (is (find-ns 'kotoba.security.crypto-policy))
    (is (find-ns 'kotoba.security.qualification))))
