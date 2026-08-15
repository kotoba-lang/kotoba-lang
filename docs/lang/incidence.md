# Content-addressed incidence

Kotoba models durable social and distributed state as immutable, typed
relations. An incidence is an n-ary relation whose participants occur under
explicit roles. Its canonical DAG-CBOR block has a CIDv1 identity.

```clojure
{:incidence/kind :organization/member-added
 :incidence/roles
 {:organization #{{:ref/type :cid :ref/value "bafy..."}}
  :member #{{:ref/type :did :ref/value "did:key:z6Mk..."}}}
 :incidence/facts {:membership/roles #{:maintainer}}
 :incidence/parents #{"bafy..."}
 :incidence/evidence #{"bafy..."}
 :incidence/policies #{"bafy..."}}
```

The data and coordination model is Syndicate-like incidence-first, rather than
actor-first or object-first:

- a person, software agent, conventional organization, and system-of-systems
  are constituted through the same `:organization/constitution` relation;
- an organization identity is the CID of that constitution, not its mutable
  name, DID document, or current membership;
- membership changes append incidences; they do not mutate the constitution;
- a removal names the exact member-added CIDs it observed, giving deterministic
  observed-remove-set projection under concurrent histories;
- current state is a verified projection of an immutable incidence DAG.

A DID can be one constituent of that root, but it is not substituted for the
constitution CID. An injected verifier resolves the DID and verifies its proof
material, revocation/current-method policy, and trust policy. Kotoba then
independently checks that the result names the exact constitution CID and kind,
that the DID is a constituent, and that the admitted verification method is an
`assertionMethod` of that DID. Success mints an opaque organization binding
containing the authorized authenticated-session peers. A DID document, VC,
boolean, or caller-built binding map remains inert data.

This follows the W3C distinction between an identifier, a verification method,
and a purpose-specific verification relationship. DID Core assigns
`authentication` to challenge-response authentication and `assertionMethod` to
claims expressed by a DID subject; resolving a DID alone therefore does not
authorize an append or prove an organization constitution.

An addressed incidence is an assertion. A `:dataspace/retracted` incidence
names exact assertion CIDs and acts as an append-only tombstone. Observation is
an inert EDN selector over the verified active projection.

Facet lifecycle is a pure state machine. `facet-assert` returns the next facet
state plus an addressed incidence to emit. `facet-stop` returns the stopped
state plus one retraction targeting every CID owned by that facet. Duplicate
assert and duplicate stop are idempotent. An empty facet stops without emitting
a meaningless empty retraction.

```clojure
(let [opened (incidence/facet me)
      asserted (incidence/facet-assert opened presence)
      stopped (incidence/facet-stop (:facet asserted))]
  {:publish-now (:emit asserted)
   :publish-on-stop (:emit stopped)})
```

Facet state and emissions are inert EDN. Reconstructing either does not grant
publish authority. Callbacks, transport, and publication remain runtime
concerns; the pure language layer does not pretend that constructing a map has
performed an effect.

## Distributed replication

The bounded replica is an anti-entropy state machine over verified addressed
incidences. It atomically hash-checks each bounded input batch and monotonically
unions entries by CID. Arrival order has no semantic meaning. A valid child may
arrive before its parent; it is retained as an orphan and its missing parent CID
is prioritized in the next pull request. Stable bounded CID inventory pages and
exact requested export batches provide the transport-neutral synchronization
surface.

Partial replication is never promoted into complete state. Projection remains
blocked until every referenced parent is locally present and the incidence DAG
passes the normal verification rules. Thus an orphan is useful replication
progress, not an assertion that incomplete history is authoritative.

Replication and consensus are deliberately separate. CID set union converges
when peers eventually exchange all blocks, but says nothing about a unique
winner for mutually exclusive organization decisions. Applications needing a
Byzantine total order attach an external consensus adapter such as Inga; the
dataspace kernel does not disguise wall-clock arrival as consensus.

`kotoba.lang.consensus-order` is that narrow adapter boundary. An injected
consensus verifier must return a closed binding for the exact dataspace,
height, parent commit, commit ID, and ordered incidence CIDs. A truthy answer,
serialized lookalike, skipped height, wrong parent, fork, reordered entry, or
hash-invalid block fails closed. Successful admission is opaque. Applying it
hash-checks and ingests the exact certified entries while returning their total
order beside the replica; it does not relabel set-union replication as
consensus. Inga QC verification remains owned by Inga and is injected here.

