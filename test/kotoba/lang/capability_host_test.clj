(ns kotoba.lang.capability-host-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-host :as host]
            [kotoba.lang.capability-values :as caps]))

(def manifest-path "lang/capability-conformance/manifest.edn")

(defn read-edn
  [path]
  (edn/read-string (slurp (io/file path))))

;; lang/capability-conformance/manifest.edn is stored as Datomic/Datascript
;; tx-data (see schema.edn). See scripts/check-capability-values.bb for the
;; same reconstitution -- the manifest's plain :cases key was renamed to
;; :kotoba.lang.capability.conformance/cases and blob-stringified, while the
;; already-namespaced :version key was left untouched.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-capability-conformance-manifest [tx-data]
  (let [e (dissoc (first tx-data) :db/id)]
    {:kotoba.lang.capability.conformance/version
     (:kotoba.lang.capability.conformance/version e)
     :cases (unblob (:kotoba.lang.capability.conformance/cases e))}))

(def graph-a "bafygrapha11111111111111111111111111111111111111111111111")
(def graph-b "bafygraphb22222222222222222222222222222222222222222222222")
(def now "2026-07-02")

(defn grant
  [kind resources expires id]
  {:grant/kind kind
   :grant/resources resources
   :grant/expires expires
   :grant/id id})

