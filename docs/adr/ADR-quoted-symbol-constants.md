# ADR: Quoted symbol data is a literal -- `'sym`, `'#{a b}`, `'{a 1 b 2}`, `'[a b]`

- Status: accepted
- Date: 2026-09-12
- Context: adr-2609111900 (superproject), the JVM-free toolchain self-host
  ceiling loop -- measure, take the largest wall, widen the compiler or fix
  the source, land, pin, re-measure.
- Files: `lang/guest-grammar.edn` `:core-form-shapes :def :value` (`:literal`,
  `:quoted`, `:quoted-cids`, `:quoted-not-widened`, `:quoted-measured`,
  `:not-a-fold`), `:refused-by-name :unspellable`, `:spelling`;
  `lang/surface-status.edn` `:other-gaps :computed-module-constant`; the
  vendored byte copies; `test/kotoba/lang/grammar_authority_test.cljk`'s
  digest literal; `lang/vendored-copies.edn`'s deferrals. Frontend:
  kotoba-sema `src/kotoba/compiler/frontend.cljk` (`quote-form?`,
  `quoted-symbol-datum-kind`, `desugar-quoted-datum`, `constant-literal?`,
  `spell-folded-value`, `def-parts`), landed in the same wave against this
  authority, with `test/kotoba/compiler/quoted_symbol_constants_test.cljk`.

## The wall

`lang/selfhost-distance.edn` measurement 18 (amu cdebca89, 225 compiler
sources): 12 modules refused

    constant value is quoted data holding symbols: (quote #{vector-assoc ...})
    -- a symbol is not an admitted constant kind (integer/string/keyword/
    boolean/nil/vector/map/keyword-set); spell the entries as keywords

8 with a quoted SET of symbols, 4 with a quoted MAP whose keys are symbols.
Every one is an operation table of the compiler's own -- `(def arithmetic
'#{+ - * quot bit-xor bit-and bit-or})`, `(def heap-operations '{pair 2
pair-first 1 pair-second 1})`, `(def find-name 'kotoba$string-index-find)`.
"Spell the entries as keywords" is not available to those authors: the
members ARE symbols, operation heads compared against read source.

## Classification, written before the change

IMPLEMENTATION STATE, not a property. `:symbol` is a value type of the
frontend (`value-types`); `(symbol "a")` builds one from a `:string` and
executes; `(typed-set-new [:set :symbol] (symbol "a") (symbol "b"))` and
`(typed-map-new [:map :symbol :i64] ...)` check and run; the `clojure.set`
compat template instantiates with `{elem :symbol}`. Nothing in this file,
surface-status or safety-claims says a symbol may not be written down. What
was missing was the LITERAL spelling: `(quote sym)` -- the form the reader
hands `'sym` over as since 2026-09-11 -- had no elaboration (`unknown
operation: quote` in a body) and the `def` arm refused the datum by the
sentence above.

## Decision

`(quote datum)` holding symbols is one of FOUR kinds, or refused by name:

| written       | type                    | lowers to                                              |
|---------------|-------------------------|--------------------------------------------------------|
| `'sym`        | `:symbol`               | `(symbol "sym")` -- exactly, the same KIR              |
| `'#{a b}`     | `[:set :symbol]`        | `(typed-set-new [:set :symbol] (symbol "a") (symbol "b"))`, pr-str order |
| `'{a 1 b 2}`  | `[:map :symbol V]`      | `(typed-map-new [:map :symbol V] ...)` sorted by key; V is ONE scalar kind (integer / string / keyword / boolean) |
| `'[a b]`      | `[:vector [:symbol ...]]` | `(hetero-vector-new [:vector [:symbol :symbol]] ...)`  |

A namespaced `'ns/x` is the symbol `(symbol "ns/x")` gives: that call was
measured admitted and executing to `ns/x`, and is mirrored rather than
refused. A quoted datum holding NO symbol has its quote taken off, as
before (`'#{:a :b}` is `#{:a :b}`).

At a `def` the quoted datum is held AS WRITTEN, quote and all. This is the
same representation the keyword set has (the raw literal, lowered where it
is used) with one necessary difference: a bare symbol at a def is an ALIAS
of another constant, and a raw set of symbols would be substituted into
function bodies and read as variable references. The quote is what keeps
the datum data; `substitute-constants` and every rewriting pass already
leave `quote` subtrees alone. The fold spells a `:symbol` result as `'sym`,
a `[:set :symbol]` as `'#{...}` and a scalar-valued `[:map :symbol V]` as
`'{...}`, so `(def s 'a)` and `(def s (symbol "a"))` are ONE constant --
the CID control, pinned in kotoba-sema.

Refused BY NAME, the same sentence at a def and in a body: a set or vector
mixing symbols with other kinds, a map mixing symbol keys with other key
kinds, a symbol-key map whose values are not one scalar kind (a symbol or a
collection as a value), and -- in a body -- a quoted list. A quoted list at
a def keeps the closedness sentence, as `'(1 2)` always has.

## Not widened, recorded

`=` on two `:symbol`s stays `equality type is outside the safe value
profile` (measurement 18 shows the same sentence for `(= (symbol "a")
(symbol "a"))`). Membership is `typed-set-contains [:set :symbol]`
(`contains?` on a typed set is refused by the map-presence rule); lookup is
`get` on the typed map.

## Measured

- wasm32-browser, 2026-09-12: amu cdebca89's nbb route (`compile --target
  wasm32-browser --jvm-free`), the kotoba-sema worktree carrying the
  widening placed before the locked kotoba-sema on the classpath, amu
  unchanged, artifacts run under `runtime/browser-host.mjs`
  `instantiateKotoba`: `(def ops '#{a b})` with `main` =
  `(if (typed-set-contains [:set :symbol] ops 'a) 1 0)` answers 1; with
  `'c`, 0; `(def heap-operations '{pair 2 pair-first 1 pair-second 1})`
  with `(get heap-operations 'pair 0)` answers 2. Control at the locked
  kotoba-sema 99f03fc8: the old `spell the entries as keywords` refusal,
  exit 65.
- KIR interpreter: kotoba-sema `quoted_symbol_constants_test.cljk` runs the
  same and the CID controls; against the unmodified frontend the suite
  fails 10 / errors 19 of 34 assertions (the old sentences), passing only
  the pins of what did not move.

## Digest

The authority advances 5b8af0f9 -> e16c74e5. No head admitted or withdrawn:
`quote` is elaborated away and reaches no backend.
