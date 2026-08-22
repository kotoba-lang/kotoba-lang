# ADR — Canonical value codec for EDN data identity

- **Status**: Proposed
- **Date**: 2026-07-28
- **Artifacts**: `lang/value-codec.edn`
- **Related**: `ADR-kotoba-code-identity-and-abilities.md`,
  `ADR-kotoba-lang-profile.md`, `ADR-safe-capability-language.md`,
  `kotoba:docs/ADR-kotoba-content-addressed-codebase-gap.md` (G5)
- **Execution-boundary authority**:
  `90-docs/adr/2607252500-kotoba-wasm-component-first-execution-boundary.edn`

## Context

`ADR-kotoba-code-identity-and-abilities.md` gives a *definition* a content
identity.  It does not give a *value* one.  Today three unrelated canonical
forms coexist, and none of them covers the values the language can express.

| Canonical form | Where | Scope |
|---|---|---|
| DAG-CBOR literal IR | `kotoba:src/kotoba/semantic_code.cljc:75` | code literals only |
| Canonical EDN text → CIDv1-raw | `compiler:src/kotoba/compiler/artifact.cljc:49` | signed supply-chain objects |
| `ipld/encode` over triple components | `arrangement:src/arrangement/core.cljc:158` | persisted datom values |

Three concrete defects follow from this split.

