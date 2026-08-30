(ns kotoba.lang.q9-migration
  "Fail-closed authorization checks for the Q9 dependency-ordered migration."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]))

(def q9-path "lang/q9-migration.edn")
(def all-waves #{:wave-0 :wave-1 :wave-2 :wave-3 :wave-4 :wave-5})
(def complete-statuses #{:qualified})
(def compiled-dispositions #{:kotoba-only :clj-kotoba :common :split})
(def required-build-evidence #{:kotoba-cli-build :amu-compile})

(defn read-program []
  (edn/read-string (slurp q9-path)))

(defn validation-errors [q9]
  (let [waves (:waves q9)
        dependencies (:wave-dependencies q9)
        decision (:current-decision q9)
        authorized (:authorized-waves decision)
        scope (:scope q9)
        build-contract (:whole-component-build-contract q9)
        completed (into #{} (keep (fn [[wave v]]
                                    (when (complete-statuses (:status v)) wave)))
                        waves)
        production-authorized? (true? (:production-deploy-authorized decision))]
    (vec
     (concat
      (when-not (= all-waves (set (keys waves)) (set (keys dependencies)))
        [{:code :q9/wave-inventory-drift}])
      (when-not (and (= :whole-component (:migration-unit scope))
                     (true? (:decision-only-extraction-forbidden scope))
                     (true? (:whole-component-build-required scope)))
        [{:code :q9/decision-slice-migration-enabled}])
      (for [disposition compiled-dispositions
            :let [required (set (get-in q9 [:dispositions disposition :requires]))]
            :when (not (set/subset? required-build-evidence required))]
        {:code :q9/missing-whole-component-build-gate
         :disposition disposition
         :missing (set/difference required-build-evidence required)})
      (when-not (and (= "kotoba compile <entry.kotoba|entry.cljk> --target <target> --output <artifact>"
                        (get-in build-contract [:public-cli :source-build]))
                     (= "kotoba rad build --project <repository> --profile release"
                        (get-in build-contract [:public-cli :package-build]))
                     (= "amu compile <entry.kotoba|entry.cljk> --target <target> --output <artifact>"
                        (get-in build-contract [:amu :compile]))
                     (true? (get-in build-contract
                                    [:public-cli :internal-namespace-entry-forbidden]))
                     (some #{:decision-core-only-shadow} (:forbidden build-contract)))
        [{:code :q9/build-contract-weakened}])
      (for [wave authorized
            :when (not (set/subset? (get dependencies wave #{}) authorized))]
        {:code :q9/dependency-not-authorized :wave wave})
      (for [[wave {:keys [status evidence]}] waves
            :when (and (= :qualified status) (empty? evidence))]
        {:code :q9/qualified-without-evidence :wave wave})
      (when (and production-authorized? (not= all-waves completed))
        [{:code :q9/premature-production-authorization
          :incomplete (set/difference all-waves completed)}])
      (when (and (:fleet-complete decision) (not= all-waves completed))
        [{:code :q9/premature-fleet-completion
          :incomplete (set/difference all-waves completed)}])
      (when (and (= all-waves completed) (not production-authorized?))
        [{:code :q9/missing-explicit-production-authorization}])))))

(defn report
  ([] (report (read-program)))
  ([q9]
   (let [errors (validation-errors q9)
         statuses (frequencies (map :status (vals (:waves q9))))]
     {:valid? (empty? errors)
      :fleet-complete? (true? (get-in q9 [:current-decision :fleet-complete]))
      :production-authorized?
      (true? (get-in q9 [:current-decision :production-deploy-authorized]))
      :wave-statuses statuses
      :errors errors})))

(defn -main [& _]
  (let [result (report)]
    (prn result)
    (when-not (:valid? result)
      (System/exit 1))))
