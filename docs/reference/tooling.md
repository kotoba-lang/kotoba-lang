# Kotoba tooling reference

The installed `kotoba` executable and its release artifacts are owned by
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba). The host-neutral
command vocabulary is machine-readable in [`lang/cli.edn`](../../lang/cli.edn).

## Common workflows

| Goal | Command |
|---|---|
| verify the installed self-host seed | `kotoba selfhost check --json` |
| compile and run an expression | `kotoba -e '(+ 1 2)'` |
| compile a module to Wasm | `kotoba compile app.kotoba --target wasm --output app.wasm --json` |
| compile a module for the web | `kotoba compile app.kotoba --target web --output app.mjs --json` |
| inspect required capability policy | `kotoba wasm safe-policy app.kotoba` |
| build with an explicit policy | `kotoba wasm safe-build app.kotoba --policy policy.edn -o app.wasm` |

Use `kotoba --help` and the implementation README for the exact commands
available in the installed release. The language contract may describe a
command shape before every adapter implements it; unsupported commands must be
reported explicitly.

## Packages

Package and lock semantics are owned by
[`kotoba-core-contracts/lang/package.edn`](https://github.com/kotoba-lang/kotoba-core-contracts/blob/main/lang/package.edn).
Human-facing rules are in [package rules](../lang/package-rules.md). Safe
execution requires content pins, repository authority, signer validation, and
explicit capability grants; name plus semver is insufficient.

## Standard library

The foundational libraries are separate repositories and carry independent
semver. Their inventory and exact pinned evidence are recorded in
[`docs/lang/coverage.edn`](../lang/coverage.edn) under `:stdlib`. Start with:

- data: `coll`, `spec`, `json`, `text`
- capability/effect: `wit`, `async`, `device`
- I/O contracts: `time`, `fs`, `http`, `io`
- tooling: `test`, `fmt`, `lsp`, `lsp-rpc`, `lint`, `lint-kotoba`

Repository-level `M6` means the declared conformance/versioning stages exist;
it does not mean 1.0 API stability, broad adoption, or production SLO evidence.

## Editors and diagnostics

`lsp` and `lsp-rpc` provide the protocol substrate, while `fmt` and `lint`
provide portable tooling libraries. End-user editor installation, searchable
per-symbol API pages, and a stable diagnostic-code index remain documentation
product gaps. Track them in [maturity](../maturity.md) rather than inferring
completion from repository existence.
