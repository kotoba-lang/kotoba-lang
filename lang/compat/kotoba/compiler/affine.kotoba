(ns kotoba.compiler.affine
  "Is this vector binding used LINEARLY — at most once on every path, with a
  consuming update as its last use?

  ## Why the question is worth asking

  `vector-assoc` allocates. The native host says so in its own words: *every
  operation that changes an element allocates rather than mutates*, and
  `checked_vector_assoc` is a `memmove` of the whole vector followed by a bump
  into an arena that is never reclaimed. Two consequences, both measured
  rather than reasoned about:

  - one element write costs O(length)
  - the number of writes a vector may ever receive is
    `arena / length` — **a 16,384-item vector, the longest KIR admits, can be
    updated four times in the life of a program** (65,536 / 16,384)

  An order book is a struct of arrays with about a million slots per field and
  a write on every order. Both numbers are wrong for it by orders of
  magnitude, and neither is a safety property: `surface-status.edn` classifies
  the whole native vector entry `:implemented-partial`, and its `:state` entry
  says plainly that **the invariant is AMBIENT, not mutation itself**.

  ## What linearity buys, and why it is not a new semantics

  If a vector handle is dead after an update, updating it in place and
  allocating a copy are indistinguishable to every observer. So this analysis
  does not change what a program MEANS. It decides when the backend may lower
  `vector-assoc` to a store instead of a copy, which is why the answer has to
  be conservative: a wrong `true` here silently mutates a value somebody still
  holds.

  ## What conservative means here

  `false` for anything not obviously linear. The shapes admitted are the ones
  a struct-of-arrays actually writes:

      (let [v (vector-i64-new n)
            v (vector-assoc v 0 x)      ; shadows — the old v is dead
            v (vector-assoc v 1 y)]
        (vector-at v 0))

  Also admitted, as ONE use rather than two: a read and a write fused into a
  single compound expression at the SAME index --
  `(vector-assoc! v k (+ (vector-at v k) delta))`, `v[k] += delta` and the
  only expression Kotoba has for it (`fused-rmw?` below; superproject
  ADR-2609010500 is the program that needed this admitted and could not be
  written without it).

  A binding that is READ after being consumed, referenced twice on one path,
  returned, passed to a function, or captured is refused. Escape analysis for
  literals already exists next door in `kotoba.native.vector-region`; this is
  the same posture applied to the update path rather than to construction.

  ## Why it lives in the frontend

  It first landed in `kotoba-native`, next to `kotoba.native.vector-region`,
  which is a similar-looking escape analysis. Wrong home: this reads SOURCE,
  `kotoba-sema` is what holds source, and a backend repo is not on this one's
  dependency path -- so the gate could never have been consulted from where
  the decision is made."
  (:require [clojure.set :as set]))

(def consuming
  "Operations that make the vector they are given dead.

  `vector-drop` is here with the assoc family: it returns a view of a suffix,
  and a view whose parent is later written in place is a view of something
  else. Admitting it as non-consuming would make the one operation that
  ALIASES the safe one."
  '#{vector-assoc vector-f64-assoc vector-conj vector-f64-conj
     vector-drop vector-f64-drop
     ;; The bang forms consume too. Leaving them out made the gate refuse the
     ;; only programs it exists to admit: `(vector-assoc! v 0 i)` was neither
     ;; consuming nor reading, so `position-ok?` fell through to the bare `v`
     ;; inside it and answered false. Every linear program was rejected, with
     ;; a message saying the handle was used more than once when it was used
     ;; exactly once. Found by compiling one.
     vector-assoc! vector-f64-assoc!})

