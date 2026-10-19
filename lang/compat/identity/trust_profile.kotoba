(ns identity.trust-profile
  "Machine-readable discovery for Kotoba's external trust evidence boundary.

  This is intentionally a description of accepted evidence types, not a
  universal score and not proof that a particular subject is trustworthy."
  (:require [identity.trust-policy :as trust-policy]))

(def schema "https://kotoba-lang.org/schemas/trust-profile/v1")

(def human-passport-coordinate
  {:namespace "eip155"
   :chainId 10
   :network "optimism"
   :easAddress "0x4200000000000000000000000000000000000021"
   :schemaRegistryAddress "0x4200000000000000000000000000000000000020"})

(def human-passport-schema-uid
  "0xda0257756063c891659fed52fd36ef7557f7b45d66f59645fd3c3b263b747254")

(def human-passport-attester
  "0x843829986e895facd330486a61Ebee9E1f1adB1a")

(defn profile
  "Build the public contract for one service.

  `service` must name its HTTPS origin, authority DID, role, and the local
  identity endpoint that contextualizes this profile. The external sources
  and refusal semantics remain identical across every service."
  [{:keys [origin authorityDid role identityEndpoint]}]
  (let [service-policy (trust-policy/service-policy role)
        erc-policy (:erc8004 service-policy)
        erc-bound? (= "active" (:status erc-policy))]
   {:schema schema
   :version 1
   :service {:origin origin
             :authorityDid authorityDid
             :role role
             :identityEndpoint identityEndpoint}
   :semantics {:decisionModel "scoped-evidence"
               :universalTrustScore false
               :nonAdjudicating true
               :failClosed true}
   :sources
   {:humanPassport
    {:specification "https://docs.passport.human.tech/building-with-passport/stamps/introduction"
     :purpose "identity/sybil-resistance"
     :coordinate human-passport-coordinate
     :schemaUid human-passport-schema-uid
     :allowedAttesters [human-passport-attester]
     :maximumAgeSeconds 7776000
     :minimumScore "deployment-policy"
     :status "supported"}
    :ethereumAttestationService
    {:specification "https://docs.attest.org/docs/core--concepts/attestations"
     :purpose "provenance-and-lifecycle-verification"
     :requirements ["schema-allowlist" "attester-allowlist" "not-revoked"
                    "not-expired" "chain-coordinate"]
     :status "supported"}
    :erc8004
    (cond->
     {:specification "https://eips.ethereum.org/EIPS/eip-8004"
      :purpose ["agent/registration" "agent/reputation" "agent/validation"]
      :implementation "three-registry-draft-adapter"
      :registryBinding nil
      :status "supported-unbound"
      :refusalReason "canonical-registry-coordinate-not-governed"}
      erc-bound?
      (assoc :purpose ["agent/registration" "agent/reputation"]
             :registryBinding {:namespace (:namespace erc-policy)
                               :chainId (:chainId erc-policy)
                               :identityRegistry (:identityRegistry erc-policy)
                               :reputationRegistry (:reputationRegistry erc-policy)
                               :validationRegistry (:validationRegistry erc-policy)}
             :status "supported-bound"
             :refusalReason nil))}
   :policy service-policy
   :claims {:humanPassportKyc false
            :humanPassportUniversalReputation false
            :erc8004LiveRegistry erc-bound?}}))
