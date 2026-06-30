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
