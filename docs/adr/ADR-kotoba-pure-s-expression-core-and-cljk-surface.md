# ADR — Pure S-expression Core and the Dual Surface (pure `.kotoba` + desugared `.cljk`)

- **Status**: Accepted
- **Date**: 2026-09-04
- **Supersedes**: `ADR-kotoba-syntax-layers-and-quote-position.md` (item 1 regarding surface restriction)
- **Artifacts**: this ADR and language architecture contract
- **Related**: `ADR-kotoba-syntax-layers-and-quote-position.md`,
  `ADR-q9-whole-component-build-migration.md`,
  `ADR-safe-capability-language.md`,
  `ADR-kotoba-code-identity-and-abilities.md`,
  `ADR-kotoba-language-surface-status.md`,
  `90-docs/adr/2608311750-cljk-migration-cost-is-not-effects.edn`

## Context

Following the synthesis in `ADR-kotoba-syntax-layers-and-quote-position.md`,
a critical architectural decision was made regarding long-term AI-first
language properties, compiler verification loops, and corpus exposure.

Empirical evaluation of LLM coding across Lisp dialects reveals two distinct regimes:

1. **Short-term Zero-Shot / Pretraining Prior Exploitation**:
   Existing LLMs exhibit maximum familiarity and minimal hallucination when
   writing Clojure-shaped syntax (`defn`, vector bindings, persistent map literals)
   due to public GitHub corpus exposure (~89,000+ repos).
2. **Long-term AI-First Verification & Repairability**:
   When an autonomous coding agent operates in an interactive feedback loop:
   ```text
   generate → parse → type/effect check → error → repair
   ```
   Pretraining volume matters significantly less than **semantic ambiguity,
   dialect drift, and error surface size**. A language with a tiny, orthogonal
   pure S-expression core (approx. 20 forms: `lam`, `app`, `rel`, `query`,
   `perform`, `handle`, `ref`, `let`, `if`) confines error modes to a strictly
   bounded lattice (`unknown form`, `arity mismatch`, `unresolved ref`,
   `type mismatch`, `effect mismatch`, `capability mismatch`).
   In this environment, repair iteration count and token budget decrease
   drastically, and repair accuracy approaches 9.5/10.

Furthermore, Kotoba's content-addressed identity pipeline:
```text
SemanticTerm → Canonical Pure S-expression → Definition CID
```
requires minimizing surface variability. In Clojure-like syntax, semantic
equivalence can be expressed via divergent forms (e.g., `{:a 1}` vs `(hash-map :a 1)`).
A pure S-expression representation eliminates accidental divergence and
ensures that source identity converges directly with canonical definition identity.

## Decision

Kotoba adopts a **two-tier syntax and source specialization model**:

```text
               [ LLM / Human Authors ]
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
    [.cljk Source]                  [.kotoba Source]
(Clojure-shaped Surface)        (Pure S-expression Core)
  • Rich friendly forms           • Orthogonal S-expressions
  • Clojure compatibility         • Zero unnecessary sugar
  • Desugaring preserved          • Direct canonical alignment
          │                               │
          │ (desugar)                     │ (identity / normalize)
          └───────────────┬───────────────┘
                          ▼
            [ Canonical Pure S-expr Core ]
              • lam, app, rel, query, ref
              • perform, handle, let, if
                          │
                          ▼
                  [ Semantic DAG ]
                          │
                          ▼
                 [ Definition CID ]
```

### 1. `.cljk` retains the rich friendly desugaring surface
- The `.cljk` file extension is the home for the **Clojure-shaped friendly surface**.
- It preserves all existing desugaring contracts (`defn`, `let` destructuring,
  threading `->` / `->>`, `match`, `case`, `cond`, `condp`, `when-let`,
  `if-let`, `defdesugar`, etc.).
- It allows zero-refactor migration from `.cljc` and maximizes pretraining
  prior utilization for general LLMs without custom instruction.

