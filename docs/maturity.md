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
| standard-library reference | searchable per-symbol API pages | machine inventory and repository READMEs | gap |
| command reference | generated, per-command CLI documentation | `lang/cli.edn` plus a workflow-oriented reference | partial |
| diagnostics | stable codes with searchable explanations | phase model; stable-code coverage incomplete | gap |
| versions and releases | docs bound to shipped versions | profile/package/release axes exist; profile 6 is not yet bound by the current release policy | blocked release gate |
| editor experience | installable LSP/editor guides | LSP substrate exists; end-user setup guide is absent | gap |
| discoverability | searchable, link-checked public site | generated landing page links the docs; full-text search is absent | partial |

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
| D5 release-bound docs | every public page names a shipped implementation/profile pair; CLI/API/diagnostics are generated and searchable | not complete |
| D6 external validation | task-based user tests, localization, accessibility and search telemetry drive revisions | not complete |

## Next maturity gates

1. Bind language profile 6 to a signed implementation release through
   `kotoba-lang/release`; do not change `lang/version-policy.edn` as prose-only
   bookkeeping.
2. Generate CLI pages from `lang/cli.edn` and symbol pages from stdlib manifests.
3. Publish a stable diagnostic-code registry with examples and explanations.
4. Add full-text search over the generated static documentation artifact.
5. Run external first-install, first-build, capability-policy, and error-recovery
   tasks; feed measured failures back into this roadmap.

Run `nbb scripts/check-docs.cljs` to verify the D3 contract.
