(ns kotoba.cli-test
  (:require #?(:clj [clojure.edn :as edn])
            [clojure.test :refer [deftest is run-tests]]
            [kotoba.cli :as cli]))

(def contract (cli/read-contract))
(def adapters
  #?(:clj (edn/read-string (slurp "lang/adapters.edn"))
     :cljs {}))

(deftest contract-validates-in-cljc
  (let [result (cli/validate-contract contract)]
    (is (:kotoba.cli/ok? result))
    (is (= {:version 1
            :commands [:id :run :compile :check :graph :git :rad :build :test :deploy :library :hinshitsu]
            :command-count 12
            :option-count 66}
           (:kotoba.cli/data result)))))

(deftest cljc-authority-implements-contract-commands
  (is (= {:kotoba.cli/ok? true
          :kotoba.cli/source :cljc
          :kotoba.cli/contract-commands ["build" "check" "compile" "deploy" "git" "graph" "hinshitsu" "id" "library" "rad" "run" "test"]
          :kotoba.cli/implemented-commands ["build" "check" "compile" "deploy" "git" "graph" "hinshitsu" "id" "library" "rad" "run" "test"]
          :kotoba.cli/missing-commands []}
         (cli/conformance contract))))

(deftest adapter-registry-is-cljc-authoritative
  (let [result (cli/validate-adapter-registry adapters)]
    (is (:kotoba.cli/ok? result))
    (is (= {:adapter-count 3
            :adapters [:adapter/node-cli :adapter/jvm-cli :adapter/native-cli]}
           (:kotoba.cli/data result)))))

(deftest argv-is-shaped-as-data
  (is (= {:positionals ["main.kotoba"]
          :options {:target "kotoba"
                    :arg ["1" "2"]
                    :json true}}
         (cli/parse-argv ["main.kotoba" "--target" "kotoba" "--arg" "1" "--arg" "2" "--json"]))))

(deftest check-cli-contract-runs-in-cljc
  (let [result (cli/dispatch contract ["check" "--kind" "cli-contract"])]
    (is (:kotoba.cli/ok? result))
    (is (= :check (:kotoba.cli/command result)))
    (is (= :contract/valid (:kotoba.cli/code result)))))

(deftest id-is-passkey-first-and-chain-neutral
  (let [result (cli/dispatch contract ["id" "new" "--rp-id" "itonami.cloud"])]
    (is (:kotoba.cli/ok? result))
    (is (= :id/enrollment-requested (:kotoba.cli/code result)))
    (is (= :passkey-smart-account (get-in result [:kotoba.cli/data :method])))
    (is (= :webauthn-p256
           (get-in result [:kotoba.cli/data :controller :signature-suite])))
    (is (= :secure-random-principal-id
           (get-in result [:kotoba.cli/data :host-action])))
    (is (nil? (get-in result [:kotoba.cli/data :chain-default])))
    (is (nil? (get-in result [:kotoba.cli/data :private-key])))))

(deftest id-plans-explicit-smart-account-links-without-a-base-default
  (let [principal "urn:kotoba:principal:018f4d6c-29bf-7f80-9a21-111111111111"
        base "eip155:8453:0xa00366234d29d4f882088048c0b2fa0db7302d4e"
        ethereum "eip155:1:0xa00366234d29d4f882088048c0b2fa0db7302d4e"
        result (cli/dispatch contract ["id" "new" "--rp-id" "itonami.cloud"
                                       "--principal" principal
                                       "--account" base
                                       "--account" ethereum])]
    (is (:kotoba.cli/ok? result))
    (is (= :id/enrollment-planned (:kotoba.cli/code result)))
    (is (= principal (get-in result [:kotoba.cli/data :principal])))
    (is (= [base ethereum]
           (mapv :identity.account/id (get-in result [:kotoba.cli/data :accounts]))))
    (is (every? #(= :erc4337 (:identity.account/protocol %))
                (get-in result [:kotoba.cli/data :accounts])))
    (is (every? #(= #{:erc1271 :erc6492}
                     (:identity.account/signature-verifiers %))
                (get-in result [:kotoba.cli/data :accounts])))
    (is (nil? (get-in result [:kotoba.cli/data :chain-default])))))

(deftest legacy-evm-address-is-only-an-explicit-account-link
  (let [address "0xA00366234D29d4F882088048c0B2fa0dB7302D4E"
        no-chain (cli/dispatch contract ["id" "account" "--address" address])
        result (cli/dispatch contract ["id" "account" "--address" address
                                       "--chain-id" "10"])]
    (is (= :id/chain-required (:kotoba.cli/code no-chain)))
    (is (:kotoba.cli/ok? result))
    (is (= :id/account-described (:kotoba.cli/code result)))
    (is (false? (get-in result [:kotoba.cli/data :principal?])))
    (is (= "eip155:10:0xa00366234d29d4f882088048c0b2fa0db7302d4e"
           (get-in result [:kotoba.cli/data :account-id])))
    (is (= "did:pkh:eip155:10:0xa00366234d29d4f882088048c0b2fa0db7302d4e"
           (get-in result [:kotoba.cli/data :account-did])))))

(deftest id-rejects-incomplete-or-invalid-input
  (is (= :id/rp-id-invalid
         (:kotoba.cli/code (cli/dispatch contract ["id" "new"]))))
  (is (= :id/account-invalid
         (:kotoba.cli/code
          (cli/dispatch contract ["id" "new" "--rp-id" "itonami.cloud"
                                  "--account" "base:0xnot-caip10"]))))
  (is (= :id/chain-invalid
         (:kotoba.cli/code
          (cli/dispatch contract ["id" "account" "--address"
                                  "0xA00366234D29d4F882088048c0B2fa0dB7302D4E"
                                  "--chain-id" "0"])))))

(deftest side-effecting-commands-return-adapter-data
  (doseq [command ["run" "graph" "git" "build" "test" "rad" "deploy" "hinshitsu"]]
    (let [result (cli/dispatch contract [command "--json"])]
      (is (:kotoba.cli/ok? result))
      (is (= :command/planned (:kotoba.cli/code result)))
      (is (= :adapter-required (get-in result [:kotoba.cli/data :host-action]))))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'kotoba.cli-test)]
    (when (pos? (+ (or fail 0) (or error 0)))
      #?(:clj (System/exit 1)))))
