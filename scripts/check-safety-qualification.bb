#!/usr/bin/env bb
(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.set :as set])

(load-file "src/kotoba/lang/capability_values.cljc")
(alias 'caps 'kotoba.lang.capability-values)

(defn read-edn [path] (edn/read-string (slurp path)))
(defn fail [message data] (throw (ex-info message data)))
(defn require-truth [value message data]
  (when-not value (fail message data)))

(let [claims (read-edn "lang/safety-claims.edn")
      semantics (read-edn "lang/capability-semantics.edn")
      plan (read-edn "lang/safety-qualification.edn")
      roles (read-edn "lang/component-role-model.edn")
      transport (read-edn "lang/transport-component-abi.edn")
      transport-bytes (slurp "lang/transport-component-abi.edn")
      required-claims #{:t1-memory :t2-effect :t3-confinement :t4-determinism
                        :t5-resource-bounds :t6-supply-chain :t7-backend-parity
                        :t8-host-resource-scope}
      actual-claims (into #{} (map :id) (:claims claims))
      source-kinds (set (keys caps/effect-for-kind))
      contract-kinds (:kinds semantics)
      grammar-bytes (slurp "lang/guest-grammar.edn")]
  (require-truth (= required-claims actual-claims)
                 "Q1 must define exactly T1-T8" {:actual actual-claims})
  (doseq [claim (:claims claims)]
    (doseq [key [:claim :owner :tcb :negative-evidence :residual-risk]]
      (require-truth (seq (get claim key)) "Q1 claim field missing"
                     {:claim (:id claim) :field key}))
    (doseq [path (:negative-evidence claim)]
      (require-truth (.isFile (io/file path)) "Q1 evidence file missing"
                     {:claim (:id claim) :path path})))
  (require-truth (= source-kinds contract-kinds)
                 "Q2 capability catalog drift"
                 {:only-in-source (set/difference source-kinds contract-kinds)
                  :only-in-contract (set/difference contract-kinds source-kinds)})
  (require-truth (= :forbidden (get-in semantics [:rules :production-effective-wildcard]))
                 "Q2 production wildcard must be forbidden" {})
  (require-truth (true? (get-in semantics [:revocation :fail-closed]))
                 "Q2 revocation must fail closed" {})
  (require-truth (= :q1-threat-model-and-safety-claims
                    (first (:implementation-order plan)))
                 "qualification implementation order must start at Q1" {})
  (require-truth (and (= ".kotoba" (get-in roles [:source-language :canonical-extension]))
                      (some #{:guest-only} (get-in roles [:source-language :does-not-imply]))
                      (true? (get-in roles [:runtime-roles :relational]))
                      (true? (get-in roles [:capability-invariant
                                            :same-language-provider-does-not-bypass-imports]))
                      (= :component-tender-and-linker (get-in roles [:kototama :role])))
                 "component source and runtime role authority drift" {:roles roles})
  (require-truth (and (= :bounded-prototype (:status transport))
                      (= '#{transport-connect tls-open tls-server-end-point transport-write
                            transport-read transport-close}
                         (set (keys (:operations transport))))
                      (true? (get-in transport [:limits :default-deny]))
                      (zero? (get-in transport [:limits :max-open-connections]))
                      (= :implemented-jvm-prototype
                         (get-in transport [:maturity :kototama-native-transport-provider]))
                      (= :implemented
                         (get-in transport [:maturity :tls-local-conformance]))
                      (false? (get-in transport [:maturity :production-qualified])))
                 "bounded transport ABI authority drift" {:transport transport})
  (doseq [path ["../kotoba/resources/kotoba/lang/guest-grammar.edn"
                "../compiler/resources/kotoba/lang/guest-grammar.edn"]]
    (require-truth (= grammar-bytes (slurp path))
                   "Q3 vendored guest grammar drift"
                   {:authority "lang/guest-grammar.edn" :consumer path}))
  (require-truth (= transport-bytes
                    (slurp "../compiler/resources/kotoba/lang/transport-component-abi.edn"))
                 "transport ABI vendored compiler resource drift"
                 {:authority "lang/transport-component-abi.edn"
                  :consumer "../compiler/resources/kotoba/lang/transport-component-abi.edn"})
  (require-truth (.isFile (io/file "lang/qualification/q3-backend-parity.edn"))
                 "Q3 parity manifest missing" {})
  (println "Q1 PASS: T1-T8 claims have owner, TCB, negative evidence, and residual risk")
  (println "Q2 PASS:" (count contract-kinds)
           "capability kinds match executable semantics; production wildcard forbidden")
  (println "Q3 CONTRACT PASS: canonical guest grammar is byte-identical in both consumers"))
