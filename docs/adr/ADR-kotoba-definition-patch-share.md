# ADR — Patch/share is the interchange unit over `:definition-cid`

- **Status**: Accepted for the interchange unit; local apply/share spike
  implemented. Transport, persistence, and concurrent merge are unmeasured.
- **Date**: 2026-09-02
- **Artifacts**: `lang/definition-patch.edn`,
  `src/kotoba/lang/definition_patch.cljc`,
  `test/kotoba/lang/definition_patch_test.clj`
- **Extends**: `ADR-kotoba-content-addressed-codebase.md`,
  `ADR-kotoba-code-identity-and-abilities.md`
- **Consumes**: `lang/code-identity.edn` (`:definition-cid`, payload-version 2);
  facade `src/kotoba/lang/code_identity.cljc`; authority
  `kotoba.kir.definition-identity`
- **Does not own**: typed eval (`ADR-kotoba-typed-eval.md`,
  `lang/typed-eval.edn`); guest grammar; the kotoba-kir hasher

## Investigation

The identity hasher is already the pin. `lang/code-identity.edn` names
`:definition-cid` `:implemented` (payload-version 2, DAG-CBOR / sha2-256,
scope `:closed-deterministic-checked-definition`). This repository only
delegates: `kotoba.lang.code-identity` is a facade. The algorithm lives in
`kotoba-lang/kotoba-kir`. This ADR does not fork a parallel hasher and does
not move that pin.

What was missing is the *interchange unit* for those identities.

`ADR-kotoba-content-addressed-codebase.md` still says source files remain the
primary Git interchange surface. That is the gap versus exchanging a set of
`:definition-cid` adds, replaces, and removes plus name mappings. That ADR
also names `lang/semantic-code.edn` as an artifact; that file is 404 in this
repository. The older C1–C5 semantic-code / namespace-commit work is therefore
not a layer on top of payload-v2 `:definition-cid` here, and this change does
not revive it.

`kotoba-lang/codebase` has authoring/diff and namespace-commit machinery, but
it hashes through `kotoba.codebase.typed-code` (`:second-implementation-to-be-migrated`).
Measured 2026-09-02, the same function mints two CIDs. That tree is migrate-away,
not the hash this contract extends. Building patch/share on typed-code would
mint a second interchange domain.

Stale related branches (`codex/definition-ipld-links`,
`codex/delegate-defcid-to-kir`) are not a base. The facade is already on this
pin.

Package registry `:registry/definition-cids` locks exported identities inside a
signed package record. That is package admission, not a patch people exchange
instead of copying files.

## Decision

1. **Hash `:definition-cid` only.** Patch/share names payload-v2 definition
   CIDs from `kotoba.kir.definition-identity` via the language facade. It does
   not hash source-tree bytes, source files, package names, Git refs, Wasm
   artifacts, or `kotoba.typed-definition.v1` blocks.
2. **A CID is identity, never authority.** Applying or sharing a patch does
   not admit eval, grant a capability, bind a WIT world, or satisfy
   `lang/typed-eval.edn`. Typed eval remains the owner of `(eval request)`.
3. **Patch/share is the interchange unit for definitions.** A patch is an
   ordered vector of `:add` / `:replace` / `:remove` operations, each binding
   a human-facing name to a `:definition-cid`. A share is that patch plus
   optional definition payloads whose authority hash must equal the claimed
   CID. People exchange this object rather than copying source files.
4. **This is not a Unison clone.** Kotoba keeps Clojure-shaped source, Wasm
   component-first execution, and CID/EDN packages. Unison is a source of the
   *idea* that names and hashes are separate, not a syntax, runtime, or
   codebase manager to copy.
5. **The parallel hasher fails closed.** A patch that names
   `:kotoba.codebase/typed-code`, or that presents a CID measured as a
   typed-code hash of the same function, is refused. Unknown typed-code CIDs
   that look like CIDv1 cannot be detected without running that hasher; that
   detection remains unmeasured. When a share carries a payload, the facade
   recomputes the authority CID and a typed-code claim will not match.

Source files remain the authoring surface. Git remains the repository
surface. Patch/share is the *definition* interchange surface on top of the
existing identity pin.

## Shape

```edn
{:kotoba.definition-patch/version 1
 :hasher :kotoba.kir/definition-identity
 :ops
 [{:op :add :name "math/inc" :definition-cid "bafy…"}
  {:op :replace :name "math/inc" :from "bafy…" :to "bafy…"}
  {:op :remove :name "math/inc" :definition-cid "bafy…"}]}
```

```edn
{:kotoba.definition-share/version 1
 :patch {:kotoba.definition-patch/version 1 :ops […]}
 :definitions {"bafy…" {:definition/kir …}}}
```

Names are metadata. Rename is `:remove` plus `:add` of the same CID.
`:replace` requires the current binding to equal `:from`, so a patch cannot
silently clobber.

## Explicit non-goals

- Unison syntax, a global Unison-style codebase, or copying Unison source
- A second hasher, or adopting `kotoba.codebase.typed-code`
- Treating `:source-tree-cid` (status `:not-implemented`) as implemented
- Rewriting guest grammar, typed-eval internals, or the JVM-clj drop
- Lifting HOLD
- Network transport, Kotobase persistence, patch merge, or a patch CID
- Language scores, GTM claims, or a Release URL
- Host `eval` / `load-string` / reader-eval

## Unmeasured

These stay unmeasured and are not claimed by the spike:

- whether any user-local or provider store persists typed-code CIDs
  (already UNVERIFIED in `lang/code-identity.edn`)
- wire transport, authentication, or missing-block sync for a share
- three-way merge of concurrent patches
- a content-addressed identity *of the patch document itself*
- ClojureScript byte-identity of apply/share (the spike is portable source;
  only the JVM tests in this repository are measured)

## Consequences

Definition interchange can name checked KIR identities without copying files
and without granting the right to run them. Package locks, typed eval, and
Wasm admission stay the authority gates they already are. The migrate-away
typed-code tree cannot be smuggled in as this unit.
