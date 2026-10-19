(ns identity.principal
  "Chain-neutral principals controlled by replaceable authenticators.

  A principal is the stable logical subject. Passkeys, hardware keys and
  threshold groups are controllers of that subject; blockchain accounts are
  linked endpoints. An account address is therefore never promoted to the
  principal and neither a controller nor an account link grants a capability.

  This namespace is deliberately pure. WebAuthn ceremony verification,
  ERC-1271/6492 calls, chain reads, persistence and capability admission stay
  with their respective host/provider boundaries."
  (:require [clojure.string :as str]))

(def schema "kotoba.identity.principal.v1")

(def controller-kinds #{:passkey :key :threshold})
(def controller-statuses #{:pending :verified :revoked})
(def account-kinds #{:smart-account :externally-owned})
(def account-statuses #{:pending :verified :revoked})
(def smart-account-protocols #{:erc4337 :native})
(def evm-signature-verifiers #{:erc1271 :erc6492})

(def ^:private caip10-re
  #"^[-a-z0-9]{3,8}:[-_a-zA-Z0-9]{1,32}:[-.%a-zA-Z0-9]{1,128}$")

(defn- non-blank-string? [x]
  (and (string? x) (not (str/blank? x))))

(defn principal-id?
  "Kotoba accepts an interoperable DID or its chain-neutral principal URN.
  The identifier names the subject, never authority by itself."
  [x]
  (and (non-blank-string? x)
       (or (str/starts-with? x "did:")
           (str/starts-with? x "urn:kotoba:principal:"))))

(defn account-id?
  "True for a CAIP-10 account id (`namespace:reference:address`)."
  [x]
  (and (string? x) (boolean (re-matches caip10-re x))))

(defn account-namespace [account-id]
  (when (account-id? account-id)
    (first (str/split account-id #":" 3))))

(defn passkey-controller
  "Describe one RP-scoped WebAuthn P-256 controller.

  `:verified` is meaningful only with `:registration-evidence`, produced by a
  verifier after a real, single-use WebAuthn registration ceremony. Supplying
  a credential id or public-key reference alone never makes a controller live."
  [id {:keys [rp-id credential-id public-key-ref status registration-evidence
              label added-at]}]
  {:identity.controller/id id
   :identity.controller/kind :passkey
   :identity.controller/status (or status :pending)
   :identity.controller/rp-id rp-id
   :identity.controller/credential-id credential-id
   :identity.controller/public-key-ref public-key-ref
   :identity.controller/signature-suite :webauthn-p256
   :identity.controller/registration-evidence registration-evidence
   :identity.controller/label label
   :identity.controller/added-at added-at})

(defn smart-account
  "Describe a chain account controlled through `controller-id`.

  CAIP-10 supplies the chain coordinate. `:erc4337` is EVM-only and declares
  ERC-1271 plus optional ERC-6492 verification; it does not select Base or any
  provider. Non-EVM account abstraction uses `:native` and an explicit proof
  reference owned by that chain adapter."
  [account-id controller-id
   {:keys [protocol status deployed? signature-verifiers link-evidence label]}]
  {:identity.account/id account-id
   :identity.account/kind :smart-account
   :identity.account/status (or status :pending)
   :identity.account/controller controller-id
   :identity.account/protocol protocol
   :identity.account/deployed? (boolean deployed?)
   :identity.account/signature-verifiers (set signature-verifiers)
   :identity.account/link-evidence link-evidence
   :identity.account/label label})

(defn externally-owned-account
  "Describe an optional legacy/HD-wallet account link. It remains an account,
  never the principal or the default authentication root."
  [account-id {:keys [status link-evidence label]}]
  {:identity.account/id account-id
   :identity.account/kind :externally-owned
   :identity.account/status (or status :pending)
   :identity.account/link-evidence link-evidence
   :identity.account/label label})

(defn principal
  "Build a principal document without choosing a chain or wallet provider."
  [id {:keys [controllers accounts recovery updated-at]}]
  {:identity.principal/schema schema
   :identity.principal/id id
   :identity.principal/controllers (vec controllers)
   :identity.principal/accounts (vec accounts)
   :identity.principal/recovery (vec recovery)
   :identity.principal/updated-at updated-at})

(defn- duplicate-values [xs]
  (->> xs frequencies (keep (fn [[x n]] (when (> n 1) x))) set))

(defn- controller-problems [controller]
  (let [kind (:identity.controller/kind controller)
        status (:identity.controller/status controller)]
    (cond-> []
      (not (non-blank-string? (:identity.controller/id controller)))
      (conj :controller/id)

      (not (contains? controller-kinds kind))
      (conj :controller/kind)

      (not (contains? controller-statuses status))
      (conj :controller/status)

      (and (= :passkey kind)
           (not (non-blank-string? (:identity.controller/rp-id controller))))
      (conj :passkey/rp-id)

      (and (= :passkey kind)
           (not (non-blank-string? (:identity.controller/credential-id controller))))
      (conj :passkey/credential-id)

      (and (= :passkey kind)
           (not (non-blank-string? (:identity.controller/public-key-ref controller))))
      (conj :passkey/public-key-ref)

      (and (= :passkey kind)
           (not= :webauthn-p256 (:identity.controller/signature-suite controller)))
      (conj :passkey/signature-suite)

      (and (= :verified status)
           (not (non-blank-string?
                 (:identity.controller/registration-evidence controller))))
      (conj :controller/verified-without-evidence))))

(defn- account-problems [account controller-ids]
  (let [account-id (:identity.account/id account)
        kind (:identity.account/kind account)
        status (:identity.account/status account)
        protocol (:identity.account/protocol account)
        verifiers (:identity.account/signature-verifiers account)]
    (cond-> []
      (not (account-id? account-id))
      (conj :account/caip10)

      (not (contains? account-kinds kind))
      (conj :account/kind)

      (not (contains? account-statuses status))
      (conj :account/status)

      (and (= :verified status)
           (not (non-blank-string? (:identity.account/link-evidence account))))
      (conj :account/verified-without-evidence)

      (and (= :smart-account kind)
           (not (contains? controller-ids (:identity.account/controller account))))
      (conj :smart-account/controller)

      (and (= :smart-account kind)
           (not (contains? smart-account-protocols protocol)))
      (conj :smart-account/protocol)

      (and (= :erc4337 protocol)
           (not= "eip155" (account-namespace account-id)))
      (conj :erc4337/non-evm-account)

      (and (= :erc4337 protocol)
           (or (empty? verifiers)
               (not (every? evm-signature-verifiers verifiers))
               (not (contains? verifiers :erc1271))))
      (conj :erc4337/signature-verifiers))))

(defn problems
  "Return every structural refusal. Unknown/missing proof never becomes live."
  [document]
  (let [controllers (:identity.principal/controllers document)
        accounts (:identity.principal/accounts document)
        controller-ids (set (map :identity.controller/id controllers))
        duplicate-controller-ids (duplicate-values (map :identity.controller/id controllers))
        duplicate-account-ids (duplicate-values (map :identity.account/id accounts))]
    (cond-> []
      (not= schema (:identity.principal/schema document))
      (conj :principal/schema)

      (not (principal-id? (:identity.principal/id document)))
      (conj :principal/id)

      (not (vector? controllers))
      (conj :principal/controllers)

      (not (vector? accounts))
      (conj :principal/accounts)

      (seq duplicate-controller-ids)
      (conj [:principal/duplicate-controllers duplicate-controller-ids])

      (seq duplicate-account-ids)
      (conj [:principal/duplicate-accounts duplicate-account-ids])

      :always
      (into (mapcat controller-problems controllers))

      :always
      (into (mapcat #(account-problems % controller-ids) accounts)))))

(defn valid? [document]
  (empty? (problems document)))

(defn active-controllers
  "Controllers that passed structural validation and carry verifier evidence.
  Authentication still requires a fresh assertion; this does not authorize an
  operation or mint a capability."
  [document]
  (if (valid? document)
    (->> (:identity.principal/controllers document)
         (filter #(= :verified (:identity.controller/status %)))
         vec)
    []))

(defn controlled?
  "True only when the document is sound and has a verified controller."
  [document]
  (boolean (seq (active-controllers document))))
