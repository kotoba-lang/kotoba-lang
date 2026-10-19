(ns identity.adapters.erc8004
  "Adapter for the current draft ERC-8004 three-registry model.

  This is deliberately not an adapter for the historical Etzhayyim
  monolithic `ERC-8004-shaped` contract. Hosts must read the standard Identity,
  Reputation, and Validation registries and fetch the registration document."
  (:require [clojure.string :as str]
            [identity.causal :as causal]
            [identity.model :as model]))

(def specification "https://eips.ethereum.org/EIPS/eip-8004")
(def registration-type
  "https://eips.ethereum.org/EIPS/eip-8004#registration-v1")

(defprotocol IERC8004Reader
  (read-registry-bindings! [reader coordinate])
  (read-agent! [reader coordinate agent-id])
  (read-reputation! [reader coordinate agent-id client-addresses])
  (read-validations! [reader coordinate agent-id validator-addresses]))

(defn static-reader [{:keys [registry-bindings agents reputations validations]}]
  (reify IERC8004Reader
    (read-registry-bindings! [_ coordinate]
      (or registry-bindings
          {:reputation-identity-registry (:identity-registry coordinate)
           :validation-identity-registry (:identity-registry coordinate)}))
    (read-agent! [_ _ agent-id] (get agents agent-id))
    (read-reputation! [_ _ agent-id clients]
      (get reputations [agent-id clients]))
    (read-validations! [_ _ agent-id validators]
      (get validations [agent-id validators]))))