(deftest grant-path-invokes-handler-with-concrete-narrowed-cap
  (let [seen (atom nil)
        outcome (host/guard-call
                 {:call :kotoba.host/graph-write
                  :requested (caps/graph-write-cap :any)
                  :cacao-grants [(grant :graph-write #{graph-a} "2027-01-01" "g1")]
                  :local-policy {:policy/allow {:graph-write #{graph-a graph-b}}}
                  :now now
                  :handler (fn [concrete]
                             (reset! seen concrete)
                             :provider-result)})]
    (is (true? (:kotoba.host/ok? outcome)))
    (is (= :provider-result (:kotoba.host/result outcome)))
    (testing "handler receives the concrete capability, not the requested one"
      (is (caps/capability? @seen))
      (is (= graph-a (:cap/resource @seen)))
      (is (= ["g1"] (:cap/provenance @seen))))
    (testing "receipt embeds the concrete capability and validates"
      (let [receipt (:kotoba.host/receipt outcome)]
        (is (true? (:ok? (caps/validate-receipt receipt))))
        (is (= :ok (:receipt/outcome receipt)))
        (is (= @seen (:receipt/cap receipt)))
        (is (= :kotoba.host/graph-write (:receipt/call receipt)))
        (is (= now (:receipt/at receipt)))))))

(deftest denial-never-invokes-handler-and-records-denial-receipt
  (let [calls (atom 0)
        outcome (host/guard-call
                 {:call :kotoba.host/graph-write
                  :requested (caps/graph-write-cap graph-b)
                  :cacao-grants [(grant :graph-write #{graph-a} nil "g1")]
                  :local-policy {:policy/allow {:graph-write #{graph-a}}}
                  :now now
                  :handler (fn [_] (swap! calls inc))})]
    (is (false? (:kotoba.host/ok? outcome)))
    (is (= :empty-intersection (:kotoba.host/denied outcome)))
    (is (zero? @calls))
    (let [receipt (:kotoba.host/receipt outcome)]
      (is (caps/denial-receipt? receipt))
      (is (= :empty-intersection (:receipt/denied receipt)))
      (is (= :denied (:receipt/outcome receipt)))
      (is (true? (:ok? (caps/validate-receipt receipt)))))))

(deftest journal-appends-receipts-in-order-for-mixed-sequences
  (let [{:keys [record! entries]} (host/journal)
        run! (fn [requested grants]
               (try
                 (host/guard-call {:call :kotoba.host/graph-read
                                   :requested requested
                                   :cacao-grants grants
                                   :local-policy {:policy/allow {:graph-read #{graph-a}}}
                                   :now now
                                   :record! record!
                                   :handler (constantly :ok)})
                 (catch Exception _ nil)))]
    (run! (caps/graph-read-cap graph-a) [(grant :graph-read #{graph-a} nil "g1")])
    (run! (caps/graph-read-cap graph-b) [(grant :graph-read #{graph-a} nil "g1")])
    (run! (caps/graph-read-cap :any) [(grant :graph-read #{graph-a} "2020-01-01" "g-stale")])
    (run! (caps/graph-read-cap graph-a) [(grant :graph-read #{:any} nil "g-root")])
    (let [receipts (entries)]
      (is (= 4 (count receipts)))
      (is (= [:ok :denied :denied :ok] (mapv :receipt/outcome receipts)))
      (is (= [nil :empty-intersection :expired nil]
             (mapv :receipt/denied receipts)))
      (is (every? #(true? (:ok? (caps/validate-receipt %))) receipts))
      (testing "grant receipts carry the concrete capability"
        (is (= graph-a (get-in receipts [0 :receipt/cap :cap/resource])))
        (is (= graph-a (get-in receipts [3 :receipt/cap :cap/resource])))))))

(deftest handler-exception-is-recorded-and-rethrown
  (let [{:keys [record! entries]} (host/journal)
        thrown (try
                 (host/guard-call
                  {:call :kotoba.host/graph-write
                   :requested (caps/graph-write-cap graph-a)
                   :cacao-grants [(grant :graph-write #{graph-a} nil "g1")]
                   :local-policy {:policy/allow {:graph-write #{graph-a}}}
                   :now now
                   :record! record!
                   :handler (fn [_] (throw (ex-info "provider blew up" {:boom true})))})
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (some? thrown))
    (is (= "provider blew up" (ex-message thrown)))
    (let [receipts (entries)
          receipt (first receipts)]
      (is (= 1 (count receipts)))
      (is (= :error (:receipt/outcome receipt)))
      (is (= "provider blew up" (:receipt/error receipt)))
      (is (true? (:ok? (caps/validate-receipt receipt))))
      (is (= graph-a (get-in receipt [:receipt/cap :cap/resource]))))))

(deftest record-bang-is-optional
  (let [grant-outcome (host/guard-call
                       {:call :kotoba.host/graph-read
                        :requested (caps/graph-read-cap graph-a)
                        :cacao-grants [(grant :graph-read #{graph-a} nil "g1")]
                        :local-policy {:policy/allow {:graph-read #{graph-a}}}
                        :now now
                        :handler (constantly 7)})
        denial-outcome (host/guard-call
                        {:call :kotoba.host/graph-read
                         :requested (caps/graph-read-cap graph-b)
                         :cacao-grants [(grant :graph-read #{graph-a} nil "g1")]
                         :local-policy {:policy/allow {:graph-read #{graph-a}}}
                         :now now
                         :handler (constantly 7)})]
    (is (true? (:kotoba.host/ok? grant-outcome)))
    (is (= 7 (:kotoba.host/result grant-outcome)))
    (is (some? (:kotoba.host/receipt grant-outcome)))
    (is (false? (:kotoba.host/ok? denial-outcome)))
    (is (some? (:kotoba.host/receipt denial-outcome)))))

(deftest malformed-requested-capability-fails-closed-with-receipt
  (let [calls (atom 0)
        outcome (host/guard-call
                 {:call :kotoba.host/graph-read
                  :requested graph-a
                  :cacao-grants [(grant :graph-read #{graph-a} nil "g1")]
                  :local-policy {:policy/allow {:graph-read #{graph-a}}}
                  :now now
                  :handler (fn [_] (swap! calls inc))})]
    (is (false? (:kotoba.host/ok? outcome)))
    (is (= :malformed-requested (:kotoba.host/denied outcome)))
    (is (zero? @calls))
    (is (true? (:ok? (caps/validate-receipt (:kotoba.host/receipt outcome)))))))

(deftest host-kind-dispatch-uses-registered-host-kinds
  (let [outcome (host/guard-call
                 {:call :kotoba.host/clipboard-read
                  :requested (caps/make-cap :host/clipboard-read :any)
                  :cacao-grants [(grant :host/clipboard-read #{"clipboard:system"}
                                        nil "policy:clipboard/text")]
                  :local-policy {:policy/allow {:host/clipboard-read
                                                #{"clipboard:system"}}}
                  :now now
                  :handler (fn [concrete] (:cap/resource concrete))})]
    (is (true? (:kotoba.host/ok? outcome)))
    (is (= "clipboard:system" (:kotoba.host/result outcome)))
    (is (= "clipboard:system"
           (get-in outcome [:kotoba.host/receipt :receipt/cap :cap/resource])))))

(deftest host-dispatch-conformance-fixtures-match-contract
  (let [manifest (reconstitute-capability-conformance-manifest (read-edn manifest-path))
        host-cases (filter #(= :host-dispatch (:type %)) (:cases manifest))]
    (is (seq host-cases))
    (doseq [tc host-cases
            :let [data (read-edn (str "lang/capability-conformance/" (:file tc)))
                  result (host/check-case tc data)]]
      (is (:ok? result) (str (:id tc) " -> " (pr-str (:actual result)))))))
