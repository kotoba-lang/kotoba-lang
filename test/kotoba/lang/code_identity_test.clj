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
    ;; A real IR node, because kotoba-kir now refuses a :definition/kir that is
    ;; not one ("definition typed KIR must be an IR node with :op"). The claim
    ;; is unchanged -- entry order must not reach the CID -- and the block below
    ;; was already written this way.
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

;; ---------------------------------------------------------------------------
;; scope: checked definitions, effectful included (2026-09-02)
;; ---------------------------------------------------------------------------

(def ^:private contract-file "lang/code-identity.edn")

(deftest the-contract-and-the-fixtures-agree-that-effectful-definitions-are-in-scope
  (let [contract (get-in (read-edn contract-file) [:identities :definition-cid])
        manifest (read-edn (str conformance-root "manifest.edn"))
        cases (into {} (map (juxt :id identity)) (:cases manifest))]
    (testing "the contract no longer says pure-only"
      (is (= :closed-deterministic-checked-definition (:scope contract)))
      (is (= :sealed-by-effect-row (get-in contract [:effectful :status]))))
    (testing "a positive identity fixture carries a non-empty effect row and its
              expected CID is the frozen :effect-row-http vector"
      (let [tc (get cases :positive-identity-effectful-http)
            data (read-edn (str conformance-root (:file tc)))
            frozen (first (filter #(= :effect-row-http (:id %)) (:vectors vectors)))]
        (is (some? tc) "fixture must be in the manifest")
        (is (seq (:definition/effect-row data)))
        (is (= (:definition-cid frozen) (:expected-cid tc)))))
    (testing "a locked effectful definition is admitted, not merely identified"
      (let [tc (get cases :positive-locked-effectful-definition-admitted)
            data (read-edn (str conformance-root (:file tc)))]
        (is (some? tc) "fixture must be in the manifest")
        (is (seq (get-in data [:resolved 0 :definition :definition/effect-row])))
        (is (= :accept (:kind tc)))))
    (testing "the vocabulary is bridged, and the contract names the bridge:
              the compiler's [:cap/call id] rows are still refused by the
              identity itself, and effect-row-from-hir is the one route from
              them to the named-operation row the identity seals"
      (is (= :bridged (get-in contract [:effect-row-vocabulary :status])))
      (is (= "kotoba.kir.definition-identity/effect-row-from-hir"
             (get-in contract [:effect-row-vocabulary :adapter])))
      (is (= (get-in contract [:effect-row-vocabulary :refusal])
             (:message (identity/definition-error
                        (assoc definition :definition/effect-row #{[:cap/call 8]}))))
          "the recorded refusal literal must be the one the mechanism emits")
      (let [row (identity/effect-row-from-hir {:effects #{[:cap/call 8]}
                                               :named-operations #{:state/transact}}
                                              {:id->name {8 :state/transact}})]
        (is (= #{:state/transact} row))
        (is (nil? (identity/definition-error (assoc definition :definition/effect-row row)))))
      (is (= (get-in contract [:effect-row-vocabulary :unnamed-wire-id-refusal])
             (try (identity/effect-row-from-hir {:effects #{[:cap/call 200]}}
                                                {:id->name {8 :state/transact}})
                  nil
                  (catch clojure.lang.ExceptionInfo e (.getMessage e))))
          "the recorded unnamed-id refusal literal must be the one the bridge emits"))
    (testing "a positive identity fixture carries a row produced by the bridge"
      (let [tc (get cases :positive-identity-named-operation-row)
            data (read-edn (str conformance-root (:file tc)))]
        (is (some? tc) "fixture must be in the manifest")
        (is (= #{:state/transact :clock/now} (:definition/effect-row data)))
        (is (= (:expected-cid tc) (identity/definition-cid data)))))))

(deftest the-contract-lists-both-identity-implementations-and-a-direction
  (testing "two algorithms answer `what is this definition` today; the contract
            says so, names which one is the authority, and says which way the
            other one moves. Silence here is how the second answer stayed
            invisible for a month."
    (let [impls (:identity-implementations (read-edn contract-file))]
      (is (map? impls))
      (is (contains? impls :kotoba.kir/definition-identity))
      (is (contains? impls :kotoba.codebase/typed-code))
      (is (= :kotoba.kir/definition-identity (:authority impls)))
      (is (= :typed-code-adopts-definition-identity (get-in impls [:direction :decision])))
      (is (not= (get-in impls [:kotoba.kir/definition-identity :status])
                (get-in impls [:kotoba.codebase/typed-code :status]))
          "one is the authority and the other is the one being migrated"))))

(deftest the-contract-says-where-the-second-answer-is-persisted
  (testing "this entry was UNVERIFIED, and an unmeasured store reads exactly
            like an empty one. Measured 2026-09-02 store by store, and one
            layer-1 definition CID turned out to be published, signed and
            live -- in THIS repository, which the first survey did not grep."
    (let [persistence (get-in (read-edn contract-file)
                              [:identity-implementations :measured-difference :persistence])]
      (is (map? persistence)
          "a prose sentence ending in UNVERIFIED is not a measurement")
      (is (= :persisted-and-published (:verdict persistence)))
      (is (seq (:stores persistence)))
      (is (every? #(contains? % :found) (:stores persistence))
          "every enumerated store answers, so a store nobody looked at cannot
           be mistaken for one that came back empty")
      (is (some #(and (true? (:found %)) (pos? (:count %)) (seq (:cids %)))
                (:stores persistence))
          "and the store that found something says how many and which")
      (testing "`could not check` is a distinct answer from `not found`"
        (let [unchecked (filter #(= :could-not-check (:found %)) (:stores persistence))]
          (is (seq unchecked))
          (is (every? :reason unchecked)
              "with the reason it could not be checked, never a bare absence"))))))

(deftest the-migration-landed-as-an-opt-in-layer
  (testing "and the contract records the flag, its default, and what layer 2
            refuses rather than approximating"
    (let [direction (get-in (read-edn contract-file)
                            [:identity-implementations :direction])]
      (is (= :landed-as-opt-in-layer-2 (:status direction)))
      (is (re-find #"default-identity-version is 1" (:flag direction))
          "the default is layer 1 because a layer-1 CID is live")
      (is (seq (:refused-rather-than-approximated direction)))
      (is (seq (:remaining direction))
          "what layer 2 still cannot do is written down, not left to be
           discovered by the next caller"))))

(deftest every-declared-stage-has-an-implementation-entry-with-a-status
  (testing "a stage named in :stages with no :implementation entry is a plan
            that reads like a deployment -- the same shape :identities was
            fixed for on 2026-08-10. Nothing compared the two maps until
            :ci8 was added on 2026-09-02, so a stage could be declared and
            never say whether anything implements it."
    (let [contract (read-edn contract-file)
          stages (set (keys (:stages contract)))
          impls (:implementation contract)]
      (is (seq stages) "the contract must declare stages")
      (is (= stages (set (keys impls)))
          "every declared stage has an implementation entry, and no implementation
           entry names a stage the contract does not declare")
      (doseq [[stage entry] impls]
        (is (contains? entry :status)
            (str stage " must say whether it is implemented"))
        (is (contains? #{:implemented :partial :planned :not-implemented} (:status entry))
            (str stage " has an unrecognised :status " (pr-str (:status entry))))
        (when (= :implemented (:status entry))
          (is (seq (:evidence entry))
              (str stage " claims :implemented and must name its evidence")))))))
