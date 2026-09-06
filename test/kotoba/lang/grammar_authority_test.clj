(ns kotoba.lang.grammar-authority-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [kotoba.lang.grammar-authority :as auth]))

(def deferred-vendor-copies
  "Sibling copies KNOWN to be behind this authority, keyed by CHECKOUT path.

  DERIVED from `lang/vendored-copies.edn` since 2026-09-03. It used to be a
  literal map here, which made it the fourth place the registry of copies
  lived -- and the four disagreed, which is how kotoba-sema's copy of
  `capability-catalog.edn` went unmeasured entirely.

  A copy that is behind and is NOT recorded fails. Recording one is a decision
  with a date on it, not a way of not noticing: every entry carries `:as-of`,
  `:reason` and `:closes-when`, and `deferrals-are-not-a-bare-skip-list` below
  refuses one that does not.

  THE CONVERSE -- an entry whose copy is no longer behind -- is asserted by
  `scripts/check-vendored-copies-fleet.cljs`, not here. Its sibling paths exist
  only in the west monorepo layout, so what this file can see depends on which
  revision happens to be checked out beside it; the same assertion here would
  be a coin flip, and was for that reason only ever a printed note. The fleet
  script asks GitHub about the consumer's DEFAULT BRANCH, an answer that is the
  same everywhere, so over there it is a failure."
  (into {}
        (keep (fn [c]
                (when-let [d (auth/deferral-for (:checkout-path c))]
                  [(:checkout-path c) d])))
        (:copies auth/registry)))

