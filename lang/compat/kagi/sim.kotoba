(ns kagi.sim
  "デモドライバ。1 つの VaultActor に複数 op を通し、Governor verdict と台帳を見せる。
  実行: clojure -M:dev:run"
  (:require [langgraph.graph :as g]
            [kagi.operation :as op]
            [kagi.store :as store]
            [kagi.ledger :as ledger]
            [kagi.identity :as identity]
            [kagi.crypto :as crypto]))

(defn- run-op [actor request context]
  (:state (g/run* actor {:request request :context context}
                  {:thread-id (str (:op request) "-" (:item-id request))})))

(defn -main [& _]
  (let [id     (identity/generate-identity)
        st     (store/mem-store {:members {(:did id) #:member{:did (:did id) :role :owner}}})
        crypto (crypto/jvm-provider)
        actor  (op/build st {:crypto crypto :signer (identity/sign-secret id)})
        owner  {:did (:did id) :role :owner :phase 1
                :vmk (crypto/rand-bytes crypto 32) :purpose :daily-use}]
    (println "actor did :" (:did id))
    (println "graph(IPNS):" (:graph id))
    (println "── create(owner, phase 1) ──")
    (let [r (run-op actor {:op :item/create :item-id "gh-token"
                           :compartment "work" :plaintext (.getBytes "ghp_demo_secret" "UTF-8")}
                    owner)]
      (println "  result:" (:result r)))
    (println "── reveal(owner, purpose=daily-use) ──")
    (let [r (run-op actor {:op :item/reveal :item-id "gh-token"} owner)]
      (println "  revealed:" (when-let [pt (get-in r [:result :plaintext])]
                               (String. ^bytes pt "UTF-8"))))
    (println "── reveal(viewer, NO grant) → HOLD ──")
    (let [r (run-op actor {:op :item/reveal :item-id "gh-token"}
                    {:did "did:key:zViewer" :role :viewer :phase 1 :purpose :audit})]
      (println "  disposition:" (:disposition r) "result:" (:result r)))
    (println "── reveal(owner, NO purpose) → HOLD ──")
    (let [r (run-op actor {:op :item/reveal :item-id "gh-token"}
                    {:did "did:key:zOwner" :role :owner :phase 1})]
      (println "  disposition:" (:disposition r)))
    (println "── 監査台帳(hybrid 署名 + ハッシュ鎖) ──")
    (doseq [f (store/ledger st)]
      (println "  " (select-keys f [:ledger/seq :t :op :disposition :basis])
               "sig?" (boolean (:ledger/sig f))))
    (let [r (ledger/verify-chain (store/ledger st) crypto (constantly (identity/sign-public id)))]
      (println "verify-chain:" r))))
