(ns kotoba.lang.trusted-admission-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.trusted-admission :as trusted]))

(def peer (incidence/typed-ref :did "did:key:z6Mkpeer"))
(def transcript-block
  (incidence/incidence :session/transcript {:session/peer #{peer}} {}))
(def transcript-cid (incidence/incidence-cid transcript-block))

(def valid-chain
  {:chain/valid? true
   :chain/problems []
   :chain/root-iss "did:key:z6Mkroot"
   :chain/holder "did:key:z6Mkholder"
   :chain/resources #{"kotoba://cap/host/ledger-append/dataspace:rooms/a"}
   :chain/expires nil
   :chain/depth 1})

(def valid-session
  {:session/valid? true
   :session/id "captp-session-a"
   :session/version trusted/captp-version
   :session/peer peer
   :session/transcript-cid transcript-cid})

(deftest verified-delegation-is-opaque-and-maps-only-verified-results
  (let [value (trusted/verify-delegation! (constantly valid-chain)
                                           {:cacao "inert-evidence"})]
    (is (trusted/verified-delegation? value))
    (is (false? (trusted/verified-delegation? valid-chain)))
    (is (= "did:key:z6Mkroot"
           (:chain/root-iss (trusted/delegation-description value))))
    (is (= :host/ledger-append
           (:grant/kind (first (trusted/delegation-grants value)))))
    (is (not (map? value)))
    (is (try
          (edn/read-string (pr-str value))
          false
          (catch Exception _ true)))))

(deftest delegation-verification-fails-closed-and-sanitizes-errors
  (doseq [[expected verify!]
          [[:trusted/delegation-not-verified
            (constantly (assoc valid-chain :chain/valid? false))]
           [:trusted/delegation-verification-failed
            (fn [_] (throw (ex-info "secret verifier trace" {:secret true})))]]]
    (let [thrown (try
                   (trusted/verify-delegation! verify! :evidence)
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= expected (:problem (ex-data thrown))))
      (is (not (re-find #"secret" (str (ex-data thrown))))))))

(deftest authenticated-session-binds-peer-transcript-and-live-transport
  (let [sent (atom [])
        requested (atom [])
        session (trusted/authenticate-session!
                 (constantly valid-session)
                 {:handshake :inert}
                 {:send! (fn [message]
                           (swap! sent conj message)
                           :sent)
                  :request! (fn [call]
                              (swap! requested conj call)
                              :settled)})]
    (is (trusted/authenticated-session? session))
    (is (trusted/request-capable-session? session))
    (is (= :sent (trusted/session-send! session {:op :deliver})))
    (is (= :settled (trusted/session-request! session {:call :append})))
    (is (= [{:op :deliver}] @sent))
    (is (= [{:call :append}] @requested))
    (is (= peer (:session/peer (trusted/session-description session))))
    (is (= transcript-cid
           (:session/transcript-cid (trusted/session-description session))))
    (is (not (contains? (trusted/session-description session) :send!)))
    (is (not (contains? (trusted/session-description session) :request!)))
    (is (false? (trusted/authenticated-session? valid-session)))))

(deftest session-verification-and-transport-validation-fail-closed
  (doseq [[expected verify! transport]
          [[:trusted/session-not-verified
            (constantly (assoc valid-session :session/valid? false))
            {:send! identity}]
           [:trusted/session-transcript-invalid
            (constantly (assoc valid-session :session/transcript-cid "not-a-cid"))
            {:send! identity}]
           [:trusted/session-send-invalid
            (constantly valid-session)
            {:send! :serialized-function}]
           [:trusted/session-verification-failed
            (fn [_] (throw (ex-info "secret handshake" {:secret true})))
            {:send! identity}]]]
    (let [thrown (try
                   (trusted/authenticate-session! verify! :evidence transport)
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= expected (:problem (ex-data thrown))))
      (is (not (re-find #"secret" (str (ex-data thrown))))))))

(deftest send-only-session-does-not-gain-request-authority
  (let [session (trusted/authenticate-session!
                 (constantly valid-session) :evidence {:send! identity})
        thrown (try
                 (trusted/session-request! session :call)
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (trusted/authenticated-session? session))
    (is (false? (trusted/request-capable-session? session)))
    (is (= :trusted/session-request-not-supported
           (:problem (ex-data thrown))))))
