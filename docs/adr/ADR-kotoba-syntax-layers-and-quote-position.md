# ADR — Syntax layers, quote position, and the Semantic OS convergence

- **Status**: Accepted
- **Date**: 2026-09-04
- **Artifacts**: this ADR only — it records measured state and settles a design
  comparison; it changes no implementation and admits no new surface
- **Related**: `ADR-safe-capability-language.md`,
  `ADR-kotoba-typed-eval.md`, `ADR-kotoba-code-identity-and-abilities.md`,
  `ADR-kotoba-contextual-document-literals.md`,
  `ADR-kotoba-language-surface-status.md`

## Context

A design comparison was brought to the language on 2026-09-04: Common Lisp,
Clojure, Scheme and pure S-expressions were scored as candidate
surface/canonical layers for Kotoba, converging on a recommendation of
"pure S-expression core + Scheme semantics + Clojure data ideas + Unison
identity", with a four-layer structure (pure S-expression reader → friendly
surface → canonical S-expression → semantic DAG) and a strong reified quote
(`Quote(e) = SemanticGraph(e)`). A thirty-five-section "Semantic OS"
integration followed it: immutable facts, content-addressed identity,
capability plus information flow, a consistency ladder, and a polyglot
consistency runtime.

This ADR settles that comparison against the authority surfaces that already
exist. The recommended composition is, almost in full, a description of what
has already landed — with one genuine gap, `quote`.

## Reconciliation — recommendation → existing authority

| Recommended element | Existing authority | State |
|---|---|---|
| Friendly surface (Clojure-shaped) | `lang/guest-grammar.edn` — P0 strict grammar, closed sugar set | landed |
| "Hash semantics, not spelling" | `lang/code-identity.edn` — definition CID payload v2 over typed KIR + profile + desugar-contract + effect row + interface + dependencies | landed |
| α-equivalent definitions share identity | `kotoba.kir.alpha-normalization` (authority, landed 2026-09-02) — one left-to-right counter across all binding forms | landed: `(+ a 1)` and `(+ b 1)` hash identically |
| Canonical S-expression IR `(app (ref CID) (bound 0))` | typed HIR/KIR operator vocabulary | landed as HIR/KIR; a **second** canonical S-expression is **refused** by this ADR |
| Lisp-1: one namespace per symbol | current frontend — one resolution per symbol; function/relation/effect/value distinguished by type and effect elaboration, not namespaces | landed |
| No unrestricted eval | `ADR-kotoba-typed-eval.md` — `(eval request)` is `:code/eval`, DefCID-addressed, receipted; host eval / load-string / reader-eval stay forbidden | landed |
| Application semantics without a required surface `apply` | `:apply` sugar — bounded runtime argument chain, 0–4 arguments | landed (bounded); unrestricted `(apply f args-list)` does not exist |
| No macros in the safe surface | `:no-guest-macros` (`lang/surface-status.edn`) — `defdesugar` bounded pure templates are the admitted alternative | landed |
| `Quote(e) = SemanticGraph(e)` | — | **not implemented** — measured position below; widening path recorded by this ADR |

The scoring that placed "Kotoba-own hybrid" first is therefore an accurate
description of the shipped language, not a request for a new one. The only
element of the recommendation that is genuinely absent is the reified quote.

## Measured position of `quote` (2026-09-04)

Measured with `amu` `bin/kotoba -M check` (nbb/Node, JVM-free), one probe per
file:

1. `(let [{:syms [age]} {(quote age) 4}] age)` → refused
   `:kotoba.error/map-literal-key` — "map literal keys must be keywords,
   integers or strings; this literal has a key of kind: expression".
2. `(let [xs (quote (1 2 3))] (first xs))` → refused as
   `:kotoba.error/internal-operation-failure` ("internal compiler error").
   Fail-closed, but the diagnostic names nothing about the cause.
3. `(let [k (quote foo)] …)` → refused "unbound symbol has no value type" —
   the quoted form's contents are **resolved as an expression**, not read as
   data. There is no quote value.
4. `(let [k (symbol "foo")] …)` → admitted. The value-side spelling of a
   symbol datum is the `symbol` builtin (`guest-grammar.edn :predicates`).

Two authority-surface gaps surround this:

- `quote` appears in kotoba-sema's `structural-heads` (so defdesugar template
  substitution does not descend under it, and the conformance fixture
  `lang/conformance/values/string_symbol.kotoba` uses `(quote age)` in
  map-key position on the values-conformance route), while
  `lang/guest-grammar.edn` — the source-surface authority — mentions `quote`
  nowhere. This is the authority-lags-frontend direction of drift that
  ADR-2607279200's delivery #1 exists to prevent. Resolving it requires the
  grammar entry plus the four vendored consumer copies (w0-exit-gate
  byte-match), so it is recorded here and landed by a separate change, not by
  this ADR.
