(ns filecoin.cloud.chain
  "Where Filecoin Onchain Cloud is deployed, and how its clock works.

  Onchain Cloud lives on the FEVM, so its contracts have Ethereum addresses
  and are called through `eth_call` / `eth_sendRawTransaction` — while the
  data they are about is named by Filecoin CIDs and the deadlines they
  enforce are counted in Filecoin **epochs**. Both halves are here because
  getting from one to the other is where the mistakes are.

  An epoch is 30 seconds since genesis, and genesis is a different instant on
  each network: 2020-08-24 for mainnet, 2022-11-01 for calibration. A
  deadline computed with the wrong genesis is out by two years, and the
  arithmetic gives no sign of it."
  (:require [filecoin.address :as addr]))

(def ^:const epoch-seconds
  "Filecoin's block time. `builtin.EpochDurationSeconds`."
  30)

(def chains
  {:mainnet
   {:chain-id 314
    :genesis-timestamp 1598306400        ; 2020-08-24 22:00:00 UTC
    :rpc "https://api.node.glif.io/rpc/v1"
    :retrieval-domain "filbeam.io"
    :contracts
    {:usdfc "0x80B98d3aa09ffff255c3ba4A241111Ff1262F045"
     :filecoin-pay "0x23b1e018F08BB982348b15a86ee926eEBf7F4DAa"
     :warm-storage "0x8408502033C418E1bbC97cE9ac48E5528F371A9f"
     :warm-storage-view "0xAD28BBF18A72f728Ed816D07F5a1d7Ec40D68b5e"
     :service-provider-registry "0xf55dDbf63F1b55c3F1D4FA7e339a68AB7b64A5eB"
     :session-key-registry "0x74FD50525A958aF5d484601E252271f9625231aB"
     :pdp-verifier "0xBADd0B92C1c71d02E7d520f64c0876538fa2557F"}}

   :calibration
   {:chain-id 314159
    :genesis-timestamp 1667326380        ; 2022-11-01 18:13:00 UTC
    :rpc "https://api.calibration.node.glif.io/rpc/v1"
    :retrieval-domain "calibration.filbeam.io"
    :contracts
    {:usdfc "0xb3042734b608a1B16e9e86B374A3f3e389B4cDf0"
     :filecoin-pay "0x09a0fDc2723fAd1A7b8e3e00eE5DF73841df55a0"
     :warm-storage "0x02925630df557F957f70E112bA06e50965417CA0"
     :warm-storage-view "0xF4B446171b3677fD2B9b183a9fB76d517365700a"
     :service-provider-registry "0x839e5c9988e4e9977d40708d0094103c0839Ac9D"
     :session-key-registry "0x518411c2062E119Aaf7A8B12A2eDf9a939347655"
     :pdp-verifier "0x85e366Cf9DD2c0aE37E963d9556F5f4718d6417C"}}})

(defn chain
  "A chain by keyword or by chain id."
  [k]
  (or (get chains k)
      (first (filter #(= k (:chain-id %)) (vals chains)))
      (throw (ex-info "chain: unknown" {:chain k}))))

(defn contract
  "The deployed address of a contract on a chain.

  Named rather than passed around loose: the same client talks to seven of
  these, and `0x02925630…` on calibration is Warm Storage while
  `0x09a0fDc2…` is Filecoin Pay. A transposition sends a call to a contract
  that does not have the function, which reverts without saying why."
  [chain-key k]
  (or (get-in (chain chain-key) [:contracts k])
      (throw (ex-info "chain: unknown contract" {:chain chain-key :contract k}))))

;; ── the clock ────────────────────────────────────────────────────────────────

(defn epoch->unix [chain-key epoch]
  (+ (:genesis-timestamp (chain chain-key)) (* epoch epoch-seconds)))

(defn unix->epoch
  "Floored, not rounded: an epoch is the one that has *started*, and rounding
  up names a deadline that has not happened yet.

  `quot` rather than `Math/floor` — `int` truncates to 32 bits on the
  ClojureScript side, and this is exactly the kind of value that would be
  fine in a test and wrong in ten years. Times before genesis are not
  meaningful and are not handled specially."
  [chain-key unix]
  (quot (- unix (:genesis-timestamp (chain chain-key))) epoch-seconds))

(defn epochs-in
  "Epochs in a number of seconds — for turning a human duration into the
  units a lockup or a proving period is denominated in."
  [seconds]
  (quot seconds epoch-seconds))

(def ^:const epochs-per-day (quot 86400 epoch-seconds))     ; 2880
(def ^:const epochs-per-month (* 30 epochs-per-day))        ; 86400

;; ── the two address spaces ───────────────────────────────────────────────────

(defn f4->eth
  "The `f410f…` address of an FEVM contract → its `0x…` form. Both name the
  same actor; wallets and explorers show one, contract calls take the other."
  [a]
  (addr/to-eth-address (if (string? a) (addr/from-string a) a)))

(defn eth->f4 [hex] (addr/to-string (addr/from-eth-address hex)))
