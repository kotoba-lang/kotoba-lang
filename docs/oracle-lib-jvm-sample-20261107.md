# JVM oracle run of the monorepo library sample (measured 2026-11-07)

One-off local measurement under the oracle rules (JUN-579). Not CI, not a gate, not a qualification of the native
route. It runs the tests of a sample of `lib/` libraries on JVM Clojure through the staging shim
(`docs/oracle-stage-cljc.md`), the first run of these `.cljk` libraries on a runtime other than the nbb engine.

## Method

- Tree: `lib/` of `kotoba-lang/kotoba` at `91ed14c090c90ceff833a868f1685c8840503f76`, taken with `git archive` and
  staged with `scripts/oracle-stage-cljc.py` (33291 files, 11294 renamed).
- Sample: the same 40 of the 926 cljk/cljc libraries as `oracle-lib-sample-20261107.md`
  (`random.Random(20261107)`).
- Run: in each staged library directory, `clojure -A:test -M -e "(require 'ns ...) (clojure.test/run-tests 'ns ...)"`
  with every namespace found in a `*_test.clj` or `*_test.cljc` file under `test/`. Clojure CLI 1.12.5.1654,
  JDK 21.0.1, 300 s timeout, macOS arm64. The library's own `deps.edn` builds the classpath, so a `:git/sha`
  dependency that no longer exists stops the run.
- Only `.clj` and `.cljc` tests are run; a library with only `.cljs` or `.kotoba` tests counts as having none.

## Result

| Outcome | Libraries (of 40) |
|---|---|
| ran, all pass | 17 |
| stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) | 12 |
| stopped: classpath, dependency repository cannot be cloned | 4 |
| ran, with failure or error | 2 |
| stopped: classpath, local versions of `edn` conflict | 2 |
| no test namespaces | 2 |
| stopped: reader error in a test file | 1 |

Of the 40, 17 ran their tests and all passed. The rest are separate stories, listed below; none of the stops is a
test that ran and failed.

## What the stops are

- **`:kotoba/export` metadata (12 of 40).** These libraries load a namespace whose `ns` carries
  `{:kotoba/export [name ...]}` with unquoted symbols (for example `kotoba.bytes`, `kotoba.hir`, `kotoba.json`).
  Clojure evaluates a `ns` attribute map, so each symbol is an unresolved symbol and the namespace does not load.
  The nbb engine does not evaluate it. In the staged tree, 211 `.cljc` source files carry `:kotoba/export`, so this
  is a general JVM portability gap of the cljk sources, not a per-library defect. It was not worked around: the shim
  copies bytes unchanged.
- **Dependency repositories that cannot be cloned (4):** kotoba-hir (3 libraries) and org-w3-did.
- **Local versions of `edn` conflict (2):** kuraNode and madoguchi.
- **Reader error in a test file (1):** org.iso.h264, `decode_cabac_test.clj:80:15`.

## Tests that ran with a failure or error (2)

- kotoba.kamiMangakaSceneAuthor: 15 tests, 70 assertions, 1 failure. The test
  `json-text-differs-between-runtimes-on-whole-doubles` expects `"fov_deg":35.0`; JVM printed `35`. This is a real
  JVM and nbb difference in how a whole double prints, which is what the test is named for.
- kotoba.nagare: 36 tests, 716 assertions, 1 error: `kernel-is-cljc-portable` opens `lib/kotoba.nagare/src/nagare/mesh.cljc`,
  a path relative to the repository root, and the run used the library directory.

## No test namespaces (2)

kotoba.sysTms and org.ieee.mkdir.

## Per library

