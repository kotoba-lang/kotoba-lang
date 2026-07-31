(ns kotoba.lang.surface-status-citations-test
  "Every `:conformance` id in surface-status.edn must name a real case.

  surface-status.edn is the record of what the language surface does, and
  `:conformance` is where an entry points at the case that proves it. A citation
  that resolves nowhere is a claim with no evidence behind it, which is the one
  thing this file must not contain.

  The ids resolve against TWO corpora, and the field does not say which:

    7 of 8   this repo's own lang/**/manifest.edn (81 cases across 6 manifests)
    1 of 8   :record-kit, in kotoba-lang/compiler's pilot-manifest.edn

  That ambiguity is not hypothetical -- checking only the compiler's manifest
  reports 6 of 8 missing, and checking only this repo's reports 1 of 8 missing.
  Both readings are wrong, and a careful reader reaches each of them in turn.
  Until the field records its corpus, a checker has to try both."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]))

(def ^:private compiler-manifest
  "resources/kotoba/lang-conformance/pilot-manifest.edn")

(defn- local-case-ids []
  (->> (file-seq (io/file "lang"))
       (filter #(= "manifest.edn" (.getName ^java.io.File %)))
       (mapcat #(map :id (:cases (edn/read-string (slurp %)))))
       (into #{})))

(defn- compiler-case-ids
  "Empty when the sibling checkout is absent, so a local run without it reports
  only the ids this repo can resolve rather than failing for the wrong reason."
  []
  (let [f (io/file ".." "compiler" compiler-manifest)]
    (if (.isFile f)
      (into #{} (map :id) (:cases (edn/read-string (slurp f))))
      #{})))

(defn- citations []
  (for [[_ section] (edn/read-string (slurp "lang/surface-status.edn"))
        :when (map? section)
        [entry body] section
        :when (and (map? body) (:conformance body))]
    [entry (:conformance body)]))

(deftest every-conformance-citation-resolves
  (let [local (local-case-ids)
        sibling (compiler-case-ids)
        known (into local sibling)
        dangling (sort (for [[entry id] (citations)
                             :when (not (contains? known id))]
                         [entry id]))]
    (is (seq local) "no conformance manifests found under lang/")
    (when (seq sibling)
      (is (empty? dangling)
          (str "surface-status.edn cites conformance cases that exist in neither "
               "this repo's lang/**/manifest.edn nor ../compiler/"
               compiler-manifest ": " (pr-str dangling))))))
