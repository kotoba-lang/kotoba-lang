# Capability Value Semantics

Status: proposed profile extension
Date: 2026-07-01

Safe Kotoba already rejects ambient host authority and checks literal resource
ids against policy. Capability values are the profile-level contract for dynamic
resources: a program should pass an explicit scoped authority object, not a
plain string that later becomes authority.

## Principle

```text
resource string != capability
```

A resource id names a graph, model, egress origin, secret, or host surface. A
capability value authorizes a specific action on a constrained resource set.

## Core Capability Values

| Capability value | Actions | Resource constraint |
|---|---|---|
| `GraphReadCap` | graph read/query | graph CID or graph set |
| `GraphWriteCap` | graph assert/retract | graph CID or graph set |
| `InferCap` | model inference | model CID or model set |
| `EgressCap` | outbound request | origin/method/path constraints |
| `SecretReadCap` | secret unwrap/read | secret id, purpose, expiry |
| `ClockCap` | time access | monotonic/wall-clock mode |
| `RandomCap` | entropy access | deterministic/security entropy class |

The concrete ABI representation is implementation-owned, but the profile
requires these semantic fields:

- capability class;
- action set;
- resource constraint;
- issuer/delegation reference where applicable;
- expiry or epoch where applicable;
- policy/grant binding id;
- receipt id after use.

## Effect Consistency

If a function accepts a capability value, its effect row must include the effect
enabled by that capability. For example:

```clojure
(defn write-note
  {:effects #{:graph-write}}
  [^GraphWriteCap cap note]
  (graph-assert! cap note))
```

The profile rule is:

```text
capability parameter class => required effect
```

An implementation may infer this effect, but it must not silently treat the
capability as pure data when invoking host authority.

## Dynamic Resource Rule

Dynamic resource access should be expressed as capability passing:

```clojure
;; preferred
(defn writer [cap obj]
  (graph-assert! cap obj))

;; not authority by itself
(defn writer [graph-cid obj]
  (graph-assert-by-id! graph-cid obj))
```

The second form can remain as a compatibility surface, but it must be checked by
runtime policy and will often require broader grants. The first form lets
least-privilege policy generation avoid wildcard resource grants.

## Runtime Intersection

Using a capability value requires runtime intersection:

```text
effective capability =
  capability value
  intersect external delegation
  intersect local policy
  intersect component manifest
  intersect package lock
  intersect surface policy
  intersect runtime limits
```

The host call must fail closed if the intersection is empty.

## Receipt Requirement

Every host call using a capability value must emit or link to a receipt
containing:

- component id;
- package/component CID when available;
- capability class;
- concrete resource/action;
- delegation/grant id;
- policy id;
- result: grant, denial, or trap.

## Conformance Direction

The conformance suite should cover:

- a string resource id is not accepted where a capability value is required;
- a capability value carries the effect required by its class;
- a dynamic graph/model call can avoid wildcard policy when capability-valued;
- receipts include concrete capability information.

## Host-call dispatch

`src/kotoba/lang/capability_host.cljc` (`kotoba.lang.capability-host`) is the
pure CLJC dispatch kernel that hosts wire their provider invocation paths
through. `guard-call` takes the host call name, the requested capability, the
CACAO grants, the local policy, the current date, and the concrete provider
`:handler`; it runs `intersect-grants` at call time and:

- on denial, returns `{:kotoba.host/ok? false :kotoba.host/denied <reason>
  :kotoba.host/receipt <denial receipt>}` WITHOUT invoking the handler
  (fail closed);
- on grant, invokes the handler with the CONCRETE (post-intersection)
  capability — never the broader requested one — and returns
  `{:kotoba.host/ok? true :kotoba.host/result .. :kotoba.host/receipt ..}`;
- when the handler throws, records a receipt with `:receipt/outcome :error`
  and rethrows.

Every outcome builds its receipt via `kotoba.lang.capability-values/receipt`;
denial receipts additionally carry `:receipt/denied <reason>` (and embed the
requested capability, since no concrete capability exists), and
`validate-receipt` accepts both shapes. `journal` returns an atom-backed
append-only recorder (`{:record! fn :entries fn}`) so a host gets an ordered
audit trail of receipts with zero extra dependencies.

