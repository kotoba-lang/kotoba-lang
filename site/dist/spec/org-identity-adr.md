# ADR 2609141633: Organization identity, verifiable membership and delegation for Kotoba Cloud

**Status:** Accepted

## Context

Kotoba Cloud currently has no organization layer between the individual
Stable Principal and resources:

- Billing is per-principal (`billing_account.cljk` binds one Stripe customer
  to one principal). There is no shared paid account, no transfer of a paid
  account between people or entities, no team.
- Databases use tenants (`t_[a-z0-9]{12,48}`, `/v1/database/session/tenants`)
  whose ownership is asserted only by `auth.kotoba.cloud`, with no
  transferable or auditable ownership record.
- The existing delegation formats are in place but are not organized into a
  product surface: `org-biscuitsec` (the workspace's delegation centre,
  ADR-2608180200), `org-chainagnostic-cacao` (session authentication),
  and a first-party did:webvh root (`assets/well-known/did.jsonl`,
  did:webvh 1.0, SCID-pinned, witness threshold 3, portable).

Products in this space (Phala Cloud onboarding, Microsoft Entra Verified ID,
EBSI) show what the missing layer must answer: a stable organization name, a
transferable ownership of that name and its keys, role management with
revocation, and an auditable "who delegated what to whom".

Two reference designs inform this decision:

- **Microsoft Entra Verified ID** separates the roles Issuer / Holder /
  Verifier, keeps the DID document as the canonical home of keys and names
  only, and moves attestations ("this subject has property X") into signed
  Verifiable Credentials presented per verification.
- **EBSI** keeps that model and adds a trust registry (DIDR/TIR/TSR) as the
  verification anchor, split DID methods by actor type (legal entities get
  registered DIDs, natural persons use `did:key` for privacy), and requires a
  credential-status/revocation mechanism.

## Decision

Kotoba Cloud adopts a three-layer organization model. Each layer has exactly
one canonical source of truth.

### 1. Name and keys — did:webvh organization DID

An organization is a webvh DID rooted in the existing first-party webvh
trust system, addressed as a subdomain of kotoba.cloud:

```
did:webvh:<SCID>:com-<handle>.kotoba.cloud
```

- The DID document carries only `updateKeys`, controller (the owning
  principal DID), and `alsoKnownAs`. Membership, roles and billing state are
  never written into the DID document.
- Organization name transfer (sale, succession, operator intervention) is a
  webvh update: rotate `updateKeys`, witness the new log entry. The SCID does
  not change, so the name and every external reference (Stripe customer
  metadata, tenant owner display, published links) survive the transfer
  untouched.
- `portable: true` is retained so the namespace can later migrate witnesses
  or hosting without changing the identifier.
- Subdomain allocation under `*.kotoba.cloud` is operator policy (allocation,
  disputes, reserved names), not a registry feature.

### 2. Membership and roles — Verifiable Credentials with status

"Principal P is a member of organization O with role R" is a W3C VC:

- `issuer` = the organization's webvh DID, signed with the organization's
  current signing key.
- `subject` = the member's principal DID (stable principal, `did:key`-family).
- `type` = `KotobaOrgMembership/v1`; roles limited to `owner`, `admin`,
  `member`, `billing`.
- `credentialStatus` = a status-list mechanism (EBSI-style) so that removing
  a member, suspension and role change take effect at verification time
  without touching the DID document.
- A first-time organization issues the founder an owner membership VC
  self-issued by the organization (issuer = subject's controlling org).

Following Entra/EBSI, the DID document is never the role database; VCs are
portable signed attestations, and the verification anchor decides their
current validity.

### 3. Trust registry — first-party, static-hosted

Kotoba Cloud operates its own trust registry in the EBSI direction
(DIDR + TIR roles merged into one surface), hosted as static assets:

- `/.well-known/kotoba-org-registry.json` (and per-org entries): the current
  organization DID list, the `KotobaOrgMembership/v1` JSON schema, and
  revocation/status list endpoints.
- Verification of an org VC = resolve the org's webvh document (existing
  `assets/well-known` delivery), check the membership VC signature chain,
  check `credentialStatus`, and check the registry's current issuer entry.
  All four checks fail closed.
- The registry is a cacheable projection of webvh logs and status lists; the
  DID log itself remains the tamper-evident record.

### 4. Execution capability — Biscuit, attenuated from org membership

Fine-grained runtime authority (run a research job, write to tenant `t_xxx`)
stays with `org-biscuitsec`:

- The Biscuit authority block is signed by the organization's current signing
  key and carries org facts (`org`, role, scope).
- Members attenuate with their own keys (`append`) for a concrete task.
- The verification policy additionally requires a derived fact
  `[[valid-membership <member-did>]]`, which the verifier derives only from a
  currently valid membership VC (step 2 + step 3 checks). An expired,
  revoked, or transferred-away member's Biscuit tokens fail closed even if
  the token itself is intact.
- CACAO/SIWE remains the session-authentication layer and is unchanged.

### 5. Billing binds to the organization DID

The Stripe customer metadata records the org's webvh DID. The existing
per-principal billing account becomes the personal (org-less) fallback.
Checkout webhook approval adds credits to the org account; `/account`
surfaces org billing through the same panel structure already shipped.

## Roles by DID method (EBSI separation)

- Natural person principal: existing Stable Principal, self-certifying
  (`did:key`-family), not registered in the org registry.
- Organization: did:webvh, registered, witness-backed, transferable.

## Consequences

- Transfer of an organization: rotate webvh `updateKeys` (witnessed) → hand
  over the org signing key → issue new membership VCs. Old-key-issued VCs and
  Biscuit tokens fail closed at (a) signature chain, (b) credentialStatus,
  (c) registry issuer match. Name, SCID, and external references survive.
- Revocation is time-bounded by status-list refresh cadence; the registry
  publishes the cadence and verifiers may cache only within it.
- Operator trust remains concentrated in kotoba.cloud's webvh root and
  registry hosting; the design accepts this as consistent with the existing
  first-party control plane and keeps `portable: true` open for migration.
- The personal billing fallback and Stripe checkout/portal code paths are
  unchanged; only metadata gains the org binding.

## Implementation order

1. `/.well-known/kotoba-org-registry.json` + `KotobaOrgMembership/v1` JSON
   schema publication (static assets).
2. `POST /v1/org/create` (webvh issuance + owner membership VC) and
   `POST /v1/org/delegate` (Biscuit attenuation issuance).
3. `/account` organization panel: orgs, members, VC status, billing binding.
4. Database gateway tenant authorization consumes org membership VC instead
   of bare `auth.kotoba.cloud` tenant assertion.

## References

- Microsoft Entra Verified ID — Decentralized identifier overview
  (issuer/holder/verifier, did:web, DID doc as key/name canonical source).
- EBSI — W3C Verifiable Credentials and EBSI (trust registry, DID-method
  separation by actor type, credential status/revocation).
- ADR-2608180200 (org-biscuitsec delegation centre), ADR-2609021500
  (did:webvh root), ADR-2609071000 (Base Account sign-in).
