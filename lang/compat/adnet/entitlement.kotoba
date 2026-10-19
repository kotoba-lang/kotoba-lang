(ns adnet.entitlement
  "Ad-funded service: turn one ad impression into an entitlement to run one
  metered unit of a service, so a publisher can offer that service free of
  charge and be paid by the advertiser instead of the user.

  `adnet.core` decides which ad wins; `adnet.billing` decides what the
  advertiser owes for it. Neither knows that the money is meant to BUY
  something for the viewer, and until this namespace existed nothing joined
  the two — the ad rail earned USDC and the metered service charged credits,
  with no rule connecting one to the other. That gap is where an ad-funded
  free tier goes wrong, because the obvious implementation (serve an ad, then
  grant a request) grants on events that earned nothing.

  Pure .cljc, zero I/O, zero deps beyond adnet itself, like the rest of the
  stack: the host runs the auction, appends the ledger events and moves the
  credits. This namespace only says whether an impression paid for a unit,
  and how much of one.

  ── the four ways a naive ad-funded tier leaks ─────────────────────────────

  Each of these returns the same shape as a legitimately funded impression
  unless something refuses it, which is why they are decided here and not at
  the call site:

  1. **The house backfill.** `adnet.core/serve` always returns a creative —
     `{:kind :house}` when no campaign qualifies. A house ad is inventory we
     did not sell, so it funds nothing. Granting on it converts \"no
     advertiser\" into \"free service at our own expense\" silently.

  2. **The unbillable impression.** `adnet.billing/charge-micros` is 0 for an
     impression on a CPC campaign — correct, and it means a CPC advertiser's
     views earn nothing. A paid serve is therefore NOT evidence of revenue.

  3. **The replayed view.** An impression the viewer can report more than once
     mints unlimited units. This namespace requires the caller to present a
     receipt it already consumed (`:receipt-consumed?`), and refuses when the
     caller cannot say — an unanswerable question is not a yes.

  4. **The sub-unit charge.** One impression rarely pays for one request: at
     a $4.00 CPM an impression earns 4,000 micros and a $0.01 request costs
     10,000. Rounding that up to one unit loses money per request. So credit
     is accumulated in micros and units are bought out of the balance, which
     also makes \"how many ads is one request\" a measured consequence of the
     bid rather than a number anyone configures.

  ── money and margin, stated rather than assumed ──────────────────────────

  `:unit-price-micros` is the publisher's OWN list price for the unit (for
  murakumo.cloud, the x402 price of one inference request). `:funding-share`
  is the fraction of ad revenue that goes to funding service rather than
  margin; at the default 1 the free tier breaks even against list price and
  earns nothing, which is a deliberate choice and not an oversight — the
  marginal cost of a self-hosted unit is not measured here, so this namespace
  refuses to pretend it knows the profit. Lower the share to take margin;
  the viewer then watches proportionally more ads.

  Money is USDC micros (integer, 6 decimals) throughout, matching adnet.core
  and pay.core.")

;; ── policy ─────────────────────────────────────────────────────────────────

(def default-policy
  "The funding policy a host must supply. Every field is required in spirit:
  `policy-problem` refuses a policy with a missing or nonsensical field rather
  than filling it in, because a silently defaulted price is a silently wrong
  exchange rate between ads and service.

  :unit-price-micros   what one service unit costs at list price.
  :funding-share       fraction of ad revenue that funds service, in (0,1].
  :daily-unit-cap      most units one viewer may be granted per day.
  :max-balance-micros  ceiling on a viewer's carried balance, so a viewer
                       cannot bank credit indefinitely and spend it in one
                       burst the fleet has no capacity for.
  :sponsor             optional; when present, house serves may fund from a
                       DECLARED sponsorship instead of refusing. Its
                       :funded-by names who pays, so a sponsored grant is
                       never reported as advertiser revenue, and its
                       :daily-micros is a FLEET-WIDE daily budget -- not a
                       per-view or per-viewer figure. The distinction is the
                       whole point of the field: the per-viewer daily cap
                       bounds one viewer, so without a global budget a
                       sponsorship is bounded only by how many viewers show
                       up, which is not a bound at all."
  {:unit-price-micros 10000
   :funding-share 1
   :daily-unit-cap 20
   :max-balance-micros 200000
   :sponsor nil})

(defn- share-ok? [v]
  (and (number? v) (> v 0) (<= v 1)))

