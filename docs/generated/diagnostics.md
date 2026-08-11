# Generated Kotoba diagnostic-code reference

> Generated from [`lang/diagnostics.edn`](../../lang/diagnostics.edn). Do not edit by hand.

Coverage is `bounded-contract-surface`, not an exhaustive list of compiler or host errors. Codes are stable within a language profile; message wording is informational.

## `:command/unknown`

Phase: `cli`.

The requested command is not in the public CLI contract.

**Recovery:** Use a command generated from lang/cli.edn.

Authority: [`src/kotoba/cli.cljc`](../../src/kotoba/cli.cljc).

## `:contract/invalid`

Phase: `cli`.

The CLI contract failed structural validation.

**Recovery:** Inspect the structured :errors collection; do not dispatch the command.

Authority: [`src/kotoba/cli.cljc`](../../src/kotoba/cli.cljc).

## `:version/unsupported`

Phase: `compatibility`.

The requested language or package contract version is unknown.

**Recovery:** Select a version listed under :supported in lang/version-policy.edn.

Authority: [`src/kotoba/lang/version_policy.clj`](../../src/kotoba/lang/version_policy.clj).

## `:version/removed`

Phase: `compatibility`.

The requested contract version has been removed.

**Recovery:** Migrate to the active version before compiling or running.

Authority: [`src/kotoba/lang/version_policy.clj`](../../src/kotoba/lang/version_policy.clj).

## `:version/deprecation-expired`

Phase: `compatibility`.

The compatibility window for a deprecated version has expired.

**Recovery:** Apply the migration named by the version policy.

Authority: [`src/kotoba/lang/version_policy.clj`](../../src/kotoba/lang/version_policy.clj).

## `:release/invalid-semver`

Phase: `release`.

A release identifier is not strict SemVer.

**Recovery:** Use MAJOR.MINOR.PATCH with an optional valid pre-release or build suffix.

Authority: [`src/kotoba/lang/version_policy.clj`](../../src/kotoba/lang/version_policy.clj).

## `:docs/no-release-bound-profile`

Phase: `documentation`.

No published implementation evidence binds the active language profile.

**Recovery:** Keep the public default blocked until a signed release envelope binds the implementation and profile.

Authority: [`lang/docs-release.edn`](../../lang/docs-release.edn).

## `:docs/link-missing`

Phase: `documentation`.

A checked document points to a missing local target.

**Recovery:** Restore the target or update the authority map and regenerate the reference.

Authority: [`scripts/check-docs.cljs`](../../scripts/check-docs.cljs).

## `:docs/profile-version-drift`

Phase: `documentation`.

Grammar, surface, and elaboration authorities disagree on the language profile.

**Recovery:** Reconcile the authorities before publishing documentation.

Authority: [`scripts/check-docs.cljs`](../../scripts/check-docs.cljs).

## `:docs/generated-drift`

Phase: `documentation`.

A committed generated reference does not match its machine authority.

**Recovery:** Run nbb scripts/generate-docs-reference.cljs and commit the result.

Authority: [`scripts/check-docs.cljs`](../../scripts/check-docs.cljs).

## `:docs/validation-result-invalid`

Phase: `documentation`.

A user-validation observation is incomplete or overclaims an external result.

**Recovery:** Record participant class, task, outcome, evidence, and observed time.

Authority: [`scripts/check-docs.cljs`](../../scripts/check-docs.cljs).

