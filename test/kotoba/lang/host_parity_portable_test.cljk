(ns kotoba.lang.host-parity-portable-test
  "kotoba.lang.host-parity's OTHER host: measured 2026-09-08, porting
  kotoba.lang.host-parity-test as-is failed 20 of 24 assertions on this host,
  every one comparing REAL catalog data (read from `lang/host-parity.edn`,
  which has no ClojureScript implementation here) against the honest
  `:unavailable` stub `catalog*` falls back to on cljs. That was correctly
  judged not a defect and not forced green -- see the source's own
  `unavailable-catalog` docstring.

  What was missing is the mirror image: a test of the STUB's own contract,
  which is real, well-defined, and true on THIS host specifically.

  This file is deliberately `.cljs`, not `.cljc`: `catalog*`'s `:clj` branch
  DOES successfully read the real `lang/host-parity.edn` in this repository
  (measured directly -- the JVM `.clj` sibling of this assertion set is
  simply false there, 59 real imports and version 2, not the empty stub). A
  `.cljc` with these exact assertions would fail every one of them the moment
  cognitect's JVM test-runner picked it up, which it does for any `.clj`/
  `.cljc` under `test/`. Making the assertions themselves host-conditional
  would just duplicate kotoba.lang.host-parity-test's real-catalog checks
  into a second file; keeping this one `.cljs`-only says plainly that what it
  measures is cljs-specific, and it is still discovered as portable evidence
  by `scripts/verify-portable-source-tested-on-one-host.cljs` in the
  superproject, whose definition of \"portable\" is \"not `.clj`\", not
  \"`.cljc` specifically\"."
  (:require [cljs.test :refer [deftest is testing] :include-macros true]
            [kotoba.lang.host-parity :as hp]))

(deftest the-cljs-catalog-says-it-was-not-loaded
  ;; The mirror of code_identity's "a-loaded-catalog-says-it-was-loaded":
  ;; this host's `catalog*` never reaches for `fs`, by design (the namespace
  ;; runs in the browser too, so it must not decide on its own to inject a
  ;; file-read capability it does not have), so `catalog-available?` must be
  ;; false here and the source must say why.
  (is (false? (hp/catalog-available?)))
  (is (= :unavailable (:kotoba.lang.host-parity/source (hp/catalog))))
  (is (= 0 (:kotoba.lang.host-parity/version (hp/catalog)))))

(deftest the-unavailable-catalog-is-empty-and-honest-about-it
  (testing "empty AND says so -- not merely empty, which before 2026-09-08 was
            indistinguishable from a real catalog with no host imports"
    (is (= {} (:imports (hp/catalog))))
    (is (= [] (get-in (hp/catalog) [:conformance :cases])))))

(deftest report-on-this-host-is-catalog-unavailable-not-below-threshold
  ;; `:below-threshold` and `:catalog-unavailable` were the same word before
  ;; 2026-09-08. On the host that cannot read the catalog at all, the honest
  ;; status is specifically :catalog-unavailable.
  (let [r (hp/report)]
    (is (= :catalog-unavailable (:status r)))
    (is (false? (:catalog-available? r)))))

(deftest score-on-this-host-reports-catalog-unavailable-and-not-ok
  (let [s (hp/score)]
    (is (false? (:catalog-available? s)))
    (is (= 0 (:total s)))
    (is (false? (:ok? s))
        "an empty required-import set can never meet a >0 minimum-required-ratio")))

(deftest matrix-is-empty-with-no-catalog
  (is (= [] (hp/matrix))))

(deftest availability-is-unknown-for-any-import-with-no-catalog
  ;; `:unknown-import`, never `:available` -- an unreadable catalog must never
  ;; be misreported as a workspace with no host imports.
  (is (= :unknown-import (hp/availability :sha256-hex :jvm)))
  (is (= :unknown-import (hp/availability :anything-at-all :browser)))
  (is (= :unknown-host (hp/availability :sha256-hex :fleet))
      "host validity is checked before catalog membership"))

(deftest guard-host-import-denies-with-no-catalog
  (let [denied (hp/guard-host-import :sha256-hex :browser)]
    (is (false? (:kotoba.host/ok? denied)))
    (is (= :host-absent (:kotoba.host/denied denied)))
    (is (= :unknown-import (:status denied))
        "the L5 gate fails closed even though the reason is 'could not
         measure', not 'measured absent'")))

(deftest conformance-with-no-catalog-is-vacuously-total-zero
  ;; This is deliberately NOT asserted as a pass on its own: `:total 0` here
  ;; is meaningful only paired with `catalog-available? false` above, which is
  ;; exactly the distinction :below-threshold vs :catalog-unavailable exists
  ;; to preserve. A caller must check :catalog-available? before trusting
  ;; :ok? true on zero cases.
  (let [r (hp/run-conformance)]
    (is (= 0 (:total r)))
    (is (= [] (hp/conformance-cases)))
    (is (true? (:ok? r)))))
