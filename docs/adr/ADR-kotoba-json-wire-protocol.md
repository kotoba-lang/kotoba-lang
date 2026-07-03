# ADR - Kotoba JSON wire protocol

- **Status**: Accepted
- **Date**: 2026-07-03
- **Supersedes**: `ADR-kotoba-transit-wire-protocol.md`
- **Artifacts**: `kotoba-lang/transit`, `kotoba-lang/slides` (`src/slides/wire.cljc`),
  `docs/lang/package-rules.md`, `docs/lang/README.md`, `docs/lang/gates.md`
- **Related**: `ADR-kotoba-package-cid-lock.md`, `ADR-safe-capability-language.md`,
  `ADR-kotoba-rad-git-sovereign-repo.md`

## Context

`ADR-kotoba-transit-wire-protocol.md` (2026-07-01) adopted Transit JSON —
concretely, a bespoke `~:`/`~$`/`~#` string-tag scheme implemented in
`kotoba-lang/transit` — as the default wire protocol for Kotoba-owned
app/resource APIs, to carry EDN/Datomic values (keywords, symbols, sets,
lists) across the network without lossy ad hoc encodings.

Two days later, at M1 maturity, actual adoption was a single call site
(`kotoba-lang/slides` → `src/slides/wire.cljc`). At the same time, Kotoba's
transport-layer direction (`kotoba-lang/net`, `kotoba-lang/murakumo`,
`kotoba-lang/rt`) had already converged on boring, widely-supported
transports: libp2p QUIC for native↔native, HTTP/3-first ALPN with HTTP/2
fallback for browser/edge, and transport-independent WebRTC signaling with
WebTransport/QUIC as a documented future option. A bespoke tag scheme at the
application/payload layer is inconsistent with that "use the web-standard
transport" direction, and it makes every Kotoba wire message opaque to
generic tooling (curl, browser devtools, `jq`, log pipelines, observability
stacks) that expects plain JSON.

The cost of reversing the payload-encoding decision is lowest right now:
one call site, two days old, no external consumers.

## Decision

Adopt plain JSON — `application/json`, optionally gzip-compressed via
`Content-Encoding: gzip` — as Kotoba's default internal application wire
protocol, replacing the Transit-tagged JSON scheme.

The authoritative implementation stays `kotoba-lang/transit` (repo name and
`transit.core` namespace unchanged, to avoid a disruptive rename); its wire
values are now plain JSON with no reader tags. Hosts turn the JSON-compatible
value into bytes, gzip-compress it when `:content-encoding "gzip"` is
present, send HTTP requests, or store messages, but they do not invent new
Kotoba envelope keys independently.

The default HTTP media type is:

```text
application/json
```

Bodies above `transit.core/default-gzip-threshold-bytes` (1024 bytes) should
be gzip-compressed with `Content-Encoding: gzip` when the client sends
`Accept-Encoding: gzip`.

Kotoba application messages use a JSON envelope with the same shape as
before, minus tagging:

```clojure
{:kotoba.protocol/family :kotoba.protocol/office
 :kotoba.protocol/version 1
 :kotoba.resource/kind :slides/deck
 :kotoba.resource/payload {...}}
```

which is written to the wire as plain JSON:

```json
{"kotoba.protocol/family": "kotoba.protocol/office",
 "kotoba.protocol/version": 1,
 "kotoba.resource/kind": "slides/deck",
 "kotoba.resource/payload": {...}}
```

## Wire projection is intentionally lossy

Unlike the superseded Transit-tagged scheme, this JSON projection does not
attempt to preserve exact EDN type identity for a generic reader:

- keywords/symbols become bare strings (`:slides/deck` → `"slides/deck"`);
- sets and other seqs become JSON arrays;
- maps become JSON objects with string keys.

A generic reader cannot tell a keyword from a plain string that happens to
look like one. This is accepted deliberately: the envelope shape (protocol
family, resource kind, operation kind — the fields that discriminate how to
interpret the rest of the message) is known ahead of time by both sides of a
given wire contract, so those specific fields are recovered explicitly by
name (e.g. `read-office-envelope-body`), not by a universal tag reader. The
payload body itself round-trips as plain JSON data; a caller that needs the
original EDN shape back converts it explicitly using the schema it already
has for that resource kind.

