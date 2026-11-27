# Form ontology mapping and derivation proof (KOTOBA-985 design draft)

Status: **proposed design record, not implemented**. This note fixes the
boundary the issue asks for and gives a conformance record that can be checked
once a projection and derivation verifier exist. It changes no Definition CID,
no payload version and no runtime authority.

Machine-readable draft: `lang/form-ontology.edn`.
Conformance record: `lang/form-ontology-conformance/manifest.edn`.

## Boundary being preserved

Two identities are already decided elsewhere and must not be merged:

- `lang/pure-form.ipldsch` + `lang/pure-form.edn`: the **source Form** shape on
  IPLD. A block CID addresses bytes; a path inside a block addresses a node.
  Shape validity is not checked Kotoba.
- `lang/code-identity.edn` payload-v2: the **checked typed KIR** Definition
  CID. It is minted only after admission and seals interface, effects and
  dependencies.

The ontology maps both, but keeps them in separate fields:
`:source-block-cid` is evidence, `:definition-cid` is checked identity. A form
block CID used where a Definition CID is required is a reject case
(`negative/form_cid_as_defcid.edn`).

## What the ontology is for

KOTOBA-985 asks for existing Kotoba Form/IPLD data to be projected into an
ontology that Kotobase/Awai can search, with derivations that can be
re-verified from the original data and rules. The first subject is program
versions, dependencies and review evidence.

The draft vocabulary therefore has a small set of classes:

- `kotoba:Form` / `kotoba:Module` — IPLD source nodes, versioned by block CID.
- `kotoba:ProgramVersion` — a checked definition projected for search,
  versioned by Definition CID, carrying optional source-form evidence.
- `kotoba:Review` — a review artifact, versioned by evidence CID plus path.
- `kotoba:RuleSet`, `kotoba:Fact`, `kotoba:Derivation`, `kotoba:InferenceStep`
  — the re-verifiable derivation layer.

Names and namespaces are metadata (`:display-name`). Identity is the CID; the
stable IRI is derived from the immutable CID so a rename does not move the
entity.

## Projection rules (deterministic)

`kotoba/form-ontology/v1` is a pure function of
`(dataRootCid, mapping-version, vocabulary-version)`:

1. Walk the IPLD blocks reachable from `dataRootCid` under the same
   selector/budget rules as other IPLD reads. Emit `Form` facts with
   `inBlock`, `blockPath`, `formKind` and `formTag`. A `Link` is a CID value,
   never dereferenced as a Definition CID by this layer.
2. For each admitted checked definition, emit one `ProgramVersion` with
   `definitionCid` (payload-v2), `sourceBlockCid`/`sourceKind` when the source
   was a pure form, `displayName`, and one `dependsOnDefinitionCid` per sealed
   payload-v2 dependency — even if the dependency is not yet projected.
3. For each admitted review artifact, emit a `Review` with `evidenceCid`,
   `evidencePath` and `reviewScope`, and link the `ProgramVersion` with
   `reviewedBy`/`hasReview`.
4. Rule output is emitted as `Fact` with `factKind :derived` and `derivedBy`.
   Source projection facts are `:asserted`. A fact is never marked derived
   without a `Derivation` record.

Rebuild is from the root closure, not from a previous projection. A later root
that adds or revokes a fact produces a different fact set; the old root stays
rebuildable. That is the update/revocation story: history is new roots, not
edits.

## Derivation records

`kotoba/derivation/v1` requires `dataRootCid`, `ruleSetCid`, `conclusion`,
`premise`, `step` and `proofFormatVersion`. Each `InferenceStep` cites a
`ruleRef` inside `ruleSetCid` and the premises it consumed.

Re-verification recomputes the conclusion from the data root and rule set. It
accepts only if the recomputed fact set contains the conclusion and every step
premise is present in that root or an earlier step. The conformance record
includes the required negative controls: swapping the data root, the rule set
CID or a premise must reject (`negative/swapped_*.edn`).

The first rule is deliberately small: `type-inheritance/v1`
(`?x rdf:type ?sub`, `?sub rdfs:subClassOf ?super` ⇒ `?x rdf:type ?super`),
which is the ProgramVersion ⊑ Artifact example in KOTOBA-985.

A `Derivation` proves only the stated rule application over the stated data and
rules. It does not prove the source program correct, the rules sound, the
reviewer authorized, or the definition executable.

## Verification layers

| Layer | Checks | Guarantee | Does not prove |
| --- | --- | --- | --- |
| L0 bytes/CID | block CID/digest, IPLD Schema shape for Form, declared required-field checks | bytes are the addressed bytes; forms have the pinned shape | checked Kotoba, DefCID, authority |
| L1 projection determinism | rebuild fact set from root + mapping + vocabulary | same inputs give same facts; updates/revocations visible as a new root | the mapping is the right ontology |
| L2 derivation re-verification | recompute `Derivation`; negative controls swap root/rules/premises | the stated derivation is valid for the stated inputs | rule soundness, source truth |
| L3 program proof connection | future binding to DefCID, typed KIR semantics, SIR/machine-code proofs (KOTOBA-963..965) | deferred | KIR/SIR/target equivalence |

This is the "which layer and guarantee scope" record the issue asks for. L3 is
named so the DefCID chosen here is the same one those proofs will bind; this
spec does not claim that binding yet.

## Acceptance mapping

- **AC1 program version + review query**: `positive/program_version_review_query.edn`
  returns dependency CIDs and review evidence CID/path from one root.
- **AC2 deterministic rebuild**: `positive/deterministic_rebuild.edn`.
- **AC3 derivation re-verification**: `positive/type_inheritance_derivation.edn`
  plus `negative/swapped_data_root.edn`, `negative/swapped_rule_set.edn`,
  `negative/swapped_premise.edn`.
- **AC4 layer/guarantee record**: `lang/form-ontology.edn`
  `:verification-layers` and this note.
- **Boundary**: `negative/form_cid_as_defcid.edn`.

## Implementation scope

This issue is design + conformance record only. `lang/form-ontology.edn`
`:implementation-scope` marks the owner as **needs-owner-assignment** and lists
candidate repos (kotoba host/IPLD+datom ingest, kcp/Kotobase query surface,
network-awai-apex/Awai surface, amu typed KIR/DefCID producer). Naming the
implementer and the first slice is a separate decision; no product code changes
are part of this draft.

## Non-goals

No payload-v3, no Definition CID change, no implementation of projection/query/
verifier, no authority or execution admission from a CID, no full OWL reasoning
or SHACL conformance claim, no production deploy, no Linear status change.
