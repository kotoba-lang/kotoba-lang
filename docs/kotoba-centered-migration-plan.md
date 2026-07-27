# Kotoba-centered migration plan

This plan turns the existing Clojure-shaped language contract, Unison-inspired
definition identity, typed abilities, portable effects, and compiler admission
gates into one migration path. It does not introduce a replacement application
DSL. The authoritative workspace decision is root
`ADR-2607279200-kotoba-clojure-shaped-safety-elaboration-migration`.

## Target developer experience

Application source should continue to look like ordinary Clojure-family code:

```clojure
(ns murakumo.models
  (:require [kotoba.http :as http]
            [kotoba.storage :as storage]))

(defn load-model [id]
  (if-let [cached (storage/get [:model id])]
    cached
    (http/get [:models id])))
```

The source does not name numeric capability IDs, WIT imports, portable-effect
envelopes, provider callbacks, or host objects. The compiler infers effects,
elaborates ability parameters, and emits the exact target imports. Explicit
capability values appear only where code attenuates and delegates authority.

The common pipeline is:

```text
closed reader
→ bounded defdesugar/core sugar
→ module resolution
→ type and schema inference
→ interprocedural effect inference
→ implicit ability elaboration
→ typed HIR/KIR
→ semantic definition CID
→ restricted ESM or Wasm Component
→ admitted host provider
```

## Ownership

| Concern | Authority |
| --- | --- |
| Source profile, guest grammar, desugar contract, semantic capability names | `kotoba-lang/kotoba-lang` |
| Typed admission, HIR/KIR, target lowering, verifier | `kotoba-lang/compiler` |
| Semantic definition/codebase implementation and launcher integration | `kotoba-lang/kotoba` |
| Component linking, WIT admission, runtime resource enforcement | `kotoba-lang/kototama` |
| Grant policy and native enforcement boundary | `kotoba-lang/aiueos` and the selected host |
| Fleet placement and Murakumo service mechanisms | `kotoba-lang/murakumo` |

No implementation repository may silently widen the source grammar or
capability vocabulary. Language changes start in the contract and conformance
fixtures, then land in implementations.

## Workstreams

### W0 — Freeze and publish the authority set

- Reconcile dirty or unpublished language/compiler changes before recording
  immutable pins.
- Keep `lang/guest-grammar.edn` as the single source-surface authority.
- Add a check that every compiler-admitted form is classified by
  `lang/surface-status.edn`.
- Add the inverse check: a form marked portable must have compiler, primary
  Wasm, restricted ESM, and conformance evidence or remain partial.
- Record exact profile, desugar, typed-KIR, capability-catalog, and semantic-CID
  contract versions in every artifact.

Exit gate: a standalone checkout can reproduce the same grammar inventory,
normalized form, and artifact identity from immutable dependencies.

### W1 — One frontend elaboration pipeline

- Make the closed reader and bounded `defdesugar` the only syntax expansion
  path.
- Normalize threading forms, `cond`, `case`, `if-let`, destructuring,
  higher-order functions, records/protocols, and lazy operations before effect
  inference.
- Infer transitive effects through calls and recursive groups.
- Treat function effect declarations as optional public contracts or ceilings;
  reject inferred effects outside a declaration.
- Elaborate ordinary named operations such as `http/get` into hidden typed
  ability parameters and exact capability calls.
- Preserve source spans through every phase so diagnostics refer to the
  Clojure-shaped source, not generated KIR.
- Remove user-facing numeric `cap-call` examples once named-operation lowering
  covers the same contract.

Exit gate: representative existing `.cljc` pure and effectful functions compile
without adding framework-specific special forms, and denial diagnostics name
the original operation and source span.

**W1 named-operation slice (2026-07-27):** compiler admits friendly namespaced
heads such as `(clock/now seed)` (no user-facing numeric IDs), infers transitive
effects, enforces optional `{:effects #{...}}` ceilings, and attaches
`:source-operation` + source span to denial diagnostics. Fixtures:
`examples/w1-*.kotoba` and `test/kotoba/compiler/w1_elaboration_test.clj`.

