(ns identity.trust-policy
  "Governed, service-specific use of external trust evidence.

  Adapter support is not authorization.  This namespace is the small policy
  bridge between verified evidence and one named service action.  Missing
  service/action policy always means deny; no source produces ambient trust."
  (:require [clojure.string :as str]))

(def policy-version 1)

(def itonami-human-passport-policy
  {:id "urn:sha256:528431823be6f06bb184dcaaa712ab642c35e92795cd55a3f6859911aadbf877"
   :version policy-version
   :service-origin "https://itonami.cloud"
   :action "identity.sybil-step-up"
   :source :human-passport
   :status :active
   :subject-binding :verified-principal-account-recipient
   :scorer-id 335
   :minimum-score 200000
   :maximum-age-seconds (* 90 24 60 60)
   :issued-at "2026-08-28T00:00:00Z"
   :verification-endpoint "/api/v1/trust/human-passport/verify"
   :effect :evidence-only
   :grants-capability? false})

(def kotobase-eas-policy
  {:id "urn:sha256:69f422026ab1efb38c7848a1e1bc5a0b2c52a4de6ecd774eef7b96cc0af6a6c1"
   :version policy-version
   :service-origin "https://kotobase.net"
   :action "evidence.ingest"
   :source :ethereum-attestation-service
   :status :active
   :subject-binding :authenticated-tenant-evidence-record
   :chain-id 10
   :eas-address "0x4200000000000000000000000000000000000021"
   :schema-uid "0xda0257756063c891659fed52fd36ef7557f7b45d66f59645fd3c3b263b747254"
   :allowed-attesters ["0x843829986e895facd330486a61Ebee9E1f1adB1a"]
   :maximum-age-seconds (* 90 24 60 60)
   :issued-at "2026-08-28T00:00:00Z"
   :verification-endpoint "/xrpc/ai.gftd.apps.kotobase.evidence.ingest"
   :idempotency :sha256-keyed-strong-transaction
   :effect :evidence-only
   :grants-capability? false})

(def murakumo-erc8004-policy
  {:id "urn:sha256:16764a912de06ccb974ba9870c8c2dcd24c1bdd40cf2ccd1bb1dd079f02e0953"
   :version policy-version
   :service-origin "https://murakumo.cloud"
   :action "agent.execute"
   :source :erc8004
   :status :active
   :subject-binding :access-token-subject-equals-verified-agent-wallet
   :namespace "eip155"
   :chain-id 8453
   :identity-registry "0x8004A169FB4a3325136EB29fA0ceB6D2e539a432"
   :reputation-registry "0x8004BAa17C55a88189AE136b182e5fdA19dE9b63"
   :validation-registry nil
   :validation-status :deferred-upstream-no-official-deployment
   :validation-reference "https://github.com/erc-8004/erc-8004-contracts#mainnet"
   :validation-activation-requirements
   ["official Base mainnet deployment coordinate"
    "pinned runtime code hash and Identity Registry binding"
    "validator allowlist, response policy, and expiry semantics"
    "positive, expired, revoked, and unavailable evidence proofs"]
   :allowed-clients ["0xA00366234D29d4F882088048c0B2fa0dB7302D4E"]
   :minimum-feedback-count 1
   :minimum-reputation-score 1
   :issued-at "2026-08-28T00:00:00Z"
   :verification-endpoint "/api/v1/agent/execute"
   :effect :execution-admission-evidence
   :grants-capability? false})

(defn- public-human-passport-policy [policy]
  {:id (:id policy)
   :version (:version policy)
   :serviceOrigin (:service-origin policy)
   :action (:action policy)
   :source "human-passport"
   :status (name (:status policy))
   :subjectBinding (name (:subject-binding policy))
   :scorerId (:scorer-id policy)
   :minimumScore (:minimum-score policy)
   :maximumAgeSeconds (:maximum-age-seconds policy)
   :issuedAt (:issued-at policy)
   :verificationEndpoint (:verification-endpoint policy)
   :effect (name (:effect policy))
   :grantsCapability false})

