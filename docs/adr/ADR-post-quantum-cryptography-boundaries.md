# ADR — Post-quantum protection is attached to explicit operations

Status: accepted — 2026-08-29

## Context

“Kotoba is post-quantum” is too broad to be testable. Encryption, package
admission, WebAuthn, TLS, IPNS, and long-lived identity use different keys and
protocols. Migrating one does not migrate the others.

## Decision

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
  ML-DSA-65 signature over every security-relevant publication field. The
  first valid ML-DSA key is atomically pinned to that Principal and cannot be
  replaced by a later Passkey session.

The last item is application-layer co-approval. A platform Passkey continues
to use the COSE algorithm implemented by its authenticator. Kotoba Cloud
cannot remotely replace that authenticator key with ML-DSA. First enrollment
of the ML-DSA key therefore inherits the classical security of the Passkey
ceremony; after enrollment, compromising only that Passkey is insufficient to
replace the pinned ML-DSA key.

## Non-claims

These slices do not establish that WebAuthn authenticators, TLS connections,
legacy IPNS records, every stored object, every identity controller, or every
deployment is post-quantum. A future authenticator-native ML-DSA Passkey
requires interoperable WebAuthn/COSE support in authenticators, browsers, and
the relying party and will be qualified as a separate migration.

## Consequences

Receipts and machine profiles expose the exact suite and scope. Documentation
must say “hybrid-encrypted CLI object,” “hybrid package attestation,” or
“Passkey plus Principal-pinned ML-DSA publication approval,” rather than the
unbounded phrase “post-quantum Passkey” or “Kotoba is post-quantum.”
