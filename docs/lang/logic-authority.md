# Effect, delegation, and runtime authority

Status: language profile contract  
Date: 2026-08-30

Kotoba uses one bounded relational vocabulary across the compiler and runtime,
but it does not collapse their trust boundaries. The same predicate shape can
carry facts from several origins; the origin determines what the fact proves.

```text
source
  -> Amu infers effects and emits a content-bound logic manifest
  -> the VM observes one concrete intent
  -> Biscuit supplies an attenuated delegated grant
  -> local policy and the current runtime world narrow it
  -> only then does the host create a concrete capability value
  -> every allow or denial emits a receipt
```

## Vocabulary

| Term | Meaning | Owner |
| --- | --- | --- |
| effect | an operation a definition may perform | language and Amu |
| intent | the concrete operation requested by this run | guest and VM |
| grant | an upper bound delegated between principals | Biscuit |
| capability | the post-authorization runtime value passed to a provider | host runtime |
| receipt | evidence for an allowed or denied attempt | VM and provider |

Ordinary source names operations. It does not repeat capability declarations,
numeric capability IDs, WIT imports, or provider callbacks. Effects are inferred
from the closed call graph. An explicit effect declaration is a public contract
or security ceiling, never a way to acquire authority. Explicit capability
values remain useful only for attenuation and delegation.

## Logic manifest

Amu emits `:kotoba.logic-manifest/v1` from checked KIR. The manifest binds the
definition, artifact, KIR digest, compiler contract, language semantics, world,
dependencies, inferred semantic effect row, target wire effects, intent schema
CIDs, and resource bounds. Callers cannot supply or widen either effect row.

The persisted representation is canonical IPLD data. Its authorizer projection
is a bounded vector of n-ary tuples such as:

```clojure
[["amu:definition" definition-cid]
 ["amu:requires" definition-cid :http/post]
 ["amu:world" definition-cid world-cid]]
```

Kotoba's authored logic may use closed inert lists. Amu lowers those lists to
canonical tuple data; neither the compiler nor VM evaluates them as Clojure.

The VM accepts compiler facts only after checking the manifest hash and its
content-addressed identity. A production VM should additionally verify either
a pinned compiler attestation or an independently checkable KIR certificate.
Datalog authorization does not prove compiler soundness.

## Authorization relation

The effective authority relation is:

```text
statically possible (Amu)
  AND requested now (VM intent)
  AND delegated (Biscuit)
  AND permitted here (local authorizer)
  AND currently available (provider world, epoch, generation)
  => concrete capability
```

The terms are relations and scopes rather than merely flat sets, but each term
is mandatory. Missing compiler evidence, delegation, policy, trusted time, or
runtime availability denies the call.

Biscuit is the delegation plane. Token facts and checks may attenuate a grant,
but a token cannot authorize itself. Only local authorizer policy may produce
`allow`. Token facts must not be pooled with trusted `amu:`, `policy:`, or
`runtime:` facts; adapters project verified and monotonically attenuated token
authority into `grant:` facts before policy evaluation.

Revocation remains a runtime responsibility. Biscuit expiry narrows a grant;
Kototama/aiueos epoch and generation state can invalidate an otherwise valid
grant at each effect boundary.

## IPLD and CARv2

IPLD gives manifests, policies, inputs, states, decisions, and receipts stable
content identities. CARv2 may pack the reachable non-secret blocks and provide
an index. Packing changes transport and lookup cost, not meaning or authority.

A raw Biscuit bearer token must not be placed in a public or shared CAR. The
bundle may contain a token commitment, root public-key CID, revocation IDs,
verification result, safely reduced grant evidence, and authorization receipt.
Raw bearer bytes remain ephemeral or are stored only as a recipient-encrypted
private block.

## Required runtime properties

- compiler, VM, grant, policy, and runtime facts retain distinct provenance;
- token-supplied facts cannot impersonate another provenance;
- Datalog evaluation is range-restricted and budgeted; budget exhaustion is a
  named denial, not an empty result;
- the provider receives only the concrete post-intersection capability;
- every allowed, denied, expired, unavailable, malformed, or budget-refused
  attempt emits or links a receipt;
- a receipt links the manifest, policy, world/epoch, grant evidence, concrete
  request, and outcome without containing bearer credentials.