For availability evidence, distinct authenticated peers can each return the
opaque signed-readback admission described below. Claims for the same
dataspace, incidence CID, constitution CID, and issuer DID can satisfy a
caller-supplied lexical threshold and mint an opaque replication certificate.
The same peer never counts twice, and serialized receipt maps never count at
all. The threshold remains explicit policy: the certificate proves only that
that many admitted peers made fresh signed readback claims, not physical
medium, retention duration, or Byzantine agreement.

## Organization governance

Governance uses the same immutable incidence model:

1. `:organization/governance-policy` binds the constitution CID to a governor
   set, distinct-governor threshold, and allowed action kinds.
2. `:organization/proposal` binds that exact policy CID to an action, payload
   CID, proposer, and application-defined conflict key.
3. `:organization/approval` binds a governor and approve/reject decision to the
   exact proposal and names one external proof CID.
4. `:organization/enacted` names the proposal and the exact approval CIDs that
   met policy.

All four records are inert content-addressed data. An injected verifier must
verify each approval proof and mint an opaque approval bound to its approval
CID, proposal CID, constitution CID, governor, and evidence CID. Enactment
requires distinct opaque approvals from the policy's governor set; a repeated
governor, rejected vote, invalid/misbound proof, below-threshold set, or
serialized lookalike fails closed. Success returns an opaque enacted decision
plus its inert addressed incidence for capability-guarded publication.

Several valid certificates for the same proposal collapse without changing
the decision. Different enacted proposals with the same organization and
conflict key remain an explicit branch conflict. The projector does not choose
the latest timestamp or lexical winner. Resolution requires another governed
proposal or a configured total-order consensus adapter.

## Capability-guarded publication

The incidence port is the narrow boundary between pure facet emissions and a
host dataspace. Its append provider is injected through lexical scope; EDN
cannot name or reconstruct that function.

Before the provider is called, the port:

1. verifies every addressed incidence and rejects duplicate CIDs;
2. requires an exact dataspace resource, never a wildcard request;
3. checks the caller's :host/ledger-append effect declaration;
4. intersects verified delegation with local policy;
5. passes only the concrete post-intersection capability to the provider;
6. records a receipt for every attempted append.

The provider may append to Holochain, a local content-addressed DAG, or an
OCapN remote object. Those are adapters behind the same port, not alternate
language authority models.

### OCapN adapter

The OCapN adapter accepts only an opaque authenticated-session value minted by
a trusted host verifier and binds the remote target to that session. It no
longer accepts an `authenticated=true` boolean, caller-provided session map, or
serialized transport function. A locator URI, sturdyref record, or other EDN
value cannot be converted into a live reference by the language kernel.

For each append it creates the current draft CapTP 1.0 abstract delivery:

    op:deliver remote-export
      [append-incidence dataspace incidence-cid canonical-dag-cbor-bytes]
      answer-position=false
      resolve-me=false

The bounded Kotoba CapTP runtime now owns canonical Syrup encoding/decoding for
the passable values used here, `op:start-session` admission, the
`starting -> active -> aborted` lifecycle, one active session per peer,
one-way client `op:deliver`, answer/resolver allocation and settlement,
opaque deferred answers, `desc:answer` promise pipelining, one-shot
`op:listen`, and the answer-derivation operations `op:get`, `op:index`, and
`op:untag`,
outbound `op:gc-answers`, wire-counted import/export `op:gc-exports`, and
`op:abort`. Malformed or
non-canonical inbound frames abort the session. Netlayer exceptions are reduced
to stable diagnostics rather than exposing remote or host debugging data.

The reliable in-order secure channel, ephemeral session-key generation, and
cryptographic verification of `op:start-session` remain injected host
capabilities. `provider-transport` supplies a real bounded TLS netlayer, but
transport certificate identity is not silently promoted to a CapTP principal.

