#!/usr/bin/env python3
"""Observe native selfhost processes; Python only stages, hashes and times.

The independent comparator source text is retained verbatim from the historical
harness. No hosted Kotoba compiler, evaluator or machine-code writer is used.
This does not run the site's retired build route or publish a new site.
"""
import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import statistics
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BENCH = ROOT / "bench/public-domain-comparison"
FUEL = "1000000000"
WORK = {
    "string": (400, 100, 6400),
    "collection": (8, 1000, 1216),
    "allocation": (5, 500, 760),
    "io": (12, 3, 1604321280),
    "concurrency": (1000000, 8, 4985653002),
    "realApp": (8, 1000, 64),
}


def pin(path):
    data = Path(path).read_bytes()
    return {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def observed(argv, *, env, cwd, log=None):
    started = time.perf_counter_ns()
    p = subprocess.run([str(x) for x in argv], env=env, cwd=cwd,
                       capture_output=True, timeout=180)
    elapsed = (time.perf_counter_ns() - started) / 1e6
    if log:
        log.with_suffix(".stdout").write_bytes(p.stdout)
        log.with_suffix(".stderr").write_bytes(p.stderr)
    return p, elapsed


def require_ok(p):
    if p.returncode:
        raise RuntimeError((p.returncode, p.stdout[-400:], p.stderr[-800:]))


def check_answer(p, expected, native):
    require_ok(p)
    out = p.stdout.decode().strip()
    if native:
        match = re.fullmatch(r"\{:status :ok :result (-?\d+) :fuel \{:initial \d+ :remaining \d+\}\}", out)
        if not match or int(match[1]) != expected:
            raise RuntimeError(("native checksum", expected, out, p.stderr.decode()))
    elif out != str(expected):
        raise RuntimeError(("comparator checksum", expected, out))
    if p.stderr:
        raise RuntimeError(("unexpected stderr", p.stderr.decode()))


def summary(values, divisor=1):
    vals = [x / divisor for x in values]
    return dict(status="measured", medianMilliseconds=statistics.median(vals),
                samplesMilliseconds=vals, minimumMilliseconds=min(vals),
                maximumMilliseconds=max(vals))


def expected(id_, multiplier):
    if id_ != "concurrency":
        return WORK[id_][2] * multiplier
    # Reference checksum, not execution of the candidate program.
    # Affine LCG exponentiation gives a separate, logarithmic reference route.
    def state(seed, n):
        a, c, mul, add = 1664525, 1013904223, 1, 0
        while n:
            if n & 1:
                mul, add = (a * mul) & 0xffffffff, (a * add + c) & 0xffffffff
            a, c = (a * a) & 0xffffffff, (a * c + c) & 0xffffffff
            n >>= 1
        return (mul * seed + add) & 0xffffffff
    return sum(state(s, WORK[id_][0] * multiplier) for s in range(1, 5))


def main(a):
    if (platform.system(), platform.machine()) != ("Linux", "x86_64"):
        raise RuntimeError("native Linux x86_64 required; no emulation fallback")
    if a.runs < 7 or a.runs % 2 != 1:
        raise RuntimeError("at least seven odd-numbered repetitions required")
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    raw = out / "raw"
    raw.mkdir()
    qualification = json.loads((BENCH / "selfhost/compiler-qualification.json").read_text())
    compiler = a.compiler.resolve()
    if pin(compiler) != qualification["compilerImage"]:
        raise RuntimeError("compiler is not the exact native-qualified selfhost image")
    if len(qualification["verifiedObservations"]["x86NativeGenerations"]) != 3:
        raise RuntimeError("missing three native generations")
    compiler_copy = out / "amu-selfhost"
    shutil.copyfile(compiler, compiler_copy)
    compiler_copy.chmod(0o700)
    base = dict(os.environ)
    for key in list(base):
        if key.startswith("KEXE_"):
            del base[key]
    compiler_env = dict(base, PATH="/nonexistent", KEXE_ALLOW="35,37,38,39",
                        KEXE_CAP_RESOURCES_35=str(out), KEXE_STACK_BYTES="8388608")
    guest_env = dict(base, PATH="/nonexistent", KEXE_ALLOW="-",
                     KEXE_FUEL=FUEL, KEXE_STRUCTURED_REPORT="1")
    payload = out / "payload.bin"
    payload.write_bytes(bytes((i * 17 + 23) & 255 for i in range(1024 * 1024)))
    assert sum(payload.read_bytes()) * 12 == WORK["io"][2]
    # These fixtures are independently authored comparator programs. Extract
    # their unchanged text without executing the old Node compiler harness.
    old = (ROOT / "scripts/benchmark-public-domains.mjs").read_text()
    comparators = {}
    for variable, filename in [("cSource", "domain.c"), ("rustSource", "domain.rs"),
                               ("goSource", "domain.go"), ("javaSource", "Domain.java"),
                               ("nodeSource", "domain-node.mjs")]:
        text = re.search(r"write\(" + variable + r", String.raw`([\s\S]*?)`\);", old)[1]
        path = out / filename
        path.write_text(text)
        comparators[filename] = pin(path)
    tool_paths = {k: shutil.which(k) for k in ("clang", "rustc", "go", "javac", "java", "node", "strace")}
    if not all(tool_paths.values()):
        raise RuntimeError(("missing tools", tool_paths))
    versions = {}
    for tool, args in [("clang", ["--version"]), ("rustc", ["--version"]),
                       ("go", ["version"]), ("java", ["-version"]), ("node", ["--version"])]:
        p, _ = observed([tool_paths[tool], *args], env=base, cwd=out)
        require_ok(p)
        versions[tool] = (p.stdout + p.stderr).decode().splitlines()[0]
    builds = {
        "c": [tool_paths["clang"], "-O2", "-pthread", "-o", out / "domain-c", out / "domain.c"],
        "rust": [tool_paths["rustc"], "-C", "opt-level=2", "-o", out / "domain-rust", out / "domain.rs"],
        "go": [tool_paths["go"], "build", "-trimpath", "-o", out / "domain-go", out / "domain.go"],
        "jvm": [tool_paths["javac"], "-d", out / "java-classes", out / "Domain.java"],
    }
    (out / "java-classes").mkdir()
    for name, command in builds.items():
        p, _ = observed(command, env=base, cwd=out, log=raw / (name + "-build"))
        require_ok(p)
    native = {}
    native_sources = {}
    for id_, (iterations, multiplier, _) in WORK.items():
        if id_ == "concurrency":
            continue
        original = BENCH / ("selfhost/io.kotoba" if id_ == "io" else f"probes/{id_}.kotoba")
        native_sources[id_] = pin(original)
        for mode, n in [("cold", iterations), ("amortized", iterations * multiplier)]:
            text = original.read_text()
            if id_ == "io":
                text = text.replace("(= i 12)", f"(= i {n})")
            else:
                text = text.replace("(defn main []", "(defn workload []")
                text += (f"\n(defn main [] :i64 (loop [i 0 total 0] "
                         f"(if (>= i {n}) total (recur (+ i 1) (+ total (workload))))))\n")
            source, image = out / f"{id_}-{mode}.kotoba", out / f"{id_}-{mode}.elf"
            source.write_text(text)
            command = [compiler_copy, "compile", source, "--target", "x86_64-linux",
                       "--output", image, "--format", "static"]
            # Actual execve trace proves which executable built this artifact.
            p, _ = observed([tool_paths["strace"], "-f", "-e", "trace=execve", "-o",
                             raw / f"{id_}-{mode}-build.execve", *command],
                            env=compiler_env, cwd=out, log=raw / f"{id_}-{mode}-build")
            require_ok(p)
            trace = (raw / f"{id_}-{mode}-build.execve").read_text()
            if len(re.findall(r"execve\(", trace)) != 1:
                raise RuntimeError("compiler launched another executable")
            image.chmod(0o700)
            native[(id_, mode)] = image
    lanes = ["kotoba", "rust", "c", "go", "jvm", "javascript"]
    def invocation(lane, id_, mode):
        mult = 1 if mode == "cold" else WORK[id_][1]
        if lane == "kotoba":
            env = dict(guest_env)
            if id_ == "io":
                env.update(KEXE_ALLOW="35", KEXE_CAP_RESOURCES_35=str(out))
            return [native[(id_, mode)]], env
        rest = [id_, str(WORK[id_][0] * mult), str(payload)]
        if lane == "jvm":
            return [tool_paths["java"], "-cp", out / "java-classes", "Domain", *rest], base
        if lane == "javascript":
            return [tool_paths["node"], out / "domain-node.mjs", *rest], base
        return [out / ("domain-" + lane), *rest], base
    def run(lane, id_, mode, label):
        cmd, env = invocation(lane, id_, mode)
        p, ms = observed(cmd, env=env, cwd=out, log=raw / label)
        check_answer(p, expected(id_, 1 if mode == "cold" else WORK[id_][1]), lane == "kotoba")
        return ms
    # A wrong answer must be rejected by the very checker used for every sample.
    cmd, env = invocation("kotoba", "collection", "cold")
    p, _ = observed(cmd, env=env, cwd=out, log=raw / "wrong-answer-control")
    try:
        check_answer(p, 1217, True)
    except RuntimeError:
        pass
    else:
        raise RuntimeError("wrong-answer control did not refuse")
    # Native metering and explicit empty authority remain active.
    p, _ = observed(cmd, env=dict(env, KEXE_FUEL="1"), cwd=out, log=raw / "fuel-control")
    if p.returncode != 120 or b":budget/fuel" not in p.stderr:
        raise RuntimeError(("fuel control", p.returncode, p.stderr))
    io_cmd, _ = invocation("kotoba", "io", "cold")
    p, _ = observed(io_cmd, env=guest_env, cwd=out, log=raw / "io-no-grant-control")
    if p.returncode != 120 or b":not-granted" not in p.stderr:
        raise RuntimeError(("I/O no-grant control", p.returncode, p.stderr))
    load = [{"phase": "before", "load1": os.getloadavg()[0]}]
    samples = {(lane, id_, mode): [] for lane in lanes for id_ in WORK for mode in ("cold", "amortized")
               if not (lane == "kotoba" and id_ == "concurrency")}
    for (lane, id_, mode) in samples:
        run(lane, id_, mode, f"warm-{lane}-{id_}-{mode}")
    for i in range(a.runs):
        # Rotate all engines on each repetition; retain every observation.
        order = lanes[i % len(lanes):] + lanes[:i % len(lanes)]
        for id_ in WORK:
            for lane in order:
                for mode in ("cold", "amortized"):
                    key = (lane, id_, mode)
                    if key in samples:
                        samples[key].append(run(lane, id_, mode, f"sample-{i}-{lane}-{id_}-{mode}"))
            load.append({"phase": f"sample-{i}-{id_}", "load1": os.getloadavg()[0]})
    cpu_count = os.cpu_count()
    quiet = max(x["load1"] for x in load) <= cpu_count * .75
    na = dict(status="not-applicable", reason="The pinned native runtime exposes synchronous process/spawn, not a declared four-worker concurrent/thread join contract. Serial child execution would change this workload.")
    artifact_paths = {"c": out / "domain-c", "rust": out / "domain-rust", "go": out / "domain-go",
                      "jvm": out / "java-classes/Domain.class", "javascript": out / "domain-node.mjs"}
    report = dict(schema="kotoba.public-domain-selfhost-observation.v1", generatedAt=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                  compiler={"image": pin(compiler_copy), "qualificationRecord": pin(BENCH / "selfhost/compiler-qualification.json"),
                            "sourceRevision": qualification["sourceRevision"], "ci": qualification["ci"],
                            "currentMainCompiler": False, "note": "Explicitly pinned native-qualified snapshot; current main candidate is not silently substituted."},
                  machine={"os": platform.system(), "kernel": platform.release(), "architecture": platform.machine(),
                           "cpu": next(x.split(":", 1)[1].strip() for x in Path('/proc/cpuinfo').read_text().splitlines() if x.startswith('model name')),
                           "logicalCpu": cpu_count, "virtualization": subprocess.run(["systemd-detect-virt"], capture_output=True, text=True).stdout.strip(), "load": load},
                  qualification={"quietHost": quiet, "rule": "maximum load1 across every workload round <= 0.75 * logical CPUs",
                                 "independentHostQualified": False, "publicReleaseQualified": False,
                                 "scope": "Same-host process-cold observations for these tiny workloads; no universal ranking, CI qualification or independent reproduction claim."},
                  method={"runs": a.runs, "warmups": 1, "nativeFuel": int(FUEL),
                          "processBoundary": "Every sample starts a fresh process; compilation excluded.",
                          "nativeRuntime": "Amu-produced loaderless static ELF, empty guest authority except scoped wire35 for I/O; native structured result/fuel reporting included in elapsed time.",
                          "amortized": "Separate larger in-process batch divided by workload multiplier; not perfectly warmed steady state.",
                          "controls": ["wrong checksum rejected", "native fuel1 refused", "I/O without wire35 refused", "every native build execve trace contains only pinned compiler"],
                          "sampling": "Rotate six engine lanes each round; process-cold then amortized; load recorded after every domain round."},
                  inputPins={"historicalHarness": pin(ROOT / "scripts/benchmark-public-domains.mjs"), "nativeProbes": native_sources,
                             "comparators": comparators, "payload": pin(payload)},
                  workloads={k: dict(iterations=v[0], amortizedMultiplier=v[1], expected=v[2]) for k, v in WORK.items()}, tools=[])
    for lane in lanes:
        row = dict(id=lane, label="Kotoba / Amu selfhost native" if lane == "kotoba" else lane,
                   target="x86_64-linux static ELF" if lane == "kotoba" else {"jvm": "JVM", "javascript": "Node.js"}.get(lane, "x86_64 Linux native"),
                   version=qualification["sourceRevision"] if lane == "kotoba" else versions[{"c": "clang", "rust": "rustc", "go": "go", "jvm": "java", "javascript": "node"}[lane]],
                   artifacts={f"{k[0]}-{k[1]}": pin(v) for k, v in native.items()} if lane == "kotoba" else pin(artifact_paths[lane]), results={}, amortizedResults={})
        for id_ in WORK:
            row["results"][id_] = na if lane == "kotoba" and id_ == "concurrency" else summary(samples[(lane, id_, "cold")])
            row["amortizedResults"][id_] = na if lane == "kotoba" and id_ == "concurrency" else summary(samples[(lane, id_, "amortized")], WORK[id_][1])
        report["tools"].append(row)
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"quietHost": quiet, "medians": {x["id"]: {k: v.get("medianMilliseconds") for k, v in x["results"].items()} for x in report["tools"]}}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compiler", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--runs", default=7, type=int)
    main(parser.parse_args())
