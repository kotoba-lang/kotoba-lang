# ADR — Kotobase cryptographic trust and data-access control

- **Status**: Accepted, implementation in progress — `legacy-public` remains the only configured deployment mode; no production S4/S5 claim
- **Date**: 2026-07-18
- **Deciders**: Kotoba/Kotobase maintainers and deployment operators
- **Scope**: `kotobase`, `kotobase-client`, `kotobase-server`, `kotobase-peer`, deployable workers, storage adapters, and content-addressed code-graph admission
- **Related**: `ADR-kotoba-content-addressed-codebase.md`, `ADR-safe-capability-language.md`, `ADR-kotoba-package-cid-lock.md`

Production promotion evidence is defined by
`kotobase-cljc-worker/docs/security-production-readiness.md`.

## Implementation evidence (2026-07-18)

The redesign is now enforced in the shared libraries and CLJC worker, but
deployment promotion remains a separate operational decision:

| Boundary | Implemented evidence | Remaining condition |
|---|---|---|
| Request authentication | Worker-created `SecurityContext`; exact domain/audience/tenant/graph/operation; mandatory bounded expiry; trusted-root CACAO delegation-chain verification with monotonic attenuation; grant/DID revocation from a versioned `AUTHORITY_REGISTRY` snapshot | Production must provision, authenticate, monitor, and recover the registry binding before claiming S2 |
| Read/write policy | `kotobase-peer.policy` fail-closed public/private/sealed modes; policy-CID decisions; filtering before query/pull/history/count; complete-transaction entity/attribute/action/purpose/quota checks; protected policy/schema/authority attrs | Relationship predicates and tenant-specific policy distribution require a later policy schema version |
| Content integrity | Rehashed CID reads in `io-ipld`; verified commit traversal in `chain`; verifier-derived semantic code projections in `kotobase` | Codec/hash contract versioning for long-lived verification caches |
| Confidential storage | AES-256-GCM-SIV versioned envelopes; tenant/graph/block-kind/schema/key-id AAD; HMAC-SHA-256 blind indexes; HPKE-wrapped keyrings; `KEY_UNWRAPPER` KMS binding; active/retained/retired key tooling; offline plaintext-to-encrypted migration with logical parity check | Production must provision keys/KMS, run migration, document leakage/recovery, and prove restore/retirement drills before claiming S4 |
| Publication/replay | R2 ETag CAS plus atomic `If-None-Match: *` genesis; leased/recoverable idempotency records; credential-CID request binding that permits only exact retries | Adapters without equivalent conditional writes need an external coordinator |
| Audit | Encrypted DAG-CBOR allow/deny/error receipts, Ed25519 signatures, receipt CIDs, request/policy/key/head evidence, and independent CID/decrypt/signature verification | Production must provision an audit key, retain/replicate receipts, and operate external verification before claiming S5 |

Current automated evidence: worker `66 tests / 224 assertions`, server `39 / 194`,
peer/policy `121 / 280`, client `41 / 140`, Prolly Tree CLJ `7 / 2028` and
CLJS `10 / 2034`, IPLD `8 / 22`, chain `8 / 22`, and semantic code admission
`22 / 87`, all passing on 2026-07-18. These counts are evidence for
the checked revision, not a permanent security certification.

## Context

Kotobase combines Datomic-shaped datoms and queries with an IPLD substrate:
canonical DAG-CBOR blocks, content identifiers, Prolly Tree indexes, and an
immutable commit chain. That substrate provides stable identity and a basis for
tamper evidence, but it does not by itself provide confidentiality, authenticate
a tenant, authorize a query, or grant a capability.

The legacy implementation had useful seams but did not compose them into one
enforced security boundary:

- `IStore` deliberately has no principal, tenant, or policy parameters;
- block and semantic-code verification are injected by the host;
- CACAO verification in some worker paths proves only possession of the
  issuer's Ed25519 key and expiry of the self-signed envelope;
- audience, operation, graph resource, delegation, attenuation, and revocation
  are not uniformly enforced;
- read visibility is an application-level row post-filter whose protected-read
  capability is currently accepted as a resource string from that envelope;
- a missing or malformed in-graph read policy resolves to public access;
- write authority is graph-wide and has no entity/attribute/action policy;
- the production worker's explicit crypto profile is plaintext passthrough;
- ordinary block reads decode bytes without always recomputing the requested
  CID; and
- semantic code records accept caller-supplied dependency/effect projections
  separately from the verified block.

