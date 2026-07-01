# ADR - Kotoba package references, CID locks, and capability-safe dependencies

- **Status**: Accepted (contract; implementation pending)
- **Date**: 2026-06-30
- **Artifacts**: `lang/package.edn`, `examples/package-manifest.edn`, `examples/kotoba.lock.edn`
- **Related**: `ADR-kotoba-lang-foundational-stdlib.md`, `ADR-safe-capability-language.md`, `ADR-kotoba-rad-git-sovereign-repo.md`

## Context

Kotoba package distribution starts from plain Git repositories and the planned
`kotoba-lang/registry` tooling. The language already uses CID-addressed Git
objects, repo identity documents, capability policies, and reproducible Wasm
component builds.

CID pinning gives integrity, but it does not by itself answer authority:

- who is allowed to publish `kotoba-lang/json`?
- which signed repo or registry record mapped version `0.1.0` to this content?
- which capabilities can this dependency request?
- are transitive dependencies locked to the same content?

Safe Kotoba therefore needs package references to combine content addressing,
repository authority, signatures, and capability policy.

The same rule applies to library composition. Optional integrations must not be
hidden inside a vague `core` package split. A package should instead declare the
kind of boundary it represents: a base library, an adapter package, a schema
contract, a tool, or an executable component.

## Decision

Adopt `lang/package.edn` as the language-level package reference contract.

A conforming safe package reference is not just `name + version`. It must lock:

- package name and package version
- repository identity CID (`repo-rid`)
- signed package manifest CID
- source tree CID
- Git commit id, when the package is mirrored through Git
- publisher DID signatures
- granted capability set
- transitive dependency lock entries
- optional reproducible component CID

Name/version without repo RID, signature, and CID pins is non-conforming for
safe Kotoba execution.

Package boundaries are named by responsibility, not by centrality:

- `:library` packages expose pure code/data APIs and no implicit host
  capability.
- `:adapter` packages bind two or more packages or contracts explicitly, for
  example `kotoba-lang/slides-office` depending on `kotoba-lang/slides`,
  `kotoba-lang/office`, and `kotoba-lang/office-style`.
- `:schema-contract` packages publish Lexicon, EDN IR, WIT, or similar data
  contracts. Consumers depend on the locked contract CID rather than a runtime
  implementation when the dependency is only a data shape.
- `:tool` packages provide CLI/development tooling.
- `:component` packages provide executable or Wasm components.

This keeps optional integrations visible in the lockfile. A base library should
not silently import an optional adapter dependency; the caller selects the
adapter package or alias and locks it like any other dependency.

## Manifest Shape

Package manifests declare what a package is and what it may request:

```edn
{:kotoba.package/name "kotoba-lang/json"
 :kotoba.package/version "0.1.0"
 :kotoba.package/repo-rid "bafy...repo"
 :kotoba.package/source
 {:git-commit "git-oid..."
  :tree-cid "bafy...tree"
  :manifest-cid "bafy...manifest"}
 :kotoba.package/build
 {:deterministic true
  :profile-version 1
  :component-cid "bafy...component"}
 :kotoba.package/capabilities []
 :kotoba.package/dependencies []
 :kotoba.package/signatures
 [{:did "did:key:..."
   :alg :ed25519
   :sig "..."}]}
```

The manifest declares capability requests. It does not grant them. The caller's
lockfile and policy grant a subset.

Manifests may also declare contract surfaces that a package provides or
consumes. For example, a slides package family can be modeled as:

```edn
{:kotoba.package/name "kotoba-lang/slides"
 :kotoba.package/kind :library
 :kotoba.package/provides [:app.kotoba.slides.deck
                           :wire.kotoba.slides.deck.transit]}

{:kotoba.package/name "kotoba-lang/slides-office"
 :kotoba.package/kind :adapter
 :kotoba.package/consumes [:app.kotoba.slides.deck
                           :wire.kotoba.slides.deck.transit
                           :app.kotoba.office.graph
                           :app.kotoba.officeStyle.styleIr]
 :kotoba.package/provides [:app.kotoba.slides.officeImport]}

{:kotoba.package/name "app.kotoba.officeStyle.styleIr"
 :kotoba.package/kind :schema-contract
 :kotoba.package/provides [:app.kotoba.officeStyle.styleIr]}
```

The contract names are illustrative; the safety rule is that the schema or
Lexicon package carrying them is CID-locked and signed just like code.

## Lock Shape

Lockfiles are the execution input:

```edn
{:kotoba.lock/version 1
 :deps
 [{:dep/name "kotoba-lang/json"
   :dep/version "0.1.0"
   :dep/repo-rid "bafy...repo"
   :dep/ref "refs/tags/v0.1.0"
   :dep/commit "git-oid..."
   :dep/tree-cid "bafy...tree"
   :dep/manifest-cid "bafy...manifest"
   :dep/signers ["did:key:..."]
   :dep/capabilities []
   :dep/build {:deterministic true
               :component-cid "bafy...component"}}]}
```

The lockfile is the supply-chain counterpart to the safe-build policy. A
dependency receives no host capability unless both the dependency lock entry and
the caller policy grant it.

## Safety Rules

- **Content integrity**: source trees, manifests, registry records, and built
  components are addressed by CID.
- **Authority**: package authority comes from repo RID and signed records, not
  CID alone.
- **Deny by default**: dependencies get no graph, model, filesystem, network,
  clock, random, or secret capability by default.
- **Capability subset**: a lockfile grant must be a subset of the package's
  declared requests and the caller policy.
- **Transitive closure**: all transitive dependencies must be locked before
  execution.
- **Deterministic build**: a published component should include component CID
  evidence from a reproducible build.
- **Local path escape hatch**: local dependencies are development-only unless
  locked by tree CID before safe-build.
- **Schema contract pinning**: Lexicon, EDN IR, WIT, and other schema contracts
  used as dependency boundaries must be locked by manifest CID and tree CID.
- **Adapter explicitness**: optional integrations live in adapter packages or
  explicit aliases; base libraries do not silently import optional adapters.

## Consequences

- `kotoba-lang/registry` can be implemented as a name/version-to-manifest-CID
  index, but the registry is not the root of trust by itself.
- `kotoba-rad` repo identity and signed journal records can authorize which
  refs and tags are valid for a package.
- `kotoba wasm safe-build` can reject dependencies that are version-only,
  unsigned, not CID-pinned, or requesting ungranted capabilities.
- Existing stdlib package repos can start as plain Git repos, then become
  registry entries without changing the lockfile trust model.
- Library families such as `slides` can avoid a catch-all `core` artifact by
  publishing responsibility-named packages such as `slides`, `slides-office`,
  and `slides-svgraph`, each locked and signed independently.

## Maturity

- `M0`: this ADR.
- `M1`: `lang/package.edn` machine-readable contract.
- `M2`: positive package manifest and lockfile examples.
- `M3`: negative fixtures for missing CID, bad signature, bad repo RID, and
  excessive capability grant.
- `M4`: manifest-driven package contract runner.
- `M5`: `kotoba-lang/registry` or `kotoba-cli` consumes the same suite.
- `M6`: signing, revocation, and package compatibility policy.
