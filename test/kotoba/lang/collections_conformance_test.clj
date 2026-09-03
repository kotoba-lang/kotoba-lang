(ns kotoba.lang.collections-conformance-test
  "Executes the `collections/` conformance cases, so `:set-literal`'s
  `:conformance :bounded-set-literal-and-operations` in `surface-status.edn`
  is something this repository measures rather than states.

  ## Why this file exists

  `:bounded-set-literal-and-operations` declares
  `:required-backends #{:kir :wasm32-kotoba-v1}`. Measured 2026-09-03 across
  the whole toolchain, NOTHING executed it on either:

    * amu's `kotoba.compiler.lang-conformance` drives exactly those two
      backends -- and only over its OWN
      `resources/kotoba/lang-conformance/pilot-manifest.edn`, which does not
      declare this case. It never reads this manifest.
    * `kotoba`'s `kotoba.language-conformance-test` DOES read this manifest,
      and drives `kotoba.runtime/wasm-binary`, the legacy form walker -- a
      backend this manifest's `:backends` map does not name at all. It also
      reads the manifest through its own PINNED kotoba-lang, not this one.
    * this repository ran the `local-state/` cases and nothing else.

  So the case was declared on two backends, executed on a third that was not
  declared, and the two facts had nowhere to meet. That is ADR-2608136000's
  shape at the level of the manifest: a green suite reporting on something
  other than what the manifest claims to require.

  ## What is pinned here

  1. every `collections/` case the manifest declares is EXECUTED on `:kir` and
     must answer its `:expect`;
  2. the set of cases executed equals the set the manifest declares -- so
     dropping `:kir` from a case's `:required-backends`, or dropping the case,
     turns this red rather than quietly shrinking the run;
  3. an evidence floor: zero executed cases is a failure, not a clean run, and
     so is fewer than `min-executed-run-cases` -- a case may stop running only
     by being recorded as deferred AND the floor being confronted;
  3b. a case recorded as unexecutable on `:kir` is asserted to be REFUSED, and
     refused with the message recorded next to it, so `:unexecuted-backends`
     cannot become a parking space and a fixture failing for some other cause
     cannot be counted as evidence for the recorded one;
  4. the directory and the manifest agree in both directions;
  5. the negative case is refused with its pinned message AND for its pinned
     reason, so a fixture that fails for some other cause cannot be counted as
     the refusal it was written to demonstrate.

  `:wasm32-kotoba-v1` is the other half of every pure-product case's
  requirement and has no runner in this repository; that gap is recorded in the
  manifest's `:runners`/`:deferred-runners` and checked by
  `kotoba.lang.conformance-matrix-test`, not papered over here."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private conformance-root "lang/conformance")
(def ^:private entry-prefixes
  "The whole of `collections/`.

  It was `[\"collections/map_\" \"collections/set\"]` until 2026-09-03, and the
  reason given for not widening it to `collections/` was that doing so would
  silently pull in three fixtures that could not run: measured against sema
  `24a59c74`, `(count [7 8 9])` was refused `operation has no admitted
  lowering`, exactly as `(conj #{:a} :b)` had been. Measured again against
  sema `7e277da6`, which dispatched `count` and the pair accessors onto the
  primitives each collection already had:

    destructuring.kotoba   RAN, and had run all along -- the note above
                           grouped it with the other two and was wrong about
                           it; it uses `nth` with a default and typed-map
                           `:keys` destructuring, neither of which ever needed
                           `count`. It was unexercised for no reason at all.
    higher_order.kotoba    now runs; it was refused `expected i64, got
                           vector-i64` because it spells `pair-first` over a
                           `filter` result, which is a bounded vector.
    vector.kotoba          could not run YET: `peek` and `pop` had no
                           lowering on any receiver, and Clojure's vector
                           `pop` -- all but the LAST item -- could not be
                           built from `vector-drop`, which drops from the
                           front.

  That last one now runs too. Later on 2026-09-03 kotoba-kir `b021a0d1`
  gained `vector-take` -- `vector-drop`'s mirror, keeping the HEAD where drop
  keeps the tail -- and kotoba-sema `cb5cf553` gained the heads, so the
  fixture executes to its 20 and its `:kir` entry moved from
  `:unexecuted-backends` to `:executed-by`. `min-executed-run-cases` rose
  from 4 to 5 in the same change: a case that starts running raises the
  ratchet, or the floor would keep passing if it stopped again.

  So nothing is pulled in silently any more: all five are matched, all five
  are driven, and each one records the runner that drives it. The third case
  below still asserts that anything recorded as unexecutable STILL IS -- a
  `:unexecuted-backends` entry that nothing checks is a parking space -- and
  now says out loud when it has nothing to assert about.

  Still a VECTOR rather than a single string, because the shape is the
  runner's contract with `owned-entry?` and a future slice may need a prefix
  of its own again."
  ["collections/"])

(defn- owned-entry? [entry]
  (boolean (some #(str/starts-with? (str entry) %) entry-prefixes)))

(def ^:private this-runner
  "kotoba-lang/kotoba-lang kotoba.lang.collections-conformance-test")

(defn- manifest []
  (edn/read-string (slurp (str conformance-root "/manifest.edn"))))

(defn- collections-cases [m]
  (filter #(owned-entry? (:entry %)) (:cases m)))

(defn- source-of [case]
  (slurp (str conformance-root "/" (:entry case))))

(defn- refusal-of [source]
  (try (do (sema/analyze source) nil)
       (catch Throwable e e)))

(def ^:private min-executed-run-cases
  "Floor on how many `:run` cases this file actually executes. A ratchet:
  raise it when a case starts running, never lower it. Without it, moving
  every case into `:unexecuted-backends` would leave this file green while
  executing nothing -- and `(= declared executed)` alone cannot see that,
  because it would compare two empty sets.

  Raised from 4 to 5 on 2026-09-03 when `vector.kotoba` started running, which
  is the ratchet doing its job: the case that moved out of
  `:unexecuted-backends` is now one the floor requires."
  5)

(deftest every-collections-case-that-requires-kir-executes-to-its-expected-value
  (let [cases (collections-cases (manifest))
        runnable (filter #(= :run (:kind %)) cases)
        {driven false deferred true}
        (group-by #(contains? (:unexecuted-backends %) :kir) runnable)
        executed (atom #{})]
    (doseq [case driven]
      (testing (str (:id case))
        (is (contains? (:required-backends case) :kir)
            "a :run case matched by this runner that does not require :kir has
             no runner at all; either it requires :kir or it does not belong
             to this class")
        ;; The other direction of the manifest check: `validate-execution`
        ;; verifies that a case's `:executed-by` accounts for its required
        ;; backends, but it cannot tell whether the runner named there runs.
        ;; This can.
        (is (= this-runner (get-in case [:executed-by :kir]))
            "the case does not name this runner as the one that drives :kir")
        (let [hir (sema/analyze (source-of case))
              value (kir/execute (kir/lower hir)
                                 (symbol (or (:function case) "main"))
                                 (vec (:args case)))]
          (is (= (get-in case [:expect :kotoba]) (long value)))
          (swap! executed conj (:id case)))))
    (println (format "EXECUTED\t%d\t%s :run cases on :kir (%d deferred)"
                     (count @executed) (str/join ", " entry-prefixes)
                     (count deferred)))
    (is (pos? (count @executed))
        "no collections case executed; a run that measured nothing is not a run
         that found nothing wrong")
    (is (>= (count @executed) min-executed-run-cases)
        (str "executed " (count @executed) ", floor is " min-executed-run-cases
             "; a case may only stop running by being recorded as deferred AND
             the floor being confronted, never by quietly disappearing"))
    (is (= (into #{} (map :id) driven) @executed)
        "a declared :run case did not execute")))

(deftest a-case-recorded-as-unexecutable-on-kir-still-is
  ;; `:unexecuted-backends` must not become a parking space. A case parked
  ;; there is asserted to be REFUSED, and refused with the message recorded --
  ;; not merely refused, since a fixture failing for some other cause would
  ;; otherwise count as evidence for the recorded one (ADR-2608136000
  ;; question 6). If it starts working, this goes red and the entry moves.
  ;;
  ;; As of 2026-09-03 NOTHING is parked on `:kir`: `vector.kotoba` was the
  ;; last one and it runs. So this body iterates an empty sequence, and a test
  ;; that iterates an empty sequence and reports success is the exact shape
  ;; ADR-2608136000 exists to stop -- the check that measured nothing
  ;; answering like the check that measured and found nothing wrong. It is
  ;; kept, because it is what will catch the NEXT case someone parks, and the
  ;; printed line SAYS which of the two states produced the green. There is
  ;; deliberately no floor on the parked count: zero parked is the goal, and a
  ;; floor would forbid reaching it. The evidence floor lives in the test
  ;; above, over the cases that do run, and it went up when this one emptied.
  (let [deferred (filter #(and (= :run (:kind %))
                               (contains? (:unexecuted-backends %) :kir))
                         (collections-cases (manifest)))]
    (println (format "DEFERRED\t%d\t%s :run cases parked on :kir%s"
                     (count deferred) (str/join ", " entry-prefixes)
                     (if (zero? (count deferred))
                       " (NOTHING PARKED -- this deftest asserted nothing)"
                       "")))
    (doseq [case deferred]
      (testing (str (:id case))
        (let [refusal (refusal-of (source-of case))]
          (is (some? refusal)
              "the case is recorded as unexecutable on :kir and it compiles;
               move the entry to :executed-by and run it")
          (when refusal
            (let [recorded (get-in case [:unexecuted-backends :kir :refused-with])]
              (is (string? recorded)
                  "a :kir deferral must record :refused-with, the message it
                   was measured to produce; without it this check would assert
                   only that SOMETHING failed")
              (is (= :kotoba.error/subset-reject
                     (:kotoba.error/code (ex-data refusal)))
                  (ex-message refusal))
              (when (string? recorded)
                (is (str/includes? (ex-message refusal) recorded)
                    (str "the recorded refusal and the measured one differ: "
                         (ex-message refusal)))))))))))

(deftest the-negative-case-is-refused-for-its-own-reason
  (doseq [case (filter #(= :expect-error (:kind %)) (collections-cases (manifest)))]
    (testing (str (:id case))
      (let [refusal (refusal-of (source-of case))]
        (is (some? refusal) "the fixture was admitted")
        (when refusal
          (is (= this-runner (get-in case [:executed-by :compiler-admit]))
              "the case does not name this runner as the one that drives
               :compiler-admit")
          (is (str/includes? (ex-message refusal) (:error-contains case))
              (ex-message refusal))
          ;; The message alone is not the reason. A heterogeneous set literal
          ;; must be refused as an ITEM TYPE mismatch against the set's own
          ;; type, carrying the required and actual types as data -- not by an
          ;; unbound symbol or an arity error that happens to mention a set.
          (let [data (ex-data refusal)]
            (is (= :keyword (:kotoba.error/expected data)))
            (is (= :i64 (:kotoba.error/actual data)))))))))

(deftest the-fixture-directory-and-the-manifest-agree
  (let [on-disk (->> (file-seq (java.io.File. (str conformance-root "/collections")))
                     (filter #(.isFile ^java.io.File %))
                     (map #(subs (.getPath ^java.io.File %) (inc (count conformance-root))))
                     (filter owned-entry?)
                     set)
        declared (into #{} (map :entry) (collections-cases (manifest)))]
    (is (pos? (count on-disk)) "the fixture directory was not read")
    (is (= on-disk declared)
        (str "fixtures with no case: " (pr-str (sort (remove declared on-disk)))
             "; cases with no fixture: "
             (pr-str (sort (remove on-disk declared)))))))
