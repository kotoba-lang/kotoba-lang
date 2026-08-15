(ns kotoba.lang.incidence-port-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.capability-host :as host]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-port :as port]
            [kotoba.lang.trusted-admission :as trusted]))

(def alice (incidence/typed-ref :did "did:key:z6Mkalice"))
(def room (incidence/typed-ref :uri "https://example.test/rooms/a"))
(def dataspace "dataspace:rooms/a")
(def now "2026-08-15")

(def presence
  (incidence/incidence :presence/online
                       {:room #{room} :participant #{alice}}
                       {}))

(def status
  (incidence/incidence :presence/status
                       {:participant #{alice}}
                       {:facts {:status :available}}))

(defn requested-cap []
  (capabilities/make-cap port/append-kind dataspace))

(defn verified-delegation
  [resources]
  (trusted/verify-delegation!
   (constantly
    {:chain/valid? true
     :chain/problems []
     :chain/root-iss "did:key:z6Mkroot"
     :chain/holder "did:key:z6Mkholder"
     :chain/resources resources
     :chain/expires nil
     :chain/depth 1})
   :test-evidence))

(defn publish-opts [emissions append!]
  {:dataspace dataspace
   :emissions emissions
   :requested (requested-cap)
   :effect-row #{port/append-effect}
   :verified-delegation
   (verified-delegation
    #{(str "kotoba://cap/host/ledger-append/" dataspace)})
   :local-policy {:policy/allow {port/append-kind #{dataspace}}}
   :now now
   :append! append!})

(deftest facet-emissions-reach-only-the-capability-guarded-provider
  (let [first-step (incidence/facet-assert (incidence/facet alice) presence)
        second-step (incidence/facet-assert (:facet first-step) status)
        emissions (into (:emit first-step) (:emit second-step))
        calls (atom [])
        {:keys [record! entries]} (host/journal)
        result (port/publish-emissions!
                (assoc (publish-opts
                        emissions
                        (fn [request]
                          (swap! calls conj request)
                          (:incidence/cid (:entry request))))
                       :record! record!))]
    (is (:ok? result))
    (is (= (mapv :incidence/cid emissions) (:results result)))
    (is (= 2 (count @calls)))
    (is (= 2 (count (:receipts result))))
    (is (= (:receipts result) (entries)))
    (is (every? #(= dataspace (:dataspace %)) @calls))
    (is (every? #(= dataspace (get-in % [:capability :cap/resource])) @calls))
    (is (every? #(= ["cacao:did:key:z6Mkroot:0"]
                    (get-in % [:capability :cap/provenance]))
                @calls))
    (is (every? #(true? (:ok? (capabilities/validate-receipt %)))
                (:receipts result)))))

(deftest missing-effect-or-delegation-never-reaches-provider
  (doseq [[expected changes]
          [[:effect-not-declared {:effect-row #{}}]
           [:empty-intersection
            {:verified-delegation (verified-delegation #{})}]
           [:empty-intersection
            {:local-policy {:policy/allow {port/append-kind
                                            #{"dataspace:somewhere-else"}}}}]]]
    (let [calls (atom 0)
          {:keys [record! entries]} (host/journal)
          result (port/publish-emissions!
                  (merge (publish-opts
                          [(incidence/assertion presence)]
                          (fn [_] (swap! calls inc)))
                         changes
                         {:record! record!}))]
      (is (false? (:ok? result)))
      (is (= expected (:reason result)))
      (is (zero? @calls))
      (is (= 1 (count (entries))))
      (is (= :denied (:receipt/outcome (first (entries))))))))

(deftest serialized-grants-cannot-cross-the-publication-boundary
  (let [calls (atom 0)
        {:keys [record! entries]} (host/journal)
        result (port/publish-emissions!
                (assoc
                 (publish-opts [(incidence/assertion presence)]
                               (fn [_] (swap! calls inc)))
                 :verified-delegation
                 [{:grant/kind port/append-kind
                   :grant/resources #{dataspace}}]
                 :record! record!))]
    (is (false? (:ok? result)))
    (is (= :dataspace/delegation-not-verified (:reason result)))
    (is (zero? @calls))
    (is (empty? (entries)))))

(deftest malformed-or-substituted-emissions-fail-before-any-effect
  (let [entry (incidence/assertion presence)
        substituted (assoc-in entry
                              [:incidence/block :incidence/facts :status]
                              :forged)
        calls (atom 0)
        {:keys [record! entries]} (host/journal)
        result (port/publish-emissions!
                (assoc (publish-opts [substituted]
                                     (fn [_] (swap! calls inc)))
                       :record! record!))]
    (is (false? (:ok? result)))
    (is (= :dataspace/emission-invalid (:reason result)))
    (is (zero? @calls))
    (is (empty? (entries)))))

(deftest requested-scope-is-exact-and-serialized-data-is-not-enough
  (let [calls (atom 0)
        emissions [(incidence/assertion presence)]]
    (doseq [[expected changes]
            [[:dataspace/capability-kind
              {:requested (capabilities/make-cap :host/http dataspace)}]
             [:dataspace/capability-resource
             {:requested (capabilities/make-cap port/append-kind :any)}]
             [:dataspace/provider-invalid {:append! :pretend-authority}]
             [:dataspace/delegation-not-verified
              {:verified-delegation {:pretend :verified}}]
             [:dataspace/receipt-date-invalid {:now "today"}]
             [:dataspace/recorder-invalid {:record! :pretend-recorder}]]]
      (let [result (port/publish-emissions!
                    (merge (publish-opts emissions
                                         (fn [_] (swap! calls inc)))
                           changes))]
        (is (false? (:ok? result)))
        (is (= expected (:reason result)))))
    (is (zero? @calls))))

(deftest duplicate-batch-is-rejected-and-empty-batch-is-a-no-op
  (let [entry (incidence/assertion presence)
        calls (atom 0)
        duplicate (port/publish-emissions!
                   (publish-opts [entry entry] (fn [_] (swap! calls inc))))
        empty-result (port/publish-emissions!
                      (publish-opts [] (fn [_] (swap! calls inc))))]
    (is (= :dataspace/emission-duplicate (:reason duplicate)))
    (is (:ok? empty-result))
    (is (empty? (:receipts empty-result)))
    (is (zero? @calls))))

(deftest provider-error-is-receipted-and-rethrown
  (let [{:keys [record! entries]} (host/journal)
        thrown (try
                 (port/publish-emissions!
                  (assoc (publish-opts
                          [(incidence/assertion presence)]
                          (fn [_]
                            (throw (ex-info "append failed" {:provider true}))))
                         :record! record!))
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= "append failed" (ex-message thrown)))
    (is (= 1 (count (entries))))
    (is (= :error (:receipt/outcome (first (entries)))))
    (is (true? (:ok? (capabilities/validate-receipt (first (entries))))))))
