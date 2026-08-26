#!/usr/bin/env node

import { execFileSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";
import { performance } from "node:perf_hooks";

const root = resolve(import.meta.dirname, "..");
const kotobaSource = join(root, "bench/public-compile-comparison/main.kotoba");
const rustSource = join(root, "bench/public-compile-comparison/main.rs");

function option(name, fallback) {
  const index = process.argv.indexOf(name);
  return index === -1 ? fallback : process.argv[index + 1];
}

const runs = Number(option("--runs", "21"));
const output = resolve(root, option("--output", "bench/public-compile-comparison/latest.json"));
if (!Number.isInteger(runs) || runs < 7 || runs > 101 || runs % 2 === 0) {
  throw new Error("--runs must be an odd integer from 7 through 101");
}

function text(command, args = []) {
  return execFileSync(command, args, { encoding: "utf8" }).trim();
}

function optionalText(command, args = []) {
  try { return text(command, args); } catch { return "unavailable"; }
}

function sha256(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}

function wasm(path) {
  const bytes = readFileSync(path);
  if (bytes.length < 8 || bytes.subarray(0, 4).toString("hex") !== "0061736d") {
    throw new Error(`${path} is not a WebAssembly module`);
  }
}

function run(command, args, outputPath, validateStdout) {
  const started = performance.now();
  const result = spawnSync(command, args, { cwd: root, encoding: "utf8" });
  const elapsedMilliseconds = performance.now() - started;
  if (result.status !== 0) {
    throw new Error(`${command} failed (${result.status}): ${result.stderr || result.stdout}`);
  }
  if (validateStdout) validateStdout(result.stdout);
  if (!existsSync(outputPath)) throw new Error(`${command} did not create ${outputPath}`);
  wasm(outputPath);
  return elapsedMilliseconds;
}

function kotoba(outputPath) {
  return run("kotoba", ["compile", kotobaSource, "--target", "wasm", "--output", outputPath, "--json"], outputPath,
    (stdout) => {
      const result = JSON.parse(stdout);
      if (result["kotoba.cli/code"] !== "emitted") {
        throw new Error(`Kotoba did not report emitted: ${stdout}`);
      }
    });
}

function rust(outputPath) {
  return run("rustc", ["--edition=2024", "--crate-type=cdylib", "--target=wasm32-unknown-unknown",
    "-C", "opt-level=0", "-C", "debuginfo=0", "-C", "panic=abort", "-o", outputPath, rustSource], outputPath);
}

function rounded(value) { return Number(value.toFixed(3)); }
function percentile(values, fraction) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}
function summary(values) {
  return {
    medianMilliseconds: rounded(percentile(values, 0.5)),
    p95Milliseconds: rounded(percentile(values, 0.95)),
    minimumMilliseconds: rounded(Math.min(...values)),
    maximumMilliseconds: rounded(Math.max(...values)),
    samplesMilliseconds: values.map(rounded),
  };
}

const directory = mkdtempSync(join(tmpdir(), "kotoba-public-compile-"));
try {
  for (let warmup = 0; warmup < 3; warmup += 1) {
    kotoba(join(directory, `warmup-kotoba-${warmup}.wasm`));
    rust(join(directory, `warmup-rust-${warmup}.wasm`));
  }

  const samples = { kotoba: [], rust: [] };
  for (let index = 0; index < runs; index += 1) {
    const order = index % 2 === 0 ? ["kotoba", "rust"] : ["rust", "kotoba"];
    for (const tool of order) {
      const target = join(directory, `${tool}-${index}.wasm`);
      samples[tool].push(tool === "kotoba" ? kotoba(target) : rust(target));
    }
  }

  const kotobaArtifact = join(directory, `kotoba-${runs - 1}.wasm`);
  const rustArtifact = join(directory, `rust-${runs - 1}.wasm`);
  const kotobaSummary = summary(samples.kotoba);
  const rustSummary = summary(samples.rust);
  const report = {
    format: "kotoba.public-compile-comparison/v1",
    generatedAt: new Date().toISOString(),
    scope: "process-cold wall time for tiny exported main returning i64 42; both emit WebAssembly",
    interpretation: "Narrow toolchain-startup observation only; different ABIs and runtime contracts; no general compile-speed or artifact-size claim",
    method: { runs, warmupsPerTool: 3, ordering: "alternating, Kotoba first on even zero-based rounds", percentile: "nearest-rank" },
    environment: {
      os: optionalText("uname", ["-srv"]),
      architecture: optionalText("uname", ["-m"]),
      chip: optionalText("sysctl", ["-n", "machdep.cpu.brand_string"]),
      memoryBytes: Number(optionalText("sysctl", ["-n", "hw.memsize"])) || null,
      node: process.version,
      kotoba: optionalText("brew", ["list", "--versions", "kotoba"]),
      rustc: optionalText("rustc", ["--version"]),
      rustTarget: "wasm32-unknown-unknown",
      repositoryCommit: optionalText("git", ["rev-parse", "HEAD"]),
      repositoryDirty: optionalText("git", ["status", "--porcelain"]) !== "",
    },
    workload: {
      result: 42,
      sources: {
        kotoba: { path: "bench/public-compile-comparison/main.kotoba", sha256: sha256(kotobaSource) },
        rust: { path: "bench/public-compile-comparison/main.rs", sha256: sha256(rustSource) },
      },
      commands: {
        kotoba: "kotoba compile main.kotoba --target wasm --output main.wasm --json",
        rust: "rustc --edition=2024 --crate-type=cdylib --target=wasm32-unknown-unknown -C opt-level=0 -C debuginfo=0 -C panic=abort -o main.wasm main.rs",
      },
    },
    results: {
      kotoba: { ...kotobaSummary, artifactBytes: statSync(kotobaArtifact).size, artifactSha256: sha256(kotobaArtifact) },
      rust: { ...rustSummary, artifactBytes: statSync(rustArtifact).size, artifactSha256: sha256(rustArtifact) },
      medianRatioKotobaToRust: rounded(kotobaSummary.medianMilliseconds / rustSummary.medianMilliseconds),
    },
  };
  writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
} finally {
  rmSync(directory, { recursive: true, force: true });
}
