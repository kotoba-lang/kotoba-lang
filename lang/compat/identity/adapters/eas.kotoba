(ns identity.adapters.eas
  "Fail-closed Ethereum Attestation Service (EAS) verification boundary.

  Chain access and ABI decoding belong to the host.  This namespace accepts
  decoded registry records, verifies their provenance and lifecycle, and
  emits portable identity evidence."
  (:require [clojure.string :as str]
            [identity.model :as model]))

(defprotocol IEASReader
  (read-schema! [reader coordinate schema-uid])
  (read-attestation! [reader coordinate attestation-uid]))

(defn static-reader
  "Contract-test reader. Production hosts implement IEASReader with pinned RPC
  and contract addresses."
  [{:keys [schemas attestations]}]
  (reify IEASReader
    (read-schema! [_ _ uid] (get schemas uid))
    (read-attestation! [_ _ uid] (get attestations uid))))

(defn- hex? [n x]
  (boolean (and (string? x)
                (= (+ 2 n) (count x))
                (str/starts-with? x "0x")
                (re-matches #"[0-9a-fA-F]+" (subs x 2)))))

(defn- fail! [code details]
  (throw (ex-info "EAS attestation rejected"
                  (assoc details :identity.eas/problem code))))

(defn- canonical-hex [x]
  (if (string? x) (str/lower-case x) x))

(defn- allowed-hex? [allowlist value]
  (contains? (set (map canonical-hex allowlist)) (canonical-hex value)))

(defn- coordinate! [{:keys [namespace chain-id eas-address schema-registry-address]
                     :as coordinate}]
  (when-not (= "eip155" namespace)
    (fail! :coordinate/namespace {:coordinate coordinate}))
  (when-not (pos-int? chain-id)
    (fail! :coordinate/chain-id {:coordinate coordinate}))
  (when-not (hex? 40 eas-address)
    (fail! :coordinate/eas-address {:coordinate coordinate}))
  (when-not (hex? 40 schema-registry-address)
    (fail! :coordinate/schema-registry-address {:coordinate coordinate}))
  coordinate)

(defn verify!
  "Read and verify one EAS attestation.

  Required policy keys are `:allowed-schema-uids`, `:allowed-attesters`, and
  integer `:now`.  Allowlists are mandatory: a valid chain record is not by
  itself a trusted record."
  [reader coordinate attestation-uid
   {:keys [allowed-schema-uids allowed-attesters now] :as policy}]
  (coordinate! coordinate)
  (when-not (hex? 64 attestation-uid)
    (fail! :attestation/uid {:uid attestation-uid}))
  (when-not (seq allowed-schema-uids)
    (fail! :policy/schema-allowlist {:policy policy}))
  (when-not (seq allowed-attesters)
    (fail! :policy/attester-allowlist {:policy policy}))
  (when-not (and (integer? now) (not (neg? now)))
    (fail! :policy/now {:policy policy}))
  (let [record (read-attestation! reader coordinate attestation-uid)]
    (when-not (map? record)
      (fail! :attestation/not-found {:uid attestation-uid}))
    (when-not (= (canonical-hex attestation-uid) (canonical-hex (:uid record)))
      (fail! :attestation/uid-mismatch {:requested attestation-uid
                                        :returned (:uid record)}))
    (when-not (allowed-hex? allowed-schema-uids (:schema-uid record))
      (fail! :attestation/schema-not-allowed {:schema-uid (:schema-uid record)}))
    (when-not (allowed-hex? allowed-attesters (:attester record))
      (fail! :attestation/attester-not-allowed {:attester (:attester record)}))
    (when-not (and (integer? (:time record)) (<= 0 (:time record) now))
      (fail! :attestation/time {:time (:time record) :now now}))
    (when-not (and (integer? (or (:revocation-time record) 0))
                   (not (neg? (or (:revocation-time record) 0))))
      (fail! :attestation/revocation-time {:value (:revocation-time record)}))
    (when-not (and (integer? (or (:expiration-time record) 0))
                   (not (neg? (or (:expiration-time record) 0))))
      (fail! :attestation/expiration-time {:value (:expiration-time record)}))
    (when (or (:revoked? record) (pos? (or (:revocation-time record) 0)))
      (fail! :attestation/revoked {:uid attestation-uid}))
    (when (and (pos? (or (:expiration-time record) 0))
               (> now (:expiration-time record)))
      (fail! :attestation/expired {:expiration-time (:expiration-time record)
                                   :now now}))
    (let [schema (read-schema! reader coordinate (:schema-uid record))]
      (when-not (map? schema)
        (fail! :schema/not-found {:schema-uid (:schema-uid record)}))
      (when-not (= (canonical-hex (:schema-uid record))
                   (canonical-hex (:uid schema)))
        (fail! :schema/uid-mismatch {:record (:schema-uid record)
                                     :schema (:uid schema)}))
      (when-not (contains? #{true false} (:revocable? schema))
        (fail! :schema/revocability-invalid {:schema-uid (:uid schema)}))
      (when (and (false? (:revocable? schema)) (:revocable? record))
        (fail! :schema/revocability-mismatch {:schema-uid (:uid schema)}))
      {:identity.eas/coordinate coordinate
       :identity.eas/schema schema
       :identity.eas/attestation record
       :identity.eas/evidence
       (model/evidence-ref
        (str "eas:" (:chain-id coordinate) ":" attestation-uid)
        :onchain-attestation
        {:ref {:namespace "eip155"
               :chain-id (:chain-id coordinate)
               :contract (:eas-address coordinate)
               :uid attestation-uid
               :schema-uid (:schema-uid record)
               :recipient (:recipient record)
               :reference-uid (:reference-uid record)}
         :source (:attester record)
         :observed-at now
         :claims {:schema (:schema schema)
                  :time (:time record)
                  :expiration-time (:expiration-time record)}
         :non-adjudicating true})})))
