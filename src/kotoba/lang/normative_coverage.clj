(ns kotoba.lang.normative-coverage
  "Fail-closed mapping from machine-readable normative rules to executable tests."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]))

(def registry-path "lang/normative-coverage.edn")

(defn read-edn [path]
  (edn/read-string (slurp (io/file path))))

(defn qualify [namespace value]
  (keyword namespace (name value)))

(def grammar-rule-categories
  [:strict-grammar :function-semantics :forbidden-heads
   :string-head-host-ops :arithmetic :admission-limits
   :core-special-forms :predicates :sugar :comparisons
   :admitted-builtins])

(defn category-members [value]
  (cond
    (map? value) (keys value)
    (set? value) value
    (sequential? value) value
    :else [:value]))

(defn nested-leaf-paths
  ([value] (nested-leaf-paths [] value))
  ([path value]
   (if (map? value)
     (mapcat (fn [[k v]] (nested-leaf-paths (conj path k) v)) value)
     [path])))

(defn path-rule [prefix path]
  (keyword prefix
           (str/join "." (map #(if (instance? clojure.lang.Named %)
                                 (name %)
                                 (str %))
                              path))))

(defn source-rules [{:keys [kind path]}]
  (let [document (read-edn path)]
    (case kind
      :safety-claims
      (set (map #(qualify "safety" (:id %)) (:claims document)))

      :capability-rules
      (set (map #(qualify "capability" %) (keys (:rules document))))

      :package-trust-rules
      (set (map #(qualify "package" (:rule %)) (:trust-rules document)))

      :malicious-source-classes
      (set (map #(qualify "malicious" (:attack-class %)) (:cases document)))

      :guest-grammar-rules
      (set
       (for [category grammar-rule-categories
             member (category-members (get document category))]
         (path-rule "guest" [category member])))

      :version-policy-controls
      (set (map #(path-rule "version" %)
                (nested-leaf-paths
                 (select-keys document
                              [:release/current :release/semver-required
                               :supported :deprecation :release-tags
                               :compatibility-report]))))

      (throw (ex-info "Unknown normative source kind" {:kind kind :path path})))))

(defn deftest-exists? [path test-var]
  (and (.isFile (io/file path))
       (boolean
        (re-find (re-pattern
                  (str "(?m)^\\(deftest\\s+"
                       (java.util.regex.Pattern/quote (name test-var))
                       "(?:\\s|\\n)"))
                 (slurp path)))))

(defn range-covers? [{:keys [min max]} value]
  (and (integer? min) (integer? max) (<= min value max)))

(defn validate-registry
  ([] (validate-registry (read-edn registry-path)))
  ([registry]
   (let [expected (apply set/union #{} (map source-rules (:sources registry)))
         by-kind (into {} (map (juxt :kind source-rules)) (:sources registry))
         entries (mapv #(if-let [kind (:source-kind %)]
                          (assoc % :rules (get by-kind kind))
                          %)
                       (:coverage registry))
         actual (set (mapcat :rules entries))
         duplicates (->> entries (mapcat :rules) frequencies
                         (keep (fn [[rule n]] (when (> n 1) rule))) set)
         missing-tests
         (->> entries
              (keep (fn [{:keys [test-file test-var rules]}]
                      (when-not (deftest-exists? test-file test-var)
                        {:rules rules :test-file test-file :test-var test-var})))
              vec)
         current (:current-compatibility registry)
         ranges (:compatibility registry)
         compatibility-errors
         (->> current
              (keep (fn [[axis version]]
                      (when-not (range-covers? (get ranges axis) version)
                        {:axis axis :version version :range (get ranges axis)})))
              vec)
         errors (cond-> []
                  (not= 1 (:kotoba.lang.normative-coverage/version registry))
                  (conj {:code :registry/version})
                  (seq (set/difference expected actual))
                  (conj {:code :coverage/missing
                         :rules (set/difference expected actual)})
                  (seq (set/difference actual expected))
                  (conj {:code :coverage/unknown
                         :rules (set/difference actual expected)})
                  (seq duplicates)
                  (conj {:code :coverage/duplicate :rules duplicates})
                  (seq missing-tests)
                  (conj {:code :coverage/test-not-found :tests missing-tests})
                  (seq compatibility-errors)
                  (conj {:code :coverage/incompatible
                         :ranges compatibility-errors}))]
     {:valid? (empty? errors)
      :attestable? (and (empty? errors) (empty? (:residual-gaps registry)))
      :rule-count (count expected)
      :mapped-rule-count (count actual)
      :residual-gaps (vec (:residual-gaps registry))
      :errors errors})))

(defn -main [& _]
  (let [result (validate-registry)]
    (prn result)
    (when-not (:valid? result)
      (System/exit 1))))
