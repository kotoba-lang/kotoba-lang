# ADR — Canonical value codec for EDN data identity

- **Status**: Accepted — VC0–VC5 implemented
- **Date**: 2026-07-28
- **Artifacts**: `lang/value-codec.edn`,
  `kotoba-lang/io-ipld:src/kotoba/value/codec.cljc`,
  `kotoba-lang/io-ipld:src/ipld/value.cljc`,
  `kotoba-lang/provider:src/provider/value_codec.cljc`,
  `kotoba-lang/arrangement:src/arrangement/core.cljc`,
  `kotoba-lang/kotoba:src/kotoba/semantic_code.cljc`
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
| integer | 2 | CBOR int | safe-integer range only (§3) |
| float | 3 | CBOR byte string, 8 bytes | big-endian IEEE-754 binary64; explicit wrapper required (§3) |
| string | 4 | CBOR text | valid UTF-8, no lone surrogates |
| keyword | 5 | CBOR text | `"ns/name"` or `"name"` |
| symbol | 6 | CBOR text | `"ns/name"` or `"name"` |
| bytes | 7 | CBOR byte string | |
| link | 8 | CBOR tag 42 | `ipld.link/Link`; `ipld.core/link` remains compatible |
| vector | 16 | CBOR array of encoded values | order preserved |
| list | 17 | CBOR array of encoded values | order preserved, distinct from vector |
| set | 18 | CBOR array of encoded values | sorted by encoded element bytes, unsigned |
| map | 19 | CBOR array of `[k v]` pairs | sorted by encoded key bytes, unsigned |

Everything else — ratios, chars, uuids, `#inst`, records, functions, any
tagged literal, any CBOR tag other than 42 — is **rejected closed**.  A new
type is a codec version bump, never a silent widening.

### 3. Three constraints the conformance vectors forced, and their cost

Each of these was found by running the VC1 vectors on both runtimes.  They are
recorded here because each one narrows what the codec promises.

**A float requires an explicit wrapper.**  JavaScript cannot distinguish `1.0`
from `1`: `(integer? 1.0)` is `true` on ClojureScript and `false` on the JVM.
Classifying a bare number by its runtime type would give one source program
two encodings.  A float is therefore an explicit `float64` value and a bare
non-integral number is rejected closed — the same discipline `ipld.core`
already applies to links, which are an explicit `Link` rather than a guess
about which strings are CIDs.  The cost is that authors and the compiler must
wrap float literals; the compiler can do this at read time, where the source
token `1.0` is unambiguous regardless of runtime.

**Floats are carried as an 8-byte big-endian byte string, not a CBOR float.**
`cbor.core` implements a deliberately tight profile with no float support at
all ("no indefinite lengths, no floats").  Adding major-type-7 floats to the
shared CBOR layer would change a dependency every content-addressed repo in
the org already pins.  Since the codec has its own tag envelope, it does not
need CBOR-native float typing, and controlling the eight bytes directly also
removes any per-runtime float-encoding divergence.

**Integers are admitted only in the safe-integer range** (±(2^53−1)).
`cbor.core`'s `byte-at` documents that its ClojureScript path is exact only to
2^53, because JS bitwise operators truncate to Int32 first and it divides
instead.  Admitting a wider integer would silently produce different bytes on
the two runtimes.  Exact i64 needs a BigInt-aware payload and is deferred to a
named follow-up rather than faked — a real limitation, since Kotoba has `:i64`
as a first-class type.

### 4. Determinism is a cross-runtime obligation

Encoding is a total function of the value: definite-length items only, no
tags but 42, canonical integer minor forms, and set/map order computed over
**unsigned** encoded bytes — shorter encoding first, then bytewise, the same
rule `cbor.core` applies to DAG-CBOR map keys.  Unsignedness is load-bearing:
a raw JVM byte array compared signed orders `0x80`–`0xff` before `0x00`, while
a ClojureScript `Uint8Array` orders them after, so a sort that leaked the
platform's signedness would be byte-divergence hiding inside a "deterministic"
sort.  `semantic_code.cljc:87,91` currently sorts signed byte vectors and
inherits the fix when it delegates at VC4.

Byte-identical output across runtimes is normative, not incidental.
`compiler:src/kotoba/compiler/artifact.cljc:7-29` records a live instance of
this exact failure — the same KIR hashing differently on JVM and nbb because
`pr-str` rendered a bigint differently — so `kotoba.value.v1` ships with
shared conformance vectors from its first stage, not after.

### 5. Three consumers, one codec

| Consumer | Change | Effect |
|---|---|---|
| `io-ipld` | add `ipld.value/encode-value`/`decode-value`; add `ipld.core/link->tag`/`tag->link` so the tag-42 discipline is not re-derived per codec; leave `encode`/`decode` (node codec) unchanged | no break for existing node callers |
| `arrangement` `index-root` | encode the leaf **value** with `encode-value`; `schema-version` 1 → 2 | typed s/p/o survive persistence.  The **key** path is deliberately unchanged (§6a), so `link->edn`/`edn->link` are retained, not retired |
| `semantic-code` `normalize-literal` (`semantic_code.cljc:75`) | delegate the value position to the codec | closes the float hole; one literal contract |

