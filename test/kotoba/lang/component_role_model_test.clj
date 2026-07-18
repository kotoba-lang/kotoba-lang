(ns kotoba.lang.component-role-model-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]))

(def model
  (edn/read-string (slurp "lang/component-role-model.edn")))

(deftest source-extension-and-runtime-role-are-orthogonal
  (is (= ".kotoba" (get-in model [:source-language :canonical-extension])))
  (is (some #{:guest-only} (get-in model [:source-language :does-not-imply])))
  (is (true? (get-in model [:runtime-roles :relational])))
  (is (true? (get-in model [:capability-invariant
                             :same-language-provider-does-not-bypass-imports]))))

(deftest kotoba-providers-still-depend-on-lower-capabilities
  (let [providers (get-in model [:component-kinds :provider :examples])
        http (first (filter #(= :http-client (:name %)) providers))
        db (first (filter #(= :database-driver (:name %)) providers))]
    (is (= #{:http/get :http/post} (:exports http)))
    (is (contains? (:imports http) :net/connect))
    (is (= #{:db/query :db/transaction} (:exports db)))
    (is (contains? (:imports db) :secret/read-scoped))))

(deftest native-trust-root-remains-explicit
  (is (some #{:raw-syscall-binding}
            (get-in model [:native-tcb :must-remain-outside-component-authority])))
  (is (= :component-tender-and-linker (get-in model [:kototama :role])))
  (is (some #{:ci-soak} (get-in model [:migration :cutover-still-requires]))))
