# Public search metadata and agent onboarding

2026-09-08. Publish crawlable canonical pages, social previews and a short Markdown agent entry across the Kotoba stack. Connect language installation and executable validation, graph CLI/auth/MCP, and Cloud capability discovery. Public documents require no credentials; operation authority remains unchanged.

Validation: metadata, JSON-LD parsing, sitemap XML, public document links and all three default design audits pass (100/100). Kotoba selfhost check and compile/execute of hello.kotoba return 42; Kotobase health and MCP config work. Kotobase public-page suite: 46 tests / 492 assertions; Cloud: 51 tests / 492 assertions, boot manifest tamper checks and Worker smoke pass. Existing unrelated Kotobase full-release/admin/DID gaps remain recorded in the prior release ADR.

The native CLI package-add path returned runtime/internal-error / IllegalArgumentException for the profile-pinned reference package. Both Cloud agent documents explicitly record this gap; package installation or execution is not claimed verified. The first executable path is the validated zero-authority Wasm program. Hosted Cloud apply remains unavailable.
