# ADR: A closure parameter carries any closure result type

- Status: accepted
- Date: 2026-09-12
- Context: adr-2609111900 (superproject), the JVM-free toolchain self-host
  ceiling loop -- measure, take the largest wall, widen the compiler or fix
  the source, land, pin, re-measure.
- Files: `lang/guest-grammar.edn` `:callable-type` (`:parameter-types`,
  `:parameter-types-rule`, `:parameter-types-lowering`,
  `:parameter-types-typing-positions`, `:parameter-types-measured`, `:note`);
  `lang/surface-status.edn` `:typed-closure-parameters`; the vendored byte
  copies; `test/kotoba/lang/grammar_authority_test.cljk`'s digest literal and
  `public-callable-contract-is-bounded-and-abi-neutral`;
  `lang/vendored-copies.edn`'s deferrals. Frontend: kotoba-sema
  `src/kotoba/compiler/frontend.cljk` (`validate-value-type!`,
  `dispatcher-key` / `dispatcher-parameter-label` / `invoke-dispatcher-name`,
  `desugar-lexical-call`, `lift-lambda`, `lambda-dispatchers`,
  `desugar-ordinary-call`, `*function-callable-param-contracts*`), landed in
  the same wave against this authority, with
  `test/kotoba/compiler/typed_closure_parameters_test.cljk`. amu: the
  kotoba-sema pin, `callable_values_test`'s pinned refusal, the digest
  history.

## The wall

`lang/selfhost-distance.edn` measurements 16-19 (amu cdebca89, 225 compiler
sources). The `clojure.walk` twin (`lang/compat/clojure/walk.kotoba`, landed
2026-09-12) could provide `prewalk-replace` / `postwalk-replace` /
`keywordize-keys` / `stringify-keys` but not `walk` / `prewalk` / `postwalk`,
because their shape

    (defn apply-doc [f [:fn [[:document] :document]] d :document] :document (f d))

was refused

    callable parameter types currently require the i64 closure ABI

-- 21 of the 25 `walk`-family call sites in the compiler's own modules, 19
modules. The `kotoba.lang.coll` and `clojure.set` twins could not be passed
as VALUES to `reduce` / `merge-with` for the same reason: `set/union` is a
`[:fn [[[:set T] [:set T]] [:set T]]]` and no such parameter type existed
(18 modules).

## Classification, written before the change

IMPLEMENTATION STATE, not a property. The `:typed-closure-parameters` entry
said so itself: "physically a typed set is one word on wasm and native ...
and closure captures already erase to i64 pair chains, so the restriction is
type-level, which is what makes it a decision and not a port." Measured
before touching anything:

- A closure RESULT of type `:document`, `[:set :keyword]`, a record, an
  option, a result, a variant, a list, a heterogeneous vector or a typed map
  was already admitted (`closure-result-type?`), lowered to a per-family
  dispatcher `__kotoba_invoke_<label>$arityN` whose `:result` is that type,
  and executed on the KIR interpreter, restricted ESM and wasm32 (amu
  `callable_values_test`, the `descriptor-keyed-*` cases).
- That dispatcher is an ordinary KIR function: `{:name ... :params [closure
  arg0 ...] :param-types [...] :result T :body (if (= (pair-first closure)
  id) (helper captures... args...) fallback)}`. Nothing in `kotoba.kir` or
  in kotoba-wasm reads its name; amu's linker (`project-dispatchers`) and its
  cljs backend match `__kotoba_invoke(_[^$]+)?$arity[0-4]` and take
  `:param-types` from the function. The helper a `fn` lowers to is another
  ordinary function whose `:param-types` default to all-`:i64` only because
  nothing had written anything else there.
- On wasm32 (typed v4) every non-`:i64` / `:f32` / `:f64` value is an
  `externref` -- one word, a host-table handle -- exactly as a `:document`
  parameter of a plain `defn` already is. On the interpreter it is a value.
- The parameter side had ONE refusal sentence (`validate-value-type!`) and
  one dead second check (`desugar-lexical-call`, refusing a non-i64 `wanted`
  that could never arrive), and no lowering behind either.

So an argument of a closure-result type crosses a closure call exactly as it
crosses a direct call, once the dispatcher and the helper say its type. No
port; a type-level rule, and the lowering that the result side already used.

## Decision

**A closure PARAMETER type is admitted exactly when it is admitted as a
closure RESULT type.** One predicate -- kotoba-sema `closure-result-type?`
-- checked on both sides, so the two sets cannot drift. Today that is the
flat set `:i64 :bool :f32 :f64 :string :bytes :vector-i64 :vector-f64
:document` and every record / `[:option T]` / `[:result T E]` / variant /
`[:list T]` / `[:vector [...]]` / `[:set T]` / `[:map K V]` descriptor with
a closed default inhabitant. `:physical-abi :i64` stays: the closure WORD is
an i64 pair; only what is handed through it widened.

