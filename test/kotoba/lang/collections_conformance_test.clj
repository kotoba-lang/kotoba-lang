(ns kotoba.lang.collections-conformance-test
  "Executes the `collections/` conformance cases, so `:set-literal`'s
  `:conformance :bounded-set-literal-and-operations` in `surface-status.edn`
  is something this repository measures rather than states.

  ## Why this file exists

  `:bounded-set-literal-and-operations` declares
  `:required-backends #{:kir :wasm32-kotoba-v1}`. Measured 2026-09-03 across
  the whole toolchain, NOTHING executed it on either:

    * amu's `kotoba.compiler.lang-conformance` drives exactly those two
      backends -- and only over its OWN
      `resources/kotoba/lang-conformance/pilot-manifest.edn`, which does not
      declare this case. It never reads this manifest.
    * `kotoba`'s `kotoba.language-conformance-test` DOES read this manifest,
      and drives `kotoba.runtime/wasm-binary`, the legacy form walker -- a
      backend this manifest's `:backends` map does not name at all. It also
      reads the manifest through its own PINNED kotoba-lang, not this one.
    * this repository ran the `local-state/` cases and nothing else.

  So the case was declared on two backends, executed on a third that was not
  declared, and the two facts had nowhere to meet. That is ADR-2608136000's
  shape at the level of the manifest: a green suite reporting on something
  other than what the manifest claims to require.

  ## What is pinned here

  1. every `collections/` case the manifest declares is EXECUTED on `:kir` and
     must answer its `:expect`;
  2. the set of cases executed equals the set the manifest declares -- so
     dropping `:kir` from a case's `:required-backends`, or dropping the case,
     turns this red rather than quietly shrinking the run;
  3. an evidence floor: zero executed cases is a failure, not a clean run;
  4. the directory and the manifest agree in both directions;
  5. the negative case is refused with its pinned message AND for its pinned
     reason, so a fixture that fails for some other cause cannot be counted as
     the refusal it was written to demonstrate.

  `:wasm32-kotoba-v1` is the other half of every pure-product case's
  requirement and has no runner in this repository; that gap is recorded in the
  manifest's `:runners`/`:deferred-runners` and checked by
  `kotoba.lang.conformance-matrix-test`, not papered over here."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private conformance-root "lang/conformance")
(def ^:private entry-prefix
  "The set slice of `collections/`. The other three fixtures in that directory
  -- vector.kotoba, higher_order.kotoba, destructuring.kotoba -- are NOT run
  here, and not because they were forgotten: measured 2026-09-03 against sema
  `24a59c74`, `(count [7 8 9])` is refused `operation has no admitted
  lowering`, exactly as `(conj #{:a} :b)` was. They are the same defect on
  different heads and they are somebody's next change, not this one's. What
  this file must not do is include them, go red, and be quietly narrowed
  later; the manifest records which cases have a runner, and those three do
  not claim one."
  "collections/set")

(def ^:private this-runner
  "kotoba-lang/kotoba-lang kotoba.lang.collections-conformance-test")

(defn- manifest []
  (edn/read-string (slurp (str conformance-root "/manifest.edn"))))

(defn- collections-cases [m]
  (filter #(some-> (:entry %) (str/starts-with? entry-prefix)) (:cases m)))

(defn- source-of [case]
  (slurp (str conformance-root "/" (:entry case))))

(defn- refusal-of [source]
  (try (do (sema/analyze source) nil)
       (catch Throwable e e)))

(deftest every-collections-case-that-requires-kir-executes-to-its-expected-value
  (let [cases (collections-cases (manifest))
        runnable (filter #(= :run (:kind %)) cases)
        executed (atom #{})]
    (doseq [case runnable]
      (testing (str (:id case))
        (is (contains? (:required-backends case) :kir)
            "a :run case matched by this runner that does not require :kir has
             no runner at all; either it requires :kir or it does not belong
             to this class")
        ;; The other direction of the manifest check: `validate-execution`
        ;; verifies that a case's `:executed-by` accounts for its required
        ;; backends, but it cannot tell whether the runner named there runs.
        ;; This can.
        (is (= this-runner (get-in case [:executed-by :kir]))
            "the case does not name this runner as the one that drives :kir")
        (let [hir (sema/analyze (source-of case))
              value (kir/execute (kir/lower hir)
                                 (symbol (or (:function case) "main"))
                                 (vec (:args case)))]
          (is (= (get-in case [:expect :kotoba]) (long value)))
          (swap! executed conj (:id case)))))
    (println (format "EXECUTED\t%d\t%s cases on :kir" (count @executed) entry-prefix))
    (is (pos? (count @executed))
        "no collections case executed; a run that measured nothing is not a run
         that found nothing wrong")
    (is (= (into #{} (map :id) runnable) @executed)
        "a declared :run case did not execute")))

(deftest the-negative-case-is-refused-for-its-own-reason
  (doseq [case (filter #(= :expect-error (:kind %)) (collections-cases (manifest)))]
    (testing (str (:id case))
      (let [refusal (refusal-of (source-of case))]
        (is (some? refusal) "the fixture was admitted")
        (when refusal
          (is (= this-runner (get-in case [:executed-by :compiler-admit]))
              "the case does not name this runner as the one that drives
               :compiler-admit")
          (is (str/includes? (ex-message refusal) (:error-contains case))
              (ex-message refusal))
          ;; The message alone is not the reason. A heterogeneous set literal
          ;; must be refused as an ITEM TYPE mismatch against the set's own
          ;; type, carrying the required and actual types as data -- not by an
          ;; unbound symbol or an arity error that happens to mention a set.
          (let [data (ex-data refusal)]
            (is (= :keyword (:kotoba.error/expected data)))
            (is (= :i64 (:kotoba.error/actual data)))))))))

(deftest the-fixture-directory-and-the-manifest-agree
  (let [on-disk (->> (file-seq (java.io.File. (str conformance-root "/collections")))
                     (filter #(.isFile ^java.io.File %))
                     (map #(subs (.getPath ^java.io.File %) (inc (count conformance-root))))
                     (filter #(str/starts-with? % entry-prefix))
                     set)
        declared (into #{} (map :entry) (collections-cases (manifest)))]
    (is (pos? (count on-disk)) "the fixture directory was not read")
    (is (= on-disk declared)
        (str "fixtures with no case: " (pr-str (sort (remove declared on-disk)))
             "; cases with no fixture: "
             (pr-str (sort (remove on-disk declared)))))))
