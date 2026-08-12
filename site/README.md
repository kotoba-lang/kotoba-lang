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
lang/surface-status.edn         lang/code-identity.edn
lang/capability-semantics.edn   lang/safety-qualification.edn
lang/elaboration-pipeline.edn
lang/docs-release.edn           lang/diagnostics.edn
lang/cli.edn                    lang/conformance/stdlib/manifest.edn
docs/user-validation.edn        docs/search-index.edn
```

Change the spec and the page changes with it. The page cannot drift into
claiming more than the spec claims, which is the property a safety-oriented
language's marketing page most needs to have.

The page also exposes the four checked documentation routes—learn, use,
implement, and evaluate—from `docs/authority-map.edn`. The prose documents are
linked rather than copied into the landing page. Run `nbb scripts/check-docs.cljs`
before regenerating so moved authorities and broken reader paths fail closed.
Run `nbb scripts/generate-docs-reference.cljs --check` first so the embedded
search corpus and generated Markdown cannot lag those authorities.

## Regenerate

Run from the **repository root**, with the design-system repos checked out as
west siblings (`orgs/kotoba-lang/*`):

```sh
nbb --classpath "../shitsuke/src:../css/src:../html/src:../liquid-glass-ui/src:../kotoba-ui/src:../byoubu/src:../byoubu-ui/src:../kotoba-kir/src:../kotoba-hir/src" \
    site/generate.cljs
```

`kotoba-kir` and `kotoba-hir` are on that classpath because shitsuke moved its
raw-text safety check into a compiled `.kotoba` decision core, so
`shitsuke.hiccup` now loads `kotoba.kir`, which loads `kotoba.hir`. They are
required to *load* the design system, not merely to call it. The generator also
registers the shipped KIR itself — on ClojureScript there is no classpath to
read the artifact from, and `->html` refuses rather than silently skipping the
check on the `[:script ...]` and `[:style ...]` this page emits. It reads that
artifact from `../shitsuke/resources` by default; set `SHITSUKE_RESOURCES` for a
checkout that is not laid out as a west sibling.

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

The page is built with the kotoba-lang design system (`kotoba-ui.core` only:
HIG tokens, liquid-glass material, shell layout). The single theme map at the
top of `generate.cljs` is the only place a color literal is allowed.
