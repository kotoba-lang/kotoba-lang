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
   **W5 first slice (2026-07-27, compiler + provider#2 / ADR 0079):** dual-runtime
   semantic vectors for log+clock on reference (`:clj`) and nbb (`:cljs`).
   Log sequence counters use canonical i64 bigint on cljs (clock-style).
   Denial + invalid-tick vectors on clj; log append/read/limits/truncation/denial
   on nbb. Restored nbb harness after provider extraction (`-M:test` classpath +
   BigInt admission allow). **Does not** flip `:wasm-aot`/`:native-aot`/`:jit`
   (still pending).
   **W5 second slice (2026-07-27, kotoba-component#47 + compiler#349 / ADR 0084):**
   real (non-wiring-only) `clock-provider-wat` + `package-clock-provider` for
   clock-v1's own literal shape. Synthetic self-contained wall/monotonic +
   observation-sequence; closed application composition + `wasm-tools validate`.
   Production host time remains ADR 0073 CLJ/CLJS; WASI clocks on the
   component-model contract are not wired; `:wasm-aot` stays pending (same
   honesty bar as state after ADR 0060).
   **W5 third slice (2026-07-27, kotoba-wasm#33 + kotoba-component#48 +
   compiler#350 / ADR 0085):** real dual-export `log-provider-wat` +
   `package-log-provider` for log-v1 (append+read ring buffer, field sets,
   oldest-drop, read limit/truncation). Requires Canonical `:set` layout
   (pointer+length, typed-set bound 32). Default capacity 8 (parametric 256).
   Provider packaging + validate only — KIR asymmetric record+set application
   emit and Wasmtime multi-step driver deferred. `:wasm-aot` stays pending.
   **Family 1 (log+clock) wasm packaging evidence is now present for both kits.**
   **W5 deepen (2026-07-27, kotoba-component#56 + compiler#366 / ADR 0101):**
   multi-step Wasmtime clock sequence driver (wall→mono; obs delta 1).
   **Family 1 multi-step execution evidence now present for clock.
   **W5 deepen (2026-07-27, kotoba-component#57 + compiler#367 / ADR 0102):**
   multi-step Wasmtime log append sequence (two appends; seq delta 1).
   **Family 1 multi-step execution evidence now present for log+clock.****
2. HTTP egress;
   **W5 family-2 first slice (2026-07-27, provider#3 + compiler#351 / ADR 0086):**
   dual-runtime semantic vectors for `:http/post` on reference (`:clj`) and
   nbb (`:cljs`) with a mock host transport. Timeout-ms and response status
   use canonical i64 bigint on cljs. Denial + origin/timeout fail-closed +
   typed transport error/redaction vectors. Production cljs HTTP transport
   remains unimplemented (JVM transport ADR 0066 only).
   **W5 family-2 second slice (2026-07-27, kotoba-component#49 + compiler#352 /
   ADR 0087):** synthetic `http-provider-wat` + `package-http-provider` —
   timeout/header/url/body bounds + `https://` prefix, fixed ok response,
   no ambient network. Provider packaging + validate only. `:wasm-aot` stays
   pending. **Family 2 dual-runtime + wasm packaging intermediate evidence
   now present.**
   **W5 deepen (2026-07-27, kotoba-component#59 + compiler#370 / ADR 0105):**
   multi-step Wasmtime http post sequence (status sum / 200 = 2). **Family 2
   multi-step execution evidence now present.**
   **W5 deepen (2026-07-27, provider#12 + compiler#384 / ADR 0117):**
   production cljs/nbb HTTP transport (spawnSync hops). **Family 2 production
   cljs transport intermediate evidence now present.**
   **W5 deepen (2026-07-27, provider#13 + compiler#385 / ADR 0118):**
   production cljs/nbb LLM transport (spawnSync hops). **Family 6 production
   cljs transport intermediate evidence now present.**
   **W5 deepen (2026-07-27, provider#14 + compiler#386 / ADR 0119):**
   production cljs/nbb storage transport (spawnSync hops). **Family 4
   production cljs transport intermediate evidence now present. Production
   cljs transports complete for HTTP + LLM + storage.**
   **W5 deepen ADR 0120–0131:** :bytes leaf + object/http get-stream ready/pending/
   joined multi-chunk + chunk-queue + progressive open-stream + guest poll/read
   ops + production HTTP/object transports + object/http get-stream wasm packaging on
   reference + nbb dual-runtime.
   Next: product apps / linear task resource table.
3. HTTP ingress and lifecycle;
   **W5 family-3 first slice (2026-07-27, abi#17 + kotoba-component#54 +
   provider#10 + compiler#362 / ADR 0097):** host-inject / guest-poll
   lifecycle for `:http/accept` (id 17) + `:http/reply` (id 18). Single-
   inflight queue, option incoming request, status [100,599], dual-runtime
   vectors on reference + nbb. No ambient listen; workerd cutover still
   pending. **Family 3 intermediate dual-runtime evidence now present.**
   **W5 family-3 second slice (2026-07-27, kotoba-component#55 +
   compiler#363 / ADR 0098):** synthetic dual-export
   `http-ingress-provider-wat` + `package-http-ingress-provider` (accept
   always-none; reply status bounds + true). **Family 3 dual-runtime + wasm
   packaging intermediate evidence now present.**
   **W5 deepen (2026-07-27, kotoba-component#63 + compiler#375 / ADR 0109):**
   multi-step Wasmtime accept→none (none-count 2). **Family 3 multi-step
   execution evidence now present for accept.**
   **W5 deepen (2026-07-27, kotoba-component#64 + compiler#376 / ADR 0110):**
   multi-step Wasmtime reply true-sum (true-sum 2). **Family 3 multi-step
   now covers accept + reply.**
   **W5 deepen (2026-07-27, kotoba-component#68 + compiler#380 / ADR 0114):**
   accept+reply multi-function multi-step (none+true = 2). **Family 3
   multi-function multi-step walk now present.**
   **W5 family-3 third slice (2026-07-27, provider#11 + compiler#364 /
   ADR 0099):** multi-inflight host queue (default depth 8, parametric
   [1,256]), FIFO accept, host may buffer while a request is pending reply.
   **Family 3 multi-inflight dual-runtime evidence now present.**
   **W5 family-3 fourth slice (2026-07-27, compiler#365 / ADR 0100):**
   workerd adapter `toIncoming`/`handleIncoming`/`fromReply` with
   `max-request-bytes`, method/path/header/body bounds, legacy `fetch`
   fallback. **Family 3 workerd ingress boundary intermediate evidence now
   present.**
   **W5 deepen (2026-07-27, kotoba-component#56 + compiler#366 / ADR 0101):**
   clock multi-step Wasmtime sequence (wall→mono).
   **W5 deepen (2026-07-27, kotoba-component#57 + compiler#367 / ADR 0102):**
   log multi-step Wasmtime append sequence. **Multi-step Wasmtime driver
   evidence now present for clock + log.**
   **W5 deepen (2026-07-27, kotoba-component#65 + compiler#377 / ADR 0111):**
   dual-export compose-closed + log append→read (latest-sequence 1). **Log
   multi-step now covers append-only and append+read ring walk.**
   **W5 family-3 product cutover (2026-07-27, compiler#368 + toshokan#8 /
   ADR 0103):** host-profile `ingress-methods` default split from egress
   allowlist; murakumo-toshokan `handleIncoming` + `max-request-bytes`.
   **First product workerd ingress cutover evidence now present.**
   **W5 deepen (2026-07-27, kotoba-component#58 + compiler#369 / ADR 0104):**
   multi-step Wasmtime ui commit sequence (two empty commits; rev delta 1).
   **Multi-step Wasmtime driver evidence now present for clock + log + ui.**
   **W5 deepen (2026-07-27, kotoba-component#59 + compiler#370 / ADR 0105):**
   multi-step Wasmtime http post sequence (two fixed-ok posts; status sum /
   200 = 2). **Multi-step Wasmtime driver evidence now present for clock +
   log + ui + http.**
   **W5 deepen (2026-07-27, kotoba-component#60 + compiler#371 / ADR 0106):**
   multi-step Wasmtime storage get→missing sequence (disc sum 2).
   **Multi-step Wasmtime driver evidence now present for clock + log + ui +
   http + storage.**
   **W5 deepen (2026-07-27, kotoba-component#61 + compiler#372 / ADR 0107):**
   multi-step Wasmtime llm generate sequence (fixed text-length sum 4).
   **Multi-step Wasmtime driver evidence now present for clock + log + ui +
   http + storage + llm.**
   **W5 deepen (2026-07-27, kotoba-component#62 + compiler#374 / ADR 0108):**
   multi-step Wasmtime object put-block sequence (true-sum 2).
   **Multi-step Wasmtime suite complete for intermediate-packaged kits:
   clock + log + ui + http + storage + llm + object-write.**
   **W5 deepen (2026-07-27, kotoba-component#63 + compiler#375 / ADR 0109):**
   multi-step Wasmtime http-ingress accept→none sequence (none-count 2).
   **Family-3 ingress multi-step execution evidence now present (accept path).**
   **W5 deepen (2026-07-27, kotoba-component#64 + compiler#376 / ADR 0110):**
   multi-step Wasmtime http-ingress reply true-sum sequence (true-sum 2).
   **Family-3 ingress multi-step now covers both dual-export paths (accept +
   reply).**
   **W5 deepen (2026-07-27, kotoba-component#68 + compiler#380 / ADR 0114):**
   multi-function accept+reply multi-step (none+true = 2). **Family-3 multi-step
   covers accept-only, reply-only, and accept+reply dual-export walks.**
   **W5 deepen (2026-07-27, kotoba-component#65 + compiler#377 / ADR 0111):**
   dual-export `compose-closed` subset matching + log append→read multi-step
   (latest-sequence 1). Closes ADR 0102 deferred append+read walk. **State
   multi-step remains covered by ADR 0060/0061 in compiler.**
   **W5 deepen (2026-07-27, kotoba-component#69 + compiler#381 / ADR 0115):**
   log ring-overflow oldest-drop multi-step (oldest-sequence 2). **Log multi-step
   covers append-only, append+read, and ring-overflow oldest-drop.**
   **W5 deepen (2026-07-27, kotoba-component#70 + compiler#383 / ADR 0116):**
   log truncation-flag dual-read multi-step (trunc-sum 2). **Log multi-step suite
   complete (append / append+read / oldest-drop / truncation).**
   **W5 deepen (2026-07-27, kotoba-component#66 + compiler#378 / ADR 0112):**
   object-write multi-function put+CAS multi-step (true-sum 2). **Write-path
   multi-step now covers put-only and put+CAS dual-export walks.**
   **W5 deepen (2026-07-27, kotoba-component#67 + compiler#379 / ADR 0113):**
   ui multi-function commit+next-event multi-step (rev+none = 2). **UI
   multi-step now covers commit-only and commit+next-event dual-export walks.**
   **W5 deepen (2026-07-27, kotoba-component#68 + compiler#380 / ADR 0114):**
   http-ingress multi-function accept+reply multi-step (none+true = 2).
   Closes ADR 0110 deferred lifecycle coupling. **Family-3 multi-step now
   covers accept-only, reply-only, and accept+reply dual-export walks.
   All dual-export intermediate providers now have multi-function multi-step
   evidence (log, ui, object-write, http-ingress).**
   **W5 deepen (2026-07-27, kotoba-component#69 + compiler#381 / ADR 0115):**
   log ring-overflow oldest-drop multi-step (capacity 2, 3 appends → oldest 2).
   Closes ADR 0111 deferred ring-buffer oldest-drop slice. **Log multi-step
   covers append-only, append+read, and ring-overflow oldest-drop.**
   **W5 deepen (2026-07-27, kotoba-component#70 + compiler#383 / ADR 0116):**
   log truncation-flag dual-read multi-step (after0 true + after1 false → 2).
   Closes ADR 0111 deferred truncation multi-step slice. **Log multi-step
   suite complete: append-only, append+read, oldest-drop, truncation flag.**
   **W5 deepen (2026-07-27, provider#12 + compiler#384 / ADR 0117):**
   production \`:cljs\`/nbb HTTP transport via spawnSync hops (allow-list,
   redirect decline, destination-IP block, local echo POST). Closes ADR 0066
   explicit cljs gap for \`:http/post\`. **Family 2 production cljs transport
   intermediate evidence now present.**
   **W5 deepen (2026-07-27, provider#13 + compiler#385 / ADR 0118):**
   production \`:cljs\`/nbb LLM transport via spawnSync hops (①→②→③ resolve,
   Anthropic Messages wire, typed 429/401/500, bearer). Closes ADR 0064
   explicit cljs gap for \`:llm/generate\`. **Family 6 production cljs transport
   intermediate evidence now present.**
   **W5 deepen (2026-07-27, provider#14 + compiler#386 / ADR 0119):**
   production \`:cljs\`/nbb storage transport via spawnSync hops (required
   host endpoint, put/get/delete/conflict, typed 429/500). Closes ADR 0071
   explicit cljs gap for \`:storage/transact\`. **Production cljs transports
   complete for HTTP + LLM + storage (ADR 0117–0119).**
   **W5 deepen (2026-07-27, kotoba-kir#12 + provider#15 + compiler#387 / ADR 0120):**
   runtime \`:bytes\` leaf type (JVM byte[] / cljs Uint8Array); object put-block
   field type is \`:bytes\` (closes ADR 0095 string workaround). **Reference-path
   \`:bytes\` admitted.**
   **W5 deepen (2026-07-27, kotoba-kir#13 + provider#16 + compiler#388 / ADR 0121):**
   host \`[:task [:stream :bytes]]\` / \`[:stream :bytes]\` + object get-stream
   (id 14) dual-runtime ready-task → stream-read. **Reference dual-runtime
   covers stream-object write + get-stream ready-task.**
   **W5 deepen (2026-07-27, provider#17 + compiler#389 / ADR 0122):**
   \`:http/get-stream\` (id 13) dual-runtime ready-task (url+headers, exact-origin,
   stream-read). **Reference dual-runtime covers object + http get-stream.**
   **W5 deepen (2026-07-28, kotoba-kir#14 + provider#18 + compiler#390 / ADR 0123):**
   pending→ready via \`task-fulfill!\` + multi-chunk join (\`{:pending true}\` /
   \`{:chunks [...]}\` transport replies) for object (id 14) and http (id 13)
   get-stream on the reference path. **Pending scheduling + multi-chunk first
   slice landed (host-side; not guest poll/read, not true async producers).**
   **W5 deepen (2026-07-28, compiler / ADR 0124):** nbb (`:cljs`) dual-runtime
   vectors for object + http get-stream ready / pending→fulfill / multi-chunk
   (8+8 cases); kit `stream-object-v1` http request synced to url+headers record.
   **Get-stream dual-runtime covers reference + nbb for ready/pending/multi-chunk.**
   **W5 deepen (2026-07-28, kotoba-kir#15 + provider#19 + compiler#392 / ADR 0125):**
   true multi-chunk via \`{:chunk-queue [...]}\` — each \`stream-read!\` yields one
   producer chunk without pre-join (atomic chunk vs max-bytes). Reference + nbb
   dual-runtime (9+9). **True multi-chunk producer first slice landed
   (host-side; not guest poll/read, not progressive live push).**
   **W5 deepen (2026-07-28, kotoba-kir#16 + provider#20 + compiler#393 / ADR 0126):**
   progressive live push via \`{:open-stream true}\` — host \`stream-enqueue!\` /
   \`stream-close!\` while consumer polls empty+open as \`pending?\`. Reference +
   nbb (10+10). **Progressive open-stream first slice landed (host-side;
   not guest poll/read/enqueue language ops, not blocking wait).**
   **W5 deepen (2026-07-28, kotoba-kir#17 + compiler#394 / ADR 0127):**
   guest-language \`task-ready?\` (poll) and \`bytes-task-byte-count\` (poll+drain+count)
   evaluated inside KIR; dual-runtime guest exports return i64 without host
   \`task-poll\`/\`stream-read!\`. Reference + nbb (12+12). **Guest poll/read first
   slice landed (not multi-value stream return, not progressive guest drain).**
   **W5 deepen (2026-07-28, provider#21 + compiler / ADR 0128):** production
   \`production-get-stream-transport\` for \`:http/get-stream\` (GET, same
   allow-list/redirect/SSRF floor as POST ADR 0066/0117; \`{:bytes ...}\` ready
   task). clj + cljs. **Live HTTP get-stream transport first slice landed
   (not object-store live, not status/headers surface).**
   **W5 deepen (2026-07-28, provider#22 + compiler / ADR 0129):** production
   \`object-transport/production-transport\` — host-configured endpoint, fixed-path
   JSON for get-stream/put-block/CAS; no ambient object store. clj + cljs.
   **Live object-store transport first slice landed (not Component v0.3 packaging).**
   **W5 deepen (2026-07-28, kotoba-component#71 + compiler / ADR 0130):** synthetic
   object get-stream wasm packaging (binding+key → i64 body-length 2) + multi-step
   Wasmtime sum 4. **Get-stream Component packaging first slice landed
   (not linear bytes-task resource table, not HTTP get-stream packaging).**
   **W5 deepen (2026-07-28, kotoba-component#72 + compiler / ADR 0131):** synthetic
   http get-stream wasm packaging (url+headers → i64 body-length 2) + multi-step
   Wasmtime sum 4. **HTTP get-stream Component packaging first slice landed
   (not linear bytes-task resource table).**
   Next: product apps / linear task resource table.
4. state and storage;
   **W5 family-4 first slice (2026-07-27, provider#4 + compiler#353 / ADR 0088):**
   dual-runtime semantic vectors for `:state/transact` on reference (`:clj`)
   and nbb (`:cljs`). Entry versions use canonical i64 bigint on cljs.
   Round-trip, instance isolation, capacity typed error, missing-grant denial.
   State wasm packaging already exists (ADR 0060/0061).
   **W5 family-4 second slice (2026-07-27, provider#5 + compiler#354 / ADR 0089):**
   dual-runtime semantic vectors for `:storage/transact` on reference (`:clj`)
   and nbb (`:cljs`) with mock host transport. Versions (entry, expected,
   conflict) use canonical i64 bigint on cljs. Put boundary, missing/conflict,
   redaction, invalid-version fail-closed, denial. Production cljs storage
   transport still unimplemented (JVM ADR 0071). **Family 4 dual-runtime
 intermediate evidence now covers state + storage.** Next: UI dual-runtime
   (family 5), storage wasm packaging, or HTTP ingress kit (family 3).
5. UI commit/event and DOM reconciliation;
   **W5 family-5 first slice (2026-07-27, provider#6 + compiler#355 / ADR 0090):**
   dual-runtime semantic vectors for `:ui/commit` + `:ui/next-event` on
   reference (`:clj`) and nbb (`:cljs`). Revisions/node-count use canonical
   i64 bigint on cljs. Declarative commit/events, stale revision fail-closed,
   node/typed-set limit, missing-grant denial. No DOM objects cross the
   boundary. Next: ui wasm packaging, LLM dual-runtime (family 6), or HTTP
   ingress kit (family 3).
6. LLM generate/stream/cancel/tool result;
   **W5 family-6 first slice (2026-07-27, provider#7 + compiler#356 / ADR 0091):**
   dual-runtime semantic vectors for `:llm/generate` on reference (`:clj`)
   and nbb (`:cljs`) with mock host transport. Token budgets, temperature,
   and usage counts use canonical i64 bigint on cljs. Generation boundary,
   model/budget fail-closed, typed errors/redaction, missing-grant denial.
   **W5 deepen ADR 0118:** production cljs/nbb LLM transport landed; was unimplemented (JVM ADR 0064);
   streaming/tool-calls out of v1 kit. **Reference dual-runtime now covers
   log, clock, http, state, storage, ui, llm.**
   **W5 remaining kit wasm (2026-07-27, kotoba-component#50 + compiler#357 /
   ADR 0092):** synthetic `ui-provider-wat` + `package-ui-provider` dual-export
   (revision counter, empty next-event, node-count bound). **Wasm packaging now covers clock, log, http, state, ui.**
   **W5 deepen (2026-07-27, kotoba-component#58 + compiler#369 / ADR 0104):**
   multi-step Wasmtime ui commit sequence (rev delta 1). **Family 5 multi-step
   execution evidence now present for commit.**
   **W5 deepen (2026-07-27, kotoba-component#67 + compiler#379 / ADR 0113):**
   commit+next-event multi-function multi-step (rev+none = 2). **Family 5
   multi-step covers commit-only and commit+next-event.**
   **W5 remaining kit wasm (2026-07-27, kotoba-component#51 + compiler#358 /
   ADR 0093):** synthetic `storage-provider-wat` + `package-storage-provider`
   (always-missing, disc range-check). **Wasm packaging now also covers
   storage.**
   **W5 deepen (2026-07-27, kotoba-component#60 + compiler#371 / ADR 0106):**
   multi-step Wasmtime storage get→missing (disc sum 2). **Family 4 multi-step
   execution evidence now present for storage.**
   **W5 remaining kit wasm (2026-07-27, kotoba-component#52 + compiler#359 /
   ADR 0094):** synthetic `llm-provider-wat` + `package-llm-provider`
   (budget/string bounds; fixed ok completion; no ambient credentials).
   **Wasm packaging now covers clock, log, http, state, storage, ui, llm
   (all 7 dual-runtime kits with intermediate packaging evidence).**
   **W5 deepen (2026-07-27, kotoba-component#61 + compiler#372 / ADR 0107):**
   multi-step Wasmtime llm generate (text-length sum 4). **Family 6 multi-step
   execution evidence now present for generate.**
   **W5 stream-object dual-runtime first slice (2026-07-27, provider#8/#9 +
   compiler#360 / ADR 0095):** reference + nbb vectors for the write path —
   `:object/put-block` + `:object/compare-and-set-ref` (binding allowlist,
   bounded payload as host string, bool results, redaction, denial). Linear
   Component v0.3 keeps linear handle ABI; **ADR 0120–0131 :bytes + object/http
   get-stream ready/pending/joined multi-chunk/chunk-queue/open-stream + guest
   poll/read + production transports + object/http get-stream wasm packaging on reference + nbb.** **Reference dual-runtime now also covers stream-object write ops.**

   **W5 stream-object write-path wasm (2026-07-27, kotoba-component#53 +
   compiler#361 / ADR 0096):** synthetic dual-export
   `object-write-provider-wat` + `package-object-write-provider` (bounds +
   option disc; always-true; no ambient store). **Write-path dual-runtime +
   wasm packaging intermediate evidence now present.**
   **W5 deepen (2026-07-27, kotoba-component#62 + compiler#374 / ADR 0108):**
   multi-step Wasmtime put-block sequence (true-sum 2). **Object-write multi-step
   execution evidence now present.**
   **W5 deepen (2026-07-27, kotoba-component#66 + compiler#378 / ADR 0112):**
   put+CAS multi-function multi-step (true-sum 2). **Object-write multi-step
   covers put-only and put+CAS.**
   **See family 3 first+second slices (ADR 0097/0098) above.** Next:
   multi-inflight / workerd adapter, get-stream dual-runtime once reference
   `:bytes`/task values exist, or Wasmtime multi-step drivers / production
   cljs transports.
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
`html.core` unchanged.

**Delivery-6 third cutover (2026-07-27, kotoba-lang/shitsuke#9):** shitsuke
token groups as `:document` (`:prefix`/`:group`/`:entries` or nested
`:props`) → CSS custom-property stream in `tokens_document.kotoba` +
`hig_document.kotoba`. Byte-equality with form-A tokens_core/hig_core;
sha256 + print/read identity.

**Delivery-6 fourth cutover (2026-07-27, liquid-glass-ui#10):** liquid-glass
material tokens as `:document` → `--liquid-glass-*` stream in
`tokens_document.kotoba` (light+dark roots, surface/motion nested).
Byte-equality with form-A; print/read identity.

**Delivery-6 fifth (final) cutover (2026-07-27, kotoba-ui#9):** kotoba-ui
composition-layer theme as `:document` (`:accent` hex, optional
`:appearance`) → cascade layer-order + accent CSS-var stream in
`theme_document.kotoba`. Byte-equality with form-A `theme_core` helpers
(hex→rgba, accent decls, layer, shell class/layout constants); sha256 +
print/read identity. Full `theme-css` host join (shitsuke+glass+shell)
remains `.cljc`. Form-A remains oracle; consumer APIs unchanged.
**Design-system dependency-order document cutover is complete 5/5**
(css → html → shitsuke → liquid-glass-ui → kotoba-ui). Remaining program
work: W5 host kits (not a design-system file port).

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

