(ns kotoba.banking.api
  "Open Banking payment-initiation and account-information request/response
  construction — pure data contracts, no network, no I/O.

  PRIMARY TARGET (implemented): the Berlin Group **NextGenPSD2 XS2A
  Framework**, Implementation Guidelines + OpenAPI definition **version
  1.3.11 (2021-09-24)** — the EU/DEU-anchored PSD2 Open Banking API standard
  this fleet already cites (BaFin, in cloud-itonami-isic-6419's
  banking.facts catalog). Field names, the `transactionStatus` enum and the
  request/response JSON shapes below are transcribed verbatim from the
  Berlin Group's own machine-readable OpenAPI schema, as mirrored in
  adorsys/xs2a (the Berlin-Group-affiliated reference open-source XS2A
  implementation):
  https://github.com/adorsys/xs2a/blob/develop/xs2a-impl/src/main/resources/static/psd2-api_v1.3.11-2021-10-01v1.yaml
  — cross-checked against a live ASPSP's generated API reference
  (https://docs-nextgenpsd2.api.memo.bank/operation/operation-initiatepayment)
  for the field list and the `tppMessages` error-code catalog (the OpenAPI
  file itself types `code` as a free string — the concrete code catalog
  lives in the Implementation Guidelines' prose tables, not the machine-
  readable schema, so `tpp-message-codes` below cites the second source).

  COVERED: single payment-initiation request/response
  (`POST /v1/{payment-service}/{payment-product}` with
  `payment-service=\"payments\"` — commonly written `POST
  /v1/payments/{payment-product}` in docs/summaries), the `transactionStatus`
  enum (14 real ISO 20022 codes), the `tppMessages`-shaped 4xx error body,
  and account information (`GET /v1/accounts`, `GET
  /v1/accounts/{account-id}/balances`).

  NOT COVERED (see README for the full scope note): consent management
  (`/v1/consents`), all SCA/strong-customer-authentication flows (redirect /
  OAuth2 / decoupled / embedded authorisation sub-resources, `scaMethods`,
  `challengeData`), bulk/periodic payments, payment cancellation, the
  transaction list (`/transactions`), card accounts, and the pain.001-XML-
  bodied payment products (only the four JSON-bodied products are modeled).

  SECONDARY jurisdictions (documented, not implemented — see README): UK
  Open Banking (OBIE) Payment Initiation API domestic-payments (PascalCase
  fields, `InstructedAmount{Amount,Currency}`, sort-code+account-number
  `SchemeName`/`Identification` instead of IBAN-first) and Japan's 全銀協
  (Japanese Bankers Association) 銀行分野のオープンAPIに係る電文仕様標準
  (fund-transfer/振込 is a defined category of that standard, though
  update-type — as opposed to inquiry-type — bank Open APIs remain rare in
  production per the Association's own reporting).

  Wired to kotoba.banking's existing records, not a duplicate data model:
  reuses `kotoba.banking/iban-valid?`/`parse-iban` for IBAN identification
  and builds/parses these JSON-shaped payloads FROM and TO
  `kotoba.banking/account` records. No network, no I/O — this namespace
  only builds and parses the EDN standing in for the real JSON body; a real
  adapter is a follow-up (same discipline as kotoba-lang/kessai's wire
  rail)."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [kotoba.banking :as bank]))

;; ---------------------------------------------------------------------------
;; Enumerations verified against the Berlin Group XS2A OpenAPI schema
;; ---------------------------------------------------------------------------

(def payment-products
  "JSON-bodied XS2A single-payment products this namespace covers (schema
  `paymentInitiation_json`'s documented product table). The four sibling
  `pain.001-*` XML-bodied products are NOT covered."
  #{"sepa-credit-transfers" "instant-sepa-credit-transfers"
    "target-2-payments" "cross-border-credit-transfers"})

(def transaction-statuses
  "ISO 20022 external payment/transaction status codes used by XS2A's
  `transactionStatus` (OpenAPI schema #/components/schemas/transactionStatus,
  Implementation Guidelines v1.3.11) -> a short description transcribed from
  the schema's own field descriptions."
  {"ACCC" "AcceptedSettlementCompleted — settlement on the creditor's account has been completed."
   "ACCP" "AcceptedCustomerProfile — technical validation and customer profile check succeeded."
   "ACSC" "AcceptedSettlementCompleted — settlement on the debtor's account has been completed."
   "ACSP" "AcceptedSettlementInProcess — all preceding checks succeeded; accepted for execution."
   "ACTC" "AcceptedTechnicalValidation — authentication and syntactical/semantical validation succeeded."
   "ACWC" "AcceptedWithChange — instruction accepted but a change will be made (e.g. date, remittance)."
   "ACWP" "AcceptedWithoutPosting — accepted without being posted to the creditor customer's account."
   "RCVD" "Received — payment initiation has been received by the receiving agent."
   "PDNG" "Pending — further checks and a status update will follow."
   "RJCT" "Rejected — payment initiation or an included transaction has been rejected."
   "CANC" "Cancelled — payment initiation has been cancelled before execution."
   "ACFC" "AcceptedFundsChecked — technical/profile checks succeeded and an automatic funds check was positive."
   "PATC" "PartiallyAcceptedTechnical — needs multiple authorisations; some but not all performed."
   "PART" "PartiallyAccepted — bulk-payment only: some transactions accepted, some not (yet)."})

(defn transaction-status?
  "True when s is one of the 14 real transactionStatus codes."
  [s]
  (contains? transaction-statuses s))

(def balance-types
  "Real `balanceType` enum values (OpenAPI schema #/components/schemas/balanceType)."
  #{"closingBooked" "expected" "openingBooked" "interimAvailable"
    "interimBooked" "forwardAvailable" "nonInvoiced"})

(def cash-account-types
  "ISO 20022 ExternalCashAccountType1Code values `cashAccountType` draws from
  (OpenAPI schema #/components/schemas/cashAccountType — the code list is
  commented out in the ASPSP-facing schema itself, since it is an external
  ISO 20022 code list, but is real and stable)."
  #{"CACC" "CARD" "CASH" "CHAR" "CISH" "COMM" "CPAC" "LLSV" "LOAN" "MGLD"
    "MOMA" "NREX" "ODFT" "ONDP" "OTHR" "SACC" "SLRY" "SVGS" "TAXE" "TRAN" "TRAS"})

