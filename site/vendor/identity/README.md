# Vendored trust contracts (from the kotoba-lang/identity repository, commit f10ee2c800bd8c5d2ca0e00d754efc62dc20f716)

The site publishes five files from identity as a deterministic HTTPS projection (see generate.cljk,
"identity-root"). The identity repository was consolidated into kotoba-lang/kotoba lib/kotoba.identity and then deleted,
so the pinned commit can no longer be fetched. These are the exact bytes the production site served before this change
(build-dependencies.json had pinned that commit), copied from a local clone of it.

The monorepo copy has moved on: lib/kotoba.identity publishes the EAS policy on Base (chainId 8453, new schema UID and
attester, issuedAt 2026-10-07) instead of Optimism (chainId 10). Advancing the published policy to it is a separate,
deliberate rollout; do it by replacing these files from lib/kotoba.identity and regenerating, not as a side effect of a
dependency change.
