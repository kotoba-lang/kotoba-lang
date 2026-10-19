(ns authorization.adapters.rules
  (:require [authorization.adapters.policy :as policy]))

(defn policy-bundle [id version rules opts]
  {:authz.policy/id id
   :authz.policy/version version
   :authz.policy/rules (vec rules)
   :authz.policy/default-decision (or (:default-decision opts) :deny)
   :authz.policy/issued-at (:issued-at opts)})

(defn- matches-value? [expected actual]
  (cond
    (nil? expected) true
    (set? expected) (contains? expected actual)
    (fn? expected) (boolean (expected actual))
    :else (= expected actual)))

(defn- rule-match? [payload rule]
  (and (matches-value? (:principal rule) (:principal payload))
       (matches-value? (:action rule) (:action payload))
       (matches-value? (:resource rule) (:resource payload))
       (matches-value? (:capability-ref rule) (:capability-ref payload))
       (every? (fn [[k expected]]
                 (matches-value? expected (get-in payload [:context k])))
               (:context rule))))

(defn- rule-effect [rule matched?]
  {:rule-id (:id rule)
   :decision (:decision rule)
   :matched? (boolean matched?)})

(defn- evaluate-rules [rules payload default-decision policy-ref policy-version]
  (let [matched-idx (first (keep-indexed (fn [i r] (when (rule-match? payload r) i)) rules))
        matched-rule (when matched-idx (nth rules matched-idx))
        effect-trace (vec (map-indexed (fn [i r] (rule-effect r (= i matched-idx))) rules))]
    (if matched-rule
      {:decision (:decision matched-rule)
       :by (or (:by matched-rule) "rules")
       :reason (:reason matched-rule)
       :policy-ref policy-ref
       :policy-version policy-version
       :effect-trace effect-trace
       :obligations (:obligations matched-rule)
       :issued-at (:issued-at matched-rule)}
      {:decision default-decision
       :by "rules"
       :reason (when (= :deny default-decision) :no-matching-rule)
       :policy-ref policy-ref
       :policy-version policy-version
       :effect-trace effect-trace
       :obligations []})))

(defn rules-engine
  ([rules] (rules-engine rules {:default-decision :deny}))
  ([rules opts]
   (let [bundle (if (:authz.policy/rules rules)
                  rules
                  (policy-bundle (:policy-ref opts)
                                 (:policy-version opts)
                                 rules
                                 opts))
         bundle-rules (:authz.policy/rules bundle)]
     (reify policy/IPolicyEngine
       (evaluate! [_ payload call-opts]
         (evaluate-rules bundle-rules
                         payload
                         (or (:default-decision call-opts)
                             (:authz.policy/default-decision bundle)
                             :deny)
                         (or (:policy-ref call-opts)
                             (:authz.policy/id bundle))
                         (or (:policy-version call-opts)
                             (:authz.policy/version bundle))))))))
