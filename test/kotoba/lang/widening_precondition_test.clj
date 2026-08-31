(ns kotoba.lang.widening-precondition-test
  "The preconditions ADR-2608301500 put on the two widening paths, measured.

  `surface-status.edn` lists what must be true before `atom`/`swap!`/`reset!`
  or `throw`/`try`/`catch` may be admitted. A list is not a gate: nothing here
  asked whether any entry on it holds, so `:precondition-status` was derived by
  hand and could drift the moment either side moved.

  One of the four state preconditions is the reason this file exists.
  `:capability-handle-storage-rejected-by-schema` reads like a check that
  passes. It holds because a capability handle is not a value in the source
  surface AT ALL -- `cap-acquire` and `has-capability?` are in the grammar's
  admitted-builtins and the frontend has no type signature or lowering for
  either. Absence and enforcement return the same answer, and the day handles
  become values the precondition stops holding with nothing to say so.

  So the refusals are pinned here by their exact message. When someone gives
  handles a type, these tests go red, and the decision they force -- write the
  storage rejection, or widen without it -- is made deliberately rather than by
  not noticing."
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kotoba.sema :as sema]))

(def ^:private surface (delay (edn/read-string (slurp "lang/surface-status.edn"))))

(defn- entry [id]
  (letfn [(walk [x]
            (when (map? x)
              (or (some (fn [[k v]] (when (and (= k id) (map? v)) v)) x)
                  (some (fn [[_ v]] (walk v)) x))))]
    (walk @surface)))

(defn- rejection-of [source]
  (try (do (sema/analyze source) nil)
       (catch Throwable e (ex-message e))))

(deftest capability-handles-are-not-values
  (testing "cap-acquire has no type signature, so nothing can bind one"
    (is (= "operation has no admitted type signature"
           (rejection-of "(defn f [] :i64 (let [h (cap-acquire 8)] 1))
                          (defn main [] :i64 0)"))))
  (testing "nor store one in a record field"
    (is (= "operation has no admitted type signature"
           (rejection-of "(defrecord Box [h :i64])
                          (defn f [] :i64 (let [b (->Box (cap-acquire 8))] 1))
                          (defn main [] :i64 0)"))))
  (testing "nor return one"
    (is (= "operation has no admitted lowering"
           (rejection-of "(defn f [] :i64 (cap-acquire 8))
                          (defn main [] :i64 0)"))))
  (testing "and has-capability? has no lowering either"
    (is (= "operation has no admitted lowering"
           (rejection-of "(defn f [] :bool (has-capability? 8))
                          (defn main [] :i64 0)")))))

(deftest the-state-effect-reaches-the-row-by-name
  (testing "the one state precondition recorded as met"
    (let [hir (sema/analyze "(defn bump [x :i64] :i64 (state/transact x))
                             (defn main [] :i64 0)")]
      (is (contains? (:named-operations hir) :state/transact))
      ;; Recorded beside it because it is the reason the row is not simply
      ;; readable: capability-catalog declares :numeric-id :not-user-facing and
      ;; the numeric row still carries one.
      (is (some #(and (vector? %) (= :cap/call (first %))) (:effects hir))))))

(deftest the-recorded-status-matches-what-is-measured
  (testing "state"
    (let [status (get-in (entry :no-ambient-mutation) [:widening-path :precondition-status])]
      (is (= :vacuous (get-in status [:capability-handle-storage-rejected-by-schema :status])))
      (is (= :met (get-in status [:effect-row-shows-state :status])))
      (is (= :not-met (get-in status [:conformance-vectors-positive-and-negative :status])))
      (is (= :partial (get-in status [:state-kit-backend-qualification :status])))))
  (testing "abort"
    (let [status (get-in (entry :explicit-errors) [:widening-path :precondition-status])]
      (is (= :not-met (get-in status [:checked-lexical-facet-unwind :status])))))
  (testing "and the facet unwind the abort path waits on is the one the dataspace entry declares missing"
    (is (contains? (set (:missing (entry :dataspace))) :checked-lexical-facet-unwind)))
  (testing "no widening has landed: every covered head is still forbidden"
    (let [grammar (edn/read-string (slurp "lang/guest-grammar.edn"))
          forbidden (set (:forbidden-heads grammar))]
      (is (every? forbidden '#{atom swap! reset! volatile! ref dosync}))
      (is (every? forbidden '#{throw try catch})))))
