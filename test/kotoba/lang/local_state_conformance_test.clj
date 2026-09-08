(ns kotoba.lang.local-state-conformance-test
  "Runs local-state slice 1's conformance cases (lang/local-state.edn) through
  the frontend and KIR, so `:conformance-vectors-positive-and-negative :met`
  under `:local-atom-elaboration` in `surface-status.edn` is something this
  repository measures rather than states.

  The three positive cases execute on KIR and must answer the manifest's
  `:expect`. The negative cases are pinned by their EXACT rejection message:
  `:error-contains` is compared with `=`, not `includes?`, because a fixture
  refused for some other reason -- an unbound symbol, a type error in the
  fixture itself -- would otherwise count as the refusal it was written to
  demonstrate. That matters more here than usual: three of the five negatives
  are the SAME message about three different escape routes, so a fixture that
  stopped exercising its route would still be refused, just not for its
  reason. Each also has to carry a `:kotoba.error/local-state-*` code."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [kotoba.lang.text :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private conformance-root "lang/conformance")

(defn- local-state-cases []
  (->> (:cases (edn/read-string (slurp (str conformance-root "/manifest.edn"))))
       (filter #(some-> (:entry %) (str/starts-with? "local-state/")))))

(defn- source-of [case]
  (slurp (str conformance-root "/" (:entry case))))

(defn- refusal-of [source]
  (try (do (sema/analyze source) nil)
       (catch Throwable e e)))

(deftest the-manifest-declares-the-cases-the-contract-lists
  (let [contract (edn/read-string (slurp "lang/local-state.edn"))
        declared (into #{} (map :id) (local-state-cases))]
    (is (= 1 (:kotoba.lang.local-state/version contract)))
    (is (= :slice-1-implemented (:kotoba.lang.local-state/status contract)))
    (is (= (set (concat (get-in contract [:conformance :positive])
                        (get-in contract [:conformance :negative])))
           declared))
    (is (= 3 (count (filter #(= :run (:kind %)) (local-state-cases)))))
    (is (<= 5 (count (filter #(= :expect-error (:kind %)) (local-state-cases)))))))

(deftest every-positive-case-executes-to-its-expected-value
  (doseq [case (filter #(= :run (:kind %)) (local-state-cases))]
    (testing (str (:id case))
      (is (= #{:kir} (:required-backends case)))
      (let [hir (sema/analyze (source-of case))
            value (kir/execute (kir/lower hir) (symbol (:function case)) (:args case))]
        (is (= (get-in case [:expect :kotoba]) (long value)))
        (is (not-any? #(and (seq? %) (seq %)
                            (contains? '#{atom swap! reset! deref clojure.core/deref}
                                       (first %)))
                      (tree-seq coll? seq (mapv :body (:functions hir))))
            "no cell operation survives elaboration")
        (is (= #{} (:effects hir))
            "the elaboration adds nothing to the effect row")
        (is (contains? (:named-operations hir) :local-state)
            "but it is visible in :named-operations")))))

(deftest every-negative-case-is-refused-with-exactly-the-pinned-message
  (doseq [case (filter #(= :expect-error (:kind %)) (local-state-cases))]
    (testing (str (:id case))
      (let [refusal (refusal-of (source-of case))]
        (is (some? refusal) "the fixture was admitted")
        (when refusal
          (is (= (:error-contains case) (ex-message refusal)))
          (is (str/starts-with? (str (:kotoba.error/code (ex-data refusal)))
                                ":kotoba.error/local-state-")
              (pr-str (ex-data refusal))))))))

(deftest the-fixture-directory-and-the-manifest-agree
  (let [on-disk (->> (file-seq (java.io.File. (str conformance-root "/local-state")))
                     (filter #(.isFile ^java.io.File %))
                     (map #(subs (.getPath ^java.io.File %) (inc (count conformance-root))))
                     set)
        declared (into #{} (map :entry) (local-state-cases))]
    (is (= on-disk declared)
        "every fixture is a case and every case is a fixture")))

(deftest the-contract-and-the-grammar-agree-on-the-surface
  (let [contract (edn/read-string (slurp "lang/local-state.edn"))
        grammar (edn/read-string (slurp "lang/guest-grammar.edn"))
        surface (edn/read-string (slurp "lang/surface-status.edn"))
        entry (get-in surface [:invariants :no-ambient-mutation])]
    (testing "the four heads are the sugar entry's forms"
      (is (= '[atom swap! reset! deref] (get-in grammar [:sugar :atom-local :forms])))
      (is (= :slice-1-implemented (get-in grammar [:sugar :atom-local :status])))
      (is (= "lang/local-state.edn" (get-in grammar [:sugar :atom-local :contract]))))
    (testing "and are the set surface-status excuses from :forbidden-heads"
      (is (= (:surface contract) (:admitted-via-elaboration entry)))
      (is (= (:surface contract) (get-in entry [:local-atom-elaboration :covers]))))
    (testing "the heads that stay refused are still forbidden by the grammar"
      ;; `ref` is NOT in this list, and its absence is asserted rather than
      ;; assumed. It left `:forbidden-heads` on 2026-09-06 for a reason that
      ;; has nothing to do with local state: ADR-544's pure definition
      ;; reference shares its spelling, and a head cannot be in both the
      ;; admitted head set and the forbidden one. Dropping it from this list
      ;; without checking WHY would turn a security assertion into a shorter
      ;; security assertion, so the check below is that every head missing
      ;; from `:forbidden-heads` is missing for a recorded reason.
      (let [forbidden (set (:forbidden-heads grammar))
            still-refused '#{set! alter-var-root dosync volatile! binding var}
            excused (into (set (:admitted-via-elaboration entry))
                          (set (:admitted-via-pure-core-elaboration entry)))]
        (is (every? forbidden still-refused))
        (is (not-any? forbidden '#{atom swap! reset! deref}))
        (is (contains? excused 'ref)
            "`ref` is out of :forbidden-heads, so some excusal must account
             for it -- otherwise a security head has simply gone missing")
        (is (empty? (set/difference
                     (set (:surface entry))
                     (set/union forbidden excused)))
            (str "a head named by :no-ambient-mutation is neither forbidden "
                 "nor excused: "
                 (pr-str (set/difference
                          (set (:surface entry))
                          (set/union forbidden excused)))))))))
