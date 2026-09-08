(ns kotoba.lang.host-parity
  "Host import parity matrix + L5 cross-host conformance (ADR-2607180900).

  Loads lang/host-parity.edn. Pure data — no DOM, no Wasm execution.
  Missing host import is modeled as capability absence, never ambient success."
  (:require [kotoba.lang.edn :as edn]
            [kotoba.lang.coll :as coll]
            #?(:clj [clojure.java.io :as io])))

(def ^:private unavailable-catalog
  "The catalog when `lang/host-parity.edn` could not be read.

  It is empty AND it says so. Before 2026-09-08 it was only empty: the `:cljs`
  branch returned this shape unconditionally and the `:clj` branch returned it
  whenever the resource was missing, and nothing downstream could tell that
  from a real catalog which happens to list no imports.

  The consequences were never UNSAFE -- `availability` answers
  `:unknown-import` for everything, so `guard-host-import` denies and the L5
  gate fails closed, which is the documented intent. They were MISLEADING.
  `score` reports `:total 0 :ratio 0.0 :ok? false` and `report` reports
  `:below-threshold`, and both read as a measurement of a workspace with no
  host imports rather than as a failure to measure. Measured 2026-09-08:
  porting `host_parity_test` to `.cljc` failed 20 of its 24 assertions on nbb,
  every one comparing real catalog data against this stub, and the only clue in
  the output was a version of 0.

  `::source` is the difference. Nothing else about the fallback changes."
  {:kotoba.lang.host-parity/version 0
   :kotoba.lang.host-parity/source :unavailable
   :imports {}
   :acceptance {:browser-linkable-statuses #{:yes}
                :min-browser-ratio 0.0}
   :conformance {:cases []}})

(def ^:private catalog*
  (delay
    #?(:clj
       (let [c (or (io/resource "kotoba/lang/host-parity.edn")
                   (io/resource "lang/host-parity.edn")
                   (let [f (io/file "lang/host-parity.edn")]
                     (when (.isFile f) f)))]
         (if c
           (with-open [r (io/reader c)]
             (assoc (edn/read-string (slurp r))
                    :kotoba.lang.host-parity/source :resource))
           unavailable-catalog))
       ;; ClojureScript has no ambient file read here, and this namespace runs
       ;; in the browser as well as under nbb, so reaching for `fs` would be
       ;; wrong rather than merely unimplemented. Supplying the catalog by
       ;; injection is a design decision this namespace does not get to make on
       ;; its own; until it is made, the honest answer is that the catalog is
       ;; UNAVAILABLE on this host, said out loud.
       :cljs unavailable-catalog)))

(defn catalog [] @catalog*)

(defn catalog-available?
  "Whether `lang/host-parity.edn` was actually read.

  False means every parity number below is the shape of an answer, not an
  answer. Check this before quoting `score` or `report`."
  []
  (= :resource (:kotoba.lang.host-parity/source (catalog))))

(defn- linkable?
  [status statuses]
  (contains? statuses status))

(defn matrix
  "Vector of {:import :jvm :browser :node :wasm-field :note}."
  []
  (let [c (catalog)
        imports (:imports c {})
        required (or (:required-imports c) (set (keys imports)))
        default-row (:unlisted-import-default c {})]
    (mapv (fn [id]
            (let [row (merge default-row (get imports id {}))]
            {:import id
             :jvm (:jvm row)
             :browser (:browser row)
             :node (:node row)
             :wasm-field (:wasm-field row)
             :note (:note row)}))
          (sort-by name required))))

(defn import-status
  "Raw matrix status for IMPORT on HOST (:jvm/:browser/:node), or nil."
  [import host]
  (get-in (catalog) [:imports import host]))

(defn linkable-statuses
  "Statuses that count as host-linkable (available for guest use)."
  []
  (or (get-in (catalog) [:conformance :linkable-statuses])
      (get-in (catalog) [:acceptance :browser-linkable-statuses])
      #{:yes}))

(defn availability
  "Classify IMPORT on HOST for L5 conformance.

  Returns:
  - :available          — host can link the import (:yes/:inject/:coop-or-inject)
  - :capability-absent  — host status is :no (honest gap; guest must fail closed)
  - :unknown-import     — import not in the matrix
  - :unknown-host       — host not in #{:jvm :browser :node}"
  [import host]
  (cond
    (not (contains? #{:jvm :browser :node} host))
    :unknown-host

    (not (contains? (:imports (catalog) {}) import))
    :unknown-import

    :else
    (let [st (import-status import host)]
      (cond
        (nil? st) :capability-absent
        (= :no st) :capability-absent
        (linkable? st (linkable-statuses)) :available
        :else :capability-absent))))

(defn score
  "Browser linkability plus profile-required coverage. Intentional native
  boundaries are not misreported as browser product gaps."
  []
  (let [c (catalog)
        statuses (get-in c [:acceptance :browser-linkable-statuses] #{:yes})
        profile (:browser-profile c)
        required (:required profile #{})
        classified (apply coll/set-union #{} (map #(get profile % #{})
                                             [:required :intentional-native-boundary
                                              :deferred-provider-components
                                              :deferred-host-injection]))
        rows (matrix)
        n (count rows)
        yes (count (filter #(linkable? (:browser %) statuses) rows))
        linkable-ids (set (map :import (filter #(linkable? (:browser %) statuses) rows)))
        required-yes (count (coll/set-intersection required linkable-ids))
        required-ratio (if (seq required) (double (/ required-yes (count required))) 0.0)
        minimum (get profile :minimum-required-coverage 1.0)
        partition-ok? (= classified (set (map :import rows)))]
    {:total n
     :browser-yes yes
     :browser-no (- n yes)
     :ratio (if (pos? n) (double (/ yes n)) 0.0)
     :required-total (count required)
     :required-yes required-yes
     :required-ratio required-ratio
     :minimum-required-ratio minimum
     :classification-complete? partition-ok?
     ;; Carried so a caller can tell `measured zero` from `could not measure`.
     ;; `:ok?` is unchanged: it was already false in the unavailable case, and
     ;; making the distinction visible must not quietly change a verdict.
     :catalog-available? (catalog-available?)
     :ok? (and partition-ok? (>= required-ratio minimum))
     :missing (mapv :import (remove #(linkable? (:browser %) statuses) rows))
     :gaps (get-in c [:acceptance :honest-gaps] [])}))

(defn- expand-case
  "Expand a conformance case that may use :host or :hosts into one row per host."
  [c]
  (let [hosts (or (:hosts c)
                  (when-let [h (:host c)] [h])
                  [])]
    (mapv (fn [h]
            {:id (:id c)
             :import (:import c)
             :host h
             :expect (:expect c)
             :note (:note c)})
          hosts)))

(defn conformance-cases
  "Flattened vector of {:id :import :host :expect :note}."
  []
  (into []
        (mapcat expand-case)
        (get-in (catalog) [:conformance :cases] [])))

(defn check-case
  "Evaluate one expanded conformance case.
  Returns {:ok? bool :actual <availability> :expected ... :id ...}."
  [{:keys [id import host expect] :as case}]
  (let [actual (availability import host)]
    {:id id
     :import import
     :host host
     :expected expect
     :actual actual
     :ok? (= actual expect)
     :note (:note case)}))

(defn run-conformance
  "Run every L5 conformance fixture. Returns
  {:ok? bool :total N :passed N :failed [case-result ...] :results [...]}."
  []
  (let [results (mapv check-case (conformance-cases))
        failed (filterv (complement :ok?) results)]
    {:ok? (empty? failed)
     :total (count results)
     :passed (- (count results) (count failed))
     :failed failed
     :results results
     :rule (get-in (catalog) [:conformance :rule]
                   "missing host import == capability absence")}))

(defn guard-host-import
  "L5 host-call gate: treat matrix absence as capability denial before any
  provider runs. Returns
  {:kotoba.host/ok? true :status :available}
  or
  {:kotoba.host/ok? false :kotoba.host/denied :host-absent
   :import ... :host ... :status ...}.

  Compose with capability-host/guard-call for CACAO/policy intersection after
  the host itself is known to be linkable."
  [import host]
  (let [st (availability import host)]
    (if (= :available st)
      {:kotoba.host/ok? true :status st :import import :host host}
      {:kotoba.host/ok? false
       :kotoba.host/denied :host-absent
       :status st
       :import import
       :host host})))

(defn report
  "Aggregate parity + conformance snapshot for CLI/doctor."
  []
  (let [s (score)
        conf (run-conformance)]
    {:level :l5
     ;; `:below-threshold` and `:catalog-unavailable` were the same word before.
     ;; A reader cannot act on the first without knowing it is not the second.
     :status (cond
               (not (catalog-available?)) :catalog-unavailable
               (and (:ok? s) (:ok? conf)) :meets-threshold
               :else :below-threshold)
     :catalog-available? (catalog-available?)
     :score s
     :conformance conf
     :matrix (matrix)
     :version (:kotoba.lang.host-parity/version (catalog) 0)}))
