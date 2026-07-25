(ns kotoba.lang.portable-effect-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-host :as host]
            [kotoba.lang.capability-values :as caps]
            [kotoba.lang.portable-effect :as effect]))

(def resource "library:ndl")
(def limits {:max-bytes 2097152 :max-items 20 :deadline-ms 10000})
(def now "2026-07-25")

(def ability
  (caps/make-component-cap
   :host/http resource
   {:target :toshokan/http
    :operation :get
    :limits limits
    :audit-id "ndl-search-1"}))

(def request
  (effect/request
   {:id "ndl-search-1"
    :call :toshokan/fetch
    :effects #{:host/http}
    :ability ability
    :input {:url "https://ndlsearch.ndl.go.jp/api/sru?q=kotoba"}}))

(def grant
  {:grant/kind :host/http
   :grant/resources #{resource}
   :grant/id "test-grant"
   :grant/target :toshokan/http
   :grant/operations #{:get}
   :grant/limits limits})

(def policy
  {:policy/allow {:host/http #{resource}}
   :policy/component
   {:host/http {:targets #{:toshokan/http}
                :operations #{:get}
                :limits limits}}})

(deftest portable-request-dispatches-through-component-ability-gate
  (let [seen (atom nil)
        {:keys [record! entries]} (host/journal)
        outcome (effect/dispatch
                 {:handlers {[:toshokan/http :get]
                             (fn [concrete input]
                               (reset! seen [concrete input])
                               {:status 200})}
                  :cacao-grants [grant]
                  :local-policy policy
                  :now now
                  :record! record!}
                 request)]
    (is (:kotoba.host/ok? outcome))
    (is (= {:status 200} (:kotoba.host/result outcome)))
    (is (= (assoc ability :cap/provenance ["test-grant"]) (first @seen))
        "the provider receives the concrete post-intersection ability")
    (is (= (:effect/input request) (second @seen)))
    (is (= :ok (:receipt/outcome (first (entries)))))))

(deftest portable-request-is-deny-by-default
  (testing "a missing effect row denies before provider execution"
    (let [calls (atom 0)
          outcome (effect/dispatch
                   {:handlers {[:toshokan/http :get]
                               (fn [_ _] (swap! calls inc))}
                    :cacao-grants [grant] :local-policy policy :now now}
                   (assoc request :effect/effects #{}))]
      (is (= :effect-not-declared (:kotoba.host/denied outcome)))
      (is (zero? @calls))))
  (testing "an unbound target cannot fall back to ambient host access"
    (let [outcome (effect/dispatch
                   {:handlers {} :cacao-grants [grant]
                    :local-policy policy :now now}
                   request)]
      (is (= :provider-absent (:kotoba.host/denied outcome)))))
  (testing "callbacks and other authority cannot be smuggled in the envelope"
    (let [outcome (effect/dispatch
                   {:handlers {} :cacao-grants [] :local-policy {} :now now}
                   (assoc request :handler identity))]
      (is (= :portable-effect-invalid (:kotoba.host/denied outcome))))))

(deftest sequential-dispatch-stops-at-first-denial
  (let [calls (atom [])
        handler (fn [_ input] (swap! calls conj (:n input)) (:n input))
        host {:handlers {[:toshokan/http :get] handler}
              :cacao-grants [grant] :local-policy policy :now now}
        denied (assoc request :effect/effects #{})
        result (effect/dispatch-all
                host
                [(assoc request :effect/id "one" :effect/input {:n 1})
                 denied
                 (assoc request :effect/id "three" :effect/input {:n 3})])]
    (is (false? (:kotoba.host/ok? result)))
    (is (= [1] @calls))
    (is (= 2 (count (:kotoba.host/outcomes result))))))
