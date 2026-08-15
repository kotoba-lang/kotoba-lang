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

(defn request-connection [request!]
  (assoc (connection identity) :request! request!))

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
           [:ocapn/request-port-invalid
            (assoc (connection identity) :request! :serialized-function)]
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
    (is (not (contains? description :request!)))
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

(deftest durable-receipt-is-deterministic-and-bound-to-the-incidence
  (let [entry (incidence/assertion presence)
        receipt-a (ocapn/durable-receipt dataspace entry)
        receipt-b (ocapn/durable-receipt dataspace entry)
        block (:incidence/block receipt-a)]
    (is (= receipt-a receipt-b))
    (is (:ok? (incidence/verify-addressed receipt-a)))
    (is (= ocapn/durable-receipt-kind (:incidence/kind block)))
    (is (= #{(:incidence/cid entry)} (:incidence/parents block)))
    (is (= #{(incidence/typed-ref :cid (:incidence/cid entry))}
           (get-in block [:incidence/roles :receipt/subject])))
    (is (= {:receipt/version ocapn/durable-receipt-version
            :receipt/status :durable
            :receipt/dataspace dataspace}
           (:incidence/facts block)))))

(deftest durable-receipt-shape-is-part-of-incidence-semantics
  (let [entry (incidence/assertion presence)
        valid-block (:incidence/block
                     (incidence/append-durable-receipt dataspace entry))
        invalid-block (assoc-in valid-block
                                [:incidence/facts :receipt/status]
                                :queued)
        thrown (try
                 (incidence/incidence-cid invalid-block)
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= :dataspace/append-durable (:problem (ex-data thrown))))))

(deftest request-capable-reference-returns-a-bound-durable-receipt
  (let [calls (atom [])
        entry (incidence/assertion presence)
        remote-receipt (ocapn/durable-receipt dataspace entry)
        reference (ocapn/connected-reference
                   (request-connection
                    (fn [call]
                      (swap! calls conj call)
                      {:ocapn/status :fulfilled
                       :ocapn/value remote-receipt})))
        {:keys [record! entries]} (host/journal)
        result (port/publish-emissions!
                (publish-opts (ocapn/durable-append-provider reference)
                              record!))
        call (first @calls)]
    (is (ocapn/request-capable-reference? reference))
    (is (:ok? result))
    (is (= 1 (count @calls)))
    (is (= :settled (:ocapn/result call)))
    (is (= {:ocapn/descriptor :desc/export :ocapn/position 7}
           (:ocapn/to call)))
    (is (= 'append-incidence (first (:ocapn/args call))))
    (is (= (:incidence/cid entry) (nth (:ocapn/args call) 2)))
    (is (true? (get-in result [:results 0 :ocapn/remote-durable?])))
    (is (= (:incidence/cid remote-receipt)
           (get-in result [:results 0 :receipt/cid])))
    (is (= :ok (:receipt/outcome (first (entries)))))))

(deftest one-way-reference-cannot-claim-remote-durability
  (let [reference (ocapn/connected-reference (connection identity))
        thrown (try
                 (ocapn/durable-append-provider reference)
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (false? (ocapn/request-capable-reference? reference)))
    (is (= :ocapn/request-not-supported (:problem (ex-data thrown))))))

(deftest mismatched-or-tampered-durable-receipt-is-rejected
  (let [entry (incidence/assertion presence)
        wrong-space (ocapn/durable-receipt "dataspace:rooms/b" entry)
        tampered (assoc (ocapn/durable-receipt dataspace entry)
                        :incidence/cid "bafytampered")]
    (doseq [[expected receipt]
            [[:ocapn/durable-receipt-mismatch wrong-space]
             [:ocapn/durable-receipt-address-invalid tampered]]]
      (let [reference (ocapn/connected-reference
                       (request-connection
                        (constantly {:ocapn/status :fulfilled
                                     :ocapn/value receipt})))
            {:keys [record! entries]} (host/journal)
            thrown (try
                     (port/publish-emissions!
                      (publish-opts (ocapn/durable-append-provider reference)
                                    record!))
                     nil
                     (catch clojure.lang.ExceptionInfo e e))]
        (is (= expected (:problem (ex-data thrown))))
        (is (= :error (:receipt/outcome (first (entries)))))))))

(deftest broken-remote-settlement-does-not-leak-remote-debug-data
  (let [reference (ocapn/connected-reference
                   (request-connection
                    (constantly {:ocapn/status :broken
                                 :ocapn/reason "remote stack and secret"})))
        {:keys [record!]} (host/journal)
        thrown (try
                 (port/publish-emissions!
                  (publish-opts (ocapn/durable-append-provider reference)
                                record!))
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= {:problem :ocapn/remote-broken} (ex-data thrown)))
    (is (not (re-find #"secret" (str (ex-data thrown)))))))
