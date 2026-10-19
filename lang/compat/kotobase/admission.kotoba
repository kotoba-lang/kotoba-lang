(ns kotobase.admission
  "Authorization membrane for hydrate, execute, and pin operations.

  A CID proves bytes, never authority. Production callers must pass package
  admission, delegated grants, local policy, and durable audit admission
  before the effect thunk can run."
  (:require [clojure.set :as set]))

(def production-actions #{:hydrate :execute :pin})

(defn decide
  [{:keys [action cid-verified? package-receipt requested-effects
           delegated-effects local-policy-effects resource]}]
  (let [requested (set requested-effects)
        delegated (set delegated-effects)
        local (set local-policy-effects)
        granted (set/intersection requested delegated local)
        missing (set/difference requested granted)
        package-ok? (and (true? (:package/verified? package-receipt))
                         (string? (:package/lock-cid package-receipt))
                         (seq (:package/lock-cid package-receipt)))
        code (cond
               (not (contains? production-actions action))
               :admission/unknown-action
               (not cid-verified?) :admission/cid-invalid
               (not package-ok?) :admission/package
               (or (contains? delegated :any) (contains? local :any)
                   (contains? requested :any))
               :admission/wildcard
               (empty? requested) :admission/no-requested-effects
               (seq missing) :admission/capability
               (not (and (string? resource) (seq resource)))
               :admission/resource
               :else nil)]
    {:admission/allowed? (nil? code)
     :admission/code code
     :admission/action action
     :admission/resource resource
     :admission/package-lock-cid (:package/lock-cid package-receipt)
     :admission/requested requested
     :admission/granted granted
     :admission/missing missing}))

(defn guard!
  "Audit the authorization decision before invoking EFFECT.
   AUDIT! must return a durable receipt acknowledgement."
  [{:keys [audit! effect] :as request}]
  (let [decision (decide request)
        audit-ack (when (ifn? audit!) (audit! decision))
        audit-ok? (and (map? audit-ack)
                       (true? (:audit/durable? audit-ack))
                       (string? (:audit/receipt-id audit-ack))
                       (seq (:audit/receipt-id audit-ack)))]
    (when-not audit-ok?
      (throw (ex-info "authorization audit persistence failed"
                      {:type :kotobase/audit-denied
                       :decision decision})))
    (when-not (:admission/allowed? decision)
      (throw (ex-info "content authorization denied"
                      {:type :kotobase/admission-denied
                       :decision decision
                       :audit audit-ack})))
    (when-not (ifn? effect)
      (throw (ex-info "authorized effect missing"
                      {:type :kotobase/effect-missing})))
    {:decision decision :audit audit-ack :result (effect decision)}))
