# Security: extend package contract conformance for safe execution blockers

Architecture review finding: `F-001`
Severity: Critical
Owner: language profile/package contract

## Problem

The language-level package contract is accepted, but the conformance suite must
make unsafe package references unambiguously non-conforming for safe execution.

## Risk

Implementation repos can drift if the language profile does not pin negative
fixtures for unsafe package shapes.

## Required work

- Add package-conformance negative fixtures for version-only dependencies.
- Add missing repo RID, manifest CID, tree CID, signer, and transitive-lock
  negative fixtures.
- Add over-capability dependency fixtures.
- Add stale/revoked signer fixture vocabulary, even if implementation initially
  marks it pending.

## Acceptance criteria

- `scripts/check-package-contract.bb` rejects each unsafe fixture.
- `docs/lang/package-rules.md` documents these blockers for authors.
- `kotoba-lang/security` risk `R-001` links to language conformance evidence.

## Local resolution evidence

- Added negative fixtures for missing repo RID, missing manifest CID, bad
  signature algorithm, revoked signer, and expired signer.
- Extended `scripts/check-package-contract.bb` with signer-status rejection
  vocabulary for revoked, expired, and compromised signers.
- Verified with `bb scripts/check-package-contract.bb`.

## References

- `kotoba-lang/security/docs/architecture-review-2026-07-01.md` finding `F-001`
- `docs/adr/ADR-kotoba-package-cid-lock.md`
