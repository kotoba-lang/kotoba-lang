# ADR: Capability repository separation + semantic definition CID

- **Status:** accepted
- **Date:** 2026-07-28
- **Authority:** `kotoba-lang/kotoba-core-contracts` (catalog + CID), Tamaki (emit), Kototama (admit)
- **Related:** superproject ADR-2607289500; contracts#21; tamaki#10; kototama#95

## Context

Authority capabilities were catalogued centrally but needed a Unison-like
**content-addressed import identity** so that:

- repository renames do not break meaning;
- effect/ABI/policy changes are explicit new identities;
- Tamaki/Kototama cannot silently substitute aliases.

## Decision

### 1. One authority capability → one public repository

- Repository name: `capability-<id-with-dashes>` (example: `capability-http-fetch`).
- Composition capabilities stay in Tamaki and never receive HostCaps directly.
- Scaffold: `clojure -M -m scaffold-capability-repos /abs/out [--update]`
  in `kotoba-core-contracts`.

### 2. Import identity is `:capability/definition-cid`

- CIDv1 over canonical **DAG-CBOR** definition block (`definition-schema`
  `kotoba.capability-definition.v1`).
- Block fields: schema, version, abi{namespace,version}, sorted imports,
  sorted effects, defaultPolicy, artifactFormat, dependencies, hashContract link.
- **Excluded:** human name, GitHub path, Radicle RID, provider availability.
- Hash algorithm rules are pinned by `:capability/hash-contract-cid`.

### 3. Discovery aliases

Repository name and Radicle RID are for discovery only. Rename/move preserves
identity if the definition CID is unchanged. Changing authority semantics
**must** produce a new CID.

### 4. Tamaki / Kototama boundary

| Component | Duty |
|---|---|
| **core-contracts** | Compute definition + hash-contract CIDs; full catalog; scaffold |
| **Tamaki** | Pin contracts; put `repository-refs-for-imports` (with both CIDs) on the execution envelope |
| **Kototama** | Admit **exact** definition CIDs; reject drift/substitution (`capability-repository-set-mismatch`) |

CID possession does not grant authority. Policy + sealed envelope admission remain mandatory.

### 5. Generated packages are contract-only

Until a reviewed, signed, content-addressed Wasm component is published,
`provider-status` remains `contract-only`. Do not claim component digests that
do not exist.

### 6. Landed pins (2026-07-28)

| Repo | PR | Merge SHA |
|---|---|---|
| kotoba-core-contracts | [#21](https://github.com/kotoba-lang/kotoba-core-contracts/pull/21) | `2e615d17406ef4b6311401167294becceb5af2a1` |
| tamaki | [#10](https://github.com/kotoba-lang/tamaki/pull/10) | `660e70bfbb08423d1f498616a68d4d01ae2a39e5` |
| kototama | [#95](https://github.com/kotoba-lang/kototama/pull/95) | `74146a896dfae72b8eb407acf6f5717e0d2fd2e1` |

Verification at land: contracts 45/420 + 50/50 generated repos; Tamaki 232/704;
Kototama Tamaki-contract slice 5/8.

## Consequences

- Agents adding capabilities must edit **contracts first**, regenerate,
  then advance Tamaki/Kototama pins.
- App authors must not treat repo names as capability identity.
- Wire numeric IDs in `capability_contract.edn` and compiler kit IDs remain
  separate layers from definition CIDs (see agent handoff §3).

## References

- `src/kotoba/core/capability_repository.cljc` (`definition-block`, `definition-cid`,
  `repository-refs-for-imports`)
- `scripts/scaffold_capability_repos.clj`
- Example package README: `kotoba-lang/capability-http-fetch`

## Amendment: reference-implemented (2026-07-28)

`kotoba-core-contracts#22` allows `:provider-status :reference-implemented` for an
allowlist of pure capabilities when `artifacts/*.wasm` + sha256 + exports are
published. Definition CIDs remain independent of digests. First landings:
`capability-math-sin#1`, `capability-math-cos#1`.