| library | exit | seconds | outcome | detail |
|---|---|---|---|---|
| kotoba.designQuality | 1 | 4 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| kotoba.dxf | 0 | 4 | ran, all pass | 1 ns; ran 5 tests, 10 assertions, 0 failures, 0 errors |
| kotoba.eduBerkeleySpice | 0 | 4 | ran, all pass | 1 ns; ran 11 tests, 32 assertions, 0 failures, 0 errors |
| kotoba.ioIpniSpecs | 1 | 2 | stopped: classpath, dependency repository cannot be cloned |  |
| kotoba.kamiEizoGrade | 0 | 3 | ran, all pass | 5 ns; ran 22 tests, 68 assertions, 0 failures, 0 errors |
| kotoba.kamiEngineCad | 0 | 4 | ran, all pass | 1 ns; ran 46 tests, 185 assertions, 0 failures, 0 errors |
| kotoba.kamiMangakaRender | 0 | 4 | ran, all pass | 1 ns; ran 8 tests, 37 assertions, 0 failures, 0 errors |
| kotoba.kamiMangakaSceneAuthor | 1 | 4 | ran, with failure or error | 1 ns; ran 15 tests, 70 assertions, 1 failures, 0 errors |
| kotoba.kamiOngakuPluginHost | 0 | 3 | ran, all pass | 1 ns; ran 11 tests, 38 assertions, 0 failures, 0 errors |
| kotoba.kip | 1 | 2 | stopped: classpath, dependency repository cannot be cloned |  |
| kotoba.kotobaHir | 1 | 3 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| kotoba.kuraNode | 1 | 1 | stopped: classpath, local versions of `edn` conflict |  |
| kotoba.madoguchi | 1 | 1 | stopped: classpath, local versions of `edn` conflict |  |
| kotoba.mailArchive | 1 | 4 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| kotoba.nagare | 1 | 22 | ran, with failure or error | 8 ns; ran 36 tests, 716 assertions, 0 failures, 1 errors |
| kotoba.newsfeed | 0 | 4 | ran, all pass | 1 ns; ran 25 tests, 99 assertions, 0 failures, 0 errors |
| kotoba.nio | 0 | 5 | ran, all pass | 1 ns; ran 11 tests, 32 assertions, 0 failures, 0 errors |
| kotoba.officeStyle | 1 | 4 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| kotoba.okaimono | 0 | 4 | ran, all pass | 3 ns; ran 12 tests, 50 assertions, 0 failures, 0 errors |
| kotoba.onetime | 0 | 3 | ran, all pass | 5 ns; ran 14 tests, 25 assertions, 0 failures, 0 errors |
| kotoba.ongaku | 1 | 3 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| kotoba.pathfind | 0 | 4 | ran, all pass | 1 ns; ran 2 tests, 4 assertions, 0 failures, 0 errors |
| kotoba.pay | 1 | 4 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| kotoba.pedigree | 0 | 3 | ran, all pass | 1 ns; ran 7 tests, 47 assertions, 0 failures, 0 errors |
| kotoba.providerIncidence | 1 | 1 | stopped: classpath, dependency repository cannot be cloned |  |
| kotoba.quickjs | 0 | 2 | ran, all pass | 1 ns; ran 3 tests, 9 assertions, 0 failures, 0 errors |
| kotoba.scheduler | 1 | 2 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| kotoba.session | 1 | 3 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| kotoba.shomen | 0 | 3 | ran, all pass | 3 ns; ran 21 tests, 61 assertions, 0 failures, 0 errors |
| kotoba.sysTms | - | 0 | no test namespaces | no test namespaces (no *_test.clj/cljc under test/) |
| kotoba.torrent | 1 | 2 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| org.ieee.mkdir | - | 0 | no test namespaces | no test namespaces (no *_test.clj/cljc under test/) |
| org.ietf.cbor | 1 | 3 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| org.iso.h264 | 1 | 4 | stopped: reader error in a test file |  |
| org.msgpack.core | 0 | 4 | ran, all pass | 1 ns; ran 16 tests, 214 assertions, 0 failures, 0 errors |
| org.openehr.adl | 0 | 4 | ran, all pass | 5 ns; ran 40 tests, 250 assertions, 0 failures, 0 errors |
| org.w3.nquads | 0 | 4 | ran, all pass | 1 ns; ran 21 tests, 64 assertions, 0 failures, 0 errors |
| org.w3.rdfCanon | 1 | 2 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |
| org.w3.webauthn | 1 | 2 | stopped: classpath, dependency repository cannot be cloned |  |
| org.w3.woff | 1 | 4 | stopped: namespace metadata `{:kotoba/export [...]}` evaluated by Clojure (unresolved symbol) |  |

## Limits

- 40 of 926 is a sample. Of the 19 that ran, 17 passed cleanly and 2 had a runtime difference or a launch issue; the share that ran any test at all is 19 of 40.
- A passing suite shows the tests pass on that runtime, not that the library is complete.
- The JVM run covers the `.clj` and `.cljc` tests only. Babashka and Basilisp were not run.
- Stops that depend on how the library was launched (working directory, classpath) were not retried.
