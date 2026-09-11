# ADR: A `defn` / `defn-` / `fn` body of several forms is one `do`, as `let`'s is

- Status: accepted
- Date: 2026-09-12
- Context: adr-2609111900 (superproject), the JVM-free toolchain self-host
  ceiling loop -- measure, take the largest wall, widen the compiler or fix
  the source, land, pin, re-measure.
- Files: `lang/guest-grammar.edn` `:implicit-body-forms` (and the `:map` /
  `:map-indexed` `:callback` entries), `lang/surface-status.edn`
  `:collections :map-function` / `:map-indexed-function`, the vendored byte
  copies, `test/kotoba/lang/grammar_authority_test.cljk`'s digest literal,
  `lang/vendored-copies.edn`'s deferrals. Frontend: kotoba-sema
  `src/kotoba/compiler/frontend.cljk` (`implicit-do-body`), landed in the
  same wave against this authority.

## Decision

`:implicit-body-forms` moves `defn`, `defn-` and `fn` from `:refuses` to
`:sequences`:

```edn
{:sequences #{let when when-not when-let defn defn- fn}
 :refuses   #{loop try catch}
 ...}
```

`(defn f [x] a b c)` is `(defn f [x] (do a b c))`. The same for `defn-`, for
a multi-arity clause, for a variadic clause, for `fn` (single- and
multi-arity, as a value and as the inline callback of `map`, `map-indexed`,
`reduce` and `filter`). The two spellings analyse to the SAME HIR -- one
function, one body -- and therefore mint the same definition CID.

## Why this is a spelling, not a semantics

`do` is a first-class head on every backend (`:sugar :do`: "kept as a
first-class `do` head; sequential drop + last value"), and it is exactly what
`let` already collapses a multi-form body into (`:core-form-shapes :let
:collapse do`, since 2026-09-02). The widening adds no evaluation rule the
language did not already have: the non-final forms run in order for their
effects and the last form is the value. Nothing truncates -- a collapse that
kept the first form (the `let` defect of 2026-09-02, "answered 16, should be
106") is the failure the tests are written against, so every positive RUNS
to the value of the LAST form.

## Classification: implementation state

The refusal was IMPLEMENTATION STATE, not a property. Nothing in this file,
in `surface-status.edn` or in the safety claims says a function body must be
one expression; what they say about bodies is the `do` entry and the `let`
collapse above. The `:several-body-forms` entries under `:map` and
`:map-indexed` said as much before this ADR: "widening it is a grammar move
for every `fn` and `defn` site at once (measured on wasm32, as `:sequences`
was), not this site's alone". This is that move.

## The wall it removes

`lang/selfhost-distance.edn` measurement 14 (amu 75f2c37) refuses 14 of the
compiler's own 225 modules with `function must contain one result
expression`. A reader survey of the same 225 files (2026-09-12) finds **272**
`defn` / `defn-` forms with more than one body expression; the first
expression is `when-not` in 177 and `when` in 41 -- the validation guard
`(when-not ok (throw (ex-info ...)))` followed by the result. It is the
largest latent wall in the corpus: the 14 modules stop at the FIRST such
defn, and 258 more are behind them.

## What stays refused, by name

- An EMPTY body, at every site, with the sentence it always had: `function
  must contain one result expression` (defn), `fn value requires unique
  arities with zero to four unique parameters` (a single-arity fn value),
  `multi-arity fn requires ([params] body) clauses`, `map fn requires a body
  expression`, `map-indexed fn requires a body expression`, `reduce fn must
  be (fn [acc x] single-expr)`, `filter pred must be (fn [x] single-expr)`.
- `loop` -- `loop requires exactly one body expression (this profile has no
  `do`)`. Its body becomes a recursive helper; sequencing it is that
  lowering's decision, and it was not measured here.
- `try` -- `try requires exactly one body expression and one catch clause`.
  `lang/abort-ability.edn` `:try :body-forms 1` is the abort contract's own
  shape rule.
- `catch` -- the same contract, `:handler-forms 1`.
- A protocol method -- `protocol method must contain one result expression`
  is `defprotocol`'s diagnostic, not this profile's, and is left alone.

## Measurement

- kotoba-sema, KIR interpreter (`test/kotoba/compiler/defn_body_sequences_test.cljk`):
  `(defn f [x :i64] :i64 (+ x 1) (* x 2))` answers 42 for 21; the explicit
  `(do ...)` spelling analyses EQUAL; the guard shape
  `(defn- g [x :i64] :i64 (when-not (< 0 x) (throw :neg)) (+ x 1))` answers
  42 on the good path and the handler's 7 on the bad one, with
  `[:result :i64 :keyword]` and `#{:abort}`; multi-arity, variadic, fn value,
  multi-arity fn, and the map / map-indexed / reduce / filter callbacks each
  answer their LAST form. Against the unmodified frontend (d17a490, shadowed
  onto the classpath) the same suite reports 17 errors and 1 failure, the
  first of them `function must contain one result expression`.
- wasm32-browser: amu 54b2ca78's nbb route with the kotoba-sema worktree
  placed before the locked kotoba-sema on the classpath (amu unchanged),
  artifacts run under `runtime/browser-host.mjs` `instantiateKotoba`:
  `(defn main [] :i64 (+ 20 1) (* 21 2))` -> 42; a `defn-` helper with two
  forms -> 42; `(let [f (fn [y] (+ y 1) (* y 2))] (f 21))` -> 42. Control:
  `bin/amu compile` at the locked kotoba-sema refuses the first source with
  `function must contain one result expression`.
- Suite: kotoba-sema `run-tests.cljk` 505 tests / 1814 assertions at
  d17a490 -> 513 / 1845 after, 0 failures.

## Consequences

- The authority digest moves to `5b8af0f9`. kotoba-sema's copy lands with
  its frontend; grammar's and kotoba's copies stay deferred on their own
  blockers (`lang/vendored-copies.edn`).
- amu's kotoba-sema pin is advanced separately; `lang/selfhost-distance.edn`
  is re-measured after that pin, not here.
- `lang/compat/` and `lang/compat.edn` are untouched by this ADR.
