(ns filecoin.cloud.warm
  "FilecoinWarmStorageService — the contract that turns proofs into payment.

  It is the **listener** on a PDP data set: PDPVerifier tells it every time
  pieces are added, removed or proven, and it decides what that means for the
  payment rail. That is the whole architecture in one sentence — proving and
  paying are separate contracts, joined by a listener, so a data set can be
  proven for a service that has stopped paying, and a rail can run for a data
  set that has stopped being proven. Neither contract knows the other's
  business.

  Two addresses, and they are not interchangeable. **Warm Storage** takes the
  calls that change something; the separate **state view** contract answers
  the questions. Solidity contracts have a 24 KiB code limit and a service
  this size does not fit with its getters, so they were split — which means
  sending `getDataSet` to the service address reverts with no reason string,
  looking exactly like a missing data set.

  Selectors are constants checked against the deployed ABI; see
  `filecoin.cloud.pdp`. Note that `terminateService` is overloaded, so it
  appears twice under names distinguished by arity — the ABI allows that and
  a single name could only pick one."
  (:require [ethereum.abi :as abi]))

(def signatures
  {:get-service-price "getServicePrice()"
   :get-effective-rates "getEffectiveRates()"
   :pdp-verifier-address "pdpVerifierAddress()"
   :payments-contract-address "paymentsContractAddress()"
   :usdfc-token-address "usdfcTokenAddress()"
   :view-contract-address "viewContractAddress()"
   :service-provider-registry "serviceProviderRegistry()"
   :session-key-registry "sessionKeyRegistry()"
   :fil-beam-beneficiary-address "filBeamBeneficiaryAddress()"
   :terminate-service "terminateService(uint256)"
   :terminate-service-2 "terminateService(uint256,bytes)"
   :terminate-cdn-service "terminateCDNService(uint256)"
   :top-up-lifecycle-reserve "topUpLifecycleReserve(uint256,uint256)"
   :top-up-cdn-payment-rails "topUpCDNPaymentRails(uint256,uint256,uint256)"
   :validate-payment "validatePayment(uint256,uint256,uint256,uint256,uint256)"
   :get-proving-period-for-epoch "getProvingPeriodForEpoch(uint256,uint256)"
   :version "VERSION()"})

(def selectors
  {:get-service-price "0x5482bdf9"
   :get-effective-rates "0x93124a79"
   :pdp-verifier-address "0xde4b6b71"
   :payments-contract-address "0xbc471469"
   :usdfc-token-address "0xd39b33ab"
   :view-contract-address "0x7a9ebc15"
   :service-provider-registry "0x05f892ec"
   :session-key-registry "0x9f6aa572"
   :fil-beam-beneficiary-address "0xdd6979bf"
   :terminate-service "0xb997a71e"
   :terminate-service-2 "0xd0e3c954"
   :terminate-cdn-service "0x648564c0"
   :top-up-lifecycle-reserve "0x8f9324dd"
   :top-up-cdn-payment-rails "0xeb561d9c"
   :validate-payment "0x1a7bf46f"
   :get-proving-period-for-epoch "0x4a1fd7a3"
   :version "0xffa1ad74"})

(def view-signatures
  {:get-data-set "getDataSet(uint256)"
   :get-data-set-status "getDataSetStatus(uint256)"
   :get-data-set-size-in-bytes "getDataSetSizeInBytes(uint256)"
   :get-data-set-metadata "getDataSetMetadata(uint256,string)"
   :get-all-data-set-metadata "getAllDataSetMetadata(uint256)"
   :get-piece-metadata "getPieceMetadata(uint256,uint256,string)"
   :get-all-piece-metadata "getAllPieceMetadata(uint256,uint256)"
   :get-client-data-sets "getClientDataSets(address)"
   :get-client-data-sets-length "getClientDataSetsLength(address)"
   :client-nonces "clientNonces(address,uint256)"
   :get-approved-providers "getApprovedProviders(uint256,uint256)"
   :get-approved-providers-length "getApprovedProvidersLength()"
   :is-provider-approved "isProviderApproved(uint256)"
   :get-current-pricing-rates "getCurrentPricingRates()"
   :get-price-list "getPriceList()"
   :service-commission-bps "serviceCommissionBps()"
   :get-pdp-config "getPDPConfig()"
   :proving-deadline "provingDeadline(uint256)"
   :proving-activation-epoch "provingActivationEpoch(uint256)"
   :proven-this-period "provenThisPeriod(uint256)"
   :proven-periods "provenPeriods(uint256,uint256)"
   :next-pdp-challenge-window-start "nextPDPChallengeWindowStart(uint256)"
   :rail-to-data-set "railToDataSet(uint256)"})

(def view-selectors
  {:get-data-set "0xbdaac056"
   :get-data-set-status "0x617285ad"
   :get-data-set-size-in-bytes "0xfe295953"
   :get-data-set-metadata "0x4dc17df1"
   :get-all-data-set-metadata "0xf417c13f"
   :get-piece-metadata "0x837a7f49"
   :get-all-piece-metadata "0x3c0bd253"
   :get-client-data-sets "0x967c6f21"
   :get-client-data-sets-length "0x98a0b04e"
   :client-nonces "0x35b0e3f4"
   :get-approved-providers "0x7709a7f7"
   :get-approved-providers-length "0x4d745000"
   :is-provider-approved "0xb6133b7a"
   :get-current-pricing-rates "0xb5a578fc"
   :get-price-list "0xb7029144"
   :service-commission-bps "0x2afcc1a4"
   :get-pdp-config "0xea0f9354"
   :proving-deadline "0x149ac5cc"
   :proving-activation-epoch "0x725e3216"
   :proven-this-period "0x7598a1cd"
   :proven-periods "0x698762cb"
   :next-pdp-challenge-window-start "0x11d41294"
   :rail-to-data-set "0x2ad6e6b5"})

(defn selector [k]
  (or (get selectors k)
      (throw (ex-info "warm: unknown method" {:method k}))))

(defn view-selector [k]
  (or (get view-selectors k)
      (throw (ex-info "warm: unknown view method" {:method k}))))

(defn call
  "Calldata for a Warm Storage method — send this to the **service** address."
  [k values]
  (abi/encode-call-hex (selector k)
                       (abi/argument-types (get signatures k))
                       values))

(defn view-call
  "Calldata for a state-view method — send this to the **view** address.
  `filecoin.cloud.chain/contract` has both under `:warm-storage` and
  `:warm-storage-view`."
  [k values]
  (abi/encode-call-hex (view-selector k)
                       (abi/argument-types (get view-signatures k))
                       values))
