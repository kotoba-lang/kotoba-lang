# ADR — kotoba-lang foundational horizontal stdlib layer

- **Status**: Accepted — **implemented**: 12 foundational + 3 composite libs shipped at v0.1.0 / M6
- **Date**: 2026-06-30 (decision) · updated 2026-07-01 (completion + positioning)
- **Artifacts**: `docs/lang/coverage.edn` `:stdlib` track; `docs/lang/stdlib-versioning.md` (M6 policy); `docs/lang/stdlib-gates.md` (M4 gate set); repos `kotoba-lang/{coll,spec,json,wit,async,time,fs,http,io,test,fmt,lsp,scheduler,store,lint}`
- **Related**: `ADR-kotoba-lang-profile.md`, `ADR-safe-capability-language.md`

## Context

`kotoba-lang` ships a thick set of **vertical / domain DSL** libraries
(`langchain`, `langgraph`, `statechart`, `policy`, `mcp`, `num`,
`multiformats`, `dag-cbor`, `ed25519`, `cacao`, `dsl-core`, `sigma`, `dmn`,
`bpmn`, `cmmn`). They are all zero-dependency portable `.cljc`, designed to run
on JVM / SCI / ClojureScript / GraalVM / kotoba-WASM, with I/O host-injected.

What was **thin** was the **horizontal foundational stdlib** those vertical
libs implicitly assume. Each actor re-rolled collections, JSON parsing, time,
byte streams, and async glue. The differentiator kotoba should press is the
capability-confined, host-injected WASM premise from
`ADR-safe-capability-language.md`: the horizontal stdlib can be
**capability-parameterized** rather than direct-OS — exactly the shape deno /
rust / ts / python / go do *not* have natively.

## Decision

Adopt a **horizontal foundational stdlib layer** under the `kotoba-lang` org,
using the same zero-dep `.cljc` + host-injected-ports pattern as `dsl-core` /
`statechart` / `num`. Layer it so the vertical libs gain a shared foundation and
the capability-safe story gets a first-class user API:

```
Layer 4 — Tooling/UX : fmt · lsp · test(property)
Layer 3 — I/O        : fs · http · io · time  (capability-tokenized, host-injected)
Layer 2 — Cap/effect : wit (WIT bindings + capability tokens) · async (CSP channels, bounded)
Layer 1 — Data       : coll · spec · json   (pure, the foundation everything stands on)
Composite consumers  : scheduler (←async/time/coll) · store (←fs/io/wit/coll) · lint (←fmt/lsp/fs/coll)
```

### Catalog (shipped)

| Repo (`kotoba-lang/`) | Layer | Compares to | Maturity |
|---|---|---|---|
| `coll`   | data | Go slices/maps · deno.std/collections · clojure core | M6 |
| `spec`   | data | zod · pydantic · serde · clojure.spec/malli | M6 |
| `json`   | data | encoding/json · serde_json · std/json | M6 |
| `wit`    | cap/effect | wit-bindgen · Deno.* perms | M6 |
| `async`  | cap/effect | tokio · asyncio · core.async | M6 |
| `time`   | I/O | time · chrono · JS Temporal | M6 |
| `fs`     | I/O | os/path · deno.std/fs | M6 |
| `http`   | I/O | net/http · deno/http | M6 |
| `io`     | I/O | io · std::io · deno/streams | M6 |
| `test`   | tooling | proptest · deno.test · test.check | M6 |
| `fmt`    | tooling | rustfmt · dprint | M6 |
| `lsp`    | tooling | rust-analyzer · tsserver | M6 |
| `scheduler` | composite | durable tick scheduler / actor outer loop | M6 |
| `store`     | composite | capability-guarded KV store | M6 |
| `lint`      | composite | EDN source linter/formatter | M6 |

Design rules each lib follows:

- **Zero third-party runtime deps; every namespace `.cljc`.** Same contract as
  `dsl-core` / `num` core / `langchain`. Runs on JVM, SCI, ClojureScript,
  GraalVM, and kotoba-WASM.
- **Capability-parameterized, not direct-OS.** `fs` / `http` / `time` / `io`
  take a host-injected capability handle (same seam as `kotobase.store/IStore`
  and `num.protocol/IBackend`). They never touch the OS directly.
- **Plugs into the existing effect/capability boundary.** `wit` and `async`
  surface `effects.rs` / `policy.rs` (deny-by-default, per-cid, interprocedural
  effect gate from `ADR-safe-capability-language.md`) as a *user* API, not a new
  enforcement layer.
- **stdlib versioning is separate from profile-version.** `:kotoba.lang/profile-version`
  in `lang/profile.edn` is the *source contract* and is unchanged (stays 1). Each
  stdlib lib carries its own semver, currently `0.1.0` (see `stdlib-versioning.md`).

## Positioning: kotoba-lang vs Rust / Go / TS / Clojure

kotoba-lang does **not** compete with Rust/Go/TS/Clojure on "general-purpose
language stability / ecosystem breadth". It competes on an **orthogonal axis**:
capability-safe × WASM-confinement × AI-agent-native × content-addressed
substrate. The matrix:

| Axis | Rust | Go | TS | Clojure | kotoba-lang |
|---|---|---|---|---|---|
| Compile target | native/wasm | native/wasm | JS/wasm | JVM/JS | **WASM Component Model** |
| Capability/security model | ownership/borrow | — | — | — | **deny-by-default capability confinement + per-cid + effect gate** |
| AI-agent-native (untrusted code) | — | — | — | — | **`:ai-generated` profile: ephemeral, net/secret/persist denied by default** |
| WASM-isolated OS substrate | external(wasmtime) | — | — | — | **aiueos (capability Wasm OS, append-only audit)** |
| Content-addressed data substrate | — | — | — | datomic(external) | **Datom[CID/T] + Datalog + CACAO + IPFS + kotobase PDS** |
| Horizontal stdlib breadth | std small, eco huge | batteries | huge | medium(JVM) | 12 foundational + 3 composite (v0.1.0/M6) |
| Property testing | proptest | — | — | test.check | **test (consumes spec generator)** |
| Package registry | crates.io | go modules | npm/deno.land | clojars/maven | git-SHA dep + west manifest (`:packages` CID-lock track in progress) |
| LSP wire | rust-analyzer | gopls | tsserver | clojure-lsp | lsp data layer ✅, JSON-RPC wire not integrated |
| async runtime maturity | tokio(mature) | runtime(mature) | libuv(mature) | core.async | no runtime, pure state-machine (WASM premise, by design) |
| 1.0 stability | ✅ | ✅ | ✅ | ✅ | 0.1.0 (API settling) |

**Where kotoba leads (no peer):** capability-safe × WASM-confinement;
AI-agent-native untrusted-code execution; aiueos capability OS;
content-addressed data substrate; phonosemantic (kototama/kotodama).

**Where kotoba trails (engineering, not design):** package registry; 1.0
stability; async runtime maturity (intentionally host-driven); LSP wire
integration.

## Consequences

- **Vertical libs gained a foundation.** `langchain` now depends on
  `kotoba-lang/json` (the old "punt JSON to the host" escape hatch became an
  in-language default) — a real vertical lib consuming a foundational-stdlib
  lib. `langgraph` / `statechart` / `num` / `dag-cbor` stop re-rolling
  collections, JSON, byte streams, and async glue.
- **`spec` + `test` closed the property-testing gap.** `spec` generators feed
  `test` property cases, and `test`'s PRNG/generators/quickcheck compose with
  the existing conformance fixture runner.
- **`wit` + `async` maximize the capability differentiator.** The
  deny-by-default enforcement already existed in `effects.rs` / `policy.rs`;
  these libs turn it into the documented user-facing API — the one column where
  kotoba beats deno / rust / ts / python / go rather than trailing them.
- **M5 consumer provenance is universal.** Every consumable leaf has a
  confirmed external consumer: `json` ← http, langchain (real vertical); `spec`
  ← test; `async`/`time`/`coll` ← scheduler; `fs`/`io`/`wit` ← store;
  `fmt`/`lsp` ← lint.
- **Coverage/maturity scope expanded, not the profile.** This ADR added a
  `:stdlib` track to `docs/lang/coverage.edn` (now at `:m6`); it does **not**
  bump `:kotoba.lang/profile-version` or the core `:maturity :m6` of the profile
  itself.

## Maturity — reached

All 15 shipped libs are at **M6 / v0.1.0**:

- **M0**: ADR + catalog + repo plan.
- **M1**: machine-readable lib contract (protocol/`contract.edn` seam).
- **M2**: positive fixtures / examples.
- **M3**: negative fixtures (denied capability, malformed input).
- **M4**: manifest-driven runner / CI gate (`stdlib-gates.md`; each lib's
  GitHub Actions `clojure -M:test` on JDK 17+21 is green).
- **M5**: external consumer — confirmed for every consumable leaf (see above).
- **M6**: semver + compatibility policy (`stdlib-versioning.md`).

## Roadmap — engineering gaps to close (not design)

These are the axes where the matrix above shows kotoba trailing; tracked here so
they become follow-up work rather than drift:

1. **Package registry** — currently git-SHA dep + west manifest. A first-party
   registry/lockfile is the `:packages` CID-lock track
   (`package-rules.md` / `ADR-kotoba-package-cid-lock`, owner WIP); the
   `registry` stdlib lib is **deferred** there to avoid duplication.
2. **LSP wire** — `lsp` lib owns the data contract (positions/ranges/diagnostics);
   a JSON-RPC transport binding is the gap (currently host-supplied).
3. **async runtime** — intentionally a pure state machine (WASM premise: no
   threads, no wall-clock). The gap is durable-outer-loop reference drivers that
   thread `async` + `time` + `scheduler` for long-running cells, not a tokio.
4. **1.0** — all libs at `0.1.0`; cut `1.0` per lib once its surface is stable
   under real use (per `stdlib-versioning.md`).

## Out of scope

- Does **not** change `lang/profile.edn` or `:kotoba.lang/profile-version`.
- Does **not** invent new enforcement gates; `wit` / `async` reuse the existing
  `effects.rs` / `policy.rs` boundary.
- Does **not** build the `:packages` CID-lock track — that is owner-led; this
  ADR only defers `registry` to it.
