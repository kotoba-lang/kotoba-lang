# ADR: `clojure.set` for the self-host route is a template with a default binding

- Status: accepted
- Date: 2026-09-11
- Applies: superproject ADR `adr-2609111900-jvm-free-toolchain-selfhost-ceiling-loop`
  (one wall per iteration); builds on `adr-2609113100` (module type
  parameters, "option C").
- Files: `lang/compat/clojure/set.kotoba` (new), `lang/compat.edn`
  `:modules :clojure.set`, `lang/guest-grammar.edn` `:core-form-shapes :ns
  :params` and `:require-spec :with :on-absent`, the kotoba-sema byte copy,
  amu `kotoba.compiler.project` (the linker rule) and
  `test/kotoba/lang/clojure_set_compat_test.cljk` (the oracle).

## Context

`lang/selfhost-distance.edn` at amu 6e56b1d8, project route, 208 sources:
`required module is missing from the explicit source paths` -- 34 modules.
Surveyed over the 16 modules on the compiler's classpath that require
`clojure.set`, the call sites are `difference` 38, `union` 21,
`intersection` 15, `subset?` 11, then `map-invert` 2 and one each of
`superset?` / `select` / `rename-keys` / `rename` / `project` / `join` /
`index` (the last seven all in `kotoba.lang.coll`, which mirrors clojure.set
rather than consuming it). The operands are keyword sets -- effect rows,
capability ids, map-key sets -- in all but three modules (symbol heads in
kotoba-sema's frontend, CID strings in amu's package lock, path strings in
amu's coverage).

Kotoba has the structure already: `#{:a :b}` lowers to `[:set :keyword]`, and
`typed-set-count / -contains / -conj / -disj / -nth` reach any element type.
What it lacks is the algebra under the names the sources use, reachable
through `(:require [clojure.set :as set])`.

## The constraint that decides the design

The sources are DUAL-RUNTIME: they keep loading on nbb / Clojure while they
are being admitted here, so the require form cannot change. Measured
2026-09-11, `[clojure.set :as set :with {T :keyword}]`:

| host | answer |
|---|---|
| ClojureScript 1.12.42 (`cljs.main -c`) | **refused**: `Only :as, :refer and :rename options supported in :require / :require-macros; offending spec: [clojure.set :as set :with {T :keyword}]` |
| Clojure 1.12.5 (`clojure -M -e`) | loads, prints `#{:a :b}` -- unknown libspec options are ignored |
| nbb 1.4.208 (`nbb -e`) | loads, prints `#{:a :b}` -- same |

So the premise "Clojure's libspec refuses unknown options" is true of the
ClojureScript analyzer and false of the other two; but a spelling that one
host refuses and two accept by ignoring it is not a place to hang a meaning.
`:with` stays out of dual-runtime source.

## The three candidates, probed

Each through a 2-module project, `amu check <app> --source-path <app>
--source-path <compat> --jvm-free`, at amu 29502e5b (before the change).
Baseline with no compat root: `required module is missing from the explicit
source paths` (exit 65).

**(A) A template `clojure.set` `(:params [elem])` plus a linker rule for a
bare require.** Probed as written: the bare require is refused `template
module clojure.set declares (:params [elem]) and needs an instantiation:
require it with :with {elem <type>}`; with `:with {elem :keyword}` the
project is `:ok true` and `main` answers 3221 on the KIR interpreter
(union 3 / difference 2 / intersection 2 / subset? 1, base-10 digits).
Two sub-rules were on the table for the bare require:

- *infer the binding from the first call site's argument types* -- the
  linker has no types; it would have to analyse the importer before its
  imports exist as stubs, which is circular, and "first" makes the meaning
  of a module depend on the order of its functions. Refused as magic.
- *a default declared in the template* -- `(:params [elem :keyword])`, the
  name then the type, exactly the way a defn parameter vector spells
  `[s :string]`. The bare require IS the require with `:with {elem
  :keyword}`: same instantiation key, same substituted text, same definition
  CIDs (measured: the `check` output's `:definitions` for the bare and the
  spelled-out require hash identically). Nothing is inferred; the binding is
  a fact written in the template.

**(B) Frontend builtins for `clojure.set/*`, lowered like `into`, with an
empty compat module so the require resolves.** Probed: `(ns clojure.set
(:export []))` is refused `at least one defn is required`; with a private
placeholder defn and an empty export, `entryless library requires at least
one exported function`. The empty module cannot exist, so the smell -- a
module whose alias resolves to names it does not export -- would need a
second exemption in the linker on top of the frontend work (four
loop-synthesising heads in a 15,889-line frontend, whose semantics could only
be checked against clojure.set by running the compiler rather than a library
text). Refused: it moves the algebra out of source and into the compiler, and
it is the only candidate that needs two exemptions to reach its own smell.

**(C) Monomorphic functions per element kind.** `(set/union-keyword ...)`
on nbb: `Unable to resolve symbol: set/union-keyword`. Every dual-runtime call
site would have to change and would then not load on the host that builds
the compiler. Refused by the constraint.

## Decision

(A) with a declared default. In amu's linker (`kotoba.compiler.project`):

- `(:params [sym TYPE? ...])`: a type form after a symbol is that parameter's
  default binding. A symbol after a symbol is the next parameter (`[a b]` is
  two parameters, never `a` defaulting to `b`); a type form before any
  symbol, and a non-type non-symbol item, are refused by name.
- A `:require` without `:with` of a template every parameter of which has a
  default carries the defaults as its binding -- resolved once, before the
  instantiation key is formed, so it dedupes with an importer that wrote the
  same `:with`. A template with a parameter lacking a default is refused
  naming that parameter. An explicit `:with` still binds every parameter;
  defaults never fill in behind a partial map.
- `lang/compat/clojure/set.kotoba` is the first template to carry one:
  `(:params [elem :keyword])`, exporting `union / difference / intersection /
  subset?` at the two-argument arity, strict clojure.set semantics on finite
  sets. `select`, `project`, `rename-keys`, `rename`, `index`, `map-invert`,
  `join`, `superset?` and the other arities are absent with a reason each in
  `lang/compat.edn`, as `clojure.string` does.

What this admits: a module whose sets are keyword sets links with no change
on its side. What it refuses, and how: a module that unions a `[:set
:string]` under the bare require is refused at that call, by type --
`expression type mismatch: expected [:set :keyword], got [:set :string]` --
never by a different answer. A Kotoba-only importer chooses another element
type the ordinary way (`:with {elem :symbol}`), and a dual-runtime module that
needs two element kinds needs two aliases, the second of which is
Kotoba-only; that is recorded as a hazard, not solved here.

## Consequences

- Definition CIDs of modules that do not require `clojure.set` are
  unchanged: measured on a 2-module project and on `examples/todo-app.kotoba`
  before and after the amu change, identical `:definitions`.
- An amu without the rule refuses the new spelling by name (`namespace
  :params must be a non-empty vector of distinct simple symbols`, measured at
  29502e5b): an older consumer gets a refusal, not a misread.
- The oracle is clojure.set itself: `test/kotoba/lang/clojure_set_compat_
  test.cljk` runs the module text on the KIR interpreter over every pair of
  subsets of a five-element universe (1,024 pairs x 4 functions) and
  compares. Breaking `difference` (conj for disj) turns it red on 8 of the
  first 8 pairs; restoring it turns it green.
- `(reduce set/union #{} xs)` and `(apply set/union ...)` pass the import as
  a VALUE; the linker rewrites qualified call heads only. That is the next
  wall in those modules, not this one.
- The grammar authority's `:ns :params` entry and `:require-spec :with
  :on-absent` say the rule; the kotoba-sema byte copy moves with it. The
  frontend itself is unchanged -- it never sees a `:params` clause on any
  route.
