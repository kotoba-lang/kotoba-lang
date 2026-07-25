(ns kotoba.lang.host-parity
  "Host import parity matrix (ADR-2607180900 P1 / L5-partial).

  Loads lang/host-parity.edn and scores browser linkability. Pure data —
  no DOM, no Wasm execution."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
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

(defn score
  "Browser linkability plus profile-required coverage. Intentional native
  boundaries are not misreported as browser product gaps."
  []
  (let [c (catalog)
        statuses (get-in c [:acceptance :browser-linkable-statuses] #{:yes})
        profile (:browser-profile c)
        required (:required profile #{})
        classified (apply set/union #{} (map #(get profile % #{})
                                             [:required :intentional-native-boundary
                                              :deferred-provider-components
                                              :deferred-host-injection]))
        rows (matrix)
        n (count rows)
        yes (count (filter #(linkable? (:browser %) statuses) rows))
        linkable-ids (set (map :import (filter #(linkable? (:browser %) statuses) rows)))
        required-yes (count (set/intersection required linkable-ids))
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
     :ok? (and partition-ok? (>= required-ratio minimum))
     :missing (mapv :import (remove #(linkable? (:browser %) statuses) rows))
     :gaps (get-in c [:acceptance :honest-gaps] [])}))

(defn availability
  "Return the closed availability decision for one declared host import.
  A missing row, explicit `:no`, or an unknown host is capability absence;
  no caller receives ambient authority merely because a Component compiled."
  [import host]
  (let [status (get-in (catalog) [:imports import host])]
    (if (contains? #{:yes :inject :coop-or-inject :component-link :native-provider} status)
      :available
      :capability-absent)))

(defn guard-host-import
  "Data-only host guard used before a provider is selected or linked."
  [import host]
  (let [status (availability import host)]
    (if (= :available status)
      {:kotoba.host/ok? true :status status :import import :host host}
      {:kotoba.host/ok? false :kotoba.host/denied :host-absent
       :status status :import import :host host})))

(defn run-conformance
  "Validate every declared matrix cell is a known, explicit availability
  state. This is deliberately a contract conformance check, not a provider
  invocation: providers remain separately qualified by their host runtimes."
  []
  (let [known #{:yes :no :inject :coop-or-inject :component-link :native-provider}
        cases (vec (for [[import row] (:imports (catalog))
                         host (:hosts (catalog))]
                     {:import import :host host :status (get row host)}))
        failed (vec (remove #(contains? known (:status %)) cases))]
    {:ok? (empty? failed) :total (count cases)
     :passed (- (count cases) (count failed)) :failed failed :results cases}))

(defn report
  "Aggregate parity snapshot for CLI/doctor."
  []
  {:level :l5-partial
   :status (if (:ok? (score)) :meets-profile :below-profile)
   :score (score)
   :matrix (matrix)
   :version (:kotoba.lang.host-parity/version (catalog) 0)})
