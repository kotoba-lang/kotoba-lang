(ns kotoba.cli
  "CLJC authority for the public Kotoba CLI contract.

  Host binaries are adapters. This namespace owns the data contract loading,
  argument shaping, and command result model without depending on Rust."
  #?(:clj (:require [clojure.edn :as edn]
                    [clojure.java.io :as io]
                    [clojure.string :as str]
                    [identity.principal :as principal])) ; cljs consumers pass parsed EDN.
  #?(:cljs (:require [clojure.string :as str]
                     [identity.principal :as principal])))

(def default-contract-path "lang/cli.edn")

(def required-commands
  #{:id :run :compile :check :graph :git :build :test :rad :deploy :library :hinshitsu})

(def adapter-kinds #{:node :jvm :native :browser :edge})

(defn read-contract
  "Read a CLI contract EDN file. CLJS callers should pass the parsed map to
  `validate-contract` and `command-result`."
  ([] #?(:clj (read-contract default-contract-path)
         :cljs (throw (ex-info "read-contract requires an EDN map on CLJS" {}))))
  ([path]
   #?(:clj
      (edn/read-string (slurp (io/file path)))
      :cljs
      (throw (ex-info "read-contract is not available on CLJS" {:path path})))))

(defn- duplicate-set [xs]
  (->> xs frequencies (filter (fn [[_ n]] (> n 1))) (map first) set))

(defn- failure [code message data]
  {:kotoba.cli/ok? false
   :kotoba.cli/code code
   :kotoba.cli/message message
   :kotoba.cli/data data})

(defn- success [code data]
  {:kotoba.cli/ok? true
   :kotoba.cli/code code
   :kotoba.cli/data data})