(defn- public-eas-policy [policy]
  {:id (:id policy)
   :version (:version policy)
   :serviceOrigin (:service-origin policy)
   :action (:action policy)
   :source "ethereum-attestation-service"
   :status (name (:status policy))
   :subjectBinding (name (:subject-binding policy))
   :chainId (:chain-id policy)
   :easAddress (:eas-address policy)
   :schemaUid (:schema-uid policy)
   :allowedAttesters (:allowed-attesters policy)
   :maximumAgeSeconds (:maximum-age-seconds policy)
   :issuedAt (:issued-at policy)
   :verificationEndpoint (:verification-endpoint policy)
   :idempotency (name (:idempotency policy))
   :effect (name (:effect policy))
   :grantsCapability false})

(defn- public-erc8004-policy [policy]
  {:id (:id policy)
   :version (:version policy)
   :serviceOrigin (:service-origin policy)
   :action (:action policy)
   :source "erc8004"
   :status (name (:status policy))
   :subjectBinding (name (:subject-binding policy))
   :namespace (:namespace policy)
   :chainId (:chain-id policy)
   :identityRegistry (:identity-registry policy)
   :reputationRegistry (:reputation-registry policy)
   :validationRegistry (:validation-registry policy)
   :validationStatus (name (:validation-status policy))
   :validationReference (:validation-reference policy)
   :validationActivationRequirements (:validation-activation-requirements policy)
   :allowedClients (:allowed-clients policy)
   :minimumFeedbackCount (:minimum-feedback-count policy)
   :minimumReputationScore (:minimum-reputation-score policy)
   :issuedAt (:issued-at policy)
   :verificationEndpoint (:verification-endpoint policy)
   :additionalRequirements ["valid murakumo generation capability"
                            "billing admission"
                            "configured generation upstream"]
   :effect (name (:effect policy))
   :grantsCapability false})

(def service-policies
  {"human-organization-operator"
   {:humanPassport (public-human-passport-policy itonami-human-passport-policy)
    :ethereumAttestationService {:status "active-via-human-passport"
                                 :acceptedPurpose ["identity" "sybil-resistance"]}
    :erc8004 {:status "unsupported-for-human-authorization"}}

   "evidence-authority"
   {:humanPassport {:status "not-enforced"}
    :ethereumAttestationService (public-eas-policy kotobase-eas-policy)
    :erc8004 {:status "unbound"}}

   "agent-execution"
   {:humanPassport {:status "not-enforced-for-agent-execution"}
    :ethereumAttestationService {:status "adapter-available-not-enforced"}
    :erc8004 (public-erc8004-policy murakumo-erc8004-policy)}})

(defn service-policy [role]
  (get service-policies role
       {:humanPassport {:status "not-enforced"}
        :ethereumAttestationService {:status "not-enforced"}
        :erc8004 {:status "unbound"}}))

(defn human-passport-policy
  "Return the active policy only for the exact service/action pair."
  [service-origin action]
  (let [policy itonami-human-passport-policy]
    (when (and (= service-origin (:service-origin policy))
               (= action (:action policy))
               (= :active (:status policy)))
      policy)))

(defn authorize-human-passport
  "Convert an already verified Human Passport result into a bounded decision.

  This does not verify EAS and never grants a capability.  It proves that the
  verified recipient is the service's verified Principal account and records
  the exact policy/evidence identifiers used for the step-up evidence."
  [policy expected-recipient verified]
  (let [actual (some-> verified :identity.eas/attestation :recipient str/lower-case)
        expected (some-> expected-recipient str str/lower-case)
        claim (first (:identity/trust-claims verified))]
    (cond
      (nil? policy)
      {:allowed? false :reason :policy/not-found}

      (or (str/blank? expected) (not= expected actual))
      {:allowed? false :reason :subject/recipient-mismatch}

      (nil? claim)
      {:allowed? false :reason :evidence/trust-claim-missing}

      :else
      {:allowed? true
       :reason :evidence/verified
       :action (:action policy)
       :effect (:effect policy)
       :grants-capability? false
       :policy-id (:id policy)
       :subject-recipient expected
       :claim-id (:identity.causal/id claim)
       :valid-until (:identity.causal/valid-until claim)})))
