# ADR: Kotoba Cloud carries the admitted-computation boundary into operation

Status: accepted — 2026-08-29

## Context

Kotoba's public thesis is **“AI writes freely. Kotoba draws the boundary.”**
The language makes authority, effects, resources, target support, and artifact
identity part of admission rather than reconstructing intent after deployment.

`kotoba.cloud` is the operational entrance for Passkey identity and CLI
deploy discovery. Describing it only as “from words to execution” loses the
security-first distinction; describing the Cloud surface as the source of
safety would instead collapse language, verifier, host, and service authority.

## Decision

Public Cloud copy reuses the language thesis, then states the narrower product
role: Kotoba Cloud carries an admitted-computation boundary into identity,
storage, compute, and agent work without merging their authority.

- `kotoba-lang.org` owns language semantics, admission contracts, safety
  claims, documentation, and conformance.
- `kotoba.cloud` owns Passkey identity and CLI control/discovery contracts.
- `kotobase.net` owns durable storage, state, artifacts, and receipts.
- `murakumo.cloud` owns CPU/GPU placement and execution.
- `itonami.cloud` owns continuing agent work, goals, tools, and approvals.

The Cloud page must not imply that discovery delegates authority, that
`hostedApply` is available, or that the apex Worker replaces checked KIR,
artifact verification, provider qualification, host enforcement, key custody,
or operating-system isolation.

For hosted library publication, `kotoba.cloud` additionally enforces a
Principal-pinned ML-DSA-65 co-signature beside the Passkey session. The scope
must remain explicit: it protects that publication approval path and does not
make the authenticator's WebAuthn credential, TLS, IPNS, or every Kotoba
operation post-quantum.

The public entrance is localized from one structural view. Japanese is
canonical at `/`; English is canonical at `/en/`; reciprocal `hreflang`
links and a locale registry make additional languages explicit extensions.
Translations may adapt sentence structure, but normative product boundaries,
evidence status, and negative claims must remain equivalent.

## Consequences

The language and Cloud sites tell one security-first story while retaining
different responsibilities. Locale growth cannot silently fork product claims,
and operational convenience cannot be presented as proof that AI-written code
is trustworthy.
