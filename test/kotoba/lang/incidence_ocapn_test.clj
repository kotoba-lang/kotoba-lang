(ns kotoba.lang.incidence-ocapn-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.capability-host :as host]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-ocapn :as ocapn]
            [kotoba.lang.incidence-port :as port]))

(def alice (incidence/typed-ref :did "did:key:z6Mkalice"))
(def room (incidence/typed-ref :uri "https://example.test/rooms/a"))
(def dataspace "dataspace:rooms/a")
(def now "2026-08-15")

(def presence
  (incidence/incidence :presence/online
                       {:room #{room} :participant #{alice}}
                       {}))

(defn connection [send!]
  {:session/id "captp-session-a"
   :session/version ocapn/captp-version
   :session/authenticated? true
   :remote/target {:ocapn/descriptor :desc/export
                   :ocapn/position 7}
   :send! send!})

(defn publish-opts [append! record!]
  {:dataspace dataspace
   :emissions [(incidence/assertion presence)]
   :requested (capabilities/make-cap port/append-kind dataspace)
   :effect-row #{port/append-effect}
   :cacao-grants [{:grant/kind port/append-kind
                   :grant/resources #{dataspace}
                   :grant/expires nil
                   :grant/id "ucan:rooms/a"}]
   :local-policy {:policy/allow {port/append-kind #{dataspace}}}
   :now now
   :record! record!
   :append! append!})

(deftest live-reference-adapts-the-guarded-port-to-one-way-captp-delivery
  (let [messages (atom [])
        reference (ocapn/connected-reference
                   (connection
                    (fn [message]
                      (swap! messages conj message)
                      {:ocapn/accepted? true
                       :ocapn/message-id "message-1"})))
        {:keys [record! entries]} (host/journal)
        result (port/publish-emissions!
                (publish-opts (ocapn/append-provider reference) record!))
        message (first @messages)
        [_ sent-dataspace sent-cid sent-bytes] (:ocapn/args message)]
    (is (:ok? result))
    (is (= 1 (count @messages)))
    (is (= ocapn/draft-profile (:ocapn/profile message)))
    (is (= :op/deliver (:ocapn/op message)))
    (is (= {:ocapn/descriptor :desc/export :ocapn/position 7}
           (:ocapn/to message)))
    (is (= 'append-incidence (first (:ocapn/args message))))
    (is (= dataspace sent-dataspace))
    (is (= (incidence/incidence-cid presence) sent-cid))
    (is (= (seq (incidence/canonical-bytes presence)) (seq sent-bytes)))
    (is (false? (:ocapn/answer-position message)))
    (is (false? (:ocapn/resolve-me message)))
    (is (= "message-1" (get-in result [:results 0 :ocapn/message-id])))
    (is (= 1 (count (entries))))
    (is (= :ok (:receipt/outcome (first (entries)))))))

(deftest locator-and-sturdyref-data-never-become-live-authority
  (doseq [data ["ocapn://peer.example.tcp/s/swiss-number"
                {:ocapn/sturdyref {:peer "peer.example"
                                   :swiss-number "swiss-number"}}
                {:session/id "captp-session-a"
                 :remote/target {:ocapn/descriptor :desc/export
                                 :ocapn/position 7}}]]
    (is (false? (ocapn/live-reference? data)))
    (is (= :ocapn/not-live-reference
           (:problem
            (ex-data
             (try (ocapn/append-provider data)
                  (catch clojure.lang.ExceptionInfo e e))))))))

(deftest connection-setup-is-closed-and-requires-authenticated-session
  (doseq [[expected opts]
          [[:ocapn/version-unsupported
            (assoc (connection identity) :session/version "future")]
           [:ocapn/session-not-authenticated
            (assoc (connection identity) :session/authenticated? false)]
           [:ocapn/target-invalid
            (assoc (connection identity)
                   :remote/target {:ocapn/descriptor :desc/answer
                                   :ocapn/position 1})]
           [:ocapn/send-port-invalid
            (assoc (connection identity) :send! :serialized-function)]
           [:ocapn/connection-fields
            (assoc (connection identity) :sturdyref "ocapn://example")]]]
    (is (= expected
           (:problem
            (ex-data
             (try (ocapn/connected-reference opts)
                  (catch clojure.lang.ExceptionInfo e e))))))))

(deftest reference-description-does-not-expose-send-authority
  (let [reference (ocapn/connected-reference (connection identity))
        description (ocapn/reference-description reference)]
    (is (= ocapn/draft-profile (:ocapn/profile description)))
    (is (= "captp-session-a" (:session/id description)))
    (is (not (contains? description :send!)))
    (is (nil? (ocapn/reference-description {:pretend :reference})))))

(deftest bootstrap-export-zero-is-a-valid-live-target
  (let [reference (ocapn/connected-reference
                   (assoc (connection identity)
                          :remote/target {:ocapn/descriptor :desc/export
                                          :ocapn/position 0}))]
    (is (ocapn/live-reference? reference))
    (is (= 0 (get-in (ocapn/reference-description reference)
                     [:remote/target :ocapn/position])))))

(deftest tampered-addressed-incidence-is-rejected-before-send
  (let [messages (atom [])
        reference (ocapn/connected-reference
                   (connection
                    (fn [message]
                      (swap! messages conj message)
                      {:ocapn/accepted? true
                       :ocapn/message-id "must-not-send"})))
        append! (ocapn/append-provider reference)
        addressed (incidence/addressed presence)
        thrown (try
                 (append! {:dataspace dataspace
                           :entry (assoc addressed :incidence/cid "bafytampered")
                           :capability (capabilities/make-cap port/append-kind
                                                              dataspace)})
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= :ocapn/incidence-invalid (:problem (ex-data thrown))))
    (is (empty? @messages))))

(deftest unconfirmed-session-send-is-an-error-receipt-not-success
  (let [reference (ocapn/connected-reference
                   (connection (constantly {:ocapn/accepted? false})))
        {:keys [record! entries]} (host/journal)
        thrown (try
                 (port/publish-emissions!
                  (publish-opts (ocapn/append-provider reference) record!))
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= :ocapn/send-unconfirmed (:problem (ex-data thrown))))
    (is (= 1 (count (entries))))
    (is (= :error (:receipt/outcome (first (entries)))))
    (is (true? (:ok? (capabilities/validate-receipt (first (entries))))))))
