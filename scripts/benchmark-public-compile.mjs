#!/usr/bin/env node

import { execFileSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";
import { performance } from "node:perf_hooks";

const root = resolve(import.meta.dirname, "..");
const kotobaSource = join(root, "bench/public-compile-comparison/main.kotoba");
// Exact former bench/public-compile-comparison/main.rs bytes. This repo's
// legacy-runtime-absence gate forbids committed *.rs; rustc still needs a
// file, so the harness writes these bytes into the temp workdir.
const rustSourceText = "#![no_std]\n\n#[unsafe(no_mangle)]\npub extern \"C\" fn main() -> i64 {\n    40 + 2\n}\n\n#[panic_handler]\nfn panic(_info: &core::panic::PanicInfo<'_>) -> ! {\n    loop {}\n}\n";
const cSourceText = "__attribute__((visibility(\"default\")))\nlong long main(void) {\n    return 40 + 2;\n}\n";
const javaSourceText = "public final class Main {\n    public static long answer() { return 40L + 2L; }\n    public static void main(String[] args) {\n        if (answer() != 42L) throw new AssertionError(\"wrong result\");\n    }\n}\n";

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

function optionalVersion(command, args = []) {
  const result = spawnSync(command, args, { encoding: "utf8" });
  if (result.status !== 0) return "unavailable";
  return `${result.stdout || ""}${result.stderr || ""}`.trim().split("\n")[0];
}

const llvmPrefix = optionalText("brew", ["--prefix", "llvm"]);
const clangCommand = process.env.KOTOBA_BENCH_CLANG
  || (llvmPrefix !== "unavailable" && existsSync(join(llvmPrefix, "bin", "clang"))
    ? join(llvmPrefix, "bin", "clang")
    : "clang");

function sha256(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}

function wasm(path) {
  const bytes = readFileSync(path);
  if (bytes.length < 8 || bytes.subarray(0, 4).toString("hex") !== "0061736d") {
    throw new Error(`${path} is not a WebAssembly module`);
  }
  const module = new WebAssembly.Module(bytes);
  if (WebAssembly.Module.imports(module).length !== 0) {
    throw new Error(`${path} unexpectedly requires host imports`);
  }
  const instance = new WebAssembly.Instance(module, {});
  if (typeof instance.exports.main !== "function" || instance.exports.main() !== 42n) {
    throw new Error(`${path} does not export main returning i64 42`);
  }
}

function run(command, args, outputPath, validateArtifact, validateStdout) {
  const started = performance.now();
  const result = spawnSync(command, args, { cwd: root, encoding: "utf8" });
  const elapsedMilliseconds = performance.now() - started;
  if (result.status !== 0) {
    throw new Error(`${command} failed (${result.status}): ${result.stderr || result.stdout}`);
  }
  if (validateStdout) validateStdout(result.stdout);
  if (!existsSync(outputPath)) throw new Error(`${command} did not create ${outputPath}`);
  validateArtifact(outputPath);
  return elapsedMilliseconds;
}

function kotoba(outputPath) {
  return run("kotoba", ["compile", kotobaSource, "--target", "wasm", "--output", outputPath, "--json"], outputPath,
    wasm,
    (stdout) => {
      const result = JSON.parse(stdout);
      if (result["kotoba.cli/code"] !== "emitted") {
        throw new Error(`Kotoba did not report emitted: ${stdout}`);
      }
    });
}

function rust(outputPath, rustSource) {
  return run("rustc", ["--edition=2024", "--crate-type=cdylib", "--target=wasm32-unknown-unknown",
    "-C", "opt-level=0", "-C", "debuginfo=0", "-C", "panic=abort", "-o", outputPath, rustSource], outputPath, wasm);
}

function c(outputPath, cSource) {
  return run(clangCommand, ["--target=wasm32", "-O0", "-nostdlib", "-Wl,--no-entry",
    "-Wl,--export=main", "-Wl,--strip-all", "-o", outputPath, cSource], outputPath, wasm);
}

function jvm(outputPath, javaSource, directory) {
  return run("javac", ["-g:none", "-d", directory, javaSource], outputPath,
    (path) => {
      if (readFileSync(path).subarray(0, 4).toString("hex") !== "cafebabe") {
        throw new Error(`${path} is not a JVM class file`);
      }
      execFileSync("java", ["-cp", directory, "Main"], { stdio: "pipe" });
    });
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

function load1() {
  const raw = optionalText("sysctl", ["-n", "vm.loadavg"]);
  const match = raw.match(/\{\s*([0-9.]+)/);
  return match ? rounded(Number(match[1])) : null;
}

const observedLoad1First = load1();
const directory = mkdtempSync(join(tmpdir(), "kotoba-public-compile-"));
try {
  const rustSource = join(directory, "main.rs");
  const cSource = join(directory, "main.c");
  const javaSource = join(directory, "Main.java");
  writeFileSync(rustSource, rustSourceText);
  writeFileSync(cSource, cSourceText);
  writeFileSync(javaSource, javaSourceText);
  const rustSourceSha256 = createHash("sha256").update(rustSourceText).digest("hex");
  const cSourceSha256 = createHash("sha256").update(cSourceText).digest("hex");
  const javaSourceSha256 = createHash("sha256").update(javaSourceText).digest("hex");
  for (let warmup = 0; warmup < 3; warmup += 1) {
    kotoba(join(directory, `warmup-kotoba-${warmup}.wasm`));
    rust(join(directory, `warmup-rust-${warmup}.wasm`), rustSource);
    c(join(directory, `warmup-c-${warmup}.wasm`), cSource);
    jvm(join(directory, "Main.class"), javaSource, directory);
  }

  const tools = ["kotoba", "rust", "c", "jvm"];
  const samples = Object.fromEntries(tools.map((tool) => [tool, []]));
  for (let index = 0; index < runs; index += 1) {
    const offset = index % tools.length;
    const order = tools.slice(offset).concat(tools.slice(0, offset));
    for (const tool of order) {
      const target = join(directory, tool === "jvm" ? "Main.class" : `${tool}-${index}.wasm`);
      const elapsed = tool === "kotoba" ? kotoba(target)
        : tool === "rust" ? rust(target, rustSource)
        : tool === "c" ? c(target, cSource)
        : jvm(target, javaSource, directory);
      samples[tool].push(elapsed);
    }
  }

  const artifacts = {
    kotoba: join(directory, `kotoba-${runs - 1}.wasm`),
    rust: join(directory, `rust-${runs - 1}.wasm`),
    c: join(directory, `c-${runs - 1}.wasm`),
    jvm: join(directory, "Main.class"),
  };
  const summaries = Object.fromEntries(tools.map((tool) => [tool, summary(samples[tool])]));
  const observedLoad1Last = load1();
  const quietLoad1Limit = 1.0;
  const speedQualified = observedLoad1First !== null && observedLoad1Last !== null
    && observedLoad1First <= quietLoad1Limit && observedLoad1Last <= quietLoad1Limit;
  const report = {
    format: "kotoba.public-compile-comparison/v2",
    generatedAt: new Date().toISOString(),
    scope: "process-cold wall time for a tiny source-level result of 42; Kotoba, Rust, and C emit WebAssembly while javac emits a JVM class",
    interpretation: "Narrow toolchain-startup observation only; targets, ABIs, runtime contracts, and compiler work differ, so this is not a general language build-speed or artifact-size ranking",
    method: { runs, warmupsPerTool: 3, ordering: "four-tool rotating order", percentile: "nearest-rank", validationOutsideTimedRegion: true },
    speedQualification: {
      verdict: speedQualified ? "qualified-host-load" : "unqualified-host-load",
      buildSpeedRankingQualified: speedQualified,
      quietLoad1Limit,
      observedLoad1First,
      observedLoad1Last,
      explanation: speedQualified
        ? "The host-load gate passed; the ranking remains limited to this exact startup workload."
        : "The artifacts and samples are valid observations, but the host-load gate failed; do not use this run to rank the toolchains.",
    },
    environment: {
      os: optionalText("uname", ["-srv"]),
      architecture: optionalText("uname", ["-m"]),
      chip: optionalText("sysctl", ["-n", "machdep.cpu.brand_string"]),
      memoryBytes: Number(optionalText("sysctl", ["-n", "hw.memsize"])) || null,
      node: process.version,
      kotoba: optionalText("brew", ["list", "--versions", "kotoba"]),
      rustc: optionalText("rustc", ["--version"]),
      clang: optionalVersion(clangCommand, ["--version"]),
      clangExecutable: clangCommand,
      javac: optionalVersion("javac", ["-version"]),
      java: optionalVersion("java", ["-version"]),
      rustTarget: "wasm32-unknown-unknown",
      cTarget: "wasm32",
      jvmTarget: "class file for the installed JDK",
      repositoryCommit: optionalText("git", ["rev-parse", "HEAD"]),
      repositoryDirty: optionalText("git", ["status", "--porcelain"]) !== "",
    },
    workload: {
      result: 42,
      validation: "each Wasm module has zero imports and exports main returning i64 42; the JVM class has CAFEBABE magic and exits successfully after checking answer() == 42",
      sources: {
        kotoba: { path: "bench/public-compile-comparison/main.kotoba", sha256: sha256(kotobaSource) },
        rust: { path: "scripts/benchmark-public-compile.mjs#rustSourceText", sha256: rustSourceSha256 },
        c: { path: "scripts/benchmark-public-compile.mjs#cSourceText", sha256: cSourceSha256 },
        jvm: { path: "scripts/benchmark-public-compile.mjs#javaSourceText", sha256: javaSourceSha256 },
      },
      commands: {
        kotoba: "kotoba compile main.kotoba --target wasm --output main.wasm --json",
        rust: "rustc --edition=2024 --crate-type=cdylib --target=wasm32-unknown-unknown -C opt-level=0 -C debuginfo=0 -C panic=abort -o main.wasm main.rs",
        c: "clang --target=wasm32 -O0 -nostdlib -Wl,--no-entry -Wl,--export=main -Wl,--strip-all -o main.wasm main.c",
        jvm: "javac -g:none -d <temp-directory> Main.java",
      },
    },
    results: Object.fromEntries(tools.map((tool) => [tool, {
      ...summaries[tool],
      artifactKind: tool === "jvm" ? "JVM class" : "WebAssembly module",
      artifactBytes: statSync(artifacts[tool]).size,
      artifactSha256: sha256(artifacts[tool]),
      medianRatioToKotoba: rounded(summaries[tool].medianMilliseconds / summaries.kotoba.medianMilliseconds),
    }])),
  };
  writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
} finally {
  rmSync(directory, { recursive: true, force: true });
}
