# ADR - Kotoba Transit wire protocol

- **Status**: Superseded by `ADR-kotoba-json-wire-protocol.md` (2026-07-03)
- **Date**: 2026-07-01
- **Artifacts**: `kotoba-lang/transit`, `docs/lang/package-rules.md`, `docs/lang/README.md`
- **Related**: `ADR-kotoba-package-cid-lock.md`, `ADR-safe-capability-language.md`, `ADR-kotoba-rad-git-sovereign-repo.md`

> **Superseded**: at M1 maturity (one call site, two days old), the
> Transit-style `~:`/`~$`/`~#` tag scheme decided here was replaced by plain
> `application/json` (optionally gzip-compressed) to stay aligned with
> Kotoba's web-standard transport direction (QUIC/WebTransport in `net`,
> `murakumo`, `rt`) and to keep wire messages debuggable with generic JSON
> tooling. See `ADR-kotoba-json-wire-protocol.md` for the current rules. This
> document is kept for history.

## Context

Kotoba's semantic data model is EDN/Datomic-shaped. Keywords, symbols, sets,
lists, tagged values, datoms, package resource kinds, CIDs, operations, and
patches are first-class data. Plain JSON can transport some of those values only
after lossy string conventions or ad hoc schema-specific encodings.

At the same time, Kotoba needs a browser/server wire format that can be carried
by ordinary HTTP, Workers, Pages, JVM, bb, JavaScript, and compatibility Rust
hosts. The wire protocol must preserve Kotoba values without making a host
runtime the source of protocol truth.

## Decision

Adopt Transit JSON as Kotoba's default internal application wire protocol.

The authoritative implementation and tag/envelope semantics live in
`kotoba-lang/transit` CLJC. Hosts may turn the JSON-compatible value into bytes,
send HTTP requests, or store messages, but they do not define new Kotoba Transit
tags or envelope keys independently.

The default HTTP media type is:

```text
application/transit+json
```

Kotoba application messages use a Transit envelope with:

```clojure
{:kotoba.protocol/family :kotoba.protocol/office
 :kotoba.protocol/version 1
 :kotoba.resource/kind :slides/deck
 :kotoba.resource/payload {...}}
```

The same pattern extends to Datomic requests, package registry operations,
runtime control messages, and app resources. Resource families may define their
own resource kinds, but must use the shared Transit value encoding and versioned
envelope vocabulary.

## Rules

- **Internal app wire is Transit**: Kotoba-owned app/resource APIs use Transit
  JSON by default.
- **EDN remains the in-memory and file authoring shape**: libraries expose pure
  EDN data models; Transit is the network/wire projection.
- **CID remains the integrity and storage identity**: Transit envelopes may
  carry CIDs, but CID verification and package locks are separate from wire
  serialization.
- **JSON is an adapter surface**: external APIs may speak plain JSON, OpenAPI,
  GraphQL, ActivityStreams, XRPC, or provider-specific shapes, but these are
  adapters over Kotoba Transit/EDN semantics.
- **No ad hoc JSON tag schemes**: a package must not invent incompatible string
  encodings for keywords, symbols, sets, datoms, CIDs, or operations when the
  message is Kotoba-internal.
- **Version every envelope family**: breaking changes to envelope keys,
  resource-kind semantics, or tag interpretation require a protocol version
  bump.
- **MessagePack is an optimization, not a new protocol**: Transit MessagePack may
  be added for binary/high-throughput transport, using the same semantic tags and
  envelope vocabulary.

## Package Boundary

Packages that expose a network or host message surface should declare the
contract they provide or consume. For example:

```edn
{:kotoba.package/name "kotoba-lang/slides"
 :kotoba.package/kind :library
 :kotoba.package/provides [:app.kotoba.slides.deck
                           :wire.kotoba.slides.deck.transit]}

{:kotoba.package/name "kotoba-lang/slides-office"
 :kotoba.package/kind :adapter
 :kotoba.package/consumes [:app.kotoba.slides.deck
                           :wire.kotoba.slides.deck.transit
                           :app.kotoba.office.graph]
 :kotoba.package/provides [:app.kotoba.slides.officeImport]}
```

Schema-contract packages may define resource shapes. Runtime packages implement
or adapt those shapes. Both are CID-locked and signed under the package rules.

## Consequences

- Kotoba browser, server, bb, JVM, and compatibility Rust hosts can share a
  single protocol vocabulary.
- EDN/Datomic values survive network roundtrips without schema-local string
  hacks.
- JSON-only provider protocols remain possible, but live behind explicit
  adapters.
- `kotoba-lang/transit` becomes a foundational library and must stay small,
  portable, CLJC, and dependency-light.

## Maturity

- `M0`: this ADR and rules.
- `M1`: `kotoba-lang/transit` defines media types, EDN value encoding, Datomic
  envelopes, and office-family envelopes.
- `M2`: Slides, Sheets, and Docs expose Transit envelope helpers.
- `M3`: package contract fixtures include wire contract surfaces.
- `M4`: safe-build/package gates reject Kotoba-internal app APIs that expose only
  ad hoc JSON wire contracts.
- `M5`: Transit MessagePack and streaming readers share the same semantic
  envelope vocabulary.
