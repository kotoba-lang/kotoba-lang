# ADR: AST-based refactoring is the default; text edits are the exception

- Date: 2026-10-01
- Status: Accepted (owner decision, 2026-10-01).
- Machine authority for the command surface: `lang/cli.edn` (to gain a
  `:refactor` command, below); rule data: `lang/refactor-rules/`.
- Companion in the compiler repository: `kotoba-lang/amu` ADR 0357, which
  holds the full design (rule format, safety contract, error codes, migration
  of the Python analysis). This ADR records what `kotoba-lang/kotoba-lang`
  owns: the public contract, the rule data, and the delegation.

## Context

Selfhost walls (`amu check` on the Kotoba project route) are removed by
rewriting many call sites in the compiler sources and by cutting the 22k-line
`frontend.cljk` into modules. The tooling that proved this works lives as
scratch scripts in the compiler repository (`scripts/selfhost-split`,
`scripts/selfhost-codemod`; Python for analysis, nbb for verification).
Kotoba users porting their own code from `.cljc` to the Kotoba route face the
same class of change (destructuring lambdas, dynamic vars, `try/catch` around
capability calls, `#?(:kotoba ...)` twins), and the dual-runtime idiom
(`docs/` dual-runtime notes, `lang/compat/`) is exactly a rule-shaped
rewrite. Today none of that is reachable from the public `kotoba` CLI.

## Decision

1. **AST-based refactoring is the default way to change code at scale.** A
   rule-shaped change (a pattern applied to many sites, a rename across a
   namespace, a module split, a dialect port) is made with `kotoba refactor`.
   A hand text edit is the exception, for a change that is not a pattern. A
   hand edit that repeats becomes a rule.
2. **The public CLI gets `kotoba refactor`**, with the contract in
   `lang/cli.edn` and the same subcommands as `amu refactor`:

   ```
   kotoba refactor list-rules
   kotoba refactor plan  <rule> <paths...>             # dry run: counts, unified diff
   kotoba refactor apply <rule> <paths...> [--check]
   kotoba refactor graph <path>                        # dependency graph EDN
   kotoba refactor split <path> --partition p.edn --out <dir>
   kotoba refactor verify                              # differential, per-test outcomes
   ```

   Output is one EDN map with `:format :kotoba.refactor/v1`. Refusals are
   `:kotoba.cli-error/v1` with the stable `:refactor/*` codes listed in
   amu ADR 0357 and the existing exit mapping.
3. **`kotoba refactor` delegates to the `amu` implementation.** The `kotoba`
   CLI is a host adapter over this repository's contract (`lang/cli.edn`
   is the authority; `src/kotoba/cli.cljk`'s `required-commands` is validated
   1:1 against it by `test/kotoba/cli_test.cljc`, and
   `scripts/check-cli-contract.bb` checks the adapters). The command is added
   to the contract as `:refactor` with a `:subcommands` vector mirrored by an
   `--op` enum, the shape of `:graph` / `:git` / `:rad` / `:hinshitsu`, and
   to `required-commands`. The host adapter in the compiler repository
   (`bin/kotoba`) maps it to the same code as `amu refactor`; there is one
   engine and one rule loader, not two. The contract is `:tier :m1` until
   conformance fixtures (below) make it `:m2`.
4. **Rules are owned here as data.** The language-level rules (the ones
   that encode Kotoba-route idioms: destructuring lambda, `reject!`,
   dynamic-var, keyword callback, `let` flattening, `Form` loops, the
   `#?(:kotoba ...)` dual-runtime wrap) are the built-in rule set in
   `lang/refactor-rules/`, next to `lang/compat/`, `lang/guest-grammar.edn`
   and `lang/surface-status.edn`, so a rule changes in the same commit as the
   language fact it depends on. Compiler-internal rules stay in the
   compiler repository's rule directory; `--rules <dir>` loads either.

## Rule format and identity (summary; full text in amu ADR 0357)

A rule = `:name`, `:pattern` (structural, on the offset-preserving CST,
reader-conditional arms included), `:rewrite` (a Kotoba function returning
replacement spans), `:classify` (`:actionable` vs `:human`, the latter
reported with file, line and reason and never silently skipped), and `:guard`
(applicability). Rules are EDN plus Kotoba functions written in the
Kotoba-route subset.

Identity is content-addressed in line with `lang/code-identity.edn`: a rule's
CID is the CID of its EDN with named functions replaced by their definition
CIDs; the rule-set CID is the CID of the sorted rule CIDs. `plan` and `apply`
report them and a refactor's provenance records
`:refactor/rule-set-cid`, `:refactor/input-cid`, `:refactor/output-cid`.
A rule is never edited in place under the same CID.

## Safety contract (summary)

- `apply` always works from the plan it just computed (a pure function of
  rule CID and input bytes) and refuses with `:refactor/plan-stale` if it
  differs from a supplied plan CID.
- `apply` refuses on a dirty tree for the target paths and on input that does
  not parse or does not round-trip (`print(parse(s)) == s`); every output must
  parse again or nothing is written (all-or-nothing).
- Behaviour preservation is checked, not assumed: `verify` runs the project's
  test set (`kotoba test`, one instance per test) on baseline and result and
  compares per-test outcomes; a pass-to-fail test fails the command.
- Fail closed (`docs/selfhost-priority.md` rules in the compiler repository):
  no Python, JVM, GraalVM fallback. The first implementation runs on nbb and
  is labelled bootstrap reference wherever it is reported.
- Existing commands and host behaviour do not change.

## Relation to selfhost

The parser, engine and rules are Kotoba-route code. Once they pass `amu check`
on the project route the compiler builds its own refactoring tool, and
`kotoba refactor` in a selfhost-built binary needs no nbb. The tool's first
user is itself: the rules that remove selfhost walls are applied to the tool's
sources. Walls it hits are fixed in the language, not worked around.

## Migration plan

1. Add `:refactor` to `lang/cli.edn` and `required-commands`; `kotoba refactor`
   in `bin/kotoba` forwards to the amu implementation; the contract test
   (`test/kotoba/cli_test.cljc`) and `scripts/check-cli-contract.bb` stay green.
2. Move the codemod rules a-f from the compiler repository's
   `scripts/selfhost-codemod` into `lang/refactor-rules/` in the rule format.
3. Port the Python analysis (`cljparse`, `sourcegraph`, `analyze`, `extract`,
   `roundtrip`, `verify`, `consumers`) to `refactor graph|split|verify` on the
   shared lossless parser, gated on byte-equal graph/partition output and an
   equal per-test outcome table with the Python while both exist; then delete
   the Python.
4. Conformance fixtures under `lang/conformance/refactor/` (input, rule,
   expected plan, expected output, expected refusals), so `:m2` is reached
   and both CLIs are checked against the same data.
5. Rules and tool pass `amu check` on the project route.

## Making it the default (documentation)

`AGENTS.md` here gains a short section: rule-shaped changes use
`kotoba refactor` (plan, apply, verify); say why when a hand edit is used.
`docs/selfhost-priority.md` in the compiler repository carries the same rule.

## Consequences

- One more command in the public contract and one more required adapter
  command; host adapters that do not implement it fail the contract check
  until they delegate.
- Rule changes ship with language changes in one repository; the cost is that
  rule CIDs change when a guarded language fact changes, which is intended.
- Until migration step 5, the tool is bootstrap-reference and is labelled so.
