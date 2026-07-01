# ADR — kotoba-lang foundational horizontal stdlib layer

- **Status**: Accepted (roadmap; per-lib maturity tracked in `docs/lang/coverage.edn` `:stdlib`)
- **Date**: 2026-06-30
- **Artifacts**: `docs/lang/coverage.edn` `:stdlib` track; planned repos
  `kotoba-lang/{coll,spec,json,wit,async,fs,http,io,time,test,fmt,lsp,registry}`
- **Related**: `ADR-kotoba-lang-profile.md`, `ADR-safe-capability-language.md`

## Context

`kotoba-lang` today ships a thick set of **vertical / domain DSL** libraries
(`langchain`, `langgraph`, `statechart`, `states`, `policy`, `mcp`, `num`,
`multiformats`, `dag-cbor`, `ed25519`, `cacao`, `dsl-core`, `sigma`, `dmn`,
`bpmn`, `cmmn`). They are all zero-dependency portable `.cljc`, designed to run
on JVM / SCI / ClojureScript / GraalVM / kotoba-WASM, with I/O host-injected.

What is **thin** is the **horizontal foundational stdlib** that those vertical
libs implicitly assume. Each actor re-rolls collections, JSON parsing, time,
byte streams, and async glue. Compared to the ecosystems kotoba competes with
for untrusted / AI-generated code, the gap is concrete:

| Axis | Go | Python | Rust | Deno/TS | kotoba (now) | Gap |
|---|---|---|---|---|---|---|
| Horizontal stdlib (coll/io/time/text/encode) | batteries | huge | std + core/alloc | deno.std | `dsl-core` problem maps only | **large** |
| Async / concurrency | goroutines | asyncio | tokio | promises | none (WASM premise excludes threads/clock) | **large** |
| Package / registry | go.mod | pypi | cargo | npm / deno.land | git-dep + west only | medium |
| Capability / effect system | — | — | — | perms | `effects.rs` + WIT | **strength** |
| Test / property | testing | pytest | proptest | deno.test | test-runner + conformance | medium (no property) |
| Spec / schema | — | pydantic | serde | zod | `dsl-core.problem` only | **large** |
| LSP / formatter / docs | gopls | ruff | rustfmt | dprint | clojure-side only | medium |

The differentiator kotoba should press is the last strength column: the
capability-confined, host-injected WASM premise from
`ADR-safe-capability-language.md` means the horizontal stdlib can be
**capability-parameterized** rather than direct-OS — exactly the shape deno /
rust / ts / python / go do *not* have natively.

## Decision

Adopt a **horizontal foundational stdlib layer** under the `kotoba-lang` org,
using the same zero-dep `.cljc` + host-injected-ports pattern as `dsl-core` /
`statechart` / `num`. Layer it so the vertical libs gain a shared foundation and
the capability-safe story gets a first-class user API:

```
Layer 4 — Tooling/UX : fmt · lsp · registry · test(property)
Layer 3 — I/O        : fs · http · io  (capability-tokenized, host-injected)
Layer 2 — Cap/effect : wit (WIT bindings + capability tokens) · async (CSP channels, bounded)
Layer 1 — Data       : coll · spec · json   (pure, the foundation everything stands on)
```

Catalog (priority reflects what unblocks the most existing vertical libs first):

| Repo (`kotoba-lang/`) | Layer | Compares to | Priority | Maturity |
|---|---|---|---|---|
| `coll`   | data | Go slices/maps · deno.std/collections · clojure core | P0 | M0 (planned) |
| `spec`   | data | zod · pydantic · serde · clojure.spec/malli | P0 | M0 (planned) |
| `json`   | data | encoding/json · serde_json · std/json | P0 | M0 (planned) |
| `wit`    | cap/effect | wit-bindgen · Deno.* perms | P0 | M0 (planned) |
| `async`  | cap/effect | tokio · asyncio · core.async | P0 | M0 (planned) |
| `time`   | I/O | time · chrono · JS Temporal | P1 | M0 (planned) |
| `fs`     | I/O | os/path · deno.std/fs | P1 | M0 (planned) |
| `http`   | I/O | net/http · deno/http | P1 | M0 (planned) |
| `io`     | I/O | io · std::io · deno/streams | P1 | M0 (planned) |
| `test`   | tooling | proptest · deno.test · test.check | P2 | M0 (planned) |
| `fmt`    | tooling | rustfmt · dprint | P2 | M0 (planned) |
| `lsp`    | tooling | rust-analyzer · tsserver | P2 | M0 (planned) |
| `registry` | tooling | crates.io · deno.land/x · go modules | P2 | M0 (planned) |

