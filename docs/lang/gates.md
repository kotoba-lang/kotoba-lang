# Kotoba Language Gates

The standalone artifact gate for this repository is:

```sh
test -f lang/profile.edn
test -f lang/package.edn
test -f lang/conformance/manifest.edn
test -f lang/package-conformance/manifest.edn
test -f examples/package-manifest.edn
test -f examples/kotoba.lock.edn
test -f docs/adr/ADR-kotoba-transit-wire-protocol.md
test -f docs/lang/package-rules.md
bb scripts/check-package-contract.bb
```

These commands are the maturity gate for `kotoba-lang` profile version 2, and
they are CLJ/EDN-first — no Rust toolchain is required or allowed in this
repository:

```sh
clojure -M:test
bb scripts/check-cli-contract.bb lang/cli.edn
bb scripts/check-package-contract.bb
bb scripts/check-capability-values.bb
bb scripts/check-legacy-runtime-absence.bb
```

The first command runs the CLJC test suites: the CLI contract conformance
tests (`test/kotoba/cli_test.cljc`), the package contract tests
(`test/kotoba/lang/package_contract_test.clj`), and the capability value
contract tests (`test/kotoba/lang/capability_values_test.clj`). The second
command validates the machine-readable CLI command contract. The third command
runs the package manifest/lock conformance fixtures from
`lang/package-conformance/`. The fourth command runs the capability value
conformance fixtures from `lang/capability-conformance/` through the same
pure CLJC logic (`src/kotoba/lang/capability_values.cljc`): capability shape,
effect-row consistency, CACAO grant / local policy intersection, and receipt
cases. The last two commands pin the lab site artifacts.

Implementation conformance against the language profile is owned by the
launcher and CLJC authority gates in `kotoba-lang/kotoba` (see its
`docs/lang/gates.md`): a conforming implementation consumes
`lang/conformance/manifest.edn`, runs all `:kind :run` source-file cases and
`:kind :compile-expr` inline-expression cases for the declared target set, and
produces the declared errors for all negative cases relevant to its admission
mode. CI additionally enforces that no Rust source or Cargo build files
reappear in this repository.

Package-safety maturity additionally requires `scripts/check-package-contract.bb`
to accept positive package manifest/lock fixtures and reject version-only,
unsigned, missing-CID, and over-capability negative fixtures from
`lang/package-conformance/`. The same gate also pins package boundary metadata:
known package kinds, adapter `:consumes` requirements, schema-contract
`:provides` requirements, and `app.kotoba.*` / `wire.kotoba.*` contract
surfaces in manifests and lockfiles.
