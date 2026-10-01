# Agent rules

## The product does not depend on nbb, Node or the JVM

Decision 2026-10-01: nbb, Node, the JVM and GraalVM are bootstrap references
only. The end state is `kotoba`/`amu` built by Amu, running `check`, `refactor`,
`compile` and `kotoba ...` with no node/nbb/JVM process. Details and the
inventory tool live in `amu-measure`: `docs/selfhost-priority.md` (rules 8-11),
`scripts/selfhost-wall/bootstrap-boundary.sh`, stage S6 of
`docs/selfhost-core-rewrite-plan-20260930.md`.

- New code on the product path is Kotoba (`.cljk`/`.kotoba`) with a `:kotoba`
  reading that passes `amu check`. Never nbb-, Node- or JVM-only code. This
  includes `lang/compat` twins: a twin is a Kotoba reading, not a host shim.
- Host-only code (`node:*`, `js/*`, `java.*`, `:import`) lives behind
  `#?(:cljs ...)`/`#?(:clj ...)` in files flagged `;; bootstrap-tooling` in the
  first five lines, or under `scripts/`, `test/`, `bench/`.
- A PR or commit that adds an nbb/Node/JVM dependency to the product path is
  refused. `#?(:kotoba nil ...)` and named refusals are debt, not progress.
- 100% means: the `amu` binary built by Amu itself runs the full check,
  refactor and compile on its own sources with zero node/JVM/nbb processes,
  verified by `scripts/selfhost-wall/no-host-processes.sh -- <command>` in
  `amu-measure` (strace/dtruss execve trace; no tracer is a failure).

## No Rust

- The `kotoba` CLI and everything it needs is Kotoba (`.cljk`/`.kotoba`) running
  on the nbb launcher (bootstrap only) or the amu native route. No Rust binary, crate, Cargo
  file, cargo build, Rust host adapter or Rust parity target may be required to
  build, test, run or release anything. `lang/cli.edn` names
  `:host-adapter-targets [:amu-nbb :amu-native]`. Rust may appear only as a
  benchmark competitor or in historical text.

## Refactoring is AST-based by default

- Rule-shaped changes (a pattern at many sites, a rename, a module split, a
  dialect port) use `kotoba refactor` (`plan`, `apply`, `verify`), not sed or
  hand text edits. A hand edit is the exception; say why, and turn a repeated
  hand edit into a rule in `lang/refactor-rules/`.
- No Python or JVM on the CLI path; fail closed. Decision:
  `docs/adr/ADR-ast-based-refactoring-is-the-default.md`.

## Language semantics are target-independent

- wasm32 is one build target of the Amu native compiler, not the definition of
  Kotoba. Do not cap, narrow or defer a language or stdlib feature because
  wasm32 cannot lower it yet: specify the semantics against the KIR
  interpreter, and let each target lower the feature or refuse it by name
  (exit 65, recorded in `lang/surface-status.edn`).
- Keep a limit only if it bounds a resource or protects an invariant.
- Never relaxes the safety pipeline, fail-closed behaviour or the no-JVM rules.
  Decision: `docs/adr/ADR-language-semantics-are-target-independent.md`.

## Q9 source migration is whole-component and JVM-free

The machine authority is `lang/q9-migration.edn`; the accepted decision is
`docs/adr/ADR-q9-whole-component-build-migration.md`.

- Migrate a complete namespace/deployable component: every public export and
  its transitive Kotoba source closure. A predicate, decision core or
  caller-precomputed scalar shadow is not migration progress.
- Keep native mechanisms behind explicit capability/provider imports. Do not
  shrink the component to avoid a missing language or backend feature; mark
  that migration blocked.
- Every target must pass the verified native Kotoba CLI (`kotoba check`,
  `kotoba compile`, and package-level `kotoba rad build`) and Amu
  `check`/`compile` with `--jvm-free`.
- Q9 build, test, parity and soak gates must not require a JDK, Java process or
  Clojure CLI. Deny/trace `java`, `javac`, `clojure` and `clj`; unsupported
  routes and lock failures fail closed instead of falling back to the JVM.
- Run portable retained `.cljc` oracles through nbb/CLJS, native or Wasm, or
  use content-addressed golden vectors. JVM observations are diagnostic only.
- Existing JVM/Clojure compiler and test paths are compatibility surfaces;
  they cannot satisfy or weaken Q9 acceptance.

## Refactor with `kotoba refactor`, not by hand

- Mechanical source changes (porting a form shape, renaming, splitting a
  namespace, moving a definition, any edit of the same shape in more than one
  place) go through the AST-based CLI, never through hand text edits, sed,
  regex or ad-hoc scripts. Use `kotoba refactor` (`amu refactor` in
  `amu-measure`; same subcommands, same EDN, format `:kotoba.refactor/v1`).
  It runs on nbb/Kotoba only: no Python, no JVM (fail closed).
- The loop is `plan -> apply --check -> verify`:
  1. `kotoba refactor list-rules`, then `kotoba refactor plan <rule> <paths>`
     (dry run: actionable / human counts and a unified diff). `:human`
     findings are not applied; they are the hand work that remains.
  2. `kotoba refactor apply <rule> <paths> --check`, then `apply` without
     `--check`. Rules are offset-preserving: comments, whitespace and every
     untouched byte survive, and the host (JVM/nbb) arm is kept byte-for-byte
     as `:default` of a one-form `#?(:kotoba NEW :default OLD)`.
  3. `kotoba refactor verify`: the differential. It runs the project's test set
     before and after and compares per-test outcomes. Any test whose outcome
     changed, or any new refusal, blocks the change.
- Hand text edits are only for a change no rule covers. Then either write the
  rule (below) or record in the commit message why this change is one-off.
  If you made the same hand edit twice, it was a rule.
- To add a rule: implement a rule map `{:id :find}` in the refactor rule
  library (`scripts/selfhost-codemod/src` in `amu-measure` today, being folded into the
  `*_cli.cljk` nbb sources), where `:find` takes `{:src :nodes :opts}` and
  returns findings `{:status :auto|:human :reason :edits}`. Add a fixture and a
  test (host equivalence by evaluation where the rule claims it), register it
  so `list-rules` shows it, and give refusals a stable code. A rule that cannot
  prove host equivalence reports `:human`, not `:auto`.
- The commit message carries the evidence: the rule id, the `plan` counts
  (actionable / human / ported), and the `verify` result (tests compared,
  changed outcomes = 0). A refactor commit without them is incomplete.
- Differential discipline: never "fix" a test or loosen a check to make
  `verify` pass; compare before and after on the same test set and the same
  revision otherwise. Failures that existed before stay failures, and are
  listed, not hidden.
- Parallelising a large module: `kotoba refactor graph <path>` (dependency graph
  EDN, SCCs, dynamic vars) -> choose or generate a partition ->
  `kotoba refactor split <path> --partition p.edn --out dir` (byte-identical
  round trip, facade keeps the old namespace) -> one agent per module, each
  using the loop above on its own module -> `verify` on the recombined whole.
  Agents commit path-specific and never touch another module's files.
- Until the CLI subcommands land in your checkout, the same rules run from
  `scripts/selfhost-codemod/codemod.sh` and `scripts/selfhost-split/` in
  `amu-measure`; use
  them with this same loop and note it in the commit.
