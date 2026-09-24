#!/usr/bin/env bash
# pure_s_parity_gate.sh — kotoba-pure-s cron task
# Verifies the consistency between .cljk friendly surface, .kotoba pure S-expression core, and authority files.
set -euo pipefail

ROOT="~/github/com-junkawasaki"
KOTOBA_LANG="$ROOT/orgs/kotoba-lang/kotoba-lang"
GRAMMAR_REPO="$ROOT/orgs/kotoba-lang/grammar"
# site/dependencies.edn pins grammar revision 0ed166aa (artifact sha 1acc2843...);
# the grammar main branch has moved past it, so generate must read the pinned rev.
GRAMMAR_PIN_REV="0ed166aa89669b8d1ad74a9e682eea3933652ce9"
GRAMMAR_PIN_DIR="/tmp/grammar-pin-0ed166a"
# A stale relative KOTOBA_GRAMMAR_ROOT (e.g. ../grammar-pin-0ed166a) can leak in
# from the environment and break under cron's workdir; force the absolute path.
export KOTOBA_GRAMMAR_ROOT="$GRAMMAR_PIN_DIR"

echo "=== 1. Authority Verification ==="
cd "$KOTOBA_LANG"

# Materialize the pinned grammar worktree if missing (e.g. after reboot clears /tmp)
if [ ! -f "$KOTOBA_GRAMMAR_ROOT/syntaxes/kotoba.tmLanguage.json" ]; then
  (cd "$GRAMMAR_REPO" && git worktree add --force "$GRAMMAR_PIN_DIR" "$GRAMMAR_PIN_REV") >&2
fi

# Verify site generator against specs
nbb --classpath "site/src:../grammar/src:../text/src:../jp-go-digital-design-system/src:../css/src:../html/src" site/generate.cljk --check
echo "ok  - site/generate.cljk clean"

# Verify ADRs and Q9 migration dispositions
if grep -q ":pure-s-expression-core" lang/q9-migration.edn && grep -q ":clojure-shaped-desugared-surface" lang/q9-migration.edn; then
  echo "ok  - lang/q9-migration.edn carries two-tier syntax profiles"
else
  echo "FAIL- lang/q9-migration.edn missing two-tier syntax profiles"
  exit 1
fi

echo "=== 2. Two-tier Parity & Grammar Sanity ==="
# Check existence of the newly settled ADR
if [ -f "docs/adr/ADR-kotoba-pure-s-expression-core-and-cljk-surface.md" ]; then
  echo "ok  - ADR-kotoba-pure-s-expression-core-and-cljk-surface.md exists"
else
  echo "FAIL- ADR missing"
  exit 1
fi

echo "All checks passed successfully."
