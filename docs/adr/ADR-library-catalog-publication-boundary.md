# ADR — Library catalog and publication authority boundary

Status: accepted — 2026-08-29

## Decision

`kotoba-lang.org/libraries/` is the public library discovery and evidence
surface. It explains immutable definition and release CIDs, exact dependency
edges, GitHub provenance, target compatibility, and comparison methodology.
Its machine projection is `/.well-known/kotoba-libraries.json`, generated from
`lang/library-publication.edn` by the authoritative site generator.

Publication begins in Kotoba CLI. `kotoba library inspect` and
`kotoba library publish` are a human-facing projection over the existing
hash-native codebase, signed namespace heads, verified block ingress, and IPNS
publication. The first applied mode is local-operator signed IPNS. Publish is
dry-run by default.

Domain responsibilities remain separate:

- `kotoba-lang.org`: catalog contract, documentation, compatibility and
  comparison evidence;
- `kotoba.cloud`: Passkey identity, publication-control discovery, history and
  future deploy-readiness projection;
- `kotobase.net`: immutable CID blocks and receipts;
- GitHub: source and commit provenance, never content identity or namespace
  authority.

Names, versions and `latest`-style refs are discovery. Definition CIDs identify
definitions; a signed namespace-head CID identifies a selected release graph;
SourceCID, BuildCID and ArtifactCID remain distinct when present. A valid CID
does not grant publication, installation, capability use, or execution.

Passkey-hosted publication remains false until the hosted control plane binds a
verified Principal to namespace authorization, rate/quota policy, storage
admission, durable receipt, revocation, and catalog ingestion. Public copy and
machine profiles must retain that negative claim.

Library comparisons must name the exact CID/ref, workload, target, host,
toolchain, samples, measurement time, result verification, receipt, and
residual limit. API coverage, target support, compile performance, runtime
performance and production qualification are separate axes.
