# Agent rules

## Product introduction and performance evidence

Describe Kotoba as the language at the heart of a modern Lisp machine in
development: programs as data, composable forms and a system people can
understand are central to its Lisp heritage. Amu owns compilation, Kototama
the execution contract and AIUEOS the OS direction. Distinguish aspirations
such as live system modification from functionality established by code.

The 2026-10-15 Apple M4 Embench observation is 0.80× C execution time over
19 translated ports for one compiler snapshot and one sample per workload;
the measured path includes audited algorithm substitutions. It is not a
released-toolchain result, a certified score or a universal speed ranking.
Keep those limits beside the comparison and link the audit:
https://kotoba-lang.org/blog/embench-c-parity-20261015/.
Do not claim that all targets, the complete machine, or independent-user
release qualification are ready based on this observation.

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
- Kotoba and AIUEOS stay a Kotoba / Amu selfhost-only Lisp machine, language and
  compiler (owner decision 2026-11-07, AWAI-160). Apps and OS features are built
  first in `.cljc` as Otehon, which may use NixOS and other stacks, and are used
  later as an oracle for the selfhost implementation under the rules below. See
  network-awai-apex ADR 0026 D18. Otehon never enters a native route.
- Generated targets (owner decision 2026-11-07, JUN-949,
  `docs/adr/ADR-target-portable-canonical-form.md`): routes other than Amu are
  official when they pass the conformance corpus, and target toolchains may run
  generated output in a separate, non-required tier. That tier never feeds the
  compiler, its build or the required gates, and is never a fallback.
- `.kotoba` is headed to a pure form: IPLD-representable data, one spelling per
  meaning, and identity from the Definition CID rather than names. Selfhost
  admits `lam`, `app` and `ref` (amu#1854). Clojure-shaped `.kotoba` stays admitted
  until the migration is complete. That migration and the target stages are
  deferred for now (owner decision 2026-11-07, JUN-950).


## Local reference oracle (owner decision 2026-10-29, JUN-579 / JUN-588; C and Python added 2026-11-06)

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
- The oracle languages are nbb, JVM, C, Python, Basilisp, jank and Fennel (owner decision
  2026-11-06; Hy replaced by Basilisp 2026-11-07): the old reference implementations and
  prototypes in those languages. The same rules apply
  to every use, including a Workflow subagent running one on a developer machine: one-off,
  dated evidence, never in CI, gates, builds or runtime paths.
- Every language that supports `.cljc` is also an oracle language (owner decision 2026-11-07), for
  example Clojure, ClojureScript, ClojureCLR, Babashka, Squint, Cherry, ClojureDart, jank, Clojerl, Lokke
  and Phel. The unverified candidate table is `docs/oracle-cljc-dialects-20261107.md`; confirm the tool
  version, feature key and `.cljc` handling at first use and record it.
- CI exception (owner decision 2026-11-07; relaxes the "never in CI" and CDCI JVM/nbb wording above for
  oracle use only): JVM, nbb, Babashka and every other `.cljc` language above may run in CI as oracle jobs. Keep each in its own job, named
  and reported as an oracle. Native build, gates, packaging and runtime must not depend on it, and a
  native result must not silently fall back to it. Making an oracle job a required check is a separate
  owner decision; until then it is not required. The ban on JVM, nbb, Babashka and GraalVM in CI for any
  other purpose (JUN-290) is unchanged.
- Before a selfhost refactor of a cljk component, take its reference values first from the JVM and
  node (nbb) oracles (owner decision 2026-11-07). Hand an oracle the source as `.cljc`; Basilisp reads
  `.cljc` with the `:lpy` feature key and does not read the `.cljk` extension. Record versions,
  inputs and outputs with the date.
- CLJK stays as a reference oracle source and is treated as CLJC-equivalent for oracle runs (owner
  decision 2026-11-07, JUN-579): `#?(:kotoba …)` selects the Kotoba branch, `#?@` is not read, and an
  oracle is handed the source as `.cljc`. The route stays `.kotoba`; native input rules are unchanged
  (CLJC/CLJS/CLJ refused natively, CLJK sugar only through the existing entries) and native `.cljc`
  acceptance is not pursued. This replaces the earlier 2026-11-07 wording that made it the target.
  Where the code disagrees, the code is the state and the disagreement is reported.

## Shared agent rule

All agents follow this contract. Old inventories, receipts and ADRs record
past state; they do not prove current native qualification or grant permission
for a production cutover or stopping existing services. Preserve unrelated WIP.

## Site transition: retain the CLJK route (owner 2026-11-05, KOTOBA-160)

The owner explicitly requested resolving native site generation and publishing
JUN-851 while retaining the current CLJK route because native validation is
still insufficient. Preserve `site/generate.cljk`, its dependencies and its
explicit build entry for independent comparison and recovery during this
transition. A local, explicitly selected CLJK comparison build is permitted;
record its command, tool versions, pinned inputs and output hashes separately.
The native path must not invoke it or fall back to it, and CLJK success must
not be reported as native qualification. This scoped site instruction does
not restore JVM/NBB in CI or change other products' native-only contracts.

## Native site delivery boundary (KOTOBA-160, 2026-11-05)

The site's default build is the explicit macOS ARM64 native builder documented
in `site/native/BUILD.md`; source/data pins and negative controls are required.
The previous Workers Builds Node/NBB automatic build/deploy trigger is removed
for the native cutover. There is no qualified Linux CI replacement yet; the
local native build, route/DOM observer and refusal controls cover this release,
while automated cloud rebuilding and other native targets remain unqualified.
Keep the owner's explicitly retained local `site:build:cljk` route independent.
Neither generation nor publication may silently select it as a fallback.

## Linux site observation (KOTOBA-160, 2026-11-05)

The Linux workflow is a bounded native diagnostic: immutable static Amu ELF,
native execution/refusals and actual producer-source compilation. It neither
reinstates the retired hosted build nor claims a qualified native release
controller. Python only stages bytes and observes the native processes.
Full Linux rendering, the native controller, locale/catalog and byte parity
remain explicit qualification requirements before automatic release. There
is no production deploy step, npm install or CLJK fallback in this diagnostic;
the existing local CLJK build/source/dependencies must remain intact.