(defn- hex-address? [x]
  (boolean (and (string? x) (= 42 (count x)) (str/starts-with? x "0x")
                (re-matches #"[0-9a-fA-F]+" (subs x 2)))))

(defn- same-address? [a b]
  (and (string? a) (string? b) (= (str/lower-case a) (str/lower-case b))))

(defn- fail! [code details]
  (throw (ex-info "ERC-8004 record rejected"
                  (assoc details :identity.erc8004/problem code))))

(defn- coordinate! [{:keys [namespace chain-id identity-registry
                             reputation-registry validation-registry]
                     :as coordinate}]
  (when-not (= "eip155" namespace)
    (fail! :coordinate/namespace {:coordinate coordinate}))
  (when-not (pos-int? chain-id)
    (fail! :coordinate/chain-id {:coordinate coordinate}))
  (doseq [[kind address] (cond-> [[:identity identity-registry]
                                  [:reputation reputation-registry]]
                           (some? validation-registry)
                           (conj [:validation validation-registry]))]
    (when-not (hex-address? address)
      (fail! :coordinate/registry-address {:registry kind :address address})))
  coordinate)

(defn- service? [{:keys [name endpoint]}]
  (and (string? name) (not-empty name)
       (string? endpoint) (not-empty endpoint)))

(defn- registration-entry? [coordinate agent-id entry]
  (and (= agent-id (:agent-id entry))
       (= (str/lower-case
           (str "eip155:" (:chain-id coordinate) ":" (:identity-registry coordinate)))
          (some-> (:agent-registry entry) str/lower-case))))

(defn- registration! [coordinate agent-id registration]
  (when-not (map? registration)
    (fail! :registration/not-found {:agent-id agent-id}))
  (when-not (= registration-type (:type registration))
    (fail! :registration/type {:type (:type registration)}))
  (doseq [field [:name :description :image]]
    (when-not (and (string? (get registration field)) (not-empty (get registration field)))
      (fail! :registration/required-field {:field field})))
  (when-not (and (vector? (:services registration))
                 (every? service? (:services registration)))
    (fail! :registration/services {:services (:services registration)}))
  (when-not (true? (:active registration))
    (fail! :registration/inactive {:agent-id agent-id}))
  (when-not (contains? #{true false} (:x402-support registration))
    (fail! :registration/x402-support {:value (:x402-support registration)}))
  (when-not (and (vector? (:registrations registration))
                 (some #(registration-entry? coordinate agent-id %)
                       (:registrations registration)))
    (fail! :registration/registry-binding {:agent-id agent-id
                                            :registrations (:registrations registration)}))
  registration)

(defn- policy! [{:keys [policy-cid issued-at reputation validation] :as policy}]
  (when-not (and (string? policy-cid) (not-empty policy-cid))
    (fail! :policy/policy-cid {:policy policy}))
  (when-not (and (string? issued-at) (not-empty issued-at))
    (fail! :policy/issued-at {:policy policy}))
  (when (and reputation (not (seq (:allowed-clients reputation))))
    (fail! :policy/reputation-client-allowlist {:policy reputation}))
  (when (and validation (not (seq (:allowed-validators validation))))
    (fail! :policy/validation-validator-allowlist {:policy validation}))
  policy)

(defn- summary-refusal [kind summary {:keys [minimum-count minimum-score]}]
  (cond
    (not (and (map? summary)
              (integer? (:count summary))
              (not (neg? (:count summary)))
              (number? (:score summary))))
    {:identity.refusal/code (keyword (name kind) "unavailable")}

    (< (:count summary 0) (or minimum-count 1))
    {:identity.refusal/code (keyword (name kind) "insufficient-count")
     :identity.refusal/actual (:count summary 0)
     :identity.refusal/required (or minimum-count 1)}

    (< (:score summary 0) (or minimum-score 0))
    {:identity.refusal/code (keyword (name kind) "below-threshold")
     :identity.refusal/actual (:score summary 0)
     :identity.refusal/required (or minimum-score 0)}))

(defn verify!
  "Verify an agent registration and evaluate explicitly allowlisted reputation
  and validation summaries. Below-threshold summaries return refusals; invalid
  provenance and unsafe policy configuration throw."
  [reader coordinate agent-id policy]
  (coordinate! coordinate)
  (policy! policy)
  (when-not (and (integer? agent-id) (not (neg? agent-id)))
    (fail! :agent/id {:agent-id agent-id}))
  (let [{:keys [reputation-identity-registry validation-identity-registry] :as bindings}
        (read-registry-bindings! reader coordinate)]
    (when-not (same-address? (:identity-registry coordinate)
                             reputation-identity-registry)
      (fail! :registry/reputation-identity-binding {:bindings bindings}))
    (when (:validation policy)
      (when-not (and (:validation-registry coordinate)
                     (same-address? (:identity-registry coordinate)
                                    validation-identity-registry))
        (fail! :registry/validation-identity-binding {:bindings bindings}))))
  (let [{:keys [owner agent-uri registration agent-wallet wallet-verified?] :as chain-agent}
        (read-agent! reader coordinate agent-id)]
    (when-not (map? chain-agent)
      (fail! :agent/not-found {:agent-id agent-id}))
    (when-not (hex-address? owner)
      (fail! :agent/owner {:owner owner}))
    (when-not (and (string? agent-uri) (not-empty agent-uri))
      (fail! :agent/uri {:agent-uri agent-uri}))
    (registration! coordinate agent-id registration)
    (when (and agent-wallet (not (and (hex-address? agent-wallet) wallet-verified?)))
      (fail! :agent/wallet-not-verified {:agent-wallet agent-wallet}))
    (let [subject-id (str "erc8004:" (:chain-id coordinate) ":"
                          (:identity-registry coordinate) ":" agent-id)
          registration-id (str subject-id ":registration")
          reputation-policy (:reputation policy)
          validation-policy (:validation policy)
          reputation-summary (when reputation-policy
                               (read-reputation! reader coordinate agent-id
                                                 (:allowed-clients reputation-policy)))
          validation-summary (when validation-policy
                               (read-validations! reader coordinate agent-id
                                                  (:allowed-validators validation-policy)))
          reputation-refusal (when reputation-policy
                               (summary-refusal :reputation reputation-summary reputation-policy))
          validation-refusal (when validation-policy
                               (summary-refusal :validation validation-summary validation-policy))
          registration-evidence
          (model/evidence-ref
           registration-id :agent-registration
           {:ref {:specification specification
                  :chain-id (:chain-id coordinate)
                  :registry (:identity-registry coordinate)
                  :agent-id agent-id
                  :agent-uri agent-uri}
            :source owner
            :observed-at (:observed-at policy)
            :claims {:supported-trust (:supported-trust registration)
                     :services (mapv #(select-keys % [:name :endpoint])
                                     (:services registration))}
            :non-adjudicating true})
          reputation-id (str subject-id ":reputation")
          validation-id (str subject-id ":validation")
          evidence (cond-> [registration-evidence]
                     reputation-policy
                     (conj (model/evidence-ref
                            reputation-id :agent-feedback
                            {:ref {:registry (:reputation-registry coordinate)
                                   :agent-id agent-id
                                   :clients (vec (sort (:allowed-clients reputation-policy)))}
                             :source (:reputation-registry coordinate)
                             :observed-at (:observed-at policy)
                             :claims reputation-summary
                             :non-adjudicating true}))
                     validation-policy
                     (conj (model/evidence-ref
                            validation-id :agent-validation
                            {:ref {:registry (:validation-registry coordinate)
                                   :agent-id agent-id
                                   :validators (vec (sort (:allowed-validators validation-policy)))}
                             :source (:validation-registry coordinate)
                             :observed-at (:observed-at policy)
                             :claims validation-summary
                             :non-adjudicating true})))
          claims (cond-> []
                   (and reputation-policy (nil? reputation-refusal))
                   (conj (causal/trust-claim
                          (str reputation-id ":claim") subject-id :erc8004/reputation-qualified
                          {:scope [:agent :reputation]
                           :issuer (:reputation-registry coordinate)
                           :evaluator {:evaluator/id "erc8004-reputation-registry"
                                       :evaluator/kind :institution}
                           :evidence [reputation-id]
                           :policy-cid (:policy-cid policy)
                           :confidence 1.0
                           :issued-at (:issued-at policy)}))
                   (and validation-policy (nil? validation-refusal))
                   (conj (causal/trust-claim
                          (str validation-id ":claim") subject-id :erc8004/validation-qualified
                          {:scope [:agent :validation]
                           :issuer (:validation-registry coordinate)
                           :evaluator {:evaluator/id "erc8004-validation-registry"
                                       :evaluator/kind :institution}
                           :evidence [validation-id]
                           :policy-cid (:policy-cid policy)
                           :confidence 1.0
                           :issued-at (:issued-at policy)})))
          refusals (vec (remove nil? [reputation-refusal validation-refusal]))]
      {:identity/subject
       (model/subject subject-id :agent
                      {:source :erc8004
                       :aliases (vec (remove nil? [owner agent-wallet agent-uri]))})
       :identity/evidence evidence
       :identity/attestations
       [(model/attestation
         (str registration-id ":attestation") subject-id :erc8004/registered
         {:issuer (:identity-registry coordinate)
          :evidence [registration-id]
          :issued-at (:issued-at policy)
          :non-adjudicating true})]
       :identity/trust-claims claims
       :identity/refusals refusals
       :identity.erc8004/registration registration
       :identity.erc8004/owner owner
       :identity.erc8004/agent-wallet agent-wallet})))
