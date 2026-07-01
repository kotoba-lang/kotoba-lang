# Rust Migration Inventory

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

Kotoba language and protocol semantics must not be authored in Rust as the source
of truth. Rust may remain only in an explicitly adapter-owned repository or in
`kotoba-v2025` as legacy reference material.
