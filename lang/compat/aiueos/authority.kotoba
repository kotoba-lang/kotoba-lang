(ns aiueos.authority
  "Pure Principal–Intent–Decision authority kernel.

  This namespace is the narrow waist between credential adapters and runtime
  providers.  It accepts verified, data-only facts; it never parses cookies,
  CACAO, Passkeys, JWTs, or private keys.  It returns a decision and, on
  success, a capability *specification*.  The host broker remains responsible
  for turning that specification into an unforgeable runtime handle.")

(def input-keys
  #{:authority/principal :authority/actor :authority/intent
    :authority/effects :authority/grants :authority/policy :authority/context})

(def principal-required-keys
  #{:principal/id :principal/kind :principal/authenticated?})

(def principal-keys
  (into principal-required-keys
        #{:principal/account :principal/tenant :principal/authn-method
          :principal/assurance :principal/delegator}))

(def actor-required-keys #{:actor/id :actor/kind})
(def actor-keys (into actor-required-keys #{:actor/code-cid}))

(def intent-required-keys #{:intent/action :intent/resource})
(def intent-keys
  (into intent-required-keys #{:intent/audience :intent/body-cid}))

(def grant-required-keys
  #{:grant/id :grant/subject :grant/actions :grant/resources})

(def grant-keys
  (into grant-required-keys
        #{:grant/audience :grant/tenant :grant/not-before :grant/expires-at
          :grant/revoked? :grant/parent :grant/limits}))

(def policy-keys
  #{:policy/public :policy/deny-actions :policy/min-assurance
    :policy/required-approvals})

(def context-required-keys #{:context/now})
(def context-keys
  (into context-required-keys
        #{:context/audience :context/tenant :context/approvals
          :context/nonce :context/nonce-used?}))

(def assurance-rank
  {:anonymous 0 :low 1 :substantial 2 :high 3})

(defn- closed-map? [value required allowed]
  (and (map? value)
       (every? #(contains? value %) required)
       (every? allowed (keys value))))

(defn- set-like? [value]
  (or (set? value) (vector? value) (list? value)))

(defn- optional-number? [value]
  (or (nil? value) (number? value)))

(defn- valid-grant? [grant]
  (and (closed-map? grant grant-required-keys grant-keys)
       (set-like? (:grant/actions grant))
       (set-like? (:grant/resources grant))
       (optional-number? (:grant/not-before grant))
       (optional-number? (:grant/expires-at grant))
       (or (nil? (:grant/revoked? grant))
           (boolean? (:grant/revoked? grant)))))

(defn- valid-policy? [policy]
  (and (closed-map? policy #{} policy-keys)
       (or (nil? (:policy/public policy))
           (set-like? (:policy/public policy)))
       (or (nil? (:policy/deny-actions policy))
           (set-like? (:policy/deny-actions policy)))
       (or (nil? (:policy/min-assurance policy))
           (and (map? (:policy/min-assurance policy))
                (every? assurance-rank (vals (:policy/min-assurance policy)))))
       (or (nil? (:policy/required-approvals policy))
           (and (map? (:policy/required-approvals policy))
                (every? #(and (integer? %) (not (neg? %)))
                        (vals (:policy/required-approvals policy)))))))

(defn- valid-context? [context]
  (and (closed-map? context context-required-keys context-keys)
       (number? (:context/now context))
       (or (nil? (:context/approvals context))
           (set-like? (:context/approvals context)))
       (or (nil? (:context/nonce-used? context))
           (boolean? (:context/nonce-used? context)))))

(defn- invalid-input-reason [{:authority/keys [principal actor intent effects
                                                grants policy context] :as input}]
  (cond
    (not= input-keys (set (keys input))) :authority/invalid-envelope
    (not (closed-map? principal principal-required-keys principal-keys)) :authority/invalid-principal
    (not (closed-map? actor actor-required-keys actor-keys)) :authority/invalid-actor
    (not (closed-map? intent intent-required-keys intent-keys)) :authority/invalid-intent
    (not (set-like? effects)) :authority/invalid-effects
    (not (and (vector? grants) (every? valid-grant? grants))) :authority/invalid-grants
    (not (valid-policy? policy)) :authority/invalid-policy
    (not (valid-context? context)) :authority/invalid-context
    :else nil))

(defn deny
  "Return a canonical fail-closed decision. DETAIL is evidence, not authority."
  ([reason] (deny reason nil))
  ([reason detail]
   (cond-> {:decision/status :deny
            :decision/reason reason}
     (some? detail) (assoc :decision/detail detail))))

(defn- challenge [reason missing]
  {:decision/status :challenge
   :decision/reason reason
   :decision/missing missing})

(defn- member? [items value]
  (contains? (set items) value))

(defn- resource-match? [resources resource]
  (or (member? resources :*)
      (member? resources resource)))

(defn- public-intent? [policy action resource]
  (let [public (set (:policy/public policy))]
    (or (contains? public [action resource])
        (contains? public [action :*]))))

(defn- effect-allows? [effects action resource]
  (let [effects (set effects)]
    (or (contains? effects action)
        (contains? effects [action resource])
        (contains? effects [action :*]))))

(defn- live-grant? [grant now]
  (and (not (:grant/revoked? grant))
       (or (nil? (:grant/not-before grant))
           (<= (:grant/not-before grant) now))
       (or (nil? (:grant/expires-at grant))
           (< now (:grant/expires-at grant)))))

(defn- grant-matches? [grant principal intent context]
  (let [principal-id (:principal/id principal)
        principal-tenant (:principal/tenant principal)
        action (:intent/action intent)
        resource (:intent/resource intent)
        audience (or (:intent/audience intent) (:context/audience context))
        tenant (or (:context/tenant context) principal-tenant)]
    (and (= principal-id (:grant/subject grant))
         (member? (:grant/actions grant) action)
         (resource-match? (:grant/resources grant) resource)
         (or (nil? (:grant/audience grant))
             (= audience (:grant/audience grant)))
         (or (nil? (:grant/tenant grant))
             (and (= tenant (:grant/tenant grant))
                  (or (nil? principal-tenant)
                      (= principal-tenant (:grant/tenant grant)))))
         (live-grant? grant (:context/now context)))))

(defn- assurance-sufficient? [principal required]
  (>= (get assurance-rank (:principal/assurance principal) 0)
      (get assurance-rank required 0)))

(defn- required-approval-count [policy action]
  (get (:policy/required-approvals policy) action 0))

(defn- independent-approvals [principal context]
  (disj (set (:context/approvals context)) (:principal/id principal)))

(defn decide
  "Decide one normalized authority request.

  The function is pure and deterministic. Unknown fields and malformed data
  deny rather than being ignored. Authentication and satisfiable shared-
  approval/assurance shortages return :challenge; policy, effect, grant,
  tenant, audience, replay, revocation, and expiry failures return :deny."
  [{:authority/keys [principal actor intent effects grants policy context] :as input}]
  (if-let [reason (invalid-input-reason input)]
    (deny reason)
    (let [action (:intent/action intent)
          resource (:intent/resource intent)
          public? (public-intent? policy action resource)
          matching-grants (filterv #(grant-matches? % principal intent context) grants)
          required-assurance (get (:policy/min-assurance policy) action :anonymous)
          required-approvals (required-approval-count policy action)
          approvals (independent-approvals principal context)]
      (cond
        (member? (:policy/deny-actions policy) action)
        (deny :authority/action-denied)

        (:context/nonce-used? context)
        (deny :authority/nonce-replayed)

        (and (:intent/audience intent)
             (:context/audience context)
             (not= (:intent/audience intent) (:context/audience context)))
        (deny :authority/audience-mismatch)

        (and (:principal/tenant principal)
             (:context/tenant context)
             (not= (:principal/tenant principal) (:context/tenant context)))
        (deny :authority/tenant-mismatch)

        (not (effect-allows? effects action resource))
        (deny :authority/effect-not-declared)

        (and (not public?) (not (:principal/authenticated? principal)))
        (challenge :authority/authentication-required
                   [{:missing/type :principal/authentication}])

        (not (assurance-sufficient? principal required-assurance))
        (challenge :authority/step-up-required
                   [{:missing/type :principal/assurance
                     :missing/required required-assurance}])

        (< (count approvals) required-approvals)
        (challenge :authority/shared-approval-required
                   [{:missing/type :authority/approval
                     :missing/count (- required-approvals (count approvals))}])

        (and (not public?) (empty? matching-grants))
        (deny :authority/grant-missing)

        :else
        {:decision/status :allow
         :decision/reason (if public? :authority/public-policy
                              :authority/grant-matched)
         :decision/grant-ids (mapv :grant/id matching-grants)
         :decision/runtime-capability-spec
         {:capability/principal (:principal/id principal)
          :capability/actor (:actor/id actor)
          :capability/action action
          :capability/resource resource
          :capability/audience (or (:intent/audience intent)
                                   (:context/audience context))
          :capability/tenant (or (:context/tenant context)
                                 (:principal/tenant principal))}}))))

(def receipt-input-keys
  #{:receipt/intent-cid :receipt/principal :receipt/actor :receipt/code-cid
    :receipt/policy-cid :receipt/grant-cids :receipt/decision :receipt/outcome
    :receipt/basis :receipt/provider :receipt/at})

(def outcome-required-keys #{:outcome/status})
(def outcome-keys #{:outcome/status :outcome/result-cid :outcome/error-code})

(defn receipt
  "Validate and return the canonical receipt projection.

  The closed shape intentionally has no credential, bearer-token, private-key,
  or raw-secret slot. CID computation and durable append are storage concerns."
  [input]
  (when-not (and (= receipt-input-keys (set (keys input)))
                 (contains? #{:allow :deny :challenge}
                            (get-in input [:receipt/decision :decision/status]))
                 (closed-map? (:receipt/outcome input)
                              outcome-required-keys outcome-keys)
                 (vector? (:receipt/grant-cids input)))
    (throw (ex-info "invalid authority receipt" {:reason :authority/invalid-receipt})))
  input)
