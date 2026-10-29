# Six-domain selfhost observation — 29 October 2026

Fresh measurements use Kotoba compiled by Amu selfhost to loaderless native
Linux x86_64 ELF. The previous Wasm/typed-JavaScript run remains in `../latest.json`.
These are separate runs on different machines: do not calculate a speedup by
dividing one by the other.

The compiler is the explicitly pinned, native-qualified snapshot
[`ed7318b7`](https://github.com/kotoba-lang/amu/tree/ed7318b7a7c8683e5f75ab113dfe455b96fba5d0),
SHA-256 `d545032000f25e30fd1990ca454f81ac469921e90d8dbac9e38680606174b242`.
Its [exact-head CI](https://github.com/kotoba-lang/amu/actions/runs/40802391423)
was read back as completed/success. `compiler-qualification.json` binds its
source and three equal native generations. The newer main candidate
`2e99b0df` was still unqualified when inspected; this run does **not** measure it
or promote its qualification.

## Process-cold medians

Milliseconds; seven fresh processes per available cell, one correctness warmup,
rotating engine order. Compilation is excluded. All 490 measured processes
returned the full independent expected checksum. One lane is not applicable;
it is not a zero or a failed timing sample.

| Domain | Kotoba / Amu selfhost | C / Clang | Rust | Go | Java | Node.js |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| String | 1.155 | 0.516 | 0.724 | 0.942 | 18.723 | 65.839 |
| Collection | 0.740 | 0.490 | 0.712 | 0.842 | 23.870 | 64.592 |
| Allocation | 0.707 | 0.479 | 0.703 | 0.816 | 18.913 | 65.708 |
| I/O | 1163.732 | 2.753 | 3.768 | 6.489 | 60.072 | 121.814 |
| Concurrency | N/A | 0.656 | 1.097 | 1.680 | 30.630 | 106.570 |
| Real application kernel | 0.751 | 0.501 | 0.781 | 0.825 | 19.075 | 66.638 |

The I/O result is retained even though it is much slower. It reads the same
1 MiB file 12 times through explicitly granted native FS2 wire35 and sums every
unsigned byte. The native response is converted to a vector and checked for its
FS2 acknowledgement and exact length before summation. This includes native
capability and value-representation work, and is not a raw-syscall-only test.
Changing one input byte from 23 to 24 makes the **same artifact** return
1604321292 instead of 1604321280, exactly 12 higher; the input was restored.
This diagnostic is separate from all timing samples.

Concurrency stays N/A: the pinned runtime's wire20 process/spawn waits for a
child's response before returning; it supplies no admitted four-worker
concurrent/thread join contract. Running children serially or implementing the
Kotoba lane in another language would change the workload. The mechanism is
in the pinned Amu `seed/77-aarch64-static-rt.kotoba`, `kxrt-spawn` and
`kxrt-sp-reap`. This replaces the historical Wasm-specific N/A explanation for
this native observation; it does not claim that native Kotoba has no I/O.

## Scope and controls

Host: native Linux x86_64, AMD Ryzen 5 7500F, 12 logical CPUs,
`systemd-detect-virt=none`. Load1 ranged from 0.427 to 0.743, below the gate of
9.0 (`0.75 * 12`) throughout all workload rounds. The load gate passed; this
is not independent host/release qualification. CPU frequency and core affinity
were not fixed. Seven tiny process-cold samples are not a statistical
cross-language performance ranking or a general application benchmark.

Toolchains: Clang 18.1.3 (`-O2`), Rust 1.75.0 (`-C opt-level=2`), Go 1.22.2,
OpenJDK 21.0.12.1, Node.js 18.19.1. Comparator source text is copied verbatim
from `scripts/benchmark-public-domains.mjs` without executing that historical
harness. Their different allocation, optimization and runtime contracts remain;
in particular, the label "Allocation" does not prohibit compiler fusion.
The real-application case is only the original fixed request-admission kernel.
Java and Node run only the independent comparator lanes: neither builds or runs
the Kotoba compiler or its workload artifacts.

The original four pure `.kotoba` probes are unchanged. An emitted native main
repeats their original per-iteration function inside the process, replacing the
old JavaScript host loop. Native processes include native startup, workload,
full result and fuel reporting. Each gets finite fuel 1,000,000,000 and no
capabilities; only the I/O lane gets wire35 scoped to the payload directory.
The finite whole-process budget differs from the historical Wasm per-call
budget and is recorded explicitly. Larger in-process batches and their
normalization multipliers remain separately available in the JSON; they are
not claimed as perfectly warmed steady-state values.

Controls passed: wrong checksum rejected; fuel1 refused with `:budget/fuel`;
I/O without its grant refused with `:not-granted`; all ten native build execve
traces contain exactly one executable, the hash-pinned selfhost compiler.
`PATH=/nonexistent` is used for compiler and guest calls. Python stages bytes,
hashes, invokes native processes and observes elapsed wall time; it supplies
no Kotoba evaluator, compiler, machine-code writer or runtime fallback.

## Evidence and reproduction

- `20261029-linux-x86_64.json`: all samples, artifact/source pins, versions,
  qualification boundaries, and the load after every workload round.
- `run-1-evidence.tar.gz`: all 1,164 stdout/stderr/execve files from the run,
  including warmups, samples, compiler output and negative controls.
- `io-input-control.json`: the separate input-mutation observation.
- `compiler-qualification.json`: source-bound native qualification record.
- `evidence-manifest.json`: hashes binding this packet to the actual observer.

Use native Linux x86_64 with Python 3, strace and the named comparator
compilers on PATH. Obtain `bin/amu` from the exact Amu revision above and
verify its digest against `compiler-qualification.json`. The observer refuses
any different compiler image, missing tool, checksum error or existing output
directory. It never selects a legacy compiler. From this repository root:

```sh
python3 scripts/observe-public-domains-selfhost.py \
  --compiler /absolute/path/to/pinned-native-amu \
  --output /absolute/new/task-output --runs 7
```

Use the owning Amu repository's native build queue if other native work shares
the measurement host, and admit only one campaign at a time. Build before
sampling, retain failed output, and rerun the load gate on each host. The
observation script deliberately does not install toolchains or touch services.

This packet does not replace `latest.json`, run the retired site generator,
merge changes or publish the website. Switching the visible panels remains
separate from obtaining fresh native evidence.
