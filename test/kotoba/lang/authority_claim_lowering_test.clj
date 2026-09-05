(ns kotoba.lang.authority-claim-lowering-test
  "Runs every head `lang/guest-grammar.edn` and `lang/surface-status.edn`
  claim is admitted through the frontend, so a claim cannot name an operation
  that does not exist.

  ## Which frontend this measures

  kotoba-sema, at whatever sha `deps.edn`'s `:test` alias pins. The sha is
  read from the classpath rather than restated here, because a pin written
  into a docstring is a second copy that goes stale silently -- and this one
  would have: it said 24a59c74 for the length of one afternoon, and the pin
  moved to 5fd767b5 before this file first landed.

  That is the same frontend `kotoba.lang.collections-conformance-test`
  executes its fixtures on, and it reaches
  `kotoba.compiler.frontend/analyze`, which `guest-grammar.edn`
  `:backends :compiler` names as that backend's entry point. So this measures
  the backend the claims name.

  It is not necessarily the sha `manifest/west.yml` pins. On 2026-09-03 the
  two differed -- `:test` at 24a59c74, west at df383ba0, seven commits ahead
  -- and both were MEASURED by running this file's own enumeration and probe
  over each in turn: 58 absent heads on each, symmetric difference empty.

  Agreement on one day is not a property, and the gap is real in one
  direction: a lowering added upstream and not yet pinned into `:test` reads
  as `:absent` here and would land in `stale-claim-exceptions` as though the
  frontend lacked it. What closes it is the `:test` pin tracking west. Until
  then an entry's `:closes-when` is the only place that distinguishes `no arm
  anywhere` from `an arm this pin cannot see` -- and
  `a-recorded-exception-must-still-be-a-gap` is what forces the distinction
  to be made: when the pin advanced to 5fd767b5, `count` gained an arm and
  that assertion went red naming it within the same merge.

  ## Why the probe is shaped this way

  Three failure modes were measured while building it, each of which produced
  a confident wrong answer:

  1. **Probing in the wrong position.** `(defn main [] (defrecord R [a]))`
     is refused, and not because `defrecord` is missing -- it is a top-level
     form. Twelve definition forms read as gaps until the probe learned to
     try top level and a `loop` body as well as call position.
  2. **Counting a position refusal as evidence.** Once top level was probed,
     EVERY head looked present: `(count 0) (defn main [] 0)` is refused `only
     ns, def, defn, and defn- are allowed at top level`, which is not the
     fallback string, so a classifier keyed on that string called it an arm.
     The sweep went from 71 absent to 0 and looked like good news.
  3. **Hand-listing the fallback strings.** That is what made (2) possible.

  The fix for all three is a NEGATIVE CONTROL. A head that certainly does not
  exist is probed in every cell -- position x arity x argument shape -- and its
  answer for that cell is the baseline. A real head's answer counts as evidence
  of an arm only where it DIFFERS from the control's answer in the same cell.
  Nothing about which strings mean absence is written down, so nothing about it
  can go stale.

  `the-negative-control-is-refused-everywhere` asserts the control behaves. If
  the control were ever admitted, every measurement in this file would be
  meaningless while still being green.

  ## What is a gap and what is not

  A head refused for its ARGUMENT is admitted. `(contains? #{:a} :a)` is
  refused `contains? requires a canonical typed map ...; got [:set :keyword]`,
  by name, pointing at `typed-set-contains` -- the authority is right and the
  frontend is right. Only the fallback -- the same answer the control gets --
  is absence. Conflating the two is how a sweep produces a green that means
  nothing."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.authority-claims :as ac]
            [kotoba.sema :as sema]))

(def ^:private control-head
  "A head no authority claims and no frontend admits. Long and arbitrary on
  purpose: a short plausible name could one day become real, and the day it
  did, every baseline in this file would silently become the answer for a head
  that exists."
  "zzz-no-such-head-9r3k")

