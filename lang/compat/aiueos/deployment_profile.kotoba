(ns aiueos.deployment-profile
  "Fail-closed deployment-profile admission for aiueos boot.

  Profile evidence is deliberately explicit and machine-checkable.  It is an
  admission prerequisite, not proof that the named controls are effective."
  (:require [clojure.string :as str]))

(def profile-version 1)

(def ^:private research-controls
  [:deny-by-default?
   :bounded-wasm?
   :append-only-audit?
   :non-claims-declared?])

(def ^:private sensitive-controls
  (into research-controls
        [:single-tenant?
         :encrypted-audit?
         :encrypted-data?
         :secure-entropy?
         :privileged-grants-reviewed?]))

(def ^:private regulated-controls
  (into sensitive-controls
        [:key-lifecycle?
         :signer-expiry-revocation?
         :package-verification?
         :monitoring?
         :incident-exercise?]))

(defn- non-blank-string? [value]
  (and (string? value) (not (str/blank? value))))

(defn- positive-number? [value]
  (and (number? value) (pos? value)))

(def ^:private side-channel-classes
  #{:timing :cache :spectre})

(def ^:private side-channel-mitigations
  #{:single-tenant :smt-disabled :core-isolation :speculation-controls
    :constant-time-crypto :secret-zeroization :no-secret-dependent-branches})

