# Kotoba documentation

Kotoba is a small Clojure-shaped language profile for capability-checked
WebAssembly components. This page is the stable entrance to its documentation;
the repository root README remains the project overview.

## Choose a path

| I want to… | Start here | Then read |
|---|---|---|
| install Kotoba and run a program | [Getting started](getting-started.md) | [Tooling reference](reference/tooling.md) |
| learn the language surface | [Language reference](reference/language.md) | [Option/result](lang/option-result-guide.md), [records](lang/record-cookbook.md) |
| implement a compiler or runtime | [Semantics SSoT](lang/semantics-ssot.md) | [grammar](../lang/guest-grammar.edn), [surface status](../lang/surface-status.edn), [conformance](../lang/conformance/manifest.edn) |
| evaluate readiness honestly | [Maturity and comparison](maturity.md) | [coverage evidence](lang/coverage.edn), [engineering model](system-dynamics/kotoba-lang-maturity.md) |

## What is normative?

Kotoba deliberately separates language meaning from implementation and
assurance. The machine-readable routing table is
[`docs/authority-map.edn`](authority-map.edn).

| Contract | Authority |
|---|---|
| language meaning | [`docs/lang/semantics-ssot.md`](lang/semantics-ssot.md) |
| admitted forms | [`lang/guest-grammar.edn`](../lang/guest-grammar.edn) |
| implemented, partial, and excluded surface | [`lang/surface-status.edn`](../lang/surface-status.edn) |
| source-file classification | [`kotoba-core-contracts/lang/profile.edn`](https://github.com/kotoba-lang/kotoba-core-contracts/blob/main/lang/profile.edn) |
| package and lock contract | [`kotoba-core-contracts/lang/package.edn`](https://github.com/kotoba-lang/kotoba-core-contracts/blob/main/lang/package.edn) |
| compiler, launcher, installation, releases | [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) |
| signed release admission | [`kotoba-lang/release`](https://github.com/kotoba-lang/release) |

An ADR explains why a decision was made. It is not automatically the current
reference. When an ADR and a machine-readable authority differ, follow the
authority and file the drift as a documentation defect.

## Documentation contract

Documentation changes must keep all of these true:

1. Every reader path above has one stable start page.
2. Normative ownership is explicit; moved contracts are not copied back.
3. Relative links in the checked document set resolve.
4. Grammar, surface, and elaboration profile versions agree.
5. Maturity claims name their axis. Contract maturity, documentation maturity,
   operational reliability, ecosystem adoption, and production SLOs are not
   interchangeable.

Run the executable documentation gate:

```sh
nbb scripts/check-docs.cljs
```
