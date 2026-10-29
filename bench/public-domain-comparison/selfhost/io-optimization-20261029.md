# Native I/O diagnosis, optimization and paired remeasurement

The same 1 MiB deterministic file is read12 times and every unsigned byte is
summed on each read. Exact expected result remains1604321280. Native compiler,
guest runtime, finite fuel and explicit directory-scoped FS2 capability are
retained. No C/Java/Node helper executes any part of the Kotoba workload.

## Measured result

Native Linux x86_64, Ryzen5 7500F,12 logical CPUs. Ten alternating ABBA/BAAB
rounds per pair, fresh process for every sample; compilation excluded.
Maximum observed load1 was0.911, below the9.0 gate. CPU frequency/affinity
remain OS controlled. All140 timed processes returned the exact expected
checksum. Both compiler graphs retain all49 modules and have3 consecutive
identical native selfhost generations; candidate reproduction and runtime
regressions are retained in [Amu PR1681](https://github.com/kotoba-lang/amu/pull/1681).

| Native I/O configuration | Median ms | Samples |
| --- | ---: | ---: |
| Original runtime + original source | 1164.377 | 40 |
| Optimized runtime + original source | 543.973 | 20 |
| Original runtime + optimized source | 677.758 | 10 |
| Optimized runtime + optimized source | **318.384** | 20 |

The combined ratio using the displayed aggregate baseline is **3.657x**
(72.7% less elapsed time). Using only the20 baseline samples from the direct
original/combined pair gives1163.565 ms and **3.655x**. The runtime-only pair
improves by2.141x using its own baseline samples. The source-only lane is a
separate rotating observation, not a claimed independent ABBA comparison.
Both pairs are recorded separately; the baseline's40 samples contain20 per
pair. The full40-sample baseline median is used in the displayed summary.

Fresh independent I/O comparator medians on this host: C/Clang2.728 ms,
Rust3.285 ms, Go6.503 ms, Java56.919 ms, Node120.520 ms (10 processes each).
Their artifacts exactly match the first run's pins. Kotoba remains about117x
the C elapsed time on this tiny read-and-byte-sum workload. The optimization
is not native I/O parity, a universal language claim, or a release result.

## Cause and change

The original executable's syscall profile accounted for about3 ms total,
including24 reads (data plus EOF),12 openat2 and12 closes. Most wall time was
in user-space value processing. Separate diagnostic ablations found:

- Actual reads plus original value conversion, omitting aggregation:525 ms.
- Reads with bytes kept directly, omitting aggregation:194 ms.
- Original workload with immutable length hoisted:1003 ms.
- Direct bytes plus length hoist:678 ms.

These three-sample diagnostics locate costs; they are not substituted into
the official workload or used as qualified rankings. Intermediate diagnostics
could overlap compiler builds. `perf` was unavailable because the host's
perf_event_paranoid is4; system policy was not changed.

The native FS2 response expands each raw byte into a64-bit arena item.
`vector-i64-from-bytes` additionally invokes the full byte validation/copy
route (`seed/30-lower.kotoba` maps it to RT-BYTES-FROM-VECTOR). The original
byte loop repeatedly requested its immutable length and dispatched checked
access through multiple metadata/address helper calls.

`io-optimized.kotoba` keeps the response as bytes, hoists its immutable length,
and uses bytes-at for **every** byte. It still checks the exact FS2 prefix and
response length on every read. No cached file sum, sampled byte set, reduced
read count, serial concurrency substitute or precomputed answer is used.
Loop unrolling was tested diagnostically and omitted from the final source;
the original per-byte loop and its guest fuel consumption are preserved.

The generic runtime change derives descriptor metadata once per freshly
checked operation, and derives stable item bases outside private byte-copy,
expansion and validation loops. No metadata is retained across calls, and
no callbacks or arena rollback occur inside these loops. Handle/index/byte
checks, copy order, resource limits and highwater updates are retained.
Runtime-only measurements use the **unchanged original source**. The new
baseline compiler emits exactly the historical original I/O artifact hash,
which also excludes unrelated compiler-source changes as a timing explanation.

The optimized native runtime's direct-read diagnostic still costs about134 ms;
byte expansion and checked per-byte work remain substantial. No attribution
to disk latency or claim of an optimized packed-byte representation is made.

## Correctness, authority and provenance

All native timed processes have initial fuel1000000000 and remaining
fuel987417063, including all four source/runtime combinations. Fuel was not
increased or metering disabled. The source optimization avoids allocations;
the generic runtime-only change preserves arena accounting for the same source.

Controls on the exact measured candidate artifact:

- No wire35 grant refuses with exit120 and `:not-granted`.
- Fuel1 refuses with exit120 and `:budget/fuel`.
- Payload byte0 changed23->24 increases the result by exactly12, to1604321292.
- A one-byte-short file fails the per-read length check and returns-12.
- The payload is restored before sampling. Every native build execve trace
  invokes only the pinned selfhost executable with PATH=/nonexistent.

Amu's native differential test additionally verifies copy values, both
conversion directions' immutability, all byte values over boundary lengths,
a built wrong-expectation control, unchanged runtime24 and5 byte/index/slice
refusals. Baseline and candidate have identical result/fuel/arena observations.
The public root/role/image pins and full native ARM/controller/release gates
are **not** promoted by this scoped x86 experiment; Amu PR1681 remains draft.

Evidence:

- `io-paired-20261029.json`: all140 samples, both compiler fixed points,
  source/image hashes, full method, load history and separate comparator lanes.
- `io-pair-summary-20261029.json`: pair-specific medians and round-bootstrap
  uncertainty, derived from the unchanged raw process ledger.
- `io-paired-final-1-evidence.tar.gz`: every raw sample/control/build output,
  syscall profile, build traces and process ledger.
- `io-diagnosis-evidence.tar.gz`: diagnostic source variants, raw observations
  and ablation reports. These are explicitly separate from formal timings.
- `io-optimization-manifest.json`: hashes for this follow-up packet.
- [Amu runtime implementation and native proof](https://github.com/kotoba-lang/amu/pull/1681).

To reproduce on native Linux x86_64, first build the exact complete baseline
and candidate using Amu's `observe-full-native-fixedpoint.py`, retaining3 equal
generations and checking their source pins. Use the original comparator
artifacts from `observe-public-domains-selfhost.py` or rebuild them with the
recorded toolchains and verify their exact pins. Then, from this repository:

```sh
python3 scripts/observe-public-io-selfhost.py \
  --baseline /absolute/baseline/G2.elf \
  --compiler /absolute/candidate/G3.elf \
  --baseline-proof /absolute/baseline/fixed-point.json \
  --candidate-proof /absolute/candidate/fixed-point.json \
  --comparators /absolute/original-domain-output \
  --java /absolute/path/to/java \
  --output /absolute/new/io-output --rounds 10
```

Build before measuring, and admit only one campaign on the host. The observer
refuses drifted compiler/comparator images and checks every sample. Python
only stages, hashes and observes native processes; it never supplies a Kotoba
compiler, evaluator or runtime fallback. Historical six-domain data and the
website remain separate from this I/O follow-up.
