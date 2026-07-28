# Grade A release and compatibility policy

`lang/version-policy.edn` is the machine-readable authority for:

- strict SemVer release identifiers;
- active, deprecated, removed, and unsupported language-profile versions;
- supported package-contract versions;
- a minimum 180-day deprecation window;
- major-version requirements for removals;
- signed tag naming and content bindings;
- deterministic compatibility reports.

Release-tag signatures bind version, commit, tree, source-root digest, and
issuance time. Verification requires an active signer from an external trust
store and Ed25519 proof; changed tags, versions, commits, trees, roots,
signatures, or revoked signers fail closed. Package manifests and dependency
lock entries use the same strict SemVer syntax.

```sh
clojure -M:compatibility 4 1 0.4.0
clojure -M:test -n kotoba.lang.version-policy-test \
  -n kotoba.lang.package-contract-test
```

The report is stable-order data suitable for CI and tooling rather than
human-only prose. The protected release workflow remains part of the global
release hard gate; it does not weaken this compatibility contract.

## Operational gates (T10.1–T10.3)

| Gate | Command / artifact |
|---|---|
| Policy + current release | `clojure -M:compatibility` |
| Explicit triple | `clojure -M:compatibility 4 1 0.4.0` |
| CI | `.github/workflows/ci.yml` uploads `compatibility-report.edn` |
| Tag content | binds include `:language-profile` (see `lang/version-policy.edn`) |
| Release notes | mention `lang/surface-status.edn` / surface-matrix when surface changes |

Current release binds **language-profile 4** (active) and package-contract **1**.

