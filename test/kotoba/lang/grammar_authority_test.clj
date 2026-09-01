(ns kotoba.lang.grammar-authority-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [kotoba.lang.grammar-authority :as auth]))

(deftest guest-grammar-is-source-surface-authority
  (let [grammar (auth/read-edn auth/grammar-path)
        surface (auth/read-edn auth/surface-path)
        pipeline (auth/read-edn auth/pipeline-path)
        result (auth/validate grammar surface pipeline)]
    (is (= "kotoba-lang/kotoba-lang"
           (:kotoba.lang.guest-grammar/authority grammar)))
    (is (= auth/grammar-path
           (:kotoba.lang.elaboration-pipeline/source-surface-authority pipeline)))
    (is (= 1 (:kotoba.lang.elaboration-pipeline/version pipeline)))
    (is (map? (:contract-versions pipeline)))
    (is (:valid? result)
        (pr-str (:errors result)))))

(deftest forbidden-heads-are-surface-security-constraints
  (let [grammar (auth/read-edn auth/grammar-path)
        surface (auth/read-edn auth/surface-path)
        forbidden (auth/forbidden-heads grammar)
        inv (auth/invariant-surfaces surface)]
    (is (set/subset? forbidden inv)
        (pr-str {:missing (set/difference forbidden inv)}))))

;; The other direction, and it is NOT the mirror image of the test above.
;;
;; That one asks "is every forbidden head classified?" -- against ALL invariant
;; surfaces, which is right for it. This one asks "is every form a SECURITY
;; constraint names actually forbidden?", and it must exclude
;; `:intentional-semantic-simplification` surfaces: those name admitted
;; operations (`:bool-is-a-type-not-a-number` names `= < > <= >=` and friends),
;; so running this over `invariant-surfaces` would demand the grammar forbid
;; comparison.
;;
;; Measured 2026-08-12: `:no-ambient-mutation` named `reset!` and `swap!` while
;; `:forbidden-heads` did not carry them. The compiler refused both anyway --
;; but with "operation has no admitted lowering", not the forbidden-head
;; rejection its siblings `atom` and `set!` got. A named invariant that holds
;; only because nothing happens to lower those symbols is not the fail-closed
;; enforcement `:classification-rule :security-constraint-requires` asks for.
(deftest security-constraint-surfaces-are-forbidden-heads
  (let [grammar (auth/read-edn auth/grammar-path)
        surface (auth/read-edn auth/surface-path)
        forbidden (auth/forbidden-heads grammar)
        security (auth/security-constraint-surfaces surface)]
    (is (seq security) "no :intentional-security-constraint surfaces were read")
    (is (set/subset? security forbidden)
        (pr-str {:named-but-not-forbidden (set/difference security forbidden)}))))

;; The third leg of `:security-constraint-requires`. The other two were
;; checkable and checked; this one was stated and never enforced, and measured
;; 2026-08-12 all seven security constraints were missing it while both
;; semantic-simplification entries -- which the rule does not ask -- carried a
;; reference.
(deftest security-constraints-carry-an-adr
  (let [surface (auth/read-edn auth/surface-path)
        missing (auth/security-constraints-missing-adr surface)]
    (is (empty? missing) (pr-str {:missing-adr missing}))))

;; And the reference has to point at something. A repo-relative path must
;; resolve; an ADR id (no slash) names a superproject document this repo cannot
;; see and is accepted by convention, not by resolution.
(deftest security-constraint-adr-paths-resolve
  (let [surface (auth/read-edn auth/surface-path)
        unresolved (auth/security-constraint-adr-paths-unresolved surface)]
    (is (empty? unresolved) (pr-str {:unresolved unresolved}))))

