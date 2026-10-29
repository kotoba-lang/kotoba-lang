#!/usr/bin/env python3
"""Stage and observe paired native I/O processes; no compiler/runtime fallback."""
import argparse
import hashlib
import json
import os
import platform
import re
import statistics
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BENCH = ROOT / "bench/public-domain-comparison/selfhost"
EXPECTED = 1604321280


def pin(path):
    data = path.read_bytes()
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def main(a):
    assert (platform.system(), platform.machine()) == ("Linux", "x86_64")
    assert a.rounds >= 10
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    raw = out / "raw"
    raw.mkdir()
    proofs = {"baseline": json.loads(a.baseline_proof.read_text()),
              "candidate": json.loads(a.candidate_proof.read_text())}
    compilers = {"baseline": a.baseline.resolve(), "candidate": a.compiler.resolve()}
    for lane, proof in proofs.items():
        rows = proof["threeEqualGenerations"]
        assert len(rows) == 3 and len({x["sha256"] for x in rows}) == 1
        assert pin(compilers[lane])["sha256"] == rows[0]["sha256"]
    payload = out / "payload.bin"
    data = bytes((i * 17 + 23) & 255 for i in range(1048576))
    payload.write_bytes(data)
    env = dict(PATH="/nonexistent", KEXE_ALLOW="35", KEXE_CAP_RESOURCES_35=str(out),
               KEXE_FUEL="1000000000", KEXE_STRUCTURED_REPORT="1")
    commands, sources, artifacts, calls = {}, {}, {}, []

    def observe(name, cmd, execution_env):
        started = time.perf_counter_ns()
        p = subprocess.run([str(x) for x in cmd], cwd=out, env=execution_env,
                           capture_output=True, timeout=60)
        ms = (time.perf_counter_ns() - started) / 1e6
        (raw / (name + ".stdout")).write_bytes(p.stdout)
        (raw / (name + ".stderr")).write_bytes(p.stderr)
        calls.append(dict(name=name, exit=p.returncode, milliseconds=ms))
        (out / "calls.json").write_text(json.dumps(calls, indent=2) + "\n")
        return p, ms

    def correct(p, native=True, expected=EXPECTED):
        assert p.returncode == 0 and not p.stderr, (p.returncode, p.stdout, p.stderr)
        if native:
            match = re.fullmatch(rb"\{:status :ok :result (-?\d+) :fuel \{:initial 1000000000 :remaining (\d+)\}\}\s*", p.stdout)
            assert match and int(match[1]) == expected, p.stdout
            return int(match[2])
        assert p.stdout.strip() == str(expected).encode(), p.stdout

    for source_id in ("original", "optimized"):
        original = BENCH / ("io.kotoba" if source_id == "original" else "io-optimized.kotoba")
        sources[source_id] = pin(original)
        for compiler_id, compiler in compilers.items():
            lane = compiler_id + "-" + source_id
            src = out / (lane + ".kotoba")
            image = src.with_suffix(".elf")
            src.write_bytes(original.read_bytes())
            p, _ = observe(lane + "-build", ["/usr/bin/strace", "-f", "-e", "trace=execve", "-o",
                                            raw / (lane + ".execve"), compiler, "compile", src,
                                            "--target", "x86_64-linux", "--output", image, "--format", "static"],
                           dict(PATH="/nonexistent", KEXE_CAP_RESOURCES_35=str(out), KEXE_STACK_BYTES="8388608"))
            assert p.returncode == 0 and not p.stderr
            assert len(re.findall(r"execve\(", (raw / (lane + ".execve")).read_text())) == 1
            image.chmod(0o700)
            commands[lane] = [image]
            artifacts[lane] = pin(image)
    # Independent comparator artifacts from the previous run must match its
    # published pins exactly; they are never used as a Kotoba compiler/host.
    prior = json.loads((BENCH / "20261029-linux-x86_64.json").read_text())
    comparator_dir = a.comparators.resolve()
    java = a.java.resolve()
    for lane, artifact in [("c", "domain-c"), ("rust", "domain-rust"), ("go", "domain-go"),
                           ("jvm", "java-classes/Domain.class"), ("javascript", "domain-node.mjs")]:
        path = comparator_dir / artifact
        prior_tool = next(t for t in prior["tools"] if t["id"] == lane)
        assert pin(path) == prior_tool["artifacts"]
        artifacts[lane] = pin(path)
        rest = ["io", "12", payload]
        if lane == "jvm":
            commands[lane] = [java, "-cp", comparator_dir / "java-classes", "Domain", *rest]
        elif lane == "javascript":
            commands[lane] = ["/usr/bin/node", path, *rest]
        else:
            commands[lane] = [path, *rest]
    comparator_env = dict(PATH="/usr/bin:/bin")
    fuel = []
    for lane, cmd in commands.items():
        p, _ = observe("warm-" + lane, cmd, env if "-" in lane else comparator_env)
        remaining = correct(p, "-" in lane)
        if remaining is not None:
            fuel.append(remaining)
    assert len(set(fuel)) == 1, "the source optimization must preserve the original guest fuel count"
    # These are untimed diagnostic controls using the exact measured artifact.
    combined = commands["candidate-optimized"]
    p, _ = observe("no-grant", combined, dict(env, KEXE_ALLOW="-"))
    assert p.returncode == 120 and b":not-granted" in p.stderr
    p, _ = observe("fuel1", combined, dict(env, KEXE_FUEL="1"))
    assert p.returncode == 120 and b":budget/fuel" in p.stderr
    try:
        payload.write_bytes(bytes([24]) + data[1:])
        p, _ = observe("changed-byte", combined, env)
        correct(p, expected=EXPECTED + 12)
        payload.write_bytes(data[:-1])
        p, _ = observe("short-file", combined, env)
        correct(p, expected=-12)
    finally:
        payload.write_bytes(data)
    p, _ = observe("syscall-profile", ["/usr/bin/strace", "-c", *combined], env)
    assert p.returncode == 0 and f":result {EXPECTED} ".encode() in p.stdout
    load = [os.getloadavg()[0]]
    samples = {lane: [] for lane in commands}
    # Ten alternating ABBA/BAAB rounds for the direct original/combined pair
    # and for the runtime-only pair. Source-only is a separate rotating lane.
    for i in range(a.rounds):
        for pair, b in [("combined", "candidate-optimized"), ("runtime", "candidate-original")]:
            first, second = "baseline-original", b
            order = [first, second, second, first] if i % 2 == 0 else [second, first, first, second]
            for j, lane in enumerate(order):
                p, ms = observe(f"sample-{i}-{pair}-{j}-{lane}", commands[lane], env)
                assert correct(p) == fuel[0]
                samples[lane].append(ms)
            load.append(os.getloadavg()[0])
        order = ["baseline-optimized", "c", "rust", "go", "jvm", "javascript"]
        order = order[i % len(order):] + order[:i % len(order)]
        for lane in order:
            p, ms = observe(f"sample-{i}-other-{lane}", commands[lane], env if lane == "baseline-optimized" else comparator_env)
            correct(p, lane == "baseline-optimized")
            samples[lane].append(ms)
        load.append(os.getloadavg()[0])
    report = dict(schema="kotoba.public-io-selfhost-paired.v1", generatedAt=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                  compilerProofs={k: {"image": pin(compilers[k]), "sourceSha256": v["sourceSha256"],
                                      "threeEqualGenerations": v["threeEqualGenerations"]} for k, v in proofs.items()},
                  sources=sources, artifacts=artifacts, observer=pin(Path(__file__)), payload=pin(payload),
                  method={"rounds": a.rounds, "pairedOrder": "alternating ABBA/BAAB", "warmups": 1,
                          "bytesPerRead": 1048576, "readsPerProcess": 12, "expected": EXPECTED,
                          "processCold": True, "compilationTimed": False, "fuel": 1000000000,
                          "remainingFuelAllNativeLanes": fuel[0], "allByteChecksRetained": True,
                          "controls": ["no-grant", "fuel1", "changed-byte +12", "short-file -12", "one executable per build"]},
                  machine={"os": platform.system(), "architecture": platform.machine(), "logicalCpu": os.cpu_count(), "load1": load},
                  qualification={"quietHost": max(load) <= .75 * os.cpu_count(), "publicReleaseQualified": False,
                                 "scope": "same-host original/runtime/source/combined I/O observations; no universal language claim"},
                  results={lane: dict(medianMilliseconds=statistics.median(vals), minimumMilliseconds=min(vals),
                                      maximumMilliseconds=max(vals), samplesMilliseconds=vals) for lane, vals in samples.items()})
    report["combinedSpeedup"] = report["results"]["baseline-original"]["medianMilliseconds"] / report["results"]["candidate-optimized"]["medianMilliseconds"]
    report["runtimeOnlySpeedup"] = report["results"]["baseline-original"]["medianMilliseconds"] / report["results"]["candidate-original"]["medianMilliseconds"]
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"quietHost": report["qualification"]["quietHost"], "combinedSpeedup": report["combinedSpeedup"],
                      "medians": {k: v["medianMilliseconds"] for k, v in report["results"].items()}}, indent=2))


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--baseline", type=Path, required=True)
    p.add_argument("--compiler", type=Path, required=True)
    p.add_argument("--baseline-proof", type=Path, required=True)
    p.add_argument("--candidate-proof", type=Path, required=True)
    p.add_argument("--comparators", type=Path, required=True)
    p.add_argument("--java", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--rounds", type=int, default=10)
    main(p.parse_args())
