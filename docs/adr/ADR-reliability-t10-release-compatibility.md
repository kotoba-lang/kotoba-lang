# ADR: Reliability T10.1–T10.3 — release binds profile + CI compatibility gate

- Status: Accepted
- Date: 2026-07-28
- WBS: T10.1 / T10.2 / T10.3

## Decision

1. **T10.1** — `lang/version-policy.edn` carries
   `:release/language-profile` and `:release/package-contract` for
   `:release/current`. Signed release tags **bind** `:language-profile` in
   addition to version/commit/tree/source-root/issued-at.  
2. **T10.2** — `clojure -M:compatibility` validates policy and emits a
   deterministic compatibility report for the current release (or explicit
   args). CI (`ci.yml`) runs this and uploads `compatibility-report.edn`.  
3. **T10.3** — Release notes must mention `lang/surface-status.edn` diffs when
   the surface changes (`:release-notes` in version-policy). Process, not a
   hard CI fail yet.

## Evidence

- `kotoba.lang.version-policy-test`  
- `clojure -M:compatibility` exit 0 on main
