# ADR-w6-t84-host-parity-critical-fixtures

- Status: Accepted
- Date: 2026-07-28

## Context

Reliability WBS **T8.4** asks to expand `lang/host-parity.edn` L5 conformance
cases for critical imports. Runtime loads `resources/kotoba/lang/host-parity.edn`
first; that copy had drifted behind `lang/host-parity.edn` (missing required
imports / browser-profile / most import rows).

## Decision

1. **Sync** resources from `lang/host-parity.edn` (SSoT = lang; resources is the
   classpath projection).
2. **Expand** `:conformance :cases` for critical crypto/http/kagi/transport/llm
   imports (multi-host where applicable).
3. **Honesty:** `:component-link` remains outside linkable-statuses — fixtures
   assert `:capability-absent` for jvm/node `http-get` until signed AOT packaging
   makes component-linked providers first-class.

## Non-claims

- Does not implement live host runners in kototama/wasm-webcomponent (remaining T8.4).
- Does not flip kit readiness `:signed-wasm`.

## Evidence

- `lang/host-parity.edn` + `resources/kotoba/lang/host-parity.edn`
- `test/kotoba/lang/host_parity_test.clj` (45 expanded cases green)

## Related

- Reliability WBS T8.4
- ADR-2607180900 host-parity L5
