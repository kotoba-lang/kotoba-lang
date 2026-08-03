# site — kotoba-lang.org

The language's public page. One static, self-contained document: no build step
and no runtime JavaScript for anyone visiting it.

## What makes this page different from a README

`generate.cljs` **derives the page from this repository's own authority files**
rather than restating them in prose. The eight safety claims and their residual
risks, the deliberately-absent surface, the elaboration pipeline, the deny
rules, the Component Model / WASI pins and the identity non-goals are all read
at build time from:

```
lang/safety-claims.edn          lang/wasm-component-platform.edn
lang/surface-status.edn         lang/code-identity.edn
lang/capability-semantics.edn   lang/safety-qualification.edn
lang/elaboration-pipeline.edn
```

Change the spec and the page changes with it. The page cannot drift into
claiming more than the spec claims, which is the property a safety-oriented
language's marketing page most needs to have.

## Regenerate

Run from the **repository root**, with the design-system repos checked out as
west siblings (`orgs/kotoba-lang/*`):

```sh
nbb --classpath "../shitsuke/src:../css/src:../html/src:../liquid-glass-ui/src:../kotoba-ui/src:../byoubu/src:../byoubu-ui/src" \
    site/generate.cljs
```

Output: `site/dist/index.html` (committed, so a clean checkout can deploy
without running the generator).

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
