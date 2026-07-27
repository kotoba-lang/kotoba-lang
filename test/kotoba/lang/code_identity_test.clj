(ns kotoba.lang.code-identity-test
  "The first tests for kotoba.lang.code-identity.

  Four of its public functions -- identity-payload, canonical-edn,
  definition-cid and verify-locked-definitions -- were nested inside
  definition-error's body by a missing paren and were therefore never bound at
  load time. Nothing called them, so nothing noticed. These tests exist so that
  cannot recur silently."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.code-identity :as identity]))

(def ^:private definition
  {:definition/profile-version 1
   :definition/kir {:op :const :value 1}
   :definition/interface {:arity 0}
   :definition/dependencies []})

(deftest public-functions-are-bound-at-load
  (testing "each is a real fn, not an unbound var nested in another form"
    (doseq [v ['identity-payload 'canonical-edn 'definition-cid
               'definition-error 'verify-locked-definitions]]
      (is (bound? (resolve (symbol "kotoba.lang.code-identity" (name v))))
          (str v " must be a top-level definition")))))

(deftest definition-cid-is-stable-and-content-addressed
  (let [cid (identity/definition-cid definition)]
    (is (string? cid))
    (testing "the same definition hashes identically"
      (is (= cid (identity/definition-cid definition))))
    (testing "a changed body changes the identity"
      (is (not= cid (identity/definition-cid
                     (assoc definition :definition/kir {:op :const :value 2})))))
    (testing "the author-facing alias is deliberately not semantic identity"
      (is (= cid (identity/definition-cid
                  (assoc definition :definition/name "renamed")))))))

(deftest verify-locked-definitions-fails-closed
  (let [cid (identity/definition-cid definition)
        lock {:deps [{:dep/name "acme/lib" :dep/definition-cids [cid]}]}
        entry {:dep/name "acme/lib" :definition definition :definition-cid cid}]
    (testing "nothing resolved is vacuously fine"
      (is (= {:ok? true} (identity/verify-locked-definitions lock []))))
    (testing "a locked, matching definition is admitted"
      (is (= {:ok? true} (identity/verify-locked-definitions lock [entry]))))
    (testing "a dependency absent from the lock is rejected"
      (is (= :definition/unknown-dependency
             (:reason (identity/verify-locked-definitions {:deps []} [entry])))))
    (testing "a syntactically invalid cid is rejected before hashing"
      (is (= :definition/cid-invalid
             (:reason (identity/verify-locked-definitions
                       lock [(assoc entry :definition-cid "not-a-cid")])))))
    (testing "code that does not hash to its claimed identity is rejected"
      (let [other (identity/definition-cid
                   (assoc definition :definition/kir {:op :const :value 99}))]
        (is (= :definition/hash-mismatch
               (:reason (identity/verify-locked-definitions
                         {:deps [{:dep/name "acme/lib"
                                  :dep/definition-cids [other]}]}
                         [(assoc entry :definition-cid other)]))))))
    (testing "a real definition the lock does not list is rejected"
      (is (= :definition/not-locked
             (:reason (identity/verify-locked-definitions
                       {:deps [{:dep/name "acme/lib" :dep/definition-cids []}]}
                       [entry])))))))
