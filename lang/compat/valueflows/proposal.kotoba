(ns valueflows.proposal
  "Offers, requests, and the agreements they turn into — the reciprocity layer.

   `vf:Proposal` is \"published requests or offers, SOMETIMES with what is
   expected in return\", and `vf:Agreement` is \"a set of reciprocal commitments
   among economic agents\". Reciprocity is the thing this namespace is for:
   which intent is offered, which is expected back, and whether both sides of a
   deal were actually settled.

   ## A one-sided deal is not a defect

   Upstream's \"sometimes\" is load-bearing. A gift, a grant, a mutual-aid
   contribution and a donation are proposals with nothing reciprocal, and an
   agreement can stipulate commitments in one direction only. Treating a
   missing reciprocal as an error would make this library unable to express
   most of what a commons does. `:reciprocal? false` is a description, not a
   complaint.

   ## Only a unit-based proposal can be multiplied

   `vf:unitBased` means \"this group of intents contains unit based
   quantities, which can be multiplied to create commitments; commonly seen in
   a price list\". So `commitments-from` scales a price-list line by an order
   quantity, and REFUSES to scale a one-off offer — multiplying \"this specific
   used bicycle\" by three is not a bigger order, it is a category error."
  (:require [valueflows.commitment :as commitment]
            [valueflows.unit :as unit]))