`kotoba.lang.ocapn-handoff` implements the certificate and authority boundary
for third-party handoffs: canonical signed `desc:handoff-give` and
`desc:handoff-receive` records, Gifter and Receiver session binding, both
signature checks through injected capabilities, unique gift IDs, per-session
handoff-count replay rejection, and a single-use opaque admitted gift. The
bounded store currently requires deposit before withdrawal; waiting promises
for a future deposit and a network-facing bootstrap dispatcher remain provider
concerns rather than hidden kernel threads.

An accepted one-way send means only that the local authenticated session driver
accepted the frame. It is not evidence that the remote peer durably stored the
incidence.

For callers that require a remote durability claim, a second adapter requires a
distinct request-capable runtime. It passes the same target and arguments to
the authenticated session and requests a settled result. The runtime allocates
the concrete `resolve-me-desc` and answer position, accepts only a single
`fulfill` or `break`, releases the answer, and retains exported resolvers until
the peer's wire-count GC releases them. A remote `break` becomes only the
stable `:broken` status; its error payload is not exposed across the boundary.

The fulfilled value is accepted only when it is the exact deterministic
content-addressed `:dataspace/append-durable` incidence for the requested
dataspace and incidence CID. The appended CID is both its parent and typed
subject. A receipt for another dataspace or CID, a substituted block, a broken
promise, or a malformed settlement fails closed and produces an error attempt
receipt at the existing host guard.

This receipt is an authenticated remote durability *claim* because it arrived
through the injected authenticated session. Its CID proves the integrity and
binding of the claim; it does not independently prove physical persistence or
make the receipt data into authority.

The stronger `signed-readback-append-provider` adds an organization-bound,
fresh readback mode. Before delivery it obtains a one-shot challenge from an
injected capability and binds that challenge to the exact dataspace and
incidence CID. The remote response contains a content-addressed
`:dataspace/signed-readback` statement plus an adapter-specific proof. The
statement binds:

- the appended and read-back incidence CID (which must be identical);
- the dataspace and organization constitution CID;
- the issuer DID and its admitted `assertionMethod`;
- the authenticated session peer and transcript CID;
- the one-shot challenge, issue time, and expiry.

The lexical verifier consumes the challenge before parsing or cryptographic
verification, enforces its maximum-age policy with an injected clock, and asks
an injected signature verifier to verify the statement's canonical DAG-CBOR
bytes. Only then does it mint an opaque verified-readback value. A replay,
expired statement, substituted CID, mismatched constitution/peer/session,
wrong proof purpose, invalid signature, or serialized lookalike fails closed.
Transport failure and remote break also discard the pending challenge.

This proves a fresh, organization-signed claim that the exact CID was read
back. It still does not independently demonstrate the physical medium,
replication factor, retention period, or Byzantine quorum; those belong to the
next distributed dataspace layer.

OCapN remains a changing draft. This adapter pins its interpreted profile as
ocapn-captp-1.0-draft-2026-08-15 rather than claiming timeless wire
compatibility.

