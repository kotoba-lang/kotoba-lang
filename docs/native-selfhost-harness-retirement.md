# Hosted selfhost measurement drivers retired

Owner direction, 2026-10-22: canonical Kotoba and Amu selfhost are the only
compiler/build/packaging/runtime route. CLJK syntax sugar, KBB-first and
C-loader pinning are superseded. The Q9 and CDCI contracts remain in
`lang/q9-migration.edn` and `AGENTS.md`.

## Removed execution graph

The former manual source scan imported the differential helper, which ran
Node/NBB compiler and oracle processes and compiled `tools/kexe_loader.c`
with `cc`. The red probe ran that scan repeatedly, invoked Node/nbb for its
positive control and mutated a CLJK host reading. These are not current
selfhost acceptance paths. The KBB invocation guidance recently added to
the scan does not override the owner contract.

Retire callers before their dependency:

1. `scripts/prove-selfhost-native-red.cljk` (136 lines).
2. `scripts/measure-selfhost-distance.cljk` (489 lines).
3. `scripts/selfhost_native.cljk` (645 lines).

Repository reference inspection found no workflow, package alias or other
executable consumer beyond these three scripts. Remove the scan's stale
`cljk-origin.edn` entry. Update the old scoreboard description and compiler
grant comments; the grant's twelve capability entries are unchanged.
The compatibility stdio module's former scoreboard exception is historical,
not a retained host exception; its executable forms are unchanged.

## Full corpus and provenance preserved

All 1,270 retired source lines are preserved **byte for byte as text data**
under `docs/evidence/retired-selfhost-host-drivers-20261022/`, with `.cljk.txt`
suffixes and a SHA-256/Git-blob manifest. No source loader, script command,
workflow, import or product entry executes those text files. The pinned
original source commit is `c214f4f21bf46ea7333ae2856934f9c488820be3`.

The archive preserves every boundary value, generator and mutation assertion:

- All 13 i64, 16 string, 7 vector and 8 bytes boundaries, bool and option
  generation, xorshift32 seed/replay and parameter phase rules.
- Every export and both ISA obligations, unsupported-type/arity refusal,
  complete result codecs, counterexamples and non-success distinctions.
- Compile/verifier failures, boundary refusal versus function refusal,
  mismatch, could-not-run, skipped and not-drivable results.
- The full control/unlowerable/wrong-value red corpus, including the f32-min
  diagnostic, zero agreeing wrong-value cases, exact 0 -> 1 counterexample
  and unchanged counts for every other export.
- The historical host representation classifications, including the latest
  out-of-safe input test and Number rounding check. Those observations are
  not a required hosted oracle or a native pass.

`lang/selfhost-distance.edn` is unchanged, including its recorded source and
result evidence. Its SHA-256 remains
`93dda3d61913645ec3cf6b299cc11c820dd47c46c364debb42704a89f6513354`.
Preserving data does not qualify its old compiler or loader dependency.

## Native acceptance still open

Port and qualify the complete measurement/oracle/red behavior through the
owning canonical Kotoba compiler and runtime, retaining the complete corpus
and stronger authority/resource/cleanup/path/race obligations. Static source
admission, a small probe or a narrower generator is not completion. Historical
host comparison is provenance; it is not a new required gate.

The human has requested native-only builds and execution. Currently located
independent compiler/runner artifacts do not support that full route: old CLI
image paths are missing; inspected Linux selfhost evidence declares
`trustedCLoader=true` and contains raw bin/KX64 payloads. No forbidden compiler,
loader or hosted wrapper was used to obtain a passing replacement result.

No implementation, compiler, assembler, generator, provider or tests were
executed for this retirement. Only source/reference, exact-byte corpus and
grant/implementation-form preservation checks were performed. No workflow,
required check, protection, profile activation, service or production route
is changed. Native gate/controller qualification remains open.
