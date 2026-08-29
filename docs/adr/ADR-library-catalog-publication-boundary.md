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

Passkey-hosted publication is available as a two-authority relay. The CLI signs
the namespace head locally, uploads the complete immutable closure to
Kotobase's digest-verifying ingress, and places only a bounded signed request in
the approval URL fragment. After an explicit click, kotoba.cloud verifies the
Passkey session and relays the signed mutable record. Kotobase independently
checks that the key named by the `k51...` name signed the record and enforces
monotonic sequence CAS. The private signing seed, storage token, and Passkey
cookie never cross those boundaries.

This first hosted slice returns an immediate publication receipt. Catalog
ingestion, revocation UI, publication history, and storage-token replacement by
a short-lived Passkey-scoped grant remain separate follow-ups and must not be
claimed as live.

Library comparisons must name the exact CID/ref, workload, target, host,
toolchain, samples, measurement time, result verification, receipt, and
residual limit. API coverage, target support, compile performance, runtime
performance and production qualification are separate axes.
