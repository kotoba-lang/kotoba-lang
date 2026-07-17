(ns kotoba.cli-test
  (:require #?(:clj [clojure.edn :as edn])
            [clojure.test :refer [deftest is run-tests]]
            [kotoba.cli :as cli]))

(def contract (cli/read-contract))

;; lang/adapters.edn is stored as Datomic/Datascript tx-data (see
;; schema.edn); every key was already namespaced (:kotoba.adapter.registry/*)
;; so the transform did not rename anything -- reverse the blob-ification of
;; non-scalar values the same way kotoba.cli/read-contract does for cli.edn.
#?(:clj
   (defn- unblob [v]
     (if (string? v)
       (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
            (catch Exception _ v))
       v)))

#?(:clj
   (defn- reconstitute-entity [tx-data]
     (into {} (map (fn [[k v]] [k (unblob v)]))
           (dissoc (first tx-data) :db/id))))

(def adapters
  #?(:clj (reconstitute-entity (edn/read-string (slurp "lang/adapters.edn")))
     :cljs {}))

(deftest contract-validates-in-cljc
  (let [result (cli/validate-contract contract)]
    (is (:kotoba.cli/ok? result))
    (is (= {:version 1
            :commands [:run :compile :check :db :git :rad :deploy :hinshitsu :wasm]
            :command-count 9
            :option-count 49}
           (:kotoba.cli/data result)))))

(deftest cljc-authority-implements-contract-commands
  (is (= {:kotoba.cli/ok? true
          :kotoba.cli/source :cljc
          :kotoba.cli/contract-commands ["check" "compile" "db" "deploy" "git" "hinshitsu" "rad" "run" "wasm"]
          :kotoba.cli/implemented-commands ["check" "compile" "db" "deploy" "git" "hinshitsu" "rad" "run" "wasm"]
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

(deftest compile-selects-language-owned-backends
  (is (= :kotoba-script
         (get-in (cli/dispatch contract ["compile" "main.kotoba" "--target" "web"])
                 [:kotoba.cli/data :backend])))
  (is (= :kotoba-script
         (get-in (cli/dispatch contract ["compile" "main.cljc" "--target" "web"])
                 [:kotoba.cli/data :backend])))
  (is (= :clojurescript
         (get-in (cli/dispatch contract ["compile" "main.cljs" "--target" "web"])
                 [:kotoba.cli/data :backend])))
  (is (= :compile/unsupported-extension
         (:kotoba.cli/code (cli/dispatch contract ["compile" "main.js" "--target" "web"])))))

(deftest side-effecting-commands-return-adapter-data
  (doseq [command ["run" "db" "git" "rad" "deploy" "hinshitsu"]]
    (let [result (cli/dispatch contract [command "--json"])]
      (is (:kotoba.cli/ok? result))
      (is (= :command/planned (:kotoba.cli/code result)))
      (is (= :adapter-required (get-in result [:kotoba.cli/data :host-action]))))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'kotoba.cli-test)]
    (when (pos? (+ (or fail 0) (or error 0)))
      #?(:clj (System/exit 1)
         :cljs nil))))