**1. The IPLD codec is a node codec, not a value codec.**  `->cbor-data`
(`io-ipld:src/ipld/core.cljc:93`) admits keyword map keys and passes them
through unchanged, and `decode` returns strings — so a keyword does not
round-trip.  Sets are not handled at all: `set?` is neither `map?` nor
`sequential?`, so a set falls through to the `:else` branch and reaches
`cbor/encode` with no defined element order.  `arrangement`'s README states
the consequence plainly ("s/p/o are opaque strings … general typed values are
a follow-up"), and its `link->edn`/`edn->link` pair
(`arrangement:src/arrangement/core.cljc:91-99`) exists only because a
prolly-tree leaf key is `pr-str`'d rather than encoded — a workaround for one
type that a value codec would subsume.

**2. The language's own literal subset has holes.**  `normalize-literal`
(`kotoba:src/kotoba/semantic_code.cljc:75-94`) admits `nil`, booleans,
integers, strings, keywords, symbols, vectors, sets, and maps, and rejects
everything else closed.  Floating-point literals are among the rejected, while
`:f32` is already a semantic metadata key in the same file
(`semantic_code.cljc:34`) and a value type in `semantic-type-block`
(`semantic_code.cljc:222-226`).  A checked `f32` definition therefore cannot
contain an `f32` literal.

**3. The datom model promises more than any persistence path accepts.**
`kotoba.kgraph` documents datoms as `[e a v]` where "e/a/v are arbitrary EDN
values" (`kotoba:src/kotoba/kgraph.clj:11-12`) and shares `datom.core` with
`kotobase-engine`, but the only durable path — `index-root` — is limited to
what `ipld/encode` round-trips.  The in-memory Arrangement itself is already
general: `assert-quad` (`arrangement:src/arrangement/core.cljc:44-56`) stores
s/p/o as ordinary map keys.  The restriction lives entirely at the
persistence boundary.

G5 of the codebase-gap ADR already requires explicit codecs and versioned
schemas before new value constructs participate in identity.  This ADR
supplies the value half of that requirement.

## Decision

Adopt **one canonical value codec**, `kotoba.value.v1`, normative for every
place a Kotoba EDN value acquires an identity or crosses a content-addressed
boundary.  `lang/value-codec.edn` is its machine-readable contract.

### 1. Uniform tagged encoding

Every admitted value encodes as a DAG-CBOR 2-element array `[type-code
payload]`.  DAG-CBOR has no keyword, symbol, or set type, so a direct
mapping cannot distinguish `:a` from `"a"`; a single-key escape map
(`{"/kw" …}`) would collide with a user map that has that key.  A uniform
tag removes both ambiguities at a cost of roughly two bytes per scalar.

The codec covers the *value*.  Structures that embed values — the semantic
definition IR, a prolly-tree leaf, a datom — keep their own envelopes and
call the codec for each value position.

### 2. Admitted types

| Type | Code | Payload | Notes |
|---|---|---|---|
| nil | 0 | CBOR null | |
| boolean | 1 | CBOR true/false | |
| integer | 2 | CBOR int | 64-bit two's-complement range; wider rejected |
| float | 3 | CBOR binary64 | NaN, ±Infinity, and `-0.0` rejected |
| string | 4 | CBOR text | valid UTF-8, no lone surrogates |
| keyword | 5 | CBOR text | `"ns/name"` or `"name"` |
| symbol | 6 | CBOR text | `"ns/name"` or `"name"` |
| bytes | 7 | CBOR byte string | |
| link | 8 | CBOR tag 42 | `ipld.core/Link` |
| vector | 16 | CBOR array of encoded values | order preserved |
| list | 17 | CBOR array of encoded values | order preserved, distinct from vector |
| set | 18 | CBOR array of encoded values | sorted by encoded element bytes |
| map | 19 | CBOR array of `[k v]` pairs | sorted by encoded key bytes |

Everything else — ratios, chars, uuids, `#inst`, records, functions, any
tagged literal, any CBOR tag other than 42 — is **rejected closed**.  A new
type is a codec version bump, never a silent widening.

Float admission follows DAG-CBOR: binary64 only, with the non-finite values
excluded so that no two distinct bit patterns share a meaning.  `-0.0` is
rejected rather than normalized to `0.0`, so that rejection is visible at the
authoring boundary instead of silently changing a value's identity.  `:f32`
remains a type-level annotation on a binder, as it already is in
`semantic-type-block`; it is not a separate wire type.

### 3. Determinism is a cross-runtime obligation

Encoding is a total function of the value: definite-length items only, no
tags but 42, canonical integer/float minor forms, and the sort orders above
computed over encoded bytes rather than over source order or `pr-str` text.

Byte-identical output across JVM and nbb is normative, not incidental.
`compiler:src/kotoba/compiler/artifact.cljc:7-29` records a live instance of
this exact failure — the same KIR hashing differently on the two runtimes
because `pr-str` rendered a bigint differently — so `kotoba.value.v1` ships
with shared conformance vectors from its first stage, not after.

### 4. Three consumers, one codec

| Consumer | Change | Effect |
|---|---|---|
| `io-ipld` | add `encode-value`/`decode-value`; leave `encode`/`decode` (node codec) unchanged | no break for existing node callers |
| `arrangement` `index-root` (`core.cljc:158`) | encode the leaf value with `encode-value` | typed s/p/o; `link->edn`/`edn->link` become redundant |
| `semantic-code` `normalize-literal` (`semantic_code.cljc:75`) | delegate the value position to the codec | closes the float hole; one literal contract |

### 5. Versioning and migration are required, not optional

`arrangement/current-schema-version` (`core.cljc:174`) must go 1 → 2, and
`restore` already rejects unknown versions (`core.cljc:234-237`), so existing
snapshots need a declared migration path before the bump lands.  The
`semantic-code` change lands on the **v2** elaborated contract identity
(`semantic_code.cljc:270-273`), not on v1: old definition CIDs must remain
verifiable under their recorded contract, and a new codec must never
reinterpret them.

### 6. Consequence for structured host arguments

With a value codec, a **compile-time constant** collection literal in a
host-argument position can lower to a data segment carrying its canonical
bytes, exactly as a bare string literal already lowers via `:string-host-arg`
(`compiler:resources/kotoba/lang/guest-grammar.edn:195`).  This is the
admitted route for structured host arguments such as a datalog query map, and
it has three effects:

- the argument leaves the 127-UTF-8-byte portable-string bound
  (`guest-grammar.edn:194`), which today truncates any non-trivial query;
- the argument survives into the typed KIR as structured data, where
  `normalize-literal` already gives it a definition identity at no additional
  hash-contract cost;
- the host stops parsing an opaque string
  (`kotoba:src/kotoba/wasm_exec.clj:294`) and decodes a bounded, typed value.

It does **not** introduce runtime data construction.  `quote` is not a core
special form (`guest-grammar.edn:33`) and keyword literals lower to an FNV-1a
integer identity (`guest-grammar.edn:190`), so a guest cannot rebuild an EDN
value at runtime.  Only constants are admissible, and that restriction is
deliberate.

## Explicit non-goals

- **This is not a cache key.**  A query identity plus a snapshot identity is
  not declared effect-free here.  `commit!` rebuilds all four prolly trees
  over every triple on every commit (`arrangement:src/arrangement/core.cljc:154-160`,
  `202-219`), so snapshot-per-transaction is O(N), not O(Δ), and query
  remains an effect excluded from the shared cache
  (`kotoba:src/kotoba/shared_semantic_cache.clj:70-72`).
- **This does not relax any capability rule.**  `kgraph-query` stays denied
  under every scoped grant (`kotoba:src/kotoba/wasm_exec.clj:290-296`).  A
  query CID does not bound which entities a join returns, and a parameterized
  query shares one CID across different result scopes, so a query-CID
  allowlist is a separate decision with its own ADR.
- **This does not replace canonical EDN text hashing.**  Signed
  supply-chain objects stay on `artifact.cljc`'s canonical EDN text because
  they are meant to be read and reviewed as EDN.  Two encodings continue to
  exist deliberately: DAG-CBOR for values and definitions, canonical EDN text
  for signed contract objects.
- **This does not make a randomized AEAD deterministic.**  `commit!`'s
  content-addressing claim is conditional on `blind-fn`/`encrypt-fn` being
  deterministic (`arrangement:src/arrangement/core.cljc:145-152`); the codec
  does not change that and does not weaken ADR-2607051000.
- No guest-side EDN reader, tagged-literal extension point, reader macro, or
  runtime `eval`.

## Safety consequences

A single value codec removes two silent-corruption classes: a keyword
persisted and read back as a string, and a set persisted in an
implementation-defined order.  Both currently produce values that differ from
what was written while remaining validly CID-addressed — integrity checks pass
on the wrong value.  Fail-closed rejection of unadmitted types keeps the
attack surface at the codec rather than at each call site, and the existing
DAG-CBOR admission bounds (`kotoba:src/kotoba/bounded_cbor.clj:7-12`)
continue to apply before decoding.

The codec proves nothing about authority.  A value CID is not a grant, and
stage VC5 below adds no permission that stage VC0 did not already have.

## Delivery stages

| Stage | Deliverable | Admission rule |
|---|---|---|
| VC0 | This ADR and `lang/value-codec.edn` | no implementation claim |
| VC1 | Codec implementation plus JVM/nbb conformance vectors | byte-identical output on both runtimes; every rejected type has a negative vector |
| VC2 | `io-ipld` `encode-value`/`decode-value` | existing node `encode`/`decode` callers unchanged |
| VC3 | `arrangement` leaf values on the codec, `schema-version` 2, declared migration | a v1 snapshot either migrates or is rejected with a named reason; never reinterpreted |
| VC4 | `semantic-code` delegates literals; float literals admitted under contract v2 | v1 definition CIDs still verify under the v1 contract |
| VC5 | `:data-host-arg` lowering and typed host decode for structured arguments | constants only; no runtime construction; capability rules unchanged |

Until VC3 and VC4 land, the design must be described as **proposed**, and
`arrangement`'s documented string-only persistence and `semantic-code`'s v1
literal subset remain the operative contracts.
