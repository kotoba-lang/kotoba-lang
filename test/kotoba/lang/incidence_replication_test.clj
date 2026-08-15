(ns kotoba.lang.incidence-replication-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-port :as port]
            [kotoba.lang.incidence-replication :as replication]
            [kotoba.lang.signed-readback :as readback]
            [kotoba.lang.trusted-admission :as trusted]))

(def dataspace "dataspace:replication/example")
(def org-did "did:key:z6Mkreplicationorg")
(def org-ref (incidence/typed-ref :did org-did))
(def peers
  [(incidence/typed-ref :did "did:key:z6Mkpeer1")
   (incidence/typed-ref :did "did:key:z6Mkpeer2")
   (incidence/typed-ref :did "did:key:z6Mkpeer3")])
(def root
  (incidence/addressed
   (incidence/constitute :organization #{org-ref} {})))
(def child
  (incidence/addressed
   (incidence/incidence
    :replication/child
    {:subject #{(incidence/typed-ref :cid (:incidence/cid root))}}
    {:parents #{(:incidence/cid root)}})))

(defn organization-binding []
  (trusted/verify-organization-binding!
   (constantly
    {:binding/valid? true
     :binding/constitution-cid (:incidence/cid root)
     :binding/kind :organization
     :binding/did org-did
     :binding/authorized-peers (set peers)
     :binding/verification-relationship :assertionMethod
     :binding/verification-method (str org-did "#assertion")
     :binding/evidence-cid (:incidence/cid root)})
   root :binding-proof))

(defn request []
  {:dataspace dataspace
   :entry child
   :capability (capabilities/make-cap port/append-kind dataspace)})

(defn admitted-readback [peer challenge]
  (let [session
        (trusted/authenticate-session!
         (constantly
          {:session/valid? true
           :session/id (str "session-" challenge)
           :session/version trusted/captp-version
           :session/peer peer
           :session/transcript-cid (:incidence/cid root)})
         :handshake {:send! identity :request! identity})
        verifier
        (readback/verifier
         {:organization-binding (organization-binding)
          :session session
          :challenge! (constantly challenge)
          :clock! (constantly 1000)
          :verify! (fn [input]
                     {:proof/valid? true
                      :proof/payload-cid (:payload-cid input)
                      :proof/issuer (:issuer input)
                      :proof/verification-method (:verification-method input)
                      :proof/relationship :assertionMethod})
          :max-age-ms 500})
        issued (readback/issue-challenge! verifier (request))
        statement (readback/statement verifier (request) issued 900 1100)]
    (readback/verify-envelope!
     verifier (request) issued
     {:receipt/statement statement :receipt/proof {:proofValue "signature"}})))

(deftest out-of-order-anti-entropy-converges-by-cid-set-union
  (let [empty (replication/replica dataspace {:max-batch 8})
        orphan-result (replication/ingest empty [child])
        orphan (:replica orphan-result)
        source (:replica (replication/ingest empty [root child]))]
    (is (:ok? orphan-result))
    (is (= #{(:incidence/cid root)} (:replica/missing orphan)))
    (is (= :replication/incomplete (:reason (replication/projection orphan))))
    (is (= [(:incidence/cid root)]
           (replication/wanted orphan
                               #{(:incidence/cid root) (:incidence/cid child)}
                               2)))
    (let [batch (replication/export-batch source [(:incidence/cid root)])
          healed (:replica (replication/ingest orphan batch))
          reverse-order (:replica (replication/ingest empty [child root]))]
      (is (empty? (:replica/missing healed)))
      (is (:ok? (replication/projection healed)))
      (is (= (:replica/blocks healed) (:replica/blocks reverse-order)))
      (is (= (:replica/frontier healed) (:replica/frontier reverse-order))))))

(deftest batches-are-bounded-atomic-and-hash-verified
  (let [state (replication/replica dataspace {:max-batch 1})
        tampered (assoc child :incidence/cid (:incidence/cid root))]
    (is (= :replication/batch-too-large
           (:reason (replication/ingest state [root child]))))
    (let [result (replication/ingest state [root tampered])]
      (is (false? (:ok? result)))
      (is (empty? (get-in result [:replica :replica/blocks])))))
  (let [state (:replica (replication/ingest
                         (replication/replica dataspace {:max-batch 4})
                         [root child]))
        page (replication/inventory-page state nil 1)]
    (is (= 1 (count (:replication/cids page))))
    (is (string? (:replication/next-cursor page)))))

(deftest signed-readbacks-form-an-opaque-distinct-peer-quorum
  (let [admissions (mapv admitted-readback peers ["c1" "c2" "c3"])
        certificate (replication/certify-readback-quorum admissions 2)
        info (replication/certificate-description certificate)]
    (is (replication/replication-certificate? certificate))
    (is (= 3 (count (:replication/peers info))))
    (is (= (:incidence/cid child) (:receipt/incidence-cid info)))
    (is (false? (replication/replication-certificate? info)))
    (testing "serialized maps and repeated peers do not count"
      (is (= :replication/readback-not-verified
             (:problem
              (ex-data
               (try (replication/certify-readback-quorum [info info] 2)
                    (catch clojure.lang.ExceptionInfo e e))))))
      (let [same-peer [(admitted-readback (first peers) "d1")
                       (admitted-readback (first peers) "d2")]]
        (is (= :replication/duplicate-peer
               (:problem
                (ex-data
                 (try (replication/certify-readback-quorum same-peer 2)
                      (catch clojure.lang.ExceptionInfo e e))))))))))
