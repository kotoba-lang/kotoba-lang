(ns kotoba.lang.security-adoption-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [kotoba.lang.release-admission]))

(def required-security-sha
  "The one place this repo states which shared security baseline it runs on.

  It used to live here only, while `security-adoption.edn` -- the config the
  SHARED verifier reads -- carried its own copy. Nothing in this repo read that
  file, so its copy drifted unnoticed: it declared 49fc4ce while deps.edn had
  been on 65811c9. Two sources of truth for one fact, one of them unchecked and
  wrong. The test now binds all three together."
  "65811c9d6878e881357e98f9f9fe6a60aeff7070")

(deftest central-security-control-is-an-immutable-runtime-dependency
  (let [deps (edn/read-string (slurp "deps.edn"))
        security (get-in deps [:deps 'io.github.kotoba-lang/security])]
    (is (= "https://github.com/kotoba-lang/security.git" (:git/url security)))
    (is (= required-security-sha (:git/sha security)))
    (is (find-ns 'kotoba.security.capability))
    (is (find-ns 'kotoba.security.crypto-policy))
    (is (find-ns 'kotoba.security.qualification))))

(deftest declared-security-baseline-matches-the-pinned-one
  (let [adoption (edn/read-string (slurp "security-adoption.edn"))]
    (is (= required-security-sha (:security/git-sha adoption))
        "security-adoption.edn declares a baseline this repo does not run")
    (is (= :kotoba-lang (:consumer/id adoption)))))
