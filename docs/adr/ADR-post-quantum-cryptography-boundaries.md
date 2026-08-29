# ADR — Post-quantum protection is attached to explicit operations

Status: accepted — 2026-08-29

## Context

“Kotoba is post-quantum” is too broad to be testable. Encryption, package
admission, WebAuthn, TLS, IPNS, and long-lived identity use different keys and
protocols. Migrating one does not migrate the others.

## Decision

Post-quantum cryptography is the admission floor for every new Kotoba
cryptographic boundary. It is not an opt-in profile and classical-only
downgrade is not a compatibility mode. A classical primitive may remain as one
half of a hybrid construction, but cannot satisfy a new boundary by itself.

Development-only legacy paths are not migration targets. They may remain
inspectable while being excluded from admission, publication, or execution.

Post-quantum claims name the protected operation and exact suite.

- New CLI-encrypted objects use
  `x25519+ml-kem-768+aes-256-gcm`. X25519 and ML-KEM-768 jointly derive the
  AES-256-GCM key. The full versioned header is authenticated; removing either
  KEM half, changing the suite, using another recipient, or modifying the
  ciphertext fails closed. There is no classical-only fallback.
- Package installation requires the content-addressed
  `ed25519+ml-dsa-65` publication attestation already pinned by catalog CID.
- Hosted library publication requires three gates: the namespace's Ed25519
  signature, a live Passkey session for the Stable Principal, and an
  ML-DSA-65 signature over every security-relevant publication field. Each
  approval also carries a single-use request ID, a short expiry, and the
  Principal's monotonic PQ key epoch. The first valid ML-DSA key is atomically
  pinned to that Principal; a replay, expired request, old epoch, mismatched
  key, or revoked state fails closed.
- Normal ML-DSA key rotation and revocation are public, authenticated
  operations. Rotation requires the current and next keys to sign identical
  transition bytes plus a live Passkey session; revocation requires the
  current key plus Passkey. The Principal-scoped Durable Object changes state
  atomically and rejects exact transition-ID replay.

The last item is application-layer co-approval. A platform Passkey continues
to use the COSE algorithm implemented by its authenticator. Kotoba Cloud
cannot remotely replace that authenticator key with ML-DSA. First enrollment
of the ML-DSA key therefore inherits the classical security of the Passkey
ceremony; after enrollment, compromising only that Passkey is insufficient to
replace the pinned ML-DSA key.

## Non-claims

The Kotoba default governs boundaries Kotoba admits; it does not reclassify
external WebAuthn authenticators, TLS connections, or protocols outside that
admission boundary. A future authenticator-native ML-DSA Passkey
requires interoperable WebAuthn/COSE support in authenticators, browsers, and
the relying party and will be qualified as a separate migration.

## Consequences

Receipts and machine profiles expose the exact suite and scope. “Kotoba is
post-quantum by default” means new Kotoba cryptographic boundaries are refused
without PQ material and downgrade rejection. More specific claims continue to
name “hybrid-encrypted CLI object,” “hybrid package attestation,” or “Passkey
plus Principal-pinned ML-DSA publication approval”; the phrase does not turn an
external platform Passkey into a post-quantum authenticator.

The machine-readable authority for the current classifications, evidence, and
named gaps is `security/cryptographic-boundaries.edn`. Its validator and the
Murakumo fleet gate reject an admitted managed boundary that lacks PQ material,
implementation evidence, negative tests, or downgrade rejection. The normal
Cloud PQ key lifecycle is admitted: authenticated rotation and revocation
endpoints, CLI approval flow, atomic state transition, and no-store receipt are
live. Recovery without the current key remains blocked until an independent
quorum exists. A scheduled recovery/rotation drill and a public transparency
witness are also not yet operational; the immediate HTTPS receipt is not
represented as independently witnessed evidence.