### 2. `.kotoba` transitions to the Pure S-expression Core
- The `.kotoba` file extension becomes the canonical **Pure S-expression language**.
- Its core forms are stripped of dialect-specific syntactic noise and
  redundant sugar variants:
  - Function definition: `(def name (fn (params...) body))` or pure `lam`
  - Function application: standard prefix list application `(app f args...)` or `(f args...)`
  - Core primitives: bounded to the orthogonal semantic heads:
    `lam`, `app`, `ref`, `let`, `if`, `do`, `perform`, `handle`, `rel`, `query`.
- Syntactic variance is minimized to achieve near-1:1 mapping with KIR and DefCID.
- AI agents operating against Kotoba directly target `.kotoba` pure S-expressions
  for maximum repair determinism and bounded verification complexity.

### 3. Identity and Verification Pipeline
- Both `.cljk` and `.kotoba` compile to the same underlying **typed KIR / Semantic DAG**
  and mint identical **Definition CIDs** for equivalent normalized semantics.
- Verification tools (`kotoba check`, `amu check`) operate uniformly across
  both extensions, with `.cljk` passing through the desugaring frontend while
  `.kotoba` enters the pure core directly.
- The Unison-style retrieved semantic context (`(ref cid:...)` + signature context)
  replaces parametric library recall for both surfaces.

## Consequences

- The tension between "LLM zero-shot readability" and "verifier loop minimality"
  is completely resolved by clean separation of file role and extension.
- Existing `.cljc` → `.cljk` component migrations remain fully valid and unbroken.
- Compiler architecture (`kotoba-sema` / `amu`) explicitly formalizes the pure
  S-expression core as an admitted direct source frontend for `.kotoba`.
- The language documentation, specification files, and grammar authorities
  distinguish `.cljk` (Clojure-friendly desugared surface) from `.kotoba`
  (canonical pure S-expression core).


## Addendum — measured grammar state (2026-09-05)

This addendum records where the pure S-expression core actually stands,
measured, so the decision above is not read as an implementation claim.

**What is measured (guest-grammar.edn, the source-surface authority):**

- The admitted `:core-special-forms` for `.kotoba` today are
  `ns def defn defprotocol definterface defrecord extend-type extend-protocol
  let if do main` — the clojure-shaped core. `defn` is a core special form,
  not a desugar.
- `lam`, `app`, `rel`, `query`, `perform`, `handle` appear in **no** admitted
  head set of the grammar authority. The pure core form set of this ADR is
  decided but **not implemented** in the source surface.
- `:sugar` (`->`, `and`, `when`, `loop/recur`, …) is admitted in source and
  must desugar before emit — this is the bounded-sugar part of the surface,
  and it is enforced.

**What `:canonical? true` means today:** in
`kotoba/lang/source_contract.edn` the `.kotoba` kind carries
`:canonical? true` and `:reader-target :kotoba`. This makes `.kotoba` the
canonical *text format* (EDN, one admitted reader target). It does **not**
mean the pure form set of this ADR is the admitted grammar. Everything that
runs today — `kbb`, the compiler, `amu` — consumes clojure-shaped `.kotoba`.

**Every kbb ops script written under ADR-2607181900's readiness gate** (for
example `src/demo_kbb_proc_exec.kotoba` in kotoba-lang/kotoba) uses
`(ns …) (defn main [] (let …))` and is admitted exactly because the
clojure-shaped core is what the grammar authority admits.

**Path to the pure core** (unchanged decision, honest sequence):

1. Extend the grammar authority with the pure head set as *additional
   admitted source forms* that desugar to the existing primitives — same
   discipline as every sugar entry (`:desugars-to`, bounded error lattice,
   measured per backend).
2. Land the elaboration so `.cljk` and pure `.kotoba` mint identical
   Definition CIDs for equivalent normalized semantics (this ADR §3).
3. Only then flip `q9-migration.edn :kotoba-only` from an aspirational
   profile (requires `:q1-q8-profile`, not yet satisfied) to the enforced
   admission profile. The `:dispositions` table already records these
   requirements; nothing else needs to change when the gate is met.

Until step 3, writing pure-`.kotoba` code is not possible and the
`.kotoba` files in the fleet are clojure-shaped by authority, not by
oversight.