(def ^:private argument-shapes
  ["0" ":a" "\"s\"" "[7 8 9]" "{:a 1}" "#{:a}" "true"])

(def ^:private positions
  "The three source positions a head can legally occupy. Definition forms are
  refused in call position for their position, not for their name."
  {:call (fn [call] (format "(defn main [] %s)" call))
   :top-level (fn [call] (format "%s (defn main [] 0)" call))
   :loop-body (fn [call]
                (format "(defn main [] (loop [i 0] (if (< i 1) %s i)))" call))})

(def ^:private cells
  "Every (position, arity, argument shape) the probe visits, cheapest first:
  the all-i64 arities in all three positions, then the typed shapes. Ordered
  so a head with an arm is usually found in the first cell or two; only a head
  with no arm anywhere pays for the whole grid."
  (vec (concat (for [n (range 0 5) p (keys positions)] [p n :i64 (str/join (repeat n " 0"))])
               (for [n (range 1 5) s argument-shapes p (keys positions)]
                 [p n s (str/join (repeat n (str " " s)))]))))

(defn- analyze-outcome
  "`::admitted`, or the refusal's first line."
  [source]
  (try (do (sema/analyze source) ::admitted)
       (catch Throwable e (first (str/split-lines (str (ex-message e)))))))

(defn- source-for [head [position _ _ args]]
  ((positions position) (format "(%s%s)" head args)))

(def ^:private baseline
  "The control's answer per cell -- the answer that means `nothing here`."
  (delay (into {} (for [c cells] [(subvec c 0 3) (analyze-outcome (source-for control-head c))]))))

(defn- measure
  "Probe one head. `:absent` only when EVERY cell answered exactly what the
  control answered in that cell."
  [head]
  (loop [[c & more] cells, refused nil]
    (if (nil? c)
      (or refused {:verdict :absent :msg (@baseline [:call 1 :i64])})
      (let [outcome (analyze-outcome (source-for head c))
            [position arity shape] c]
        (cond
          (= ::admitted outcome)
          {:verdict :admitted :position position :arity arity :shape shape}

          (= outcome (@baseline (subvec c 0 3)))
          (recur more refused)

          :else
          (recur more (or refused {:verdict :refused-for-argument :msg outcome
                                   :position position :arity arity :shape shape})))))))

(def ^:private result
  (delay (ac/validate (ac/load-guest-grammar) (ac/load-surface-status) measure)))

