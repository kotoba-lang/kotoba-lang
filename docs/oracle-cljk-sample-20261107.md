# Oracle sample: do the cljk repos run? (measured 2026-11-07)

One-off local measurement under the oracle rules (JUN-579). Not CI, not a gate,
not a qualification of the native route. It answers one question: of a seeded
sample of repos holding cljk/cljc source, how many run their own tests today.

## Method

- Population: 2275 of 2749 repos under `kotoba-lang` with `.cljk` or `.cljc`
  tracked source (the tracked `.nbb/` cache excluded).
- Sample: 30 repos, `random.Random(20261107).shuffle`, first 30, sorted.
- Run: `git archive HEAD` of each repo into a scratch directory, then
  `kbb -M:test` with a 300 s timeout. `kbb` runs the nbb engine
  (`nbb v1.4.208`; also present: Clojure CLI 1.12.5.1654, JDK 21.0.1,
  Babashka 1.12.218, node v26.0.0). The sources are `.cljk`, which the JVM
  cannot read, so the JVM route was not used. `kbb` resolves only `:local/root`
  and `:git/sha` dependencies and substitutes `cljs.test` for the JVM runner.
- Dependencies were fetched from `github.com/kotoba-lang` git by `kbb`.
- Machine: macOS (Darwin 25.4.0), arm64.

## Result

| Outcome | Repos |
|---|---|
| all tests pass | 15 |
| no test namespace found | 3 |
| could not run (blocked) | 12 |
| tests ran and some failed | 0 |

Of the repos that have tests and could be run, every one passed. The blocked
ones failed before any test ran, for these reasons:

- a namespace is required but not declared as a dependency (7 repos);
- an unresolved symbol (1);
- a pinned dependency repository does not exist on GitHub under that name,
  although a local checkout of it exists (3: hinshitsu, tayori, org-nist-sha3);
- no `deps.edn` at HEAD (1: translate).

## Per repo

| repo | HEAD | exit | seconds | result |
|---|---|---|---|---|
| agent | `b9dccca85` | 0 | 8 | 10 tests, 34 assertions pass, 0 fail, 0 error |
| capability-net-connect | `b53987037` | 1 | 11 | blocked: missing namespace cbor.core |
| capability-net-transport | `33ea97c22` | 1 | 5 | blocked: missing namespace cbor.core |
| coll-prewalk | `3345611a6` | 1 | 5 | no tests |
| com-berkeley-lights | `ee898f1db` | 0 | 31 | 5 tests, 85 assertions pass, 0 fail, 0 error |
| com-blueprism | `e241fa9fc` | 0 | 7 | 6 tests, 90 assertions pass, 0 fail, 0 error |
| com-hitachi-osiv | `248bb8724` | 0 | 6 | 5 tests, 86 assertions pass, 0 fail, 0 error |
| com-m365 | `ddfe5be55` | 0 | 6 | 5 tests, 82 assertions pass, 0 fail, 0 error |
| com-rocket-lab | `8334b2b16` | 0 | 7 | 5 tests, 90 assertions pass, 0 fail, 0 error |
| com-ros2-nav | `9cbbe3e2c` | 0 | 6 | 5 tests, 81 assertions pass, 0 fail, 0 error |
| daichou | `9f4b4b31f` | 0 | 6 | 10 tests, 47 assertions pass, 0 fail, 0 error |
| edn-max-nodes | `6e0e39571` | 1 | 4 | no tests |
| force3d | `60f6de7f9` | 0 | 6 | 10 tests, 17 assertions pass, 0 fail, 0 error |
| hinshitsu | `31e5192fb` | 1 | 8 | blocked: dependency repos not found on GitHub (process-exec, kotoba-io-*) |
| host | `1da7c9420` | 1 | 11 | blocked: missing namespace kami.gpu |
| jp-hiroshiba-voicevox | `914c57752` | 1 | 9 | blocked: unresolved symbol client/audio-query! |
| madoguchi | `7df8061fe` | 1 | 10 | blocked: missing namespace chobo.ledger |
| openapi | `1d12ccc3e` | 0 | 9 | 12 tests, 42 assertions pass, 0 fail, 0 error |
| org-ibis | `f2c191173` | 0 | 8 | 11 tests, 42 assertions pass, 0 fail, 0 error |
| org-ietf-epp | `f8168460b` | 1 | 9 | blocked: missing namespace srs.time |
| org-nist-sha3 | `f930c4825` | 64 | 6 | blocked: dependency repos not found on GitHub (process-exec, kotoba-io-buffer-writer) |
| org-openusd | `5a6135d69` | 0 | 9 | 19 tests, 45 assertions pass, 0 fail, 0 error |
| reji | `0dd083c22` | 0 | 9 | 16 tests, 771 assertions pass, 0 fail, 0 error |
| scheduler-tick | `5afb27985` | 1 | 7 | no tests |
| sheets | `357dec617` | 1 | 9 | blocked: missing namespace transit.core |
| states | `a16f423f9` | 1 | 119 | blocked: missing namespace kotoba.verifier.signing |
| tayori | `7dccafc29` | 1 | 10 | blocked: dependency repos not found on GitHub (kotoba-io-*) |
| test | `c43aff05b` | 0 | 7 | 14 tests, 152 assertions pass, 0 fail, 0 error |
| translate | `e231c9ca3` | 66 | 1 | blocked: no deps.edn in HEAD |
| xml | `1df028c6b` | 0 | 7 | 15 tests, 66 assertions pass, 0 fail, 0 error |

## Limits

- 30 of 2275 is a sample, not a census. With 15 passes the pass rate among
  sampled repos is about 50% (15/30); the interval is wide.
- A passing suite shows the tests pass, not that the repo is complete.
- This is `kbb`/nbb only. The JVM was not run and was not able to read the
  sources, so no second-runtime comparison exists here.
- Blocked results may be fixable by adding dependencies; they were not
  retried or repaired.
