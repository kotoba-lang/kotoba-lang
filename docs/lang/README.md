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

## Source Contract

- Accepted extensions: `.kotoba`, `.clj`, `.cljc`, `.cljs`.
- Default reader target: `kotoba`.
- `:kotoba` branch fallback order: `:kotoba`, then `:clj`, then `:default`.
- Namespace resolution priority for target `kotoba`: `.kotoba`, `.cljc`, `.clj`,
  `.cljs`.
- Namespace resolution priority for target `clj`: `.cljc`, `.clj`, `.kotoba`,
  `.cljs`.
- Namespace resolution priority for target `cljs`: `.cljc`, `.cljs`, `.clj`,
  `.kotoba`.

This is source compatibility, not JVM Clojure or ClojureScript runtime
compatibility. Code still has to compile to the Kotoba compiler subset.

Inline expressions are also part of the compiler conformance vocabulary:
`kotoba -e '(+ 1 2)'` wraps the expression as an exported `main`, compiles it
through the same Kotoba -> core Wasm path, and runs `main`. This is
compile-and-run sugar, not runtime `eval`; the lower-level implementation
binary keeps a compatibility `-e` path only for crate-local testing and existing
integrations.

Capability-safe language tooling is exposed through `kotoba wasm`:

```sh
kotoba wasm build cell.kotoba
kotoba wasm build -S src cell.kotoba -o cell.wasm
kotoba wasm safe-policy cell.kotoba
kotoba wasm safe-build cell.kotoba --policy policy.edn -o cell.wasm
kotoba wasm selfhost-inspect cell.kotoba --policy policy.edn --json
```

Namespace source roots are supplied with `-S` / `--source-path` or
`KOTOBA_SOURCE_PATH`; `KOTOBA_CLJ_PATH` is retained only as a compatibility
alias.

Those commands use the compiler implementation crate underneath, but keep the
user-facing language surface under the `kotoba` command.

The machine-readable source contract lives at
`crates/kotoba-lang/resources/kotoba/lang/profile.edn`. Compiler conformance
fixtures live under `crates/kotoba-lang/resources/kotoba/lang/conformance/`.
Coverage and maturity tracking lives in `docs/lang/coverage.edn`; compatibility
rules live in `docs/lang/versioning.md`; CI-facing commands live in
`docs/lang/gates.md`.

The machine-readable CLI command contract lives at `lang/cli.edn`. It defines
the public `kotoba` command vocabulary for `run`, `check`, `db`, `git`, `rad`,
and `deploy` so host implementations can adapt to CLJC/EDN data instead of
owning the protocol surface.

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