(defn- deferred-vendor-drift?
  "A `:vendor/drift` error every one of whose mismatching paths is recorded in
  `deferred-vendor-copies`. Any other error, and any drift naming a path that
  is not recorded, is still an error."
  [e]
  (and (= :vendor/drift (:code e))
       (every? #(or (not= :byte-mismatch (:error %))
                    (contains? deferred-vendor-copies (:path %)))
               (:paths e))))

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
    ;; `:vendor/drift` for a copy recorded in `deferred-vendor-copies` is not
    ;; an authority defect; see that map for why each one is deferred and what
    ;; closes it. Every other error still fails.
    (let [errors (remove deferred-vendor-drift? (:errors result))]
      (is (empty? errors) (pr-str errors)))))

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
    (let [errors (remove deferred-vendor-drift?
                         (:errors (auth/validate grammar surface
                                                 (auth/read-edn auth/pipeline-path))))]
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
    (is (= authority (slurp auth/local-vendor-path))
        "this repository's own vendored copy is never deferred")
    (doseq [path auth/sibling-vendor-paths]
      (when (and (.isFile (io/file path))
                 (not (contains? deferred-vendor-copies path)))
        (is (= authority (slurp path)) path)))
    (let [vendor-errors (filter #(= :vendor/drift (:code %)) (:errors result))
          paths (mapcat :paths vendor-errors)
          mismatches (filter #(= :byte-mismatch (:error %)) paths)
          unexplained (remove #(contains? deferred-vendor-copies (:path %)) mismatches)
          deferred-and-behind (into #{} (map :path) mismatches)]
      (is (empty? unexplained)
          (str "a vendored copy is behind this authority and is not recorded in "
               "`deferred-vendor-copies`: " (pr-str (mapv :path unexplained))
               ". Either resync it, or record the path, the reason and the "
               "condition that closes it -- silence is not an answer."))
      ;; The two directions are not the same severity, and treating them the
      ;; same produces a FALSE RED. "Behind and not recorded" is a drift and
      ;; fails. "Recorded but no longer behind" is a stale record -- a
      ;; documentation defect -- and this repository's CI pins a kotoba
      ;; revision that a parallel stream is resyncing right now (kotoba #538,
      ;; `eee3f391`), so the entry is live in a monorepo checkout and stale in
      ;; CI at the same moment. Reported by name so it is removed on the next
      ;; pass, not asserted into a failure that depends on which sibling
      ;; revision the runner happened to check out.
      (doseq [[path _] deferred-vendor-copies]
        (when (and (.isFile (io/file path))
                   (not (contains? deferred-and-behind path)))
          (println (format "STALE-DEFERRAL\t%s\tno longer behind this authority; delete the entry"
                           path)))))))

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
  (testing "a copy that is present and different is reported, unless it is
            recorded in `deferred-vendor-copies` with a reason and a closing
            condition"
    (let [mismatches (filter #(= :byte-mismatch (:error %))
                             (auth/authority-vendor-drift))
          unexplained (remove #(contains? deferred-vendor-copies (:path %)) mismatches)]
      (println (format "DEFERRED\t%d/%d\tmismatching copies are recorded deferrals"
                       (- (count mismatches) (count unexplained)) (count mismatches)))
      (is (empty? unexplained) (pr-str unexplained))))
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

;; ---------------------------------------------------------------------------
;; ADR-the-authority-names-every-head-the-frontend-admits.
;;
;; `local-and-sibling-vendors-match-authority` above compares this file against
;; `../amu`, `../kotoba`, `../kotoba-sema` and `../grammar`. Those paths exist
;; only inside the west monorepo layout, the test guards each with
;; `(when (.isFile ...))`, and `authority-vendor-drift` reports an absent path
;; as `:missing`, which it tolerates. So in a single-repository clone it
;; compares exactly one copy -- this repository's own -- and reports green.
;;
;; Measured 2026-09-03 on main, before this wave: THREE of the four sibling
;; copies had drifted (amu one change behind at 580 lines, kotoba's two copies
;; at 401 against the authority's 601) and that test said nothing. A check that
;; could not run returned the value of a check that ran and found nothing
;; wrong -- ADR-2608136000's shape.
;;
;; Two additions, neither of which can be satisfied by absence.

(def ^:private authority-grammar-sha256
  "The sha256 of `lang/guest-grammar.edn` as of the 2026-09-05 resync wave.
  The same literal is pinned in amu, kotoba-sema and kotoba, so an authority
  edit that is not carried to all four goes red in the three that were left
  behind -- including in a clone where there is no sibling to compare against.

  Updating it is the wave: change this file, recompute, and carry the new
  digest to the other three in the same wave. A digest updated here alone is
  the defect this pin exists to make loud.

  Advanced 2026-09-05 from `811e3d5e` for :host/fs-browse (merge `0b320850`).
  Sibling pins follow (kotoba main dd8cf587, kotoba-sema#49 86565528). See `deferred-vendor-copies`.
  Advanced again 2026-09-05 for the string-search surface slice:
  :predicates gains string-index-of, string-contains? and string-split-count
  (kbb scripts-port wave 2; the compiler and KIR already implemented the
  latter two, and kotoba.runtime gains the CLJ interpreter bindings)."
  "3e41eb84a57a1fcc84dc0ec0b6a5ec1fd535c39e2cf6cfc14418fc1ec4567483")

(defn- sha256-hex [^bytes bs]
  (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") bs)]
    (apply str (map #(format "%02x" %) d))))

(deftest the-authority-digest-is-pinned-so-a-resync-wave-cannot-be-half-done
  (let [bytes (java.nio.file.Files/readAllBytes
               (.toPath (io/file auth/grammar-path)))
        actual (sha256-hex bytes)]
    (is (= authority-grammar-sha256 actual)
        (str "lang/guest-grammar.edn changed without the resync wave.\n"
             "  expected " authority-grammar-sha256 "\n"
             "  actual   " actual "\n"
             "Carry the new bytes to amu, kotoba-sema and kotoba (two copies),"
             " and update the pinned digest in all four repositories."))))

(deftest the-vendor-comparison-reports-how-many-copies-it-compared
  ;; The evidence floor. `authority-vendor-drift` returns only the copies it
  ;; had something to SAY about; a run that opened nothing and a run that
  ;; opened five identical files both return `[]`. So count the openable ones
  ;; and print the count, and refuse a run that compared none.
  (let [registry auth/vendored-authorities
        listed (mapcat val registry)
        present (filter #(.isFile (io/file %)) listed)
        drift (auth/authority-vendor-drift)
        mismatches (filter #(= :byte-mismatch (:error %)) drift)]
    (println (format "COMPARED\t%d/%d\tguest-grammar and its sibling authorities"
                     (count present) (count listed)))
    (is (pos? (count present))
        "not one vendored copy was openable; this run measured nothing")
    (is (some #(= auth/local-vendor-path %) present)
        "the local vendor copy is always present, so its absence means the
         registry stopped naming this repository's own copy")
    (println (format "DEFERRED\t%d\tsibling copies recorded as behind, by name"
                     (count deferred-vendor-copies)))
    (is (empty? (remove #(contains? deferred-vendor-copies (:path %)) mismatches))
        (str "vendored copies differ from their authority and are not recorded "
             "as deferred: "
             (pr-str (mapv (juxt :authority :path)
                           (remove #(contains? deferred-vendor-copies (:path %))
                                   mismatches)))))
    (testing "a sibling that is absent is reported as missing, never as compared"
      (is (not-any? #(= :missing (:error %))
                    (filter #(= auth/local-vendor-path (:path %)) drift))))))

(deftest admitted-builtins-names-the-kernel-families
  ;; It named three kernel heads while kotoba-sema's frontend admitted 114.
  ;;
  ;; The set has exactly one reader anywhere: `kotoba.grammar/admitted-heads`
  ;; in kotoba-lang/kotoba's vendored grammar loader, where a head missing
  ;; from it is reported as `:unknown-form`. Nothing here, in kotoba-sema or
  ;; in amu reads it, and nothing anywhere reads it to decide what the
  ;; COMPILER admits. So the understatement's consequence was one repository
  ;; calling 111 admitted heads unknown, and nothing failing.
  ;;
  ;; This test cannot read the frontend (kotoba-sema is not a dependency of
  ;; this repository), so it pins the SHAPE and the COUNT that were measured
  ;; against kotoba-sema 1afff23 on 2026-09-03. The equality itself is checked
  ;; where the frontend is on the classpath: kotoba-sema's own
  ;; `guest-grammar-vendor-test`.
  (let [grammar (auth/read-edn auth/grammar-path)
        builtins (into #{} (map name) (:admitted-builtins grammar))
        kernel (into #{} (filter #(or (str/starts-with? % "kernel-")
                                      (str/starts-with? % "slice-")))
                     builtins)
        ;; `kernel-load-` alone also matches `kernel-load-ptr`,
        ;; `kernel-load-idt` and `kernel-load-gdt-tss`, which are privileged
        ;; operations rather than window transfers -- the first draft of this
        ;; test counted 35 for that reason. The width suffix is what makes a
        ;; head a window transfer.
        windows (filter #(re-matches #"kernel-(load|store)-u(8|16|32|64)(-(4k|16k|64k))?" %)
                        kernel)
        carried (filter #(or (str/starts-with? % "slice-of-")
                             (contains? #{"slice-length" "slice-get"
                                          "slice-set!" "slice-sub"} %))
                        kernel)]
    (println (format "SCANNED\t%d\tadmitted-builtins (%d kernel heads)"
                     (count builtins) (count kernel)))
    (is (= 115 (count kernel))
        "the three kernel tables in kotoba-sema's frontend held 114 heads on
         2026-09-03, and 115 since fwstore's `kernel-uefi-alloc-region`; if
         that moves, this file and the four vendored copies move with it")
    (is (= 32 (count windows)) "four transfer widths by four window tiers")
    (is (= 8 (count carried)) "the carried slice family")
    (doseq [head ["kernel-load-u64-64k" "kernel-cmpxchg-u64" "kernel-dot-f32"
                  "kernel-dequant-dot-q6-k" "slice-sub" "kernel-xsetbv"
                  "kernel-uefi-call6" "kernel-swapgs"
                  "kernel-uefi-alloc-region"]]
      (is (contains? kernel head)
          (str head " is admitted by the frontend and must be named here")))))

;; ---------------------------------------------------------------------------
;; The registry of copies, and the discipline it has to keep
;; ---------------------------------------------------------------------------
;;
;; Added 2026-09-03 with `lang/vendored-copies.edn`. Before it, the list of
;; copies lived in four places that disagreed, and the deferral map was a
;; literal in this file with nothing checking its shape.

(deftest the-registry-answers-why-every-copy-exists
  ;; The load-bearing measurement. A copy whose reason is not recorded is a
  ;; copy the next resync wave will carry without knowing whether it should
  ;; exist at all -- which is how five of these outlived their reason.
  (let [copies (:copies auth/registry)]
    (println (format "SCANNED\t%d\tregistered copies of %d authorities"
                     (count copies) (count auth/vendored-authorities)))
    (is (seq copies)
        "the registry names no copies; this run measured nothing, which is not
         the same as finding nothing wrong")
    (doseq [c copies]
      (is (every? #(contains? c %) [:authority :repo :path :checkout-path
                                    :exists-because :read-by :dependant?])
          (str "a registered copy does not say why it exists: " (pr-str c)))
      (is (.isFile (io/file (:authority c)))
          (str "a registered copy names an authority file that is not in this "
               "repository: " (:authority c))))
    (testing "the local copy of each authority is registered, since it is the
              one file that is never allowed to be behind"
      (doseq [[authority paths] auth/vendored-authorities]
        (is (some #(not (str/starts-with? % "..")) paths)
            (str authority " has no copy inside this repository registered"))))))

(deftest deferrals-are-not-a-bare-skip-list
  ;; A skip list with no date, no reason and no closing condition is a way of
  ;; not noticing. Every entry owes all three.
  (let [ds auth/deferrals]
    (println (format "DEFERRED\t%d\tcopies recorded as knowingly behind" (count ds)))
    (doseq [[k v] ds]
      (is (map? v) (str "deferral " k " is not a map"))
      (doseq [field [:as-of :reason :closes-when]]
        (is (contains? v field)
            (str "deferral " (pr-str k) " has no " field
                 "; a deferral without one is a skip list entry")))
      (is (re-matches #"\d{4}-\d{2}-\d{2}" (str (:as-of v)))
          (str "deferral " (pr-str k) " has no dated :as-of")))))

(deftest every-deferral-names-a-registered-copy
  ;; Half of the converse. A deferral for a path nothing compares excuses
  ;; nothing, and reads as coverage. The OTHER half -- a deferral naming a copy
  ;; that is actually in sync -- needs the consumer's default branch and is
  ;; asserted in `scripts/check-vendored-copies-fleet.cljs`.
  (let [known (into #{} (map (fn [c] (str (:repo c) " " (:path c))))
                    (:copies auth/registry))]
    (is (seq known) "no registered copies; nothing to check deferrals against")
    (doseq [k (keys auth/deferrals)]
      (is (contains? known k)
          (str "deferral " (pr-str k) " names no registered copy. Registered: "
               (pr-str (sort known)))))))

(deftest retired-copies-do-not-come-back
  ;; A path deleted for a structural reason comes back the moment someone runs
  ;; a resync that still knows it. Named here so the return is a NAMED failure.
  ;; Only checkable for siblings that are present; the fleet script asks GitHub
  ;; and so can check them all.
  (let [retired auth/retired-checkout-paths
        present (filter #(.isFile (io/file %)) (keys retired))]
    (println (format "RETIRED\t%d\tpaths registered, %d resolvable here"
                     (count retired) (count present)))
    (is (seq retired) "no retired paths registered; the registry lost them")
    (doseq [p present]
      (is false
          (str "a retired copy came back: " p "\n  retired "
               (:retired (get retired p)) "\n  " (:reason (get retired p)))))
    (testing "every retired entry says when and why"
      (doseq [[p v] retired]
        (is (contains? v :retired) (str p " has no :retired date"))
        (is (contains? v :reason) (str p " has no :reason"))))))

(deftest the-registry-is-the-only-list-of-copies
  ;; Both scripts and this namespace read `lang/vendored-copies.edn`. A
  ;; hard-coded sibling path anywhere else is the fifth list, and the fifth
  ;; list is how the first four came to disagree.
  (let [script "scripts/check-grammar-authority.cljs"]
    (is (.isFile (io/file script))
        (str script " is missing, so this check could not run; refusing to
             report a pass"))
    (let [text (slurp script)]
      (is (str/includes? text auth/registry-path)
          (str script " does not read " auth/registry-path
               "; it is keeping its own list of copies"))
      (is (not (re-find #"\"\.\./(amu|kotoba|grammar|kotoba-sema|compiler)/resources/" text))
          (str script " hard-codes a sibling copy path. Derive it from "
               auth/registry-path " instead.")))))

(deftest the-vendor-count-is-files-opened-not-paths-listed
  ;; `:vendor-checked` was `(inc (count sibling-vendor-paths))` -- a constant.
  ;; It reported 7 in a clone that had compared one file. The two numbers are
  ;; now separate, and the one a reader trusts is the one that counts opens.
  (let [stats (:stats (auth/validate))
        openable (count (filter #(.isFile (io/file %))
                                (cons auth/local-vendor-path
                                      auth/sibling-vendor-paths)))]
    (println (format "COMPARED\t%d/%d\tguest-grammar copies openable here"
                     (:vendor-compared stats) (:vendor-registered stats)))
    (is (= openable (:vendor-compared stats))
        "the reported compared-count is not the number of files that opened")
    (is (pos? (:vendor-compared stats))
        "not one copy opened; this run measured nothing")
    (is (<= (:vendor-compared stats) (:vendor-registered stats))
        "more copies were compared than are registered")))

(deftest pure-s-expression-core-heads-are-not-yet-admitted
  ;; ADR-544 (pure S-expression core + cljk surface) step 1 admits the pure
  ;; head set (lam app rel query perform handle ref) as desugaring source
  ;; forms. Step 1 has landed IN THE FRONTEND and not here: measured
  ;; 2026-09-06 against kotoba-sema fcd4e35, `lam` `app` `ref` `perform`
  ;; compile and run (`(app (ref inc1) n)` produces the same HIR, KIR and
  ;; wasm32 bytes as `(inc1 n)`), while this authority admits none of the
  ;; seven.
  ;;
  ;; That drift is the point of this test. `lang/guest-grammar.edn` has five
  ;; vendored copies across four repositories (`lang/vendored-copies.edn`), so
  ;; admitting the heads here is a resync wave rather than an edit -- and
  ;; until it lands, a consumer that builds its admitted head set from this
  ;; authority rejects source the compiler accepts.
  ;;
  ;; Do NOT delete this when the wave lands. Move the four into the admitted
  ;; group below; the split is the assertion.
  (let [grammar (auth/read-edn auth/grammar-path)
        admitted (:all (auth/admitted-source-forms grammar))
        ;; Lowered by the frontend today; not yet in this authority.
        frontend-admitted '[lam app ref perform]
        ;; No existing primitive to desugar onto, so not admitted anywhere:
        ;; `rel`/`query` need a relational value model, `handle` needs an
        ;; effect handler (the abort ability's try/catch lowers a
        ;; `[:result T E]`; it does not resume).
        no-lowering-anywhere '[rel query handle]]
    (doseq [h frontend-admitted]
      (is (not (contains? admitted h))
          (str h " is admitted by the FRONTEND but not by this authority. If "
               "this now fails, the guest-grammar resync wave landed -- move "
               h " into the admitted group instead of deleting the test")))
    (doseq [h no-lowering-anywhere]
      (is (not (contains? admitted h))
          (str h " has no primitive to desugar onto and must not be admitted "
               "by the authority ahead of one")))))

(deftest the-pure-ref-spelling-collision-is-recorded-on-both-sides
  ;; `ref` is ADR-544's definition reference AND Clojure's STM constructor,
  ;; which `:no-ambient-mutation` forbids. The pure-head rewrite runs before
  ;; `:forbidden-heads` is consulted, so for two kotoba-sema commits the
  ;; security refusal was silently gone (`(let [r (ref n)] n)` was admitted).
  ;;
  ;; A reader arrives at this collision from either side: from the invariant,
  ;; asking "is `ref` refused?", or from the pure core, asking "what does
  ;; `(ref x)` do?". Both must say the same thing, so this holds the two
  ;; records to each other rather than trusting a comment on one of them.
  (let [surface (auth/read-edn auth/surface-path)
        grammar (auth/read-edn auth/grammar-path)
        invariant (get-in surface [:invariants :no-ambient-mutation])
        collision (:spelling-collision invariant)
        pure (get-in surface [:other-gaps :pure-s-expression-core])
        forbidden (into #{} (map symbol) (map name (:forbidden-heads grammar #{})))]
    (is (some? collision)
        "the invariant must record that `ref` has a second reading -- without
         it, `:surface` and `:forbidden-heads` read as `(ref x)` is never
         admitted, which stopped being true at kotoba-sema a2f86f87")
    (is (= 'ref (:head collision)))
    (is (contains? (set (:surface invariant)) 'ref)
        "the STM reading is still forbidden and `ref` must stay in :surface")
    (is (contains? forbidden 'ref)
        "and in :forbidden-heads -- the narrowing admits a different head that
         shares a spelling, it does not widen this constraint")
    (is (not (contains? (set (:admitted-via-elaboration invariant)) 'ref))
        "`ref` is NOT admitted via the local-state elaboration; putting it in
         that set would subtract it from the required-forbidden set and say
         the STM reading had been let in")
    (is (contains? (set (:operations pure)) 'ref)
        "and the pure core must claim it, or the collision record describes
         an admission nothing makes")
    ;; The fail-closed half. These five are what make the collision
    ;; containable: the spelling is admitted for definitions, and nothing can
    ;; be DONE with an STM-shaped ref.
    (is (= '#{deref dosync alter commute ref-set}
           (set (get-in collision [:fail-closed :refused])))
        (pr-str (get-in collision [:fail-closed :refused])))
    (is (string? (:pinned-by collision))
        "a fail-closed claim with no test named is a sentence, not a gate")))

(deftest no-pure-head-collides-with-a-security-surface-unrecorded
  ;; The generalisation of the `ref` bug. A pure head that shares a spelling
  ;; with a forbidden one takes that refusal away silently, because the
  ;; pure-head rewrite runs BEFORE `:forbidden-heads` is consulted -- the
  ;; frontend never sees the head at all. Nothing would have said so; `ref`
  ;; was found by reading, not by a check.
  ;;
  ;; So: every pure head that also appears in a security surface must carry a
  ;; `:spelling-collision` record. Measured 2026-09-06, `ref` is the only one
  ;; -- but "only one today" is exactly the claim that goes stale, and the
  ;; cost of the next one is a security constraint that stops firing.
  (let [surface (auth/read-edn auth/surface-path)
        grammar (auth/read-edn auth/grammar-path)
        pure-heads '#{lam app ref perform handle rel query}
        forbidden (into #{} (map symbol) (map name (:forbidden-heads grammar #{})))
        security-surfaces
        (into {}
              (keep (fn [[k v]]
                      (when (= :intentional-security-constraint (:disposition v))
                        (let [hit (set (filter pure-heads
                                               (map (comp symbol name) (:surface v #{}))))]
                          (when (seq hit) [k hit])))))
              (:invariants surface {}))
        recorded (into #{}
                       (keep (fn [[_ v]] (get-in v [:spelling-collision :head])))
                       (:invariants surface {}))
        colliding (into (set (mapcat val security-surfaces))
                        (filter pure-heads forbidden))]
    (is (pos? (count pure-heads)) "the head set is empty; this measured nothing")
    (is (empty? (set/difference colliding recorded))
        (str "pure head(s) share a spelling with a forbidden or security-"
             "constrained head and no invariant records the collision: "
             (pr-str (set/difference colliding recorded))
             ". The pure-head rewrite runs before :forbidden-heads, so this "
             "is a refusal that has silently stopped firing -- add a "
             ":spelling-collision record naming the admitted shape, what "
             "everything else refuses with, and the fail-closed evidence."))
    (is (empty? (set/difference recorded colliding))
        (str "a :spelling-collision is recorded for a head that no longer "
             "collides -- delete it: " (pr-str (set/difference recorded colliding))))))

(deftest the-surface-status-record-of-the-pure-core-matches-this-authority
  ;; The frontend/authority drift above is recorded in surface-status so a
  ;; reader finds it without running the compiler. This test keeps the two
  ;; records from disagreeing: every head surface-status lists as admitted by
  ;; the frontend must still be absent from the authority, and every head it
  ;; lists as having no lowering must be absent too. When the resync wave
  ;; lands, both files move together or this goes red.
  (let [grammar (auth/read-edn auth/grammar-path)
        surface (auth/read-edn auth/surface-path)
        admitted (:all (auth/admitted-source-forms grammar))
        entry (get-in surface [:other-gaps :pure-s-expression-core])
        operations (set (:operations entry))
        not-admitted (set (map (comp symbol name) (keys (:not-admitted entry))))]
    (is (some? entry)
        "surface-status must carry :pure-s-expression-core -- it is the only
         place the frontend/authority split is written down")
    (is (= '#{lam app ref perform} operations)
        (pr-str operations))
    (is (= '#{rel query handle} not-admitted)
        (pr-str not-admitted))
    (is (empty? (set/intersection operations not-admitted))
        "a head cannot be both lowered and unlowerable")
    (is (empty? (set/intersection admitted (set/union operations not-admitted)))
        (str "surface-status says these heads are not in the authority, but the "
             "authority admits: "
             (pr-str (set/intersection
                      admitted (set/union operations not-admitted)))))))