(def account-statuses
  "Real `accountStatus` enum values (OpenAPI schema #/components/schemas/accountStatus)."
  #{"enabled" "deleted" "blocked"})

(def tpp-message-categories
  "Real `tppMessageCategory` enum values (OpenAPI schema
  #/components/schemas/tppMessageCategory)."
  #{"ERROR" "WARNING"})

(def tpp-message-codes
  "A representative, non-exhaustive subset of the NextGenPSD2 `tppMessages`
  code catalog. The OpenAPI file leaves `code` a free string (the concrete
  catalog is documented in the Implementation Guidelines' prose tables, not
  the machine-readable schema); this set is cross-verified against multiple
  independent ASPSPs' generated API docs built on the same Berlin Group
  spec (Memo Bank, Salt Edge, Landsbanki, IBM Open Banking Sandbox all
  document FORMAT_ERROR / PSU_CREDENTIALS_INVALID / RESOURCE_UNKNOWN with
  matching descriptions). Extend, never invent."
  #{"FORMAT_ERROR" "PARAMETER_NOT_CONSISTENT" "PARAMETER_NOT_SUPPORTED"
    "SERVICE_INVALID" "PRODUCT_INVALID" "PRODUCT_UNKNOWN" "RESOURCE_UNKNOWN"
    "RESOURCE_EXPIRED" "RESOURCE_BLOCKED" "TIMESTAMP_INVALID" "PERIOD_INVALID"
    "EXECUTION_DATE_INVALID" "SCA_METHOD_UNKNOWN" "SCA_INVALID"
    "PSU_CREDENTIALS_INVALID" "CORPORATE_ID_INVALID" "CONSENT_UNKNOWN"
    "CONSENT_INVALID" "CONSENT_EXPIRED" "TOKEN_UNKNOWN" "TOKEN_INVALID"
    "TOKEN_EXPIRED" "CERTIFICATE_INVALID" "CERTIFICATE_EXPIRED"
    "CERTIFICATE_BLOCKED" "CERTIFICATE_MISSING" "CERTIFICATE_REVOKED"
    "SIGNATURE_INVALID" "SIGNATURE_MISSING" "FUNDS_NOT_AVAILABLE"
    "PAYMENT_FAILED" "STATUS_INVALID" "ACCESS_EXCEEDED"})

;; ---------------------------------------------------------------------------
;; amountValue — decimal string, not a JSON number (schema pattern
;; "-?[0-9]{1,14}(\.[0-9]{1,3})?"). kotoba.banking keeps ledger amounts as
;; smallest-unit integers (cents); these helpers bridge the two.
;; ASSUMES 2 minor units (EUR/USD/GBP/... — the common case). 0-minor-unit
;; currencies (JPY) and 3-minor-unit currencies (BHD/KWD/OMR) are NOT
;; handled — a real integration needs an ISO 4217 minor-unit table this
;; fleet does not yet have.
;; ---------------------------------------------------------------------------

(defn- cents->amount-value [cents]
  (when (integer? cents)
    (let [negative? (neg? cents)
          c         (if negative? (- cents) cents)
          major     (quot c 100)
          minor     (rem c 100)]
      (str (when negative? "-") major "." (if (< minor 10) (str "0" minor) (str minor))))))

(defn- amount-value->cents [s]
  (when-let [[_ sign int-part frac-part]
             (and (string? s) (re-matches #"(-?)([0-9]{1,14})(?:\.([0-9]{1,3}))?" s))]
    (let [frac2 (subs (str (or frac-part "0") "00") 0 2)
          n     (+ (* (edn/read-string int-part) 100) (edn/read-string frac2))]
      (if (= sign "-") (- n) n))))

;; ---------------------------------------------------------------------------
;; accountReference (schema #/components/schemas/accountReference) —
;; bridges kotoba.banking/account <-> the real wire shape. An account whose
;; :account/id is a valid IBAN maps to the "iban" form; otherwise it maps to
;; the schema's "other" proprietary-ID form (both are real options the
;; schema itself documents).
;; ---------------------------------------------------------------------------

(defn account->account-reference
  "Adapt a kotoba.banking account record into an XS2A `accountReference`
  map: {\"iban\" ... \"currency\" ...} when :account/id is a valid IBAN,
  else {\"other\" {\"identification\" ...} \"currency\" ...}."
  [account]
  (let [id       (:account/id account)
        currency (:account/currency account)]
    (cond-> {}
      (bank/iban-valid? id) (assoc "iban" (:iban/normalized (bank/parse-iban id)))
      (not (bank/iban-valid? id)) (assoc "other" {"identification" id})
      currency (assoc "currency" currency))))

(defn account-reference->account
  "Parse an XS2A `accountReference` map back into a kotoba.banking account
  record. Returns nil when neither \"iban\" nor \"other\"/\"identification\"
  is present."
  [{:strs [iban other currency]} & {:keys [holder type]}]
  (when-let [id (or iban (get other "identification"))]
    (bank/account id currency :holder holder :type type)))

;; ---------------------------------------------------------------------------
;; Payment initiation — POST /v1/{payment-service}/{payment-product}
;;   (payment-service = "payments"), schema paymentInitiation_json /
;;   paymentInitationRequestResponse-201
;; ---------------------------------------------------------------------------

(defn payment-initiation-request
  "Construct the JSON-shaped request body for a single Berlin Group XS2A
  payment initiation (schema #/components/schemas/paymentInitiation_json;
  endpoint `POST /v1/payments/{payment-product}`). Returns a map with plain
  string keys mirroring the real JSON field names 1:1 — debtor-account /
  creditor-account are kotoba.banking account records (or bare IBAN
  strings), amount is a smallest-unit integer (cents) per
  kotoba.banking's convention. Throws ex-info with {:api/errors [...]}
  when a required field is invalid."
  [{:keys [payment-product debtor-account creditor-account creditor-name
           amount currency end-to-end-identification
           instruction-identification remittance-information-unstructured
           requested-execution-date]
    :as   fields}]
  (let [debtor-id         (if (map? debtor-account) (:account/id debtor-account) debtor-account)
        creditor-id       (if (map? creditor-account) (:account/id creditor-account) creditor-account)
        debtor-currency   (if (map? debtor-account) (:account/currency debtor-account) currency)
        creditor-currency (if (map? creditor-account) (:account/currency creditor-account) currency)
        errors            (cond-> []
                             (not (contains? payment-products payment-product)) (conj :bad-payment-product)
                             (not (bank/iban-valid? debtor-id))                 (conj :bad-debtor-iban)
                             (not (bank/iban-valid? creditor-id))                (conj :bad-creditor-iban)
                             (str/blank? creditor-name)                          (conj :missing-creditor-name)
                             (not (and (integer? amount) (pos? amount)))         (conj :bad-amount)
                             (not (re-matches #"[A-Z]{3}" (or currency "")))     (conj :bad-currency))]
    (when (seq errors)
      (throw (ex-info "invalid payment initiation request"
                       {:api/errors errors :api/fields (dissoc fields :debtor-account :creditor-account)})))
    (cond-> {"debtorAccount"    (account->account-reference {:account/id debtor-id :account/currency debtor-currency})
             "instructedAmount" {"currency" currency "amount" (cents->amount-value amount)}
             "creditorAccount"  (account->account-reference {:account/id creditor-id :account/currency creditor-currency})
             "creditorName"     creditor-name}
      end-to-end-identification            (assoc "endToEndIdentification" end-to-end-identification)
      instruction-identification           (assoc "instructionIdentification" instruction-identification)
      remittance-information-unstructured  (assoc "remittanceInformationUnstructured" remittance-information-unstructured)
      requested-execution-date             (assoc "requestedExecutionDate" requested-execution-date))))

(defn parse-payment-initiation-request
  "Parse a payment-initiation request JSON map back into
  {:api/debtor-account :api/creditor-account (kotoba.banking account
  records) :api/amount (cents) :api/currency :api/creditor-name
  :api/end-to-end-identification :api/remittance-information-unstructured}.
  Returns nil when the body is not a well-formed request (mirrors
  payment-initiation-request's required fields)."
  [{:strs [debtorAccount instructedAmount creditorAccount creditorName
           endToEndIdentification remittanceInformationUnstructured] :as body}]
  (when (and (map? body) debtorAccount instructedAmount creditorAccount creditorName)
    {:api/debtor-account                       (account-reference->account debtorAccount)
     :api/creditor-account                     (account-reference->account creditorAccount)
     :api/amount                               (amount-value->cents (get instructedAmount "amount"))
     :api/currency                             (get instructedAmount "currency")
     :api/creditor-name                        creditorName
     :api/end-to-end-identification            endToEndIdentification
     :api/remittance-information-unstructured  remittanceInformationUnstructured}))

(defn payment-initiation-response
  "Construct the 201 response body for a successful payment initiation
  (schema #/components/schemas/paymentInitationRequestResponse-201):
  {\"transactionStatus\" ... \"paymentId\" ... \"_links\" {...}}. status
  must be a real transactionStatus code (see transaction-statuses); returns
  nil otherwise. self-path/status-path build the real `_links.self` /
  `_links.status` hyperlink objects when supplied."
  [payment-id status & {:keys [self-path status-path]}]
  (when (transaction-status? status)
    {"transactionStatus" status
     "paymentId"          payment-id
     "_links"             (cond-> {}
                             self-path   (assoc "self" {"href" self-path})
                             status-path (assoc "status" {"href" status-path}))}))

(defn parse-payment-initiation-response
  "Parse a payment-initiation 201 response body back into
  {:api/payment-id :api/transaction-status :api/valid?}. :api/valid? is
  false when transactionStatus is missing/unknown or paymentId is absent."
  [{:strs [transactionStatus paymentId]}]
  {:api/payment-id        paymentId
   :api/transaction-status transactionStatus
   :api/valid?            (boolean (and paymentId (transaction-status? transactionStatus)))})

;; ---------------------------------------------------------------------------
;; tppMessages error body — application/json content-type of e.g.
;; BAD_REQUEST_400_PIS (schema #/components/schemas/Error400_NG_PIS and
;; siblings). The sibling application/problem+json RFC7807 shape
;; (type/title/detail/code) is a real alternate content-type XS2A also
;; defines but is NOT built by this namespace.
;; ---------------------------------------------------------------------------

(defn tpp-message
  "Construct a single tppMessage object (schema
  #/components/schemas/tppMessageGeneric): {\"category\" ... \"code\" ...
  \"path\"? \"text\"?}. category must be \"ERROR\" or \"WARNING\"; returns
  nil otherwise. code is not restricted to tpp-message-codes (ASPSPs may
  define additional codes per the Implementation Guidelines)."
  [category code & {:keys [path text]}]
  (when (contains? tpp-message-categories category)
    (cond-> {"category" category "code" code}
      path (assoc "path" path)
      text (assoc "text" text))))

(defn error-response
  "Construct a 4xx NextGenPSD2-specific error body (schema
  #/components/schemas/Error400_NG_PIS and its 401/403/404/409 siblings):
  {\"tppMessages\" [tpp-message ...]}. nil entries in tpp-messages are
  dropped so callers can inline a failed (tpp-message ...) call."
  [tpp-messages]
  {"tppMessages" (vec (remove nil? tpp-messages))})

;; ---------------------------------------------------------------------------
;; Account information — GET /v1/accounts, GET /v1/accounts/{id}/balances
;;   schemas accountDetails / accountList / balance / readAccountBalanceResponse-200
;; ---------------------------------------------------------------------------

(defn account->account-details
  "Adapt a kotoba.banking account into the XS2A `accountDetails` shape
  (schema #/components/schemas/accountDetails) as returned inside GET
  /v1/accounts's \"accounts\" array. resource-id is the ASPSP-assigned
  addressable resource id (schema field `resourceId`)."
  [account resource-id & {:keys [product cash-account-type status]}]
  (let [id (:account/id account)]
    (cond-> {"resourceId" resource-id
             "currency"   (:account/currency account)}
      (bank/iban-valid? id)                          (assoc "iban" (:iban/normalized (bank/parse-iban id)))
      (:account/holder account)                      (assoc "name" (:account/holder account)
                                                              "ownerName" (:account/holder account))
      product                                        (assoc "product" product)
      (contains? cash-account-types cash-account-type) (assoc "cashAccountType" cash-account-type)
      (contains? account-statuses status)             (assoc "status" status))))

(defn account-details->account
  "Parse an XS2A `accountDetails` map back into a kotoba.banking account
  record. Returns nil when neither \"iban\" nor \"bban\" is present."
  [{:strs [iban bban currency name]}]
  (when-let [id (or iban bban)]
    (bank/account id currency :holder name)))

(defn accounts->account-list-response
  "Construct the GET /v1/accounts 200 response body (schema
  #/components/schemas/accountList): {\"accounts\" [accountDetails ...]}."
  [account-details-maps]
  {"accounts" (vec account-details-maps)})

(defn account->balance
  "Construct a single `balance` object (schema
  #/components/schemas/balance) from a kotoba.banking account's current
  :account/balance (cents) and :account/currency. balance-type defaults to
  \"interimBooked\" (the running ledger balance); pass one of
  balance-types for other real balance types. Returns nil when balance-type
  is not real."
  [account & {:keys [balance-type reference-date] :or {balance-type "interimBooked"}}]
  (when (contains? balance-types balance-type)
    (cond-> {"balanceAmount" {"currency" (:account/currency account)
                               "amount"  (cents->amount-value (or (:account/balance account) 0))}
             "balanceType"   balance-type}
      reference-date (assoc "referenceDate" reference-date))))

(defn account->balances-response
  "Construct the GET /v1/accounts/{account-id}/balances 200 response body
  (schema #/components/schemas/readAccountBalanceResponse-200):
  {\"account\" accountReference \"balances\" [balance ...]}."
  [account & {:keys [types] :or {types ["interimBooked"]}}]
  {"account"  (account->account-reference account)
   "balances" (vec (keep #(account->balance account :balance-type %) types))})
