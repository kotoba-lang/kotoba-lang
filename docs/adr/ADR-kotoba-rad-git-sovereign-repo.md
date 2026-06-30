# ADR: kotoba-rad / kotoba-git sovereign repository layer

**Status**: proposed
**Date**: 2026-06-28
**Deciders**: Jun Kawasaki

## Context

`kotoba-git` already gives kotoba a byte-exact Git object bridge:

- Git framed object bytes (`<type> <size>\0<body>`) are stored as CID blocks.
- `:git/oid` and `:git.object/cid` form the SHA-1 to CID bridge.
- commits, trees, tags, refs are projected into Datom attributes.
- loose and packed repos can be imported, and snapshot manifests can rehydrate the Datom projection.

The missing layer is what Radicle provides around Git: repository identity, delegate authority,
ref authorization, selective replication, source-chain auditability, and peer accountability.
This ADR names that layer **kotoba-rad** and defines how it composes with `kotoba-git`,
`kotoba-dht`, `kotoba-auth`, and `kotoba-crypto`.

This is **not** a Radicle protocol implementation. It is a kotoba-native sovereign repository
layer that fills the same role with CID, Datom, DID, Source Chain, Warrant, and object encryption.

## Decision

### Layer Split

| Layer | Crate / module | Responsibility | Trust boundary |
|---|---|---|---|
| Git object fidelity | `kotoba-git` | byte-exact Git object codec, SHA-1 to CID bridge, pack/loose import, refs projection | verifies Git object bytes |
| Repository identity | `kotoba-rad` design layer | repo id, delegate set, visibility, policies, epoch keys | signed identity journal |
| Authority / auth | `kotoba-auth` | DID key parsing, CACAO grants, signature verification | signer authority |
| Source chain / warrants | `kotoba-dht` | append-only per-DID journal, warrants for invalid updates | peer accountability |
| Object encryption | `kotoba-crypto` | envelope encryption, HPKE now, hybrid PQ later | confidentiality |
| Distribution | `kotoba-net` / read plane | CID-over-HTTP, gossipsub, bitswap-style replication | transport untrusted |

### Repository ID

`kotoba-rad` defines a repository identity as the CID of the genesis identity document:

```edn
{:kotoba.rad/type :repo.identity
 :kotoba.rad/version 1
 :repo/name "com-junkawasaki/kotoba"
 :repo/description "kotoba substrate"
 :repo/visibility :private        ; :public | :private
 :repo/default-branch "refs/heads/main"
 :repo/git-hash-suite :git.sha1    ; Git compatibility anchor
 :repo/block-hash-suite :cid.sha2-256.dag-cbor
 :repo/delegates [{:did "did:key:z6Mk..."
                   :role :maintainer
                   :can [:ref/update :ref/create :object/publish :identity/update]}]
 :repo/created-at 1782600000000}
```

`rid = cid(canonical-cbor(identity-doc))`.

The RID is immutable. Mutable repository state is a signed journal of updates that points back
to the RID.

### Identity Journal

`kotoba-rad` state is an append-only identity journal. Each event is a Source Chain entry signed
by a current delegate:

```edn
{:kotoba.rad/type :repo.event
 :repo/rid "bafy..."
 :event/seq 12
 :event/prev "bafy..."
 :event/kind :ref.update
 :event/body {:ref/name "refs/heads/main"
              :ref/old "ef01..."
              :ref/new "a41b..."
              :git/manifest-cid "bafy..."}
 :event/actor "did:key:z6Mk..."
 :event/sig {:alg :ed25519
             :bytes "..."}}
```

Event kinds:

| Event | Purpose | Required authority |
|---|---|---|
| `:identity.update` | delegate set, repo policy, crypto suite | `:identity/update` |
| `:ref.create` | create ref | `:ref/create` |
| `:ref.update` | fast-forward or allowed non-fast-forward | `:ref/update` |
| `:ref.delete` | delete ref | `:ref/delete` |
| `:object.publish` | announce object or manifest availability | `:object/publish` |
| `:grant.add` | add recipient envelope for private content | `:grant/write` |
| `:grant.revoke` | revoke future access via epoch rotation | `:grant/revoke` |
| `:warrant.issue` | publish signed evidence of invalid event | validator authority |

The journal is also projected to Datoms under `:rad.*` so Datalog can answer:

- who can update this ref?
- what is the current head of `refs/heads/main`?
- what events led to this head?
- which recipients can decrypt epoch `N`?
- which warrants accuse a peer or delegate?

### Ref Authorization

`kotoba-git` stores refs as facts. `kotoba-rad` decides whether a new ref fact is valid.

Validation rules:

1. Actor DID must be a current delegate at the event's parent journal state.
2. Actor must hold the capability required by `event/kind`.
3. `event/prev` must match the current journal head unless importing a fork as an explicit branch.
4. `:ref.update` is fast-forward by default. Non-fast-forward requires policy
   `{:ref/non-fast-forward true}` or a role with `:ref/force-update`.
