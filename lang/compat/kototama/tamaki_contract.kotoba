(ns kototama.tamaki-contract
  "Kototama admission of the shared actor capability envelope.

  kotoba-core-contracts owns vocabulary, effects, policy decisions, envelope
  shape, and host-neutral validation. Kototama adds the concrete HostCaps and
  actor:host import-surface checks immediately before execution."
  (:require [kotoba.core.actor-capability :as actor-capability]
            [kotoba.core.capability-repository :as repository]
            [kototama.contract :as contract]))

(def envelope-version actor-capability/envelope-version)
(def decisions actor-capability/decisions)
(def detailed-import-effects actor-capability/import-effects)

(defn admit
  "Return {:ok? true :host-caps ...} or a fail-closed error report."
  [envelope]
  (let [shared-report (actor-capability/validate-envelope envelope)
        repository-errors
        (repository/validate-envelope-repositories envelope)
        imports (:imports shared-report)
        grants (:grants shared-report)
        limits (:limits shared-report)
        caps (contract/host-caps {:grants grants :limits limits})
        abi (:tamaki.capability/abi envelope)
        surface {:abi/namespace (:namespace abi)
                 :abi/version (:version abi)
                 :abi/imports (vec imports)}
        host-report (contract/validate-import-surface surface caps)
        errors (vec (concat (:errors shared-report)
                            repository-errors
                            (:errors host-report)))]
    (cond-> (assoc shared-report
                   :ok? (empty? errors)
                   :errors errors)
      (empty? errors) (assoc :host-caps caps))))

(defn admit!
  [envelope]
  (let [report (admit envelope)]
    (when-not (:ok? report)
      (throw (ex-info "Kototama rejected Tamaki capability envelope"
                      report)))
    report))
