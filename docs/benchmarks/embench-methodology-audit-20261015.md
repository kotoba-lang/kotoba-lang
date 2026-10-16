# Embench methodology audit: Kotoba/C comparison of 15 October 2026

**Assessment date:** 15 October 2026  
**Scope:** the published 0.7998826447941967 Kotoba/C time ratio, its machine-readable report, and its immutable reproduction package.

**Overall rating:** strong record reproducibility; partial reuse of Embench’s measurement machinery; no basis to call the result an official Embench score or a strict same-work comparison.

## Finding

The article accurately labels 0.7999 as an exploratory result, not an official Embench score. The ratio is reproducible from the archived records. The run reuses an upstream Embench speed driver and baseline, but it is a custom comparison of translated Kotoba ports with original C on one Apple M4 Mac mini. It does not establish a score for an Embench target platform or a general Kotoba-versus-C performance claim.

There are material comparability limits in the measured compiler: the later kernel audit classifies substitutions in five workloads, and the Kotoba nettle-sha256 path uses ARMv8 SHA instructions that the C baseline does not. The article discloses both facts. Consequently, the published ratio does not represent the same algorithmic work across all 19 pairs.

## What the public Embench method specifies

The Embench user guide requires recording the exact Embench version, platform and clock, processor configuration, toolchain versions or development commits, and common compiler and linker flags. It permits per-program optimization choices when they change optimization passes. The record should let another person reproduce the run. For speed, it describes timing a few seconds of execution between start and stop triggers with a fast timer inside the device, then computing results relative to the Embench reference and summarizing them with a geometric mean, geometric standard deviation, and range. The project README says the speed workload is scaled to about four seconds and describes hot-cache runs and a 64 KiB program and RAM design envelope.

The source repository lists 0.5 and 1.0 as stable tags and 2.0rc1 and 2.0rc2 as development releases. The public results repository says it currently holds only 0.5 results and accepts submissions by pull request. The measured Kotoba run pins an exact upstream commit, which is reproducible, but the accepted release and publication path for this compiler comparison remain questions for the maintainers.

## Conformance assessment

| Area | Evidence in the published run | Assessment |
|---|---|---|
| Upstream source and driver | Pins Embench commit 09c2ed8c3b7008c95d08b038de4a3f6dc103ed70 and uses its speed driver and baseline. | Strong provenance. A pinned commit does not by itself establish that the maintainers accept it as a scored release. |
| Workload set | Includes all 19 Embench IoT workloads, with translated Kotoba ports compared to upstream C. | Complete workload coverage, but the Kotoba side is a translated implementation rather than the original C source compiled by a second compiler. |
| Output and resource exactness | The reproduction package checks results, fuel, and 17 arena counters against independently derived references. All 19 Kotoba cases pass those checks. | Strong correctness evidence for the recorded runs. Correct output does not show that the same algorithmic work was measured. |
| Timing harness | Uses the upstream speed driver with a project harness, one warm-up, one accepted sample, a 70% CPU-idle gate, CPU-only retries, and first-qualified selection. | Reproducible and load-aware, but one sample per workload provides no run-to-run uncertainty. The reviewed public guide does not prescribe a fixed number of independent repetitions. |
| Duration and scaling | Uses per-workload multipliers and accepts intervals above a 0.5 second floor; the shortest accepted Kotoba interval was 0.777 seconds. The upstream guide aims for a few seconds, and the README describes about four seconds. | A documented custom timing protocol, not the stated nominal Embench duration. The per-workload normalization is part of the custom Kotoba/C ratio. |
| Platform record | Names an Apple M4 Mac mini and uses CLOCK_MONOTONIC_RAW. The archived C report states clockMHzMeasured=false. | A useful host identity and timer record, but the guide’s platform clock field is not measured. This is a host comparison, not a reference embedded-board result. |
| Score meaning | Computes normalized Kotoba time divided by normalized C time and takes the geometric mean across 19 workloads. | Reproducible custom comparison ratio. It is not the usual Embench platform score relative to the reference platform. |
| Summary statistics | The guide calls for geometric mean, geometric standard deviation, and a one-standard-deviation range. | The article reports the per-workload ratios and geometric mean, but does not report the geometric standard deviation or range. |
| Algorithm and ISA parity | The audit identifies algorithm substitutions in huffbench, qrduino, tarfind, ud, and picojpeg. Kotoba nettle-sha256 also uses ARMv8 SHA instructions; the C build does not. | Material asymmetry. The ratio cannot support a strict same-work comparison across all workloads. |
| Publication status | The article says the result is not certified; the results repository currently describes 0.5 data only. | Official Embench acceptance and placement are unconfirmed. |