Consequently, a valid CID is sometimes treated as if verification occurred, and
a valid self-signature is sometimes treated as if authority were delegated.
Neither implication is sound:

```text
CID(bytes) = expected CID            => integrity of those bytes only
signature verifies under issuer key  => issuer authored the envelope only
```

Neither statement grants access to a graph, a row, an effect, a maintenance
operation, or a decryption key.

## Decision

Adopt a single deny-by-default security pipeline in which authentication,
authority derivation, policy evaluation, cryptographic storage, content
verification, and audit are separate mandatory gates. A deployment is secure
only when all gates are present; no inner library's portability seam may be
described as an enforcement boundary by itself.

```text
request
  -> transport limits and canonical parsing
  -> credential verification
  -> delegation / revocation validation
  -> effective capability derivation
  -> tenant + graph binding
  -> read/write policy decision
  -> verified block and transaction processing
  -> encrypted persistence + atomic head publication
  -> signed/content-addressed audit receipt
```

### 1. Security principal and request context

Every server operation receives a normalized, server-created `SecurityContext`:

```clojure
{:principal-did        "did:key:..."
 :audience             "did:web:kotobase.net"
 :tenant-id            "..."
 :graph-cid            "bafy..."
 :operation            :datom/read
 :effective-caps       #{...}
 :credential-cids      [#ipld/link "bafy..."]
 :policy-cid           #ipld/link "bafy..."
 :issued-at             ...
 :expires-at            ...
 :request-id            "..."}
```

Handlers never receive raw CACAO resource strings as authority. They receive
only effective capabilities produced by the verifier. Legacy string-only
`auth-did` arguments are non-conforming at a network boundary.

Local/embedded `IStore` remains a trusted in-process storage port. Networked or
multi-user use must wrap it in an authorization-aware service; conformance to
`IStore` says nothing about authorization.

### 2. Credential and capability verification

The server verifies every authenticated request against explicit expectations:

1. canonical envelope and supported signature/credential type;
2. issuer key resolution and signature;
3. exact audience, domain, version, and endpoint family;
4. finite and valid `iat`, optional `nbf`, mandatory short-lived `exp`, and
   bounded clock skew;
5. required operation capability;
6. exact tenant and graph resource binding;
7. the complete delegation chain to a locally trusted root;
8. monotonic attenuation at every hop;
9. revocation and key-rotation state; and
10. replay policy for non-idempotent operations.

Self-issued credentials may authenticate an actor for an actor-owned graph, but
they do not authorize arbitrary capabilities. A self-issued protected-read
resource is not a grant. Effective authority is:

```text
requested operation
  intersection delegated grants
  intersection graph policy
  intersection deployment policy
  intersection runtime/host policy
```

Every intersection is resource- and action-specific. Unknown capabilities,
unknown caveats, ambiguous graph bindings, invalid time fields, and unsupported
delegation forms fail closed.

### 3. Tenant and graph isolation

Graph identity is derived or resolved by the server, never trusted solely from a
request body.

- Actor-owned writes derive the graph from the authenticated principal and
  canonical database name.
- Shared or delegated graphs require an explicit graph capability issued by the
  graph authority.
- Reads must carry the same graph binding unless the graph policy explicitly
  declares an anonymous public surface.
- Maintenance operations such as `fold`, GC, pin/revoke, import, and head repair
  use distinct capabilities; ordinary write authority does not imply them.
- Storage keys include a deployment-controlled tenant/graph namespace even
  though blocks remain content-addressed within it. Cross-tenant deduplication
  is disabled for confidential plaintext unless the encryption construction
  explicitly makes it safe.

### 4. Read and row-level authorization

Read policy is versioned, content-addressed policy data selected by an authorized
policy head. It is not an unprotected ordinary datom whose accidental deletion
silently changes the graph from private to public.

Each graph declares an immutable security mode at creation:

- `:private`: missing, malformed, unavailable, or unverifiable policy means
  deny-all;
- `:public`: anonymous access is permitted only to policy-declared rows and
  operations; and
- `:sealed`: no plaintext server-side query is possible without an authorized
  decrypt/query capability.

Policy decisions may depend on principal, entity, attribute, action, purpose,
ownership, relationship, and time. Attribute-prefix rules remain a supported
optimization, not the complete authority model.

Visibility is applied before joins, predicates, aggregates, limits, materialized
views, pull traversal, and result counting. The same policy covers `datoms`,
`q`, `pull`, `history`, `asOf`, `since`, indexes, entity/ident resolution, log and
statistics endpoints. Metadata endpoints receive an explicit policy rather than
being omitted from the visibility set by default.