Host provider capability kinds (`:host/clipboard-read`,
`:host/clipboard-write`, `:host/http`, `:host/fs-read`, `:host/fs-write`,
`:host/keychain-read`, `:host/keychain-write`, `:host/notify`,
`:host/ledger-append`) are registered in `effect-for-kind`, following the
extensibility rule above: each host kind is its own required effect.
Host-dispatch conformance fixtures (`:type :host-dispatch` in
`lang/capability-conformance/manifest.edn`) are exercised by
`test/kotoba/lang/capability_host_test.clj` and by
`bb scripts/check-capability-values.bb`.

## Capability-passing (S4b)

The first capability-passing slice makes capability values flow as
first-class arguments through guest code (including compiled wasm) as opaque
integer handles, resolved back to concrete capabilities at host-call time.
The host side lives in `kotoba-lang/kotoba` (`kotoba.cap-table`,
`kotoba.host-providers/host-call-with`); this repository owns the semantic
contract it implements:

- **Acquire.** `(cap-acquire <kind-kw> <resource>)` runs the `guard-call`
  intersection — requested ∩ CACAO grants ∩ local policy — exactly ONCE, at
  acquisition. On grant the CONCRETE (post-intersection) capability is stored
  in a per-run capability table under a small positive integer handle
  (i64-safe; handle 0 is never issued) and the handle is returned. On denial
  no handle is ever issued and the call fails closed with the
  `intersect-grants` denial reason.
- **Use.** A guarded host op accepts the handle as its leading argument
  (`<op>-with` variants, e.g. `(host-i64-roundtrip-with cap code)`). The
  handle is resolved back to the stored concrete capability — no
  re-intersection happens, because the stored capability IS the intersected
  one — but the resolution fails closed when the handle was never issued
  (`:unknown-cap-handle`), the capability kind does not match the op
  (`:cap-kind-mismatch`), or the stored expiry has passed at the use-time
  clock (`:expired`). Expiry is therefore re-checked on EVERY use: a handle
  acquired before expiry goes stale once `:now` advances past
  `:cap/expires`.
- **Receipts.** Acquisition and every use each leave a receipt in the same
  append-only journal (`capability-host/journal`). `:receipt/call`
  distinguishes them (`:cap/acquire` vs the `<op>-with` surface), and both
  carry `:receipt/cap-handle` so the audit trail links every use back to the
  acquisition that authorized it. Grant receipts embed the concrete
  capability, never the broader requested one.
- **Effect consistency.** When a function declares an `:effects` row, every
  capability kind it acquires or uses through a handle must be covered by
  the row (`effects-consistent?`); under-declaration is rejected at
  check/emit time, per the Effect Consistency rule above.
- **Compiled threading.** The wasm demonstration shape threads the handle as
  a first-class i64 through a compiled module:
  `kotoba.cap_acquire(kind_id: i32, res_ptr: i32, res_len: i32) -> i64` and
  `kotoba.host_i64_roundtrip_with(cap: i64, code: i64) -> i64`.

### Typed capability parameters

Capability handles are typed at function boundaries. The canonical (and
only) metadata form is a `:cap` entry in the parameter metadata map, whose
value is a capability kind registered in `effect-for-kind`:

```clojure
(defn ^{:effects #{:host/ledger-append}} use-ledger
  [^{:cap :host/ledger-append} c ^:i64 code]
  (host-i64-roundtrip-with c code))
```

A `^:cap/<kind>` reader shorthand is not accepted: kinds are themselves
namespaced keywords (`:host/ledger-append`), which a keyword shorthand
cannot spell.

