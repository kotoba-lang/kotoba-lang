(ns adnet.billing
  "Ad billing — impression/click events → the advertiser's USDC charge, settled
  through the x402 rail (kotoba-lang/pay + treasury). Pure .cljc, zero deps: the
  charge is computed here; the actual on-chain settlement is the host's (it
  composes pay.x402 exactly as the sellers do). This closes the ad-income loop
  in our own currency instead of a third-party network's payout.

  Model: advertisers pre-fund a campaign (a USDC deposit to the network
  treasury, verified via treasury/receipt->onchain). Impressions/clicks draw
  down that prepaid balance — no per-event on-chain tx (that would cost more in
  gas than a $0.002 impression). Settlement is periodic: the network sweeps
  accrued charges and the advertiser tops up when the prepaid balance runs low.
  So billing here is pure accrual accounting; x402 handles the top-up deposits."
  (:require [adnet.core :as adnet]))

(defn charge-micros
  "The USDC micros an `event` costs the advertiser for `campaign`.
    :impression → CPM bids only: bid / 1000. CPC bids cost nothing per view.
    :click      → CPC bids only: the full bid. CPM bids cost nothing per click.
  Pure — an impression on a CPC campaign is free, and vice versa."
  [campaign event]
  (let [{:keys [model usd]} (:bid campaign)
        bid (adnet/usd->micros usd)]
    (cond
      (and (= event :impression) (= model :cpm)) (quot bid 1000)
      (and (= event :click)      (= model :cpc)) bid
      :else 0)))

(defn event
  "An append-only ad-event record (feeds the accrual ledger + analytics).
  `at` is an ISO-8601 timestamp supplied by the host."
  [{:keys [campaign-id advertiser placement kind at]}]
  {:ad/event kind                      ; :impression | :click
   :ad/campaign campaign-id
   :ad/advertiser advertiser
   :ad/placement placement
   :ad/at at})

(defn accrue
  "Fold an event into a campaign: record the charge and advance its budget/
  pacing (adnet.core/apply-spend). Returns {:campaign <updated> :charge-micros n
  :event <record>}. The host appends :event to the ledger and persists
  :campaign; when prepaid balance is low it asks the advertiser for an x402
  top-up. Pure."
  [campaign {:keys [kind at placement]}]
  (let [micros (charge-micros campaign kind)
        rec (event {:campaign-id (:id campaign) :advertiser (:advertiser campaign)
                    :placement placement :kind kind :at at})]
    {:campaign (if (pos? micros) (adnet/apply-spend campaign micros) campaign)
     :charge-micros micros
     :event rec}))

;; ── prepaid balance (funded by x402 USDC deposits) ──────────────────

(defn balance-micros
  "An advertiser's remaining prepaid balance: total USDC deposited (verified
  x402 top-ups) minus accrued charges. Pure over injected totals."
  [{:keys [deposited-micros accrued-micros]}]
  (max 0 (- (or deposited-micros 0) (or accrued-micros 0))))

(defn low-balance?
  "True when the prepaid balance can't cover `threshold-micros` more spend —
  the host prompts the advertiser for an x402 top-up before it runs dry."
  [account threshold-micros]
  (< (balance-micros account) threshold-micros))
