# Native static site build

`build_site.py` generates the current 189-file site (133 HTML files, including
2 authored static anatomy pages) on macOS ARM64. All authority parsing,
localization, CSS/script extraction, JSON rendering and HTML composition run
in compiled Kotoba programs. Python stages bytes, computes digests and observes
native process exit codes. It does not interpret CLJK or render the site.

The supported toolchain is the official Amu root artifacts from immutable
commit `757fd5eca0a66ac0167636e8ce84c76fa0fa0797`:

- `amu-full-root.macho`: SHA-256 `2b4ac12a77f156ccc2790eb0cf5453e57b9cac8669a2dce9779095579f7994a2`
- `kotoba-packager-root.macho`: SHA-256 `e95284408f091c8979ef74ece57116a4df2250e61e42074da5dc95ca30264142`

Supply existing verified tools, the canonical shared `kotoba/lib` sources and
the pinned grammar checkout. `build-library-pins.json`, the existing grammar/
DDS dependency descriptors, native source locks and source-level authority
pins reject drift. This is a bounded implementation of the current site,
not a general CLJK interpreter or qualification of other Amu targets.

```sh
python3 site/native/build_site.py \
  --compiler /absolute/path/amu-full-root.macho \
  --packager /absolute/path/kotoba-packager-root.macho \
  --libraries /absolute/path/kotoba/lib \
  --grammar /absolute/path/grammar \
  --output /absolute/path/new-build-directory --jobs 3
python3 site/native/qualify_site.py --build /absolute/path/new-build-directory \
  --compiler /absolute/path/amu-full-root.macho \
  --packager /absolute/path/kotoba-packager-root.macho
python3 site/native/verify_site.py --build /absolute/path/new-build-directory
python3 site/native/check_release_failures.py --build /absolute/path/new-build-directory
```

The output directory must be new. Each locale uses a private input directory.
Native children run with `PATH=/nonexistent`. Only exit 42 with empty stderr
admits a producer's stdout; failed/partial output is retained only as private
diagnostic evidence. No candidate is published by generation. `receipt.json`
lists every candidate file hash; the observer checks the exact set, canonical
links and document structure. Refusal checks cover changed inputs, invalid
locales and a real article producer that emits its head before refusing its
invalid body, ensuring that partial HTML never becomes an admitted fragment.

An explicitly selected independent reference may be observed with
`verify_site.py --build ... --reference /absolute/path/reference/site/dist`.
This records reference hashes and differences without invoking a renderer.
It is separate from native generation. `--port-reference` may supply separately
recorded source observations for the existing native chrome decisions and the
identity article, which the legacy generator currently emits with an empty
body because its route is absent from `localized-pages`. The observer checks
the full authored identity article, title/description and common controls, and
the corrected sitemap canonical. It checks localized bilingual header/footer
fragments against those independent observations (with the generated Japanese
AIUEOS route linked from dated Japanese pages); authored article content is
compared unchanged. These differences are retained in the observation report.

Generator footer stamps are compared
using an explicit set of native source names; URL spellings are resolved to
the page URL before DOM comparison. Head metadata order is normalized while
CSS/control-script order remains strict; `aria-current` true/page is treated
as equivalent on locale links. English source islands must retain LTR direction
on RTL pages. The existing exact Japanese library lead/navigation corrections
are observed explicitly. Unexplained differences fail observation.

## Retained CLJK route

The owner requested retaining this route on 2026-11-05 while native validation
matures. `site/generate.cljk`, `site/build.cljk`, `site/kbb`, the dependency
locks and locale tests remain. Select it explicitly with `npm run
site:build:cljk`; it regenerates `site/dist` and runs the existing locale tests.
Record tool versions, pinned inputs and output hashes when using it for an
independent comparison. The native builder never invokes this route and has
no fallback to it. It is not a CI route.

## Publication

After an observed candidate and refusal checks pass, use the separate asset
transport. It checks candidate/input hashes and agreement of repository
inputs with freshly fetched `origin/main` before a real upload. A dry run may
inspect an unmerged review branch. Wrangler only uploads static assets; it is
not part of native generation or native-program packaging.

```sh
python3 site/native/publish_site.py --build /absolute/path/new-build-directory \
  --wrangler /absolute/path/node_modules/.bin/wrangler --dry-run
python3 site/native/publish_site.py --build /absolute/path/new-build-directory \
  --wrangler /absolute/path/node_modules/.bin/wrangler
```

The target is the existing assets-only `kotoba-lang-org` Worker in account
`4da88288dc30d9ee257f319d3c33ecf0`, using `site/wrangler.jsonc`. A successful
upload is not live qualification: verify the active deployment, HTTP bytes,
English/Japanese navigation, browser Play and desktop/mobile light/dark
readability. Record the preceding production version for rollback.

Full generation is slow on this profile; this is not a throughput qualification.

There is no qualified Cloudflare Linux build replacement for this macOS
ARM64 profile. The former Workers Builds Node/NBB trigger is removed during
the native cutover; local explicit CLJK builds remain available. Do not add
a hosted fallback or report this local profile as Linux CI qualification.

## Linux observation, before automatic release qualification

`Native site Linux observation` runs on an x86-64 Linux cloud runner for pull
requests that change its diagnostic inputs. It downloads only a pinned Amu
static ELF and twelve pinned native library sources, verifying every digest.
It runs a negative-number float comparison, scoped file reads and the sitemap
unit/mutation pair as native programs, then observes compilation of all 52
actual producer closures. Python stages bytes and records native process
results. No CLJK, package install, C compiler, emulator or publication is run.

```sh
python3 site/native/fetch_linux_observation.py --output /new/inputs
python3 site/native/observe_linux.py --inputs /new/inputs --output /new/observation
```

This is a diagnostic, not a qualified release controller or full-site build.
The current macOS entries read virtual absolute paths under FD3, while Linux
admits real paths under an explicit filesystem root. Native compile success
does not solve that bridge or establish rendered output parity. The report
keeps producer compilation, executed verdicts, full-site generation and
publication as separate fields, including failures and private diagnostics.
The pinned upstream Linux compiler is itself labelled a candidate. A passing
observation must not enable the production Workers Builds trigger. Full-site
rendering, locale/catalog gates, native controller and output parity are still
required. The independent local CLJK route above remains available.
