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

(defn- revision
  "The record of one version, wherever it is now. A revision moves into
  :previous-revisions when the next one lands, so looking it up positionally
  makes every version bump rewrite tests that are about older versions."
  [m version]
  (first (filter #(= version (:version %))
                 (cons (:kotoba.lang.stdlib.manifest/revision m)
                       (:previous-revisions m)))))

(defn- defn-names [source]
  (->> (re-seq #"(?m)^\s*\(defn\s+([^\s\[]+)" source)
       (map (comp symbol second))
       set))

(deftest stdlib-manifest-frozen-shape
  (let [m (load-manifest)
        core (first (:modules m))]
    (is (= 4 (:kotoba.lang.stdlib.manifest/version m)))
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
        withdrawn (:withdrawn m)
        version-2 (revision m 2)]
    (is (= 2 (:version version-2))
        "the contraction stays on record under :previous-revisions once a later version is current")
    (is (= :contraction (:kind version-2)))
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

(deftest version-three-added-eleven-names-and-accounted-for-the-rest
  ;; An expansion of a frozen list is a version bump with a record of what
  ;; was added and, as loudly, what was asked for and refused.
  (let [m (load-manifest)
        core (first (:modules m))
        revision (revision m 3)
        added (:added revision)]
    (is (= 3 (:version revision)))
    (is (= :expansion (:kind revision)))
    (is (= '#{keep remove mapv take-while drop-while sort sort-by partition distinct interpose juxt2}
           added))
    (is (every? #(contains? (:public-names core) %) added)
        "every added name is frozen")
    (is (not-any? #(contains? (:withdrawn m) %) added)
        "and none of them is also withdrawn")
    (doseq [name '[frequencies get-in some juxt keep]]
      (is (contains? (:absent m) name) (str name " must be accounted for under :absent"))
      (is (keyword? (:reason (get-in m [:absent name]))) (str name " needs a keyword reason")))
    (doseq [name '[frequencies get-in]]
      (is (= "expression type mismatch: expected keyword, got i64"
             (get-in m [:absent name :message]))
          (str name " is refused by the keyword-keyed map, and the message is on record")))
    (is (= 'juxt2 (get-in m [:absent 'juxt :replaced-by])))
    (is (= 'first-match (get-in m [:absent 'some :replaced-by])))
    (is (not (contains? (:public-names core) 'frequencies)))
    (is (not (contains? (:public-names core) 'get-in)))
    (testing "the private helpers are not public names"
      (let [helpers (set (mapcat val (:private-helpers core)))]
        (is (seq helpers))
        (is (empty? (filter (:public-names core) helpers)))
        (let [src (slurp core-path)
              private-names (set (map (comp symbol second)
                                      (re-seq #"(?m)^\s*\(defn-\s+([^\s\[]+)" src)))]
          (is (= helpers private-names)
              (str "extra=" (pr-str (clojure.set/difference private-names helpers))
                   " missing=" (pr-str (clojure.set/difference helpers private-names)))))))
    (testing "and the extended conformance case is declared on both sides"
      (is (contains? (get-in m [:conformance :cases]) :portable-source-stdlib-extended))
      (is (= "lang/conformance/stdlib/extended.kotoba" (get-in m [:conformance :extended-entry])))
      (is (.exists (io/file (get-in m [:conformance :extended-entry])))))))

(deftest version-four-added-the-sorted-map-artifact
  ;; A MODULE, not more names in :core -- so what this pins is that :core is
  ;; untouched and the second artifact carries its own sha256 and its own
  ;; frozen list. A version bump that quietly moved names between modules
  ;; would pass every other test in this file.
  (let [m (load-manifest)
        revision (revision m 4)
        core (first (filter #(= :core (:id %)) (:modules m)))
        sorted (first (filter #(= :sorted-map (:id %)) (:modules m)))
        src (slurp "lang/stdlib/sorted_map.kotoba")]
    (is (= 4 (:version revision)))
    (is (= :expansion (:kind revision)))
    (is (= :sorted-map (:added-module revision)))
    (is (= 'stdlib.sorted-map (:namespace sorted)))
    (is (= :frozen (:status sorted)))
    (is (= (:sha256 sorted) (sha256-hex (.getBytes src "UTF-8"))))
    (is (= src (slurp (:conformance-path sorted)))
        "conformance mirror must match package SSoT")
    (is (= (:public-names sorted) (defn-names src))
        (str "extra=" (pr-str (clojure.set/difference (defn-names src) (:public-names sorted)))
             " missing=" (pr-str (clojure.set/difference (:public-names sorted) (defn-names src)))))
    (is (= (:added revision) (:public-names sorted)))
    (is (empty? (clojure.set/intersection (:public-names core) (:public-names sorted)))
        "the two modules must not both claim a name")
    (is (= '#{every? first-match concat reverse-into reverse
              range-step range zipmap merge
              comp2 stdlib-binary-closure-anchor partial1
              keep remove mapv take-while drop-while
              sort sort-by partition distinct interpose juxt2}
           (:public-names core))
        "version 4 is an added module; :core's frozen list does not move")
    (testing "the private helpers account for every defn- in the source"
      (let [private (set (map (comp symbol second)
                              (re-seq #"(?m)^\s*\(defn-\s+([^\s\[]+)" src)))]
        (is (= (set (mapcat val (:private-helpers sorted))) private))
        (is (empty? (clojure.set/intersection (:public-names sorted) private)))))
    (testing "AVL is chosen against a named alternative, with the refusals that ruled the others out"
      (is (= :avl (get-in sorted [:structure :kind])))
      (is (= "value type is outside the safe profile"
             (get-in sorted [:structure :rejected-alternatives :record :message]))
          "a record field cannot name its own record; the message is on record")
      (is (= 65 (get-in sorted [:structure :rejected-alternatives :record :exit])))
      (is (string? (get-in sorted [:structure :rejected-alternatives :llrb :detail]))))
    (testing "and the costs are the measured ones, not a guess"
      (is (= {15 4, 31 5, 63 6} (get-in sorted [:costs :height])))
      (is (= {15 5, 31 6, 63 8} (get-in sorted [:costs :avl-bound])))
      (is (every? (fn [[n height]] (<= height (get-in sorted [:costs :avl-bound n])))
                  (get-in sorted [:costs :height]))
          "a recorded height above the AVL bound would be a recorded lie")
      (is (= 15 (get-in sorted [:costs :ascending-inserts-under-default-fuel]))))
    (testing "and the option-valued get is absent with the message that refuses it"
      (is (= "unsupported typed Wasm expression"
             (get-in m [:absent 'sm-get-returning-an-option :message])))
      (is (= 70 (get-in m [:absent 'sm-get-returning-an-option :exit])))
      (is (not (contains? (:public-names sorted) 'sm-get-option))))
    (testing "and the ordered conformance case is declared on both sides"
      (is (contains? (get-in m [:conformance :cases]) :portable-source-stdlib-ordered))
      (is (= "lang/conformance/stdlib/ordered.kotoba" (get-in m [:conformance :ordered-entry])))
      (is (.exists (io/file (get-in m [:conformance :ordered-entry])))))))

(deftest package-contract-lists-core-module
  (let [pkg (edn/read-string (slurp "lang/stdlib.edn"))
        m (load-manifest)
        core (first (:modules m))]
    (is (= 4 (:kotoba.stdlib/version pkg)))
    (is (= "0.4.0" (:kotoba.stdlib/release pkg)))
    (is (string? (:kotoba.stdlib/absent pkg)) "the package points at the manifest's :absent")
    (is (= :core (:id (first (:kotoba.stdlib/artifacts pkg)))))
    (is (= 'stdlib.core (:namespace (first (:kotoba.stdlib/artifacts pkg)))))
    (is (= (:sha256 core) (:sha256 (first (:kotoba.stdlib/artifacts pkg)))))
    (testing "and every module the manifest freezes is an artifact of the package"
      (let [artifacts (into {} (for [a (:kotoba.stdlib/artifacts pkg)] [(:id a) a]))]
        (is (= (set (map :id (:modules m))) (set (keys artifacts)))
            "a frozen module with no artifact is a module no consumer can pin")
        (doseq [module (:modules m)]
          (is (= (:sha256 module) (:sha256 (artifacts (:id module))))
              (str (:id module) ": the two contracts must pin the same bytes"))
          (is (= (:namespace module) (:namespace (artifacts (:id module))))))))))

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
        (testing "and the limits that are somebody else's to fix"
          (is (= 3 (count (:limits-encountered reach))))
          (is (every? #(= "kotoba-lang/amu" (:owner %))
                      (:limits-encountered reach))))))
    (testing "the other places carrying the old claim are listed, not left unnamed"
      (is (seq (:also-claimed-in policy)))
      (is (every? #(and (string? (:path %)) (string? (:status %)))
                  (:also-claimed-in policy))))))
