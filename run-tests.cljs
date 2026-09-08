;; nbb --classpath "src:test:$(clojure -Spath -M:test)" run-tests.cljs
;;
;; The ClojureScript half of this repository's suite. It had none until
;; 2026-09-08, while carrying 28 `.cljc` sources and 56 `.clj` tests -- so
;; every one of those 28 was a claim of portability that no run had ever
;; checked, in the repository that defines the language.
;;
;; That is not hypothetical here. `kotoba.lang.captp-runtime`'s `parse-natural`
;; used `parse-long`, which returns nil above 2^53 on this host, and wrapped
;; the nil in a TRUTHY `[nil i]` pair -- so the caller's
;; `(or (parse-natural ...) (throw ...))` guard never fired and a Syrup wire
;; natural decoded to nil instead of being refused. Clean on the JVM, silent
;; corruption here. Found only by running this host (PR #650); the ceiling test
;; below is what keeps it found.
;;
;; Anything added to `test/` as `.cljc` belongs in BOTH lists below. Being
;; required is not being run --
;; `scripts/verify-cljs-runner-completeness.cljs` in the superproject measures
;; this file against the directory and will say so.
;;
;; `kotoba.cli-test` is deliberately absent, and was RENAMED to `.clj` in the
;; same commit that added this file. It calls `(cli/read-contract)` at top
;; level, whose `:cljs` branch is a deliberate throw -- reading a file off disk
;; has no ClojureScript implementation here -- so requiring it threw before any
;; assertion ran. It was `.cljc`, which claims otherwise. The extension now
;; matches the fact.
;;
;; The 56 `.clj` tests are NOT here and are not all JVM-only: most are portable
;; in principle, and converting the highest-value ones (starting with
;; `captp_runtime_test.clj`, which exercises the wire codec this host already
;; got wrong once) is the follow-on work. Until then, treat this runner's green
;; as covering three namespaces, not the suite.
(ns run-tests
  (:require [cljs.test :as t]
            [kotoba.lang.causal-receipt-test]
            [kotoba.lang.captp-runtime-natural-ceiling-test]))

(defmethod t/report [:cljs.test/default :end-run-tests] [m]
  (println (str "\nnbb: " (:test m) " tests, " (:pass m) " passed, "
                (:fail m) " failed, " (:error m) " errors"))
  (when (pos? (+ (or (:fail m) 0) (or (:error m) 0)))
    (set! (.-exitCode js/process) 1)))

(t/run-tests 'kotoba.lang.causal-receipt-test
             'kotoba.lang.captp-runtime-natural-ceiling-test)