(defn policy-problem
  "nil when `policy` is usable, otherwise the keyword naming what is wrong.

  Fails closed on every field rather than merging `default-policy`: a host
  that forgot to set the unit price would otherwise run at this file's
  placeholder price, and the resulting exchange rate between an impression
  and a request would be a number nobody chose."
  [policy]
  (let [{:keys [unit-price-micros funding-share daily-unit-cap
                max-balance-micros sponsor]} policy]
    (cond
      (not (map? policy)) :policy/not-a-map
      (not (pos-int? unit-price-micros)) :policy/unit-price-not-positive-integer
      (not (share-ok? funding-share)) :policy/funding-share-out-of-range
      (not (pos-int? daily-unit-cap)) :policy/daily-unit-cap-not-positive-integer
      (not (pos-int? max-balance-micros)) :policy/max-balance-not-positive-integer
      (< max-balance-micros unit-price-micros) :policy/max-balance-below-unit-price
      (and (some? sponsor)
           (not (and (map? sponsor)
                     (pos-int? (:daily-micros sponsor))
                     (string? (:funded-by sponsor))
                     (seq (:funded-by sponsor)))))
      :policy/sponsor-malformed
      :else nil)))

;; ── crediting one impression ───────────────────────────────────────────────

(defn funding-micros
  "The micros an accrued ad event contributes to a viewer's service balance.

  Takes `charge-micros` as adnet.billing/accrue computed it — the advertiser's
  actual charge — and multiplies by the funding share, truncating DOWN. Down
  matters: rounding up would credit micros no advertiser paid, and a fraction
  of a micro repeated over a million impressions is a real overdraft."
  [charge-micros funding-share]
  (let [c (or charge-micros 0)]
    (if (or (not (pos? c)) (not (share-ok? funding-share)))
      0
      (long (Math/floor (* c funding-share))))))

