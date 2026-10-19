(ns connector.consent
  "What to ask a person to grant, computed from what is actually enabled.

  Two facts make this more than a scope concatenation:

  1. Scopes are declared per tool, so a deployment running only a calendar
     reader asks for `calendar.readonly` and nothing else — even though the
     same OAuth client is capable of `gmail.send`.

  2. Several connectors can share one OAuth client. Google Drive, Gmail and
     Google Calendar are three connectors with one authorization endpoint, and
     asking a person to approve Google three times to enable three of its
     services is worse than useless — the second and third dialogs teach people
     to click through. They are grouped by `(authorization-endpoint, client-id
     env)` and asked once, for the union of what those connectors need.

  Grouping by the CLIENT rather than by the provider name is deliberate: two
  deployments of the same service under different OAuth clients must not share
  a grant, and nothing here can tell them apart by name."
  (:require [clojure.string :as str]
            [connector.auth :as auth]
            [connector.model :as m]
            [connector.provider :as p]
            [connector.registry :as reg]))

(defn- oauth2? [d]
  (= :oauth2 (get-in d [:connector/auth :connector.auth/kind])))

(defn- client-key [d]
  (let [a (:connector/auth d)]
    [(:connector.auth/authorization-endpoint a)
     (:connector.auth/token-endpoint a)
     (:connector.auth/client-id-env a)]))

(defn groups
  "The OAuth clients this registry needs, one entry per client.

  Each entry carries the connectors sharing it, the union of the scopes their
  ENABLED tools need, and the auth profile to drive the flow with. Sorted by
  client-id env then endpoint so the order is stable."
  [registry]
  (->> (reg/descriptors registry)
       (filter oauth2?)
       (group-by client-key)
       (map (fn [[k ds]]
              {:connector.consent/client k
               :connector.consent/auth (:connector/auth (first ds))
               :connector.consent/connectors (mapv :connector/id ds)
               :connector.consent/client-id-env
               (get-in (first ds) [:connector/auth :connector.auth/client-id-env])
               :connector.consent/scopes
               (->> ds (mapcat m/scopes) distinct sort vec)}))
       (sort-by (juxt :connector.consent/client-id-env
                      (comp str :connector.consent/client)))
       vec))

(defn authorization
  "One authorization URL per OAuth client the registry needs.

  opts: {:client-ids {\"GOOGLE_CLIENT_ID\" \"…\"} :redirect-uri :state-fn
         :code-challenge-fn :login-hint}

  `:state-fn` and `:code-challenge-fn` take the group and return the value, so
  the caller can mint a different state per client and keep the verifier it
  will need at callback time. Neither is generated here: state is only worth
  anything if the process that minted it checks it coming back, and a library
  cannot do the second half.

  A group whose client id is not supplied is returned with
  `:connector.consent/unconfigured true` rather than omitted. Silently dropping
  it would make an unconfigured provider look like one nobody asked for, and
  the operator would see a connector that never offers to connect."
  [registry {:keys [client-ids redirect-uri state-fn code-challenge-fn login-hint]}]
  (mapv (fn [g]
          (let [env (:connector.consent/client-id-env g)
                client-id (get client-ids env)
                descriptor {:connector/id (str/join "+" (:connector.consent/connectors g))
                            :connector/auth (:connector.consent/auth g)}]
            (if (str/blank? (str client-id))
              (assoc g :connector.consent/unconfigured true)
              (assoc g
                     :connector.consent/url
                     (auth/authorization-url
                      descriptor
                      {:client-id client-id
                       :redirect-uri redirect-uri
                       :state (when state-fn (state-fn g))
                       :scopes (:connector.consent/scopes g)
                       :code-challenge (when code-challenge-fn (code-challenge-fn g))
                       :login-hint login-hint})))))
        (groups registry)))

(defn grant-summary
  "What a grant screen should say, in the terms a person can check.

  Every line names a connector, a tool and the effect, because a scope string
  is not a sentence anyone can consent to. ADR-2608093000 states the same rule
  from the other side: a capability whose meaning cannot be written down for a
  human is one not to add."
  [registry]
  (vec
   (for [pr (sort-by p/id (reg/providers registry))
         :let [d (p/descriptor pr)]
         t (m/tools d)]
     {:connector/id (:connector/id d)
      :connector/name (:connector/name d)
      :connector/tool (:connector/name t)
      :connector/effect (:connector/effect t)
      :connector/description (:connector/description t)
      :connector/scopes (vec (:connector/scopes t))})))
