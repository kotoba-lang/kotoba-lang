(ns kotoba.cli
  "CLJC authority for the public Kotoba CLI contract.

  Host binaries are adapters. This namespace owns the data contract loading,
  argument shaping, and command result model without depending on Rust."
  #?(:clj (:require [clojure.edn :as edn]
                    [clojure.java.io :as io]
                    [clojure.string :as str])) ; cljs consumers pass parsed EDN.
  #?(:cljs (:require [clojure.string :as str])))

(def default-contract-path "lang/cli.edn")

(def required-commands #{:run :check :db :git :rad :deploy})

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
  deploy, db transact, or git commit are represented as data for host adapters."
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
