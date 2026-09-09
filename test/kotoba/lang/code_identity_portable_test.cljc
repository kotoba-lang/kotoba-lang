(ns kotoba.lang.code-identity-portable-test
  "The genuinely portable slice of kotoba.lang.code-identity-test: CI1
  (every canonical input participates), CI5 (fail-closed on out-of-domain
  values), and CI4 (lock verification) coverage that touches no file at all.

  Everything that reads `lang/code-identity-conformance/*.edn`,
  `lang/code-identity-vectors.edn`, `lang/code-identity.edn`,
  `lang/elaboration-pipeline.edn`, or `lang/guest-grammar.edn` stays in
  kotoba.lang.code-identity-test (.clj) -- not because file IO is impossible
  on this host (nbb has full `fs` access), but because
  `kotoba.lang.code-identity` and `kotoba.kir.definition-identity` deliberately
  supply no ClojureScript file-read implementation of their own, and reaching
  for `fs` from the test would inject a capability the source under test does
  not claim to have.

  One assertion needed a real substitution, not just a reader-conditional:
  `identity/i64` takes `n` and does `(str n)`, so calling it with a raw
  integer literal above 2^53-1 is exactly the failure mode this file's own
  `normalize` exists to refuse -- the ClojureScript READER rounds
  `9007199254740993` to `9007199254740992` before `i64` ever sees it, so the
  two `i64`-wrapped neighbours the JVM test expects to stay distinct would
  collide on this host. The fix is to pass the exact digits as a STRING
  literal, which `(str n)` returns unchanged on both hosts -- verified below
  against the plain-integer form (`5` and `(i64 5)` must still agree)."
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer [deftest is testing] :include-macros true])
            [cbor.core :as cbor]
            [kotoba.lang.code-identity :as identity]))

(def ^:private definition
  {:definition/profile-version 4
   :definition/desugar-contract-version 1
   :definition/kir {:op :const :value 1}
   :definition/effect-row #{}
   :definition/interface {:arity 0}
   :definition/dependencies []})

(deftest public-functions-are-real-fns
  (testing "each is a real fn, not an unbound var nested in another form by a
            missing paren -- `resolve` is not a runtime operation on this
            host, so this checks the same claim directly against the aliased
            vars instead"
    (doseq [[label v] [["identity-payload" identity/identity-payload]
                       ["canonical-bytes" identity/canonical-bytes]
                       ["canonical-hex" identity/canonical-hex]
                       ["definition-cid" identity/definition-cid]
                       ["definition-error" identity/definition-error]
                       ["normalize" identity/normalize]
                       ["f64" identity/f64]
                       ["verify-locked-definitions" identity/verify-locked-definitions]]]
      (is (fn? v) (str label " must be a real function")))))

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

;; ---------------------------------------------------------------------------
;; CI1 -- every canonical input actually participates
;; ---------------------------------------------------------------------------

