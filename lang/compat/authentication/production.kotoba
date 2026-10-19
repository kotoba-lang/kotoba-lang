(ns authentication.production
  "Fail-closed production security contracts for identity-service hosts."
  (:require [clojure.string :as str]))

(def secret-keys
  #{:token :access-token :refresh-token :password :secret :pkce-verifier
    :cacao-b64 :authorization-code})

(defn secure-redirect-uri? [uri]
  (and (string? uri)
       (or (str/starts-with? uri "https://")
           (str/starts-with? uri "http://127.0.0.1:")
           (str/starts-with? uri "http://localhost:"))
       (not (str/includes? uri "#"))))

(defn application
  [{:keys [id redirect-uris]}]
  (let [redirect-uris (set redirect-uris)]
    (when-not (and (string? id) (not (str/blank? id)) (seq redirect-uris)
                   (every? secure-redirect-uri? redirect-uris))
      (throw (ex-info "invalid application redirect allowlist" {:application/id id})))
    {:identity.application/id id
     :identity.application/redirect-uris redirect-uris}))

(defn redirect-allowed? [app uri]
  (and (secure-redirect-uri? uri)
       (contains? (:identity.application/redirect-uris app) uri)))

(defn csrf-valid?
  [{:keys [cookie-token request-token origin allowed-origins]}]
  (and (string? cookie-token) (<= 32 (count cookie-token))
       (= cookie-token request-token)
       (contains? (set allowed-origins) origin)))

(defn rate-limit
  [{:keys [attempts limit window-start now window-seconds]}]
  (let [expired? (>= (- now window-start) window-seconds)
        attempts (if expired? 0 attempts)
        allowed? (< attempts limit)]
    {:allowed? allowed?
     :attempts (if allowed? (inc attempts) attempts)
     :window-start (if expired? now window-start)
     :retry-after (when-not allowed? (max 0 (- window-seconds (- now window-start))))}))

(defn rotate-session-tx
  [{:keys [old-session-id new-session now]}]
  (when-not (and old-session-id (:identity.session/id new-session))
    (throw (ex-info "session rotation requires old and new session ids" {})))
  [{:identity.session/id old-session-id
    :identity.session/revoked? true
    :identity.session/revoked-at now
    :identity.session/revocation-reason :rotated}
   new-session])

(defn global-revoke-tx [user-id active-session-ids now]
  (mapv (fn [session-id]
          {:identity.session/id session-id
           :identity.session/user user-id
           :identity.session/revoked? true
           :identity.session/revoked-at now
           :identity.session/revocation-reason :global-revocation})
        active-session-ids))

(defn active-signing-key [keys now]
  (->> keys
       (filter #(and (= :active (:status %))
                     (<= (:not-before %) now)
                     (< now (:not-after %))))
       (sort-by :not-before >)
       first))

(defn audit-event [{:keys [type actor-id subject-id application-id at data]}]
  (when-not (and type actor-id at)
    (throw (ex-info "audit event requires type, actor and time" {})))
  {:identity.audit/type type
   :identity.audit/actor-id actor-id
   :identity.audit/subject-id subject-id
   :identity.audit/application-id application-id
   :identity.audit/at at
   :identity.audit/data (apply dissoc (or data {}) secret-keys)})

(defn recovery-decision
  [{:keys [verified-factors required-factors cooldown-until now]}]
  (cond
    (< now (or cooldown-until 0)) {:decision :hold :reason :cooldown}
    (< (count (set verified-factors)) (or required-factors 2))
    {:decision :hold :reason :insufficient-independent-factors}
    :else {:decision :allow :rotate-all-sessions? true :audit-required? true}))