Design rules each lib must follow (so they stay kotoba-shaped):

- **Zero third-party runtime deps; every namespace `.cljc`.** Same contract as
  `dsl-core` / `num` core / `langchain`. Must run on JVM, SCI, ClojureScript,
  GraalVM, and kotoba-WASM.
- **Capability-parameterized, not direct-OS.** `fs` / `http` / `time` / `io`
  take a host-injected capability handle (the same injection seam as
  `kotobase.store/IStore` and `num.protocol/IBackend`). They never touch the OS
  directly. `wit` is the typed binding generator that mints those handles.
- **Plugs into the existing effect/capability boundary.** `wit` and `async`
  surface `effects.rs` / `policy.rs` (deny-by-default, per-cid, interprocedural
  effect gate from `ADR-safe-capability-language.md`) as a *user* API, not a new
  enforcement layer. No new gates invented here.
- **stdlib versioning is separate from profile-version.** `:kotoba.lang/profile-version`
  in `lang/profile.edn` is the *source contract* and is unchanged by this ADR
  (stays 1). Each stdlib lib carries its own crate/repo semver, like the
  existing `*-clj` siblings.
- **Repos are plain-git children under `kotoba-lang` org**, registered via west
  (`manifest/repos.edn` SSoT → `gen-west-manifest.bb`; never hand-written
  `west.yml`). Per the standing authorization, each lib's
  ADR → scaffold (`.cljc` + `deps.edn` + README + test) → `git init` + initial
  commit → `gh repo create kotoba-lang/<name>` (private default) + push → west
  registration → superproject `chore(manifest)+docs(adr)` commit is a single
  follow-up flow, run per lib.

## Consequences

- **Vertical libs get a foundation.** `langchain` / `langgraph` / `statechart` /
  `num` / `dag-cbor` stop re-rolling collections, JSON, byte streams, and async
  glue. `langchain`'s current "host parses JSON for me" escape hatch becomes a
  real `kotoba-lang/json` dep.
- **`spec` + `test` close the property-testing gap.** `spec` generators feed
  `test` property cases, giving kotoba the proptest/quickcheck surface the
  comparison table shows missing — and it composes with the existing conformance
  fixture runner (`lang/conformance/manifest.edn`).
- **`wit` + `async` maximize the capability differentiator.** The deny-by-default
  enforcement already exists in `effects.rs` / `policy.rs`; these libs turn it
  into the documented user-facing API, which is the one column where kotoba
  beats deno / rust / ts / python / go rather than trailing them.
- **New packaging/UX surface.** `registry` / `fmt` / `lsp` become the
  kotoba-native module resolution, formatting, and editing experience, reducing
  reliance on clojure-side tooling for `.kotoba` source.
- **Coverage/maturity scope expands, not the profile.** This ADR adds a
  `:stdlib` track to `docs/lang/coverage.edn`; it does **not** bump
  `:kotoba.lang/profile-version` or the core `:maturity :m6` of the profile
  itself. The profile (source contract) is complete; the stdlib is a separate
  roadmap tracked alongside it.

## Maturity

Per-lib maturity reuses the same M0–M6 semantics as the core profile, tracked
in `docs/lang/coverage.edn` under a new `:stdlib` key:

- **M0**: ADR + catalog + repo plan (this ADR is the track-level M0 evidence).
- **M1**: machine-readable contract for the lib (e.g. a `contract.edn` /
  protocol seam).
- **M2**: positive fixtures / examples.
- **M3**: negative fixtures (denied capability, malformed input).
- **M4**: manifest-driven runner / CI gate.
- **M5**: an external consumer (a vertical lib or `kotoba-cli`) depends on it.
- **M6**: semver + compatibility policy for that lib.

Current state: **track at `:m0`, every lib `:planned` / `:m0`.** No lib is
implemented yet; this ADR records the direction and the catalog so
`coverage.edn` now *covers* the horizontal surface as a tracked roadmap. Track
maturity rises as libs land; the first three (`coll`, `spec`, `json`) unblock
the most existing vertical libs and are the recommended first follow-up.

## Out of scope

- Does **not** change `lang/profile.edn` or `:kotoba.lang/profile-version`.
- Does **not** scaffold the 13 repos in this ADR; each is a separate follow-up
  under the standing authorization (ADR → scaffold → repo → west).
- Does **not** invent new enforcement gates; `wit` / `async` reuse the existing
  `effects.rs` / `policy.rs` boundary.