Draft sources used by this profile are the OCapN
[CapTP specification](https://github.com/ocapn/ocapn/blob/main/draft-specifications/CapTP%20Specification.md)
and [locator specification](https://github.com/ocapn/ocapn/blob/main/draft-specifications/Locators.md),
plus the canonical [Syrup draft](https://github.com/ocapn/syrup/blob/master/draft-specification.md).

Measured cross-runtime evidence is recorded in
`docs/lang/ocapn-interop-2026-08-15.edn`: ten shared Syrup values match
`@endo/ocapn` 1.1.1 byte-for-byte, and the bounded CapTP frame round-trips on
both the JVM and nbb/Node. The same receipt now includes a live framed TCP
session with bidirectional Ed25519 start-session verification and shared
session-id derivation, plus byte-identical, cross-verified third-party handoff
signatures and deposit-after-withdraw settlement. It still explicitly does
not claim float64 or JavaScript Set byte compatibility.

Identity terminology follows W3C [DID Core](https://www.w3.org/TR/did-core/)
and [Verifiable Credentials Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/).
Kotoba does not require a VC wrapper for its native readback statement; a VC is
an external adapter form whose issuer/subject/proof must resolve to the same
admitted binding and statement CID.

`did-vc-organization-binding-verifier` is the fail-closed adapter for that
boundary. The injected resolver and verifier own DID method resolution,
credential-status policy, JSON-LD processing, canonicalization, and suite
cryptography. Kotoba independently requires an admitted `assertionMethod`, its
controller and key material, an allowlisted Data Integrity cryptosuite,
`proofPurpose` `assertionMethod`, and a credential subject containing the exact
constitution CID. Verification methods, assertion relationships, credential
subjects, and authorized peers are all bounded by lexical adapter policy. The
external verifier must return the exact secured document
it verified; success is converted only into the existing opaque organization
binding.

`ucan-delegation-verifier` similarly adapts a host UCAN verifier into the
existing opaque delegation. Envelope decoding, signatures, proof lookup, and
expiry validation stay with that injected verifier. Kotoba accepts only a
closed normalized result with the exact holder audience, a non-empty set of
`kotoba://cap/` resources, explicit attenuation, and a lexically bounded proof
depth and resource count. A token string, DID, VC, or normalized result map is
still inert data and cannot be used as authority directly.

Locator parsing is inert. A sturdyref becomes a session-bound target only when
a live host resolver capability returns both an opaque authenticated session
and a valid remote export descriptor. Possessing the URI alone remains
insufficient to mint authority.

Human names and discovery aliases may change without changing an already
published incidence. A new semantic fact produces a new block and CID.

## References

A participant is always an explicitly typed reference:

| Type | Meaning |
|---|---|
| `:cid` | immutable internal Kotoba/IPLD identity |
| `:did` | interoperable principal identifier |
| `:uri` | external resource identifier |

This avoids treating an arbitrary string as a principal or resource. A DID can
point external clients toward an organization, while internal state remains a
CID-addressed history.

## Protocol mappings

- Holochain action/source-chain links map to incidence blocks and parents.
- ValueFlows economic-event roles map directly to incidence roles.
- Syndicate assertions map to blocks; withdrawals target existing CIDs.
- UCAN and ZCAP grantor, grantee, resource, action, constraint, and proof
  relationships map to delegation incidences.
- W3C VC issuer, subject, holder, claim, and evidence relationships map to
  credential incidences. DID values remain typed external references.

These mappings preserve the external vocabularies at adapters. They do not put
mutable DID documents or protocol-specific wire envelopes into internal
semantic identity.

## Security boundary

A valid incidence CID proves only that canonical content has not changed. It
does not prove who authored the block, that a VC or UCAN signature is valid, or
that the holder may invoke an effect.

Effectful execution still follows the capability pipeline:

1. verify the signature and delegation/evidence chain;
2. mint an opaque verified-delegation runtime value;
3. intersect that verified delegation with local policy;
4. mint a concrete scoped capability value;
5. guard the host effect and emit a receipt.

Consequently a delegation or credential incidence received from an untrusted
store is data-only until its protocol adapter verifies it. Merely knowing its
CID never grants authority.

The incidence publication port accepts the opaque verified-delegation value,
not a vector of caller-constructed grant maps. Likewise, OCapN connection setup
accepts an opaque authenticated-session value containing sanitized peer and
transcript-CID metadata; transport functions and verifier capabilities are not
exposed by its audit description. These values are ordinary runtime objects to
trusted host code but are not EDN and cannot be reconstructed by a guest.

## EDN data and object capabilities

EDN is the semantic data model, not the authority model. Canonical DAG-CBOR is
the binary identity encoding, and a protocol adapter may choose a different
wire envelope. These are distinct layers.

```text
EDN-like immutable value -> canonical DAG-CBOR -> CID
runtime capability       -> host-admitted lexical/affine value, not EDN
```

There is deliberately no `#cap` tagged literal and `:ref/type` does not admit a
capability variant. A `{:capability ...}` map, delegation incidence, UCAN, or
ZCAP document is inert description/evidence. Only the capability admission
pipeline can mint the concrete runtime value accepted by a host effect.

Kotoba syntax being Clojure-shaped does not imply Clojure/JVM authority.
Ambient `slurp`, JVM interop, environment access, reader evaluation, and guest
macros remain outside the safe language. Runtime authority arrives through
arguments and captured lexical bindings and is further bounded by affine use,
effect rows, verified delegation, and local policy.

Future surface sugar may spell pure constructors and runtime effects as
`assert`, `observe`, and `facet`, but it must elaborate to this closed data and
capability kernel. Macro expansion is not allowed to become an ambient-authority
escape hatch.