(def producing
  "Heads that hand back a vector this binding OWNS.

  Provenance, not use count. `linear?` asks how many times a name is used and
  where; it does not ask where the value came from, and for an in-place store
  that is the wrong question on its own. A handle fetched out of an aggregate
  is used once, in vector-operation position, and is still ALIVE -- the
  aggregate holds it.

  Measured 2026-09-01 on amu 0bf63c94: a record holding two vector-i64 fields,
  one fetched with `record-get`, written with `vector-assoc!`, and the SAME
  field read from the record again, was admitted and returned 7 rather than 0.
  The store was visible to a reader that never asked for it, which is the one
  thing the bang promises cannot happen.

  So the set is closed and small: a let-bound handle may be written in place
  only when its initialiser is one of these. Anything else -- `record-get`,
  `hetero-vector-at`, `map-get`, a call to another function -- is refused,
  because this analysis cannot see who else holds it. A parameter is the other
  legitimate source and is handled by `linear-parameter?`."
  '#{vector-alloc vector-new vector-assoc vector-assoc! vector-conj
     vector-drop
     vector-f64-alloc vector-f64-new vector-f64-assoc vector-f64-assoc!
     vector-f64-conj vector-f64-drop})

(def reading
  "Operations that observe without consuming."
  '#{vector-count vector-f64-count vector-at vector-f64-at
     vector-get vector-f64-get nth})

