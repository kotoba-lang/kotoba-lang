# Whole-stack refactor procedure

Use [stack architecture](stack-architecture.md) and the root responsibility
legend to identify ownership before editing. This procedure coordinates
owners; it does not replace Q9, release or backend qualification contracts.

1. **Inventory and preserve.** Resolve canonical repo identities, redirects,
   remotes and west registration. Record dirty/staged/untracked files,
   branches, stashes and broken worktrees. Preserve unrelated WIP. Fetch the
   required remote main refs; create one focused branch/worktree per repo
   under `/Users/junkawasaki/github/wt/<agent>/<task>`. Put non-repository
   outputs under `/Users/junkawasaki/github/workspaces/<agent>/<task>`.
2. **Find existing authorities.** Query repository/concept indexes and symbol
   dependents before creating replacements. Read current owner contracts,
   root ADRs, manifests and qualification receipts. A missing checkout or
   empty index is not absence. Separate a permanent invariant from a dated
   backend limitation.
3. **Draw four graphs.** Record responsibility ownership; direct library
   dependencies with aliases separately; artifact producer→consumer edges;
   and runtime/service composition. Keep T0–T6, L0–L5, communication planes
   and app faces distinct. Never infer a source dependency from a runtime
   arrow. Read manifests with an EDN reader and record revision and scan count.
4. **Define the intended boundary.** State which owner/API moves, the complete
   public surface, capability/effect/budget contracts, code/state identities,
   trusted boundary, wire keys, ABI and qualification profiles. Preserve
   `:aiueos/*` wire vocabulary unless a versioned migration changes it. A
   permission-model change is semantic work, not a namespace rename.
5. **Measure the baseline.** Run the relevant positive and refusal checks on
   the unchanged base. Record failures and unsupported routes separately.
   Do not attribute a pre-existing failure to the patch or treat a skip as pass.
6. **Move in dependency order.** Update the owning contract and its complete
   component, then implementations/backends, runtime hosts/providers, OS and
   consumers. Land coupled capability wiring/parity together or record the
   required waiver. Q9 migrates whole components, never predicate shadows;
   unsupported compiler features block migration rather than shrink its scope.
7. **Verify real paths.** Use verified native Kotoba and Amu `--jvm-free` for
   Q9 acceptance; name every target explicitly. Trace/refuse forbidden JVM
   fallbacks. Verify checked IR, identities, exports, imports, effects, budgets,
   wire compatibility and target outputs, then execute generated artifacts.
   Test denial/resource limits and receipt integrity. Separate hosted, hybrid,
   C-free, QEMU and physical-machine evidence. Use the resource governor for
   heavy builds; do not run a broad west update on the shared workspace.
8. **Update every explanation.** Change owner specs, authority routing, root
   topology/legend, repo mirrors, README and presentation source together.
   Regenerate owned outputs; check Mermaid, links, locales and visual layout.
   Keep Lisp-machine direction separate from debugger/image/selfhost readiness.
9. **Land and propagate.** Review focused diffs, commit and push task branches,
   use approved server merges, and verify both ancestry and final remote-main
   content. Use single-entry owner tooling to advance reachable west pins;
   never hand-edit generated west.yml, fleet-db or ledgers. Reconcile from
   remote-main manifest before any requested checkout synchronization.
10. **Close with evidence.** Report local/committed/PR/merged/pinned/deployed/live
    separately, with exact revisions and gaps. Deploy only from integrated
    main with required build/dry-run/tests, then verify the public path. Retire
    task worktrees only after custody and clean-state verification. Update
    personal memory only when the user asks, through an extension note.

## Review questions

- Is grant still a pure decision layer, with no OS/engine dependency?
- Does Kototama still define transitions rather than one concrete engine?
- Are compiler output and OS boot/runtime evidence being distinguished?
- Is a cached result substituting for an effect that must actually happen?
- Are persistence, placement and public entry points being mistaken for authority?
- Can the updated docs be traced to owner contracts and measured manifests?