(deftest every-canonical-input-participates-in-identity
  (testing "lang/code-identity.edn names six canonical inputs; changing any one
            of them must move the identity."
    (let [base (identity/definition-cid definition)]
      (doseq [[label changed]
              [[:typed-kir     (assoc definition :definition/kir {:op :const :value 2})]
               [:profile-version (assoc definition :definition/profile-version 5)]
               [:desugar-contract-version (assoc definition :definition/desugar-contract-version 2)]
               [:effect-row    (assoc definition :definition/effect-row #{:host/http})]
               [:interface     (assoc definition :definition/interface {:arity 1})]
               [:dependencies  (assoc definition :definition/dependencies
                                      [(identity/definition-cid
                                        (assoc definition :definition/kir {:op :const :value 9}))])]]]
        (is (not= base (identity/definition-cid changed))
            (str label " must participate in the definition identity"))))))

(deftest a-definition-missing-a-canonical-input-is-refused
  (testing "identity is never computed over a partial semantic contract"
    (doseq [k identity/definition-required]
      (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                   (identity/definition-cid (dissoc definition k)))
          (str "missing " k " must throw rather than hash")))))

(deftest effect-row-shape-is-enforced
  (is (some? (identity/definition-error (assoc definition :definition/effect-row [:host/http])))
      "a vector is not a set: effect rows have no source order")
  (is (some? (identity/definition-error (assoc definition :definition/effect-row #{"host/http"})))
      "effect row members must be keywords, not strings"))

;; ---------------------------------------------------------------------------
;; CI1 -- the canonical encoding is really dag-cbor
;; ---------------------------------------------------------------------------

(deftest the-block-is-really-dag-cbor
  (testing "the bytes must actually decode as CBOR, or the codec claim is a
            lie an IPLD consumer discovers at read time"
    (let [bytes (identity/canonical-bytes definition)]
      (is (= (cbor/decode bytes)
             (identity/normalize (identity/identity-payload definition)))))))

(deftest canonical-order-is-not-source-order
  (testing "map entry order"
    (is (= (identity/definition-cid (assoc definition :definition/kir {:op :const :value 1}))
           (identity/definition-cid (assoc definition :definition/kir (array-map :value 1 :op :const))))))
  (testing "dependency order"
    (let [a (identity/definition-cid (assoc definition :definition/kir {:op :const :value 10}))
          b (identity/definition-cid (assoc definition :definition/kir {:op :const :value 20}))]
      (is (= (identity/definition-cid (assoc definition :definition/dependencies [a b]))
             (identity/definition-cid (assoc definition :definition/dependencies [b a]))))))
  (testing "effect row order"
    (is (= (identity/definition-cid (assoc definition :definition/effect-row #{:a :b}))
           (identity/definition-cid (assoc definition :definition/effect-row #{:b :a}))))))

(deftest the-normalized-domain-is-injective
  (testing "a tagged form means a keyword can never collide with the string of
            the same name -- the collision an untagged encoding would allow"
    (is (not= (identity/normalize :name) (identity/normalize "name")))
    (is (not= (identity/normalize 'name) (identity/normalize "name")))
    (is (not= (identity/normalize 1) (identity/normalize "1")))
    (is (not= (identity/normalize []) (identity/normalize '())))
    (is (not= (identity/normalize #{1}) (identity/normalize [1])))))

(deftest values-outside-the-domain-fail-closed
  (testing "raw platform floats are refused: JavaScript has one number type,
            so 2.0 is integer? there and a double here"
    (is (= :definition/unencodable-float
           (:problem (ex-data (try (identity/normalize 1.5)
                                   (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e e)))))))
  (testing "f64 literals have an admitted exact representation instead"
    (is (= ["f64" "3ff8000000000000"] (identity/normalize (identity/f64 1.5))))
    (is (not= (identity/normalize (identity/f64 1.5))
              (identity/normalize (identity/f64 2.5)))))
  (testing "an unknown type is refused rather than coerced"
    (is (= :definition/uncanonical-value
           (:problem (ex-data (try (identity/normalize #?(:clj (java.util.Date.) :cljs (js/Date.)))
                                   (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e e))))))))

(deftest integers-beyond-the-exact-range-must-be-explicit
  (testing "a plain literal past 2^53-1 is refused rather than silently
            hashed -- on this host the reader has already rounded the literal
            to the next representable double, which is STILL outside the
            exact range, so the refusal fires for the same reason it does on
            the JVM even though the rounded value itself differs"
    (is (= :definition/inexact-integer
           (:problem (ex-data (try (identity/normalize 9007199254740993)
                                   (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e e))))))
    (is (= :definition/inexact-integer
           (:problem (ex-data (try (identity/normalize -9007199254740993)
                                   (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e e)))))))
  (testing "the explicit form carries it exactly, and neighbours stay distinct
            -- `i64` does `(str n)`, so the digits must be passed as a STRING
            here: a raw integer literal would already be rounded by this
            host's reader before `i64` ever saw it, which is the exact defect
            this assertion exists to catch"
    (is (not= (identity/normalize (identity/i64 "9007199254740993"))
              (identity/normalize (identity/i64 "9007199254740992")))))
  (testing "how a value is spelled is not part of its meaning"
    (is (= (identity/normalize 5) (identity/normalize (identity/i64 5)))))
  (testing "the exact range itself is admitted as a plain integer"
    (is (= ["int" "9007199254740991"] (identity/normalize 9007199254740991)))))

;; ---------------------------------------------------------------------------
;; CI4 -- lock verification
;; ---------------------------------------------------------------------------

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
                       [entry])))))
    (testing "swapping in an effectful definition under a pure definition's
              lock entry is caught"
      (let [effectful (assoc definition :definition/effect-row #{:host/http})]
        (is (= :definition/hash-mismatch
               (:reason (identity/verify-locked-definitions
                         lock [(assoc entry :definition effectful)]))))))))
