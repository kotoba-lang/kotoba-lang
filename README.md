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

The implementation still lives in `kotoba-lang/kotoba`:

- `kotoba-cli` exposes `kotoba -e` and `kotoba wasm ...`.
- `kotoba-clj` compiles the Kotoba/EDN subset to Wasm.
- `kotoba-git` stores byte-exact Git objects as Kotoba CID blocks plus Datom
  projections.
- `kotoba-rad` provides the sovereign repository identity and ref-authorization
  layer.

## Current Status

`kotoba-git` and `kotoba-rad` exist as Rust crates in `kotoba-lang/kotoba`.
The server also has Git smart-HTTP endpoints and rad delegate gating.

The public `kotoba` CLI currently does not expose top-level `kotoba git ...` or
`kotoba rad ...` subcommands. The expected CLI shape is documented in
`docs/adr/ADR-kotoba-rad-git-sovereign-repo.md` and still needs to be wired into
`kotoba-cli`.

## Repository Layout

- `crates/kotoba-lang/`: small Rust crate for the source-profile constants.
- `crates/kotoba-lang/resources/kotoba/lang/profile.edn`: machine-readable
  profile.
- `crates/kotoba-lang/resources/kotoba/lang/conformance/`: profile fixtures.
- `docs/lang/`: profile maturity, gates, and versioning.
- `docs/adr/`: extracted language and repository ADRs.

## Verify

```sh
cargo test
```

The standalone crate checks stable source-profile constants and artifact
presence. Full compiler, CLI, safe-mode, and mesh gates continue to run in
`kotoba-lang/kotoba`.