- The same `(quote age)`-as-key shape passes on the values-conformance route
  and is refused by the subset gate on the plain check route. The two routes
  disagree about a quoted head's meaning; until the grammar entry lands, the
  subset gate's refusal is the conservative reading and stands.

## Decision

1. **The surface stays Clojure-shaped.** A pure S-expression reader is not
   adopted. The AI-first benefit the comparison sought is taken at the
   canonical layer, where it already exists: a definition CID hashes
   normalized, checked semantics — not source spelling — and source that
   never becomes a definition is never executed at all.
2. **Identity is the existing definition CID.** No new identity layer. The
   proposal's `CID = H(Canonical(Resolve(Elaborate(source))))` is literally
   the sealed payload: typed KIR + profile version + desugar-contract version
   + effect row + interface + direct definition dependencies, α-normalized,
   DAG-CBOR, cross-implementation byte-identical (CI6).
3. **The canonical IR is typed HIR/KIR.** A second canonical S-expression
   spelling `(app (ref <CID>) (bound 0))` alongside HIR/KIR would be one more
   surface with one more drift surface and no additional guarantee. The
   semantic-DAG idea of the proposal is already delivered by the definition
   dependency graph.
4. **`quote` keeps no value semantics.** Today and until the widening below
   lands, a quoted form is structural only: it may sit under defdesugar
   templates, and data is spelled with document literals and `(symbol …)` —
   both of which already exist and are bounded.
5. **Reify is recorded as a widening path, not promised.**
   `Quote(e) = SemanticGraph(e)` fits Kotoba and would be strictly more
   useful than Lisp's `quote`, but it must not mint a second object model.
   Preconditions, each already grounded:
   - the graph value is a typed extension of the document plane
     (ADR-kotoba-contextual-document-literals: "no second object model, wire
     tag, codec, or backend operation" carries over);
   - query over it stays inside the bounded constructor family
     (`contains?` / `document-map-get` line), never a second free-form query
     language;
   - hashing a reified graph uses the value-cid lineage of
     `lang/typed-eval.edn`'s three identity layers — a reified graph is a
     value, not a checked definition, and must not mint definition identity;
   - execution of a reified graph only ever happens through checked
     compilation and typed-eval admission: `Data ↛ Execution` stays the
     default.
6. **eval / apply / macro decisions are unchanged**, delegated to their
   existing authorities: `ADR-kotoba-typed-eval.md` (typed eval only), the
   `:apply` sugar (bounded application), `:no-guest-macros` (no user macros).
7. **The "Semantic OS" sections are outside the grammar's contract.**
   Capability semantics (effect / grant / capability / receipt, Biscuit as
   delegation-transport, `OperationalAuthority = CapabilityPossession`) are
   `lang/capability-semantics.edn`; information flow exists in check output
   as the `abac :information-flow` surface; the execution boundary is the
   wasm-component-first rule (ADR-2607252500, `kototama` as linker), not a
   new polyglot runtime; the durable/distributed plane is governed by the
   superproject's kotobase persistence rule (ADR-2608159100), not by this
   repository. Naming those ideas here records the convergence; it admits
   none of them as new language.

## Explicit non-goals

- A pure S-expression reader or a second canonical syntax.
- Lisp-2 namespaces, or a function/value split.
- Giving `quote` value semantics as part of this ADR.
- Relaxing eval, apply, or macro policy beyond the recorded contracts.
- Introducing a polyglot consistency runtime or consensus ladder into the
  language contract.

## Evidence

- Probes 1–4 above, run 2026-09-04 with `amu` `bin/kotoba -M check` under
  nbb/Node (JVM-free), one file per probe.
- α-equivalence: `lang/code-identity.edn :identity-implementations
  :alpha-normalization` (authority, landed 2026-09-02, ten frozen vectors
  byte-identical).
- Definition scope `:closed-deterministic-checked-definition` (effectful
  included, 2026-09-02) — `lang/code-identity.edn :definition-cid`.

## Consequences

- The design comparison is settled: the language already implements the
  recommended hybrid; the residual gap is quote/reify, recorded here as a
  widening path with preconditions, without an implementation claim.
- Two follow-ups are named and deliberately out of scope here:
  1. land `quote`'s structural-only position in `lang/guest-grammar.edn`
     plus the four vendored consumer copies, resolving the authority lag and
     the values-conformance vs subset-gate divergence; and
  2. replace the internal-compiler-error diagnostic for a quoted list value
     with a named refusal (it is fail-closed today, so this is diagnostic
     quality, not a safety gap).
- No CID, contract version, or admission outcome changes as a result of this
  ADR.
