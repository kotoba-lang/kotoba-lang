# Kotoba Spec: names, paths, subjects and whitespace (v1)

Status: accepted, 2026-10-21. Authority: `kotoba-lang/kotoba-lang`.
Machine contract: [naming.kotoba](../../lib/library-import/resources/kotoba/spec/naming.kotoba),
in Kotoba ADL. Portable validator: `kotoba.lang.naming`, distributed through
`:deps/root "lib/library-import"`. The full language and the source host use
the same implementation. Tracking: [JUN-192](https://linear.app/com-junkawasaki/issue/JUN-192).

The words MUST and MUST NOT below are requirements of this versioned naming
profile. Source authoring conventions do not replace the source reader,
`lang/guest-grammar.edn`, typing, effects, or capability admission. Lisp culture
is the basis: symbols are names, symbolic operators are ordinary names, and
reader syntax is distinct from the naming of values and functions.

## Context determines meaning

| Context | Example | Meaning |
| --- | --- | --- |
| Library / contract ID | `kotoba.coll`, `kotoba.docs.documentChange` | Exact catalog or contract name |
| Source reference | `coll/map-vals`, `math/+` | Namespace or alias followed by a definition name |
| Relative field path | `profile.display-name` | Data selection within an explicitly supplied value/schema |
| Concrete event subject | `kotoba.docs.document.changed` | Declared event classification |
| Subscription selector | `kotoba.docs.*.changed`, `kotoba.docs.>` | Selection among declared, authorized subjects |
| Display name | `My document` | Human text, not an identifier |

A string MUST NOT be reinterpreted across these contexts implicitly. A dotted
ID does not perform field access; a field path does not resolve/import code;
a subject does not identify a resource, grant subscription rights, or publish
an event. Resource key, scope and field path MUST remain separate fields in
event declarations/envelopes. Nothing here adds expression syntax such as
automatic `object.field` evaluation or an executable publish/subscribe form.

## Dotted IDs and exact aliases

An ID has at least two nonempty dot-separated segments, each matching
`[a-z][A-Za-z0-9]*`. Event profiles MUST start with `kotoba.`. This is project
authority, not DNS ownership or an ATProto NSID.

A specification that Kotoba defines itself gets an ID that starts with `kotoba.`.
A library that implements an external specification uses the reverse domain of that
specification's publisher, then the specification: `org.ietf.cbor` (IETF, `ietf.org`),
`gov.nist.sha3` (NIST, `nist.gov`).
The catalog declares each root it uses in
`:namespace-authorities`, a vector of `{:prefix P :authority A :dns-authority? false}`,
where `A` is `:kotoba-project` (only for `kotoba`) or `:external-reverse-domain`. An ID
under an external root has at least three segments. `:dns-authority?` MUST be `false`,
prefixes MUST be unique, and `kotoba` MUST stay declared. A catalog without
`:namespace-authorities` has the single root `kotoba`, so existing catalogs are unchanged.

The validator is syntax only and never looks anything up, so it cannot check that the
domain segment is the publisher's real domain; that is a review obligation and must come
from the specification itself, never from a repository name. A declared root grants no
authority and does not rename a namespace. Segment rules are unchanged: a hyphen is
refused, so write `org.nist.mlKem`, not `org.nist.ml-kem`; publisher domains with hyphens
(for example `wi-fi.org`) have no canonical spelling yet and need a decision before
they can be used.

Hyphens, underscores, spaces, slashes and reader/operator punctuation MUST NOT
occur in canonical ID segments. Empty segments, leading/trailing dots and
wildcards MUST be refused. These are ID rules, not restrictions on Lisp symbols.

Legacy aliases retain the existing catalog grammar (nonblank string), including
`kotoba-lang/coll`, `io.github.kotoba-lang/coll`, and `kotoba.lang.coll`.
Aliases MUST be explicitly owned by one package and resolved by exact,
case-sensitive, one-hop lookup. They MUST NOT be trimmed, Unicode-normalized,
case-folded, or converted from hyphens to dots. Any ID/alias collision refuses
the whole catalog. Unknown names refuse. Package selection MUST NOT rewrite
namespace APIs or signed locks. This version narrows no existing alias grammar.

## Lisp source names and punctuation

Ordinary word names use `kebab-case`: `map-vals`, `read-document`. Operators
such as `+`, `-`, `*`, `/`, `=`, `<`, `<=`, `not=`, and compositions such as
`->`, `->>` and `string->bytes` remain ordinary symbols where the grammar
admits them. Their mathematical/control meaning comes from a bound definition
or elaboration rule, not from their spelling alone. Type/constructor naming,
Unicode names and existing external APIs retain their established spelling.
The Common Lisp `+constant-name+` convention may be retained by an API too;
it does not itself make a binding constant in Kotoba.

| Character | Source convention / boundary |
| --- | --- |
| `-` | Word boundary within a name; standalone subtraction/negation name |
| `_` | Ordinary symbol character; `_` / `_unused` conventionally mark an unused binding. No implicit wildcard/discard semantics outside a form that declares them |
| `+`, `=` | Ordinary symbol characters, conventionally arithmetic and equality; `+1` remains a numeric-looking token, not an authored function name |
| `?` | Predicate suffix, e.g. `empty?`; it does not add runtime type checking |
| `!` | Convention for mutation or an externally observable effect, e.g. `reset!`; it is not a capability, effect declaration, or proof of impurity |
| `*` | Multiplication name; `*context*` is Lisp/Clojure earmuff notation. This convention admits no dynamic binding or ambient state |
| `/` | Namespace/name boundary, e.g. `coll/map-vals`. Standalone `/` is the division name; `math//` qualifies that name |
| `.` | Namespace hierarchy, not host member access or automatic data traversal |
| `'` | At the start, reader quote; inside a name, a Lisp prime convention such as `value'`. Quoted-data admission remains bounded by guest-grammar |
| `#` | Reader dispatch family (`#{}`, `#?`, `#?@`, etc.) or a generated-name marker in a host dialect. Not ordinary authored name punctuation; never automatic hash/CID lookup |
| `@` | Reader dereference prefix. The admitted local-atom elaboration is defined in guest-grammar; it grants no remote read or ambient dereference |
| `~`, `~@` and backquote | Reserved for the syntax-quote/unquote family in compatibility dialects. This profile does not admit macros or unquote to checked Kotoba |
| `:`, `^`, `&`, `%`, `;`, `\` | Keyword, metadata, binding, reader/comment or literal roles depend on the selected reader/form; do not mint new semantics from punctuation |

`lisp-name?`, `namespace-name?` and `qualified-name?` check this portable
authoring profile, not every accepted host token or guest expression. They
MUST NOT be used to reject existing source programs merely for their style.
Reader macros are not local names: `@x`, `~x`, `x#`, `#_` and `#=` are not
accepted by `lisp-name?`. Naming a function `unsafe!` grants nothing; naming an
effectful function without `!` removes no effect checking. Generated identifiers
and compatibility-only spellings remain subject to their original owner.
Explicit CLI hash-abbreviation lookup (such as `library inspect #hash`) keeps
its own existing contract; `#` in an arbitrary name does not request that lookup.

## Relative field paths

The typed representation is a nonempty vector of exact string keys or
nonnegative integer indices, up to the portable exact-integer limit
9,007,199,254,740,991. An index selects an array position; a string selects a
map/record field. Strings are never converted into keywords or numeric indices.

The dotted string shorthand accepts only segments matching
`[a-z][A-Za-z0-9]*(?:-[a-z0-9]+)*`. It splits only on `.` and produces **string
keys**: `profile.display-name` becomes `["profile" "display-name"]`. Existing
camel-case schema fields such as `firstName` keep their case. Local field words
prefer kebab case; external schemas retain their original names.

Use the typed vector for literal dots, spaces, punctuation, empty keys, numeric
string keys, or array indices: `["profile" "display.name"]`, `["items" 0 "name"]`,
`["0"]`, `[""]`. `items.0.name`, `profile.*`, `profile..name`, slash syntax and
backslash escapes MUST be refused as shorthand. A literal `"*"` in a vector is
a key, never a wildcard. There is no implicit creation, mutation, evaluation,
subscription, case folding, or escaping. Schema/key-kind validation and actual
access are separate operations; this profile performs no data access.

## Event subjects and selectors

Publish subjects use the concrete `kotoba.` project-ID grammar. Publication
MUST reject wildcard characters. Subscribe patterns use the same literal
segments, with `*` as one entire segment and `>` as the final entire segment.
`*` matches exactly one segment; `>` matches **one or more**, never zero.
The project root stays literal `kotoba`; `kotoba.>` is the broadest v1 selector.
Partial globs, empty segments and nonterminal `>` MUST be refused.

`kotoba.docs.*.changed` matches `kotoba.docs.document.changed`, not
`kotoba.docs.changed`. `kotoba.docs.>` does not match `kotoba.docs`.
Matching is case-sensitive with no normalization. Names that match a selector
MUST still be intersected with declarations and current grants. Matching is a
pure check, never an authorization, network operation, or transport guarantee.
Transport adapters translate this profile explicitly; this is not an assertion
of wire compatibility with NATS, ATProto, or another protocol.

## Whitespace and identity

Canonical authoring uses ASCII spaces and LF outside literals/comments. Use one
space between adjacent ordinary forms and two spaces per nesting level, allowing
aligned continuations. Layout is not significant indentation. Do not place spaces
inside a name or around an internal `.`, `-`, or `/`: `map-vals` is one symbol,
whereas `map - vals` is three forms. `(+ a b)` is application; `a+b` is a name,
not infix addition. A display name containing spaces belongs in a string.

Tabs and CRLF remain compatibility-reader inputs. The explicit
`validate-source-whitespace` authoring check diagnoses noncanonical whitespace
outside strings, character literals and comments; it is not installed as a
compiler rejection gate or a full indentation formatter. It MUST preserve
literal/comment content, and MUST NOT silently normalize Unicode spaces.
Comma remains a separator on compatibility readers that already support it,
not punctuation inside a name. Reader-target admission remains authoritative.

Formatting does not enter a checked definition's existing normalized typed-KIR
identity (`lang/code-identity.edn`). This says nothing about a raw source/tree
digest remaining equal after editing whitespace, and changes no identity codec.

## References and implementation boundary

The [Clojure reader reference](https://clojure.org/reference/reader) and
[character guide](https://clojure.org/guides/weird_characters) explain the Lisp
family conventions used here. They are references, not blanket Kotoba admission.
The Kotoba source authority remains `lang/guest-grammar.edn`; the event runtime
remains a separate implementation/qualification gate. Normative examples in the
machine contract are decoded through the actual Kotoba ADL owner and exercised
by the host design gate; portable tests cover matching, literal path identity,
punctuation and the unchanged alias resolver.
