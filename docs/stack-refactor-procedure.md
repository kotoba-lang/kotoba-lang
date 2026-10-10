# Whole-stack refactor procedure

Use [stack architecture](stack-architecture.md) and the root responsibility
legend to identify ownership before editing. This procedure coordinates
owners; it does not replace Q9, release or backend qualification contracts.

For a target/ABI or distributed-stack refactor, first read the
[target-neutral architecture direction](stack-architecture-target-neutral.ja.md).
Inventory neutral, target-profile and mixed contracts before moving them.
Wasm/WIT is a target boundary, not universal language semantics; target, host,
distribution and consistency are separate qualification axes. Preserve the
current wire/CID versions until an explicit compatible migration is defined.
The intended DAG is an intended contract graph, not evidence that existing
package dependencies or consensus guarantees have already changed.

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

## Implemented entrypoints and the next migration boundary

The ABI ownership split is implemented: `kotoba.core.execution` owns neutral
plans, abilities, decisions, approvals and the closed v2 identity/lease shapes;
`kotoba.abi.component` owns Component/WIT/WASI profiles and unchanged v1
mixed descriptors. `kotoba.abi.contract` preserves all 46 legacy exports.
The neutral namespace has no imports. See [consumer inventory](../lang/execution-profile-consumers.edn)
and [implementation report](stack-architecture-target-neutral-report.md).

For the next refactor, start from these explicit entrypoints. Keep v1 bytes,
CIDs and signatures stable. Require a verified versioned profile-binding block
before projecting v1 identities to v2; issue new identity CIDs/signatures and
reject mismatched artifact, Component or WIT bindings. The projection helpers
validate shape/equality, while hosts verify binding bytes and profile contracts.
V2 lease issuance and native/Script/EVM bindings remain explicit follow-up work;
do not turn on default v2 runtime admission through an import/pin update.

Inventory `deps.edn`, `nbb.edn`, aliases and generated locks separately. AMU's
`nbb.edn` is a stdlib bootstrap cache boundary; compiler owner pins belong in
`deps.edn` and the regenerated `deps-lock.edn`. Adding them to that bootstrap
starts the engine's bb/uberjar path on a cold cache and breaks JVM-free CI.
Use the existing owner lock/resolver instead of creating a second resolver.

## Target-neutral and distributed migration order

Apply these steps within the ten-step integration procedure above. The adopted
architecture direction does not authorize breaking existing wire contracts.

1. Inventory every public contract/export and its consumers. Classify neutral
   semantics, target profile, and mixed descriptors. Include execution identity,
   plan, approval, lease, authority event, semantic capability catalog, package
   lock and hashing/version domains. Record existing data that must stay readable.
2. Freeze the target-independent meaning first. Route source/package/capability
   contracts to core-contracts, language meaning to language/IR owners, VM
   transitions to Kototama, policy to grant/authority and app/data algebra to
   kotoba-protocol. Do not create duplicate semantics or one all-purpose core repo.
3. Define the complete new descriptor/profile contract and compatibility rules.
   Semantic interface identity is separate from WIT world, native ABI, Script
   bridge and EVM ABI. Bind profile version and artifact identity explicitly;
   preserve old wire keys/CIDs or introduce a versioned migration with refusal
   rules. Unknown profiles and unqualified imports fail closed.
4. Split neutral and target-profile entrypoints, retaining compatibility facades.
   Move the whole contracted component and all public exports before updating
   consumers. Measure the resulting transitive source closure: neutral entrypoints
   must not import engines, target adapters, socket/DB or consensus implementations.
5. Update AMU/backends and runtime host/provider/OS consumers in dependency order.
   Check lowering, ABI binding, independent artifact verification, admission,
   runtime linking, limits and receipts for each named target. A compiler target
   does not by itself certify Kototama VM conformance on that host.
6. Define distributed app/cell boundaries with existing protocol owners. Keep
   immutable code/rules, signed actions, mutable heads and discovery locations
   distinct; keep integrity validation separate from effectful coordination.
   Do not count proposed hata/mon/ito/nuno composition as shipped implementation.
7. Declare each operation's consistency domain. Local/causal history, ordered
   consensus and external settlement have distinct acceptance/finality rules.
   Shared assets, policy governance and shared heads cannot silently fall back
   from required consensus to DHT replication. State membership, quorum/finality,
   replay/fork handling, missing dependencies, revocation and recovery explicitly.
8. Bind effect intent, idempotency and receipt to the domain-specific execution /
   commit order; external effects and local commit are not implicitly atomic.
   Keep secrets and provider handles out of replicated descriptors; specify
   sealed-content and metadata policy. Distinguish execution fuel, chain gas and
   economic credits.
9. Qualify target × host × operation × consistency with real outputs/execution
   and negative paths: widened grants, wrong ABI/CID, unknown profile, replay,
   forks, expired authority, unavailable validation dependencies, missing providers
   and insufficient finality. Cross-target value/state equivalence is limited to
   specified numeric/encoding/metering contracts. Q9 remains whole-component and
   JVM-free; unsupported features block the component instead of reducing scope.
10. Refresh the four graphs, owner specs, composition routing, root legend/ADR,
    README, presentation source and generated output together. Merge normal PRs,
    verify ancestry and exact remote-main content, then advance only reachable
    forward west pins with owner tooling and reconcile the generated register.
    Report merged, pinned, deployed and qualified states separately. Retire only
    this task's clean, remotely preserved worktrees; save requested memory with
    canonical authorities and remaining migration/qualification boundaries.

## Review questions

- Is grant still a pure decision layer, with no OS/engine dependency?
- Does Kototama still define transitions rather than one concrete engine?
- Are compiler output and OS boot/runtime evidence being distinguished?
- Is a cached result substituting for an effect that must actually happen?
- Are persistence, placement and public entry points being mistaken for authority?
- Can the updated docs be traced to owner contracts and measured manifests?
