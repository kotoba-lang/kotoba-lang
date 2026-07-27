(ns kotoba.lang.malicious-source
  "Normative malicious-source corpus evaluator. All limits are checked before
   EDN parsing so hostile input cannot allocate unbounded reader structures."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]))

(def default-parser-limits
  {:max-source-bytes 65536 :max-nesting-depth 128 :max-token-chars 4096})

(defn structural-metrics [source]
  (loop [chars (seq source) depth 0 max-depth 0 token 0 max-token 0]
    (if-let [ch (first chars)]
      (cond
        (contains? #{\( \[ \{} ch)
        (recur (next chars) (inc depth) (max max-depth (inc depth))
               0 (max max-token token))
        (contains? #{\) \] \}} ch)
        (recur (next chars) (max 0 (dec depth)) max-depth
               0 (max max-token token))
        (or (Character/isWhitespace ^char ch)
            (contains? #{\, \" \;} ch))
        (recur (next chars) depth max-depth 0 (max max-token token))
        :else
        (recur (next chars) depth max-depth (inc token) max-token))
      {:max-depth max-depth :max-token (max max-token token)
       :bytes (alength (.getBytes source "UTF-8"))})))

(defn safe-read-decision [source limits]
  (let [{:keys [max-source-bytes max-nesting-depth max-token-chars]}
        (merge default-parser-limits limits)
        metrics (structural-metrics source)
        reader-escape? (boolean
                        (re-find #"(#=|#[A-Za-z][A-Za-z0-9_.-]*/?[A-Za-z0-9_.-]*)"
                                 source))
        code (cond
               (> (:bytes metrics) max-source-bytes) :parser/source-size
               (> (:max-depth metrics) max-nesting-depth) :parser/nesting
               (> (:max-token metrics) max-token-chars) :parser/token-size
               reader-escape? :reader/tag-escape
               :else nil)]
    (if code
      {:allowed? false :code code :metrics metrics}
      (try
        (edn/read-string {:readers {}
                          :default (fn [tag _]
                                     (throw (ex-info "reader tag denied"
                                                     {:tag tag})))}
                         source)
        {:allowed? true :metrics metrics}
        (catch Exception _
          {:allowed? false :code :reader/malformed :metrics metrics})))))

(defn evaluate-case
  [{:keys [attack-class source limits declared-effects observed-effects
           requested-capability delegated-capability requested-resources
           resource-limits]}]
  (case attack-class
    :reader-escape
    (safe-read-decision source limits)

    :parser-exhaustion
    (safe-read-decision source limits)

    :effect-laundering
    (if (set/subset? (set observed-effects) (set declared-effects))
      {:allowed? true}
      {:allowed? false :code :effects/laundered
       :undeclared (set/difference (set observed-effects)
                                   (set declared-effects))})

    :confused-deputy
    (if (= requested-capability delegated-capability)
      {:allowed? true}
      {:allowed? false :code :capability/confused-deputy})

    :resource-escalation
    (let [exceeded
          (into {}
                (keep (fn [[resource requested]]
                        (let [limit (get resource-limits resource)]
                          (when (or (nil? limit) (> requested limit))
                            [resource {:requested requested :limit limit}]))))
                requested-resources)]
      (if (empty? exceeded)
        {:allowed? true}
        {:allowed? false :code :resource/escalation
         :exceeded exceeded}))

    {:allowed? false :code :corpus/unknown-attack-class}))

(defn validate-corpus [manifest load-case]
  (let [required #{:reader-escape :effect-laundering :confused-deputy
                   :resource-escalation :parser-exhaustion}
        cases (mapv (fn [entry]
                      (let [case-data (load-case (:file entry))
                            result (evaluate-case case-data)]
                        {:id (:id entry) :attack-class (:attack-class entry)
                         :expected (:expected-code entry)
                         :actual (:code result)
                         :passed? (and (false? (:allowed? result))
                                       (= (:expected-code entry)
                                          (:code result)))}))
                    (:cases manifest))
        covered (set (map :attack-class cases))]
    {:valid? (and (set/subset? required covered)
                  (every? :passed? cases))
     :required required :covered covered :cases cases}))