(def purposes #{:offer :request})

;; ── shape ─────────────────────────────────────────────────────────────────

(defn conform-proposal
  "=> {:ok? bool :errors [...] :examined #{keys}}

   An empty or non-map proposal is an error, not a pass."
  [p]
  (cond
    (not (map? p)) {:ok? false :examined #{} :errors [{:code :not-a-map}]}
    (empty? p) {:ok? false :examined #{} :errors [{:code :empty-record}]}
    :else
    (let [errors
          (cond-> []
            (not (contains? purposes (:purpose p)))
            (conj {:code :purpose-not-offer-or-request
                   :purpose (:purpose p) :allowed purposes})

            (empty? (:publishes p))
            (conj {:code :publishes-nothing
                   :why "a proposal with no intent offers and asks for nothing"})

            ;; unitBased says the quantities can be multiplied. If the
            ;; published intents carry no quantity there is nothing to
            ;; multiply, and the flag is a promise the record cannot keep.
            (and (:unit-based p)
                 (not (every? #(or (:resource-quantity %) (:effort-quantity %))
                              (:publishes p))))
            (conj {:code :unit-based-without-quantities
                   :why "unitBased means the intents can be multiplied; these have no quantity"}))]
      {:ok? (empty? errors) :errors errors :examined (set (keys p))})))

(defn reciprocity
  "What is offered and what is expected back.

   => {:reciprocal? bool :offered [intents] :in-return [intents]
       :purpose :offer|:request :unit-based? bool}"
  [p]
  {:purpose (:purpose p)
   :offered (vec (:publishes p))
   :in-return (vec (:reciprocal p))
   :reciprocal? (boolean (seq (:reciprocal p)))
   :unit-based? (boolean (:unit-based p))
   :proposed-to (:proposed-to p)
   ;; stated, because "published to nobody in particular" and "published to
   ;; these agents" are different offers
   :scope (if (:proposed-to p) :named-agents :public)})

;; ── a price list line becomes an order ────────────────────────────────────

(defn- scale [m factor]
  (when m (update m :has-numerical-value * factor)))

(defn commitments-from
  "Multiply a unit-based proposal into commitments for `quantity` units.

   => {:ok? true :commitments [...] :reciprocal-commitments [...]}
      {:ok? false :insufficient :not-unit-based | :quantity-not-positive | ...}

   Both sides scale together: ordering three of something at 500 each commits
   you to three units and to 1500. Scaling only the goods and not the payment
   is how a price list becomes a loss."
  [p quantity {:keys [agreement due] :as _opts}]
  (let [c (conform-proposal p)]
    (cond
      (not (:ok? c))
      {:ok? false :insufficient :proposal-not-conformant :detail (:errors c)}

      (not (:unit-based p))
      {:ok? false :insufficient :not-unit-based
       :detail {:why "a one-off offer cannot be multiplied; three of THIS bicycle is not an order"}}

      (not (and (number? quantity) (pos? quantity)))
      {:ok? false :insufficient :quantity-not-positive :detail {:quantity quantity}}

      :else
      (let [mk (fn [intent reciprocal?]
                 (cond-> {:id (str (or (:id p) "proposal") "-"
                                   (if reciprocal? "recip" "primary") "-"
                                   (or (:id intent) (name (:action intent))))
                          :action (:action intent)
                          :satisfies (:id intent)}
                   (:resource-conforms-to intent)
                   (assoc :resource-conforms-to (:resource-conforms-to intent))
                   (:resource-quantity intent)
                   (assoc :resource-quantity (scale (:resource-quantity intent) quantity))
                   (:effort-quantity intent)
                   (assoc :effort-quantity (scale (:effort-quantity intent) quantity))
                   (:provider intent) (assoc :provider (:provider intent))
                   (:receiver intent) (assoc :receiver (:receiver intent))
                   due (assoc :due due)
                   agreement (assoc (if reciprocal? :reciprocal-clause-of :clause-of) agreement)))]
        {:ok? true
         :quantity quantity
         :commitments (mapv #(mk % false) (:publishes p))
         :reciprocal-commitments (mapv #(mk % true) (:reciprocal p))
         :reciprocal? (boolean (seq (:reciprocal p)))}))))

;; ── agreements ────────────────────────────────────────────────────────────

(def ^:private fulfilled-states
  "Delivered in full, whether or not anyone set the `finished` flag."
  #{:fulfilled :over-fulfilled :closed-exact :closed-over})

(def ^:private done-states
  "Nothing more is expected on this commitment.

   Includes :fulfilled and :over-fulfilled, NOT only the explicitly-:closed
   ones. `vf:finished` defaults to false and in a real log nobody goes back to
   set it on an order that already arrived, so requiring the flag would leave
   every delivered commitment looking open and make exposure uncomputable in
   practice. Delivered in full IS nothing-more-expected; `finished` adds the
   cases where less than the full amount will ever come."
  (into fulfilled-states #{:closed-short :closed-unmeasurable}))

(defn- side-state
  "One side of an agreement, as three separate facts rather than one verdict.

   :fulfilled?  every commitment delivered in full
   :closed?     nothing more will be done on this side, per vf:finished
   :short       the commitments that closed WITHOUT being fulfilled

   Written off and paid in full both mean 'nobody is waiting', but only one of
   them means 'nobody lost anything'. A single :settled? flag would collapse a
   loan repaid with a loan written off, which is the fact a lender most wants."
  [f]
  (when f
    (let [rows (filter :ok? (:rows f))]
      {:fulfilled? (and (:ok? f) (seq rows) (every? #(fulfilled-states (:state %)) rows))
       :closed? (and (:ok? f) (seq rows) (every? #(done-states (:state %)) rows))
       :short (mapv :commitment (filter #(= :closed-short (:state %)) rows))
       :open (mapv :commitment (remove #(done-states (:state %)) rows))})))

(defn agreement-balance
  "Are both sides of an agreement done, and did anyone lose?

   `:stipulates` are the primary commitments, `:stipulates-reciprocal` what is
   owed back. Each side is settled with `valueflows.commitment/fulfilments`, so
   the same rules apply — `finished` is not `fulfilled`, overdue needs an
   `:as-of`, orphan fulfilments are reported.

   => {:ok? true
       :both-fulfilled? bool     ; both sides delivered in full
       :both-closed? bool        ; nothing more will happen either way
       :closed-short {...}       ; which commitments closed without delivering
       :one-sided-exposure <side or nil>}

   `:one-sided-exposure` is the question an agreement exists to answer: which
   party has delivered while the other still OWES. A side that closed short is
   not exposure — nobody is waiting — but it is a loss, and `:closed-short`
   carries it."
  [agreement events opts]
  (let [primary (vec (:stipulates agreement))
        recip (vec (:stipulates-reciprocal agreement))]
    (if (and (empty? primary) (empty? recip))
      {:ok? false :insufficient :agreement-stipulates-nothing
       :detail {:agreement (:id agreement)
                :why "an agreement with no commitments is not a settled agreement"}}
      (let [pf (when (seq primary) (commitment/fulfilments primary events opts))
            rf (when (seq recip) (commitment/fulfilments recip events opts))
            ps (side-state pf)
            rs (side-state rf)
            one-sided? (empty? recip)]
        {:ok? true
         :agreement (:id agreement)
         :primary pf
         :reciprocal rf
         :primary-state ps
         :reciprocal-state rs
         :reciprocal? (not one-sided?)
         :one-sided? one-sided?
         :both-fulfilled? (if one-sided?
                            (boolean (:fulfilled? ps))
                            (boolean (and (:fulfilled? ps) (:fulfilled? rs))))
         :both-closed? (if one-sided?
                         (boolean (:closed? ps))
                         (boolean (and (:closed? ps) (:closed? rs))))
         :closed-short (cond-> {}
                         (seq (:short ps)) (assoc :primary (:short ps))
                         (seq (:short rs)) (assoc :reciprocal (:short rs)))
         ;; exposure needs one side DONE and the other still OPEN. A side that
         ;; closed short is done, so it does not leave the other exposed.
         :one-sided-exposure (when-not one-sided?
                               (cond
                                 (and (:closed? ps) (seq (:open rs))) :reciprocal-outstanding
                                 (and (:closed? rs) (seq (:open ps))) :primary-outstanding
                                 :else nil))}))))

(defn reciprocal-units
  "The units each side of an agreement is denominated in, canonicalised.

   Reported rather than checked: goods for money is the NORMAL case, so
   different units across the two sides is not an error the way adding them
   would be. A caller comparing value across sides needs a price, which this
   library does not have."
  [agreement]
  (let [u (fn [cs] (into (sorted-set)
                         (keep #(some-> (or (:resource-quantity %) (:effort-quantity %))
                                        :has-unit unit/canonical))
                         cs))]
    {:primary (u (:stipulates agreement))
     :reciprocal (u (:stipulates-reciprocal agreement))
     :note "different units across the two sides is the normal case (goods for money)"}))
