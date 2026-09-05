(ns kotoba.lang.typed-eval-hold-test
  "HOLD: :code/eval execution is not available in this repository.

  lang/typed-eval.edn :status :implemented is the request/admission CONTRACT.
  This file does not execute (eval {:definition-cid …}) and does not install
  a provider. It pins the distinction and the fail-closed hatches so the
  contract cannot be read as an e2e measurement.

  The provider lives in kotoba-lang/codebase (typed_eval.cljc) and
  kotoba-lang/kotoba (typed_eval.clj). Do not duplicate it here."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private contract
  (edn/read-string (slurp "lang/typed-eval.edn")))

(def ^:private surface
  (edn/read-string (slurp "lang/surface-status.edn")))

(deftest contract-status-is-not-an-e2e-measurement
  (is (= :implemented (:kotoba.lang.typed-eval/status contract)))
  (is (= :blocked (get-in contract [:execution-in-this-repo :status])))
  (is (true? (get-in contract [:execution-in-this-repo :this-repo-has-no-provider])))
  (is (= :absent (get-in contract [:execution-in-this-repo :this-repo-harness :codebase-dep])))
  (is (= :blocked (get-in surface [:other-gaps :typed-eval :execution-in-this-repo])))
  (is (contains? (set (get-in surface [:other-gaps :typed-eval :missing]))
                 :code-eval-provider-in-this-repo)))

(deftest raw-eval-hatches-stay-forbidden
  (is (= {:host-eval :forbidden
          :load-string :forbidden
          :reader-eval :forbidden
          :ambient-namespace-resolution :forbidden}
         (:raw-eval contract)))
  (is (= #{:source-text :reader-form :namespace-name :host-object :capability-value}
         (get-in contract [:request :never]))))

(deftest this-repo-does-not-contain-the-eval-provider
  (is (false? (.exists (io/file "src/kotoba/codebase/typed_eval.cljc"))))
  (is (false? (.exists (io/file "src/kotoba/typed_eval.clj"))))
  (is (false? (str/includes? (slurp "deps.edn") "io.github.kotoba-lang/codebase"))))

(deftest harness-elaborates-eval-but-does-not-execute-code-eval
  (testing "frontend sugar: (eval request) becomes typed-cap-call 30"
    (let [hir (sema/analyze
               "(ns app (:capabilities #{:code/eval}))
                (defn run [request :document] :i64 (eval request))
                (defn main [] 0)")
          run (->> (:functions hir) (filter #(= 'run (:name %))) first)
          lowered (kir/lower hir)
          run-kir (->> (:functions lowered) (filter #(= 'run (:name %))) first)]
      (is (= #{[:cap/call 30]} (:effects hir)))
      (is (= '(typed-cap-call 30 :document :i64 request) (:body run)))
      (is (some #(and (seq? %) (= 'typed-cap-call (first %)) (= 30 (second %)))
                (tree-seq coll? seq (:body run-kir)))
          "lowering keeps wire 30; this is not DefCID execution")))
  (testing "host/reader hatches stay rejected"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"forbidden"
         (sema/analyze "(defn main [] (load-string \"(+ 1 2)\"))")))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"typed eval requires exactly one"
         (sema/analyze
          "(ns app (:capabilities #{:code/eval}))
           (defn run [] :i64 (eval))
           (defn main [] 0)")))))
