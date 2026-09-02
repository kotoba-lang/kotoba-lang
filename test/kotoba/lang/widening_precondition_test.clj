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
  (testing "abort (slice 1 landed 2026-09-02; lang/abort-ability.edn)"
    (let [status (get-in (entry :explicit-errors) [:widening-path :precondition-status])]
      (is (= :not-met (get-in status [:checked-lexical-facet-unwind :status])))
      (is (= :met (get-in status [:effect-row-integration :status])))
      (is (= :met (get-in status [:conformance-vectors-positive-and-negative :status])))
      (is (= :tracked-elaboration (:enforcement (entry :explicit-errors))))))
  (testing "and the facet unwind the abort path waits on is the one the dataspace entry declares missing"
    (is (contains? (set (:missing (entry :dataspace))) :checked-lexical-facet-unwind)))
  (testing "the STATE KIT widening has not landed: the heads only it covers are still forbidden"
    ;; This asked about all six heads of :state-kit-desugar :covers until
    ;; 2026-09-02, when the second widening path on the same entry --
    ;; :local-atom-elaboration, lang/local-state.edn -- landed and admitted
    ;; three of them BY ELABORATION. The state kit itself has not moved: its
    ;; four preconditions are unchanged above, and the three heads below have
    ;; no host, no grant and no desugar. So the question this splits into two.
    (let [grammar (edn/read-string (slurp "lang/guest-grammar.edn"))
          forbidden (set (:forbidden-heads grammar))]
      (is (every? forbidden '#{volatile! ref dosync}))))
  (testing "the LOCAL ATOM widening has (slice 1): its heads left forbidden-heads and are sugar"
    (let [grammar (edn/read-string (slurp "lang/guest-grammar.edn"))
          forbidden (set (:forbidden-heads grammar))
          path (:local-atom-elaboration (entry :no-ambient-mutation))]
      (is (not-any? forbidden '#{atom swap! reset! deref}))
      (is (= '[atom swap! reset! deref] (get-in grammar [:sugar :atom-local :forms])))
      (is (= :slice-1-implemented (:status path)))
      (is (= '#{atom swap! reset! deref} (:covers path)))
      (is (= '#{atom swap! reset! deref}
             (:admitted-via-elaboration (entry :no-ambient-mutation))))
      (is (= :met (get-in path [:precondition-status :non-escaping-cell-enforced :status])))))
  (testing "and it did NOT advance the state kit's vacuous precondition"
    ;; A cell is not a value either, so nothing on this slice gives a
    ;; capability handle a type. `capability-handles-are-not-values` above is
    ;; the measurement; this is the record that the path knows it.
    (is (= :local-atom-elaboration
           (get-in (entry :no-ambient-mutation) [:widening-path :unchanged-by])))
    (is (= :vacuous
           (get-in (entry :no-ambient-mutation)
                   [:widening-path :precondition-status
                    :capability-handle-storage-rejected-by-schema :status]))))
  (testing "a non-escaping local atom is admitted, and an escaping one is not"
    ;; The two directions, measured rather than declared. A slice that only
    ;; admitted would not be a widening of :no-ambient-mutation at all.
    (is (nil? (rejection-of "(defn main [] :i64 (let [a (atom 0)] (swap! a + 42) @a))")))
    (is (= (str "atom `a` escapes its let scope (atom slice 1 admits "
                "swap!/reset!/deref in straight-line code of the binding "
                "function only)")
           (rejection-of "(defn main [] :i64 (let [a (atom 0)] a))"))))
  (testing "the abort widening has (slice 1): the heads left forbidden-heads and are sugar"
    (let [grammar (edn/read-string (slurp "lang/guest-grammar.edn"))
          forbidden (set (:forbidden-heads grammar))]
      (is (not-any? forbidden '#{throw try catch}))
      (is (= '[throw] (get-in grammar [:sugar :throw :forms])))
      (is (= '[try catch] (get-in grammar [:sugar :try :forms]))))))

(deftest the-abort-effect-reaches-the-row-and-try-removes-it
  ;; The precondition recorded as :met above, measured rather than stated:
  ;; the throwing callee's row carries :abort and its interface is
  ;; [:result T E]; the caller that catches carries nothing.
  (let [hir (sema/analyze "(ns w (:export [main]))
                           (defn- safe-div [a :i64 b :i64] :i64
                             (if (= b 0) (throw \"division by zero\") (quot a b)))
                           (defn main [] :i64 (try (safe-div 10 0) (catch e (string-length e))))")
        by-name (into {} (map (juxt :name identity)) (:functions hir))]
    (is (= #{:abort} (:effects (by-name 'safe-div))))
    (is (= [:result :i64 :string] (:result (by-name 'safe-div))))
    (is (= #{} (:effects (by-name 'main))))
    (is (not-any? #(and (seq? %) (contains? '#{throw try catch} (first %)))
                  (tree-seq coll? seq (mapv :body (:functions hir))))
        "the ambient forms never exist post-elaboration")))
