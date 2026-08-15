(ns kotoba.lang.captp-runtime-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.captp-runtime :as captp]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-ocapn :as ocapn]
            [kotoba.lang.trusted-admission :as trusted]))

(def peer (incidence/typed-ref :did "did:key:z6Mkcaptppeer"))
(def transcript
  (incidence/incidence :session/transcript {:session/peer #{peer}} {}))
(def transcript-cid (incidence/incidence-cid transcript))

(def local-start
  (captp/start-session-frame
   (byte-array [1 2 3])
   (captp/syrup-record 'ocapn-peer ['tcp "local.example" false])
   (byte-array [4 5 6])))

(def remote-start
  (captp/start-session-frame
   (byte-array [7 8 9])
   (captp/syrup-record 'ocapn-peer ['tcp "remote.example" false])
   (byte-array [10 11 12])))

(def verified-session
  {:session/valid? true
   :session/id "captp-runtime-a"
   :session/version trusted/captp-version
   :session/peer peer
   :session/transcript-cid transcript-cid})

(defn open-runtime
  ([] (open-runtime nil (captp/session-registry)))
  ([exchange!] (open-runtime exchange! (captp/session-registry)))
  ([exchange! registry]
   (let [written (atom [])
         next-id (atom 0)
         opts (cond->
               {:verify! (fn [{:keys [handshake local-start remote-start]}]
                           (when-not (= :noise-ik handshake)
                             (throw (ex-info "bad handshake" {})))
                           (when-not (= 'op:start-session
                                        (:syrup/record local-start))
                             (throw (ex-info "bad local start" {})))
                           (when-not (= 'op:start-session
                                        (:syrup/record remote-start))
                             ;; This branch is unreachable; it also makes sure
                             ;; the verifier consumes the supplied evidence.
                             (throw (ex-info "bad start" {})))
                           verified-session)
                :handshake :noise-ik
                :local-start local-start
                :remote-start remote-start
                :registry registry
                :write-frame!
                (fn [encoded]
                  (swap! written conj (captp/syrup-decode encoded))
                  {:netlayer/accepted? true
                   :netlayer/message-id (str "wire-" (swap! next-id inc))})}
                exchange! (assoc :exchange-frame! exchange!))]
     {:runtime (captp/open-session! opts)
      :written written})))

(deftest syrup-codec-is-canonical-and-round-trips-captp-values
  (doseq [value [true false 0 72 -5
                 "björn" 'update
                 [1 "two" false]
                 #{3 1 2}
                 {"name" "Alice" "age" 30}
                 (captp/syrup-record 'desc:export [7])
                 (byte-array [0 127 -1])]]
    (let [encoded (captp/syrup-encode value)
          decoded (captp/syrup-decode encoded)]
      (if (bytes? value)
        (is (= (seq value) (seq decoded)))
        (is (= value decoded)))
      (is (= (seq encoded) (seq (captp/syrup-encode decoded))))))
  (is (= "<11'desc:export7+>"
         (String. (captp/syrup-encode
                   (captp/syrup-record 'desc:export [7])) "UTF-8")))
  (is (= :captp/syrup-noncanonical
         (:problem
          (ex-data
           (try (captp/syrup-decode (.getBytes "01+" "UTF-8"))
                (catch clojure.lang.ExceptionInfo e e)))))))

(deftest session-opens-only-after-start-session-verification
  (let [{:keys [runtime written]} (open-runtime)
        description (captp/runtime-description runtime)
        session (captp/authenticated-session runtime)]
    (is (captp/runtime? runtime))
    (is (trusted/authenticated-session? session))
    (is (false? (trusted/request-capable-session? session)))
    (is (= :active (:captp/phase description)))
    (is (= "captp-runtime-a" (:session/id description)))
    (is (= ['op:start-session] (mapv :syrup/record @written))))
  (let [called (atom 0)
        thrown (try
                 (captp/open-session!
                  {:verify! (constantly verified-session)
                   :handshake :noise-ik
                   :local-start (captp/syrup-record 'op:start-session ["0"])
                   :remote-start remote-start
                   :registry (captp/session-registry)
                   :write-frame! (fn [_] (swap! called inc))})
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= :captp/start-session-invalid (:problem (ex-data thrown))))
    (is (zero? @called))))

(deftest registry-enforces-one-active-session-per-peer
  (let [registry (captp/session-registry)
        first-runtime (:runtime (open-runtime nil registry))
        thrown (try
                 (open-runtime nil registry)
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= :captp/duplicate-peer-session (:problem (ex-data thrown))))
    (captp/abort! first-runtime "replace session")
    (is (captp/runtime? (:runtime (open-runtime nil registry))))))

(deftest runtime-session-drives-the-existing-ocapn-live-reference
  (let [{:keys [runtime written]} (open-runtime)
        reference (ocapn/connected-reference
                   {:session (captp/authenticated-session runtime)
                    :remote/target {:ocapn/descriptor :desc/export
                                    :ocapn/position 7}})
        result ((ocapn/append-provider reference)
                {:dataspace "dataspace:room/a"
                 :entry (incidence/assertion
                         (incidence/incidence
                          :presence/online
                          {:participant #{peer}}
                          {}))
                 :capability
                 (capabilities/make-cap :host/ledger-append
                                        "dataspace:room/a")})
        frame (last @written)]
    (is (= 'op:deliver (:syrup/record frame)))
    (is (= (captp/descriptor 'desc:export 7)
           (first (:syrup/fields frame))))
    (is (= "wire-2" (:ocapn/message-id result)))
    (is (= "captp-runtime-a" (:ocapn/session-id result)))))

(deftest request-allocates-answer-and-resolver-settles-and-gcs
  (let [receipt (incidence/append-durable-receipt
                 "dataspace:room/a"
                 (incidence/assertion
                  (incidence/incidence :presence/online
                                       {:participant #{peer}} {})))
        seen (atom nil)
        exchange!
        (fn [encoded]
          (let [frame (captp/syrup-decode encoded)
                [_ _ answer resolve-me] (:syrup/fields frame)
                [resolver] (:syrup/fields resolve-me)]
            (reset! seen {:frame frame :answer answer :resolver resolver})
            [(captp/syrup-encode
              (captp/syrup-record
               'op:deliver
               [(captp/descriptor 'desc:export resolver)
                ['fulfill receipt] false false]))
             (captp/syrup-encode
              (captp/gc-exports-frame [resolver] [1]))]))
        {:keys [runtime written]} (open-runtime exchange!)
        reference (ocapn/connected-reference
                   {:session (captp/authenticated-session runtime)
                    :remote/target {:ocapn/descriptor :desc/export
                                    :ocapn/position 9}})
        request {:dataspace "dataspace:room/a"
                 :entry (incidence/assertion
                         (incidence/incidence :presence/online
                                              {:participant #{peer}} {}))
                 :capability
                 (capabilities/make-cap :host/ledger-append
                                        "dataspace:room/a")}
        result ((ocapn/durable-append-provider reference) request)
        description (captp/runtime-description runtime)]
    (is (= 1 (:answer @seen)))
    (is (= 1 (:resolver @seen)))
    (is (= 'op:deliver (get-in @seen [:frame :syrup/record])))
    (is (true? (:ocapn/remote-durable? result)))
    (is (= (:incidence/cid receipt) (:receipt/cid result)))
    (is (= 0 (:captp/answers description)))
    (is (= 1 (:captp/exports description)))
    (is (= 'op:gc-answers (:syrup/record (last @written))))))

(deftest abort-severs-session-and-clears-session-state
  (let [{:keys [runtime written]} (open-runtime)
        result (captp/abort! runtime "normal shutdown")
        thrown (try
                 (trusted/session-send!
                  (captp/authenticated-session runtime)
                  {:ocapn/profile captp/profile
                   :ocapn/op :op/deliver
                   :ocapn/to {:ocapn/descriptor :desc/export
                              :ocapn/position 0}
                   :ocapn/args []
                   :ocapn/answer-position false
                   :ocapn/resolve-me false})
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (:ocapn/accepted? result))
    (is (= 'op:abort (:syrup/record (last @written))))
    (is (= :aborted (:captp/phase (captp/runtime-description runtime))))
    (is (= :captp/session-not-active (:problem (ex-data thrown))))))

(deftest imported-descriptors-are-wire-counted-and-releasable
  (let [seen (atom nil)
        exchange!
        (fn [encoded]
          (let [frame (captp/syrup-decode encoded)
                [_ _ _ resolve-me] (:syrup/fields frame)
                [resolver] (:syrup/fields resolve-me)]
            [(captp/syrup-encode
              (captp/syrup-record
               'op:deliver
               [(captp/descriptor 'desc:export resolver)
                ['fulfill (captp/descriptor 'desc:import-object 44)]
                false false]))
             (captp/syrup-encode
              (captp/gc-exports-frame [resolver] [1]))]))
        {:keys [runtime written]} (open-runtime exchange!)
        settlement (trusted/session-request!
                    (captp/authenticated-session runtime)
                    {:ocapn/profile captp/profile
                     :ocapn/to {:ocapn/descriptor :desc/export
                                :ocapn/position 2}
                     :ocapn/args ['get-child]
                     :ocapn/result :settled})]
    (is (= :fulfilled (:ocapn/status settlement)))
    (is (= 2 (:captp/imports (captp/runtime-description runtime))))
    (reset! seen (captp/release-imports! runtime [44]))
    (is (:ocapn/accepted? @seen))
    (is (= 1 (:captp/imports (captp/runtime-description runtime))))
    (is (= 'op:gc-exports (:syrup/record (last @written))))))

(deftest exchange-errors-abort-and-do-not-leak-host-debug-data
  (let [registry (captp/session-registry)
        {:keys [runtime]}
        (open-runtime
         (fn [_]
           (throw (ex-info "secret netlayer stack" {:secret "token"})))
         registry)
        thrown (try
                 (trusted/session-request!
                  (captp/authenticated-session runtime)
                  {:ocapn/profile captp/profile
                   :ocapn/to {:ocapn/descriptor :desc/export
                              :ocapn/position 2}
                   :ocapn/args ['query]
                   :ocapn/result :settled})
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= {:problem :captp/exchange-failed} (ex-data thrown)))
    (is (not (re-find #"secret|token" (str (ex-data thrown)))))
    (is (= :aborted (:captp/phase (captp/runtime-description runtime))))
    (is (captp/runtime? (:runtime (open-runtime nil registry))))))

(deftest remote-break-payload-is-not-exposed
  (let [exchange!
        (fn [encoded]
          (let [frame (captp/syrup-decode encoded)
                [_ _ _ resolve-me] (:syrup/fields frame)
                [resolver] (:syrup/fields resolve-me)]
            [(captp/syrup-encode
              (captp/syrup-record
               'op:deliver
               [(captp/descriptor 'desc:export resolver)
                ['break {:remote/debug "secret trace"}]
                false false]))
             (captp/syrup-encode
              (captp/gc-exports-frame [resolver] [1]))]))
        {:keys [runtime]} (open-runtime exchange!)
        settlement (trusted/session-request!
                    (captp/authenticated-session runtime)
                    {:ocapn/profile captp/profile
                     :ocapn/to {:ocapn/descriptor :desc/export
                                :ocapn/position 2}
                     :ocapn/args ['query]
                     :ocapn/result :settled})]
    (is (= {:ocapn/status :broken} settlement))
    (is (not (re-find #"secret" (str settlement))))))

(deftest malformed-inbound-frame-aborts-the-session
  (let [{:keys [runtime]} (open-runtime)
        thrown (try
                 (captp/receive! runtime (.getBytes "01+" "UTF-8"))
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= :captp/syrup-noncanonical (:problem (ex-data thrown))))
    (is (= :aborted (:captp/phase (captp/runtime-description runtime))))))

(deftest deferred-answers-pipeline-derive-listen-and-settle
  (let [{:keys [runtime written]} (open-runtime)
        factory (captp/deferred-request!
                 runtime
                 {:ocapn/to {:ocapn/descriptor :desc/export
                             :ocapn/position 5}
                  :ocapn/args ['make-car-factory]})
        car (captp/pipeline-request! factory ['make-car])
        field (captp/get-answer! car "engine")
        indexed (captp/index-answer! field 0)
        untagged (captp/untag-answer! indexed "horsepower")]
    (is (every? captp/deferred-answer? [factory car field indexed untagged]))
    (is (= ['op:start-session 'op:deliver 'op:deliver 'op:get 'op:index
            'op:untag]
           (mapv :syrup/record @written)))
    (is (= (captp/descriptor 'desc:answer 1)
           (first (:syrup/fields (nth @written 2)))))
    (is (= :captp/answer-unresolved
           (:problem (ex-data
                      (try (captp/settlement! car)
                           (catch clojure.lang.ExceptionInfo e e))))))
    (captp/listen! car)
    (let [listen (last @written)
          [_ listener] (:syrup/fields listen)
          [resolver] (:syrup/fields listener)]
      (is (= 'op:listen (:syrup/record listen)))
      (captp/receive!
       runtime
       (captp/syrup-encode
        (captp/syrup-record
         'op:deliver
         [(captp/descriptor 'desc:export resolver)
          ['fulfill "car-ready"] false false])))
      (is (= {:ocapn/status :fulfilled :ocapn/value "car-ready"}
             (captp/settlement! car)))
      (is (= 'op:gc-answers (:syrup/record (last @written)))))))

(deftest deferred-answer-listener-and-shape-violations-fail-closed
  (let [{:keys [runtime]} (open-runtime)
        pending (captp/deferred-request!
                 runtime {:ocapn/to {:ocapn/descriptor :desc/export
                                     :ocapn/position 1}
                          :ocapn/args ['query]})]
    (captp/listen! pending)
    (is (= :captp/listener-already-attached
           (:problem (ex-data
                      (try (captp/listen! pending)
                           (catch clojure.lang.ExceptionInfo e e))))))
    (is (= :captp/index-invalid
           (:problem (ex-data
                      (try (captp/index-answer! pending -1)
                           (catch clojure.lang.ExceptionInfo e e))))))
    (is (= :captp/deferred-request-invalid
           (:problem (ex-data
                      (try (captp/deferred-request! runtime {:ocapn/args []})
                           (catch clojure.lang.ExceptionInfo e e))))))))

(deftest locators-remain-inert-until-a-live-resolver-admits-a-target
  (let [locator (captp/parse-locator
                 "ocapn://alice.example.tcp/s/calendar?port=443&host=a")
        {:keys [runtime]} (open-runtime)
        resolved (captp/resolve-sturdyref!
                  (fn [description]
                    (is (= locator description))
                    {:session (captp/authenticated-session runtime)
                     :remote/target {:ocapn/descriptor :desc/export
                                     :ocapn/position 12}})
                  "ocapn://alice.example.tcp/s/calendar?port=443&host=a")]
    (is (= :sturdyref (:locator/kind locator)))
    (is (= 'tcp (:locator/transport locator)))
    (is (= "alice.example" (:locator/designator locator)))
    (is (= {"host" "a" "port" "443"} (:locator/hints locator)))
    (is (trusted/authenticated-session? (:session resolved)))
    (is (= :captp/locator-resolver-required
           (:problem
            (ex-data
             (try (captp/resolve-sturdyref! nil
                                            "ocapn://alice.tcp/s/calendar")
                  (catch clojure.lang.ExceptionInfo e e))))))
    (is (= :captp/locator-component-invalid
           (:problem
            (ex-data
             (try (captp/parse-locator "ocapn://alice.tcp/s/bad%XX")
                  (catch clojure.lang.ExceptionInfo e e))))))))
