(ns identity.causal
  "Causal identity records for accountable return without identity erasure.

  These records do not assert a permanent metaphysical person.  They address
  operational identity epochs and attributed claims.  An epoch may end and a
  new one may begin at earned trust zero, while the transition keeps causal
  lineage and every open obligation explicit.")

(def epoch-statuses #{:active :closed})
(def link-statuses #{:active :revoked :superseded})
(def claim-statuses #{:active :revoked :superseded})
(def obligation-statuses #{:open :fulfilled :released})
(def evaluator-kinds #{:llm :agent :institution :human :hybrid})

(defn- non-empty-string? [x]
  (and (string? x) (not-empty x)))

(defn- non-empty-coll? [x]
  (and (coll? x) (seq x)))

(defn- distinct-vector? [x]
  (and (vector? x) (= (count x) (count (distinct x)))))

(defn epoch
  "Create one operational identity epoch.  Trust is deliberately not an
  accumulated property of the record: every epoch starts at zero and earns
  scoped claims separately."
  [id principal opts]
  {:identity.epoch/id id
   :identity.epoch/principal principal
   :identity.epoch/previous (:previous opts)
   :identity.epoch/sequence (:sequence opts)
   :identity.epoch/started-at (:started-at opts)
   :identity.epoch/status (or (:status opts) :active)
   :identity.epoch/initial-trust 0
   :identity/non-adjudicating true})

(defn identity-link-claim
  "Create an attributed claim that two identifiers are causally linked.
  Alias equality alone is not evidence: issuer, evidence and proof are
  mandatory in a valid link claim."
  [id left right opts]
  {:identity.link/id id
   :identity.link/left left
   :identity.link/right right
   :identity.link/scope (:scope opts)
   :identity.link/issuer (:issuer opts)
   :identity.link/evidence (vec (:evidence opts))
   :identity.link/proof (:proof opts)
   :identity.link/issued-at (:issued-at opts)
   :identity.link/status (or (:status opts) :active)
   :identity.link/supersedes (:supersedes opts)
   :identity/non-adjudicating true})

(defn trust-claim
  "Create one scoped evaluator claim.  It is evidence for a later authority
  decision, never the decision itself.  LLM/agent evaluators must name the
  content-addressed model and policy that produced the claim."
  [id subject predicate opts]
  {:trust.claim/id id
   :trust.claim/subject subject
   :trust.claim/scope (vec (:scope opts))
   :trust.claim/predicate predicate
   :trust.claim/issuer (:issuer opts)
   :trust.claim/evaluator (:evaluator opts)
   :trust.claim/evidence (vec (:evidence opts))
   :trust.claim/policy-cid (:policy-cid opts)
   :trust.claim/confidence (:confidence opts)
   :trust.claim/issued-at (:issued-at opts)
   :trust.claim/valid-until (:valid-until opts)
   :trust.claim/status (or (:status opts) :active)
   :trust.claim/supersedes (:supersedes opts)
   :identity/non-adjudicating true})

(defn obligation
  "Create an obligation whose identity is stable across epoch transitions."
  [id origin-epoch kind opts]
  {:identity.obligation/id id
   :identity.obligation/origin-epoch origin-epoch
   :identity.obligation/current-epoch (or (:current-epoch opts) origin-epoch)
   :identity.obligation/kind kind
   :identity.obligation/beneficiary (:beneficiary opts)
   :identity.obligation/evidence (vec (:evidence opts))
   :identity.obligation/status (or (:status opts) :open)
   :identity.obligation/updated-at (:updated-at opts)
   :identity/non-adjudicating true})

(defn transition
  "Create an epoch transition.  OPEN-OBLIGATION-IDS must contain every open
  obligation selected at the immutable basis used by the caller.  Validation
  against that basis is performed by `validate-transition!`."
  [id from-epoch to-epoch opts]
  {:identity.transition/id id
   :identity.transition/from from-epoch
   :identity.transition/to to-epoch
   :identity.transition/commitment-cid (:commitment-cid opts)
   :identity.transition/open-obligations (vec (:open-obligations opts))
   :identity.transition/revoked-grants (vec (:revoked-grants opts))
   :identity.transition/witness-claims (vec (:witness-claims opts))
   :identity.transition/policy-cid (:policy-cid opts)
   :identity.transition/basis-cid (:basis-cid opts)
   :identity.transition/occurred-at (:occurred-at opts)
   :identity.transition/proof (:proof opts)
   :identity/non-adjudicating true})

(defn- evaluator-problems [evaluator]
  (cond-> []
    (not (map? evaluator))
    (conj :trust-claim/evaluator-invalid)

    (and (map? evaluator)
         (not (non-empty-string? (:evaluator/id evaluator))))
    (conj :trust-claim/evaluator-id)

    (and (map? evaluator)
         (not (contains? evaluator-kinds (:evaluator/kind evaluator))))
    (conj :trust-claim/evaluator-kind)

    (and (map? evaluator)
         (contains? #{:llm :agent :hybrid} (:evaluator/kind evaluator))
         (not (non-empty-string? (:evaluator/model-cid evaluator))))
    (conj :trust-claim/model-cid)))

(defn problems
  "Return closed validation problems for a causal identity record."
  [record]
  (cond
    (contains? record :identity.epoch/id)
    (cond-> []
      (not (non-empty-string? (:identity.epoch/id record))) (conj :epoch/id)
      (not (non-empty-string? (:identity.epoch/principal record))) (conj :epoch/principal)
      (not (and (integer? (:identity.epoch/sequence record))
                (not (neg? (:identity.epoch/sequence record))))) (conj :epoch/sequence)
      (and (pos-int? (:identity.epoch/sequence record))
           (not (non-empty-string? (:identity.epoch/previous record)))) (conj :epoch/previous)
      (and (= 0 (:identity.epoch/sequence record))
           (some? (:identity.epoch/previous record))) (conj :epoch/unexpected-previous)
      (not (non-empty-string? (:identity.epoch/started-at record))) (conj :epoch/started-at)
      (not (contains? epoch-statuses (:identity.epoch/status record))) (conj :epoch/status)
      (not= 0 (:identity.epoch/initial-trust record)) (conj :epoch/initial-trust)
      (not (true? (:identity/non-adjudicating record))) (conj :record/adjudicating))

    (contains? record :identity.link/id)
    (cond-> []
      (not (non-empty-string? (:identity.link/id record))) (conj :link/id)
      (not (non-empty-string? (:identity.link/left record))) (conj :link/left)
      (not (non-empty-string? (:identity.link/right record))) (conj :link/right)
      (= (:identity.link/left record) (:identity.link/right record)) (conj :link/self)
      (not (non-empty-coll? (:identity.link/scope record))) (conj :link/scope)
      (not (non-empty-string? (:identity.link/issuer record))) (conj :link/issuer)
      (not (non-empty-coll? (:identity.link/evidence record))) (conj :link/evidence)
      (not (non-empty-string? (:identity.link/proof record))) (conj :link/proof)
      (not (non-empty-string? (:identity.link/issued-at record))) (conj :link/issued-at)
      (not (contains? link-statuses (:identity.link/status record))) (conj :link/status)
      (not (true? (:identity/non-adjudicating record))) (conj :record/adjudicating))

    (contains? record :trust.claim/id)
    (into
     (cond-> []
       (not (non-empty-string? (:trust.claim/id record))) (conj :trust-claim/id)
       (not (non-empty-string? (:trust.claim/subject record))) (conj :trust-claim/subject)
       (not (non-empty-coll? (:trust.claim/scope record))) (conj :trust-claim/scope)
       (not (keyword? (:trust.claim/predicate record))) (conj :trust-claim/predicate)
       (not (non-empty-string? (:trust.claim/issuer record))) (conj :trust-claim/issuer)
       (not (non-empty-coll? (:trust.claim/evidence record))) (conj :trust-claim/evidence)
       (not (non-empty-string? (:trust.claim/policy-cid record))) (conj :trust-claim/policy-cid)
       (not (and (number? (:trust.claim/confidence record))
                 (<= 0 (:trust.claim/confidence record) 1))) (conj :trust-claim/confidence)
       (not (non-empty-string? (:trust.claim/issued-at record))) (conj :trust-claim/issued-at)
       (not (contains? claim-statuses (:trust.claim/status record))) (conj :trust-claim/status)
       (not (true? (:identity/non-adjudicating record))) (conj :record/adjudicating))
     (evaluator-problems (:trust.claim/evaluator record)))

    (contains? record :identity.obligation/id)
    (cond-> []
      (not (non-empty-string? (:identity.obligation/id record))) (conj :obligation/id)
      (not (non-empty-string? (:identity.obligation/origin-epoch record))) (conj :obligation/origin)
      (not (non-empty-string? (:identity.obligation/current-epoch record))) (conj :obligation/current)
      (not (keyword? (:identity.obligation/kind record))) (conj :obligation/kind)
      (not (contains? obligation-statuses (:identity.obligation/status record))) (conj :obligation/status)
      (not (true? (:identity/non-adjudicating record))) (conj :record/adjudicating))

    (contains? record :identity.transition/id)
    (cond-> []
      (not (non-empty-string? (:identity.transition/id record))) (conj :transition/id)
      (not (non-empty-string? (:identity.transition/from record))) (conj :transition/from)
      (not (non-empty-string? (:identity.transition/to record))) (conj :transition/to)
      (= (:identity.transition/from record) (:identity.transition/to record)) (conj :transition/self)
      (not (non-empty-string? (:identity.transition/commitment-cid record))) (conj :transition/commitment)
      (not (distinct-vector? (:identity.transition/open-obligations record))) (conj :transition/obligations)
      (not (distinct-vector? (:identity.transition/revoked-grants record))) (conj :transition/grants)
      (not (distinct-vector? (:identity.transition/witness-claims record))) (conj :transition/witnesses)
      (not (non-empty-string? (:identity.transition/policy-cid record))) (conj :transition/policy-cid)
      (not (non-empty-string? (:identity.transition/basis-cid record))) (conj :transition/basis-cid)
      (not (non-empty-string? (:identity.transition/occurred-at record))) (conj :transition/occurred-at)
      (not (non-empty-string? (:identity.transition/proof record))) (conj :transition/proof)
      (not (true? (:identity/non-adjudicating record))) (conj :record/adjudicating))

    :else [:record/unknown]))

(defn valid? [record]
  (empty? (problems record)))

(defn valid! [record]
  (when-let [ps (seq (problems record))]
    (throw (ex-info "invalid causal identity record"
                    {:identity.causal/problems ps})))
  record)

(defn validate-transition!
  "Validate a transition against its immutable basis and the new epoch.

  `open-obligation-ids` and `active-grant-ids` are selected by the caller at
  `:identity.transition/basis-cid`.  Equality is intentional: omitting one
  obligation or leaving one old grant live fails closed."
  [transition-record new-epoch {:keys [basis-cid open-obligation-ids active-grant-ids]}]
  (valid! transition-record)
  (valid! new-epoch)
  (let [problems (cond-> []
                   (not= basis-cid (:identity.transition/basis-cid transition-record))
                   (conj :transition/basis-mismatch)

                   (not (vector? open-obligation-ids))
                   (conj :transition/open-obligation-basis-missing)

                   (not (vector? active-grant-ids))
                   (conj :transition/active-grant-basis-missing)

                   (not= (:identity.transition/to transition-record)
                         (:identity.epoch/id new-epoch))
                   (conj :transition/new-epoch-mismatch)

                   (not= (:identity.transition/from transition-record)
                         (:identity.epoch/previous new-epoch))
                   (conj :transition/lineage-mismatch)

                   (not (zero? (:identity.epoch/initial-trust new-epoch)))
                   (conj :transition/trust-not-zero)

                   (and (vector? open-obligation-ids)
                        (not= (set open-obligation-ids)
                              (set (:identity.transition/open-obligations
                                    transition-record))))
                   (conj :transition/open-obligations-incomplete)

                   (and (vector? active-grant-ids)
                        (not= (set active-grant-ids)
                              (set (:identity.transition/revoked-grants
                                    transition-record))))
                   (conj :transition/grant-revocation-incomplete))]
    (when (seq problems)
      (throw (ex-info "identity epoch transition rejected"
                      {:identity.causal/problems problems})))
    {:identity.causal/transition transition-record
     :identity.causal/new-epoch new-epoch}))