(def ^:private ethereum-address-re #"^0x[0-9A-Fa-f]{40}$")

(defn account-did
  "Derive a chain-bound DID alias for one explicitly selected EVM account.

  This is a linked account identifier, not the Kotoba principal. No chain is
  defaulted here and proof of account control remains a relying-party concern."
  [address chain-id]
  (when (and (string? address)
             (re-matches ethereum-address-re address)
             (pos-int? chain-id))
    (str "did:pkh:eip155:" chain-id ":" (str/lower-case address))))

(defn- parse-chain-id [value]
  (try
    (let [n #?(:clj (Long/parseLong (str value))
               :cljs (js/Number (str value)))]
      (when (and #?(:clj (pos? n)
                    :cljs (and (js/Number.isSafeInteger n) (pos? n)))
                 (<= n 9007199254740991))
        n))
    (catch #?(:clj Exception :cljs :default) _ nil)))

(defn- rp-id? [x]
  (and (string? x)
       (not (str/blank? x))
       (<= (count x) 253)
       (boolean (re-matches #"^[A-Za-z0-9.-]+$" x))
       (not (str/starts-with? x "."))
       (not (str/ends-with? x "."))))

(defn- many [x]
  (cond
    (nil? x) []
    (vector? x) x
    :else [x]))

(defn- account-plan [account-id]
  (if (= "eip155" (principal/account-namespace account-id))
    {:identity.account/id account-id
     :identity.account/kind :smart-account
     :identity.account/protocol :erc4337
     :identity.account/status :proof-required
     :identity.account/signature-verifiers #{:erc1271 :erc6492}}
    {:identity.account/id account-id
     :identity.account/kind :linked-account
     :identity.account/protocol :chain-native
     :identity.account/status :proof-required}))

(defn- passkey-enrollment-result [request]
  (let [principal-id (get-in request [:options :principal])
        rp-id (get-in request [:options :rp-id])
        account-ids (many (get-in request [:options :account]))
        invalid-account (first (remove principal/account-id? account-ids))]
    (cond
      (not (rp-id? rp-id))
      (failure :id/rp-id-invalid
               "id new requires a WebAuthn relying-party id"
               {:rp-id rp-id})

      (and principal-id (not (principal/principal-id? principal-id)))
      (failure :id/principal-invalid
               "principal must be a DID or urn:kotoba:principal:* identifier"
               {:principal principal-id})

      invalid-account
      (failure :id/account-invalid
               "account must be a CAIP-10 account id"
               {:account invalid-account})

      :else
      (success (if principal-id :id/enrollment-planned :id/enrollment-requested)
               (cond->
                {:method :passkey-smart-account
                 :principal principal-id
                 :controller {:kind :passkey
                              :signature-suite :webauthn-p256
                              :rp-id rp-id
                              :status :registration-required}
                 :accounts (mapv account-plan account-ids)
                 :chain-default nil
                 :custody :passkey-provider
                 :proof :webauthn-registration-required
                 :authority :capability-required}
                 (nil? principal-id)
                 (assoc :host-action :secure-random-principal-id))))))

(defn- evm-account-result [request]
  (let [address (or (get-in request [:options :address])
                    (first (remove #{"account"} (:positionals request))))
        raw-chain-id (get-in request [:options :chain-id])
        chain-id (when (some? raw-chain-id) (parse-chain-id raw-chain-id))]
    (cond
      (not (and (string? address) (re-matches ethereum-address-re address)))
      (failure :id/address-invalid
               "id account requires a public 0x Ethereum account address"
               {:address address})

      (nil? raw-chain-id)
      (failure :id/chain-required
               "chain-id is required; Kotoba has no implicit Base or EVM chain"
               {})

      (nil? chain-id)
      (failure :id/chain-invalid
               "chain-id must be a positive EIP-155 integer"
               {:chain-id raw-chain-id})

      :else
      (let [address (str/lower-case address)
            account-id (str "eip155:" chain-id ":" address)]
        (success :id/account-described
                 {:method :linked-chain-account
                  :principal? false
                  :account-id account-id
                  :account-did (account-did address chain-id)
                  :network :eip155
                  :chain-id chain-id
                  :address address
                  :proof :account-control-proof-required})))))

(defn validate-contract
  "Return a structured validation result for the CLI contract."
  [contract]
  (let [version (:kotoba.cli.contract/version contract)
        tier-labels (:kotoba.cli.contract/tier-labels contract)
        option-types (:kotoba.cli.contract/option-types contract)
        commands (:kotoba.cli.contract/commands contract)
        command-ids (mapv :id commands)
        errors (cond-> []
                 (not (pos-int? version))
                 (conj {:error :contract/version
                        :expected :positive-int
                        :actual version})

                 (not (map? tier-labels))
                 (conj {:error :contract/tier-labels})

                 (not (map? option-types))
                 (conj {:error :contract/option-types})

                 (not (vector? commands))
                 (conj {:error :contract/commands})

                 (and (vector? commands) (not= required-commands (set command-ids)))
                 (conj {:error :contract/command-set
                        :expected required-commands
                        :actual (set command-ids)})

                 (seq (duplicate-set command-ids))
                 (conj {:error :contract/duplicate-command
                        :ids (duplicate-set command-ids)}))]
    (if (seq errors)
      (failure :contract/invalid "CLI contract is invalid" {:errors errors})
      (success :contract/valid
               {:version version
                :commands command-ids
                :command-count (count commands)
                :option-count (count (mapcat :options commands))}))))

(defn validate-adapter-registry
  "Validate the host adapter registry for CLI launchers."
  [registry]
  (let [adapters (:kotoba.adapter.registry/adapters registry)
        errors (cond-> []
                 (not= 1 (:kotoba.adapter.registry/version registry))
                 (conj {:error :adapter-registry/version})

                 (not (false? (get-in registry [:kotoba.adapter.registry/policy :rust-in-default-repo?])))
                 (conj {:error :adapter-registry/rust-default})

                 (not (vector? adapters))
                 (conj {:error :adapter-registry/adapters})

                 (and (vector? adapters) (empty? adapters))
                 (conj {:error :adapter-registry/empty})

                 (and (vector? adapters)
                      (some #(not (keyword? (:id %))) adapters))
                 (conj {:error :adapter/id})

                 (and (vector? adapters)
                      (some #(not (contains? adapter-kinds (:kind %))) adapters))
                 (conj {:error :adapter/kind})

                 (and (vector? adapters)
                      (some #(not (string? (:repository %))) adapters))
                 (conj {:error :adapter/repository})

                 (and (vector? adapters)
                      (some #(not (and (set? (:consumes %))
                                       (seq (:consumes %))
                                       (every? keyword? (:consumes %)))) adapters))
                 (conj {:error :adapter/consumes})

                 (and (vector? adapters)
                      (some #(not (and (set? (:provides %))
                                       (seq (:provides %))
                                       (every? keyword? (:provides %)))) adapters))
                 (conj {:error :adapter/provides}))]
    (if (seq errors)
      (failure :adapter-registry/invalid "adapter registry is invalid" {:errors errors})
      (success :adapter-registry/valid
               {:adapter-count (count adapters)
                :adapters (mapv :id adapters)}))))

(defn command-specs [contract]
  (into {}
        (map (fn [command] [(:id command) command]))
        (:kotoba.cli.contract/commands contract)))

(defn implemented-commands
  "Commands implemented by this CLJC authority."
  [_contract]
  required-commands)

(defn conformance
  "Compare the contract command set with the CLJC authority implementation."
  [contract]
  (let [contract-commands (set (keys (command-specs contract)))
        implemented (implemented-commands contract)
        missing (sort (map name (remove implemented contract-commands)))]
    {:kotoba.cli/ok? (empty? missing)
     :kotoba.cli/source :cljc
     :kotoba.cli/contract-commands (sort (map name contract-commands))
     :kotoba.cli/implemented-commands (sort (map name implemented))
     :kotoba.cli/missing-commands missing}))

(defn- normalize-option-id [s]
  (keyword (str/replace s #"^--?" "")))

(defn parse-argv
  "Small data parser for host-neutral CLI args. It is intentionally not a shell
  runner; it shapes argv into EDN for command-result."
  [argv]
  (loop [args (seq argv)
         positionals []
         options {}]
    (if-not args
      {:positionals positionals :options options}
      (let [arg (first args)]
        (if (str/starts-with? arg "-")
          (let [k (normalize-option-id arg)
                more (next args)
                v (first more)]
            (if (or (nil? v) (str/starts-with? v "-"))
              (recur more positionals (assoc options k true))
              (recur (next more)
                     positionals
                     (update options k
                             (fn [old]
                               (cond
                                 (nil? old) v
                                 (vector? old) (conj old v)
                                 :else [old v]))))))
          (recur (next args) (conj positionals arg) options))))))

(defn command-result
  "Return the CLJC authoritative result shape for a command. Side effects such as
  deploy, graph transact, or git commit are represented as data for host adapters."
  [contract command-id request]
  (let [spec (get (command-specs contract) command-id)]
    (cond
      (nil? spec)
      (failure :command/unknown "unknown CLI command" {:command command-id})

      (= command-id :check)
      (let [kind (or (get-in request [:options :kind]) "auto")]
        (if (= kind "cli-contract")
          (assoc (validate-contract contract)
                 :kotoba.cli/command command-id)
          (success :check/planned
                   {:command command-id
                    :kind kind
                    :input (first (:positionals request))
                    :request request})))

      (= command-id :id)
      (let [action (first (:positionals request))
            account-mode? (or (= "account" action)
                              (some? (get-in request [:options :address]))
                              (and (string? action)
                                   (re-matches ethereum-address-re action)))]
        (cond
          account-mode? (evm-account-result request)
          (or (nil? action) (= "new" action)) (passkey-enrollment-result request)
          :else (failure :id/action-unknown
                         "id action must be new or account"
                         {:action action})))

      :else
      (success :command/planned
               {:command command-id
                :summary (:summary spec)
                :request request
                :host-action :adapter-required}))))

(defn dispatch
  "Dispatch argv as data using the CLJC authority. The first argv item is the
  command name, e.g. `[\"check\" \"--kind\" \"cli-contract\"]`."
  ([argv] (dispatch (read-contract) argv))
  ([contract argv]
   (let [[command & args] argv
         command-id (some-> command keyword)]
     (command-result contract command-id (parse-argv args)))))
