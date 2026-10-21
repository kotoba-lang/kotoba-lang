# Agent rules

## Extension discovery

Discover published Kotoba skills and MCP definitions through
https://github.com/kotoba-lang/kotoba-registry and its `index.json`/`llms.txt`.
Read a pinned entry and verify its checksum before installation. The extension
catalog is separate from signed CID language package admission; runtime tools
and west pins retain their owning repository's authority. See
`docs/adr/ADR-kotoba-extension-registry-reference.md`.

## Q9 uses whole-component Kotoba / Amu selfhost only

The machine authority is `lang/q9-migration.edn`; the current owner decision
(2026-10-22) is `docs/adr/ADR-kotoba-amu-selfhost-only.md`. It supersedes the
hosted runtime permissions in the earlier whole-component Q9 contract.

- Migrate a complete namespace/deployable component: every public export and
  its transitive Kotoba source closure. A predicate, decision core or
  caller-precomputed scalar shadow is not migration progress.
- Keep native mechanisms behind explicit capability/provider imports. Do not
  shrink the component to avoid a missing language or backend feature; mark
  that migration blocked.
- Final source is canonical `.kotoba`. CLJK syntax sugar, CLJC/CLJS source
  and host adapters are migration inputs to port and retire.
- Every target uses the qualified Amu selfhost-built Kotoba and Amu CLI for
  source checks, project builds, packaging and execution. `--jvm-free` alone
  does not qualify the compiler or exclude Node/NBB/C.
- Build, test, parity and soak closures must not depend on NBB, KBB,
  Clojure, Java/JVM, GraalVM, Node, a C compiler/loader/runtime, or wrappers
  invoking them. Record actual process traces and compiler generations.
- Port oracle behavior to Kotoba/native or selfhost-produced Wasm, retaining
  content-addressed golden vectors and historical observations as evidence.
  Do not retain a hosted oracle as a required comparison route.
- Unsupported routes, missing native policy tooling and lock failures stay
  blocked. Do not replace missing component behavior with a smaller slice or
  silently fall back. CDCI has only the Amu selfhost and Kotoba native gates;
  both and their controller use loaderless `x86_64-linux-static` ELF.


## Shared agent rule

All agents follow this contract. Old inventories, receipts and ADRs record
past state; they do not prove current native qualification or grant permission
for a production cutover or stopping existing services. Preserve unrelated WIP.