(deftest the-negative-control-is-refused-everywhere
  ;; Without this, a probe that admitted everything would report every claim
  ;; satisfied. The check that measures nothing must not answer like the check
  ;; that measured and found nothing wrong.
  (let [answers (vals @baseline)]
    (is (= (count cells) (count answers)))
    (is (not-any? #(= ::admitted %) answers)
        (str "the control head was ADMITTED somewhere; every 'this head exists'
              verdict in this file is then unfalsifiable. cells: "
             (pr-str (keep (fn [[c a]] (when (= ::admitted a) c)) @baseline))))
    (is (contains? (set answers) "operation has no admitted lowering")
        "the control never produced the frontend's own absence refusal, so the
         probe is not reaching the dispatch this file claims to measure")))

(deftest every-claimed-head-has-a-lowering
  (let [{:keys [ok? problems claimed absent]} @result]
    (println (format "CLAIMS\t%d\theads across %d authority keys (%d absent, %d recorded)"
                     claimed (count ac/claim-sources) absent
                     (count (ac/exceptions-by-head))))
    ;; The floor, stated here too so a reader of the output sees it: `claimed`
    ;; is checked against `min-claimed-heads` inside `validate`, and CLAIMS 0
    ;; cannot pass.
    (is (>= claimed ac/min-claimed-heads)
        (str "enumerated " claimed " claimed heads, floor is " ac/min-claimed-heads
             "; a run that enumerated nothing is not a run that found nothing wrong"))
    (is ok? (str "problems:\n"
                 (str/join "\n" (map #(str "  " (pr-str (dissoc % :why))) problems))))))

(deftest a-recorded-exception-must-still-be-a-gap
  ;; The converse. `stale-claim-exceptions` cannot name a head that works: an
  ;; excuse that outlives what it excuses hides the same defect pointing the
  ;; other way, and this is the only thing that will say the line is deletable.
  (let [{:keys [problems]} @result
        stale (filter #(= :recorded-exception-no-longer-applies (:type %)) problems)]
    (is (empty? stale)
        (str "recorded as gaps but now admitted -- delete from
              kotoba.lang.authority-claims/stale-claim-exceptions: "
             (pr-str (mapv (juxt :head :verdict) stale))))
    (is (pos? (count (ac/exceptions-by-head)))
        "no exception is recorded, so this assertion measured nothing")))

(deftest the-not-a-head-table-does-not-hide-a-head
  ;; The other converse: a `:sugar` key excluded as `not a head` must not be
  ;; one. Measured 2026-09-03 this failed on `:fn-ref` and `:invoke`, which
  ;; are both heads -- two live claims skipped by a table nothing checked.
  (let [entries (filter (comp nil? :head val) ac/feature-keys-that-are-not-heads)]
    (is (pos? (count entries)) "the table is empty; this assertion measured nothing")
    (doseq [[k _] entries]
      (testing (str k)
        (let [m (measure (name k))]
          (is (= :absent (:verdict m))
              (str (name k) " is excluded from the claim enumeration as `not a
                    call head`, but the frontend has an arm for it: "
                   (pr-str m)
                   ". Either give the entry a :head, or remove it from
                    feature-keys-that-are-not-heads.")))))))

(deftest every-key-of-both-authorities-is-read-or-recorded
  ;; The enumeration's own floor. A key added to either authority that names
  ;; heads and is neither read nor recorded would be missed in exactly the way
  ;; this file exists to prevent -- so a new top-level key is a failure until
  ;; someone decides, in writing, which it is.
  ;;
  ;; This is not hypothetical. `surface-status-claims` was first written over
  ;; `conformance-matrix/surface-status-sections`, which does not contain
  ;; `:checked-memory` -- so 61 heads went unenumerated while
  ;; `claim-sources` said the key was read.
  (let [namespaced? #(and (keyword? %) (namespace %))
        accounted (fn [authority]
                    (into #{} (comp (filter #(= authority (first %))) (map second))
                          (concat (keys ac/claim-sources) (keys ac/keys-not-read))))
        ;; keys that carry no head anywhere: prose, limits, shapes, metadata
        structural {:guest-grammar #{:backends :admission-limits :core-form-shapes
                                     :strict-grammar :maturity :function-semantics}
                    :surface-status #{:classification-rule :dispositions}}
        unaccounted (fn [authority m]
                      (remove #(or (namespaced? %)
                                   ((accounted authority) %)
                                   ((structural authority) %))
                              (keys m)))]
    (testing "guest-grammar.edn"
      (is (empty? (unaccounted :guest-grammar (ac/load-guest-grammar)))
          (str "keys neither read as claims nor recorded in keys-not-read: "
               (pr-str (sort (unaccounted :guest-grammar (ac/load-guest-grammar)))))))
    (testing "surface-status.edn"
      (is (empty? (unaccounted :surface-status (ac/load-surface-status)))
          (str "keys neither read as claims nor recorded in keys-not-read: "
               (pr-str (sort (unaccounted :surface-status (ac/load-surface-status)))))))
    (testing "every section named as read actually holds entries"
      ;; `:checked-memory` again: a section named in claim-sources that
      ;; resolves to nothing contributes no claims and says nothing about it.
      (doseq [section ac/claimed-surface-status-sections]
        (is (seq (get (ac/load-surface-status) section))
            (str section " is enumerated as a claim source and is empty"))))))
