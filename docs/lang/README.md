# Kotoba Language Profile

Kotoba source is a Kotoba/EDN subset with a capability-safe profile for
untrusted or AI-generated code. `.kotoba` is the canonical Kotoba-only source
extension; portable `.cljc` is for shared Clojure-family source where
Kotoba-specific behavior is selected with reader conditionals:

```clojure
#?(:kotoba (defn main [x] (+ x 10))
   :clj    (defn main [x] (+ x 1))
   :cljs   (defn main [x] (+ x 2)))
```

## Getting Started

**Product split:** **kotoba** = language (safe Kotoba → **WASM AOT emit**);
**kototama** = `.kotoba` WASM **runtime** (run the emitted guest). Not JVM
Clojure execution. JVM is only the bootstrap host for today's language CLI.
Canonical execute: `kototama` `run guest.wasm`.

```sh
# Language — AOT (package-lock mandatory)
kotoba wasm emit examples/hello.kotoba --package-lock kotoba.lock.edn -o hello.wasm
kotoba wasm safe-build examples/policy-demo.kotoba --policy policy.edn --package-lock kotoba.lock.edn -o policy-demo.wasm
# Runtime (canonical): kototama  run  hello.wasm
# Compat only:        kotoba wasm run hello.kotoba --package-lock …
```

Historical Rust-era names (`kotoba -e`, `wasm safe-policy`, `wasm selfhost-inspect`)
are not the live CLJ launcher surface. Use the commands above.

The examples in `examples/` are intentionally small. The authoritative
compatibility examples are the conformance fixtures under `lang/conformance/`.

## Source Contract

- Accepted extensions: `.kotoba`, `.cljc`, `.cljk`, `.clj`, `.cljs`.
- `.cljk` means CLJ Kotoba and selects the `:kotoba` reader/compiler target.
- `.cljc` is common source for `:clj`, `:cljs`, and `:kotoba`.
- Default reader target: `kotoba`.
- `:kotoba` branch fallback order: `:kotoba`, then `:clj`, then `:default`.
- Namespace resolution priority for target `kotoba`: `.kotoba`, `.cljc`,
  `.cljk`, `.clj`, `.cljs`.
- Namespace resolution priority for target `clj`: `.cljc`, `.clj`.
- Namespace resolution priority for target `cljs`: `.cljc`, `.cljs`.
- `.clj` and `.cljs` are single-target compatibility extensions (profile v3
  reinstates `.cljs`, previously retired in v2 — see
  `docs/lang/versioning.md`); each has its own reader-branch chain
  (`["clj" "default"]` / `["cljs" "default"]`) and neither carries
  `#?(:kotoba ...)` branches the way `.cljc` does.

`.clj` keeps Clojure semantics and `.cljs` keeps ClojureScript semantics.
`.kotoba` and `.cljk` use the Kotoba compiler subset; `.cljc` is the only
portable source surface shared by all three reader targets.

Capability-safe language tooling is exposed through `kotoba wasm`:

```sh
kotoba wasm emit cell.kotoba --package-lock lock.edn -o cell.wasm
kotoba wasm build cell.kotoba --package-lock lock.edn -o cell.wasm          # alias of emit
kotoba wasm safe-build cell.kotoba --policy policy.edn --package-lock lock.edn -o cell.wasm
kotoba wasm run cell.kotoba --package-lock lock.edn
```

Namespace source roots are supplied with `-S` / `--source-path` or
`KOTOBA_SOURCE_PATH`; `KOTOBA_CLJ_PATH` is retained only as a compatibility
alias.

Those commands are implemented by the CLJC authority and the launcher in
`kotoba-lang/kotoba`, but keep the user-facing language surface under the
`kotoba` command.

Dynamic authority is modeled as explicit capability values, not as ambient host
access or plain resource strings. The profile semantics are documented in
[`capability-values.md`](capability-values.md).

## Semantic Definition Identity

The C1 content-addressed-code contract lives at `lang/semantic-code.edn`.
Checked top-level definitions and canonical recursive groups may be lowered to canonical EDN IR
with alpha-normalized local binders and resolved dependency CIDs, then encoded
as canonical DAG-CBOR and identified by CIDv1. Human names, source paths,
formatting, comments, aliases, and top-level source order do not participate in
that semantic identity.

Source, definition, and artifact identities remain distinct. Definition CID is
content identity only: package admission, publisher signatures, CACAO,
capability intersection, local policy, and Wasm confinement remain mandatory.
The current launcher surface is:

```bash
kotoba check path/to/program.kotoba --kind semantic-code
```

The portable conformance inputs are under `lang/semantic-conformance/`. C1
currently admits terms and recursive groups and fails closed on unresolved
references or groups beyond its canonicalization bound. Namespace commits, Kotobase code-graph projection, and
CID-addressed execution are later phases of
`docs/adr/ADR-kotoba-content-addressed-codebase.md`.

The machine-readable source contract lives at `lang/profile.edn`. Compiler
conformance fixtures live under `lang/conformance/`.
Coverage and maturity tracking lives in `docs/lang/coverage.edn`; compatibility
rules live in `docs/lang/versioning.md`; CI-facing commands live in
`docs/lang/gates.md`.

The machine-readable CLI command contract lives at `lang/cli.edn`. It defines
the public `kotoba` command vocabulary for `run`, `check`, `db`, `git`, `rad`,
and `deploy` so host implementations can adapt to CLJC/EDN data instead of
owning the protocol surface.

## Package References

