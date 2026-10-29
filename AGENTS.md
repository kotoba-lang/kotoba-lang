# Agent rules

## Extension discovery

Discover published Kotoba skills and MCP definitions through
https://github.com/kotoba-lang/kotoba/tree/main/lib/kotoba.kotobaRegistry and its `index.json`/`llms.txt`.
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
- Final source is canonical `.kotoba`. CLJC/CLJS source and host adapters
  are migration inputs to port and retire. CLJK and EDN are retained as
  reader syntax sugar (owner decision 2026-10-29, JUN-579; supersedes the
  2026-10-22 "CLJK is not retained" wording): each sugar spelling must
  desugar to the same canonical Kotoba form and mint the same Definition CID
  as its pure `.kotoba` spelling. Equivalence is asserted by
  `lang/surface-equivalence-corpus.edn`, with a negative control per pair.
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


## Local reference oracle (owner decision 2026-10-29, JUN-579 / JUN-588)

The legacy cljk / JVM / nbb reference route (kotoba-sema, osaho, the nbb
`definition-cids`, `.cljk` test suites) may be run on a developer machine as a
one-off **measurement oracle**, to obtain reference values (KIR, Definition
CIDs, outputs) when porting a construct or checking cljk/edn-sugar vs pure
`.kotoba` equivalence.

- Record the exact command, tool versions, inputs and outputs as data
  (evidence, with the date); keep downloads minimal and say what was fetched.
- It never appears in CI, a gate script, a workflow, a build, packaging or a
  runtime path, and is never a fallback or a required comparison route. The
  CDCI ban on JVM/nbb is unchanged; a native result must not silently fall
  back to it.
- A reference value is evidence of the reference's behavior. It is not proof
  that a native implementation is correct, and a mismatch is reported, not
  forced to agree.

## Shared agent rule

All agents follow this contract. Old inventories, receipts and ADRs record
past state; they do not prove current native qualification or grant permission
for a production cutover or stopping existing services. Preserve unrelated WIP.
