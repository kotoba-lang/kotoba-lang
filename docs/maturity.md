# Kotoba documentation and language maturity

This assessment compares Kotoba with the documentation products provided by
Rust, Go, and Deno. It is a roadmap, not a claim that those ecosystems have the
same language goals.

Comparison baselines: [Rust documentation bookshelf](https://doc.rust-lang.org/stable/),
[Go documentation and specification index](https://go.dev/doc/), and
[Deno runtime documentation](https://docs.deno.com/runtime/).

## Comparison

| Capability | Rust / Go / Deno baseline | Kotoba now | Status |
|---|---|---|---|
| one stable documentation entrance | integrated documentation portals | [`docs/README.md`](README.md) plus the generated public landing page | implemented |
| first-run learning path | install, tutorial/tour, runnable examples | [getting started](getting-started.md) and checked examples | implemented, narrow |
| language reference | comprehensive syntax and semantic reference | reader map plus semantics SSoT, grammar, surface matrix | implemented, partial |
| executable specification | conformance suites and implementation tests | machine grammar plus positive, negative, type, capability, identity, and adversarial fixtures | strong bounded slice |
| standard-library reference | searchable per-symbol API pages | generated bounded-core symbol reference | implemented, bounded |
| command reference | generated, per-command CLI documentation | generated from `lang/cli.edn` | implemented |
| diagnostics | stable codes with searchable explanations | generated bounded contract registry; compiler/host coverage remains incomplete | implemented, bounded |
| versions and releases | docs bound to shipped versions | machine release binding blocks the public default because no implementation evidence binds profile 6 | correctly blocked |
| editor experience | installable LSP/editor guides | LSP substrate exists; end-user setup guide is absent | gap |
| discoverability | searchable, link-checked public site | generated landing page embeds a local reference search index | implemented, bounded corpus |

## Maturity axes

Do not collapse these into one score:

- **Contract maturity** records whether machine authorities, conformance, and
  compatibility policy exist. The current profile track calls this M6.
- **Documentation-product maturity** records whether a new user can learn,
  look up, troubleshoot, and select the correct released version.
- **Engineering readiness** is the measured repository/reliability model under
  [`docs/system-dynamics/`](system-dynamics/kotoba-lang-maturity.md).
- **Operational maturity** requires release history, production observations,
  incident response, and SLO evidence.
- **Ecosystem maturity** requires external users, packages, integrations,
  support history, and adoption evidence.

The contract M6 label is therefore not equivalent to “as mature as Rust, Go,
or Deno.”

## Documentation-product scale

| Stage | Exit condition | Current |
|---|---|---|
| D0 inventory | existing authorities and gaps are named | complete |
| D1 routes | learn/use/implement/evaluate each has one start page | complete |
| D2 reference | getting started, language, tooling, package, and stdlib routes exist | complete for bounded surface |
| D3 executable integrity | checked documents resolve links and authority/profile drift fails a gate | complete |
| D4 public portal | generated public page exposes the routes without restating the spec | implemented; deployment is a separate operational check |
| D5 release-bound docs | every public page names a shipped implementation/profile pair; CLI/API/diagnostics are generated and searchable | reference generation/search complete; release pair correctly blocked |
| D6 external validation | task-based user tests, localization, accessibility and search telemetry drive revisions | protocol and automation complete; external observations pending |

## Next maturity gates

1. Bind language profile 6 to a signed implementation release through
   `kotoba-lang/release`; do not change `lang/version-policy.edn` as prose-only
   bookkeeping.
2. Extend the bounded diagnostic registry as compiler and host codes become
   stable; never infer exhaustive coverage from the initial registry.
3. Run the four tasks in [`user-validation.edn`](user-validation.edn) with at
   least three external users; automated and agent-proxy results cannot close D6.
4. Add localization and privacy-preserving search/task telemetry only after its
   collection and retention policy is explicit.

Run `nbb scripts/generate-docs-reference.cljs --check` and
`nbb scripts/check-docs.cljs` to verify the generated-reference contract.
