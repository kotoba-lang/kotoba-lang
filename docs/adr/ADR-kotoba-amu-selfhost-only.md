# Q9 and tooling use Kotoba / Amu selfhost only

Status: accepted owner decision, 2026-10-22.

Canonical authored source is `.kotoba`. Required compiler, CLI, input
resolution, build orchestration, packaging, validation and execution use Amu
selfhost-produced native artifacts. Remove CLJK/CLJC/CLJS, NBB, KBB,
Clojure, Java/JVM, GraalVM, Node and C compiler/loader/runtime dependencies
from the complete closure. Browser Wasm and JavaScript are generated targets,
not permission for a hosted compiler.

This supersedes KBB-first, the Q9 Node/NBB/Babashka exceptions, C-loader
pinning and retaining CLJK as final syntax sugar. Preserve whole-component
behavior, Definition CIDs, exports/imports/effects, authority, resource bounds
and named refusals while porting. A renamed extension or source-only check
does not qualify a component.

Unused legacy tools can be removed. Where necessary native functionality is
missing, record an implementation blocker and port it; do not use the old
runtime as an acceptance fallback. The old Babashka Q9 validators and fleet
receipt collector are retired. Their native successors are unqualified, so
the policy records blocked gates with no executable command. The unused
`kotoba.lang.q9-migration` host validator, its CLJ-shaped `.kotoba` copy and
the tests asserting the retired contract are removed too; repository callers
were limited to that old test. This is not native validator qualification.
Historical inventories and receipts remain evidence only.

CDCI covers Amu selfhost and Kotoba native only. Its controller and two gates
must be built by Amu selfhost and executed as loaderless
`x86_64-linux-static` ELF, with source/lock pins, compiler generations,
process traces, artifact hashes and reproducible verdict receipts. Old fleet
soak receipts and C-loader fixed points cannot qualify this route.

Track implementation in JUN-200 → JUN-202 → JUN-199 → JUN-162, and Kotoba
core/CLI migration in KOT-13 / JUN-198. This decision updates the contract;
it does not claim native qualification or authorize service shutdown,
branch-protection changes or a production cutover. Mithril Fund is outside
the current cleanup scope.
