# Rust Migration Inventory (historical)

> **Superseded (2026-10-01)**: Rust is no longer permitted anywhere in the Kotoba toolchain, including adapter-owned repositories. The CLI is Kotoba on the nbb launcher / amu native route. This file is kept as a record of the migration; the policy below replaces the earlier "adapter-owned Rust" exception.

This inventory records the active `kotoba-lang` Rust/Cargo migration state.
`kotoba-v2025` is the explicit legacy exception and remains untouched as a
historical design/reference workspace.

Scan command:

```sh
find orgs/kotoba-lang \
  \( -path '*/.git/*' -o -path '*/target/*' -o -path '*/node_modules/*' \
     -o -path '*/.cpcache/*' -o -path '*/.shadow-cljs/*' \) -prune \
  -o \( -name Cargo.toml -o -name Cargo.lock -o -name '*.rs' \
        -o -name rust-toolchain -o -name rust-toolchain.toml \) -print
```

## Summary

| repo | Cargo/Rust count | status | decision |
|---|---:|---|---|
| `kotoba` | 0 | migrated | Rust workspace removed; CLI/server/db/git/rad/deploy authority is CLJ/CLJC/EDN. |
| `kototama` | 0 | migrated | Rust wrapper removed; CLJC authority remains. |
| `aiueos` | 0 | migrated | Rust runtime removed; CLJC/EDN contracts remain. |
| `kami-engine` | 0 | migrated | Rust workspace removed; CLJ/EDN/WIT/data assets remain. |
| `kami-engine-cfd` | 0 | migrated | Rust CFD runtime removed; CLJC CFD contract remains. |
| `kami-webgpu` | 0 | migrated | Rust fixture replaced by EDN fixture. |
| `kotoba-lang` | 0 | migrated | Source profile compatibility crate removed; CLJC/lang docs remain authoritative. |
| `kotodama-host` | 0 | migrated | Rust host scaffolds removed; TypeScript SDK remains. |
| `inference` | 0 | migrated | Rust inference runtime removed; CLJC contracts remain. |
| `kotodama-holochain` | pending | migration target | Remove Holochain Rust zome scaffolds or move them to adapter ownership. |
| `kotoba-v2025` | legacy | keep | Old design/reference. Do not migrate by default. |

## Policy

Kotoba language and protocol semantics must not be authored in Rust. Nothing that
builds, tests, runs or releases Kotoba may require a Rust toolchain, crate,
Cargo file or Rust host adapter. Host adapters consume `lang/cli.edn`
(`:host-adapter-targets [:amu-nbb :amu-native]`) and are Kotoba. The only Rust
that may be mentioned is a benchmark competitor in a comparison table or
historical text. `scripts/check-legacy-runtime-absence.bb` guards against
Cargo/Rust files returning. `kotoba-v2025` remains legacy reference material and
is not a dependency.