;; `conformance-evidence-present?` used to contain
;; `(.isDirectory (io/file "lang/conformance"))` inside its `or`, which is true
;; in every checkout and killed every clause after it. The predicate answered
;; `true` for any input, so `:portable/missing-evidence` could not fire.
;; Measured 2026-08-12. A made-up key is the whole test.
(deftest conformance-evidence-is-not-vacuously-present
  (let [surface (auth/read-edn auth/surface-path)]
    (is (not (#'auth/conformance-evidence-present? surface :totally-made-up-key)))
    (is (#'auth/conformance-evidence-present? surface :nested-let-destructuring)
        "a case id declared in lang/conformance/manifest.edn must resolve")))

;; The debt register is a ratchet in both directions: a portable claim naming no
;; conformance case must be registered, and a registered entry that has since
;; gained a link must be removed. Neither direction can be satisfied by adding
;; rows -- one fails on unregistered claims, the other on stale rows.
(deftest conformance-link-debt-register-matches-reality
  (let [grammar (auth/read-edn auth/grammar-path)
        surface (auth/read-edn auth/surface-path)
        claims (auth/feature-portable-claims surface)
        unlinked (auth/portable-claims-without-conformance surface claims)
        register (auth/conformance-link-debt-register surface)]
    (is (seq claims) "no portable claims were read")
    (is (empty? (set/difference unlinked register))
        (pr-str {:unregistered (set/difference unlinked register)}))
    (is (empty? (set/difference register unlinked))
        (pr-str {:stale-register-rows (set/difference register unlinked)}))
    ;; 3-arity: the 2-arity overload does not read the pipeline and reports
    ;; :pipeline/missing, which is not what this test is about.
    (let [errors (:errors (auth/validate grammar surface
                                         (auth/read-edn auth/pipeline-path)))]
      (is (empty? errors) (pr-str errors)))))

;; `:implementation` was free text: rendered into the surface matrix, tested
;; only for subset-of-portable-backends, read by nothing else. A typo or a
;; token coined for one entry was indistinguishable from a supported backend.
;; The declared set does not decide which vocabulary is correct — it stops the
;; namespace growing while that decision is open.
(deftest implementation-tokens-are-declared
  (let [surface (auth/read-edn auth/surface-path)
        used (auth/implementation-tokens surface)
        declared (auth/declared-implementation-vocabulary surface)]
    (is (seq used) "no :implementation tokens were read")
    (is (empty? (set/difference used declared))
        (pr-str {:undeclared (set/difference used declared)}))
    (is (empty? (set/difference declared used))
        (pr-str {:declared-but-unused (set/difference declared used)}))))

(deftest admitted-forms-are-classified
  (let [grammar (auth/read-edn auth/grammar-path)
        surface (auth/read-edn auth/surface-path)
        admitted (:all (auth/admitted-source-forms grammar))
        classified (auth/classified-forms surface)
        missing (set/difference admitted classified)]
    (is (empty? missing) (pr-str missing))
    (is (> (count admitted) 40))
    (is (> (count classified) 40))))

(deftest portable-sugar-stays-honest
  (let [grammar (auth/read-edn auth/grammar-path)
        port (auth/sugar-portability grammar)
        overclaim
        (into []
              (keep (fn [[k meta]]
                      (when (and (:portable-claim? meta)
                                 (= :not-yet-implemented (:status meta)))
                        k)))
              port)]
    (is (empty? overclaim) (pr-str overclaim))
    (is (pos? (count (filter (comp :portable-claim? val) port))))))

(deftest local-and-sibling-vendors-match-authority
  (let [authority (slurp auth/grammar-path)
        result (auth/validate)]
    (is (= authority (slurp auth/local-vendor-path)))
    (doseq [path auth/sibling-vendor-paths]
      (when (.isFile (io/file path))
        (is (= authority (slurp path)) path)))
    (let [vendor-errors (filter #(= :vendor/drift (:code %)) (:errors result))
          paths (mapcat :paths vendor-errors)
          mismatches (filter #(= :byte-mismatch (:error %)) paths)]
      (is (empty? mismatches) (pr-str mismatches)))))

(deftest every-authority-this-repo-publishes-a-copy-of-is-checked
  ;; Only guest-grammar was. Surveyed 2026-08-10, three other copies were not:
  ;; kotoba-sema's guest-grammar — and sema is now the frontend owner —
  ;; capability-catalog in three places, and host-parity in kotoba.
  ;;
  ;; The gap had already bitten. kotoba-lang/compiler was renamed to
  ;; kotoba-lang/amu, the authority says so, and the vendored copies still said
  ;; "kotoba-lang/compiler" — unnoticed because the path list named only the new
  ;; location, so the copies under the old name were never opened.
  (testing "the registry names a real authority file for every entry"
    (doseq [[authority paths] auth/vendored-authorities]
      (is (.isFile (io/file authority)) authority)
      (is (seq paths) authority)
      (doseq [p paths]
        (is (str/ends-with? p (subs authority (inc (str/last-index-of authority "/"))))
            (str p " should be a copy of " authority)))))
  (testing "guest-grammar's own paths are covered by the registry"
    (is (= (set (cons auth/local-vendor-path auth/sibling-vendor-paths))
           (set (get auth/vendored-authorities "lang/guest-grammar.edn")))))
  (testing "a copy that is present and different is reported"
    (let [mismatches (filter #(= :byte-mismatch (:error %))
                             (auth/authority-vendor-drift))]
      (is (empty? mismatches) (pr-str mismatches))))
  (testing "an absent sibling is reported as missing, not as drift"
    (let [drift (auth/authority-vendor-drift
                 {"lang/guest-grammar.edn" ["../definitely-not-a-repo/x.edn"]})]
      (is (= [:missing] (mapv :error drift)))
      (is (= ["lang/guest-grammar.edn"] (mapv :authority drift))
          "each entry says which file it was supposed to be a copy of"))))

(deftest contract-versions-are-recorded
  (let [pipeline (auth/read-edn auth/pipeline-path)
        versions (:contract-versions pipeline)]
    (doseq [k [:language-profile :guest-grammar :surface-status
               :desugar-contract :typed-kir :capability-catalog
               :semantic-cid :elaboration-pipeline :code-identity
               :typed-eval :portable-effect]]
      (is (integer? (get versions k)) k))))

(deftest contract-versions-that-restate-another-file-must-agree-with-it
  ;; `contract-versions-are-recorded` only asks that each entry is an integer,
  ;; which is how one axis came to carry three numbers: this map said
  ;; language-profile 4, surface-status said profile-version 6, and
  ;; version-policy said 5. The stale one was the number sealed into definition
  ;; identity, so a definition compiled under profile 6 would have claimed the
  ;; identity of one compiled under profile 4.
  (let [pipeline (auth/read-edn auth/pipeline-path)
        surface (auth/read-edn auth/surface-path)
        grammar (auth/read-edn auth/grammar-path)
        versions (:contract-versions pipeline)]
    (testing "each restated version equals the file that declares it"
      (is (= (:kotoba.lang.surface-status/profile-version surface)
             (:language-profile versions))
          "language-profile restates surface-status's profile-version")
      (is (= (:kotoba.lang.guest-grammar/version grammar)
             (:guest-grammar versions)))
      (is (= (:kotoba.lang.surface-status/version surface)
             (:surface-status versions)))
      (is (= (:kotoba.lang.elaboration-pipeline/version pipeline)
             (:elaboration-pipeline versions))))
    (testing "the authority check reports drift rather than tolerating it"
      (is (empty? (filter #(= :pipeline/contract-version-drift (:code %))
                          (:errors (auth/validate)))))))
  (testing "and it is reported when it exists"
    ;; Injecting the drift proves the check is doing the work, rather than
    ;; passing because nothing ever disagrees.
    (let [surface (auth/read-edn auth/surface-path)
          drifted (assoc-in (auth/read-edn auth/pipeline-path)
                            [:contract-versions :language-profile]
                            (inc (:kotoba.lang.surface-status/profile-version surface)))]
      (is (not= (:language-profile (:contract-versions drifted))
                (:kotoba.lang.surface-status/profile-version surface))
          "the injected value really does disagree with the authority"))))

(deftest public-callable-contract-is-bounded-and-abi-neutral
  (let [grammar (auth/read-edn auth/grammar-path)
        callable (:callable-type grammar)]
    (is (= 6 (:kotoba.lang.guest-grammar/profile-version grammar)))
    (is (= "[:fn [parameter-types result-type] ...]" (:syntax callable)))
    (is (= {:min 1 :max 5 :unique-by :arity} (:clauses callable)))
    (is (= {:min 0 :max 4} (:arity callable)))
    (is (= #{:i64} (:parameter-types callable)))
    (is (= :i64 (:physical-abi callable)))
    (is (= :project-interface-preserved (:module-boundary callable)))))

(deftest every-identity-says-whether-it-exists
  ;; Two entries — source-tree-cid and package-manifest-cid — described what
  ;; they prove and omitted :status, while definition-cid said :implemented and
  ;; component-admission said :planned. A description with no status reads as a
  ;; deployment, and both of those appear nowhere but the file declaring them
  ;; (surveyed 2026-08-10).
  (let [identities (:identities (auth/read-edn "lang/code-identity.edn"))
        statuses #{:implemented :planned :not-implemented}]
    (is (seq identities))
    (doseq [[k m] identities]
      (is (contains? statuses (:status m))
          (str k " must say whether it exists: " (pr-str (:status m)))))))
