(ns kotoba.lang.causal-receipt-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-host :as host]
            [kotoba.lang.capability-values :as caps]
            [kotoba.lang.causal-receipt :as causal]))

(def graph-cid "bafygraph111111111111111111111111111111111111111111111111")
(def principal "did:key:alice")
(def now "2026-08-27")

(def decision
  {:decision/status :allow
   :decision/reason :authority/grant-matched
   :decision/grant-ids ["grant:graph-read"]
   :decision/runtime-capability-spec
   {:capability/principal principal
    :capability/actor :agent/reader
    :capability/action :graph-read
    :capability/resource graph-cid
    :capability/audience :kotoba
    :capability/tenant "acme"}
   :decision/trust-epoch-cid "epoch:new"
   :decision/trust-claim-cids ["claim:reader"]
   :decision/trust-policy-cid "bafy-authority-policy"
   :decision/trust-intent-cid "bafy-intent"
   :decision/trust-basis-cid "bafy-basis"})

(def authority
  {:causal.execution/decision-cid "bafy-decision"
   :causal.execution/template
   {:causal.receipt/intent-cid "bafy-intent"
    :causal.receipt/principal principal
    :causal.receipt/epoch-cid "epoch:new"
    :causal.receipt/policy-cid "bafy-authority-policy"
    :causal.receipt/basis-cid "bafy-basis"
    :causal.receipt/claim-cids ["claim:reader"]
    :causal.receipt/decision decision}})

(def requested
  (caps/graph-read-cap graph-cid
                       {:holder principal
                        :provenance ["grant:graph-read"]}))

(def ordinary-grant
  {:grant/kind :graph-read
   :grant/resources #{graph-cid}
   :grant/expires nil
   :grant/id "grant:graph-read"})

(defn call-opts [handler]
  {:call :kotoba.host/graph-read
   :requested requested
   :causal-authority authority
   :cacao-grants [ordinary-grant]
   :local-policy {:policy/allow {:graph-read #{graph-cid}}}
   :now now
   :handler handler})

(deftest causal-allow-still-needs-the-ordinary-capability-intersection
  (let [seen (atom nil)
        result (host/guard-causal-call
                (call-opts (fn [cap]
                             (reset! seen cap)
                             :read-result)))
        receipt (:kotoba.host/receipt result)]
    (is (true? (:kotoba.host/ok? result)))
    (is (= :read-result (:kotoba.host/result result)))
    (is (= graph-cid (:cap/resource @seen)))
    (is (causal/valid-bound-receipt? receipt))
    (is (= "bafy-decision"
           (get-in receipt
                   [:causal.execution/authority
                    :causal.execution/decision-cid])))
    (is (= ["claim:reader"]
           (get-in receipt
                   [:causal.execution/authority
                    :causal.execution/template
                    :causal.receipt/claim-cids])))
    (is (= :ok
           (get-in receipt
                   [:causal.execution/host-receipt :receipt/outcome])))))

(deftest an-llm-claim-cannot-bypass-local-policy
  (let [called? (atom false)
        result (host/guard-causal-call
                (assoc (call-opts (fn [_] (reset! called? true)))
                       :local-policy {:policy/allow {:graph-read #{}}}))]
    (is (false? (:kotoba.host/ok? result)))
    (is (= :empty-intersection (:kotoba.host/denied result)))
    (is (false? @called?))
    (is (causal/valid-bound-receipt? (:kotoba.host/receipt result)))
    (is (= :denied
           (get-in result
                   [:kotoba.host/receipt
                    :causal.execution/host-receipt
                    :receipt/outcome])))))

(deftest invalid-causal-authority-is-denied-before-the-handler
  (doseq [[label invalid]
          [[:intent-mismatch
            (assoc-in authority
                      [:causal.execution/template
                       :causal.receipt/intent-cid]
                      "bafy-other-intent")]
           [:principal-mismatch
            (assoc-in authority
                      [:causal.execution/template
                       :causal.receipt/principal]
                      "did:key:mallory")]
           [:raw-credential
            (assoc-in authority
                      [:causal.execution/template :credential/raw]
                      "secret")]]]
    (testing (name label)
      (let [called? (atom false)
            result (host/guard-causal-call
                    (assoc (call-opts (fn [_] (reset! called? true)))
                           :causal-authority invalid))]
        (is (false? (:kotoba.host/ok? result)))
        (is (= :causal-authority-invalid (:kotoba.host/denied result)))
        (is (false? @called?))
        (is (= :denied
               (get-in result [:kotoba.host/receipt :receipt/outcome])))))))

(deftest a-decision-for-another-resource-cannot-run
  (let [called? (atom false)
        invalid (assoc-in authority
                          [:causal.execution/template
                           :causal.receipt/decision
                           :decision/runtime-capability-spec
                           :capability/resource]
                          "bafy-other-graph")
        result (host/guard-causal-call
                (assoc (call-opts (fn [_] (reset! called? true)))
                       :causal-authority invalid))]
    (is (= :causal-authority-invalid (:kotoba.host/denied result)))
    (is (false? @called?))))