(defn admit-impression
  "Decide whether one served-and-viewed impression funds service, and by how
  much. Pure; the caller has already run the auction, accrued the charge, and
  consumed the view receipt.

  `input`:
    :serve             the adnet.core/serve decision ({:kind :paid|:house …})
    :charge-micros     adnet.billing/accrue's :charge-micros for this event
    :receipt-consumed? did the caller atomically consume a single-use receipt
                       for this impression? nil/false both refuse — see leak 3
    :balance-micros    the viewer's current service balance
    :units-today       units already granted to this viewer today
    :sponsored-micros-today  sponsored micros ALREADY spent fleet-wide today.
                       Required whenever the policy declares a sponsor: nil is
                       refused rather than read as 0, because an unmeasured
                       spend treated as zero is how a budget stops binding.
    :policy            the funding policy

  Returns
    {:ok? true  :funding-micros n :funded-by …
     :balance-micros b' :units-affordable u :sponsored? bool}
  or
    {:ok? false :reason <keyword> …}

  A refusal is never an error state — \"this view earned nothing\" is the
  normal answer for a house backfill, and the caller renders it as \"watch
  another ad\", not as a fault."
  [{:keys [serve charge-micros receipt-consumed? balance-micros units-today policy]
    :as input}]
  (let [problem (policy-problem policy)
        {:keys [unit-price-micros funding-share daily-unit-cap
                max-balance-micros sponsor]} policy
        balance (or balance-micros 0)
        today (or units-today 0)]
    (cond
      problem
      {:ok? false :reason problem}

      ;; Leak 3. Asked before anything else: a caller that cannot prove the
      ;; receipt was consumed has not earned the right to the rest of this
      ;; decision, whatever the auction said.
      (not (true? receipt-consumed?))
      {:ok? false :reason :receipt/not-consumed}

      (not (integer? balance-micros))
      {:ok? false :reason :viewer/balance-unknown}

      (neg? balance)
      {:ok? false :reason :viewer/balance-negative}

      (not (integer? units-today))
      {:ok? false :reason :viewer/day-state-unknown}

      ;; The cap is a ceiling on grants, so at-the-cap refuses and one below
      ;; admits. Stated as >= deliberately: this is the comparison a boundary
      ;; test pins (see entitlement-test/daily-cap-boundary).
      (>= today daily-unit-cap)
      {:ok? false :reason :viewer/daily-cap-reached :units-today today}

      (>= balance max-balance-micros)
      {:ok? false :reason :viewer/balance-ceiling-reached :balance-micros balance}

      :else
      (let [paid? (= :paid (:kind serve))
            earned (funding-micros charge-micros funding-share)
            sponsored? (and (not (pos? earned)) (some? sponsor))
            sponsored-spent (:sponsored-micros-today input)
            ;; A sponsored view buys exactly one unit -- the point of a
            ;; sponsorship is that the viewer can actually run a request,
            ;; whereas an advertiser-funded view buys whatever the bid pays for.
            sponsored-credit (when sponsored? unit-price-micros)
            budget-left (when sponsored?
                          (- (:daily-micros sponsor) (or sponsored-spent 0)))
            credited (cond
                       (pos? earned) earned
                       sponsored? sponsored-credit
                       :else 0)
            funded-by (cond
                        (pos? earned) (or (get-in serve [:campaign :advertiser])
                                          :advertiser/unnamed)
                        sponsored? (:funded-by sponsor)
                        :else nil)]
        (cond
          ;; Leak 1 and 2 arrive here as the same shape and are separated on
          ;; purpose: "nobody bid" and "the winner's bid does not pay for
          ;; views" are different findings about the inventory, and a single
          ;; :not-funded reason would hide which one the publisher has.
          (and (not paid?) (not sponsored?))
          {:ok? false :reason :inventory/house-backfill-funds-nothing}

          (and paid? (not (pos? earned)) (not sponsored?))
          {:ok? false :reason :inventory/impression-not-billable
           :bid-model (get-in serve [:campaign :bid :model])}

          ;; The sponsor's budget is fleet-wide and daily, so an unmeasured
          ;; figure must refuse: reading it as 0 would make the budget
          ;; unbounded on exactly the day the query broke.
          (and sponsored? (not (integer? sponsored-spent)))
          {:ok? false :reason :sponsor/spend-unknown}

          ;; Spending the LAST of the budget is allowed; going over is not.
          ;; Stated as > so a credit that exactly finishes the budget lands --
          ;; sponsor-budget-boundary in the tests pins both sides.
          (and sponsored? (> sponsored-credit budget-left))
          {:ok? false :reason :sponsor/daily-budget-exhausted
           :sponsored-micros-today sponsored-spent
           :daily-micros (:daily-micros sponsor)}

          (not (pos? credited))
          {:ok? false :reason :inventory/no-funding-available}

          :else
          (let [balance' (min max-balance-micros (+ balance credited))]
            {:ok? true
             :funding-micros credited
             :funded-by funded-by
             :sponsored? (boolean sponsored?)
             :balance-micros balance'
             :units-affordable (quot balance' unit-price-micros)}))))))

;; ── spending the balance on one unit ───────────────────────────────────────

(defn admit-unit
  "Can this viewer run one metered unit right now? Pure.

  Admits when the balance is at least the unit price — at exactly the price it
  admits, because the price is what a unit costs, not what it costs to be
  comfortable. `units-today` is checked against the same cap as crediting, so
  a viewer who banked credit yesterday still cannot exceed today's cap."
  [{:keys [balance-micros units-today policy]}]
  (let [problem (policy-problem policy)
        {:keys [unit-price-micros daily-unit-cap]} policy
        balance (or balance-micros 0)
        today (or units-today 0)]
    (cond
      problem {:ok? false :reason problem}
      (not (integer? balance-micros)) {:ok? false :reason :viewer/balance-unknown}
      (not (integer? units-today)) {:ok? false :reason :viewer/day-state-unknown}
      (>= today daily-unit-cap) {:ok? false :reason :viewer/daily-cap-reached}
      (< balance unit-price-micros)
      {:ok? false :reason :viewer/insufficient-balance
       :balance-micros balance :unit-price-micros unit-price-micros
       :short-by-micros (- unit-price-micros balance)}
      :else
      {:ok? true
       :unit-price-micros unit-price-micros
       :balance-micros (- balance unit-price-micros)
       :units-affordable (quot (- balance unit-price-micros) unit-price-micros)})))

;; ── the records a host persists ────────────────────────────────────────────

(defn credit-record
  "The append-only record of an impression funding a viewer's balance.

  `:entitlement/funded-by` is the advertiser DID for advertiser-funded credit
  and the sponsor's name for sponsored credit, and `:entitlement/sponsored?`
  states which — so a publisher counting ad revenue cannot accidentally count
  its own sponsorship as income. That confusion is the whole reason both
  fields exist rather than one."
  [{:keys [viewer receipt-id campaign-id placement at verdict]}]
  {:entitlement/event :credit
   :entitlement/viewer viewer
   :entitlement/receipt receipt-id
   :entitlement/campaign campaign-id
   :entitlement/placement placement
   :entitlement/micros (:funding-micros verdict)
   :entitlement/funded-by (:funded-by verdict)
   :entitlement/sponsored? (boolean (:sponsored? verdict))
   :entitlement/balance-micros (:balance-micros verdict)
   :entitlement/at at})

(defn debit-record
  "The append-only record of a viewer spending balance on one unit."
  [{:keys [viewer unit-id at verdict]}]
  {:entitlement/event :debit
   :entitlement/viewer viewer
   :entitlement/unit unit-id
   :entitlement/micros (:unit-price-micros verdict)
   :entitlement/balance-micros (:balance-micros verdict)
   :entitlement/at at})

(defn impressions-per-unit
  "How many impressions of `campaign` pay for one unit under `policy` — the
  number a publisher actually needs to know, derived rather than configured.

  Returns nil when the answer does not exist (a CPC campaign never funds a
  unit through impressions), which is different from 0 and from a large
  number: it means this campaign cannot fund this service at all."
  [campaign policy billing-charge-fn]
  (let [earned (funding-micros (billing-charge-fn campaign :impression)
                               (:funding-share policy))]
    (when (pos? earned)
      (long (Math/ceil (/ (double (:unit-price-micros policy)) (double earned)))))))

;; ── funding mix, so sponsorship is never read as revenue ───────────────────

(defn funding-mix
  "Fold credit records into {:advertiser-micros n :sponsored-micros n
  :advertiser-share r :counted n}.

  `:advertiser-share` is nil when nothing was counted, never 0: no records and
  \"all of it sponsored\" are different states, and reporting an unmeasured
  mix as 0% advertiser-funded would be a measurement claim we did not make."
  [credit-records]
  (let [recs (filter #(= :credit (:entitlement/event %)) credit-records)
        adv (reduce + 0 (map #(if (:entitlement/sponsored? %) 0 (or (:entitlement/micros %) 0)) recs))
        spo (reduce + 0 (map #(if (:entitlement/sponsored? %) (or (:entitlement/micros %) 0) 0) recs))
        total (+ adv spo)]
    {:counted (count recs)
     :advertiser-micros adv
     :sponsored-micros spo
     :advertiser-share (when (pos? total) (/ (double adv) (double total)))}))

;; ── self-check: a count, never a boolean ───────────────────────────────────

(def ^:private check-policy
  {:unit-price-micros 10000 :funding-share 1 :daily-unit-cap 5
   :max-balance-micros 100000 :sponsor nil})

(defn self-check
  "Run this namespace's own invariants and return {:failures [..] :count n
  :checked n}.

  A count, not a boolean: a boolean cannot tell one regression from a wholly
  broken build, and this file is compiled into a Cloudflare Worker where a
  backend that accepts the code and answers wrongly is a real failure mode
  (root CLAUDE.md, question 8). Callers may run it at deploy time."
  []
  (let [paid {:kind :paid :campaign {:advertiser "did:key:zA" :bid {:model :cpm :usd "4.00"}}}
        house {:kind :house}
        base {:receipt-consumed? true :balance-micros 0 :units-today 0
              :policy check-policy}
        cases
        [[:house-funds-nothing
          (= :inventory/house-backfill-funds-nothing
             (:reason (admit-impression (assoc base :serve house :charge-micros 0))))]
         [:cpc-impression-not-billable
          (= :inventory/impression-not-billable
             (:reason (admit-impression
                       (assoc base :serve {:kind :paid :campaign {:bid {:model :cpc :usd "0.50"}}}
                              :charge-micros 0))))]
         [:unconsumed-receipt-refused
          (= :receipt/not-consumed
             (:reason (admit-impression (assoc base :serve paid :charge-micros 4000
                                               :receipt-consumed? nil))))]
         [:paid-impression-credits
          (= 4000 (:funding-micros (admit-impression
                                    (assoc base :serve paid :charge-micros 4000))))]
         [:unit-price-boundary-admits
          (true? (:ok? (admit-unit {:balance-micros 10000 :units-today 0
                                    :policy check-policy})))]
         [:one-micro-short-refuses
          (= :viewer/insufficient-balance
             (:reason (admit-unit {:balance-micros 9999 :units-today 0
                                   :policy check-policy})))]
         [:daily-cap-boundary
          (and (= :viewer/daily-cap-reached
                  (:reason (admit-unit {:balance-micros 100000 :units-today 5
                                        :policy check-policy})))
               (true? (:ok? (admit-unit {:balance-micros 100000 :units-today 4
                                         :policy check-policy}))))]
         [:bad-policy-refuses
          (= :policy/funding-share-out-of-range
             (:reason (admit-unit {:balance-micros 100000 :units-today 0
                                   :policy (assoc check-policy :funding-share 1.5)})))]
         [:mix-unmeasured-is-nil
          (nil? (:advertiser-share (funding-mix [])))]
         [:sponsor-budget-binds
          (let [pol (assoc check-policy :sponsor {:daily-micros 10000
                                                  :funded-by "house"})]
            (and (true? (:ok? (admit-impression (assoc base :serve house
                                                       :charge-micros 0
                                                       :sponsored-micros-today 0
                                                       :policy pol))))
                 (= :sponsor/daily-budget-exhausted
                    (:reason (admit-impression (assoc base :serve house
                                                      :charge-micros 0
                                                      :sponsored-micros-today 1
                                                      :policy pol))))))]
         [:sponsor-spend-must-be-measured
          (= :sponsor/spend-unknown
             (:reason (admit-impression
                       (assoc base :serve house :charge-micros 0
                              :policy (assoc check-policy
                                             :sponsor {:daily-micros 100000
                                                       :funded-by "house"})))))]]
        failures (->> cases (remove (fn [[_ ok?]] (true? ok?))) (mapv first))]
    {:checked (count cases)
     :count (count failures)
     :failures failures}))
