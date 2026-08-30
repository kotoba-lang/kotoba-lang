# site — kotoba-lang.org

The language's public static site. It has no third-party runtime dependency.
Small inline scripts filter the generated reference index and run the Play
surface; Play fetches only the same-origin, digest-bound Wasm artifact. Queries
stay in the browser and no telemetry is emitted.

## What makes this page different from a README

`generate.cljs` **derives the language claims from this repository's own authority files**
rather than restating them in prose. The eight safety claims and their residual
risks, the deliberately-absent surface, the elaboration pipeline, the deny
rules, the Component Model / WASI pins and the identity non-goals are all read
at build time from:

```
lang/safety-claims.edn          lang/wasm-component-platform.edn
lang/surface-status.edn         lang/elaboration-pipeline.edn
lang/docs-release.edn           lang/product-defaults.edn
docs/search-index.edn
```

Change the spec and the page changes with it. The page cannot drift into
claiming more than the spec claims, which is the property a safety-oriented
language's marketing page most needs to have.

The layout is mobile-first: narrow screens are the base CSS, while wider header
and action layouts are added with `min-width` queries. Public navigation points
to the language docs, the normative protocol and spec repositories, Kotobase,
Murakumo, and the distinct language-authority and implementation repositories.

The first screen is deliberately a 30-second narrative: **Why → What → Proof →
Architecture**. It states the product thesis, shows the language feel, separates
internal production dogfooding from customer traction, and then carries the
boundary through checked KIR, capability/effect admission, content-addressed
artifacts, and host enforcement. The 33-core figure is an explicitly bounded
internal-operations statement, not a language qualification claim, customer
count, paid-adoption metric, or revenue claim. Product safety claims remain
derived from the repository authorities above.

The page also retains the generated local documentation search: its embedded
index is built from the repository authority and no query leaves the browser.
The developer journey connects documentation, the exact checked source sample,
a digest-bound browser Play artifact, libraries, roadmap, public community
channels, the generated engineering blog, and related cloud products.
`site/assets/play/` contains the source, Wasm, provenance, and publication
manifest copied to `site/dist/play/`; the UI explicitly labels this as a
precompiled example rather than an arbitrary in-browser compiler.
The displayed Kotoba source is tokenized at build time by
`kotoba-lang/grammar`'s portable `kotoba.grammar.highlight/tokenize` API; the
site does not maintain a tokenizer, keyword list, or forbidden-form list.
Stable TextMate-compatible scopes are adapted to presentation classes, and the
generator fails if concatenating the highlighted tokens does not reproduce the
exact `.kotoba` source. The generated TextMate artifact remains digest-checked
as the editor scope contract. The deployed page needs no client-side syntax
highlighter or third-party runtime dependency.
`site/dependencies.edn` records the grammar revision, artifact digest, scope,
other build-time repositories, and the intentionally small browser runtime
dependency surface. The generator verifies the grammar digest and scope before
rendering and publishes the manifest at `/dependencies.edn`.
The public benchmark section reads the checked-in compile report at
`bench/public-compile-comparison/latest.json` and the bounded native comparison
summary at `bench/public-runtime-comparison/latest.json`. The generator copies
these reports plus the stage-separated developer-loop report at
`bench/public-end-to-end-comparison/latest.json` and the string, collection,
allocation, I/O, concurrency, and real-application report at
`bench/public-domain-comparison/latest.json` alongside `llms.txt`,
`llms-full.txt`, and the executable agent
quickstart. Compile-startup evidence and steady-state runtime evidence remain
visibly separate from dependency resolution, checking, clean/no-change builds,
first-result latency, and workload-domain behavior. Unsupported capabilities
remain reasoned N/A rather than zero. An unqualified host-load result cannot
become a fastest claim through presentation.

## Regenerate

Run from the **repository root**, with `jp-go-digital-design-system`, `grammar`,
`css`, and `html` checked out as west siblings (`orgs/kotoba-lang/*`):

```sh
JP_GO_DDS_ROOT=../jp-go-digital-design-system \
KOTOBA_GRAMMAR_ROOT=../grammar \
KOTOBA_IDENTITY_ROOT=../identity \
nbb --classpath "../grammar/src:../jp-go-digital-design-system/src:../css/src:../html/src" site/generate.cljs
```

The generator reads the vendored DADS stylesheet from
`JP_GO_DDS_ROOT/resources/jp_go_dds/dds.css` and inlines it. The resulting page
has no external font, script, analytics, or design-system request at runtime;
the small documentation-filter and Play scripts are inline.

The header uses the same six-circle `K O T O B A` artwork as the implementation
repository README. Its authoritative source is
`kotoba-lang/kotoba@cfdc08b71c4053f80d42a24d7bb418204a84b369:docs/assets/header.png`
(SHA-256 `76040326b828217845181068d3cf14ab856f671c5525bf8eb1182c40c4116a35`).
`site/assets/kotoba-wordmark.png` is a 480 x 68 mobile-sized derivative
(SHA-256 `0126a8791d4181d102892215e50379014af6bc7e36bbee7c5aa5002a6f25778a`).
The generator copies that local asset into `dist/`, so the deployed page makes
no external image request.

Output: `site/dist/index.html`, `site/dist/blog/index.html`,
`site/dist/legal/index.html`, the Play artifact and evidence, the wordmark,
AI-agent text surfaces, and the raw benchmark JSON (committed, so a clean
checkout can deploy without running the generator).

The generator also publishes identity's canonical external-trust artifacts at
`/schemas/trust-profile/v1`,
`/policies/trust/human-passport/itonami-v1.json`, and
`/policies/trust/eas/kotobase-v1.json`, and
`/policies/trust/erc8004/murakumo-v1.json` plus its detached
`.signature.json` quorum envelope. The source remains
`kotoba-lang/identity`; regeneration fails if that west sibling is absent, so a
stale handwritten site copy cannot silently deploy.

**The committed artifact is a deploy input, not a build by-product**: `wrangler
deploy` here has no build step, so whatever is in `dist/` at deploy time is what
the live zone serves. Regenerate and check `git diff` before shipping. An
*absent* `dist/` fails loudly, but an *empty* one does not — wrangler reports
"Read 0 files" and uploads them, which on a custom domain replaces the live page
with nothing. Measured 2026-08-13 with wrangler 4.103.0.

## Score it

An unmeasured page is theater. `kotoba-lang/design-quality` scores the actual
rendered HTML against a deterministic HIG/WCAG rubric — no LLM, no browser:

```sh
cd ../design-quality
nbb --classpath src -m design-quality.cli score ../kotoba-lang/site/dist/index.html --min 100
```

Current score: **100.00** (converged, no findings). Do not lower the floor to
make a regression pass.

## Deploy

```sh
cd site
npx wrangler deploy
```

Static assets only — `wrangler.jsonc` declares no Worker script. The zone is
served on the apex and `www`. The Cloudflare account that currently holds the
zone is an infrastructure label, not the published operator.

The published operator on this zone is **Kotoba Labs Inc.** Public contact
is `support@kotoba-lang.org`. Sales contact is Ryo Awai. The footer, `/legal/`,
and `security.txt` name only those facts. Do not publish a Specified
Commercial Transactions Act table, Delaware file number, address, phone, or
any other mailbox from this generator.

The page is built with `jp-go-dds` (the Digital Agency Design System mirror).
Application CSS uses the shared `--hig-*` token contract through
`jp-go-dds.tokens/skin-css`; it does not define a separate palette.
