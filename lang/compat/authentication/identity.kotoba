(ns authentication.identity
  "Portable CLJ/CLJS identity-domain core. Host applications own HTTP, crypto,
  mail delivery and persistence; this namespace owns normalized contracts and
  safe account-linking decisions."
  (:require [clojure.string :as str]))

(def provider-types #{:email :google :github :apple :microsoft :oidc :saml :passkey :cacao})
(def session-max-age-seconds (* 30 24 60 60))

(defprotocol IIdentityProvider
  (-authorization-request [provider context])
  (-normalize-profile [provider token-response]))

(defn normalize-email [value]
  (let [email (some-> value str str/trim str/lower-case)]
    (when (and email (<= 3 (count email) 254)
               (re-matches #"[^\s@]+@[^\s@]+\.[^\s@]+" email))
      email)))

(defn identity-key [{:identity/keys [provider provider-subject]}]
  (when (and (provider-types provider) (string? provider-subject)
             (not (str/blank? provider-subject)))
    [provider provider-subject]))

(defn normalized-profile
  "Provider-neutral identity returned only after the adapter has verified the
  provider response (OAuth state/PKCE/nonce and OIDC signature are host work)."
  [{:keys [provider provider-subject email email-verified? display-name avatar-url claims]}]
  (let [profile {:identity/provider provider
                 :identity/provider-subject provider-subject
                 :identity/email (normalize-email email)
                 :identity/email-verified? (true? email-verified?)
                 :identity/display-name display-name
                 :identity/avatar-url avatar-url
                 :identity/claims (or claims {})}]
    (when-not (identity-key profile)
      (throw (ex-info "invalid provider identity" {:provider provider})))
    profile))

(defn link-decision
  "Returns an explicit decision; never links accounts merely because provider
  email strings match. `current-user-id` means the caller has a live session and
  has completed provider re-authentication."
  [{:keys [current-user-id existing-identity-user-id verified-email-user-id profile]}]
  (cond
    existing-identity-user-id
    {:decision :sign-in :user-id existing-identity-user-id}

    current-user-id
    {:decision :link :user-id current-user-id :identity profile}

    verified-email-user-id
    {:decision :require-existing-account-reauth
     :user-id verified-email-user-id
     :reason :email-match-is-not-proof-of-account-control}

    :else
    {:decision :create-user :identity profile}))

(defn account-tx
  "Datomic-compatible transaction data for a newly allocated user and tenant.
  IDs are supplied by the host so this core remains deterministic/testable."
  [{:keys [user-id tenant-id tenant-did identity now]}]
  [{:db/id user-id
    :identity.user/id user-id
    :identity.user/created-at now
    :identity.user/status :active}
   (assoc identity
          :db/id "identity"
          :identity/user user-id
          :identity/created-at now)
   {:db/id tenant-id
    :identity.tenant/id tenant-id
    :identity.tenant/did tenant-did
    :identity.tenant/created-at now}
   {:db/id "membership"
    :identity.membership/user user-id
    :identity.membership/tenant tenant-id
    :identity.membership/role :owner
    :identity.membership/created-at now}])

(defn session-record
  "Server-side record. Only `token-digest`, never the opaque cookie token, is
  persisted."
  [{:keys [session-id user-id tenant-id application token-digest created-at expires-at
           user-agent-hash ip-prefix]}]
  (when-not (and (string? token-digest) (<= 32 (count token-digest)))
    (throw (ex-info "session token digest required" {})))
  {:identity.session/id session-id
   :identity.session/user user-id
   :identity.session/tenant tenant-id
   :identity.session/application application
   :identity.session/token-digest token-digest
   :identity.session/created-at created-at
   :identity.session/expires-at expires-at
   :identity.session/user-agent-hash user-agent-hash
   :identity.session/ip-prefix ip-prefix
   :identity.session/revoked? false})

(defn oauth-transaction
  "Short-lived state stored by the host before redirecting to a provider. The
  PKCE verifier is server-side only; the browser receives state and challenge."
  [{:keys [id app-id provider state nonce pkce-verifier redirect-uri return-to expires-at]}]
  (when-not (and (provider-types provider) (not= provider :email)
                 (every? #(and (string? %) (not (str/blank? %)))
                         [state nonce pkce-verifier redirect-uri]))
    (throw (ex-info "invalid OAuth transaction" {:provider provider})))
  {:identity.oauth/id id
   :identity.oauth/app-id app-id
   :identity.oauth/provider provider
   :identity.oauth/state state
   :identity.oauth/nonce nonce
   :identity.oauth/pkce-verifier pkce-verifier
   :identity.oauth/redirect-uri redirect-uri
   :identity.oauth/return-to return-to
   :identity.oauth/expires-at expires-at
   :identity.oauth/used? false})

(defn public-user [user identities memberships]
  {:user/id (:identity.user/id user)
   :user/status (:identity.user/status user)
   :user/identities (mapv #(select-keys % [:identity/provider :identity/email
                                           :identity/email-verified? :identity/display-name
                                           :identity/avatar-url]) identities)
   :user/memberships (mapv #(select-keys % [:identity.membership/tenant
                                             :identity.membership/role]) memberships)})
