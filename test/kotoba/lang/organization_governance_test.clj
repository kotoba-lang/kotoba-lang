(ns kotoba.lang.organization-governance-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.organization-governance :as governance]))

(def org-did (incidence/typed-ref :did "did:key:z6Mkgoverned"))
(def constitution
  (incidence/addressed
   (incidence/constitute :organization #{org-did} {})))
(def organization
  (incidence/typed-ref :cid (:incidence/cid constitution)))
(def governors
  [(incidence/typed-ref :did "did:key:z6Mkgov1")
   (incidence/typed-ref :did "did:key:z6Mkgov2")
   (incidence/typed-ref :did "did:key:z6Mkgov3")])
(def policy
  (governance/policy organization governors 2
                     #{:organization/set-purpose}))
(def payload-a
  (incidence/addressed
   (incidence/incidence :organization/purpose
                        {:organization #{organization}}
                        {:facts {:purpose "A"}})))
(def payload-b
  (incidence/addressed
   (incidence/incidence :organization/purpose
                        {:organization #{organization}}
                        {:facts {:purpose "B"}})))

(defn proposal [payload proposer]
  (governance/proposal policy proposer :organization/set-purpose payload
                       "organization-purpose"))

(defn approval [proposal governor evidence]
  (governance/approval policy proposal governor :approve evidence))

(defn admission [proposal approval]
  (let [block (:incidence/block approval)
        governor (first (get-in block [:incidence/roles
                                       :governance/governor]))
        evidence (first (:incidence/evidence block))]
    (governance/verify-approval!
     (constantly
      {:approval/valid? true
       :approval/cid (:incidence/cid approval)
       :approval/proposal-cid (:incidence/cid proposal)
       :approval/constitution-cid (:incidence/cid constitution)
       :approval/governor governor
       :approval/evidence-cid evidence})
     policy proposal approval :signed-proof)))

(defn enacted [payload proposer evidence-a evidence-b]
  (let [p (proposal payload proposer)
        a1 (approval p (first governors) evidence-a)
        a2 (approval p (second governors) evidence-b)]
    (governance/enact policy p [a1 a2]
                      [(admission p a1) (admission p a2)])))

(deftest policy-proposal-approval-and-enactment-are-content-addressed
  (let [p (proposal payload-a (first governors))
        a1 (approval p (first governors) (:incidence/cid constitution))
        a2 (approval p (second governors) (:incidence/cid payload-a))
        d (governance/enact policy p [a1 a2]
                            [(admission p a1) (admission p a2)])
        entry (governance/decision-entry d)
        info (governance/decision-description d)]
    (is (governance/enacted-decision? d))
    (is (:ok? (incidence/verify-addressed entry)))
    (is (= :organization/enacted
           (get-in entry [:incidence/block :incidence/kind])))
    (is (= (:incidence/cid p) (:governance/proposal-cid info)))
    (is (= (set (take 2 governors)) (:governance/governors info)))
    (is (false? (governance/enacted-decision? info)))
    (is (= 1 (count (:governance/active
                     (governance/project-decisions [d])))))))

(deftest quorum-is-distinct-verified-and-policy-bound
  (let [p (proposal payload-a (first governors))
        a1 (approval p (first governors) (:incidence/cid constitution))
        admitted (admission p a1)]
    (testing "below threshold"
      (is (= :governance/below-quorum
             (:problem
              (ex-data
               (try (governance/enact policy p [a1] [admitted])
                    (catch clojure.lang.ExceptionInfo e e)))))))
    (testing "serialized admission lookalike"
      (is (= :governance/approval-not-verified
             (:problem
              (ex-data
               (try (governance/enact
                     policy p [a1 a1]
                     [(governance/approval-description admitted)
                      (governance/approval-description admitted)])
                    (catch clojure.lang.ExceptionInfo e e)))))))
    (testing "verifier result cannot substitute a governor"
      (let [bad (try
                  (governance/verify-approval!
                   (fn [_]
                     (assoc (governance/approval-description admitted)
                            :approval/valid? true
                            :approval/governor (last governors)))
                   policy p a1 :proof)
                  nil
                  (catch clojure.lang.ExceptionInfo e e))]
        (is (= :governance/governor-mismatch
               (:problem (ex-data bad))))))))

(deftest concurrent-decisions-with-one-conflict-key-remain-a-visible-branch
  (let [a (enacted payload-a (first governors)
                   (:incidence/cid constitution) (:incidence/cid payload-a))
        b (enacted payload-b (second governors)
                   (:incidence/cid payload-a) (:incidence/cid payload-b))
        projected (governance/project-decisions [a b])
        key [(:incidence/cid constitution) "organization-purpose"]]
    (is (empty? (:governance/active projected)))
    (is (= 2 (count (get-in projected [:governance/conflicts key]))))))

(deftest repeated-certificates-collapse-order-independently
  (let [p (proposal payload-a (first governors))
        approvals (mapv #(approval p % (:incidence/cid payload-a)) governors)
        admissions (mapv #(admission p %) approvals)
        a (governance/enact policy p (subvec approvals 0 2)
                            (subvec admissions 0 2))
        b (governance/enact policy p (subvec approvals 1 3)
                            (subvec admissions 1 3))
        forward (governance/project-decisions [a b])
        reverse-order (governance/project-decisions [b a])
        active (first (vals (:governance/active forward)))]
    (is (= forward reverse-order))
    (is (= (set governors) (:governance/governors active)))
    (is (= 2 (count (:governance/enactment-cids active))))))

(deftest direct-data-cannot-smuggle-an-action-outside-policy
  (let [p (proposal payload-a (first governors))
        malicious
        (incidence/addressed
         (assoc-in (:incidence/block p)
                   [:incidence/facts :governance/action]
                   :organization/delete-everything))
        a (governance/approval policy malicious (first governors) :approve
                               (:incidence/cid constitution))
        thrown (try
                 (governance/verify-approval!
                  (constantly {}) policy malicious a :proof)
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= :governance/action-not-allowed (:problem (ex-data thrown))))))