### W2 — One semantic catalog, multiple wire ABIs

- Define each capability once by semantic name, request/result schema, effect,
  resource scope, operation set, limits, and audit requirements.
- Generate compiler numeric IDs, legacy compatibility maps, WIT interfaces,
  provider manifests, and documentation from that catalog.
- Keep wire IDs stable within each ABI while eliminating hand-maintained
  duplicate name/schema declarations.
- Make package requests and inferred effects use semantic names, never numeric
  IDs.
- Verify that every generated provider binding revalidates target, operation,
  scope, quota, deadline, revocation, and receipt identity.

Exit gate: adding a capability requires one semantic declaration and fails CI
unless all target mappings and positive/denial fixtures are present.

### W3 — Semantic identity after elaboration

- Converge `kotoba.semantic-code` and compiler definition identity on canonical
  typed KIR.
- Bind profile, desugar contract, type rules, effect row, public interface, and
  direct definition-CID dependencies.
- Preserve alpha-renaming and formatting independence.
- Add negative vectors for profile, effect, interface, and dependency
  substitution.
- Extend semantic builds from a single source to a closed multi-module graph.
- Keep source-tree, definition, component, and package CIDs distinct in
  receipts and diagnostics.

Exit gate: alias resolution through a package lock produces the expected
definition CID, and any semantic dependency change invalidates dependents
before linking.

### W4 — Clojure-like values without host objects

- Preserve immutable map/vector/set, record/protocol, structural equality,
  persistent update, and generic traversal semantics.
- Define recursive logical values with explicit node/depth/byte budgets.
  Implementations may use arenas and handles, but handles are not the
  application programming model.
- Represent document, style, route, UI, and effect descriptions as ordinary
  schema-checked values.
- Provide reader/printer round trips and deterministic canonical encodings.
- Add representative performance workloads before selecting HAMT/vector-trie,
  arena, rope, or region implementations.

Exit gate: one logical UI/document value can be inspected as data, rendered to
an HTML stream, reconciled to browser DOM, hashed deterministically, and
rejected when its resource bounds are exceeded.

**W4 first slice (2026-07-27, kotoba-lang/compiler `document-ui-render-test`):**
a logical UI tree is a `:document` value (map of `:tag`/`:text`/`:children`),
built and walked with existing `document-*` ops under depth/node/item budgets.
Pure guest recursion renders it to an HTML string; KIR and restricted ESM agree;
`document-equal?` distinguishes same vs different trees; over-budget trees fail
closed at construction.

**W4 second slice (2026-07-27, `document-digest-style-test`):** content-sensitive
structural i64 fingerprint of a `:document` (FNV-style over code points + sorted
map walk) for deterministic identity without host objects; Style vocabulary as
ordinary documents (`:selector` + `:decls` of `:prop`/`:value`) rendered to a CSS
stream.