The project’s phrase “certified kernels” refers to its own compiler-kernel qualification. It does not mean that Embench certified the result. The project’s later same-algorithm-only policy is a useful rule for a future comparison, but it does not retroactively change the published m93 binaries.

## Reproduction check performed for this audit

The offline verifier from the immutable reproduction package was run at commit 72e59fa97288f8fd881bd014b65a66ae35a3d03d. It verified 332 package hashes, the bundled campaign records, exactness and timing-admission decisions, and the 19-workload arithmetic. It returned:

    LEVEL_A PASS GM 0.7998826447941968

The recomputed value differs from the article’s 0.7998826447941967 in the last digit; the reproduction README attributes this to summation order and applies a 1e-12 tolerance. This verifies the archived records and calculation, not a new hardware run. The verifier was run without an Embench checkout argument, so it did not re-fetch and check upstream files omitted from the bundle. No independent build, timing rerun, or Embench maintainer approval is claimed here.

## Claims supported by the evidence

The public evidence supports this narrow statement:

> In one recorded cycle on one Apple M4 Mac mini, the 19 measured Kotoba port images, compiled by the named m93 seed compiler and run with the documented custom harness, had a geometric-mean normalized elapsed-time ratio of 0.7999 against the recorded Apple clang 17 C binaries.

The evidence does not support calling 0.7999 an official Embench score, claiming run-to-run statistical stability, describing all 19 comparisons as same-algorithm, or generalizing the result to released Kotoba, other processors, or language-wide performance.

## Requirements to settle before a formal submission

1. Obtain the maintainers’ answer on the Embench release or commit, target class, and result-submission format. A methodology and publication inquiry has been sent; their response is pending.
2. Agree whether translated language ports are eligible for the public results table or should be presented as a separate compiler comparison.
3. Use a same-algorithm policy for every workload, and either use equivalent SHA paths on both sides or get explicit maintainer guidance on the ISA asymmetry.
4. Use the accepted Embench scale and target timer, record the platform clock and all required toolchain and flag details, and disclose any host-specific harness changes.
5. Add repeated matched measurements and report their spread. This is recommended for confidence in a comparative claim; the public guide does not set a mandatory repetition count.
6. Submit the data and method to the results repository only after maintainers confirm the applicable version and acceptance path.

## Sources

- [Embench user guide at the measured upstream commit](https://github.com/embench/embench-iot/blob/09c2ed8c3b7008c95d08b038de4a3f6dc103ed70/doc/README.md)
- [Embench repository and its listed release tags](https://github.com/embench/embench-iot/tree/09c2ed8c3b7008c95d08b038de4a3f6dc103ed70)
- [Embench public results repository](https://github.com/embench/embench-iot-results)
- [Kotoba Embench article](https://kotoba-lang.org/blog/embench-c-parity-20261015/)
- [Machine-readable Kotoba measurement record](../../site/benchmarks/embench-c-parity-20261015.json)
- [Immutable reproduction package README](https://github.com/kotoba-lang/amu/blob/72e59fa97288f8fd881bd014b65a66ae35a3d03d/bench/embench/reproduce-20261015/README.ja.md)
- [Archived C platform report](https://github.com/kotoba-lang/amu/blob/72e59fa97288f8fd881bd014b65a66ae35a3d03d/bench/embench/reproduce-20261015/records/fresh-C/report.json)
- [Kernel scope policy and audit](https://github.com/kotoba-lang/amu/blob/72e59fa97288f8fd881bd014b65a66ae35a3d03d/bench/embench/reproduce-20261015/audit/KERNEL-POLICY.md)
- [Reproduction package offline verifier](https://github.com/kotoba-lang/amu/blob/72e59fa97288f8fd881bd014b65a66ae35a3d03d/bench/embench/reproduce-20261015/a_offline.py)
- [M93 round definition](https://github.com/kotoba-lang/amu/blob/72e59fa97288f8fd881bd014b65a66ae35a3d03d/bench/embench/reproduce-20261015/rounds/m93-20261015.json)
