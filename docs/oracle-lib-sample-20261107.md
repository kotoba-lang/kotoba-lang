# Oracle sample of the monorepo libraries (measured 2026-11-07)

One-off local measurement under the oracle rules (JUN-579). Not CI, not a gate, not a qualification of the
native route. It replaces the invalid population of `oracle-cljk-sample-20261107.md` (stale local clones) with
the place the code lives now: `lib/` of `kotoba-lang/kotoba`.

## Method

- Tree: `kotoba-lang/kotoba` at `91ed14c090c90ceff833a868f1685c8840503f76` (origin/main), `lib/` checked out.
- Population: 926 of the 982 libraries under `lib/` hold tracked `.cljk` or `.cljc` source (896 of those also
  have a `*_test.*` file; 323 libraries hold `.kotoba`; 631 of the 926 have no `.kotoba`).
- Sample: 40 of the 926, `random.Random(20261107).shuffle`, first 40, sorted.
- Run: `kbb -M:test` in each library directory (`nbb v1.4.208` engine; also present: Clojure CLI 1.12.5.1654,
  JDK 21.0.1). The sources are `.cljk`, which the JVM cannot read. 300 s timeout. Dependencies are fetched from
  `github.com/kotoba-lang` by `kbb`. Machine: macOS arm64.

## Result

**0 of 40 libraries ran a test.** All 40 stopped before any test ran.

| Reason | Libraries |
|---|---|
| transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned | 28 |
| transitive git dependency `io.github.kotoba-lang/process-exec` cannot be cloned | 5 |
| transitive git dependency `io.github.kotoba-lang/io-ipld` cannot be cloned | 2 |
| `:test` alias declares no `:main-opts` | 2 |
| transitive git dependency `io.github.kotoba-lang/dag-cbor` cannot be cloned | 1 |
| transitive git dependency `io.github.kotoba-lang/org-w3-did` cannot be cloned | 1 |
| no `:test` alias in deps.edn | 1 |

Every `transitive git dependency` stop is a coordinate that is not declared in the library's own `deps.edn` or
`nbb.edn` (a search of `lib/` for `kotoba-io-len` in those files finds none): `kbb` follows the pinned
dependencies of the libraries a library depends on, and those pins still name standalone repositories that no
longer exist on GitHub (see the correction in `oracle-cljk-sample-20261107.md`).

## What this does and does not show

- It shows that `kbb -M:test` cannot run the monorepo libraries' tests today. It does not show that the code is
  broken: nothing executed, so there is no pass or fail count for any library.
- A different runner, or overriding the dead coordinates with the monorepo's own paths, could change the result.
  Neither was tried here; the earlier repair work found that doing it by hand per repository needs many overrides.
- 40 of 926 is a sample. All 40 failing for the same few reasons makes a systematic cause (the dead pins), not a
  per-library cause, the likely explanation.
- The JVM route was not available for `.cljk`. No second runtime was compared.

## Per library

| library | exit | seconds | stopped because |
|---|---|---|---|
| kotoba.designQuality | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.dxf | 64 | 5 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.eduBerkeleySpice | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.ioIpniSpecs | 64 | 11 | transitive git dependency `io.github.kotoba-lang/dag-cbor` cannot be cloned |
| kotoba.kamiEizoGrade | 64 | 6 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.kamiEngineCad | 64 | 5 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.kamiMangakaRender | 64 | 1 | transitive git dependency `io.github.kotoba-lang/process-exec` cannot be cloned |
| kotoba.kamiMangakaSceneAuthor | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.kamiOngakuPluginHost | 64 | 5 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.kip | 64 | 12 | transitive git dependency `io.github.kotoba-lang/io-ipld` cannot be cloned |
| kotoba.kotobaHir | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.kuraNode | 64 | 6 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.madoguchi | 64 | 5 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.mailArchive | 64 | 1 | `:test` alias declares no `:main-opts` |
| kotoba.nagare | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.newsfeed | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.nio | 64 | 5 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.officeStyle | 64 | 1 | transitive git dependency `io.github.kotoba-lang/process-exec` cannot be cloned |
| kotoba.okaimono | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.onetime | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.ongaku | 64 | 1 | transitive git dependency `io.github.kotoba-lang/process-exec` cannot be cloned |
| kotoba.pathfind | 64 | 5 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.pay | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.pedigree | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.providerIncidence | 64 | 12 | transitive git dependency `io.github.kotoba-lang/org-w3-did` cannot be cloned |
| kotoba.quickjs | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.scheduler | 64 | 5 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.session | 64 | 1 | transitive git dependency `io.github.kotoba-lang/process-exec` cannot be cloned |
| kotoba.shomen | 64 | 6 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| kotoba.sysTms | 64 | 1 | no `:test` alias in deps.edn |
| kotoba.torrent | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| org.ieee.mkdir | 64 | 5 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| org.ietf.cbor | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| org.iso.h264 | 64 | 2 | transitive git dependency `io.github.kotoba-lang/process-exec` cannot be cloned |
| org.msgpack.core | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| org.openehr.adl | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| org.w3.nquads | 64 | 4 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| org.w3.rdfCanon | 64 | 6 | transitive git dependency `io.github.kotoba-lang/kotoba-io-len` cannot be cloned |
| org.w3.webauthn | 64 | 12 | transitive git dependency `io.github.kotoba-lang/io-ipld` cannot be cloned |
| org.w3.woff | 64 | 0 | `:test` alias declares no `:main-opts` |
