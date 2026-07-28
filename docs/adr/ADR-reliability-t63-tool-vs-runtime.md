# ADR: Reliability T6.3 — compiler is a CLJ tool; language runtime is not

- Status: Accepted
- Date: 2026-07-28
- WBS: T6.3

## Context

“Language reliability” is not “Clojure embedding reliability.” Product pure
oracles already ship precompiled KIR (murakumo pattern). Operators still need a
clear split: what may depend on the JVM/Clojure toolchain vs what runs in
production.

## Decision

| Layer | Role | Runtime dependency |
|---|---|---|
| **Compiler / frontend / emit** | Build-time **tool** | Clojure (JVM) or nbb tooling OK |
| **KIR interpreter (`kotoba.kir`)** | Product pure oracle runner | May load on JVM/cljs as a **library**, not as “write product logic in CLJ” |
| **wasm32-kotoba-v1 artifact** | Portable pure/guest binary | **No** Clojure required at run |
| **Native kexe / loader** | Standalone path (T6.1) | No Clojure required at run |
| **Provider kits / host** | Effects, IO, secrets | Host language (cljs/nbb/JVM) behind capabilities |

### Rules

1. **Product pure logic** is authored in `.kotoba` and proven on KIR + wasm (T1.3).  
2. **Production default** for pure oracles: precompiled KIR or wasm generated in CI;
   compiler is **not** on the production classpath (T6.2).  
3. **Compiler remains CLJ** as the build/analysis tool — no requirement to self-host
   the compiler before R3.  
4. **Runtime identity** for pure code is the artifact (KIR/wasm/kexe) + fuel/admission,
   not “whatever Clojure evaluated.”  
5. Ambient Clojure (`require`, interop, atoms) stays forbidden in guest source (T2.4).

### Non-goals (this ADR)

- Choosing wasmtime vs kexe as the single primary `kotoba run` path (T6.1)  
- Removing all CLJ host adapters from product repos  
- Claiming signed network providers ready (T8.3)

## Related

- T6.1 standalone run, T6.2 precompiled artifacts  
- T1.3/T1.5 dual-backend + golden digests  
- `docs/lang/fuel-model.md`, pure-product profile