Package and registry work is tracked separately from the source profile. The
machine-readable package contract lives at `lang/package.edn`; example package
manifest and lockfile shapes live in `examples/package-manifest.edn` and
`examples/kotoba.lock.edn`.

Safe Kotoba package references are content-pinned and authority-checked:

- source trees, package manifests, registry records, and built components are
  pinned by CID;
- package authority comes from repo RID plus signed records, not from CID alone;
- dependencies receive no host capability unless the caller lockfile and policy
  grant it explicitly;
- name plus semver without repo RID, signatures, and CID pins is non-conforming
  for safe execution.

The decision is recorded in `docs/adr/ADR-kotoba-package-cid-lock.md`.
Human-facing authoring rules live in `docs/lang/package-rules.md`.
Executable package-contract fixtures live under `lang/package-conformance/`;
`scripts/check-package-contract.bb` accepts the positive manifest/lock fixtures
and rejects version-only, unsigned, missing-CID, and over-capability negative
fixtures.

## Wire Protocol

Kotoba-owned app/resource communication uses plain JSON by default:

- media type: `application/json`, optionally `Content-Encoding: gzip`
- authoritative implementation: `kotoba-lang/transit`
- in-memory and file authoring shape: EDN
- package/storage integrity: CID, signed manifests, and lockfiles
- external OpenAPI/GraphQL/XRPC/provider protocols: explicit adapters

The decision is recorded in `docs/adr/ADR-kotoba-json-wire-protocol.md`,
superseding `docs/adr/ADR-kotoba-transit-wire-protocol.md`. Package rules
require JSON wire contract surfaces for Kotoba-internal app APIs that cross a
host or network boundary.

## Capability Values

Safe Kotoba treats resource names and authority as separate concepts. Dynamic
resource access should pass scoped capability values rather than relying on a
string that becomes authority at the host boundary. The profile-level semantics
are documented in `docs/lang/capability-values.md`.

## Self-Hosting Track

The target is for Kotoba's language and admission semantics to move into Kotoba
itself. Current self-hosting evidence lives in the implementation workspace:
`kotoba-lang/kotoba:crates/kotoba-clj/selfhost/safe_analyzer.kotoba` implements
covered effect, minimal-policy, policy-check, and admission-check slices as a
safe Kotoba component. Public CLI gates exercise `selfhost-inspect`,
`safe-policy`, and `safe-build` on the covered slices.

This repository tracks that path under `docs/lang/coverage.edn` `:selfhost`.
Remaining work is explicit: package lock enforcement in safe-build, registry
signature verification, repo RID validation through kotoba-rad, capability
values in the host ABI, and broader compiler semantics self-hosting.

## Maturity

- `M0`: constants and docs.
- `M1`: machine-readable profile.
- `M2`: positive conformance fixtures.
- `M3`: negative conformance fixtures.
- `M4`: manifest-driven conformance runner.
- `M5`: external implementation can consume the same suite.
- `M6`: profile-version compatibility policy.

## Layering

- `kotoba-lang`: language profile, source contract, conformance vocabulary.
- `kotoba-cli`: public compiler surface: `kotoba -e` and `kotoba wasm ...`.
- `kotoba-clj`: compiler implementation crate and compatibility binary for the
  profile.
- `kotoba-runtime`: host/runtime for compiled components.
- `kotoba-datomic` and storage crates: data substrate.

The profile is kept in-repo for now. Split it into a separate repository only
when an independent compiler, runtime, or external conformance suite needs to
consume it outside this workspace.

## Foundational stdlib roadmap

The profile (source contract) is mature at `:m6`, and the **horizontal
foundational stdlib** that the vertical `*-clj` libs (`langchain`, `langgraph`,
`statechart`, `num`, …) assume is now complete: 12 foundational + 3 composite
consumer libraries, all at **v0.1.0** and **M6**, all PUBLIC and CI-green under
the `kotoba-lang` org, in the same zero-dep `.cljc` + host-injected pattern as
`dsl-core` / `statechart` / `num`:

- **Layer 1 — data**: `coll`, `spec`, `json` (P0)
- **Layer 2 — cap/effect**: `wit` (WIT bindings + capability tokens), `async`
  (CSP channels, bounded) (P0)
- **Layer 3 — I/O**: `time`, `fs`, `http`, `io` (capability-tokenized,
  host-injected) (P1)
- **Layer 4 — tooling**: `test` (property), `fmt`, `lsp` (P2)
- **Composite consumers**: `scheduler` (←async/time/coll), `store`
  (←fs/io/wit/coll), `lint` (←fmt/lsp/fs/coll)

Each lib is capability-parameterized (never direct-OS), plugs into the
existing `effects.rs` / `policy.rs` deny-by-default boundary, and carries its
own semver separate from `:kotoba.lang/profile-version` (the profile stays 1).
Per-lib M0–M6 maturity and the full catalog are tracked in
`docs/lang/coverage.edn` under `:stdlib` (track at `:m6`); the semver/compat
policy is in `docs/lang/stdlib-versioning.md`; the gate set is in
`docs/lang/stdlib-gates.md`; the decision and rationale (comparison vs Go /
Python / Rust / Deno / TS) are in
`docs/adr/ADR-kotoba-lang-foundational-stdlib.md`.

**M5 consumer provenance**: every consumable leaf lib has a confirmed
external consumer — `json` ← `http` and `langchain` (a real vertical), `spec`
← `test`, `async`/`time`/`coll` ← `scheduler`, `fs`/`io`/`wit` ← `store`,
`fmt`/`lsp` ← `lint`. `registry` is deferred to the `:packages` CID-lock
track.
