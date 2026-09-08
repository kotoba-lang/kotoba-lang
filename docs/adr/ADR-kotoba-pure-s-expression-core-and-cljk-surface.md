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


## Addendum — measured state (2026-09-06, with the §3 result corrected 2026-09-08; supersedes the 2026-09-05 addendum)

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

**What they do buy: identical Definition CIDs. Measured 2026-09-08.** §3 of
this ADR says both surfaces mint the same Definition CID for equivalent
normalized semantics. They do. The 2026-09-06 reading of this pair did not
measure that:

```
pure  (app (ref inc1) (app (lam [x] (+ x 1)) n))
twin  (inc1 (let [g (fn [x] (+ x 1))] (g n)))
   -> same wasm32 sha256 (fda4cc37…), both answer run(1) = 3
   -> DIFFERENT :hir-sha256 and :kir-sha256
   -> IDENTICAL Definition CIDs, on all four definitions in the module
      run  bafyreifbyisa5fsmdmzif276hbwvh4o7pepkqcewnhcv3brwuwtylz52yy
```

`:hir-sha256` and `:kir-sha256` are not the Definition CID. They hash
intermediate text, in which the synthetic binder introduced for the beta-redex
is genuinely not the author's `g`; the identity renames binders to de Bruijn
positions before hashing, which is exactly why it does not care. **The claim
was about DefCIDs and the evidence was about two other hashes** — the failure
mode this workspace names as reporting something other than the thing that was
broken.

Two more surface pairs agree, so pair A is not a coincidence of one shape:

```
(-> n inc1 inc1)          ≡ (app (ref inc1) (app (ref inc1) n))
                            bafyreicjnack62w6ougceqtz5lqupmdvstchsxo7dkf2mxgkst5cva2rj4
(cond (< n 0) 0 :else n)  ≡ (if (< n 0) 0 n)
                            bafyreic5fnjazknv27xhovmzgwuaoaba7w32yidcvktqk2ypcnbkfyfwvu
```

The negative control moves: `(+ x 2)` in place of `(+ x 1)` gives
`bafyreicn3hzig6…`, so agreement here is discriminating rather than a
comparison that never ran.

Measured on `amu` `origin/main` `473e84ab` through
`bin/amu definition-cids --jvm-free`. **Not** on the shared checkout, which is
156 commits behind and pins a `kotoba-sema` older than the heads — run there,
the pure spelling returns `:subset-reject`, which reads exactly like the
language refusing the form. **Do not read the matching wasm bytes as evidence
for the CID claim** either; the wasm agrees for a weaker reason (it carries no
local names at all).

Three pairs are not a corpus. Destructuring, protocol dispatch, `match` and
the `document` forms are unmeasured, and §3 is retired as *the recorded gap*,
not proven in general.

**All seven heads are admitted as of 2026-09-06.** The last three were held
back by three claims — written in this addendum — that turned out, on
measurement, to be about the claims rather than about the language:

| head | the claim | what measuring showed |
|---|---|---|
| `handle` | *"a guest `handle` would let a guest intercept its own capability calls — a security decision, not a lowering"* | **False for this desugar.** `(try (cap-call :clock/now 0) (catch e 7))` is refused *"try body cannot abort; there is nothing to catch"*: a capability call contributes no `:abort` to the effect row, so `try` — and therefore `handle` — structurally cannot wrap one. The protection is enforced by the row, not by withholding the head. |
| `rel` | *"binding a pure-core head to one specific store is a language commitment"* | `kgraph-assert!` is **already admitted under `:pure-product`**, so the head adds no authority to any profile. And the kgraph is the language's *only* relational plane, so there was no second candidate to commit to. |
| `query` | *"a relational `query` needs pattern variables and more than one result"* | That was about a `query` nobody had specified. `(query e a)` **is** the point read — the eliminator for what `rel` introduces — and the head says so in its own arity refusal. A pattern query remains unbuilt, and is now a future head rather than this one's blocker. |

`handle` and `perform` are deliberately **not** a matched pair, and the
asymmetry is recorded rather than hidden: `perform` introduces a capability
effect that only the host answers; `handle` eliminates the abort ability,
which is the one a guest can handle.

`query` keeps its primitive's sentinel — `kgraph-get` answers `i64 MIN` for an
absent `(e,a)` and is **not** wrapped in an `[:option T]`. A datom whose value
*is* `i64 MIN` is indistinguishable from an absent one, so an option would
promise a totality the store does not have.

**Backends differ per head, so they are recorded per head.** Measured
2026-09-06 against amu `9bb5ea68`: `lam` `app` `ref` `perform` `handle` reach
wasm32; `rel` and `query` do not, because `(kgraph-get 1 2)` *alone* fails
`:wasm-local-encoding` on wasm32 while a plain program compiles. That gap is
inherited from the primitives, not introduced here — but a single `:backends`
set for the entry would have been an overclaim for two heads or an underclaim
for five, which is the mistake this entry already made once with
`:kotoba-wasm`.

⚠ **This paragraph called `rel` and `query` KIR-only until 2026-09-08, and
that was false.** Re-measured against amu `origin/main` `a169d7bf`, every
command `--jvm-free`, with each native artifact *executed* through
`tools/kexe_loader.c` rather than merely built:

