(ns kotoba.host-parity
  "Launcher-side host import parity + L5 cross-host conformance
  (ADR-2607180900).

  Vendored host-parity.edn under resources/kotoba/lang/ — same catalog as
  kotoba-lang/lang/host-parity.edn. Prefer kotoba.lang.host-parity when the
  language pin is current; this ns remains a self-contained launcher mirror."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kotoba.lang.host-parity :as lang-parity]))

(def ^:private catalog*
  (delay
    (let [c (or (io/resource "kotoba/lang/host-parity.edn")
                (io/resource "lang/host-parity.edn"))]
      (if c
        (with-open [r (io/reader c)]
          (edn/read (java.io.PushbackReader. r)))
        {:imports {}
         :acceptance {:browser-linkable-statuses #{:yes} :min-browser-ratio 0.0}
         :conformance {:cases []}}))))

(defn catalog [] @catalog*)

(defn matrix
  []
  (mapv (fn [[id row]]
          {:import id
           :jvm (:jvm row)
           :browser (:browser row)
           :node (:node row)
           :wasm-field (:wasm-field row)
           :note (:note row)})
        (sort-by (comp name key) (:imports (catalog) {}))))

(defn score
  []
  (let [c (catalog)
        statuses (get-in c [:acceptance :browser-linkable-statuses] #{:yes})
        min-ratio (get-in c [:acceptance :min-browser-ratio] 0.0)
        rows (matrix)
        n (count rows)
        yes (count (filter #(contains? statuses (:browser %)) rows))
        ratio (if (pos? n) (double (/ yes n)) 0.0)]
    {:total n
     :browser-yes yes
     :browser-no (- n yes)
     :ratio ratio
     :min-ratio min-ratio
     :ok? (>= ratio min-ratio)
     :missing (mapv :import (remove #(contains? statuses (:browser %)) rows))}))

(defn availability
  "Delegate to language kernel when available; local catalog as fallback."
  [import host]
  (try
    (lang-parity/availability import host)
    (catch Throwable _
      (let [st (get-in (catalog) [:imports import host])
            linkable (get-in (catalog) [:conformance :linkable-statuses]
                             (get-in (catalog) [:acceptance :browser-linkable-statuses]
                                     #{:yes}))]
        (cond
          (nil? st) :capability-absent
          (= :no st) :capability-absent
          (contains? linkable st) :available
          :else :capability-absent)))))

(defn guard-host-import
  [import host]
  (try
    (lang-parity/guard-host-import import host)
    (catch Throwable _
      (let [st (availability import host)]
        (if (= :available st)
          {:kotoba.host/ok? true :status st :import import :host host}
          {:kotoba.host/ok? false
           :kotoba.host/denied :host-absent
           :status st
           :import import
           :host host})))))

(defn run-conformance
  []
  (try
    (lang-parity/run-conformance)
    (catch Throwable _
      {:ok? false :total 0 :passed 0 :failed [] :results []
       :error "kotoba.lang.host-parity not available on classpath"})))

(defn report
  []
  (let [s (score)
        conf (run-conformance)
        conf-ok? (or (nil? conf) (true? (:ok? conf)))]
    {:level :l5
     :status (if (and (:ok? s) conf-ok?) :meets-threshold :below-threshold)
     :score s
     :conformance conf
     :matrix (matrix)}))
