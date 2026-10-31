# Standalone run path (T6.1)

**Status:** accepted path selection (documentation)  
**Related:** [T6.3 tool vs runtime](../adr/ADR-reliability-t63-tool-vs-runtime.md),
fuel-model, T1.3 dual-backend pilot

Goal: run a **pure** Kotoba program **without** `kbb -M` on the production
host. The compiler remains a build-time CLJ tool (T6.3).

## Wasm path (pure apps): **wasm32-kotoba-v1 + wasmtime**

> KOTOBA-174 (2026-10-31): the Amu selfhost native target is the foundation and
> the default `kotoba compile` target (`lang/cli.edn` `:native`). This Wasm path
> is one explicitly selected route (`--target wasm`) for hosts where native
> cannot run. See the superseding note in ADR T6.1.

| Step | Who | Command / artifact |
|---|---|---|
| 1. Compile | CI / developer tool (CLJ OK) | `kotoba compile app.kotoba --target wasm -o app.wasm` or compiler `compile-source … :wasm32-kotoba-v1` |
| 2. Optional sign | CI | content-address + signature when claiming production |
| 3. Run | Production host | `wasmtime --invoke main app.wasm` (or engine with same WASI-free import surface) |

**Why choose it**

- Uses the optional `:wasm32-kotoba-v1` backend; the pure-product gate itself
  requires KIR + `:native-host` (KOTOBA-174)  
- No kexe loader / OS-specific native toolchain on the run host  
- Fuel is baked into the module global (default 512; T7.2)  
- Evidence: compiler README wasmtime examples; dual-backend pilot 13/13  

**Not required at run:** JVM, Clojure, nbb, compiler jar.

## Native path (default target): **kexe + kexe-loader**

| Step | Who | Command |
|---|---|---|
| 1. Compile | Tool | `kotoba compile app.kotoba -o app.kexe` (omitted `--target` = `native`, the host `<isa>-<os>`) |
| 2. Sign / verify | CI | `kotoba sign` / `verify` / `verify-signed` |
| 3. Run | Host | `kexe-loader` (or `kotoba run app.signed.kexe --trust …`) |

Use when freestanding / measured native identity is required (aiueos, offline
devices). Value surface is narrower than wasm component track (see
`compiler/docs/native-aot-baseline.md`). Since KOTOBA-174 (2026-10-31) native
(`:native-host`) is a required pure-product backend; its conformance evidence is
pending (no runner yet), so this states the requirement, not a measured pass.

## Explicit non-primary

| Path | Role |
|---|---|
| `kotoba -e '…'` / `kbb -M -m …` | Dev / tool only |
| KIR `kotoba.kir/execute` on JVM/cljs | Product **oracle** embed (murakumo); still a library host, not “standalone OS process without host lang” |
| Restricted ESM (`js-kotoba-v1`) | Browser/worker; needs JS engine |

## Acceptance for “standalone pure demo”

1. Pure `.kotoba` with empty effects  
2. CI produces wasm (or kexe) **without** shipping compiler to prod  
3. Run host invokes wasmtime (or kexe-loader) only  
4. Fuel exhaustion and admission failures fail closed  

## Follow-ups

- T1.4 pure-native-v1 pilot cases in conformance runner  
- T6.2 enforce precompiled artifacts in product CI templates  
- Public `kotoba run` adapter alignment with `lang/cli.edn` (T9.1)


**Update:** T1.4 pure-native pilot landed (compiler#419 / ADR 0168): `kbb -M:native-conformance`.
