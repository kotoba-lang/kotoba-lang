(ns kotoba.lang.causal-receipt
  "Bind a Kotoba host receipt to one causal authority decision.

  The evaluator claim is evidence, not an executable capability. This adapter
  first validates grant's closed causal receipt contract, then requires the
  requested capability to name the same principal, action, and resource. The
  resulting execution receipt contains only the decision projection and the
  ordinary capability receipt; raw evidence and credentials have no slot."
  (:require [grant.causal-trust :as trust]
            [kotoba.lang.capability-values :as values]))

(def authority-keys
  #{:causal.execution/decision-cid :causal.execution/template})

(def template-keys
  #{:causal.receipt/intent-cid :causal.receipt/principal
    :causal.receipt/epoch-cid :causal.receipt/policy-cid
    :causal.receipt/basis-cid :causal.receipt/claim-cids
    :causal.receipt/decision})

(def bound-receipt-keys
  #{:causal.execution/version :causal.execution/authority
    :causal.execution/host-receipt})

(defn- non-empty-string? [value]
  (and (string? value) (seq value)))

(defn- reject! [reason data]
  (throw (ex-info "causal capability receipt rejected"
                  (assoc data :kotoba.causal-receipt/reason reason))))

(defn- validate-authority! [authority at]
  (when-not (and (map? authority)
                 (= authority-keys (set (keys authority))))
    (reject! :invalid-authority-envelope {}))
  (when-not (non-empty-string? (:causal.execution/decision-cid authority))
    (reject! :invalid-decision-cid {}))
  (let [template (:causal.execution/template authority)]
    (when-not (and (map? template)
                   (= template-keys (set (keys template))))
      (reject! :invalid-receipt-template {}))
    (when-not (= :allow (get-in template
                                [:causal.receipt/decision :decision/status]))
      (reject! :authority-not-allowed {}))
    (trust/receipt
     (assoc template
            :causal.receipt/id "urn:kotoba:causal-execution:preflight"
            :causal.receipt/outcome {:outcome/status :pending}
            :causal.receipt/at at)))
  authority)

(defn- resource-within? [outer inner]
  (cond
    (= :any outer) true
    (string? outer) (= outer inner)
    (set? outer) (cond
                   (string? inner) (contains? outer inner)
                   (set? inner) (every? outer inner)
                   :else false)
    :else (= outer inner)))

(defn- capability-agrees?
  [authority cap exact-resource?]
  (let [spec (get-in authority
                     [:causal.execution/template
                      :causal.receipt/decision
                      :decision/runtime-capability-spec])
        expected-resource (:capability/resource spec)
        actual-resource (:cap/resource cap)]
    (and (= (:capability/principal spec) (:cap/holder cap))
         (= (:capability/action spec) (:cap/kind cap))
         (if exact-resource?
           (= expected-resource actual-resource)
           (resource-within? expected-resource actual-resource)))))

(defn preflight!
  "Validate causal authority before a provider handler may be invoked."
  [authority requested-cap at]
  (validate-authority! authority at)
  (when-not (values/capability? requested-cap)
    (reject! :invalid-requested-capability {}))
  (when-not (capability-agrees? authority requested-cap true)
    (reject! :capability-decision-mismatch {}))
  authority)

(defn bind-receipt
  "Bind a validated causal decision to an ordinary Kotoba host receipt."
  [authority host-receipt]
  (validate-authority! authority (:receipt/at host-receipt))
  (when-not (true? (:ok? (values/validate-receipt host-receipt)))
    (reject! :invalid-host-receipt {}))
  (when-not (capability-agrees? authority (:receipt/cap host-receipt) false)
    (reject! :receipt-capability-mismatch {}))
  {:causal.execution/version 1
   :causal.execution/authority authority
   :causal.execution/host-receipt host-receipt})

(defn valid-bound-receipt?
  "True when a bound receipt has the closed shape and revalidates."
  [receipt]
  (try
    (and (map? receipt)
         (= bound-receipt-keys (set (keys receipt)))
         (= 1 (:causal.execution/version receipt))
         (= receipt
            (bind-receipt (:causal.execution/authority receipt)
                          (:causal.execution/host-receipt receipt))))
    (catch #?(:clj Exception :cljs :default) _ false)))
