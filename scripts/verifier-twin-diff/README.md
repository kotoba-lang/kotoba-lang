# kotoba.verifier guest twin: generation and differential test

`lang/compat/kotoba/verifier.kotoba` (+ `verifier/*.kotoba`) is the guest-route twin of osaho's
`kotoba/verifier.cljk`. Three things here keep it honest.

* `gen_ops.py <verifier.cljk> <out ops.kotoba> <ops_header.txt>` regenerates
  `verifier/ops.kotoba` (every operation-name table of the host verifier) FROM the host source, so a
  table edit on the host cannot drift silently. Run it after touching a table in `verifier.cljk`.
* `gen_corpus.cljs` compiles every example under `DIRS` (colon-separated) to runtime KIR with the
  host frontend and seals x86-64 / aarch64 native artifacts around it (`/private/tmp/corpus`).
* `differential.cljs` links the twin as a project, runs it on the KIR interpreter, and compares its
  verdict with the host `kotoba.verifier` on every corpus file plus N random structural mutants of it
  (`MUTANTS`, `SEED`, `ONLY`, `MODE=program|artifact|both`). Env: `KROOTS` (source roots, incl.
  `lang/compat`), `CORPUS`. Run with nbb and the compiler classpath.

The differential compares `verify-program-message` with the host `verify-program!`, and
`verify-artifact-structure-message` with the host `verify-artifact!` (a host refusal at the code
region -- "native instruction stream rejected" / "native export table rejected" -- is not a
structural verdict and counts as accepted). See the header of `verifier.kotoba` for what remains.