(defn- sha256-ref? [value]
  (boolean
   (and (string? value)
        (re-matches #"sha256:[0-9a-f]{64}" value))))

(defn- side-channel-violations [profile evidence]
  (let [decision (:side-channel-decision evidence)
        mitigations (:mitigations decision)
        required (if (= profile :regulated)
                   #{:single-tenant :smt-disabled :core-isolation
                     :speculation-controls :constant-time-crypto}
                   #{:single-tenant :constant-time-crypto})]
    (cond-> []
      (not= 1 (:version decision))
      (conj :side-channel-decision/version)
      (not (sha256-ref? (:threat-model-digest decision)))
      (conj :side-channel-decision/threat-model-digest)
      (not= side-channel-classes (:classes decision))
      (conj :side-channel-decision/classes)
      (or (not (set? mitigations))
          (not (every? side-channel-mitigations mitigations))
          (not (every? mitigations required)))
      (conj :side-channel-decision/mitigations)
      (not= :accepted (:residual-risk decision))
      (conj :side-channel-decision/residual-risk)
      (not (non-blank-string? (:approved-by decision)))
      (conj :side-channel-decision/approved-by)
      (not (non-blank-string? (:approved-at decision)))
      (conj :side-channel-decision/approved-at))))

(defn- production-audit-violations [evidence]
  (cond-> []
    (not= :sealed-aes-256-gcm (:audit-store evidence))
    (conj :audit-store)
    (not= 1 (:audit-format/version evidence))
    (conj :audit-format/version)
    (not (keyword? (:audit-key-id evidence)))
    (conj :audit-key-id)
    (not (positive-number? (:audit-retention-secs evidence)))
    (conj :audit-retention-secs)
    (not (non-blank-string? (:audit-restore-tested-at evidence)))
    (conj :audit-restore-tested-at)
    (not (true? (:audit-signed-chain-head? evidence)))
    (conj :audit-signed-chain-head?)
    (not= :external-durable (:audit-chain-head-storage evidence))
    (conj :audit-chain-head-storage)
    (not (sha256-ref? (:audit-chain-head-key-fingerprint evidence)))
    (conj :audit-chain-head-key-fingerprint)
    (not (non-blank-string? (:audit-truncation-tested-at evidence)))
    (conj :audit-truncation-tested-at)))

(defn- sealed-state-violations [evidence]
  (cond-> []
    (not= :sealed-aes-256-gcm (:component-state-store evidence))
    (conj :component-state-store)
    (not= 1 (:component-state-format/version evidence))
    (conj :component-state-format/version)
    (not= :external-per-component (:component-state-key-separation evidence))
    (conj :component-state-key-separation)
    (not (true? (:component-state-monotonic-version? evidence)))
    (conj :component-state-monotonic-version?)
    (not (positive-number? (:component-state-max-bytes evidence)))
    (conj :component-state-max-bytes)
    (not (non-blank-string? (:component-state-restore-tested-at evidence)))
    (conj :component-state-restore-tested-at)
    (not (non-blank-string? (:component-state-deletion-tested-at evidence)))
    (conj :component-state-deletion-tested-at)))

(defn- watchdog-violations [evidence]
  (cond-> []
    (not (true? (:hard-watchdog? evidence)))
    (conj :hard-watchdog?)
    (not= :chicory-thread-interrupt (:watchdog-engine evidence))
    (conj :watchdog-engine)
    (not (and (positive-number? (:watchdog-max-deadline-ms evidence))
              (<= (:watchdog-max-deadline-ms evidence) 30000)))
    (conj :watchdog-max-deadline-ms)
    (not (and (positive-number? (:watchdog-termination-grace-ms evidence))
              (<= (:watchdog-termination-grace-ms evidence) 5000)))
    (conj :watchdog-termination-grace-ms)
    (not (non-blank-string? (:watchdog-overrun-tested-at evidence)))
    (conj :watchdog-overrun-tested-at)))

(defn- network-topic-violations [evidence]
  (cond-> []
    (not (true? (:network-topic-authenticated? evidence)))
    (conj :network-topic-authenticated?)
    (not= 1 (:network-topic-protocol-version evidence))
    (conj :network-topic-protocol-version)
    (not (and (positive-number? (:network-topic-max-wire-bytes evidence))
              (<= (:network-topic-max-wire-bytes evidence) 65536)))
    (conj :network-topic-max-wire-bytes)
    (not= :sealed-durable (:network-topic-replay-state evidence))
    (conj :network-topic-replay-state)
    (not (non-blank-string? (:network-topic-partition-tested-at evidence)))
    (conj :network-topic-partition-tested-at)))

(defn- entropy-violations [evidence]
  (cond-> []
    (not= :os-strong (:entropy-source evidence))
    (conj :entropy-source)
    (not (non-blank-string? (:entropy-algorithm evidence)))
    (conj :entropy-algorithm)
    (not (non-blank-string? (:entropy-provider evidence)))
    (conj :entropy-provider)
    (not= #{:continuous-duplicate
            :repetition-count
            :adaptive-proportion}
          (:entropy-health-tests evidence))
    (conj :entropy-health-tests)
    (not= 4096 (:entropy-max-request-bytes evidence))
    (conj :entropy-max-request-bytes)
    (not (non-blank-string? (:entropy-provider-attested-at evidence)))
    (conj :entropy-provider-attested-at)))

(defn- key-lifecycle-violations [evidence]
  (cond-> []
    (not= 1 (:key-lifecycle-version evidence))
    (conj :key-lifecycle-version)
    (not (keyword? (:key-root-id evidence)))
    (conj :key-root-id)
    (not (sha256-ref? (:key-root-public-key-fingerprint evidence)))
    (conj :key-root-public-key-fingerprint)
    (not (positive-number? (:key-epoch evidence)))
    (conj :key-epoch)
    (not (sha256-ref? (:key-epoch-digest evidence)))
    (conj :key-epoch-digest)
    (not= :sealed-monotonic (:key-checkpoint-storage evidence))
    (conj :key-checkpoint-storage)
    (not (non-blank-string? (:key-convergence-tested-at evidence)))
    (conj :key-convergence-tested-at)
    (not (non-blank-string? (:key-compromise-recovery-tested-at evidence)))
    (conj :key-compromise-recovery-tested-at)))

(defn profile-violations
  "Return stable violation keywords for a boot config.

  An omitted profile is the backwards-compatible `:research` default.  An
  explicitly named profile must carry evidence version 1.  Production
  profiles (`:sensitive-local` and `:regulated`) additionally require every
  control listed in `docs/deployment-profiles.md`.  `:high-assurance` is
  intentionally blocked."
  [boot-config]
  (let [explicit? (contains? boot-config :aiueos/deployment-profile)
        profile (get boot-config :aiueos/deployment-profile :research)
        evidence (:aiueos/profile-evidence boot-config)
        missing (fn [controls]
                  (vec (keep #(when-not (true? (get evidence %)) %) controls)))]
    (cond
      (not (contains? #{:research :sensitive-local :regulated :high-assurance}
                      profile))
      [:unknown-deployment-profile]

      (= profile :high-assurance)
      [:high-assurance-profile-blocked]

      (and explicit? (not= profile-version (:profile/version evidence)))
      [:unsupported-profile-evidence-version]

      (= profile :research)
      (if explicit? (missing research-controls) [])

      (= profile :sensitive-local)
      (into (into (into (into (into (into
                                     (missing sensitive-controls)
                                     (production-audit-violations evidence))
                                    (sealed-state-violations evidence))
                              (watchdog-violations evidence))
                        (network-topic-violations evidence))
                  (entropy-violations evidence))
            (side-channel-violations profile evidence))

      (= profile :regulated)
      (cond-> (into (into (into (into (into (into (into
                                                   (missing regulated-controls)
                                                   (production-audit-violations evidence))
                                                  (sealed-state-violations evidence))
                                            (watchdog-violations evidence))
                                      (network-topic-violations evidence))
                                (entropy-violations evidence))
                          (key-lifecycle-violations evidence))
                    (side-channel-violations profile evidence))
        (not (non-blank-string? (:sbom-digest evidence)))
        (conj :sbom-digest)
        (not (non-blank-string? (:provenance-digest evidence)))
        (conj :provenance-digest)
        (not (sha256-ref? (:tcb-inventory-digest evidence)))
        (conj :tcb-inventory-digest)
        (not (true? (:tcb-drift-check? evidence)))
        (conj :tcb-drift-check?)
        ;; `docs/deployment-profiles.md` has required a "reproducible, signed,
        ;; independently verified release pipeline" for this profile since it
        ;; was written. The signed half was checked here; the reproducible half
        ;; was not checked anywhere, and the evaluator for it
        ;; (`kotoba.security.supply-chain/evaluate-reproducibility`) had no
        ;; caller. `aiueos.reproducibility` supplies these three keys.
        (not (true? (:reproducibility-qualified? evidence)))
        (conj :reproducibility-qualified?)
        (not (sha256-ref? (:reproducibility-artifact-digest evidence)))
        (conj :reproducibility-artifact-digest)
        ;; The artifact reproduced twice must be the artifact that was
        ;; attested. Without this, a qualified reproduction of *something* would
        ;; satisfy a release of something else.
        (not= (:artifact-digest evidence) (:reproducibility-artifact-digest evidence))
        (conj :reproducibility-artifact-binding)
        (and (:fips-claim? evidence)
             (not (non-blank-string? (:fips-module-certificate evidence))))
        (conj :fips-module-certificate)
        (and (:fips-claim? evidence)
             (not (non-blank-string? (:fips-boundary evidence))))
        (conj :fips-boundary)
        (and (:fips-claim? evidence)
             (not (true? (:entropy-fips-validated? evidence))))
        (conj :entropy-fips-validated?)))))

(defn enforce!
  "Return boot-config with its resolved profile, or reject before boot."
  [boot-config]
  (let [resolved (update boot-config :aiueos/deployment-profile #(or % :research))
        violations (profile-violations boot-config)]
    (when (seq violations)
      (throw (ex-info "aiueos deployment profile admission failed"
                      {:type :deployment-profile-admission-failed
                       :profile (:aiueos/deployment-profile resolved)
                       :profile/version profile-version
                       :violations violations})))
    resolved))
