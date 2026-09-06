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


## Addendum — measured state (2026-09-06, supersedes the 2026-09-05 addendum)

This addendum records where the pure S-expression core actually stands,
measured, so the decision above is not read as an implementation claim.
It replaces the 2026-09-05 addendum, which said the pure core was decided but
not implemented anywhere; part of it is now implemented.

**Two authorities answer "is a pure head admitted", and they do not agree.**

| head | frontend (kotoba-sema) | grammar authority (`lang/guest-grammar.edn`) |
|---|---|---|
| `lam` | admitted — `(lam [params] body)` → `(fn [params] body)` | not admitted |
| `app` | admitted — `(app f a…)` → `(f a…)` | not admitted |
| `ref` | admitted — `(ref name)` → `name` | not admitted |
| `perform` | admitted — `(perform :kind/op v…)` → `(cap-call :kind/op v…)` | not admitted |
| `rel` | refused, no lowering | not admitted |
| `query` | refused, no lowering | not admitted |
| `handle` | refused, no lowering | not admitted |

Measured 2026-09-06 against kotoba-sema `fcd4e35` (slice 2, on `9a23bbc`
slice 1), with `kotoba.sema/analyze` + `kotoba.kir/execute` for the KIR
reading and `amu compile --target wasm32 --jvm-free` — this frontend shadowing
amu `origin/main` `1e5b7b8f`'s pin — for the wasm32 reading, the artifact
executed under `runtime/browser-host.mjs`.

**What the four admitted heads cost the backends: nothing.**

```
(defn run [n :i64] :i64 (app (ref inc1) n))
(defn run [n :i64] :i64 (inc1 n))
   -> identical :hir-sha256, identical :kir-sha256, identical wasm32 bytes
```

**What they do not yet buy: identical Definition CIDs.** §3 of this ADR says
both surfaces mint the same Definition CID for equivalent normalized
semantics. Step 1 does not deliver that:

```
pure  (app (ref inc1) (app (lam [x] (+ x 1)) n))
twin  (inc1 (let [g (fn [x] (+ x 1))] (g n)))
   -> same wasm32 sha256 (fda4cc37…), both answer run(1) = 3
   -> DIFFERENT :hir-sha256 and :kir-sha256
```

The artifact matches because wasm carries no local names. The KIR differs
because the synthetic binder introduced for the beta-redex is not the author's
`g`. Identical CIDs need alpha-normalisation, which is step 2 below. **Do not
read the matching wasm bytes as evidence for the CID claim.**

**`rel`, `query` and `handle` are not "unfinished", they are unspecified —
and they are unspecified in three different ways.** Measured 2026-09-06
against kotoba-sema `e90dd5ea`:

| head | blocker | what exists |
|---|---|---|
| `rel` | **undecided semantics**, not a missing primitive | `kgraph-assert!` (arity 3, all-integer EAVT store) is admitted with an **empty effect row**, and `(do (kgraph-assert! 1 2 42) (kgraph-get 1 2))` answers 42. A `rel` head could desugar onto it today. What nobody has decided is whether ADR-544's `rel` *is* the kgraph — binding a pure-core head to one specific store is a language commitment, not a lowering. The `!` in the primitive's own name also says its authors thought it wrote something the effect row does not show. |
| `query` | **missing primitive** | `kgraph-get` is a point lookup: one value, and `i64 MIN` for an absent key. A relational `query` needs pattern variables and more than one result, and nothing produces either. `kgraph-count` + `kgraph-entity-at` let a guest *iterate*, so a pattern query is something one could **write** — which is the open question, primitive or stdlib. The sentinel return is also the first thing `pure-product-profile.edn` `:forbidden-patterns` names. |
| `handle` | **undecided authority**, not a missing primitive | `handle` is the other half of `perform`, and `perform` lowers to `cap-call`, whose answer comes from the **host**. A guest `handle` would let a guest intercept its own capability calls — it changes *who answers an effect*, which is a security decision. `try`/`catch` is not it: that lowers one `[:result T E]` and does not resume. |

An earlier revision of this addendum gave one reason for all three ("no
existing primitive to desugar onto"). For `rel` that was measurably too
strong.

**What `:canonical? true` means today:** in
`kotoba/lang/source_contract.edn` the `.kotoba` kind carries
`:canonical? true` and `:reader-target :kotoba`. This makes `.kotoba` the
canonical *text format* (EDN, one admitted reader target). It does **not**
mean the pure form set of this ADR is the admitted grammar. The clojure-shaped
core (`ns def defn defprotocol definterface defrecord extend-type
extend-protocol let if do main`) is still what every consumer builds its
admitted head set from, and every kbb ops script written under
ADR-2607181900's readiness gate is clojure-shaped for that reason.

**Path to the pure core** (unchanged decision, honest sequence):

1. Extend the grammar authority with the pure head set as *additional
   admitted source forms* that desugar to the existing primitives.
   **Half done.** The frontend lowers four of them; `lang/guest-grammar.edn`
   admits none. Closing the gap is a resync wave, not an edit:
   `lang/vendored-copies.edn` registers five copies of `guest-grammar.edn`
   across four repositories, and until they move together a consumer reading
   the authority rejects source the compiler accepts. The current state is
   recorded in `lang/surface-status.edn` `:other-gaps
   :pure-s-expression-core` and gated by
   `grammar_authority_test/pure-s-expression-core-heads-are-not-yet-admitted`.
2. Land the elaboration so `.cljk` and pure `.kotoba` mint identical
   Definition CIDs for equivalent normalized semantics (this ADR §3). The
   beta-redex measurement above is the concrete first gap.
3. Only then flip `q9-migration.edn :kotoba-only` from an aspirational
   profile (requires `:q1-q8-profile`, not yet satisfied) to the enforced
   admission profile.

Until step 3, a `.kotoba` file may use `lam`/`app`/`ref`/`perform` but cannot
be written in the pure core alone, and the `.kotoba` files in the fleet are
clojure-shaped by authority, not by oversight.

**Admitting the heads broke nothing.** Across the 3,336 `.kotoba`/`.cljk`
files in this workspace, none of the seven heads appears in operator position
or as a `defn` name (measured 2026-09-06).
