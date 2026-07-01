# Kotoba Foundational Stdlib Gates

The stdlib layer (the `:stdlib` track in `docs/lang/coverage.edn`) is M4
("CI gate") when **every shipped stdlib library's test suite is green** on its
own CI. This file enumerates those gates so a human or a superproject CI job
can run them as one set.

Each library is a plain-git child repo under `kotoba-lang/<name>` with the same
`deps.edn :test` alias, so the per-lib gate is uniform:

```sh
clojure -M:test
```

## The gate set

Foundational (Layer 1–4) libs — 12:

```sh
for n in coll spec json wit async time fs http io test fmt lsp; do
  (cd orgs/kotoba-lang/$n && clojure -M:test)
done
```

Composite consumer libs — 3:

```sh
for n in scheduler store lint; do
  (cd orgs/kotoba-lang/$n && clojure -M:test)
done
```

## CI contract

- Each library's own GitHub Actions workflow (`.github/workflows/ci.yml`) runs
  `clojure -M:test` on JDK 17 and 21. A green run there *is* that lib's M4
  evidence.
- The `:stdlib` track is M4 when every shipped lib's latest CI run is
  `success`. Track maturity in `docs/lang/coverage.edn` `:stdlib :maturity`
  (currently `:m6` — M4 + the M5 consumer provenance + the M6 semver/compat
  policy in `docs/lang/stdlib-versioning.md`).
- Composite libs depend on foundational libs by **first-party git SHA**, so a
  composite lib's CI transitively proves the pinned SHAs resolve and test
  together. (langchain, a non-stdlib vertical, additionally consumes `json` —
  the strongest M5 evidence; see `coverage.edn`.)

## Notes

- `kotoba-lang/registry` is `:m0 :planned` and intentionally **not** in the
  gate set — it is deferred to the `:packages` CID-lock track
  (`docs/lang/package-rules.md`, `ADR-kotoba-package-cid-lock`).
- This gate set does **not** cover the language *profile* (that is
  `docs/lang/gates.md`, the Rust/cargo profile conformance suite). The stdlib
  is a separate track with its own versioning (`docs/lang/stdlib-versioning.md`).
