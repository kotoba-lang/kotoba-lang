(ns kotoba.lang.incidence-datoms-test
  "The triple projection of an incidence block, and the round trip that decides
  whether the arity-3 plane is an exact encoding of the labelled one."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [kotoba.lang.incidence :as inc]))

(def alice (inc/typed-ref :did "did:key:z6Mkalice"))
(def bob   (inc/typed-ref :did "did:key:z6Mkbob"))
(def a-cid "bafyreiepyqj5rlinsrcxdypmatus2pfaipiyku5q3qq5pbnl5n2dbozzca")

(defn- block
  ([] (block {}))
  ([overrides]
   (merge {:incidence/kind :organization/constitution
           :incidence/roles {:organization/constituent #{alice}}
           :incidence/facts {:organization/name "Alice" :organization/kind :person}
           :incidence/parents #{}
           :incidence/evidence #{}
           :incidence/policies #{}}
          overrides)))

(defn- round-trips? [b]
  (let [cid (inc/incidence-cid b)]
    (= b (inc/from-datoms cid (inc/datoms cid b)))))

(deftest the-shipped-vector-round-trips
  (testing "the block lang/incidence-vectors.edn pins, not one written for this test"
    (let [vectors (:vectors (edn/read-string (slurp "lang/incidence-vectors.edn")))]
      (is (seq vectors) "vectors file must not be empty -- an empty suite passes vacuously")
      (doseq [{:keys [id block]} vectors]
        (is (round-trips? block) (str "vector " id))))))

(deftest a-role-with-several-participants-round-trips
  (testing "a set of participants is one triple each; sets have no order and no
            repeats, so nothing distinguishes the two forms"
    (is (round-trips? (block {:incidence/roles {:organization/constituent #{alice bob}}})))))

(deftest several-roles-round-trip
  (testing "a valid :organization/member-added -- two roles, one participant each"
    (is (round-trips? {:incidence/kind :organization/member-added
                       :incidence/roles {:organization #{alice} :member #{bob}}
                       :incidence/facts {:membership/roles #{:admin}}
                       :incidence/parents #{}
                       :incidence/evidence #{}
                       :incidence/policies #{}}))))

(deftest the-structural-sets-round-trip
  (is (round-trips? (block {:incidence/parents #{a-cid}
                            :incidence/evidence #{a-cid}
                            :incidence/policies #{a-cid}}))))

(deftest facts-are-qualified-so-they-cannot-collide
  (testing "a fact key equal to a structural predicate, or to a role, stays distinct"
    (is (round-trips? (block {:incidence/facts {:organization/name "Alice"
                                                :organization/kind :person
                                                :incidence/kind :not-the-kind
                                                :organization/constituent "not the role"}})))
    (let [b (block {:incidence/facts {:organization/name "Alice"
                                      :organization/kind :person
                                      :incidence/kind :not-the-kind}})
          cid (inc/incidence-cid b)]
      (is (= 1 (count (filter #(= :incidence/kind (:p %)) (inc/datoms cid b))))
          "exactly one bare :incidence/kind triple -- the structural one"))))

(deftest a-role-that-would-collide-is-refused-not-flattened
  (testing "a lossy projection that looked lossless is the failure this exists
            to prevent, so it fails closed rather than emitting an unreadable set"
    ;; These blocks are also rejected by incidence-error, so their CID is not
    ;; computable -- projectable? is a separate, earlier question and is asked
    ;; without one.
    (doseq [role inc/reserved-predicates]
      (let [b (block {:incidence/roles {role #{alice}}})]
        (is (false? (inc/projectable? b)) (str "role " role))
        (is (nil? (inc/datoms a-cid b)) (str "role " role))))))

(deftest roles-are-the-joinable-form
  (testing "an edge keeps its own keyword, so a query reads as what it is"
    (let [b (block {:incidence/roles {:organization/constituent #{alice bob}}})
          cid (inc/incidence-cid b)
          ds (inc/datoms cid b)]
      (is (= #{alice bob}
             (set (map :o (filter #(= :organization/constituent (:p %)) ds)))))
      (is (every? #(= cid (:s %)) ds) "every triple is about this incidence"))))

(deftest from-datoms-refuses-what-is-not-a-projection
  (is (nil? (inc/from-datoms a-cid #{}))
      "no kind: not a projection of any block")
  (is (nil? (inc/from-datoms a-cid #{{:s a-cid :p :incidence/kind :o :a}
                                     {:s a-cid :p :incidence/kind :o :b}}))
      "two kinds: not a projection of any block"))

(deftest triples-about-other-incidences-are-ignored
  (testing "a projection is read out of a shared db, not out of a private one"
    (let [b (block)
          cid (inc/incidence-cid b)
          noise #{{:s "bafyOTHER" :p :incidence/kind :o :something/else}
                  {:s "bafyOTHER" :p :organization/constituent :o bob}}]
      (is (= b (inc/from-datoms cid (into (inc/datoms cid b) noise)))))))
