(ns kotoba.lang.consensus-order-test
  (:require [clojure.test :refer [deftest is]]
            [inga.attest :as inga-attest]
            [inga.consensus :as inga-consensus]
            [inga.kotoba-order :as inga-order]
            [kotoba.lang.consensus-order :as order]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-replication :as replication]
            [sha2.core :as sha2]))

;; Ported from consensus_order_test.clj 2026-09-08. The only JVM-specific
;; piece was a local `sha256-hex` test helper built on `java.security.
;; MessageDigest` -- not on `kotoba.lang.consensus-order` itself, which never
;; imports Java and was already dual-host. `sha2.core` (a portable `.cljc`
;; FIPS 180-4 implementation already reachable on this classpath, verified
;; here against the standard SHA-256("abc") test vector) replaces it on BOTH
;; hosts, so the hash function used to sign and verify votes is now identical
;; on JVM and nbb rather than merely equivalent -- no reader-conditional
;; branch is needed for the digest itself, only for turning a string into the
;; unsigned-byte vector `sha2.core/sha256` expects.

(defn- utf8-byte-vec
  [s]
  #?(:clj (vec (map #(bit-and % 0xff) (.getBytes ^String s "UTF-8")))
     :cljs (vec (js/Array.from (.encode (js/TextEncoder.) s)))))

(defn- sha256-hex [text]
  (sha2/sha256-hex (utf8-byte-vec text)))

(def dataspace "dataspace:consensus/example")
(def org-ref (incidence/typed-ref :did "did:key:z6Mkconsensusorg"))
(def root
  (incidence/addressed
   (incidence/incidence :consensus/root {:organization #{org-ref}} {})))
(def child
  (incidence/addressed
   (incidence/incidence :consensus/child {:organization #{org-ref}}
                        {:parents #{(:incidence/cid root)}})))

(defn envelope [height parent commit entries]
  {:consensus/profile order/profile
   :consensus/dataspace dataspace
   :consensus/height height
   :consensus/parent-id parent
   :consensus/commit-id commit
   :consensus/entry-cids (mapv :incidence/cid entries)
   :consensus/certificate {:inga/qc "inert-test-certificate"}})

(defn verifier [input]
  (assoc (select-keys input
                      [:consensus/profile :consensus/dataspace
                       :consensus/height :consensus/parent-id
                       :consensus/commit-id :consensus/entry-cids])
         :consensus/valid? true))

(deftest external-consensus-order-is-opaque-and-applied-exactly
  (let [registry (order/commit-registry)
        first-envelope (envelope 1 nil "inga:block:1" [root child])
        commit (order/admit-commit! registry verifier first-envelope)
        applied (order/apply-commit
                 (replication/replica dataspace) commit [root child])]
    (is (order/ordered-commit? commit))
    (is (false? (order/ordered-commit? (order/commit-description commit))))
    (is (= [(:incidence/cid root) (:incidence/cid child)]
           (:consensus/order applied)))
    (is (= 2 (count (get-in applied [:replica :replica/blocks]))))
    (is (= :consensus/entry-order-mismatch
           (:problem
            (ex-data
             (try (order/apply-commit
                   (replication/replica dataspace) commit [child root])
                  (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                         e e))))))
    (is (= 2 (:consensus/height
              (order/commit-description
               (order/admit-commit!
                registry verifier
                (envelope 2 "inga:block:1" "inga:block:2" [child]))))))))

(deftest verifier-forks-and-skipped-heights-fail-closed
  (let [registry (order/commit-registry)
        first-envelope (envelope 1 nil "inga:block:1" [root])]
    (is (= :consensus/verification-invalid
           (:problem
            (ex-data
             (try (order/admit-commit! registry (constantly true) first-envelope)
                  (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                         e e))))))
    (order/admit-commit! registry verifier first-envelope)
    (doseq [bad [(envelope 1 nil "inga:fork" [child])
                 (envelope 3 "inga:block:1" "inga:skip" [child])
                 (envelope 2 "inga:wrong-parent" "inga:fork2" [child])]]
      (let [err (ex-data
                  (try (order/admit-commit! registry verifier bad)
                       (catch #?(:clj clojure.lang.ExceptionInfo
                                 :cljs ExceptionInfo)
                              e e)))]
        (is (= :consensus/order-invalid (:problem err)))
        ;; fork evidence must be recoverable at the detection seam so a
        ;; later warrant (path b / ADR-2809060410) can sign it
        (is (= (:consensus/height bad) (get-in err [:consensus/candidate :height])))
        (is (= (:consensus/parent-id bad) (get-in err [:consensus/candidate :parent-id])))
        (is (= 2 (:consensus/observed-height err)))
        (is (= "inga:block:1" (:consensus/expected-parent err)))))))

(deftest admitted-inga-qc-is-a-live-consensus-verifier
  (let [witnesses #{"validator-a" "validator-b" "validator-c" "validator-d"}
        chain-id "kotoba-incidence-live"
        sign (fn [w payload] (sha256-hex (str w "|" payload)))
        block (inga-consensus/make-block
               {:height 1 :parent-hash nil
                :proposals [(:incidence/cid root) (:incidence/cid child)]
                :proposer dataspace :ts 1786800000000 :round 0})
        commit-id (sha256-hex (inga-consensus/canonical-block block))
        votes (mapv (fn [w]
                      (-> (inga-consensus/make-vote w commit-id 1)
                          (assoc :inga.vote/view 0)
                          (inga-attest/sign-vote chain-id 0 #(sign w %))))
                    ["validator-a" "validator-b" "validator-c"])
        qc (inga-attest/certify (inga-consensus/qc votes 4 0) votes)
        envelope {:consensus/profile order/profile
                  :consensus/dataspace dataspace
                  :consensus/height 1
                  :consensus/parent-id nil
                  :consensus/commit-id commit-id
                  :consensus/entry-cids
                  [(:incidence/cid root) (:incidence/cid child)]
                  :consensus/certificate
                  {:inga/order-block block :inga/qc qc}}
        verify! (inga-order/verifier
                 {:chain-id chain-id :quorum 3 :hash-fn sha256-hex
                  :verify-sig-fn
                  (fn [w payload signature] (= signature (sign w payload)))
                  :admitted? witnesses})
        registry (order/commit-registry)
        admitted (order/admit-commit! registry verify! envelope)]
    (is (order/ordered-commit? admitted))
    (is (= commit-id
           (:consensus/commit-id (order/commit-description admitted))))
    (is (= :consensus/verification-invalid
           (:problem
            (ex-data
             (try
               (order/admit-commit!
                (order/commit-registry) verify!
                (assoc envelope :consensus/dataspace "dataspace:evil"))
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                      e e))))))))
