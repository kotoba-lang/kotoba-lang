(ns kotoba.lang.safety-qualification-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-values :as caps]))

(defn read-edn [path]
  (edn/read-string (slurp (io/file path))))

(def kotoba-root
  (or (System/getenv "KOTOBA_QUALIFICATION_ROOT") "../kotoba"))

(def compiler-root
  (or (System/getenv "KOTOBA_COMPILER_QUALIFICATION_ROOT") "../compiler"))

(defn evidence-file [path]
  (cond
    (str/starts-with? path "../kotoba/")
    (io/file kotoba-root (subs path (count "../kotoba/")))

    (str/starts-with? path "../compiler/")
    (io/file compiler-root (subs path (count "../compiler/")))

    :else (io/file path)))

(deftest q1-claims-are-auditable
  (let [claims (:claims (read-edn "lang/safety-claims.edn"))]
    (is (= #{:t1-memory :t2-effect :t3-confinement :t4-determinism
             :t5-resource-bounds :t6-supply-chain :t7-backend-parity
             :t8-host-resource-scope}
           (into #{} (map :id) claims)))
    (doseq [claim claims]
      (testing (name (:id claim))
        (is (seq (:owner claim)))
        (is (seq (:tcb claim)))
        (is (seq (:negative-evidence claim)))
        (is (seq (:residual-risk claim)))
        (doseq [path (:negative-evidence claim)]
          (is (.isFile (evidence-file path)) path))))))

(deftest q2-catalog-is-the-executable-vocabulary
  (let [semantics (read-edn "lang/capability-semantics.edn")
        contract-kinds (:kinds semantics)
        source-kinds (set (keys caps/effect-for-kind))]
    (is (= source-kinds contract-kinds)
        (str "catalog drift: source-only="
             (set/difference source-kinds contract-kinds)
             " contract-only="
             (set/difference contract-kinds source-kinds)))
    (is (= :forbidden
           (get-in semantics [:rules :production-effective-wildcard])))
    (is (true? (get-in semantics [:revocation :fail-closed])))))

(deftest q2-production-policy-cannot-yield-wildcard-authority
  (let [requested (caps/make-cap :graph-read :any)
        result (caps/intersect-grants
                {:requested requested
                 :cacao-grants [{:grant/kind :graph-read
                                 :grant/resources #{:any}
                                 :grant/id "delegation-1"}]
                 :local-policy {:policy/allow {:graph-read #{:any}}
                                :policy/forbid-wildcard true}
                 :now "2026-07-18"})]
    (is (= {:denied :wildcard-forbidden} result))))

(deftest q3-consumers-embed-the-canonical-grammar-without-drift
  (let [authority (slurp "lang/guest-grammar.edn")]
    (is (= authority (slurp (io/file kotoba-root
                                     "resources/kotoba/lang/guest-grammar.edn"))))
    (is (= authority (slurp (io/file compiler-root
                                     "resources/kotoba/lang/guest-grammar.edn"))))))

(deftest component-source-does-not-encode-runtime-role
  (let [qualification (read-edn "lang/safety-qualification.edn")
        roles (read-edn "lang/component-role-model.edn")]
    (is (= "adr-2607183900-kotoba-component-language-runtime-roles"
           (:kotoba.lang.safety-qualification/adr qualification)))
    (is (true? (get-in qualification
                        [:capability-invariant
                         :same-language-provider-does-not-bypass-imports])))
    (is (true? (get-in roles [:runtime-roles :relational])))
    (is (= :wasm-component-tender-and-linker
           (get-in qualification [:repository-roles :kotoba-lang/kototama :role])))))
