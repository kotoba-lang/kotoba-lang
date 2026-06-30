# Rust Migration Inventory

This inventory tracks Rust/Cargo dependencies under `kotoba-lang` and classifies
which ones must move toward Kotoba/CLJC, which can remain as native domain
backends, and which are legacy references.

Scan command, excluding `.git`, `target`, `node_modules`, `.cpcache`, and
`.shadow-cljs`:

```sh
find orgs/kotoba-lang \
  \( -path '*/.git/*' -o -path '*/target/*' -o -path '*/node_modules/*' \
     -o -path '*/.cpcache/*' -o -path '*/.shadow-cljs/*' \) -prune \
  -o \( -name Cargo.toml -o -name '*.rs' -o -name rust-toolchain \
        -o -name rust-toolchain.toml \) -print
```

## Summary

| repo | Cargo.toml | `.rs` | classification | decision |
|---|---:|---:|---|---|
| `kotoba` | 54 | 446 | primary migration | Move language, CLI, db, deploy, git, package semantics to Kotoba/CLJC. Keep Rust only as temporary host compatibility. |
| `kototama` | 1 | 3 | primary migration | Replace Rust wrapper with Kotoba/CLJC packaging and host adapters. |
| `aiueos` | 1 | 27 | primary migration | Move manifest, policy, broker, and safe checker semantics to Kotoba/CLJC. Native wasm execution may stay behind a host adapter while needed. |
| `kami-engine` | 109 | 536 | domain-native backend | Keep native engine pieces as backend/adapters. Move public contracts and authoring DSLs to Kotoba/CLJC. |
| `kami-engine-cfd` | 1 | 7 | domain-native backend | Keep as high-fidelity native CFD backend behind `aero`/CAE contracts. |
| `kami-webgpu` | 0 | 1 | cleanup candidate | Single Rust file without Cargo. Inspect and either remove, archive, or convert to CLJC/WebGPU contract docs. |
| `kotoba-v2025` | 69 | 333 | legacy keep | Old design/reference. Keep as-is; do not spend migration effort unless a specific artifact is promoted. |

## Migration Policy

Kotoba language and protocol semantics must not be authored in Rust as the source
of truth. Rust may remain as:

- a temporary host for existing CLI/server endpoints
- a native execution backend behind an explicit Kotoba/CLJC contract
- a legacy reference repo that is not on the active migration path

Authoritative definitions should live in Kotoba/CLJC repos such as
`kotoba-lang/kotoba-lang`, `kotoba-lang/transit`, and domain libraries under
`kotoba-lang/*`.

## Repo Notes

### `kotoba`

Current role: active Rust workspace for CLI, server, db, graph, git, rad, deploy,
runtime, and host adapters.

Target role:

- Kotoba/CLJC defines language, package, Transit, Datomic API, git/rad/deploy
  semantics.
- Rust CLI/server becomes a compatibility host until replaced.
- New protocol changes must land first in Kotoba/CLJC specs or libraries.

Immediate actions:

- Keep removing new Rust protocol crates when a CLJC authoritative repo exists.
- Add docs that mark each Rust crate as `host`, `compat`, `legacy`, or
  `migration-target`.
- Prefer generated adapters from Kotoba/CLJC contracts over handwritten Rust
  semantics.

### `kototama`

Current role: Rust wrapper around `kotoba-clj`, `kotoba-lang`, `kami-engine-clj`,
and optional `wasmtime`.

Target role:

- Replace with Kotoba/CLJC package metadata and host-specific launch adapters.
- Keep native `wasmtime` execution as a backend capability, not as language
  authority.

### `aiueos`

Current role: Rust MVP for manifests, capability graph, broker, safe checker,
audit log, and wasm runtime.

Target role:

- Move manifest schema, policy reasoning, broker protocol, audit model, and safe
  checker contracts into Kotoba/CLJC.
- Keep wasm execution/fuel/memory enforcement as a host backend where needed.

### `kami-engine`

Current role: large native workspace for engine, rendering, physics, simulation,
WebGPU/native execution, and domain-specific backends.

Target role:

- Keep performance-sensitive backends native.
- Move authoring, scene contracts, package contracts, and public DSL semantics to
  Kotoba/CLJC.
- Treat Rust crates as backend implementations behind stable Kotoba contracts.

### `kami-engine-cfd`

Current role: Rust D2Q9 lattice-Boltzmann CFD backend.

Target role:

- Keep as native high-fidelity backend.
- Expose through a Kotoba/CLJC CAE/aero contract.

### `kami-webgpu`

Current role: one `.rs` file without a Cargo manifest.

Target role:

- Inspect and delete if stale.
- If still useful, convert to a documented host adapter or CLJC/WebGPU contract.

### `kotoba-v2025`

Current role: old Rust design/reference workspace.

Target role:

- Keep as historical reference.
- Do not migrate by default.
- Promote individual artifacts only with an explicit new owner and CLJC target.

## Order of Work

1. `kotoba`: classify crates and prevent new Rust protocol authority.
2. `kototama`: replace wrapper semantics with Kotoba/CLJC package contracts.
3. `aiueos`: move policy/broker/audit/safe-checker semantics to Kotoba/CLJC.
4. `kami-webgpu`: clean up or reclassify the single Rust file.
5. `kami-engine` and `kami-engine-cfd`: keep native backends, define CLJC public
   contracts around them.
6. `kotoba-v2025`: leave untouched as legacy reference unless explicitly needed.

