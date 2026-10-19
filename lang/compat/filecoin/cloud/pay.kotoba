(ns filecoin.cloud.pay
  "Filecoin Pay — the streaming payment rails Onchain Cloud settles on.

  A **rail** is a continuous payment from a payer to a payee at a rate per
  epoch, created and adjusted by an **operator** (the service contract) under
  an approval the payer granted in advance. Nothing is a one-off transfer:
  storage is paid for by the epoch for as long as the provider keeps proving
  it, so the primitive is a rate and a lockup rather than an amount.

  Three parties, and they are the reason the approval model exists:

  - the **payer** deposits tokens and approves an operator, capping both the
    rate and the total lockup the operator may commit them to;
  - the **operator** (Warm Storage) creates and modifies rails inside that
    cap, without ever holding the funds;
  - the **payee** (the storage provider) is paid as the rail settles.

  So `set-operator-approval` is the consequential call in this namespace: it
  is where a client decides how much a service may spend on their behalf,
  and it is denominated per epoch, not per month. `filecoin.cloud.chain`
  has the conversion.

  Amounts are decimal strings — USDFC has 18 decimals, so a month of storage
  is past what a JavaScript `Number` holds exactly. Selectors are constants
  checked against the deployed ABI; see `filecoin.cloud.pdp`."
  (:require [ethereum.abi :as abi]))

(def signatures
  {:deposit "deposit(address,address,uint256)"
   :deposit-with-permit "depositWithPermit(address,address,uint256,uint256,uint8,bytes32,bytes32)"
   :withdraw "withdraw(address,uint256)"
   :withdraw-to "withdrawTo(address,address,uint256)"
   :create-rail "createRail(address,address,address,address,uint256,address)"
   :modify-rail-payment "modifyRailPayment(uint256,uint256,uint256)"
   :modify-rail-lockup "modifyRailLockup(uint256,uint256,uint256)"
   :settle-rail "settleRail(uint256,uint256)"
   :settle-terminated-rail-without-validation "settleTerminatedRailWithoutValidation(uint256)"
   :terminate-rail "terminateRail(uint256)"
   :set-operator-approval "setOperatorApproval(address,address,bool,uint256,uint256,uint256)"
   :increase-operator-approval "increaseOperatorApproval(address,address,uint256,uint256)"
   :operator-approvals "operatorApprovals(address,address,address)"
   :accounts "accounts(address,address)"
   :get-account-info-if-settled "getAccountInfoIfSettled(address,address)"
   :get-rail "getRail(uint256)"
   :get-rails-for-payer-and-token "getRailsForPayerAndToken(address,address,uint256,uint256)"
   :get-rails-for-payee-and-token "getRailsForPayeeAndToken(address,address,uint256,uint256)"
   :get-rate-change-queue-size "getRateChangeQueueSize(uint256)"
   :network-fee-numerator "NETWORK_FEE_NUMERATOR()"
   :network-fee-denominator "NETWORK_FEE_DENOMINATOR()"
   :commission-max-bps "COMMISSION_MAX_BPS()"})

(def selectors
  {:deposit "0x8340f549"
   :deposit-with-permit "0x8ef59739"
   :withdraw "0xf3fef3a3"
   :withdraw-to "0xc3b35a7e"
   :create-rail "0xf9f78de8"
   :modify-rail-payment "0x97d3ea34"
   :modify-rail-lockup "0xde07b8bb"
   :settle-rail "0xbcd40bf8"
   :settle-terminated-rail-without-validation "0x4341325c"
   :terminate-rail "0xcbb0bf18"
   :set-operator-approval "0x875bc8b6"
   :increase-operator-approval "0xa159b1ed"
   :operator-approvals "0xe3d4c69e"
   :accounts "0xad74b775"
   :get-account-info-if-settled "0x05f4c536"
   :get-rail "0x22e440b3"
   :get-rails-for-payer-and-token "0x007b5fd1"
   :get-rails-for-payee-and-token "0x7f7562fa"
   :get-rate-change-queue-size "0x356412ae"
   :network-fee-numerator "0x553d8c82"
   :network-fee-denominator "0xe0975cf8"
   :commission-max-bps "0x8aab236a"})

(defn selector [k]
  (or (get selectors k)
      (throw (ex-info "pay: unknown method" {:method k}))))

(defn call
  "Calldata for a Filecoin Pay method, as `0x…`."
  [k values]
  (abi/encode-call-hex (selector k)
                       (abi/argument-types (get signatures k))
                       values))

(defn set-operator-approval
  "Authorise `operator` to create rails paying out of the caller's `token`
  balance, up to `rate-allowance` per epoch and `lockup-allowance` in total,
  with lockups no longer than `max-lockup-period` epochs.

  Both allowances are ceilings on what the operator may *commit*, not a
  transfer — but an operator that has been approved can start a rail without
  asking again, which is the point and the risk. `false` for `approved`
  revokes."
  [token operator approved? rate-allowance lockup-allowance max-lockup-period]
  (call :set-operator-approval
        [token operator approved? (str rate-allowance) (str lockup-allowance)
         (str max-lockup-period)]))

(defn deposit
  "Move `amount` of `token` into the payments contract, credited to `to`.
  Requires an ERC-20 approval on the token first — a deposit without one
  reverts inside `transferFrom`, which reads as a payments failure."
  [token to amount]
  (call :deposit [token to (str amount)]))

(defn withdraw [token amount]
  (call :withdraw [token (str amount)]))

(defn settle-rail
  "Settle `rail-id` up to `until-epoch`. Anyone may call this; it moves what
  has already been earned, and does not decide whether it was."
  [rail-id until-epoch]
  (call :settle-rail [(str rail-id) (str until-epoch)]))