An authorization decision returns both `allow/deny` and a stable reason/policy
identity for audit. External errors do not disclose whether a denied row or
graph exists.

### 5. Write authorization

Writes are checked per datom before commit. Effective write authority includes:

```clojure
{:graph       "bafy..."
 :actions     #{:assert :retract}
 :entities    <selector>
 :attributes  <selector>
 :constraints <schema/predicate checks>
 :quota       {...}}
```

The policy protects its own policy head, ownership attributes, schema, namespace
heads, retention pins, and authority records from ordinary data writes. Changes
to those objects require dedicated capabilities and produce audit receipts.

Transaction admission validates the complete transaction before writing any
block. A policy denial, malformed datom, quota violation, or failed CAS publishes
no new head. Orphaned immutable blocks may remain after a lost head CAS but are
never reachable as committed state.

### 6. Content integrity and semantic admission

All bytes fetched by CID across a trust boundary use one primitive:

```clojure
(get-verified-block store expected-cid)
```

It rejects missing bytes, malformed canonical encoding, unsupported multicodecs,
and a recomputed-CID mismatch before decoding data for consumers. Cache entries
record that verification and are invalidated by codec/hash-contract version.
Commit traversal verifies every visited commit's CID and required sequence/parent
invariants. Full historical verification may be cached, but the current head and
newly fetched path are always verified.

Semantic code admission does not accept authoritative query projections from an
untrusted caller. The verifier returns a normalized admitted object derived from
the verified block:

```clojure
(admit-definition expected-cid block)
;; => {:verified-block ... :dependencies ... :effects ... :type-cid ...}
```

Dependencies, effects, visibility, artifact derivation, namespace bindings, and
receipt fields are either inside the hashed block or deterministically derived
from it. A boolean `verify` plus caller-supplied projections is not conforming at
an untrusted admission boundary.

### 7. Encryption at rest

Confidential deployments use an authenticated encryption profile. Plaintext
passthrough is permitted only for tests, local public data, and an explicitly
labelled development profile; startup fails if it is selected for a private or
sealed graph.

The profile provides:

- per-tenant or per-graph versioned data-encryption keys;
- AEAD for novelty payloads, index values, policy data, receipts containing
  sensitive links, and sealed blocks;
- keyed, domain-separated blind-index tokens for searchable index components;
- associated data binding ciphertext to tenant, graph, block kind, schema
  version, and key version;
- envelope encryption through KMS or HPKE-wrapped DEKs;
- rotation, revocation, recovery, and cryptographic-erasure procedures; and
- explicit leakage documentation for equality, prefix, frequency, size, and
  access-pattern leakage.

Encryption must remain compatible with content addressing without unsafe nonce
reuse. A deterministic AEAD construction designed for misuse resistance may be
used where deterministic ciphertext identity is required. Ad-hoc
`nonce = hash(plaintext)` with ordinary AES-GCM is prohibited.

### 8. Head publication and concurrency

Every graph-head mutation uses atomic compare-and-set, including genesis. A
storage adapter lacking create-if-absent semantics must serialize genesis through
a coordinator or use a transactionally created sentinel; unconditional first
writes are non-conforming.

Idempotency keys are scoped by principal, graph, operation, and request digest.
Reusing a key with different content fails. Pending claims have a bounded lease
and may be reclaimed only when the graph head still equals the recorded base
head. In private/sealed mode, the authenticated credential CID is additionally
bound by an atomic create-if-absent record to the request digest and idempotency
key: an exact network retry is accepted, while credential replay with changed
content is rejected. Retryable and non-retryable operations are distinct in the
public API.

### 9. Audit and observability

Security-relevant operations emit content-addressed receipts covering:

- authenticated principal and credential chain CIDs;
- effective capabilities and rejected/attenuated capabilities;
- operation, tenant, graph, entity/attribute scope;
- policy and key versions;
- input transaction or query digest;
- previous/new head CID and CAS result; and
- allow/deny/error outcome.

External verification pins the deployment's audit signer DID from a separately
controlled manifest; trusting only the signer embedded in a receipt proves
self-consistency but not provenance. Receipts are evidence, not authority. Sensitive receipt fields follow the same
encryption and visibility policy as protected data. Logs never include raw
credentials, secret keys, plaintext DEKs, or decrypted protected values.

