# Security Policy

## Reporting a vulnerability

Use **GitHub private vulnerability reporting** on this repository — the
*Security* tab, *Report a vulnerability*. It is enabled, so the button is
really there; if it ever is not, that is itself worth reporting.

Do not open a public issue for a suspected vulnerability, credential leak or
privacy incident.

Include the affected revision, reproduction steps, and observed impact. **Do
not include real credentials, tokens, keys or personal data** in a report — a
path and a description are enough, and a report is not a safe place to put the
thing you are reporting about.

## What in this repository is security-relevant

Kotoba's claim is a boundary — *AI writes freely, Kotoba draws the boundary* —
so the highest-severity class is **anything that crosses the boundary the
language says it enforces**: a capability obtained without a grant, ambient
authority reachable from guest code, an effect that escapes the declared effect
set, or a sandbox escape from the Wasm host.

A defect here is more serious than the same defect in an ordinary language,
because the containment is the product.

## Post-quantum-by-default policy

Every newly admitted Kotoba cryptographic boundary must include a named
post-quantum construction and reject downgrade. Confidentiality uses a hybrid
classical + ML-KEM suite; signatures and publication admission retain a
classical proof only alongside ML-DSA. Post-quantum protection is therefore a
design prerequisite, not a feature applications may silently omit.

Development-only legacy paths do not define this floor and are not a migration
requirement. External TLS and authenticator-native WebAuthn algorithms remain
outside the claim unless separately qualified.

## What is not claimed

This repository carries **no third-party security certification**. There is no
SOC 2 report, no ISO/IEC 27001 certificate and no ISMAP registration covering
it, and none is implied by whatever checks run here.

The workspace-level assurance position — which controls have design evidence,
which have implementation evidence, and which have no operating evidence at all
— is recorded in [`kotoba-lang/security`](https://github.com/kotoba-lang/security).
Read the current figures there with

```sh
nbb --classpath src scripts/check-crosswalk.cljs
```

rather than quoting a number from this file, which would be stale the moment it
was written.
