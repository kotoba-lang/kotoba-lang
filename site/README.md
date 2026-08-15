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

The market-timing cards cite primary public sources (Stack Overflow and
Anthropic). They are explicitly labelled as market evidence, not language
qualification evidence. Product and safety claims remain derived from the
repository authorities above. The page also retains the generated local
documentation search: its 47-entry index is embedded at build time, and no
query leaves the browser.

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

Output: `site/dist/index.html` (committed, so a clean checkout can deploy
without running the generator).

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