Refused by name, one sentence per rule:

| spelling                                 | sentence |
|------------------------------------------|----------|
| `[:fn [[[:fn [[:i64] :i64]]] :i64]]`     | `callable parameter types cannot be callable or linear resources` |
| `[:fn [[[:stream :bytes]] :i64]]`        | the same sentence |
| `[:fn [[:keyword] :i64]]`                | `callable parameter type is outside the admitted dispatcher profile (a parameter type is admitted exactly when it is admitted as a closure result type)` |
| `(f (+ n 1))` where `f` takes `:document` | `expression type mismatch: expected document, got i64` -- the ordinary checker, against the dispatcher's signature |

`:keyword` / `:symbol` / `:map` / `:option-i64` / `:result-i64` /
`:string-index` / `:disjoint-set-i64` are outside on BOTH sides today
(`closure-flat-result-types` does not list them). Widening the result set
widens the parameter set in the same edit; this ADR does not widen it.

### Lowering

- The dispatcher family is one function per distinct CLAUSE SIGNATURE. An
  all-i64 clause keeps `__kotoba_invoke[_RESULT]$arityN` -- same HIR, same
  KIR, same CIDs (pinned: the amu `public-callable-contracts-...` program's
  digests equal before and after, with the old frontend shadowed onto the
  classpath; `call_arity_test`'s closure golden untouched). A clause naming
  a non-i64 parameter type is `__kotoba_invoke_p_<sha256 of the
  parameter-type vector>[_RESULT]$arityN` with `:param-types [:i64 T...]`.
- The `fn`'s helper carries the clause's parameter types on its own
  parameters; its captures stay the i64 pair-chain words they are read out
  of. The lambda joins the family of exactly its signature and no i64
  family (an i64 dispatcher calling it would hand an i64 word to a typed
  parameter).
- At `(f x)` the contract selects the family and gives each argument the
  expected-type context the result side gives a tail expression (a bare
  integer literal in a `:document` slot is a document literal, as `(defn g
  [] :document 42)` is); an `:i64` argument is desugared as before.

### Typing positions -- what is and is not admitted

A `fn` / `fn-ref` takes contract-typed parameters in exactly two positions:
written in the argument slot of a module function whose clause declares a
`[:fn ...]` there (a new table, `*function-callable-param-contracts*`,
propagated only for contracts naming a non-i64 parameter type, so all-i64
contract programs lower as before), or in the result position of a function
whose result is a `[:fn ...]` (as before). A lexical callable parameter
passed on carries its own contract. Not admitted, recorded in
`surface-status.edn :missing`:

- `:contract-typed-let-bound-fn` -- `(let [g (fn [d] d)] (apply-doc g doc))`:
  `g` has i64 parameters and the typed dispatcher traps closed on it at run
  time, as a result-family mismatch always has. Compile-time refusal needs
  contract propagation through `let`, which this wave does not add.
- `:typed-closure-captures` -- a `:document` local read inside a `fn` is a
  separate wall (`expected i64, got document`), untouched.
- `:parameter-types-outside-the-result-profile` -- the seven types above.

## Measurement

- KIR interpreter (kotoba-sema `typed_closure_parameters_test`, 14 tests /
  31 assertions): `:document`, `[:set :keyword]`, two `:document`s, a record
  and a `:string` pass THROUGH a closure parameter and RUN --
  `document-merge` through the closure prints the merged document,
  `typed-set-count` through the closure answers 2; `fn-ref` in a typed slot;
  a typed result contract called with a typed argument; every refusal by its
  sentence. Against the unmodified frontend (820e85d1, shadowed): 9 errors +
  8 failures, all at the old sentence; only the CID control passes. Suite
  526 / 1887 / 0 -> 540 / 1918 / 0.
- wasm32-browser: amu dea953f2's nbb route, `compile --target
  wasm32-browser --jvm-free`, the kotoba-sema worktree placed before the
  locked 820e85d1 on the classpath, artifacts run under
  `runtime/browser-host.mjs` `instantiateKotoba`: a `[:set :keyword]` passed
  through a closure parameter answers 2; a document passed through a closure
  that `document-merge`s `{:b 40}`, read back at `:b`, answers 40. Control
  at the locked kotoba-sema: the old refusal, exit 65.

## What is not decided here

Whether the `clojure.walk` / `kotoba.lang.coll` / `clojure.set` twins now
provide what they could not is the NEXT measurement of
`lang/selfhost-distance.edn`, not this ADR's claim. `lang/compat*` and the
distance file are untouched by this wave.
