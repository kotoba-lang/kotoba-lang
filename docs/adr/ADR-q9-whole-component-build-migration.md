# ADR: Q9 migrates whole components through Kotoba CLI and Amu

- Status: Accepted
- Date: 2026-08-30
- Supersedes: Q9 decision-core and function-shadow extraction as a migration unit
- Machine authority: `lang/q9-migration.edn` version 2

## Context

The first Q9 experiments extracted small pure predicates from `.clj` or
`.cljc` namespaces into `.kotoba`/`.cljk` files. This was useful compiler
evidence, but it was not a source migration: the Clojure namespace remained
the executable component, the caller often precomputed part of the decision,
and no complete import/export/effect closure crossed the compiler boundary.

That model creates two semantic owners. A parity corpus can show that one
function agrees today, but it cannot prove that the component's other exports,
transitive calls, effects, resource bounds and host interactions migrated.
It also encourages permanent shadow implementations that no consumer can
select as a complete replacement.

Kototama is now specified as a whole-program transition over a program,
message, IPLD state, authority and budget. Amu emits a content-bound logic
manifest for that program. Q9 therefore needs the same unit: a complete
component, not a predicate selected because it is easy to port.

## Decision

### 1. The migration unit is a complete component

A Q9 target is one deployable `.kotoba` or `.cljk` entry with a closed
transitive source set and a declared public surface. Every public export of the
replaced component is implemented or explicitly removed by a versioned API
decision. A function-only or decision-only shadow is compiler research, not a
migration and cannot authorize consumer cutover.

`.cljk` remains CLJ-shaped Kotoba source, not a JVM target. `.kotoba` is used
for canonical Kotoba-only source. The extension does not weaken the whole-
component requirement.

### 2. Native mechanisms become component imports

Filesystem access, sockets, clocks, randomness, cryptography, process control,
host handles and mutable platform stores do not justify leaving the business
component in Clojure. They remain native providers behind declared imports and
capabilities. The Kotoba component owns the complete orchestration and decision
surface; the provider owns only the named mechanism.

There is no ambient host fallback. If the language/compiler cannot express a
required import, value or control-flow form, the migration is blocked and that
gap is added to the language surface plan. The component is not reduced to a
smaller predicate to make the gate green.

### 3. Every migrated target passes two build paths

The public product path and the compiler implementation path are both
mandatory.

Public Kotoba CLI:

```sh
kotoba check --safe <entry.kotoba|entry.cljk>
kotoba compile <entry.kotoba|entry.cljk> --target <target> --output <artifact>
kotoba rad build --project <repository> --profile release
```

Amu directly:

```sh
amu check <entry.kotoba|entry.cljk>
amu compile <entry.kotoba|entry.cljk> --target <target> --output <artifact>
```

“Kotoba CLI build” means the public `kotoba compile` source path plus
`kotoba rad build` for a package. Calling `kotoba.compiler.*`, Amu internals or
a test-only KIR executor does not satisfy the public build gate.

For the same target and locked inputs, both routes must bind the same payload
CID, definition CIDs, exports, imports, inferred effects and resource bounds.
CLI wrapper provenance may differ; executable meaning may not.

### 4. Verification covers the complete public surface

The retained Clojure source is a rollback oracle for the whole component.
Parity covers every public export and externally visible refusal, effect,
receipt and state transition. Tests over one extracted function are
insufficient.

Consumer resolution must select the built Kotoba component in a reversible
shadow or canary path before soak begins. Source deletion remains forbidden
until the existing receipt/time soak completes and a separate decision
authorizes oracle retirement.

### 5. Earlier decision cores are historical evidence only

Existing decision-core pilots remain useful compiler conformance fixtures.
They may not gain new production consumers or expand as the Q9 pattern. Each
must be absorbed into a complete component build before consumer cutover.

## Consequences

- Migrations become larger and may expose missing language features earlier.
- Native code remains, but only as narrow provider/TCB components with explicit
  capabilities.
- There is one semantic owner after cutover instead of a permanent Clojure
  oracle plus Kotoba shadow.
- Build success is stronger: it proves the public CLI, Amu, component closure
  and manifest agree.
- A repository may remain unmigrated longer; it may not claim progress by
  counting extracted predicates.

## Required repository record

Each repository records:

1. the complete source/API baseline;
2. the target component entry and transitive source closure;
3. declared imports, exports, effects and resource bounds;
4. Kotoba CLI and Amu commands for every target;
5. artifact/manifest equivalence evidence;
6. whole-surface oracle parity;
7. consumer shadow/canary selection;
8. rollback, soak and residual risk.

Any missing item leaves the migration incomplete.
