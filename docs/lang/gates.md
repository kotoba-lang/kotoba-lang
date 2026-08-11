# Kotoba language gates

The language repository owns semantics, grammar, conformance, documentation,
and the host-neutral CLI vocabulary. Source classification and package
contracts were intentionally moved to `kotoba-lang/kotoba-core-contracts`;
release admission is owned by `kotoba-lang/release`.

## Standalone repository gates

```sh
test -f lang/guest-grammar.edn
test -f lang/surface-status.edn
test -f lang/elaboration-pipeline.edn
test -f lang/conformance/manifest.edn
test -f lang/capability-conformance/manifest.edn
test -f lang/type-conformance/manifest.edn
test -f docs/authority-map.edn
nbb scripts/check-docs.cljs
nbb scripts/check-grammar-authority.cljs
bb scripts/check-cli-contract.bb lang/cli.edn
bb scripts/check-capability-values.bb
bb scripts/check-legacy-runtime-absence.bb
clojure -M:compatibility
clojure -M:test
```

`check-docs.cljs` rejects missing reader routes, broken relative links in the
checked set, invalid authority records, and profile-version disagreement among
grammar, surface, and elaboration authorities. `check-grammar-authority.cljs`
checks admitted/forbidden surface classification and any available vendored
grammar copies.

The full Clojure suite includes sibling-owned evidence checks and therefore
needs the west sibling layout. In a standalone clone, run the self-contained
gates above and report missing sibling evidence separately rather than calling
the whole suite green.

## Negative documentation proof

The documentation gate has a committed broken fixture:

```sh
nbb scripts/check-docs.cljs --root test/fixtures/docs-negative
```

It must exit non-zero with `:docs/link-missing`. A gate that cannot demonstrate
its failure path is not landed.

## Cross-repository gates

Package admission is verified by:

```sh
bb ../kotoba-core-contracts/scripts/check-package-contract.bb
```

Implementation conformance is owned by `kotoba-lang/kotoba` and the compiler
backends. A conforming implementation consumes `lang/conformance/manifest.edn`,
runs every declared case for its supported targets, produces the declared
negative results, and reports unsupported targets explicitly. Silent fallback
to another backend or reader target is non-conforming.

Signed release tags and trust-store verification are owned by
`kotoba-lang/release`. Updating `lang/version-policy.edn` alone does not prove a
profile has been released.
