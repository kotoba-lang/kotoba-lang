# ADR — Kotobase runtime and deployment ownership

- **Status**: Accepted; migration in progress
- **Date**: 2026-07-18
- **Deciders**: Kotoba/Kotobase and net-kotobase maintainers
- **Related**: `ADR-kotobase-security-access-control.md`

## Context

`kotoba-lang/kotobase-cljc-worker` and
`gftdcojp/net-kotobase/kotobase-cf-wasm` independently became deployable
Cloudflare Datom backends. They duplicate request parsing, CACAO verification,
crypto profiles, replay protection, head publication, storage bridging, and
XRPC dispatch. The former currently contains the stricter authority, policy,
keyring, audit, and replay implementation, while the latter is the backend of
the public `kotobase.net` product. Fixes in one therefore do not secure the
other, and the repository name does not communicate product ownership.

## Decision

Only `gftdcojp/net-kotobase` owns externally reachable Cloudflare Worker
deployments for Kotobase. This includes domains, routes, Wrangler manifests,
environment bindings, B2/R2 adapters, operational evidence, rollout, rollback,
quota, billing, and product policy.

Reusable implementation belongs to `kotoba-lang` repositories:

| Concern | Owner |
|---|---|
| Datom/query engine and row/write policy | `kotobase-peer` |
| Platform-neutral XRPC handlers and security/runtime orchestration | `kotobase-server` |
| CACAO minting and client transport | `kotobase-client` |
| Application persistence port and code graph | `kotobase` |
| Product edge, Cloudflare entry points, storage/head adapters | `gftdcojp/net-kotobase` |

`kotobase-cljc-worker` is a migration source and compatibility test fixture,
not a deployment owner. It accepts no new product features. Its reusable code
is extracted to the libraries above; its R2 adapter and domain-specific
migration are moved to `net-kotobase`. After consumers and data are migrated,
the repository is archived.

The dependency direction is:

```text
net-kotobase Cloudflare entry/adapters
  -> kotobase-server security/runtime + handler
  -> kotobase-peer engine/policy
  -> IPLD / Prolly Tree / chain
```

No `kotoba-lang` library may contain a production hostname, Cloudflare route,
bucket name, product tenant, billing/quota decision, or deploy secret binding.
No `net-kotobase` backend may maintain a second CACAO verifier, cryptographic
envelope format, policy engine, or audit-receipt verifier when the common
library provides one.

## Migration gates

1. Extract authority, crypto/keyring, audit, replay/idempotency, transport
   limits, and normalized `SecurityContext` orchestration into
   `kotobase-server` namespaces with platform ports.
2. Make `net-kotobase/kotobase-cf-wasm` compose those namespaces and retain
   only its B2/head/Cloudflare adapters.
3. Replace its legacy AES-GCM/one-seed profile and permissive read/auth paths
   with the common private/sealed profile and conformance suite.
4. Migrate the R2 graphs currently served by `kotobase.aozora.app` into a fresh
   net-kotobase-owned prefix; prove logical and audit parity.
5. Move the domain route only after rollback and recovery evidence passes.
6. Remove deploy configuration from `kotobase-cljc-worker`, then archive it.

Until gates 1–5 pass, the old route may remain live solely for compatibility,
but it is frozen and is not the target architecture. A source relocation or
route edit without data/security parity is not a completed consolidation.

## Consequences

- Public ownership and incident response become unambiguous.
- Security fixes have one implementation and one conformance suite.
- Object-store differences are explicit adapters rather than parallel DBs.
- The migration requires data movement and cannot be performed as a blind
  Cloudflare route change.
