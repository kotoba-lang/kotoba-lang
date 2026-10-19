(ns identity.live.human-passport
  "Read-only Optimism proof for the official Human Passport EAS schema."
  (:require [identity.adapters.eas :as eas]
            [identity.adapters.evm :as evm]
            [identity.adapters.human-passport :as passport]
            [identity.trust-profile :as trust-profile]))

(def optimism
  {:namespace (:namespace trust-profile/human-passport-coordinate)
   :chain-id (:chainId trust-profile/human-passport-coordinate)
   :eas-address (:easAddress trust-profile/human-passport-coordinate)
   :schema-registry-address (:schemaRegistryAddress trust-profile/human-passport-coordinate)})

(def official-schema trust-profile/human-passport-schema-uid)
(def official-attester trust-profile/human-passport-attester)
(def sample-attestation
  "0xb6612e9191aaf5741420f4933a509c60f558b6fd2ee769befe3cc07805690a68")

(defn -main [& _]
  (let [reader (evm/eas-reader {:rpc-url "https://mainnet.optimism.io"})
        decoder (evm/human-passport-decoder)
        record (eas/read-attestation! reader optimism sample-attestation)
        schema (eas/read-schema! reader optimism (:schema-uid record))
        decoded (passport/decode-score! decoder (:schema schema) (:data record))
        now (quot (System/currentTimeMillis) 1000)
        lifecycle
        (try
          (passport/verify!
           reader decoder optimism sample-attestation
           {:eas {:allowed-schema-uids #{official-schema}
                  :allowed-attesters #{official-attester}
                  :now now}
            :scorer-id 335
            :minimum-score 200000
            :policy-cid "urn:kotoba:policy:human-passport-live-proof:v1"
            :issued-at (str now)
            :subject-id "urn:kotoba:live-proof:human-passport"})
          :accepted
          (catch clojure.lang.ExceptionInfo e
            (or (:identity.eas/problem (ex-data e))
                (:identity.human-passport/problem (ex-data e)))))]
    (when-not (= :attestation/expired lifecycle)
      (throw (ex-info "Official historical sample did not fail closed as expired"
                      {:lifecycle lifecycle})))
    (prn {:network "eip155:10"
          :attestation (:uid record)
          :schema (:uid schema)
          :schema-current? (= passport/score-schema (:schema schema))
          :attester (:attester record)
          :scorer-id (:scorer-id decoded)
          :score (:score decoded)
          :threshold (:threshold decoded)
          :stamp-count (count (:stamps decoded))
          :lifecycle lifecycle
          :write-performed? false})))
