(ns kotoba.lang.grammar-authority-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [kotoba.lang.grammar-authority :as auth]))

(def deferred-vendor-copies
  "Sibling copies that are KNOWN to be behind this authority, each with the
  reason it has not been resynced and the condition that closes it.

  A copy that is behind and is NOT listed here fails. Listing one is a
  decision with a date on it, not a way of not noticing -- and an entry whose
  copy is no longer behind ALSO fails, so the map cannot outlive what it
  excuses.

  This map exists because `local-and-sibling-vendors-match-authority` measures
  a different thing depending on where it runs. Its sibling paths exist only
  in the west monorepo layout, so in a single-repository clone it compares one
  file -- this repository's own copy of itself -- and reports green. Measured
  2026-09-03 before the resync wave, three of the four sibling copies had
  drifted and it said nothing. Recording the deferral makes the state the same
  in both places: what is behind is behind on the record."
  {"../kotoba/resources/kotoba/lang/guest-grammar.edn"
   {:as-of "2026-09-03"
    :reason
    (str "kotoba's five classpath copies all agree WITH EACH OTHER at the "
         "pre-wave authority, and its own check "
         "(`every-guest-grammar-on-the-classpath-is-the-same-bytes`) is "
         "stricter than this one -- every copy byte-identical, no exemption. "
         "Resyncing the two it ships alone would break it, rightly, because "
         "`io/resource` answers with whichever copy comes first. Moving the "
         "three that arrive from its pinned amu, kotoba-lang and kotoba-sema "
         "means advancing an amu pin 106 commits behind main: a compiler "
         "migration, not a grammar resync.")
    :recorded-there
    "kotoba docs/ADR-the-vendored-grammar-is-compared-where-it-is-read.md and test/kotoba/guest_grammar_vendor_test.clj, which pins BOTH digests -- the one its copies are at and the one they owe"
    :closes-when "kotoba advances its amu pin and resyncs both copies"}
   "../kotoba/vendor/grammar/resources/kotoba/lang/guest-grammar.edn"
   {:as-of "2026-09-03"
    :reason "the second of kotoba's two shipped copies; see the entry above"
    :closes-when "kotoba advances its amu pin and resyncs both copies"}
   "../amu/resources/kotoba/lang/guest-grammar.edn"
   {:as-of "2026-09-03"
    :reason
    (str "behind by the map-literal VALUE type, which landed in kotoba-sema "
         "bfb19d71 (recorded there as ADR 0033) and in this authority in the "
         "same wave. amu is not red on "
         "its own main: its pinned digest still matches its own copy, and its "
         "kotoba-sema pin (df383ba0) predates the change. It goes red the "
         "moment it advances that pin without resyncing the copy, which is "
         "exactly amu ADR 0330's postscript -- the pin and the vendored "
         "grammar move together, because io/resource answers with whichever "
         "of the two classpath copies comes first.")
    :recorded-there
    "amu deps.edn (the grammar-sha256 line beside the kotoba-sema pin) and test/kotoba/compiler/guest_grammar_vendor_test.clj"
    :closes-when "amu advances its kotoba-sema pin to 0a5dcc2b or later and resyncs its copy and both digests in the same commit"}})

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
  "The sha256 of `lang/guest-grammar.edn` as of the 2026-09-03 resync wave.
  The same literal is pinned in amu, kotoba-sema and kotoba, so an authority
  edit that is not carried to all four goes red in the three that were left
  behind -- including in a clone where there is no sibling to compare against.

  Updating it is the wave: change this file, recompute, and carry the new
  digest to the other three in the same wave. A digest updated here alone is
  the defect this pin exists to make loud.

  Advanced 2026-09-03 from `6e1202fd` together with kotoba-sema `0a5dcc2b`,
  which carries the same bytes. The change is the map literal's VALUE type:
  this authority said it was `always :i64` and gave the reason with it --
  \"a literal has no annotation and inference runs after desugaring, so the
  value half cannot be read off the source\" -- and kotoba-sema's
  `desugar-map` now reads it off the literal's own values, so the sentence
  would have been false the moment the frontend landed. amu and kotoba are
  behind; see `deferred-vendor-copies`."
  "871f3873ae30a33ba7461c8664094b42396c0c4d79612668d11b0b29a2c0172f")

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
