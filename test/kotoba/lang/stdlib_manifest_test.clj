(ns kotoba.lang.stdlib-manifest-test
  "T4.1: frozen stdlib public module list."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import (java.security MessageDigest)))

(def manifest-path "lang/conformance/stdlib/manifest.edn")
(def core-path "lang/stdlib/core.kotoba")
(def conf-core-path "lang/conformance/stdlib/core.kotoba")

(defn- sha256-hex [bytes]
  (let [md (MessageDigest/getInstance "SHA-256")
        d (.digest md bytes)]
    (apply str (map #(format "%02x" %) d))))

(defn- load-manifest []
  (edn/read-string (slurp manifest-path)))

(defn- defn-names [source]
  (->> (re-seq #"(?m)^\s*\(defn\s+([^\s\[]+)" source)
       (map (comp symbol second))
       set))

(deftest stdlib-manifest-frozen-shape
  (let [m (load-manifest)
        core (first (:modules m))]
    (is (= 2 (:kotoba.lang.stdlib.manifest/version m)))
    (is (= :frozen (:kotoba.lang.stdlib.manifest/status m)))
    (is (= "T4.1" (:kotoba.lang.stdlib.manifest/wbs m)))
    (is (= :core (:id core)))
    (is (= :frozen (:status core)))
    (is (= 'stdlib.core (:namespace core))
        "a library module needs a namespace or nothing can require it")
    (is (set? (:public-names core)))
    (is (contains? (:public-names core) 'first-match))
    (is (not (contains? (:public-names core) 'string-from-i64))
        "string ops are language builtins, not prelude names")
    (is (contains? (get-in m [:language-builtins :string-ops]) 'string-from-i64))))

(deftest version-two-withdrew-the-names-the-language-took
  ;; Version 1 froze five names that are now RESERVED and eleven functions that
  ;; no longer admit. A frozen list is a promise about what a guest can call,
  ;; so each removal is recorded with the message that refuses it rather than
  ;; being dropped silently.
  (let [m (load-manifest)
        core (first (:modules m))
        withdrawn (:withdrawn m)]
    (is (= 2 (get-in m [:kotoba.lang.stdlib.manifest/revision :version])))
    (is (= :contraction (get-in m [:kotoba.lang.stdlib.manifest/revision :kind])))
    (doseq [name '[some option-some option-none option-some? option-value
                   option-none? ok err ok? err? unwrap-ok unwrap-err
                   find select-keys group-by update]]
      (is (contains? withdrawn name) (str name " must be accounted for"))
      (is (not (contains? (:public-names core) name))
          (str name " is withdrawn and must not be frozen"))
      (is (keyword? (:reason (get withdrawn name)))
          (str name " needs a reason")))
    (is (= 'first-match (:replaced-by (get withdrawn 'some))))
    (is (= #{} (:records core))
        "the records are gone; :records at the top level says why")
    (is (= :removed-in-version-2 (get-in m [:records :status])))
    (is (= 2 (count (get-in m [:records :two-independent-reasons]))))))

(deftest the-arity-limit-is-recorded-as-fixed-with-its-reproduction
  ;; The entry outlived the limit it named (amu #738, merge c085efe,
  ;; 2026-09-01). It is kept rather than deleted, because the reproduction is
  ;; the cheapest way to notice a regression -- so what this pins moves from
  ;; the refusal to the fix. An entry left saying :not-fixed-here would tell
  ;; the next reader to route around something that works.
  (let [m (load-manifest)]
    (is (= :fixed (get-in m [:arity-limit :status])))
    (is (nil? (get-in m [:arity-limit :not-fixed-here]))
        "the limit is gone; the entry must not still claim it is not fixed")
    (is (= "export does not name a declared function"
           (get-in m [:arity-limit :was :message]))
        "the message it used to refuse with stays readable, under :was")
    (is (str/includes? (get-in m [:arity-limit :reproduction]) "--source-path"))
    (is (str/includes? (get-in m [:arity-limit :now]) "twice$arity$2")
        "what it does now is recorded by the export names it actually gives")
    (is (= 738 (get-in m [:arity-limit :fixed-in :pr])))))

(deftest core-source-matches-public-names-and-sha
  (let [m (load-manifest)
        core (first (:modules m))
        src (slurp core-path)
        conf (slurp conf-core-path)
        names (defn-names src)
        expected (:public-names core)]
    (is (= src conf) "conformance mirror must match package SSoT")
    (is (= (:sha256 core) (sha256-hex (.getBytes src "UTF-8"))))
    (is (= expected names)
        (str "extra=" (pr-str (clojure.set/difference names expected))
             " missing=" (pr-str (clojure.set/difference expected names))))))

(deftest package-contract-lists-core-module
  (let [pkg (edn/read-string (slurp "lang/stdlib.edn"))
        m (load-manifest)
        core (first (:modules m))]
    (is (= 2 (:kotoba.stdlib/version pkg)))
    (is (= :core (:id (first (:kotoba.stdlib/artifacts pkg)))))
    (is (= 'stdlib.core (:namespace (first (:kotoba.stdlib/artifacts pkg)))))
    (is (= (:sha256 core) (:sha256 (first (:kotoba.stdlib/artifacts pkg)))))))

;; The import policy used to name `compile --prelude`, which no route
;; implements: the nbb/native routes refuse it outright and the JVM route
;; drops it unread. A contract that names a dead route is worse than one that
;; names none, because a caller reading it believes the standard library was
;; linked. This holds the correction in place -- both halves of it, since
;; naming the working route while the artifact still cannot travel it would be
;; the same defect one level down.
(deftest import-policy-names-a-route-that-carries-source
  (let [policy (:kotoba.stdlib/import-policy (edn/read-string (slurp "lang/stdlib.edn")))]
    (testing "the mode and CLI name the project route"
      (is (= :explicit-source-path (:mode policy)))
      (is (= ["compile" "--source-path"] (:cli policy)))
      (is (true? (:jvm-free policy)))
      (is (str/includes? (:example policy) "--source-path")))
    (testing "ambient loading is still refused -- the invariant did not change"
      (is (false? (:ambient-default policy))))
    (testing "--prelude is recorded as superseded, not as a working route"
      (let [superseded (:superseded-cli policy)]
        (is (= "--prelude" (:flag superseded)))
        (is (= :not-implemented-anywhere (:status superseded)))
        (is (string? (get-in superseded [:measured :cljs-backend])))))
    (testing "and the artifact travels that route, with the commands that say so"
      (let [reach (:artifact-reachability policy)]
        (is (= :reachable (:status reach)))
        (is (seq (:evidence reach)))
        (is (every? #(and (string? (:command %)) (integer? (:exit %)))
                    (:evidence reach))
            "an unreachable artifact and an unmeasured one must not read alike")
        (is (every? zero? (map :exit (:evidence reach))))
        (is (some #(str/includes? (:command %) "--jvm-free") (:evidence reach)))
        (testing "including what it took, since two of the four moved the frozen list"
          (is (= 4 (count (:blockers-cleared reach))))
          (is (every? #(and (keyword? (:id %)) (string? (:was %)))
                      (:blockers-cleared reach)))
          (is (= :refusal-stands
                 (:decision (first (filter #(= :empty-defrecord (:id %))
                                           (:blockers-cleared reach)))))))
        (testing "and the two limits that are somebody else's to fix"
          (is (= 2 (count (:limits-encountered reach))))
          (is (every? #(= "kotoba-lang/amu" (:owner %))
                      (:limits-encountered reach))))))
    (testing "the other places carrying the old claim are listed, not left unnamed"
      (is (seq (:also-claimed-in policy)))
      (is (every? #(and (string? (:path %)) (string? (:status %)))
                  (:also-claimed-in policy))))))