## Rules

- **Internal app wire is plain JSON**: Kotoba-owned app/resource APIs use
  `application/json` by default, gzip-compressed above the size threshold.
- **EDN remains the in-memory and file authoring shape**: libraries expose
  pure EDN data models; JSON is the network/wire projection.
- **CID remains the integrity and storage identity**: JSON envelopes may
  carry CIDs, but CID verification and package locks are separate from wire
  serialization.
- **No reader tags for Kotoba-internal messages**: keywords, symbols, sets,
  and lists are written as bare strings/arrays, not `~:`/`~$`/`~#`-tagged
  values. A package must not invent an incompatible ad hoc string-tag scheme
  when the message is Kotoba-internal.
- **Named discriminant fields are recovered explicitly**: readers convert the
  handful of keyword-typed fields they know about (protocol family, resource
  kind, operation kind) back to keywords by name; there is no generic,
  schema-free EDN reader for the wire format.
- **Version every envelope family**: breaking changes to envelope keys,
  resource-kind semantics, or field interpretation require a protocol
  version bump.
- **Transport is a separate concern**: this ADR only changes the payload
  encoding. HTTP/1.1, HTTP/2, HTTP/3 (QUIC), and WebTransport are all valid
  carriers of a plain-JSON body; transport choice continues to be decided
  per the existing direction in `kotoba-lang/net`, `kotoba-lang/murakumo`,
  and `kotoba-lang/rt`.
- **Binary optimization, if ever needed, is a separate ADR**: this ADR drops
  the earlier "Transit MessagePack is an optimization" path along with the
  tag scheme it optimized. A future binary wire format is a new decision,
  not an extension of this one.

## Package Boundary

Unchanged from `ADR-kotoba-transit-wire-protocol.md`: packages that expose a
network or host message surface should declare the contract they provide or
consume, e.g.:

```edn
{:kotoba.package/name "kotoba-lang/slides"
 :kotoba.package/kind :library
 :kotoba.package/provides [:app.kotoba.slides.deck
                           :wire.kotoba.slides.deck.json]}

{:kotoba.package/name "kotoba-lang/slides-office"
 :kotoba.package/kind :adapter
 :kotoba.package/consumes [:app.kotoba.slides.deck
                           :wire.kotoba.slides.deck.json
                           :app.kotoba.office.graph]
 :kotoba.package/provides [:app.kotoba.slides.officeImport]}
```

Existing manifests/lockfiles that reference `:wire.kotoba.*.transit` contract
surfaces should be updated to `:wire.kotoba.*.json` as they are touched;
this is not a breaking rename of the contract vocabulary's shape, only of
the suffix.

## Consequences

- Kotoba wire messages are debuggable with ordinary tools (curl, browser
  devtools, `jq`) without a Kotoba-specific decoder.
- `application/json` interoperates directly with generic JSON tooling,
  observability stacks, and any future non-Kotoba consumer, with no adapter
  needed at the media-type level.
- Kotoba loses generic, lossless EDN round-tripping over the wire; this
  trade is accepted because the two call sites that exist today already
  know their own envelope shape.
- `kotoba-lang/transit` keeps its name (avoiding a disruptive rename of one
  library two days into its life) but is no longer a Transit implementation;
  its docs say so explicitly.

## Maturity

- `M0`: this ADR and rules (supersedes the M0-M2 state of
  `ADR-kotoba-transit-wire-protocol.md`).
- `M1`: `kotoba-lang/transit` defines the JSON media type, EDN-to-plain-JSON
  value encoding, Datomic envelopes, gzip opt-in, and office-family
  envelopes. Done as of this ADR.
- `M2`: Slides, Sheets, and Docs expose JSON envelope helpers. Slides done;
  Sheets/Docs pending (unchanged from before — no such helpers existed).
- `M3`: package contract fixtures include `:wire.kotoba.*.json` wire contract
  surfaces.
- `M4`: safe-build/package gates reject Kotoba-internal app APIs that expose
  only ad hoc, non-JSON wire contracts.
- `M5`: dropped. A future binary/streaming wire format, if needed, is a new
  ADR rather than an extension of the Transit tag vocabulary this ADR
  removes.