Static guarantees (checked at check/emit time by the launcher's runtime
slice, `kotoba-lang/kotoba` `kotoba.runtime/cap-typed-problems`; the kind
vocabulary is this repository's `effect-for-kind`):

- unknown param kinds are rejected (`:unknown-capability-kind`);
- the leading argument of every `<op>-with` use must be **cap-typed** — the
  direct result of `(cap-acquire ...)`, a cap-typed parameter, or a
  let-bound alias — never an untyped/forgeable integer
  (`:cap-arg-not-capability`);
- kind consistency is enforced at every call site — a cap-typed value
  presented to an op or a callee parameter of another kind is
  `:cap-kind-mismatch` (always decidable in this slice: kinds are static);
- a function's required effects include its cap-typed param kinds and,
  through a fixpoint over direct calls, everything its callees require —
  an under-covering declared `:effects` row is
  `:cap-effect-under-declared` (Effect Consistency, interprocedurally).

**i64 lowering.** In compiled wasm a cap-typed parameter lowers to an i64
handle slot (the same machinery as `^:i64`), so handles flow through
user-defined function calls end-to-end: the launcher's
`demo_cap_threading` gate passes one handle through two levels of user
functions down to the `host_i64_roundtrip_with` import. At run time the
static gate is doubled dynamically: `resolve-use` re-checks kind and expiry
on every host call, so a module produced by a checker-bypassing front end
still fails closed at the host binding.

Remaining for S4b beyond this slice: kotoba-auth integration for chain
issuance/rotation, compiled-code threading for the full host-op ABI surface
(cap-typed params thread through the i64 host ABI end-to-end; the
pointer+length/buffer ABI `<op>-with` variants are interpreter-only), and
ownership of the capability contract EDN by kotoba-core-contracts. Dynamic
verification of full CACAO delegation chains is delivered by the layering
below and is wired into the launcher (`run --cacao`).

## CACAO chains

Delegation-chain authorization is split across three strictly layered owners
so this repository stays crypto-free:

1. **Crypto** — `kotoba-lang/cacao` (`cacao.core/verify-chain`) verifies an
   ordered vector of `cacao_b64` links (root first): every link's Ed25519
   signature, `iss`/`aud` re-issuance linkage, resource attenuation
   (`child ⊆ parent` under exact match or a parent trailing-`*` wildcard),
   expiry ordering, and optional `:now` freshness. It returns only a result
   map: `{:chain/valid? :chain/problems :chain/root-iss :chain/holder
   :chain/resources :chain/expires :chain/depth}`.
2. **Mapping (this repository)** —
   `src/kotoba/lang/capability_cacao.cljc`
   (`kotoba.lang.capability-cacao/grants-from-chain`) is a PURE mapping from
   that VERIFIED result map (it never sees b64 or signatures) to the grant
   shape consumed by `intersect-grants`.
3. **Wiring** — the launcher (`kotoba-lang/kotoba`, `run --cacao <file>`)
   calls both and feeds the grants into the existing guarded run.

Resource URI convention (the `/`-to-slash keyword rule): a chain grants
capabilities as

```text
kotoba://cap/<kind>/<resource>
```

where `<kind>` is the capability kind keyword printed without the colon — a
bare kind maps to a bare keyword (`graph-read` → `:graph-read`) and a
namespaced kind keeps its namespace as a path segment (`host/clipboard-read`
→ `:host/clipboard-read`). `<resource>` is the resource string;
`kotoba://cap/<kind>/*` grants the `:any` wildcard scope. Only kinds
registered in `effect-for-kind` are granted; unknown kinds and non-cap URIs
are SKIPPED with a note under `:skipped` — never silently granted.

`grants-from-chain` returns `{:grants [..] :skipped [..]}` where each grant
carries `:grant/expires` = `:chain/expires` (an ISO instant truncated to its
date part — an unintelligible expiry fails closed rather than widening to
never-expires) and a provenance-friendly `:grant/id` of
`"cacao:<root-iss>:<index>"`, so every receipt's `:cap/provenance` traces
back to the delegating root issuer. When `:chain/valid?` is not `true` the
result is `{:grants [] :problems [..]}` — grants are NEVER derived from an
unverified chain.

Conformance fixtures of `:type :cacao-grants` live in
`lang/capability-conformance/` and are exercised by
`test/kotoba/lang/capability_cacao_test.clj` and by
`bb scripts/check-capability-values.bb`.

## Capability Value Contract

The machine-checkable form of this document is
`src/kotoba/lang/capability_values.cljc` (`kotoba.lang.capability-values`):
first-class `GraphReadCap` / `GraphWriteCap` / `InferCap` values
(`graph-read-cap`, `graph-write-cap`, `infer-cap`, `capability?`,
`validate-cap`), effect-row consistency (`effect-for-kind`,
`effects-consistent?`), host-call-time CACAO grant / local policy
intersection producing the concrete authorized capability or a fail-closed
denial (`intersect-grants`), and receipts embedding the concrete — never the
broader requested — capability (`receipt`, `validate-receipt`). Conformance
fixtures live under `lang/capability-conformance/` (positive/negative cases
listed in `lang/capability-conformance/manifest.edn`); they are exercised by
`test/kotoba/lang/capability_values_test.clj` and by the gate
`bb scripts/check-capability-values.bb`, which loads the same CLJC source.

