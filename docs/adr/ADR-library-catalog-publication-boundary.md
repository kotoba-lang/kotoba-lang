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
publication. Publish is dry-run by default. Applied publication builds a
separate immutable release root binding the exact namespace head to each
definition, raw Wasm artifact, compile receipt, and reproducibility input.

Domain responsibilities remain separate:

- `kotoba-lang.org`: catalog contract, documentation, compatibility and
  comparison evidence;
- `kotoba.cloud`: Passkey identity, publication-control discovery, history and
  future deploy-readiness projection;
- `kotobase.net`: immutable CID blocks and receipts;
- GitHub: source and commit provenance, never content identity or namespace
  authority.

Names, versions and `latest`-style refs are discovery. Definition CIDs identify
definitions; a library-release CID identifies the executable release graph;
SourceCID, BuildCID and ArtifactCID remain distinct when present. A valid CID
does not grant publication, installation, capability use, or execution.

Passkey-hosted publication is available as a three-gate relay. The CLI signs
the namespace head and release link locally, uploads the same complete immutable
closure to at least two distinct digest-verifying storage origins, and places only a bounded signed request in
the approval URL fragment. It also signs every security-relevant request field
with ML-DSA-65. After an explicit click, kotoba.cloud verifies the Passkey
session, verifies the ML-DSA signature, consumes a signed single-use request ID
within its short validity window, and atomically binds the first valid ML-DSA
key and monotonic key epoch to the Stable Principal. A replay, expired request,
old epoch, mismatched key, or revoked state fails closed. A later Passkey
session may not replace that pinned key. Kotobase independently
checks that the key named by the `k51...` name signed the record and enforces
monotonic sequence CAS. The Ed25519 seed, ML-DSA seed, storage token, and
Passkey cookie never cross those boundaries.

This makes hosted publication depend on a post-quantum application signature;
it does not change the COSE algorithm implemented by the platform
authenticator. First-use ML-DSA enrollment inherits the classical security of
the Passkey ceremony. After binding, compromising only that classical Passkey
cannot replace the post-quantum approval key.

Publication and distributed qualification are separate states. A successful
upload or Passkey approval remains `pending-availability`. `kotoba library
verify ipfs://<release-cid>` re-fetches every DAG-CBOR block and raw artifact
from every named storage origin, verifies each CID and exact byte sequence, and
asks delegated routing for distinct libp2p peer IDs. Only at least two
byte-complete storage origins and two distinct routed peer IDs produce a
`kotoba.library-availability.v1` proof CID. Gateway URLs are not counted as
peers; IPNI/DHT discovery is not treated as storage. `kotoba library run`
requires the same verification before executing a hash-addressed Wasm export.

This hosted slice returns an immediate publication receipt, not a distributed
availability claim. Catalog ingestion, authenticated rotation/revocation
endpoints and UI, independent recovery, public transparency, and storage-token
replacement by a short-lived Passkey-scoped grant remain separate follow-ups
and must not be claimed as live. The lifecycle state machine and bounded
transition history are implemented and tested; that does not make those public
operations available.

Library comparisons must name the exact CID/ref, workload, target, host,
toolchain, samples, measurement time, result verification, receipt, and
residual limit. API coverage, target support, compile performance, runtime
performance and production qualification are separate axes.
