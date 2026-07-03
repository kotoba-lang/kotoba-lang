# Package Rules

Kotoba packages are resolved by signed, CID-pinned manifests and lockfiles. A
package reference that is only `name + version` is not safe for Kotoba execution.

## Required Lock Boundary

Every dependency used by safe-build must be locked with:

- package name and version
- repository identity CID (`repo-rid`)
- Git commit id when mirrored through Git
- source tree CID
- package manifest CID
- signer DID set
- explicit capability grant set

Local path dependencies are development-only unless the same content is locked
by tree CID before safe-build.

## Package Kinds

Packages are split by responsibility and contract boundary, not by a generic
`core` concept.

- `:library`: pure code/data API with no implicit host capability
- `:adapter`: explicit integration package that binds libraries or contracts
- `:schema-contract`: Lexicon, EDN IR, WIT, or similar data contract package
- `:tool`: CLI or development tooling
- `:component`: executable or Wasm component

Optional integrations should be adapters. For example, prefer
`kotoba-lang/slides-office` over hiding Office imports inside
`kotoba-lang/slides`.

## Schema Dependencies

If a dependency is only a data shape, depend on a schema-contract package and
lock that contract by manifest CID and tree CID. Runtime implementations are
separate dependencies.

Examples:

- `app.kotoba.slides.deck`
- `app.kotoba.office.graph`
- `app.kotoba.officeStyle.styleIr`
- `app.kotoba.svgraph.presentation`

Consumers may use those contract names in `:kotoba.package/consumes`; providers
may use them in `:kotoba.package/provides`.

## Wire Protocol Rule

Kotoba-owned application and resource APIs use plain JSON as their default
wire protocol. The canonical media type is `application/json` (optionally
`Content-Encoding: gzip` above `transit.core/default-gzip-threshold-bytes`),
and the authoritative implementation is `kotoba-lang/transit`
(`ADR-kotoba-json-wire-protocol.md`, superseding the earlier
Transit-tagged-JSON `ADR-kotoba-transit-wire-protocol.md`).

Rules:

- in-memory and file authoring shape: EDN data models;
- internal network/app wire: plain JSON envelopes (no `~:`/`~$`/`~#` tags);
- package/storage integrity: CID, signed manifest, and lockfile;
- external/provider interop: explicit adapters over the EDN model.

Packages that expose a network or host message surface should declare both the
semantic app contract and, when relevant, the JSON wire contract. Example
contract surfaces:

- `:wire.kotoba.slides.deck.json`
- `:wire.kotoba.sheets.workbook.json`
- `:wire.kotoba.docs.document.json`
- `:wire.kotoba.datomic.request.json`

OpenAPI, GraphQL, ActivityStreams, XRPC, provider-specific REST, and
OpenAI-compatible request shapes are adapter surfaces. They are valid at
boundaries, but the wire projection is intentionally lossy at the JSON layer
too: only the discriminant fields a reader already knows about (protocol
family, resource kind, operation kind) are recovered as keywords by name —
there is no generic, schema-free EDN reader for Kotoba-internal values such
as keywords, symbols, sets, datoms, CIDs, operations, or patches.

## Capability Rule

A package manifest declares what capabilities the package may request. A lockfile
or caller policy grants a subset. Dependencies receive no host capability by
default.

Adapter packages do not inherit the capabilities of their dependencies. Each
dependency's grant remains explicit in the lockfile.