## Threat model and guarantees

The conforming system protects against:

- a caller minting a self-signed capability it was never delegated;
- cross-tenant/graph reads and writes;
- read-policy removal or parse failure causing public disclosure;
- hidden rows influencing unauthorized query results;
- untrusted storage returning bytes under the wrong CID;
- effect/dependency projection under-declaration;
- concurrent head writers silently losing a committed update; and
- object-store disclosure of private plaintext.

It does not claim to hide all access patterns, sizes, timing, or equality leakage
from the storage operator. Those leakages must be documented per encryption and
query profile. Availability against a malicious infrastructure operator requires
independent replication and is outside confidentiality/integrity alone.

## Conformance levels

Security maturity is reported independently from API/feature maturity:

| Level | Required guarantees |
|---|---|
| `S0-local` | trusted single-process caller; no network security claim |
| `S1-integrity` | verified CID reads, verified commit paths, atomic heads |
| `S2-authenticated` | strict credential, audience, graph, operation, delegation, and revocation checks |
| `S3-authorized` | deny-by-default read/write/metadata policy with adversarial tests |
| `S4-confidential` | production AEAD/blind-index profile and managed key lifecycle |
| `S5-auditable` | complete signed/content-addressed security receipts and independent replay verification |

No deployment may advertise a higher level because a lower layer exposes a seam
for it. The checked-in deployment configuration remains explicitly
`legacy-public`; the strict S2–S5 implementation paths do not constitute a
production claim until their registry, KMS/keyring, audit-key, migration,
monitoring, recovery, and external-verification conditions are evidenced.

## Migration plan

1. Freeze security claims and label current deployments `legacy-public` or
   `legacy-plaintext`.
2. Introduce `SecurityContext` and one strict credential verifier; remove raw
   resource strings and string-only auth from handler authority decisions.
3. Require graph security mode and make private policy failures deny-all.
4. Cover every read and metadata method with the same decision engine.
5. Add per-datom write authorization and protect policy/authority entities.
6. Introduce verified block reads and verifier-derived code projections.
7. Provision managed private/sealed profiles and use the parity-checking offline
   migration tool to re-encrypt legacy blocks.
8. Close genesis CAS, add scoped idempotency, and bind write credentials to one
   request digest.
9. Maintain end-to-end adversarial conformance tests spanning client, worker, server,
   peer, block store, and policy engine.
10. Promote deployments through `S1`–`S5` only with test and operational evidence.

## Required negative tests

At minimum, CI rejects:

- valid self-signature with wrong audience/domain;
- valid self-signature with an undelegated capability string;
- valid grant scoped to another graph, tenant, operation, or expired interval;
- malformed/future/missing time bounds and revoked keys/grants;
- missing or malformed policy on a private graph;
- protected-row inference through joins, aggregates, pull, history, and metadata;
- write to a denied entity/attribute or protected policy record;
- storage bytes whose recomputed CID differs from the requested CID;
- valid semantic block accompanied by falsified effects/dependencies;
- concurrent genesis and existing-head writers;
- private deployment configured with plaintext crypto; and
- replay/idempotency-key or credential reuse with different request content.

## Consequences

### Benefits

- Authentication, authorization, integrity, and confidentiality become explicit
  and independently testable.
- CID identity and capability authority can no longer be accidentally conflated.
- RLS applies consistently across the Datomic-shaped surface.
- Deployment security claims become evidence-based rather than inferred from
  portable library seams.

### Costs

- Credential-chain, revocation, policy, KMS, and key-rotation services become
  operational dependencies.
- Blind indexes and encrypted query processing retain documented leakage and may
  reduce query flexibility or performance.
- Existing plaintext blocks and permissive policies require migration.
- Some legacy clients and self-issued tokens will be rejected.

## Rejected alternatives

### Treat any valid self-signature as a capability grant

Rejected. It authenticates authorship but supplies no external authority.

### Treat CID lookup as implicit verification

Rejected. A storage adapter can return the wrong bytes for a key; consumers must
recompute the CID.

### Default missing policy to public for every graph

Rejected for private and sealed modes. Backward compatibility is represented by
an explicit legacy/public mode, not fail-open parsing.

### Use application post-filtering as the only RLS boundary

Rejected. All query inputs and metadata surfaces must consume the same policy,
and writes require their own admission policy.

### Use ordinary deterministic AES-GCM with plaintext-derived nonces

Rejected because nonce reuse under a key violates GCM's security requirements.