(def ^:private branching
  "Heads whose arms are alternatives, not a sequence.

  Only one arm runs, so uses in different arms are uses on DIFFERENT PATHS and
  the count is the maximum rather than the sum. Getting this wrong is not a
  small conservatism: the shape Kotoba programs actually use to thread a
  vector is tail recursion, base case reading and step case consuming --

      (defn go [v :vector-i64 i :i64 n :i64] :i64
        (if (>= i n)
          (vector-at v 0)
          (go (vector-assoc v 0 i) (+ i 1) n)))

  -- and summing the arms calls that two uses, which refuses the one shape
  that matters."
  '#{if cond case when when-not if-let when-let})

(defn- let-form?
  "A `let` with its binding vector present."
  [form]
  (and (seq? form) (= 'let (first form)) (vector? (second form))))

;; `sym-uses` and `fused-rmw?` are mutually recursive: a fused call is
;; recognised by asking `sym-uses` how many times `target` appears inside its
;; own update expression, and `sym-uses` has to recognise a fused call before
;; it falls through to counting the read and the write separately.
(declare fused-rmw?)

(defn- sym-uses
  "How many times `target` can be used on the WORST SINGLE PATH through
  `form`.

  Counted rather than detected, because `at most once` is the question and
  `appears at all` is a different one — a binding used twice on one path
  cannot be consumed by either use."
  [form target]
  (cond
    (= form target) 1
    ;; `(vector-assoc! v k (+ (vector-at v k) delta))` -- v[k] += delta, the
    ;; shape a struct-of-arrays hot path needs and the only one Kotoba has no
    ;; OTHER expression for (superproject ADR-2609010500: `slab/add!` in
    ;; `torihiki.book` is exactly this, at two call sites). Read then write,
    ;; nothing between them, is ONE use of the handle, not two -- see
    ;; `fused-rmw?` for why counting it as one does not weaken what this file
    ;; exists to refuse.
    (fused-rmw? form target) 1
    ;; A `let` BINDER is not a use. Without this clause the binding vector
    ;; `[v (vector-alloc 4) ...]` counts `v` once on its own, so every
    ;; let-bound handle reached two uses and the gate refused every linear
    ;; program that could be written -- measured 2026-09-01 by compiling one
    ;; that should have passed. Initialisers all run; the body is one path.
    ;; An inner `let` that shadows `target` still counts, which over-counts
    ;; a name that is no longer reachable -- the safe direction.
    (let-form? form)
    (+ (reduce + 0 (map #(sym-uses (second %) target)
                        (partition 2 (second form))))
       (reduce + 0 (map #(sym-uses % target) (drop 2 form))))
    (and (seq? form) (contains? branching (first form)) (< 2 (count form)))
    ;; The test runs on every path; the arms are alternatives. This does not
    ;; separate `cond`/`case` tests from their arms, which over-counts a test
    ;; and never under-counts an arm -- the safe direction.
    (+ (sym-uses (second form) target)
       (reduce max 0 (map #(sym-uses % target) (drop 2 form))))
    (seq? form) (reduce + 0 (map #(sym-uses % target) form))
    (vector? form) (reduce + 0 (map #(sym-uses % target) form))
    (map? form) (reduce + 0 (map #(+ (sym-uses (key %) target)
                                     (sym-uses (val %) target)) form))
    (set? form) (reduce + 0 (map #(sym-uses % target) form))
    :else 0))

(defn- consuming-call?
  [form target]
  (and (seq? form)
       (contains? consuming (first form))
       (= target (second form))))

(defn- reading-call?
  [form target]
  (and (seq? form)
       (contains? reading (first form))
       (= target (second form))))

(defn- fused-read-in-value
  "The reading call on `target` at `idx` that `value` fuses with a write, if
  `value` mentions `target` nowhere else.

  `(some reading-call-at-idx? (rest value))` alone would admit
  `(+ (vector-at v k) (vector-count v))` -- two different reads of `v`, one of
  them at an unrelated index -- because it only asks whether a qualifying
  read is PRESENT, not whether it is the ONLY mention. The `sym-uses` count
  closes that: it counts every occurrence of `target` anywhere in `value`,
  nested or not, so requiring it to equal 1 means the one occurrence the
  `some` below finds is the only one there is."
  [value target idx]
  (and (seq? value)
       (= 1 (sym-uses value target))
       (boolean
        (some #(and (reading-call? % target) (= idx (nth % 2 ::none)))
              (rest value)))))

(defn- fused-rmw?
  "True when `form` is `(op! v idx (f (read v idx) & rest))` -- `v` read and
  written at the SAME index inside ONE compound expression, with no other
  mention of `v` in the update.

  This is the fused read-modify-write shape a struct-of-arrays hot path
  needs and Kotoba has no other expression for: `vector-assoc!` and
  `vector-at` are two separate calls, and counting them separately is two
  uses of the handle even though the second call only ever sees the value
  the first one is about to replace.

  Safe for the reason a bare `vector-assoc!` is safe: `v` is read exactly
  once -- the value about to be overwritten, and (by `fused-read-in-value`'s
  count) nothing else -- and written exactly once, with no syntax between
  the read and the write through which another reference could reach the
  old value. The index is required to be the SAME source form (not merely
  the same runtime value) read and written, which `linear?` can decide
  without evaluating anything: Kotoba expressions are pure, so one source
  form written twice is one value computed twice, not two different reads.
  A mismatched index (`(vector-assoc! v k1 (+ (vector-at v k2) d))`) does not
  match this predicate and falls through to being counted as two uses, which
  is correct -- a fused write at one index proves nothing about what a read
  at a DIFFERENT index might alias.

  Requiring `idx` itself to have zero uses of `target` rules out the
  degenerate `(vector-assoc! v v (+ (vector-at v v) d))`, where the index
  argument is the handle: `fused-read-in-value` alone would not catch this,
  because the extra occurrence lives in the outer form's index position, not
  inside `value`."
  [form target]
  (and (seq? form)
       (contains? consuming (first form))
       (= target (second form))
       (= 4 (count form))
       (let [idx (nth form 2) value (nth form 3)]
         (and (zero? (sym-uses idx target))
              (fused-read-in-value value target idx)))))

(defn- position-ok?
  "`target` may appear only as the FIRST argument of a vector operation.

  Anywhere else — an argument to something that is not a vector operation, a
  return value, an element being stored — is an escape: the handle reaches
  code this analysis cannot see, and code it cannot see may hold it."
  [form target]
  (cond
    (= form target) false
    ;; A fused read-modify-write is one vector-operation-position use, not an
    ;; escape into its own update expression -- see `fused-rmw?`, which has
    ;; already checked that the update mentions `target` nowhere else.
    (fused-rmw? form target) true
    ;; Same reason as `sym-uses`: a binder is not an occurrence. A bare `v`
    ;; standing in the binding vector would otherwise read as an escape, so
    ;; this refused let-bound handles for a second, independent reason.
    (let-form? form)
    (and (every? #(position-ok? (second %) target)
                 (partition 2 (second form)))
         (every? #(position-ok? % target) (drop 2 form)))
    (seq? form)
    (if (or (consuming-call? form target) (reading-call? form target))
      ;; The first argument is the vector; every other argument must not
      ;; mention it. `(vector-assoc v 0 v)` stores the handle into itself.
      (every? #(zero? (sym-uses % target)) (drop 2 form))
      (every? #(position-ok? % target) form))
    (vector? form) (every? #(position-ok? % target) form)
    (map? form) (every? #(and (position-ok? (key %) target)
                              (position-ok? (val %) target)) form)
    (set? form) (every? #(position-ok? % target) form)
    :else true))

(defn- owned-binding?
  "True unless `target` is bound from something that may still hold it.

  Walks every `let` in `form`; if one binds `target`, its initialiser must
  have a `producing` head. A target that no `let` binds is a parameter or a
  free name, which this predicate does not judge -- `linear-parameter?` and
  the caller do.

  Fail closed: an initialiser shape that is not recognised is refused, so a
  head added to the language later is unsafe by default rather than silently
  admitted."
  [form target]
  (let [initialisers (atom [])]
    ((fn walk [f]
       (when (let-form? f)
         (doseq [[n init] (partition 2 (second f))]
           (when (= n target) (swap! initialisers conj init))))
       (when (coll? f) (doseq [x (if (map? f) (apply concat f) f)] (walk x))))
     form)
    (every? #(and (seq? %) (contains? producing (first %))) @initialisers)))

(defn linear?
  "True when `target` may be updated in place inside `form`.

  Three conditions, and each of them is a way a wrong `true` would corrupt a
  live value:

  1. **at most one use** — two uses on one path means the second sees what the
     first consumed
  2. **every use is in vector-operation position** — anywhere else the handle
     escapes to code this cannot see
  3. **a consumed binding is not read afterwards** — enforced by 1, and stated
     because it is the property that matters"
  [form target]
  (and (<= (sym-uses form target) 1)
       (position-ok? form target)
       (owned-binding? form target)))

(defn- binding-pairs [bindings] (partition 2 bindings))

(defn linear-parameter?
  "True when a function parameter is threaded linearly through its body.

  The shape Kotoba actually uses, because `let` cannot rebind a name:

      (defn go [v :vector-i64 i :i64 n :i64] :i64
        (if (>= i n)
          (vector-at v 0)
          (go (vector-assoc v 0 i) (+ i 1) n)))

  One use per path -- read in the base case, consumed in the step -- which is
  linear exactly when alternatives are counted as alternatives."
  [body param]
  (linear? body param))

(defn linear-let-thread?
  "True when a `let` threads a vector through DISTINCT names, each consuming
  the one before.

      (let [a (vector-new 1 2 3)
            b (vector-assoc a 0 9)]
        (vector-at b 0))

  ## Distinct names, because the language refuses the other shape

  This was `linear-let-chain?` and looked for one name rebound over and over
  -- `v`, then `v`, then `v` -- which is how the same idea is written in
  Clojure. **Kotoba refuses it**: `amu check` answers `duplicate let binding`
  (measured 2026-09-01, amu e96dd8c6). So the first version described a
  program that cannot be written, and would have been a gate nothing could
  ever pass. Found by compiling one."
  [form names]
  (and (seq? form) (= 'let (first form)) (vector? (second form))
       (<= 2 (count names))
       (let [pairs (vec (binding-pairs (second form)))
             body (drop 2 form)
             bound (set (map first pairs))]
         (and (every? bound names)
              ;; Every name in the thread is used at most once across all the
              ;; initialisers, only in vector-operation position, and linearly
              ;; in the body. A name read after the update that consumed it is
              ;; exactly what makes an in-place store visible to somebody who
              ;; did not ask for it.
              (every? (fn [n]
                        (and (<= (reduce + 0 (map #(sym-uses (second %) n) pairs)) 1)
                             (every? #(position-ok? (second %) n) pairs)))
                      names)
              ;; Every name but the last is consumed by some later initialiser,
              ;; and is then DEAD -- zero uses in the body. `linear?` is not
              ;; enough here: one read of a consumed name is still one use, and
              ;; it is precisely the read that would see the in-place store.
              (every? (fn [n]
                        (and (some (fn [pair] (consuming-call? (second pair) n)) pairs)
                             (every? #(zero? (sym-uses % n)) body)))
                      (butlast names))
              ;; The last name is the live one and may be read, linearly.
              (let [live (last names)]
                (every? #(linear? % live) body))))))