**W4 third slice (2026-07-27, `document-sha256-test` + multi-repo):** first-class
`document-sha256` host op. Shared deterministic UTF-8 canonical encoding
(`n`/`b`/`i`/`f`/`s`/`k`/`v`/`m` + `K` for map keys) + SHA-256 hex, with KIR /
restricted ESM / real wasm+browser-host parity (kotoba-kir#9, kotoba-script#70,
kotoba-wasm#31, compiler#341). Null-document golden
`1b16b1df538ba12dc3f97edbb85caa7050d46c148134290feba80f8236c83db9`; content-
sensitive and signed-zero identity.

**W4 fourth slice (2026-07-27, `document-dom-reconcile-test`, compiler#342):**
host-side `reconcileUiDocument` maps the shallow UI `:document` shape
(`:tag`/`:text`/`:children`) onto real DOM nodes. Guest never holds host
objects; second reconcile updates text in place and reuses element instances;
dangerous tags (`script`/`iframe`/…) fail closed. Mock DOM for Node tests;
real `document.createElement` in browser. Does **not** replace the flat
`:ui/commit` capability kit (W5).

**W4 fifth slice (2026-07-27, `recursive_tree_value_test`, compiler#343 +
kotoba-kir#10 + kotoba-script#71):** recursive logical values as sealed
schema-checked trees. `[:ref R]` resolves to the nominal `:variant`/`:record`
descriptor carried by the value; a productive `:app/node` (leaf i64 | branch
of two refs) constructs and walks under ADT depth/node budgets (KIR +
restricted ESM). Handles remain out of the application model.

**W4 sixth slice (2026-07-27, `document_dual_renderer_test` +
`recursive_tree_update_test`, compiler#344):** dual-renderer qualification on
one shared logical UI `:document` — pure guest HTML stream and host
`reconcileUiDocument` (mock DOM serialize) agree byte-for-byte; after a
persistent `document-assoc` leaf update both agree again; digests differ.
Soft performance workload: 200× build+render of a 16-leaf tree under a 5s
wall-clock budget (records the current path before HAMT/arena selection).
Companion: sealed recursive tree persistent update via `hetero-vector-assoc`
+ pure guest structural `tree-eq` (KIR + restricted ESM). Design-system
final cutover (Delivery 6) can now target the qualified dual renderers; W5
still owns full `:ui/commit` kit qualification.

**W4 seventh slice (2026-07-27, `document_roundtrip_test`, compiler#345 +
kotoba-kir#11 + kotoba-script#72):** reader/printer round-trip for recursive
logical documents. `document-print` emits the deterministic lowercase hex of
`document-canonical-bytes` (same encoding as `document-sha256`);
`document-read` is the inverse and re-applies depth/node/item/byte budgets.
UI trees and scalar leaves round-trip equal with stable sha256; malformed
print strings fail closed (KIR + restricted ESM).

**W4 eighth slice (2026-07-27, compiler#346 + kotoba-wasm#32):** real wasm +
browser-host import parity for `document-print` / `document-read` (same
multi-repo pattern as document-sha256). `document_roundtrip_test` now covers
KIR + restricted ESM + wasm/browser-host (15 assertions).

**W4 ninth slice (2026-07-27, `document_perf_workload_test`, compiler#347):**
harder performance evidence before structure selection. Multi-section
admitted tree through construct → print → read → equal? (KIR elevated fuel
100× under 3s; ESM default fuel with fresh instantiate 50× under 4s) plus
host-plane construct/print/read/sha256 100× under 2s. Shows tagged-vector
documents are usable under admitted node budgets; HAMT/arena selection is
not yet claimed. **Delivery-4 exit evidence for recursive logical documents
is now complete enough to start Delivery-6 design-system cutover** (dual
renderers + identity + print already green). Remaining program work: W5
host kits, then design-system logical-value cutover (css→html→…).

### W5 — Host capability qualification

Qualify vertical families in dependency order:

1. log and clock;
2. HTTP egress;
3. HTTP ingress and lifecycle;
4. state and storage;
5. UI commit/event and DOM reconciliation;
6. LLM generate/stream/cancel/tool result;
7. queues, timers, actors, and durable workflow;
8. filesystem/process/git/cloud command capabilities for kbb.

For every family require:

- closed request/result schemas;
- compiler inference and admission;
- reference provider plus real target provider;
- positive, denial, timeout, quota, cancellation, and malformed-result vectors;
- audit receipts and revocation behavior;
- parity on at least two applicable runtimes;
- target-specific release evidence.

Reference-runtime success does not qualify a Wasm, browser, workerd, native, or
JIT provider.

### W6 — Repository migration

Inventory each candidate file or namespace as:

- `portable-pure`;
- `portable-effectful`;
- `host-mechanism`;
- `operational-command`;
- `blocked-by-language`;
- `blocked-by-provider`.

Migrate complete consumer-visible slices, not extensions in bulk:

1. compile the existing source as an oracle;
2. compile the Kotoba path;
3. run the same fixtures and property vectors;
4. shadow both paths in the target host;
5. compare outputs, effects, receipts, and budgets;
6. canary with an immediate rollback path;
7. soak;
8. remove the oracle only after release evidence is immutable.

For the design system, preserve dependency order:

```text
css → html → shitsuke → liquid-glass-ui → kotoba-ui
```

**Form-A oracle tranche (2026-07-27, ADR-2607270100 §10): complete 5/5.**
Each repo has a `kotoba/*_core.kotoba` pure string pipeline behind a
byte-equality KIR parity gate (css#2, html#2, shitsuke#7, liquid-glass-ui#8,
kotoba-ui#7). Consumer `.cljc` APIs are unchanged; dual-render seams,
component hiccup, full `theme-css` composition, and `spring-linear-easing`
remain on the host. This is an intermediate oracle experiment — **not** the
Delivery-6 exit. Do not make string-only SSR the final abstraction. Start
cutover only when the shared logical value (Delivery 4 / W4) and both
required renderers for that tranche are qualified.

**Delivery-6 first cutover (2026-07-27, kotoba-lang/css#3):** css logical
style `:document` (`:selector` + `:decls` of `:prop`/`:value`) → CSS stream
in `kotoba/css_document.kotoba`. Byte-equality with form-A `css_core.kotoba`
and key-sorted `css.core` (rule corpus + breakout guard); `document-sha256`
+ print/read identity on style documents. Consumer `css.core` unchanged;
form-A remains oracle.

**Delivery-6 second cutover (2026-07-27, kotoba-lang/html#3):** html logical
UI `:document` (W4 `:tag`/`:text`/`:attrs`/`:children`/`:void`) → HTML stream
in `kotoba/html_document.kotoba`. Byte-equality with form-A `html_core.kotoba`
for escape/void/closed/nested/html5; sorted attrs; sha256 + print/read
identity. Compact emission (pretty-print stays host-side). Consumer
`html.core` unchanged. Next: **shitsuke** tokens/hig on logical values.

For Cloudflare, migrate route semantics only after HTTP ingress is qualified;
until then the cljs entry remains a mechanism adapter. For Murakumo, migrate
the state/LLM/governor/checkpoint vertical slice after the corresponding kits
are qualified. For scripts, keep nbb until kbb has scoped filesystem, process,
Git, secret-custody, and cloud deployment abilities.

## Per-slice acceptance checklist

- [ ] Uses the canonical `.kotoba` compile path, never a legacy emitter.
- [ ] Introduces no ambient host access or target-specific reader branch.
- [ ] Uses ordinary values/functions; any new sugar has bounded deterministic
      desugaring.
- [ ] Inferred effects equal or are contained by the public declaration.
- [ ] Required semantic abilities are minimal and resource-scoped.
- [ ] Missing or broader grants fail before provider invocation.
- [ ] Reference KIR, restricted ESM, and target Wasm semantics agree.
- [ ] Definition and component identities are reproducible.
- [ ] Resource and performance budgets pass representative workloads.
- [ ] Existing oracle, rollback, and soak evidence are recorded.
- [ ] Host mechanism contains no product policy.

## Program-level completion

Kotoba is the center when:

- product logic, views, routes, workflows, policies, and commands have
  canonical Kotoba definitions;
- browser/workerd/native differences live behind qualified providers;
- cljs/cljc remain only as bounded compatibility or implementation layers;
- nbb remains only where kbb capabilities are not yet qualified;
- application source contains no numeric capability IDs, ambient credentials,
  raw host objects, or target-specific interop;
- the same elaborated semantics has stable definition identity and target
  parity;
- capability removal reliably causes compile, instantiate, or provider
  admission failure.

