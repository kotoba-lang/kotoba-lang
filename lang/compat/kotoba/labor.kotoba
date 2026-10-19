(ns kotoba.labor
  "Employment contracts, timesheets and payroll — pure data contracts.

  A kotoba-lang capability library for the cloud-itonami-9700 (community
  domestic employment) open business. No network, no I/O. Models the records
  a household-employer operator keeps: employment contracts (hourly or
  monthly), timesheet entries, wage calculation, and payroll records.

  Amounts are plain numbers in the smallest unit of the account currency
  (e.g. cents) — no BigDecimal assumption, keeping the library portable.
  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Employment contract
;; ---------------------------------------------------------------------------

(def wage-types #{:hourly :monthly})

(defn contract
  "Construct an employment contract. wage-type is :hourly or :monthly. rate
  is the smallest-currency-unit amount (per hour for hourly, per month for
  monthly). Returns nil for an unknown wage type."
  [id worker employer role wage-type rate & {:keys [currency start end]}]
  (when (contains? wage-types wage-type)
    {:contract/id        id
     :contract/worker    worker
     :contract/employer  employer
     :contract/role      role
     :contract/wage-type wage-type
     :contract/rate      rate
     :contract/currency  (or currency "USD")
     :contract/start     start
     :contract/end       end}))

;; ---------------------------------------------------------------------------
;; Timesheet and wage calculation
;; ---------------------------------------------------------------------------

(defn timesheet
  "Construct a timesheet entry: hours worked by a worker on a date."
  [worker date hours & {:keys [break]}]
  {:ts/worker worker
   :ts/date   date
   :ts/hours  hours
   :ts/break  break})

(defn total-hours
  "Sum hours across a collection of timesheet entries."
  [entries]
  (reduce + (map :ts/hours entries)))

(defn wages-for
  "Compute gross wages for timesheet entries under a contract. For an hourly
  contract, rate * total hours. For a monthly contract, the rate itself
  (entries are ignored — monthly pay is the contracted rate)."
  [contract-record entries]
  (if (= :hourly (:contract/wage-type contract-record))
    (* (:contract/rate contract-record) (total-hours entries))
    (:contract/rate contract-record)))

;; ---------------------------------------------------------------------------
;; Payroll record
;; ---------------------------------------------------------------------------

(defn payroll
  "Construct a payroll record for a worker and period. gross is gross wages,
  deductions is the total withheld. net = gross - deductions."
  [id worker period gross & {:keys [deductions currency]}]
  (let [ded (or deductions 0)]
    {:payroll/id         id
     :payroll/worker     worker
     :payroll/period     period
     :payroll/gross      gross
     :payroll/deductions ded
     :payroll/net        (- gross ded)
     :payroll/currency   (or currency "USD")}))

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn validate-contract
  "Return a validation result for a contract record."
  [m]
  (cond
    (not (map? m))                          {:labor/valid? false :labor/error :not-a-map}
    (not (:contract/id m))                  {:labor/valid? false :labor/error :missing-id}
    (not (contains? wage-types (:contract/wage-type m)))
    {:labor/valid? false :labor/error :unknown-wage-type}
    :else                                   {:labor/valid? true :contract/wage-type (:contract/wage-type m)}))
