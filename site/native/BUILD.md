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
requests that change its diagnostic inputs. It downloads a pinned Amu static
ELF, twelve native library sources, the import-free native runner source and
the existing DDS/grammar byte authorities (25 files), verifying every digest. Linux
project compilation accepts KX64V1 payloads, rather than the scalar `static`
format. The compiler builds a loaderless ELF runner with `kx64-runner` and
the observer checks its native result receipts. Current-source project
compilation receives the explicit bounded capability list; the older source
profile does not implement that option. No compiler flags or admission checks
are patched in memory.

It runs a negative-number float comparison, scoped file reads and the sitemap
unit/mutation pair as native programs, then observes compilation of all 52
actual producer closures. Python stages bytes and records native process
results. No CLJK, package install, C compiler, emulator or publication is run.

```sh
python3 site/native/fetch_linux_observation.py --output /new/inputs
python3 site/native/observe_linux.py --inputs /new/inputs --output /new/observation
```

This preflight is not a qualified release controller or full-site build.
The immutable old profile is a negative baseline: its native basic checks
must pass and its capability-bearing producer projects must all refuse E7101
without artifacts. Only the current profile may proceed to full generation.
The report
keeps producer compilation, executed verdicts, full-site generation and
publication as separate fields, including failures and private diagnostics.
The pinned upstream Linux compiler is itself labelled a candidate. A passing
observation must not enable the production Workers Builds trigger. The full
sequence below separately verifies rendering, locale/catalog gates and output
parity; a qualified native release controller is still required for automatic
publication. The independent local CLJK route above remains available.

### Experimental complete Linux build

`--linux-inputs` explicitly selects `LinuxBuild`, the pinned current-source
compiler, its self-built loaderless ELF KX64 runner, and `input_linux.kotoba`
as the source of the `site.native.input` module. This adapter translates the
site's logical leading slash to a relative request under the granted cwd;
the default Mac adapter keeps the FD3 absolute spelling. Neither retries a
refused operation with another provider. Text and binary reads use the same
adapter. All authority parsing and output composition remain native Kotoba.

```sh
python3 site/native/fetch_linux_observation.py --profile current-source --output /new/inputs
python3 site/native/build_site.py --linux-inputs /new/inputs --compiler /new/inputs/amu.elf \
  --libraries /new/inputs/lib --grammar /new/inputs/grammar --output /new/build --jobs 4
python3 site/native/verify_site.py --build /new/build
python3 site/native/compare_native_candidate.py --build /new/build
python3 site/native/qualify_linux.py --build /new/build --inputs /new/inputs \
  --libraries /new/inputs/lib --grammar /new/inputs/grammar
python3 site/native/check_release_failures.py --build /new/build
```

The cloud workflow performs this sequence only after its current-source
preflight passes, retaining private failures and the candidate. The byte
comparison covers all 189 files against the already published Mac receipt;
the same native page/catalog predicates cover 119 pages and 16 catalogs with
mutation, bad-path, missing-landmark/key and small-source controls. A partial
native stdout is never admitted as a successful fragment. `publish_site.py`
explicitly refuses Linux candidates while publication/controller qualification
is open.

### Observed cloud results — 2026-11-05

GitHub Actions run [41971823396](https://github.com/kotoba-lang/kotoba-lang/actions/runs/41971823396)
at source `2002873bbc19f43c034251fb03c8c1798b24dc68` passed both profiles.
The current-source profile executed all four native preflight checks, compiled
52/52 producer closures, generated 17 locales / 189 files, matched every byte
of the published Mac candidate, and passed 119 page / 16 catalog native checks
and the real partial-output refusal controls. Its full generation/qualification
step took 30m50s with four locale workers on Linux x86-64, four logical CPUs
and 16,760,344,576 bytes RAM. This is one configuration-specific observation.

The earlier two-worker run [41965504624](https://github.com/kotoba-lang/kotoba-lang/actions/runs/41965504624)
also passed the full sequence (52m15s). Its artifact omitted four `.well-known`
files because of GitHub's default hidden-file exclusion. The final workflow
explicitly includes hidden files; independent artifact readback verified all
189 candidate files plus the 56 native program/runner/source-lock bindings.
The dated receipt is `docs/evidence/native-site-linux-cloud-20261105.json`.

These results qualify the bounded generation/validation observation, not the
upstream compiler as a whole or a native automatic release controller. They
do not establish Cloudflare integrated Builds compatibility: that environment
has a separate [20-minute build limit](https://developers.cloudflare.com/workers/ci-cd/builds/limits-and-pricing/).
No Workers Builds configuration or production trigger is created by this
workflow. The explicit local CLJK route remains unchanged.
