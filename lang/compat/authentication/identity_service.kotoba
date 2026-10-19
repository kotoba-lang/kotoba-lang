(ns authentication.identity-service
  "Async CLJS orchestration for email/SNS authentication. Provider verification
  happens before `complete-profile!`; durable and ephemeral stores are injected
  through identity ports."
  (:require [authentication.identity :as identity]
            [authentication.identity-ports :as ports]
            [authentication.adapters.webcrypto :as crypto]))

(def oauth-ttl-seconds 600)
(def session-ttl-seconds identity/session-max-age-seconds)

(defn- now-ms [service] ((:now service)))
(defn- new-id [service prefix] (str prefix ((:random-id service))))

(defn service
  [{:keys [identity-store ephemeral-store session-store now random-id tenant-did]
    :as options}]
  (when-not (ports/identity-store? identity-store)
    (throw (js/Error. "identity-store required")))
  (when-not (ports/ephemeral-store? ephemeral-store)
    (throw (js/Error. "ephemeral-store required")))
  (when (and session-store (not (ports/session-store? session-store)))
    (throw (js/Error. "session-store must implement ISessionStore")))
  (merge {:now #(js/Date.now)
          :random-id #(str (js/crypto.randomUUID))
          :tenant-did #(str "did:web:kotobase.net:tenant:" %)}
         options))

(defn begin-oauth!
  "Persist a single-use OAuth transaction and return public redirect inputs.
  Client secret and PKCE verifier never appear in the returned map."
  [service {:keys [app-id provider redirect-uri return-to]}]
  (let [id (new-id service "oauth_")
        state (crypto/random-token 32)
        nonce (crypto/random-token 32)
        expires-at (+ (now-ms service) (* oauth-ttl-seconds 1000))]
    (-> (crypto/pkce-pair)
        (.then
         (fn [pkce]
           (let [transaction (identity/oauth-transaction
                              {:id id :app-id app-id :provider provider
                               :state state :nonce nonce
                               :pkce-verifier (aget pkce "verifier")
                               :redirect-uri redirect-uri :return-to return-to
                               :expires-at expires-at})]
             (-> (ports/-put-once! (:ephemeral-store service)
                                   (str "oauth:" state) transaction oauth-ttl-seconds)
                 (.then (fn [version]
                          (when-not version (throw (js/Error. "OAuth state collision")))
                          {:transaction-id id :provider provider :state state
                           :nonce nonce :code-challenge (aget pkce "challenge")
                           :code-challenge-method "S256"
                           :redirect-uri redirect-uri})))))))))

(defn consume-oauth!
  "Validate and atomically consume OAuth state. Returns the private transaction
  needed by the host's server-side code exchange."
  [service state]
  (-> (ports/-get-value (:ephemeral-store service) (str "oauth:" state))
      (.then (fn [record]
               (when-not record (throw (js/Error. "invalid or expired OAuth state")))
               (let [transaction (:value record)]
                 (when (<= (:identity.oauth/expires-at transaction) (now-ms service))
                   (throw (js/Error. "expired OAuth state")))
                 (-> (ports/-consume! (:ephemeral-store service)
                                     (str "oauth:" state) (:version record))
                     (.then (fn [consumed]
                              (when-not consumed
                                (throw (js/Error. "OAuth state already consumed")))
                              consumed))))))))

(defn issue-session!
  [service {:keys [user-id tenant-id application user-agent-hash ip-prefix]}]
  (when-not (and (string? application) (not= "" application))
    (throw (js/Error. "session application audience required")))
  (let [token (crypto/random-token 32)
        session-id (new-id service "session_")
        created-at (now-ms service)
        expires-at (+ created-at (* session-ttl-seconds 1000))]
    (-> (crypto/sha256-base64url token)
        (.then (fn [digest]
                 (let [record (identity/session-record
                               {:session-id session-id :user-id user-id :tenant-id tenant-id
                                :application application :token-digest digest
                                :created-at created-at :expires-at expires-at
                                :user-agent-hash user-agent-hash :ip-prefix ip-prefix})]
                   (-> (if-let [session-store (:session-store service)]
                         (js/Promise.resolve
                          (ports/-create-session! session-store record))
                         (ports/-put-once! (:ephemeral-store service)
                                           (str "session:" digest) record session-ttl-seconds))
                       (.then (fn [created]
                                (when-not created (throw (js/Error. "session collision")))
                                {:token token :session-id session-id :expires-at expires-at
                                 :user-id user-id :tenant-id tenant-id})))))))))

(defn complete-profile!
  "Apply safe linking policy to an already provider-verified profile, then
  issue a session. Hosts must not pass unverified OAuth/userinfo responses."
  [service {:keys [profile current-user-id application user-agent-hash ip-prefix]}]
  (let [[provider subject] (identity/identity-key profile)
        store (:identity-store service)]
    (-> (js/Promise.resolve (ports/-find-user-by-identity store provider subject))
        (.then
         (fn [existing-user-id]
           (-> (if (:identity/email-verified? profile)
                 (js/Promise.resolve
                  (ports/-find-user-by-verified-email store (:identity/email profile)))
                 (js/Promise.resolve nil))
               (.then
                (fn [email-user-id]
                  (let [decision (identity/link-decision
                                  {:current-user-id current-user-id
                                   :existing-identity-user-id existing-user-id
                                   :verified-email-user-id email-user-id
                                   :profile profile})]
                    (case (:decision decision)
                      :require-existing-account-reauth
                      (throw (ex-info "existing account reauthentication required" decision))

                      :sign-in
                      {:decision :sign-in :user-id (:user-id decision)}

                      :link
                      (-> (js/Promise.resolve
                           (ports/-link-identity! store (:user-id decision) profile (now-ms service)))
                          (.then (fn [_] {:decision :link :user-id (:user-id decision)})))

                      :create-user
                      (let [user-id (new-id service "user_")
                            tenant-id (new-id service "tenant_")
                            tx (identity/account-tx
                                {:user-id user-id :tenant-id tenant-id
                                 :tenant-did ((:tenant-did service) tenant-id)
                                 :identity profile :now (now-ms service)})]
                        (-> (js/Promise.resolve (ports/-create-account! store tx))
                            (.then (fn [_] {:decision :create-user :user-id user-id
                                           :tenant-id tenant-id})))))))))))
        (.then
         (fn [{:keys [user-id tenant-id decision]}]
           (-> (if tenant-id
                 (js/Promise.resolve tenant-id)
                 (js/Promise.resolve
                  (let [view (ports/-user-view store user-id)]
                    (if (instance? js/Promise view)
                      (.then view #(or (:tenant-id %) (:identity.membership/tenant %)))
                      (or (:tenant-id view) (:identity.membership/tenant view))))))
               (.then (fn [resolved-tenant-id]
                        (-> (issue-session! service
                                            {:user-id user-id :tenant-id resolved-tenant-id
                                             :application application
                                             :user-agent-hash user-agent-hash :ip-prefix ip-prefix})
                            (.then #(assoc % :decision decision)))))))))))

(defn resolve-session!
  "Resolve an opaque token through its digest. Durable session stores return
  nested user/tenant entities; expired, revoked, or inactive sessions fail
  closed. This is the backing operation for a shared /v1/session endpoint."
  [service token]
  (-> (crypto/sha256-base64url token)
      (.then
       (fn [digest]
         (if-let [session-store (:session-store service)]
           (js/Promise.resolve
            (ports/-find-session-by-digest session-store digest))
           (-> (ports/-get-value (:ephemeral-store service)
                                 (str "session:" digest))
               (.then #(some-> % :value))))))
      (.then
       (fn [record]
         (when (and record
                    (not (:identity.session/revoked? record))
                    (< (now-ms service) (:identity.session/expires-at record))
                    (not= :disabled
                          (get-in record [:identity.session/user
                                          :identity.user/status])))
           record)))))

(defn revoke-session! [service token]
  (-> (crypto/sha256-base64url token)
      (.then (fn [digest]
               (if-let [session-store (:session-store service)]
                 (ports/-revoke-session! session-store digest (now-ms service))
                 (ports/-delete! (:ephemeral-store service)
                                 (str "session:" digest)))))))

(defn rotate-session!
  "Issue a replacement for the same user, tenant, and application, then revoke
  the old opaque credential. A replacement failure leaves the old session
  intact; a successful return guarantees that the old digest is revoked."
  [service token {:keys [user-agent-hash ip-prefix]}]
  (.then
   (resolve-session! service token)
   (fn [record]
     (when-not record
       (throw (js/Error. "invalid or expired session")))
     (let [user (:identity.session/user record)
           tenant (:identity.session/tenant record)
           user-id (if (map? user) (:identity.user/id user) user)
           tenant-id (if (map? tenant) (:identity.tenant/id tenant) tenant)
           application (:identity.session/application record)
           replacement
           (issue-session! service
                           {:user-id user-id
                            :tenant-id tenant-id
                            :application application
                            :user-agent-hash user-agent-hash
                            :ip-prefix ip-prefix})]
       (.then
        replacement
        (fn [new-session]
          (.then
           (revoke-session! service token)
           (fn [_]
             (assoc new-session
                    :rotated-from-session-id
                    (:identity.session/id record))))))))))
