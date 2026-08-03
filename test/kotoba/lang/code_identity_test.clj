(ns kotoba.lang.code-identity-test
  "Tests for kotoba.lang.code-identity.

  Four of its public functions -- identity-payload, canonical-edn,
  definition-cid and verify-locked-definitions -- were once nested inside
  definition-error's body by a missing paren and were therefore never bound at
  load time. Nothing called them, so nothing noticed. The first test exists so
  that cannot recur silently.

  The rest are CI1: the identity must seal every input `lang/code-identity.edn`
  names as canonical, and must produce byte-for-byte identical bytes for the
  same definition. Both are checkable properties, so they are checked."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [cbor.core :as cbor]
            [kotoba.lang.code-identity :as identity]
            [kotoba.lang.package-registry :as registry]))

(def ^:private definition
  {:definition/profile-version 4
   :definition/desugar-contract-version 1
   :definition/kir {:op :const :value 1}
   :definition/effect-row #{}
   :definition/interface {:arity 0}
   :definition/dependencies []})

(deftest public-functions-are-bound-at-load
  (testing "each is a real fn, not an unbound var nested in another form"
    (doseq [v ['identity-payload 'canonical-bytes 'canonical-hex 'definition-cid
               'definition-error 'normalize 'f64 'verify-locked-definitions]]
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

;; ---------------------------------------------------------------------------
;; CI1 — every canonical input is actually sealed
;; ---------------------------------------------------------------------------

(deftest every-canonical-input-participates-in-identity
  (testing "lang/code-identity.edn names six canonical inputs; changing any one
            of them must move the identity. The effect row is the one that
            matters most: without it, a pure definition and an http-requiring
            definition with the same KIR would share a CID, so a lock pinning
            the pure one would admit the effectful one."
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
      (is (thrown? clojure.lang.ExceptionInfo
                   (identity/definition-cid (dissoc definition k)))
          (str "missing " k " must throw rather than hash")))))

(deftest effect-row-shape-is-enforced
  (is (some? (identity/definition-error (assoc definition :definition/effect-row [:host/http])))
      "a vector is not a set: effect rows have no source order")
  (is (some? (identity/definition-error (assoc definition :definition/effect-row #{"host/http"})))
      "effect row members must be keywords, not strings"))

;; ---------------------------------------------------------------------------
;; CI1 — the canonical encoding
;; ---------------------------------------------------------------------------

(deftest the-block-is-really-dag-cbor
  (testing "version 1 hashed pr-str output and labelled the CID dag-cbor. The
            bytes must actually decode as CBOR, or the codec claim is a lie an
            IPLD consumer discovers at read time."
    (let [bytes (identity/canonical-bytes definition)]
      (is (= (cbor/decode bytes)
             (identity/normalize (identity/identity-payload definition)))))))

;; ---------------------------------------------------------------------------
;; CI2 / CI3 — conformance fixtures
;; ---------------------------------------------------------------------------

(def ^:private conformance-root "lang/code-identity-conformance/")

(defn- read-edn [path] (edn/read-string (slurp (io/file path))))

(deftest code-identity-conformance-fixtures-hold
  (let [manifest (read-edn (str conformance-root "manifest.edn"))]
    (is (= 1 (:kotoba.lang.code-identity.conformance/version manifest)))
    (is (seq (:cases manifest)))
    (doseq [tc (:cases manifest)
            :let [data (read-edn (str conformance-root (:file tc)))
                  result (if (= :alias (:type tc))
                           (registry/check-case tc data)
                           (identity/check-case tc data))]]
      (is (:ok? result) (str (:id tc) " -> " (pr-str (:actual result)))))))

(deftest conformance-covers-every-sealed-input
  (testing "CI3 asks for hash/profile/interface/dependency mismatch fixtures.
            Effect row and desugar contract are sealed too, so they get fixtures
            as well -- an unsealed input is exactly the substitution a lock
            would fail to catch."
    (let [ids (set (map :id (:cases (read-edn (str conformance-root "manifest.edn")))))]
      (doseq [id [:negative-body-mismatch :negative-profile-mismatch
                  :negative-interface-mismatch :negative-dependency-mismatch
                  :negative-effect-row-mismatch :negative-desugar-mismatch]]
        (is (contains? ids id) (str id " must have a negative fixture"))))))

(deftest canonical-order-is-not-source-order
  (testing "map entry order"
    (is (= (identity/definition-cid (assoc definition :definition/kir {:x 1 :y 2}))
           (identity/definition-cid (assoc definition :definition/kir (array-map :y 2 :x 1))))))
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
  (testing "raw platform floats are refused: JavaScript has one number type, so
            2.0 is integer? there and a double here. Hashing them would make the
            same KIR encode differently per implementation."
    (is (= :definition/unencodable-float
           (:problem (ex-data (try (identity/normalize 1.5)
                                   (catch clojure.lang.ExceptionInfo e e)))))))
  (testing "f64 literals have an admitted exact representation instead"
    (is (= ["f64" "3ff8000000000000"] (identity/normalize (identity/f64 1.5))))
    (is (not= (identity/normalize (identity/f64 1.5))
              (identity/normalize (identity/f64 2.5)))))
  (testing "an unknown type is refused rather than coerced"
    (is (= :definition/uncanonical-value
           (:problem (ex-data (try (identity/normalize (java.util.Date.))
                                   (catch clojure.lang.ExceptionInfo e e))))))))

(deftest integers-beyond-the-exact-range-must-be-explicit
  (testing "the cross-implementation runner caught this: a plain literal past
            2^53-1 is rounded by the ClojureScript reader before any encoder
            runs, so 9007199254740993 and ...992 would hash the same there and
            differently here. Refusing the plain form makes that loud."
    (is (= :definition/inexact-integer
           (:problem (ex-data (try (identity/normalize 9007199254740993)
                                   (catch clojure.lang.ExceptionInfo e e))))))
    (is (= :definition/inexact-integer
           (:problem (ex-data (try (identity/normalize -9007199254740993)
                                   (catch clojure.lang.ExceptionInfo e e)))))))
  (testing "the explicit form carries it exactly, and neighbours stay distinct"
    (is (not= (identity/normalize (identity/i64 9007199254740993))
              (identity/normalize (identity/i64 9007199254740992)))))
  (testing "how a value is spelled is not part of its meaning"
    (is (= (identity/normalize 5) (identity/normalize (identity/i64 5)))))
  (testing "the exact range itself is admitted as a plain integer"
    (is (= ["int" "9007199254740991"] (identity/normalize 9007199254740991)))))

;; ---------------------------------------------------------------------------
;; CI1 — frozen vectors (also the CI6 cross-implementation reference)
;; ---------------------------------------------------------------------------

(def ^:private vectors-file "lang/code-identity-vectors.edn")

(def ^:private vectors
  (edn/read-string (slurp (io/file vectors-file))))

(deftest frozen-vectors-still-hold
  (testing "byte-for-byte deterministic identity, made checkable"
    (is (seq (:vectors vectors)) "vector table must not be empty")
    (is (= identity/payload-version (:payload-version vectors))
        "a payload-version bump changes every identity: regenerate the vectors
         with scripts/gen_code_identity_vectors.clj rather than editing hashes")
    (doseq [{:keys [id definition canonical-hex definition-cid]} (:vectors vectors)]
      (is (= canonical-hex (identity/canonical-hex definition))
          (str id ": canonical bytes moved"))
      (is (= definition-cid (identity/definition-cid definition))
          (str id ": definition CID moved")))))

(deftest frozen-vectors-are-mutually-distinct
  (testing "each vector exists to show some input changes the identity; two
            sharing a CID would mean the table is not testing what it claims"
    (let [cids (map :definition-cid (:vectors vectors))]
      (is (= (count cids) (count (distinct cids)))
          (pr-str (into {} (filter (fn [[_ n]] (> n 1)) (frequencies cids))))))))

;; ---------------------------------------------------------------------------
;; CI4 — lock verification
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
              lock entry is caught -- the case the version 1 payload could not
              see, because the effect row was not hashed"
      (let [effectful (assoc definition :definition/effect-row #{:host/http})]
        (is (= :definition/hash-mismatch
               (:reason (identity/verify-locked-definitions
                         lock [(assoc entry :definition effectful)]))))))))
