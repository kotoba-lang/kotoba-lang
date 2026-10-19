(ns kotobase.retention-qualification
  "Fail-closed evidence checks for retention changes and GC execution.

  `kotobase.code-graph/gc-plan` deliberately only plans; a storage adapter must
  submit a qualifying request before any physical deletion is permitted."
  (:require [clojure.edn :as edn]))

(def policy-path "qualification/retention-policy.edn")

(defn read-policy []
  (edn/read-string (slurp policy-path)))

(defn qualify-change
  [policy {:keys [action dry-run legal-hold-check approvals recovery-receipt
                  audit-receipt gc-plan]}]
  (let [principals (set (keep :approval/principal approvals))
        destructive? (contains? (:destructive-actions policy) action)
        errors (cond-> []
                 (not destructive?) (conj :retention/action)
                 (not (true? (:dry-run/complete? dry-run)))
                 (conj :retention/dry-run)
                 (not (true? (:legal-hold-check/complete? legal-hold-check)))
                 (conj :retention/legal-hold-check)
                 (< (count principals)
                    (get-in policy [:approval :minimum-distinct-principals]))
                 (conj :retention/two-person-approval)
                 (not (true? (:recovery-receipt/restorable? recovery-receipt)))
                 (conj :retention/recovery)
                 (not (true? (:audit-receipt/durable? audit-receipt)))
                 (conj :retention/audit)
                 (and (= action :gc/apply)
                      (not= (set (:dry-run/candidates dry-run))
                            (set (:candidates gc-plan))))
                 (conj :retention/dry-run-mismatch))]
    {:retention/allowed? (empty? errors)
     :retention/errors errors
     :retention/approvers principals
     :retention/candidates (set (:candidates gc-plan))}))
