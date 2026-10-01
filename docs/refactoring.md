# Refactoring with `kotoba refactor` (default workflow)

Decision recorded 2026-10-01. AST-based refactoring is the default way to change
Amu / Kotoba compiler sources. Hand text edits, sed and regex are the exception.
`amu refactor` in `amu-measure` has the same subcommands and output; its copy of this page is the longer one.

The CLI path is nbb/Kotoba only: no Python, no JVM. It fails closed
(`docs/selfhost-native-binary.md`). Existing commands are unchanged.

## Subcommands

All output is EDN with `:format :kotoba.refactor/v1`. Refusals carry a stable
`:code`, like the other CLIs.

| command | what |
|---|---|
| `kotoba refactor list-rules` | rule ids, one-line description, host effect |
| `kotoba refactor plan <rule> <paths>` | dry run: counts (actionable / human / ported) and a unified diff |
| `kotoba refactor apply <rule> <paths> [--check]` | `--check` verifies the edit applies and re-parses; without it, writes |
| `kotoba refactor graph <path>` | dependency graph EDN: nodes, weighted edges, SCCs, dynamic vars |
| `kotoba refactor split <path> --partition p.edn --out dir` | extract modules plus a facade; byte-identical round trip |
| `kotoba refactor verify` | differential: run the project's test set before/after, compare per-test outcomes |

## The loop

1. `list-rules`, then `plan <rule> <paths>`. Read the diff. `:human` findings
   are never applied; they are the remaining hand work.
2. `apply <rule> <paths> --check`, then `apply`.
3. `verify`. Zero changed outcomes and zero new refusals, or the change does not
   land.
4. Commit (path-specific `git add`). The message contains the rule id, the
   `plan` counts and the `verify` summary.

## Why a syntax tree

The parser is lossless with absolute offsets: comments, whitespace and every
untouched byte are preserved, so a rewrite is a list of text edits. Reader
conditionals are structure. A rewrite never replaces host text with Kotoba
text: it is host-equivalent code, or the host text stays byte-for-byte as the
`:default` arm of a one-form `#?(:kotoba NEW :default OLD)`. Edits are atomic per
finding; nested conflicts keep the innermost and re-run to a fixpoint.

## Differential discipline

- `verify` compares the same test set at the same revision, before and after.
- Never change a test or loosen a check to make `verify` pass.
- A failure that existed before stays a failure and is listed, not hidden.
- JVM/nbb behaviour must stay byte-identical; load the module under nbb after
  the change.

## Adding a rule

A rule is a map `{:id :find}`. `:find` receives `{:src :nodes :opts}` and returns
findings `{:status :auto|:human :reason :edits}`; `:edits` are
`{:s :e :text}` (a zero-width edit inserts).

1. Write the rule next to the existing ones (`amu-measure` `scripts/selfhost-codemod/src`
   until the sources move into the nbb CLI sources under
   `src/kotoba/compiler/nbb/`).
2. Add a fixture and a test; where the rule claims host equivalence, prove it by
   evaluation.
3. Register it so `list-rules` shows it; give each refusal a stable code.
4. A rule that cannot prove equivalence emits `:human`, never `:auto`.

If you are about to make the same hand edit a second time, write the rule. If
the change is truly one-off, say why in the commit message.

## Parallelising a large module

```
refactor graph  <path>                         # SCCs, cross-module edges, dynamic vars
refactor split  <path> --partition p.edn --out dir
# one agent per module: plan -> apply --check -> verify on that module
refactor verify                                # on the recombined whole
```

Each agent edits only its module and commits path-specific. Cycles reported by
`graph` are opened first, in the partition, not worked around.

## Status of the CLI

Until the subcommands exist in your checkout, the same rules and analysis run
from `amu-measure` `scripts/selfhost-codemod/codemod.sh` (rules a-f, nbb) and
`scripts/selfhost-split/` (Python analysis, nbb verification). Use the same loop
and say so in the commit message.
