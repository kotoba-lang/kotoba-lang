# Kotoba Language Gates

The standalone artifact gate for this repository is:

```sh
test -f lang/package.edn
test -f lang/package-conformance/manifest.edn
test -f examples/package-manifest.edn
test -f examples/kotoba.lock.edn
test -f docs/adr/ADR-kotoba-transit-wire-protocol.md
test -f docs/lang/package-rules.md
bb scripts/check-package-contract.bb
```

These commands are the maturity gate for `kotoba-lang` profile version 1:

```sh
cargo test -p kotoba-lang
cargo test -p kotoba-clj --test lang_profile_conformance
cargo run -p kotoba-cli -- -e '(+ 1 2)'
cargo test -p kotoba-cli --test public_cli
cargo test -p kotoba-cli wasm_cli_tests
cargo test -p kotoba-cli mesh::tests
cargo test -p kotoba-cli manifest_defaults_to_kotoba_extension_kind_with_clj_compat_host
cargo test -p kotoba-lattice manifest::tests
cargo run -p kotoba-cli -- wasm safe-policy examples/kotoba-shell-hello/src/policy.kotoba
cargo check -p kotoba-clj
```

The first command checks that the Rust profile constants, `profile.edn`, and
`coverage.edn` agree. The second command runs the manifest-driven conformance
suite from `crates/kotoba-lang/resources/kotoba/lang/conformance/` through the
implementation compiler crate. That suite covers source-file `:run` cases,
`:compile-expr` inline-expression cases, and declared negative/error cases. The
third command pins the user-facing `kotoba -e` path. The fourth command runs
public CLI integration tests for `kotoba -e`, `kotoba wasm build`,
`kotoba wasm safe-policy`, `kotoba wasm selfhost-inspect`, and
`kotoba wasm safe-build`, including checks that public output does not expose
the legacy admission-gate name and that `-S` namespace resolution prefers
canonical `.kotoba` sources over `.clj` compatibility files. The fifth command
pins the `kotoba wasm` argument surface across build, safe-build, safe-policy,
and selfhost-inspect, including default `reader-target=kotoba` and repeated
`-S` / `--source-path` handling. The sixth through eighth commands pin the
component and extension defaults: `kotoba component build` reports `.kotoba` as
canonical while keeping `.clj` / `.cljc` / `.cljs` as compatibility inputs,
extension artifacts default to `kotoba/library` with `clj/deps` as an explicit
compatibility host, and app manifests default omitted `:lang` to `:kotoba` with
legacy `:clojure` / `:clj` aliases preserved. The ninth command pins the
user-facing `kotoba wasm` safe-language tooling path. The final command catches
feature-shape compile drift in the compiler implementation crate.

CI should run these commands as the minimum language-profile gate. Broader
compiler-crate integration tests may run in a heavier implementation job.

Package-safety maturity additionally requires `scripts/check-package-contract.bb`
to accept positive package manifest/lock fixtures and reject version-only,
unsigned, missing-CID, and over-capability negative fixtures from
`lang/package-conformance/`. The same gate also pins package boundary metadata:
known package kinds, adapter `:consumes` requirements, schema-contract
`:provides` requirements, and `app.kotoba.*` / `wire.kotoba.*` contract
surfaces in manifests and lockfiles.
