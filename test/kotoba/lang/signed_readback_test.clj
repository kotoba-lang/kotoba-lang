(ns kotoba.lang.signed-readback-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-ocapn :as ocapn]
            [kotoba.lang.incidence-port :as port]
            [kotoba.lang.signed-readback :as readback]
            [kotoba.lang.trusted-admission :as trusted]))

(def org-did "did:key:z6Mkorganization")
(def org-ref (incidence/typed-ref :did org-did))
(def peer (incidence/typed-ref :did "did:key:z6Mkpeer"))
(def dataspace "dataspace:org/example")
(def now-ms 1000)
(def presence
  (incidence/addressed
   (incidence/incidence :presence/online {:participant #{peer}} {})))
(def constitution
  (incidence/addressed
   (incidence/constitute :organization #{org-ref}
                         {:facts {:organization/name "Example"}})))

(defn binding-result []
  {:binding/valid? true
   :binding/constitution-cid (:incidence/cid constitution)
   :binding/kind :organization
   :binding/did org-did
   :binding/authorized-peers #{peer}
   :binding/verification-relationship :assertionMethod
   :binding/verification-method (str org-did "#assertion-1")
   :binding/evidence-cid (:incidence/cid constitution)})

(defn org-binding []
  (trusted/verify-organization-binding!
   (constantly (binding-result)) constitution :did-document-and-proof))

(defn session
  ([] (session peer))
  ([session-peer]
   (trusted/authenticate-session!
    (constantly
     {:session/valid? true
      :session/id "captp-org-a"
      :session/version trusted/captp-version
      :session/peer session-peer
      :session/transcript-cid (:incidence/cid presence)})
    :handshake
    {:send! identity :request! identity})))

(defn proof-result [input]
  {:proof/valid? true
   :proof/payload-cid (:payload-cid input)
   :proof/issuer (:issuer input)
   :proof/verification-method (:verification-method input)
   :proof/relationship (:relationship input)})

(defn verifier
  ([] (verifier (constantly "challenge-a") proof-result))
  ([challenge! verify!]
   (readback/verifier
    {:organization-binding (org-binding)
     :session (session)
     :challenge! challenge!
     :clock! (constantly now-ms)
     :verify! verify!
     :max-age-ms 500})))

(defn request []
  {:dataspace dataspace
   :entry presence
   :capability (capabilities/make-cap port/append-kind dataspace)})

(defn envelope [verifier challenge issued expires]
  {:receipt/statement
   (readback/statement verifier (request) challenge issued expires)
   :receipt/proof {:type "DataIntegrityProof"
                   :proofValue "test-signature"}})

(defn raw-statement
  [v challenge overrides]
  (let [info (readback/verifier-description v)
        session-info (:session info)
        subject-cid (:incidence/cid presence)]
    (incidence/signed-readback-statement
     (merge {:dataspace dataspace
             :subject-cid subject-cid
             :readback-cid subject-cid
             :constitution-cid (:binding/constitution-cid info)
             :issuer (:binding/did info)
             :peer (:session/peer session-info)
             :challenge challenge
             :issued-at-ms 900
             :expires-at-ms 1100
             :session-transcript-cid (:session/transcript-cid session-info)
             :verification-method (:binding/verification-method info)
             :binding-evidence-cid (:binding/evidence-cid info)}
            overrides))))

(deftest constitution-did-binding-is-opaque-and-bound-to-the-addressed-root
  (let [admitted (org-binding)
        info (trusted/organization-binding-description admitted)]
    (is (trusted/verified-organization-binding? admitted))
    (is (= (:incidence/cid constitution) (:binding/constitution-cid info)))
    (is (= org-did (:binding/did info)))
    (is (false? (trusted/verified-organization-binding? info)))
    (doseq [[problem changed]
            [[:trusted/organization-binding-constitution-mismatch
              (assoc (binding-result) :binding/constitution-cid
                     (:incidence/cid presence))]
             [:trusted/organization-binding-did-not-constituent
              (assoc (binding-result) :binding/did "did:key:z6Mkother")]
             [:trusted/organization-binding-relationship-invalid
              (assoc (binding-result) :binding/verification-relationship
                     :authentication)]]]
      (let [thrown (try
                     (trusted/verify-organization-binding!
                      (constantly changed) constitution :evidence)
                     nil
                     (catch clojure.lang.ExceptionInfo e e))]
        (is (= problem (:problem (ex-data thrown))))))))

(deftest readback-verifier-requires-an-authorized-authenticated-peer
  (let [other (incidence/typed-ref :did "did:key:z6Mkother")
        thrown (try
                 (readback/verifier
                  {:organization-binding (org-binding)
                   :session (session other)
                   :challenge! (constantly "challenge")
                   :clock! (constantly now-ms)
                   :verify! proof-result
                   :max-age-ms 500})
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= :readback/peer-not-authorized (:problem (ex-data thrown))))))

(deftest signed-readback-is-one-shot-fresh-and-opaque
  (let [v (verifier)
        challenge (readback/issue-challenge! v (request))
        admission (readback/verify-envelope!
                   v (request) challenge
                   (envelope v challenge 900 1100))
        info (readback/verified-readback-description admission)]
    (is (readback/verified-readback? admission))
    (is (= (:incidence/cid presence) (:receipt/incidence-cid info)))
    (is (= (:incidence/cid constitution) (:receipt/constitution-cid info)))
    (is (= org-did (:receipt/issuer info)))
    (is (false? (readback/verified-readback? info)))
    (is (false? (readback/verifier? (readback/verifier-description v))))
    (let [replay (try
                   (readback/verify-envelope!
                    v (request) challenge (envelope v challenge 900 1100))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= :readback/challenge-not-pending
             (:problem (ex-data replay)))))))

(deftest stale-misbound-and-invalid-signatures-fail-closed
  (testing "expired receipt"
    (let [v (verifier)
          challenge (readback/issue-challenge! v (request))
          thrown (try
                   (readback/verify-envelope!
                    v (request) challenge (envelope v challenge 400 800))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= :readback/not-fresh (:problem (ex-data thrown))))))
  (testing "wrong challenge"
    (let [v (verifier)
          challenge (readback/issue-challenge! v (request))
          thrown (try
                   (readback/verify-envelope!
                    v (request) challenge (envelope v "other" 900 1100))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= :readback/challenge-mismatch (:problem (ex-data thrown))))))
  (testing "wrong readback CID"
    (let [v (verifier)
          challenge (readback/issue-challenge! v (request))
          tampered (assoc-in (envelope v challenge 900 1100)
                             [:receipt/statement :incidence/block
                              :incidence/facts :receipt/readback-cid]
                             (:incidence/cid constitution))
          thrown (try
                   (readback/verify-envelope! v (request) challenge tampered)
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= :readback/statement-address-invalid
             (:problem (ex-data thrown))))))
  (doseq [[label problem overrides]
          [["wrong constitution" :readback/constitution-mismatch
            {:constitution-cid (:incidence/cid presence)}]
           ["wrong peer" :readback/peer-mismatch
            {:peer (incidence/typed-ref :did "did:key:z6Mkimpostor")}]
           ["wrong session transcript" :readback/session-mismatch
            {:session-transcript-cid (:incidence/cid constitution)}]]]
    (testing label
      (let [v (verifier)
            challenge (readback/issue-challenge! v (request))
            statement (raw-statement v challenge overrides)
            thrown (try
                     (readback/verify-envelope!
                      v (request) challenge
                      {:receipt/statement statement
                       :receipt/proof {:proofValue "signature"}})
                     nil
                     (catch clojure.lang.ExceptionInfo e e))]
        (is (= problem (:problem (ex-data thrown)))))))
  (testing "invalid host-verified signature"
    (let [v (verifier (constantly "signature-challenge")
                      #(assoc (proof-result %) :proof/valid? false))
          challenge (readback/issue-challenge! v (request))
          thrown (try
                   (readback/verify-envelope!
                    v (request) challenge (envelope v challenge 900 1100))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= :readback/signature-invalid (:problem (ex-data thrown)))))))

(deftest duplicate-challenges-are-rejected-before-network-use
  (let [v (verifier (constantly "duplicate") proof-result)]
    (is (= "duplicate" (readback/issue-challenge! v (request))))
    (let [thrown (try
                   (readback/issue-challenge! v (request))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= :readback/challenge-duplicate (:problem (ex-data thrown)))))))

(deftest ocapn-provider-returns-an-opaque-verified-readback
  (let [v (verifier (constantly "provider-challenge") proof-result)
        calls (atom [])
        live-session
        (trusted/authenticate-session!
         (constantly
          {:session/valid? true
           :session/id "captp-org-a"
           :session/version trusted/captp-version
           :session/peer peer
           :session/transcript-cid (:incidence/cid presence)})
         :handshake
         {:send! identity
          :request!
          (fn [call]
            (swap! calls conj call)
            (let [challenge (nth (:ocapn/args call) 4)]
              {:ocapn/status :fulfilled
               :ocapn/value (envelope v challenge 900 1100)}))})
        reference (ocapn/connected-reference
                   {:session live-session
                    :remote/target {:ocapn/descriptor :desc/export
                                    :ocapn/position 7}})
        result ((ocapn/signed-readback-append-provider reference v) (request))
        admission (:readback/admission result)]
    (is (= 'append-incidence-readback
           (first (:ocapn/args (first @calls)))))
    (is (= (:incidence/cid constitution)
           (nth (:ocapn/args (first @calls)) 5)))
    (is (true? (:ocapn/remote-readback-verified? result)))
    (is (readback/verified-readback? admission))))
