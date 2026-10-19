(ns chobo.subscription
  "Subscription / entitlement / metering model (pure), extracted from the
  ai-gftd-apex product operating design. Portable .cljc.

  Plan{:id :name :tier :price :currency :entitlements :quotas :overage-rates}.
  Entitlement = a feature flag with optional limit; Quota = a metered limit
  (e.g. :image-gen 100/month); Usage = {tenant → {key → used}}; metering events
  accumulate usage; overage = usage above quota × overage-rate.

  This is the services-EC counterpart to mise's physical cart/pricing: a
  subscription plan prices recurring entitlement + metered usage + overage."
  (:require [chobo.ledger :as ledger]))

(defrecord Plan [id name tier price currency entitlements quotas overage-rates])
(defrecord Usage [tenant consumed])

(defn plan
  [m] (merge {:currency "JPY" :entitlements #{} :quotas {} :overage-rates {}} m))

(defn- quotas-map [plan] (:quotas plan {}))
(defn- consumed-map [usage] (:consumed usage {}))

(defn entitled?
  "True if the plan grants the entitlement key (boolean) or the limit > 0."
  [plan key]
  (let [e (get-in plan [:entitlements key] false)]
    (cond (boolean? e) e
          (number? e) (pos? e)
          :else (boolean e))))

(defn quota-for [plan key] (get (quotas-map plan) key))

(defn consumed-for
  [usage key] (get (consumed-map usage) key 0))

(defn within-quota?
  ([plan usage key]
   (within-quota? plan usage key 1))
  ([plan usage key amount]
   (if-let [limit (quota-for plan key)]
     (<= (+ (consumed-for usage key) amount) limit)
     true))) ; no quota = unlimited

(defn record-usage
  "Return a new Usage with `amount` added to `key` for the tenant."
  [usage key amount]
  (let [cur (consumed-for usage key)]
    (assoc-in usage [:consumed key] (+ cur (max 0 amount)))))

(defn overage
  "Return the overage amount (number) for a key: max(0, consumed − quota) ×
  overage-rate. 0 if no quota or no overage configured."
  [plan usage key]
  (let [limit (quota-for plan key)
        used (consumed-for usage key)
        rate (get-in plan [:overage-rates key] 0)]
    (if (and limit (> used limit))
      (* (- used limit) rate)
      0)))

(defn total-overage
  "Sum of overage across all quota keys."
  [plan usage]
  (reduce + 0 (map #(overage plan usage %) (keys (quotas-map plan)))))

;; ---------------------------------------------------------------------------
;; sample tiers (from ai-gftd-apex design)
;; ---------------------------------------------------------------------------

(def free-tier
  (plan {:id :free :name "Free" :tier :free :price 0
         :entitlements #{:chat/text :files}
         :quotas {:ephemeral-runs 50 :image-gen 5}}))

(def pro-tier
  (plan {:id :pro :name "Pro" :tier :pro :price 2900
         :entitlements #{:chat/text :agent/code :agent/research :files :memory}
         :quotas {:ephemeral-runs 1000 :image-gen 200 :video-gen 5}
         :overage-rates {:image-gen 10 :video-gen 200}}))

(defn metering-event
  "Build a ledger activity representing a metering event (usage of `key` by
  `tenant`). Caller appends it to the audit ledger."
  [tenant key amount opts]
  (ledger/activity
   (merge {:lane :billing :kind :metering :title (str "meter " key)
           :tenant tenant :source :subscription
           :props {:key key :amount amount}} opts)))
