# ADR — a core form meets an unexpected shape by refusing or sequencing, never by truncating

- **Status**: Accepted
- **Date**: 2026-09-02
- **Scope**: `kotoba-lang`, `kotoba-sema`, `kotoba-kir`, `amu`
- **Related**: `ADR-safe-capability-language.md`,
  superproject `adr-2607279200` (Kotoba-shaped safety elaboration migration),
  superproject `adr-2608136000` (a check that could not measure must not
  return the value of a check that measured and found nothing)

## Context

A `let` whose body had more than one form compiled with `:ok true` and kept
only the **first** one. Measured 2026-09-02 against `amu b1fdaad2` /
`kotoba-sema 8b2cb10`:

```
(ns letbody (:export [run]))
(defn run [n :i64] :i64
  (let [x (+ n 1)]
    (+ x 10)
    (+ x 100)))
```

`amu check --jvm-free` → `{:ok true …}`. Compiled to `wasm32` and executed,
`run(5)` answers **16** — that is `(+ x 10)` with `x = 6`. The correct answer
is 106. The second form is not mis-ordered or mis-typed; it is **not in the
program**. The analysed HIR body is `(let [x (+ n 1)] (+ x 10))`.

The same defect drops effects, not just values. Two kernel stores in one `let`
body left only the first byte written.

It was found by the QWEN-RUNTIME stream of the K16 pure-Kotoba programme, where
the dropped form carried the high word of a 64-bit offset cursor.

### Where it came from

Three facts had to hold at once, and each was individually defensible:

1. `desugar-expr` emitted **every** body form onto `let` —
   `(list* 'let bindings body…)` — a head that takes one.
2. `rewrite-record-projection` and `elaborate-named-ability` destructured
   `(let [[bindings body] args] …)` and **rebuilt the form from `body`
   alone**. Four more passes destructured the same way to read it.
3. `validate-expr` carried the rule — *"let requires one result expression"* —
   and ran **last**, so it measured the already-shortened form and admitted
   it.

The rule existed. It could not fire, because the truncation ran first and
handed it a body of exactly one. This is superproject `adr-2608136000`'s shape
exactly: a check that could not measure returned the value of a check that
measured and found nothing.

### It was not only `let`

Looking for the same shape found a second instance the same day:

```
(defn run [n :i64] :i64 (if (> n 0) (+ n 10) (+ n 100) (+ n 1000)))
```

`:ok true`; `wasm32` answers **15**. `if` survived desugaring with whatever
arity the source wrote, `elaborate-named-ability` rebuilt it from
`[test then else]`, and `validate-expr`'s `if requires test, then, else` then
measured the rebuilt three. In Clojure that source is an arity error.

### What was NOT affected

Measured, not assumed. `when`, `when-not`, `when-let`, `when-some`, `doseq`
and `dotimes` all answer correctly with several body forms — they desugar
through `do`, which is a first-class head that no pass rebuilds from a prefix.
`defn`, `fn` and `loop` **refuse** several body forms, loudly, with pinned
messages; they were never part of this defect.

So `let` was in neither set: it accepted several forms and kept one.

## Decision

**1. A `let` body is an implicit `do`.** Every form is evaluated, in source
order; the value is the last one. The core `let` keeps exactly ONE body
expression, so a multi-form source body is collapsed into a `do` during
desugaring.

`let` is the only body-taking head in the `:sequences` set that was missing —
`when` and its four relatives already behaved this way, `do` already exists as
a `:core-special-forms` head with correct semantics on every backend, and
Kotoba source is Clojure-shaped. Refusing would have been safe but would have
made `let` the one head that demands an explicit `(do …)` its neighbours do
not.

**2. The collapse is `do`, never nested `let`s.** A non-final body form
encoded as a `let` binding is an *unused binding*, and dropping unused
bindings is legal. The forms that must survive are precisely the effectful
ones — a kernel store, a `cap-call`. `kotoba.compiler.frontend` already keeps
`do` first-class through desugaring for this reason; this ADR makes the reason
part of the authority so the note in `:sugar :do` — which until today read
*"nested let (compiler)"* — cannot be read as licence.

**3. Core `if` is exactly ternary.** Any other arity is refused
(`:kotoba.error/if-arity`).

**4. A consumer that meets an unexpected core-form shape refuses; it never
truncates.** Producing a shorter program that compiles clean is not an
admitted response. Every pass that reads a core `let` body now goes through
one function that states the rule, and that function refuses
(`:kotoba.error/let-body-multiple-forms`, `:kotoba.error/let-body-empty`)
rather than returning a prefix.

## Consequences

- `lang/guest-grammar.edn` gains `:core-form-shapes` (the shape of `let` / `if`
  / `do` after desugar, and what to do on any other shape) and
  `:implicit-body-forms` (`:sequences` vs `:refuses`, with nothing in a third
  "truncates" category).
- `docs/lang/semantics-ssot.md` §4 states the implicit-`do` rule and the `if`
  arity rule.
- Source that relied on the truncation changes meaning. That source was
  already wrong: it had forms in it that never ran. The workaround this defect
  taught authors is visible in the tree — `aiueos/os/aiueos/kotoba/sha256.kotoba`
  binds each store to a `let` name and adds `(* 0 (+ s0 (+ s1 s2)))` to the
  result to keep them. That idiom is no longer necessary and is now merely
  redundant, not load-bearing.
- A 4-argument `if` that compiled before now refuses. This is a widening of
  refusal, not of admission.

## Evidence

Measured 2026-09-02 with `--jvm-free`, wasm32 output executed under Node's
`WebAssembly`:

| source | before | after |
|---|---|---|
| `(let [x (+ n 1)] (+ x 10) (+ x 100))`, `n=5` | 16 | 106 |
| `(let [x (+ n 1)] (let [y (+ x 1)] y) (+ x 100))`, `n=5` | 7 | 106 |
| two `kernel-store-u8` in one `let` body | `[65 0 0]` | `[65 66 0]` |
| `(if (> n 0) (+ n 10) (+ n 100) (+ n 1000))`, `n=5` | 15 | refused, `:kotoba.error/if-arity` |

Frontend defect site: `kotoba-sema src/kotoba/compiler/frontend.cljc` — the
`let` case of `rewrite-record-projection` (`(let [[bindings body] args …]`,
rebuilt at `(list 'let (vec pairs) …)`), reached from `analyze*`'s
`rewrite-record-projections` pass, which runs before `validate-expr`.