| head | wasm32 | aarch64-macos | native answer |
|---|---|---|---|
| `lam` `app` `ref` | compiles | compiles | `1` |
| `rel` | `:wasm-local-encoding` | compiles | `1` |
| `query` | `:wasm-local-encoding` | compiles | `7` |
| `handle` | compiles | **refused** `:verify` | — |
| `perform` | compiles | compiles | loader traps |

**Five of the seven answer natively.** A wasm32 ceiling had been read as a KIR
ceiling, and `lang/guest-grammar.edn` carried it as the marker
`:compiler-kir-only`, now removed. Per-target reach lives in
`:amu-targets-per-head`, because the `:backends` vocabulary cannot express it:
`:compiler` is amu as a whole and `:kotoba-wasm` names the legacy
`kotoba.runtime/wasm-binary` emitter, which rejects these heads.

Two controls, because agreement that cannot disagree is not evidence:

- `query` **reads the store** rather than returning a constant — storing 41
  answers `41` where storing 7 answers `7`.
- `handle`'s native refusal is **inherited, not introduced**. The same program
  written with plain `(try .. (catch ..))` and no `handle` at all is refused
  with the identical message, `native artifact contains an unsupported
  effect`, so what native lacks is the `:abort` ability. Recorded as
  `:native-gap` in `lang/surface-status.edn`.

`perform` compiling natively while the bare loader traps is not a language
gap: `kotoba-native`'s README states the backend is not an effect provider, and
this is what that reads like from the guest side. Compiling to a target and
running on that host face are two claims, and only the first holds here.

**Identity, measured:**

```
handle / try                 identical :kir-sha256 AND identical wasm32 bytes
perform / cap-call           identical wasm32 bytes
(app (ref f) n) / (f n)      identical :hir-sha256, :kir-sha256, wasm32 bytes
```

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
   **Done, 2026-09-06.** All seven heads are lowered by the frontend and
   admitted by `lang/guest-grammar.edn` `:sugar :pure-s-expression-core`,
   carried to the five copies `lang/vendored-copies.edn` registers across four
   repositories with the pinned digest advanced in three of them together.
   Gated by `grammar_authority_test/the-pure-s-expression-core-heads-this-authority-admits`
   (both directions) and `/the-pure-heads-backends-are-recorded-per-head-because-they-differ`,
   and measured against a running compiler by
   `authority-claim-lowering-test`, whose `:test` pin now names a frontend
   that has them.
2. Land the elaboration so `.cljk` and pure `.kotoba` mint identical
   Definition CIDs for equivalent normalized semantics (this ADR §3).
   **The recorded gap is retired, 2026-09-08** — the beta-redex pair and two
   others mint identical DefCIDs; see the measurement above. What remains is
   a conformance corpus rather than a fix: pair each surface construct with
   its canonical-core spelling, assert equal DefCIDs, and keep a negative
   control in it.
3. Only then flip `q9-migration.edn :kotoba-only` from an aspirational
   profile (requires `:q1-q8-profile`, not yet satisfied) to the enforced
   admission profile.

Until step 3, a `.kotoba` file may use the whole pure head set but is not
*required* to, and the `.kotoba` files in the fleet are clojure-shaped by
authority, not by oversight.

**Admitting the heads broke one file, not none.** The 2026-09-06 sweep reported
that across the 3,336 `.kotoba`/`.cljk` files in this workspace none of the
seven heads appears in operator position or as a `defn` name. Re-measured
2026-09-08, that is false, and the counterexample predates the sweep:

```
orgs/kotoba-lang/com-aadhaar/src/aadhaar/whole-component.kotoba   added 2026-09-03
  line 140   (query store entity)       2 args
  line 145   (query store entity id)    3 args
  line 149   (query store entity id)    3 args
  line 159   (query store entity id)    3 args
```

`amu check --jvm-free` refuses it with `:kotoba.error/pure-query-arity` — *query
reads one datom: (query entity attribute). It is a point read, not a pattern
query*. The file is a wave-1 Q9 pilot, so the sweep's own corpus contained it.

**It is a semantic collision, not a spelling accident.** The oracle this pilot
reproduces, `src/aadhaar/main.cljc`, defines a PUBLIC `query` of its own — a
store read over a mutable atom, arity 2 and 3. Any component whose public
surface already contains a name in the reserved set collides on migration, and
`query` is an ordinary name for a data-access function to carry.

**The blast radius is exactly one file, measured rather than assumed.** Across
every `.kotoba`/`.cljk` in the workspace, ignores off:

| head | files with the head in operator position |
|---|---|
| `lam` `app` `ref` | 1 — `cloud-itonami-app/.../pure_head_probe.kotoba`, a deliberate probe using them correctly |
| `query` | 1 — the com-aadhaar pilot above |
| `perform` `rel` `handle` | 0 |

So the decision stands and no wave of repair is owed. What was wrong is the
word *nothing*, and the sweep that produced it: a corpus scan that reported a
clean result while a file in its own corpus refused to compile for exactly the
reason being scanned for.
