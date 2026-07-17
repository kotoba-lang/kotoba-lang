(ns kotoba.lang.host-parity
  "Host import parity matrix (ADR-2607180900 P1 / L5-partial).

  Loads lang/host-parity.edn and scores browser linkability. Pure data —
  no DOM, no Wasm execution."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(def ^:private catalog*
  (delay
    #?(:clj
       (let [c (or (io/resource "kotoba/lang/host-parity.edn")
                   (io/resource "lang/host-parity.edn")
                   (let [f (io/file "lang/host-parity.edn")]
                     (when (.isFile f) f)))]
         (if c
           (with-open [r (io/reader c)]
             (edn/read (java.io.PushbackReader. r)))
           {:kotoba.lang.host-parity/version 0
            :imports {}
            :acceptance {:browser-linkable-statuses #{:yes}
                         :min-browser-ratio 0.0}}))
       :cljs
       {:kotoba.lang.host-parity/version 0
        :imports {}
        :acceptance {:browser-linkable-statuses #{:yes}
                     :min-browser-ratio 0.0}})))

(defn catalog [] @catalog*)

(defn- linkable?
  [status statuses]
  (contains? statuses status))

(defn matrix
  "Vector of {:import :jvm :browser :node :wasm-field :note}."
  []
  (let [imports (:imports (catalog) {})]
    (mapv (fn [[id row]]
            {:import id
             :jvm (:jvm row)
             :browser (:browser row)
             :node (:node row)
             :wasm-field (:wasm-field row)
             :note (:note row)})
          (sort-by (comp name key) imports))))

(defn score
  "Browser linkability score vs acceptance thresholds."
  []
  (let [c (catalog)
        statuses (get-in c [:acceptance :browser-linkable-statuses] #{:yes})
        min-ratio (get-in c [:acceptance :min-browser-ratio] 0.0)
        rows (matrix)
        n (count rows)
        yes (count (filter #(linkable? (:browser %) statuses) rows))
        ratio (if (pos? n) (double (/ yes n)) 0.0)]
    {:total n
     :browser-yes yes
     :browser-no (- n yes)
     :ratio ratio
     :min-ratio min-ratio
     :ok? (>= ratio min-ratio)
     :missing (mapv :import (remove #(linkable? (:browser %) statuses) rows))
     :gaps (get-in c [:acceptance :honest-gaps] [])}))

(defn report
  "Aggregate parity snapshot for CLI/doctor."
  []
  {:level :l5-partial
   :status (if (:ok? (score)) :meets-threshold :below-threshold)
   :score (score)
   :matrix (matrix)
   :version (:kotoba.lang.host-parity/version (catalog) 0)}))