5. `:ref/new` must resolve to a Git commit object present in the `kotoba-git` projection.
6. Every Git object reachable from the new head must have a `:git.object/cid` bridge.
7. Private repos may gossip only encrypted objects or availability proofs to non-recipient peers.

Invalid events generate `kotoba-dht` warrants:

| Violation | Warrant rule |
|---|---|
| bad event signature | `InvalidSignature` |
| broken journal sequence | `SeqBreak` |
| bad `event/prev` | `PrevMismatch` |
| actor lacks capability | `CacaoInvalid` or future `RadUnauthorizedRefUpdate` |
| manifest claims missing objects | `ProllyInconsistent` |
| revoked grant reused | `RekeyRevoked` |

### Object Privacy

Radicle-style selective replication is useful for availability control, but it is not the
confidentiality boundary. `kotoba-rad` private repositories use object encryption.

For private repos:

- Git framed bytes are encrypted before untrusted replication.
- The primary fetch key is `ciphertext-cid = hash(ciphertext)`.
- `plaintext-git-oid` and `plaintext-cid` are authority-scoped metadata.
- Recipient grants wrap epoch DEKs using `kotoba-crypto`.
- Revocation is epoch rotation; already distributed ciphertext is assumed unrecoverable only if
  the revoked party never received the epoch DEK.

Current implementation can use X25519 HPKE-like wrapping from `kotoba-crypto`. The data model must
carry algorithm tags so it can migrate to hybrid classical + post-quantum wrapping:

```edn
{:grant/epoch 3
 :grant/recipient "did:key:z6Mk..."
 :grant/kdf :hkdf-sha256
 :grant/kem [:x25519 :ml-kem-768]
 :grant/aead :aes-256-gcm
 :grant/wrapped-key-cid "bafy..."}
```

### Maturity Model

| Stage | Name | Deliverable | Status |
|---|---|---|---|
| R0 | Git object bridge | byte-exact objects, refs, pack import, snapshot manifest | implemented in `kotoba-git` |
| R1 | Signed repo identity | RID, identity journal schema, delegate validation, ref authorization | design target |
| R2 | Private object store | encrypted Git object blocks, recipient grants, epoch rotation | design target, uses `kotoba-crypto` primitives |
| R3 | P2P accountability | Source Chain publication, warrants, reputation / replication policy | partial primitives in `kotoba-dht` |
| R4 | PQ-ready suite | hybrid KEM/signatures, algorithm agility, migration tooling | future |

R1 is the next maturity step. It is mostly schema + validation logic, not a new storage backend.

### API Shape

Proposed Rust facade:

```rust
pub struct RadRepo<'a> {
    git: kotoba_git::GitStore<'a>,
    auth: RadAuthority,
}

impl<'a> RadRepo<'a> {
    pub async fn init(identity: RepoIdentity) -> Result<RepoRid>;
    pub async fn apply_event(&self, event: RepoEvent) -> Result<RepoState>;
    pub async fn authorize_ref_update(&self, update: RefUpdate) -> Result<()>;
    pub async fn publish_manifest(&self) -> Result<KotobaCid>;
    pub async fn import_git_dir(&self, git_dir: &Path, actor: Did) -> Result<ImportReport>;
}
```

CLI shape:

```bash
kotoba rad init --name com-junkawasaki/kotoba --private
kotoba rad import .git --actor "$KOTOBA_OPERATOR_DID"
kotoba rad refs
kotoba rad push --to peer --ref refs/heads/main
kotoba rad grant add did:key:z6Mk...
kotoba rad verify --rid bafy...
```

## Consequences

- Git compatibility remains byte-exact because `kotoba-git` is the only layer that handles Git
  object materialization.
- Repository sovereignty moves out of mutable forge accounts and into signed identity journals.
- Private repos can replicate encrypted bytes through untrusted transport.
- The design is stricter than Radicle private repos: peer allow-lists are availability policy,
  not the confidentiality boundary.
- R1 can be implemented incrementally without changing `kotoba-git` object storage.

## Open Questions

- Whether `kotoba-rad` becomes a new Rust crate or starts as `kotoba-git::rad`.
- Whether `RepoEvent` should reuse `kotoba-dht::ChainEntry` directly or wrap it with a narrower
  repository event type.
- Whether fast-forward validation should materialize commit ancestry through `kotoba-git::log` or
  maintain a cached reachability index.
- Which PQ implementation becomes the first production dependency for ML-KEM / ML-DSA.

## References

- `crates/kotoba-git`: Git object bridge and Datom projection.
- `crates/kotoba-dht`: Source Chain, Warrant, Neighborhood primitives.
- `crates/kotoba-auth`: DID and CACAO verification.
- `crates/kotoba-crypto`: envelope, AEAD, HPKE-like X25519 wrapping.
- `docs/ADR-kotoba-mesh-wasm-hosting.md`: mesh and no-central-master design.
- `90-docs/adr/2606271600-kotoba-stack-equivalences.md`: Radicle role equivalence and security posture.
