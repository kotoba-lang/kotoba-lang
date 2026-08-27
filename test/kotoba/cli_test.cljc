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
            :commands [:id :run :compile :check :graph :git :rad :deploy :hinshitsu]
            :command-count 9
            :option-count 47}
           (:kotoba.cli/data result)))))

(deftest cljc-authority-implements-contract-commands
  (is (= {:kotoba.cli/ok? true
          :kotoba.cli/source :cljc
          :kotoba.cli/contract-commands ["check" "compile" "deploy" "git" "graph" "hinshitsu" "id" "rad" "run"]
          :kotoba.cli/implemented-commands ["check" "compile" "deploy" "git" "graph" "hinshitsu" "id" "rad" "run"]
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

(deftest id-is-wallet-first-and-base-bound-by-default
  (let [address "0xA00366234D29d4F882088048c0B2fa0dB7302D4E"
        result (cli/dispatch contract ["id" "--address" address])]
    (is (:kotoba.cli/ok? result))
    (is (= :id/generated (:kotoba.cli/code result)))
    (is (= "did:pkh:eip155:8453:0xa00366234d29d4f882088048c0b2fa0db7302d4e"
           (get-in result [:kotoba.cli/data :did])))
    (is (= :siwe-required (get-in result [:kotoba.cli/data :proof])))
    (is (nil? (get-in result [:kotoba.cli/data :private-key])))))

(deftest id-rejects-non-wallet-input-and-invalid-chain
  (is (= :id/address-invalid
         (:kotoba.cli/code (cli/dispatch contract ["id" "--address" "did:key:zLegacy"]))))
  (is (= :id/chain-invalid
         (:kotoba.cli/code
          (cli/dispatch contract ["id" "--address"
                                  "0xA00366234D29d4F882088048c0B2fa0dB7302D4E"
                                  "--chain-id" "0"])))))

(deftest side-effecting-commands-return-adapter-data
  (doseq [command ["run" "graph" "git" "rad" "deploy" "hinshitsu"]]
    (let [result (cli/dispatch contract [command "--json"])]
      (is (:kotoba.cli/ok? result))
      (is (= :command/planned (:kotoba.cli/code result)))
      (is (= :adapter-required (get-in result [:kotoba.cli/data :host-action]))))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'kotoba.cli-test)]
    (when (pos? (+ (or fail 0) (or error 0)))
      #?(:clj (System/exit 1)))))
