# site — kotoba-lang.org

The language's public page. One static, self-contained document with no network
runtime dependency. A small inline script filters the generated reference
index; queries stay in the browser and no telemetry is emitted.

## What makes this page different from a README

`generate.cljs` **derives the language claims from this repository's own authority files**
rather than restating them in prose. The eight safety claims and their residual
risks, the deliberately-absent surface, the elaboration pipeline, the deny
rules, the Component Model / WASI pins and the identity non-goals are all read
at build time from:

```
lang/safety-claims.edn          lang/wasm-component-platform.edn
lang/surface-status.edn         lang/elaboration-pipeline.edn
lang/docs-release.edn           docs/search-index.edn
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
The public benchmark section reads the checked-in raw report at
`bench/public-compile-comparison/latest.json`; the generator copies that report
alongside `llms.txt`, `llms-full.txt`, and the executable agent quickstart.

## Regenerate

Run from the **repository root**, with `jp-go-digital-design-system`, `css`, and
`html` checked out as west siblings (`orgs/kotoba-lang/*`):

```sh
JP_GO_DDS_ROOT=../jp-go-digital-design-system \
nbb --classpath "../jp-go-digital-design-system/src:../css/src:../html/src" site/generate.cljs
```

The generator reads the vendored DADS stylesheet from
`JP_GO_DDS_ROOT/resources/jp_go_dds/dds.css` and inlines it. The resulting page
has no external font, script, analytics, or design-system request at runtime;
the small documentation-filter script is inline.

The header uses the same six-circle `K O T O B A` artwork as the implementation
repository README. Its authoritative source is
`kotoba-lang/kotoba@cfdc08b71c4053f80d42a24d7bb418204a84b369:docs/assets/header.png`
(SHA-256 `76040326b828217845181068d3cf14ab856f671c5525bf8eb1182c40c4116a35`).
`site/assets/kotoba-wordmark.png` is a 480 x 68 mobile-sized derivative
(SHA-256 `0126a8791d4181d102892215e50379014af6bc7e36bbee7c5aa5002a6f25778a`).
The generator copies that local asset into `dist/`, so the deployed page makes
no external image request.

Output: `site/dist/index.html`, the wordmark, AI-agent text surfaces, and the
raw benchmark JSON (committed, so a clean checkout can deploy without running
the generator).

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

Static assets only — `wrangler.jsonc` declares no Worker script. The zone is in
the `ai-gftd-cloud` Cloudflare account, served on the apex and `www`.

The page is built with `jp-go-dds` (the Digital Agency Design System mirror).
Application CSS uses the shared `--hig-*` token contract through
`jp-go-dds.tokens/skin-css`; it does not define a separate palette.