### 6a. The key path is bounded, not typed

VC3 types the **value** slot only.  `blind-fn` is a caller-supplied keyed MAC
whose output a cold reader re-derives to seek by prefix (`kotobase-peer`'s
`cold-datoms`), so changing what it is handed is a cross-repo crypto change
rather than a local one — and the value slot is where a component's type was
actually being lost.

What did change is that the key path now **fails closed** on components whose
`pr-str` is not canonical.  The reference `blind-fn` prints its argument, and
`pr-str` is canonical for only some values: a set or map has undefined
iteration order, a byte array prints an identity hash (so two arrays with the
*same bytes* blind differently), and a record prints platform-dependently.
None of these were rejected before; they produced divergent seek tokens
quietly.  A component must now be nil, a boolean, an integer, a string, a
keyword, a symbol, or a Link.

`link->edn`/`edn->link` are therefore **retained**, correcting this ADR's own
first draft: a leaf key is still built with `pr-str`, and `pr-str` of a Link
record is platform-dependent, so the textual stand-in still earns its place on
the key path — and `edn->link` is now the version-1 read path.

### 6. Versioning and migration are required, not optional

`arrangement/current-schema-version` must go 1 → 2, and `restore` already
rejects unknown versions, so existing snapshots need a declared migration path
before the bump lands.  The path chosen is **read-compatible**: `restore`
dispatches on the version the snapshot *records*, decoding a version-1 leaf
through the old node codec and a version-2 leaf through `kotoba.value.v1`.
No rewrite is required and no existing store stops opening.  The honest limit
is that a version-1 snapshot cannot retroactively recover a type it never
persisted — a keyword written under version 1 comes back as the string it was
stored as.  VC3 fixes what is written from now on; it does not reinterpret old
bytes.  The
`semantic-code` change lands on the **v2** elaborated contract identity
(`semantic_code.cljc:270-273`), not on v1: old definition CIDs must remain
verifiable under their recorded contract, and a new codec must never
reinterpret them.

### 7. Consequence for structured host arguments

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

`decode-value` additionally re-validates canonical set/map order and rejects
duplicates, so a peer cannot hand back a canonically-*addressed* block whose
contents are not in canonical *form*.

The codec proves nothing about authority.  A value CID is not a grant, and
stage VC5 below adds no permission that stage VC0 did not already have.

## Delivery stages

| Stage | Deliverable | Admission rule | Status |
|---|---|---|---|
| VC0 | This ADR and `lang/value-codec.edn` | no implementation claim | implemented |
| VC1 | Codec implementation plus JVM/cljs conformance vectors | byte-identical output on both runtimes; every rejected type has a negative vector | implemented |
| VC2 | `io-ipld` `encode-value`/`decode-value` | existing node `encode`/`decode` callers unchanged | implemented |
| VC3 | `arrangement` leaf values on the codec, `schema-version` 2, declared migration | a v1 snapshot either migrates or is rejected with a named reason; never reinterpreted | implemented |
| VC4 | `semantic-code` delegates literals; float literals admitted; contract identity names the codec | v1 definition CIDs still verify under the v1 contract | implemented |
| VC5 | `:data-host-arg` lowering and typed host decode for structured arguments | constants only; no runtime construction; capability rules unchanged | implemented |

VC4 also fixed two defects it uncovered rather than working around them: a
quoted form was not data (`'[a b]` recursed through `normalize-expr`, resolving
`a` and `b` as global **references**, while `'(a b)` had no branch at all), and
set/map literal order was computed over **signed** JVM bytes, so the same
literal hashed to two different definition CIDs depending on which runtime
compiled it.

One boundary is recorded rather than hidden: a float literal is admitted where
the reader preserves float-ness (JVM). On ClojureScript a source `1.0` and a
source `1` are the same runtime value, so a non-integral literal is rejected
rather than guessed — fail-closed, never two encodings for one source program.

VC5 lands the host-argument lowering on the existing `bytes-ptr`/`bytes-len`
path: `memory-layout` runs after `lower-language-forms` and already lays out
every all-integer vector literal as a data segment, so no new emit machinery
was required.  The host distinguishes the two wire forms by first byte, and the
discriminator is exact rather than heuristic — every value encodes as a CBOR
2-element array whose head byte is `0x82`, and `0x82` is a UTF-8 *continuation*
byte, so no valid UTF-8 text and therefore no valid EDN text can begin with it.
An all-integer vector is deliberately excluded from the new lowering: it is
already the raw-byte literal, and re-encoding it would change the bytes an
existing guest hands its host.

All six stages are implemented, so the design is **accepted**.  What remains is
not part of this decision: `kotobase-peer`'s `cold-datoms` must decode
version-2 leaves before it bumps its `arrangement` pin, and exact i64 still
needs a BigInt-aware payload.
