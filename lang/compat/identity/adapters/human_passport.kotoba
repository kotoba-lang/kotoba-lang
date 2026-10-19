(ns identity.adapters.human-passport
  "Human Passport score attestation adapter.

  The Passport score is retained as provenance, never promoted to Kotoba's
  universal trust score.  Passing produces only a scoped Sybil-resistance
  claim and a short-lived Sekisho evidence item."
  (:require [identity.adapters.eas :as eas]
            [identity.causal :as causal]
            [identity.model :as model]))

(def score-schema
  "bool passing_score, uint8 score_decimals, uint128 scorer_id, uint32 score, uint32 threshold, tuple(string provider, uint256 score)[] stamps")

(def max-score-age-seconds (* 90 24 60 60))

(defprotocol IHumanPassportDecoder
  (decode-score! [decoder schema raw-data]))

(defn static-decoder [decoded]
  (reify IHumanPassportDecoder
    (decode-score! [_ _ _] decoded)))

(defn- fail! [code details]
  (throw (ex-info "Human Passport attestation rejected"
                  (assoc details :identity.human-passport/problem code))))

(defn- uint? [bits x]
  (and (integer? x)
       (not (neg? x))
       ;; JavaScript cannot exactly represent the full uint128 range. The host
       ;; decoder remains responsible for lossless ABI decoding of that field.
       (or (> bits 32) (<= x (dec (Math/pow 2 bits))))))

(defn- valid-stamp? [{:keys [provider score]}]
  (and (string? provider) (not-empty provider) (integer? score) (not (neg? score))))

(defn verify!
  "Verify and normalize the current Human Passport EAS score schema.

  The host supplies ABI decoding. Required policy keys: `:eas`, `:scorer-id`,
  `:minimum-score`, `:policy-cid`, `:issued-at`, and `:subject-id`."
  [reader decoder coordinate attestation-uid
   {:keys [eas scorer-id minimum-score policy-cid issued-at subject-id] :as policy}]
  (when-not (and (uint? 128 scorer-id) (uint? 32 minimum-score))
    (fail! :policy/score {:policy policy}))
  (when-not (and (string? policy-cid) (not-empty policy-cid))
    (fail! :policy/policy-cid {:policy policy}))
  (when-not (and (string? issued-at) (not-empty issued-at))
    (fail! :policy/issued-at {:policy policy}))
  (when-not (and (string? subject-id) (not-empty subject-id))
    (fail! :policy/subject-id {:policy policy}))
  (let [{:identity.eas/keys [schema attestation evidence] :as verified}
        (eas/verify! reader coordinate attestation-uid eas)
        decoded (decode-score! decoder (:schema schema) (:data attestation))
        {:keys [passing-score score-decimals scorer-id score threshold stamps]} decoded
        now (:now eas)]
    (when-not (= score-schema (:schema schema))
      (fail! :schema/not-current {:schema (:schema schema)}))
    (when-not (map? decoded)
      (fail! :data/not-decoded {}))
    (when-not (= 4 score-decimals)
      (fail! :data/score-decimals {:score-decimals score-decimals}))
    (when-not (contains? #{true false} passing-score)
      (fail! :data/passing-score {:passing-score passing-score}))
    (when-not (and (uint? 128 scorer-id) (uint? 32 score) (uint? 32 threshold))
      (fail! :data/score {:decoded decoded}))
    (when-not (and (vector? stamps) (every? valid-stamp? stamps))
      (fail! :data/stamps {:stamps stamps}))
    (when-not (= scorer-id (:scorer-id policy))
      (fail! :data/scorer-id {:expected (:scorer-id policy) :actual scorer-id}))
    (when-not (= (boolean passing-score) (>= score threshold))
      (fail! :data/inconsistent-passing-score
             {:passing-score passing-score :score score :threshold threshold}))
    (when (> (- now (:time attestation)) max-score-age-seconds)
      (fail! :attestation/stale {:time (:time attestation) :now now
                                 :max-age max-score-age-seconds}))
    (when (< score (max threshold minimum-score))
      (fail! :policy/below-threshold {:score score :attested-threshold threshold
                                      :minimum-score minimum-score}))
    (let [humanity-id (str "human-passport:" (:chain-id coordinate) ":" attestation-uid)
          humanity-evidence
          (model/evidence-ref
           humanity-id :humanity-proof
           {:ref {:attestation-uid attestation-uid
                  :schema-uid (:schema-uid attestation)
                  :scorer-id scorer-id}
            :source (:attester attestation)
            :observed-at now
            :claims {:passing-score true
                     :score score
                     :score-decimals score-decimals
                     :threshold threshold
                     :stamp-providers (mapv :provider stamps)}
            :non-adjudicating true})
          portable-attestation
          (model/attestation
           (str humanity-id ":unique-humanity") subject-id
           :human-passport/unique-humanity
           {:issuer (:attester attestation)
            :evidence [(:identity.evidence/id evidence) humanity-id]
            :issued-at issued-at
            :non-adjudicating true})
          trust-claim
          (causal/trust-claim
           (str humanity-id ":sybil-resistance") subject-id
           :human-passport/passed
           {:scope [:identity :sybil-resistance]
            :issuer (:attester attestation)
            :evaluator {:evaluator/id (str "human-passport-scorer:" scorer-id)
                        :evaluator/kind :institution}
            :evidence [(:identity.evidence/id evidence) humanity-id]
            :policy-cid policy-cid
            :confidence 1.0
            :issued-at issued-at
            :valid-until (str (+ (:time attestation) max-score-age-seconds))})]
      (assoc verified
             :identity/subject (model/subject subject-id :wallet
                                              {:source :human-passport
                                               :aliases [(:recipient attestation)]})
             :identity/evidence [evidence humanity-evidence]
             :identity/attestations [portable-attestation]
             :identity/trust-claims [trust-claim]
             :sekisho/evidence
             {:sekisho.assurance/evidence :evidence/humanity-verified
              :sekisho.assurance/at (:time attestation)
              :sekisho.assurance/strength :verified
              :sekisho.assurance/source humanity-id
              :sekisho.assurance/note "Human Passport threshold verified from an allowlisted EAS attestation"}))))
