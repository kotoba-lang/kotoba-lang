# kotoba-lang

Kotoba language design, source-profile contract, and conformance fixtures.

This repository is split from `kotoba-lang/kotoba` so the language surface can
be reviewed independently from the current compiler, runtime, server, and mesh
implementation.

## Scope

- Kotoba source profile: `.kotoba`, `.clj`, `.cljc`, `.cljs`.
- Reader target contract: `:kotoba`, `:clj`, `:cljs`.
- Namespace source resolution priority.
- Conformance fixture manifest.
- Safe capability language design notes.
- Repository-language design for `kotoba-git` and `kotoba-rad`.
- CLJC authority for the public `kotoba` CLI command contract.
- `kotoba-lab` research notebook contract and GitHub Pages prototype.

The language and CLI authority lives here. Host implementations consume this
repository as data:

- `lang/cli.edn` defines `run`, `check`, `db`, `git`, `rad`, and `deploy`.
- `lang/adapters.edn` defines adapter-owned CLI launchers and keeps native
  implementations outside the default language authority repo.
- `lang/lab.edn` defines the `kotoba-lab` notebook, cell, artifact, evidence,
  and capability vocabulary.
- `src/kotoba/cli.cljc` validates the contract, shapes argv as EDN, and returns
  host-neutral command results.
- Node, JVM, native, or other launchers are adapters. They should not define CLI
  protocol semantics independently.

## Current Status

`kotoba-git` and `kotoba-rad` still have host implementation work outside this
repository, but the command shape is now CLJC/EDN owned here. The
remaining migration work is to replace those host implementations with
Kotoba/CLJC adapters that consume `kotoba.cli/dispatch` results.

## Repository Layout

- `lang/cli.edn`: public CLI contract.
- `lang/adapters.edn`: host adapter registry.
- `lang/lab.edn`: `kotoba-lab` data contract.
- `src/kotoba/cli.cljc`: CLJC CLI authority.
- `test/kotoba/cli_test.cljc`: CLI authority tests.
- `docs/site/`: GitHub Pages prototype rendered from `docs/site/lab.kotoba`.
- `docs/lang/`: profile maturity, gates, and versioning.
- `docs/adr/`: extracted language and repository ADRs.

## Verify

```sh
clojure -M:test
bb scripts/check-cli-contract.bb lang/cli.edn
node scripts/check-lab-site.mjs
```

`clojure -M:test` is the primary CLI authority gate. This repository should not
contain `Cargo.toml`, `Cargo.lock`, `.rs`, or Rust toolchain files.
