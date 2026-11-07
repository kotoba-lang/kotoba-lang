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

## Correction (2026-11-07, later the same day)

The first version of this document attributed the stops to pinned dependencies that cannot be cloned. That was
wrong. `kbb` prints those as warnings and carries on; the real stop in 39 of the 40 libraries is
`aliases [:test] declare no :main-opts; nothing to run (this is a classpath-only alias)`, and the remaining one
(kotoba.sysTms) has no `:test` alias. A monorepo library's `:test` alias only adds `test` to the classpath, so
`kbb -M:test` is not how these libraries are run. The measurement method was wrong for this population; the
"0 of 40" below says nothing about whether the libraries work.

## Result

Run 1, `kbb -M:test` in each library directory: 0 of 40 ran a test, because there is nothing for that command to
run (see the correction above). Do not read this as a failure of the libraries.

Run 2, the library's own entry point `kbb --backend sci --classpath src:test run-tests.cljk` from the library
directory. Only 5 of the 40 sampled libraries have a `run-tests.cljk` (115 of the 926 in the population, about
12%); the other 35 have no runner entry point in the library and were not run.

| library | exit | result |
|---|---|---|
| kotoba.ioIpniSpecs | 0 | 39 tests, 138 assertions, 0 failures, 0 errors |
| org.ietf.cbor | 1 | 25 tests, 120 assertions, 0 failures, 1 error: `ENOENT lib/org.ietf.cbor/test/cbor/golden.edn` (a path relative to the repository root; the run used the library directory) |
| kotoba.pay | 1 | stopped: `Could not find namespace: kotoba.lang.base-l2.paymaster` (sibling library not on the `src:test` classpath) |
| kotoba.scheduler | 1 | stopped: `Could not find namespace: kotoba.lang.async` (same) |
| org.w3.woff | 1 | stopped: `Could not find namespace: brotli.core` (same) |

## What this does and does not show

- Of 40 sampled libraries, one ran its suite cleanly. Four runs were affected by how they were launched (working
  directory, classpath). The other 35 were not run because no entry point exists in the library.
- The canonical way the monorepo runs library tests was not established here. The repository's own
  `run-tests.cljk` covers the portable slice at the root; the retired JVM and JS CI jobs are archived under
  `docs/retired/native-ci-20261024/` in the monorepo; the supported route is Amu selfhost.
- Nothing here shows that the libraries are complete or broken. The question "are most libraries implemented in
  cljk" is not answered by this measurement.
- Separate from running tests: 22 of the 36 distinct `amu` commits pinned from `lib/` (behind 74 libraries)
  declare direct coordinates of kotoba-lang repositories that no longer exist (abi, provider, kotoba-wasm,
  kotoba-verifier, kotoba-component, kotoba-kir, kotoba-hir, io-ipld, kotoba-object, tender-native and others).
  That is an inventory of stale pins. It was not shown to be what stops anything.
- The JVM route was not available for `.cljk`. No second runtime was compared.

## Per library (run 1, `kbb -M:test`)

The last column is the first line the log matched; dependency-clone lines are warnings, not the cause of the stop.

| library | exit | seconds | first matched line |
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
