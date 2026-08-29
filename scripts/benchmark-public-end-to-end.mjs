#!/usr/bin/env node

import { execFileSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync,
  rmSync, statSync, writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { basename, join, resolve } from "node:path";
import { performance } from "node:perf_hooks";

const root = resolve(import.meta.dirname, "..");
const output = resolve(root, option("--output", "bench/public-end-to-end-comparison/latest.json"));
const runs = Number(option("--runs", "7"));
if (!Number.isInteger(runs) || runs < 7 || runs > 31 || runs % 2 === 0) {
  throw new Error("--runs must be an odd integer from 7 through 31");
}

function option(name, fallback) {
  const index = process.argv.indexOf(name);
  return index === -1 ? fallback : process.argv[index + 1];
}

function text(command, args = [], options = {}) {
  return execFileSync(command, args, { encoding: "utf8", ...options }).trim();
}

function optionalVersion(command, args = []) {
  const result = spawnSync(command, args, { encoding: "utf8" });
  if (result.status !== 0) return "unavailable";
  return `${result.stdout || ""}${result.stderr || ""}`.trim().split("\n")[0];
}

function rounded(value) { return Number(value.toFixed(3)); }
function percentile(values, fraction) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}
function summary(values) {
  return {
    status: "measured",
    medianMilliseconds: rounded(percentile(values, 0.5)),
    p95Milliseconds: rounded(percentile(values, 0.95)),
    minimumMilliseconds: rounded(Math.min(...values)),
    maximumMilliseconds: rounded(Math.max(...values)),
    samplesMilliseconds: values.map(rounded),
  };
}
function na(reason) { return { status: "not-applicable", reason }; }
function load1() {
  const result = spawnSync("sysctl", ["-n", "vm.loadavg"], { encoding: "utf8" });
  const match = result.stdout?.match(/\{\s*([0-9.]+)/);
  return match ? rounded(Number(match[1])) : null;
}
function sha256(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}
function write(path, contents) {
  mkdirSync(resolve(path, ".."), { recursive: true });
  writeFileSync(path, contents);
}
function timed(command, args, options = {}) {
  const started = performance.now();
  const result = spawnSync(command, args, {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    ...options,
  });
  const elapsed = performance.now() - started;
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(" ")} failed (${result.status}): ${result.stderr || result.stdout}`);
  }
  return elapsed;
}
function untimed(command, args, options = {}) {
  const result = spawnSync(command, args, {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    ...options,
  });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(" ")} failed (${result.status}): ${result.stderr || result.stdout}`);
  }
  return result;
}
function findFile(directory, filename) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      const nested = findFile(path, filename);
      if (nested) return nested;
    } else if (entry.name === filename) return path;
  }
  return null;
}

const directory = mkdtempSync(join(tmpdir(), "kotoba-public-end-to-end-"));
const nodeRunner = join(directory, "run-wasm.mjs");
write(nodeRunner, `import { readFile } from "node:fs/promises";\nconst bytes = await readFile(process.argv[2]);\nconst { instance } = await WebAssembly.instantiate(bytes, {});\nconst value = instance.exports.main();\nif (Number(value) !== 42) throw new Error(\`wrong result: \${value}\`);\n`);

const nativeAotLibraryPath = ["openssl@3", "brotli"]
  .map((formula) => join(text("brew", ["--prefix", formula]), "lib"))
  .join(":");
const dotnetEnv = {
  ...process.env,
  DOTNET_CLI_TELEMETRY_OPTOUT: "1",
  DOTNET_NOLOGO: "1",
  DOTNET_SKIP_FIRST_TIME_EXPERIENCE: "1",
  LIBRARY_PATH: process.env.LIBRARY_PATH
    ? `${nativeAotLibraryPath}:${process.env.LIBRARY_PATH}`
    : nativeAotLibraryPath,
};

function nativeRunner(path) {
  return () => timed(path, []);
}
function wasmRunner(path) {
  return () => timed(process.execPath, [nodeRunner, path]);
}

function makeTools() {
  const tools = [];

  // Kotoba: exercise the public project lifecycle command, not a Clojure entrypoint.
  {
    const dir = join(directory, "kotoba-app");
    untimed("kotoba", ["rad", "new", "--project", dir, "--json"]);
    const artifact = join(dir, "target", "kotoba_app.wasm");
    const source = join(dir, "src", "kotoba_app.kotoba");
    tools.push({
      id: "kotoba", label: "Kotoba", target: "WebAssembly", artifactKind: "WebAssembly module",
      version: text("brew", ["list", "--versions", "kotoba"]), source,
      resolve: null,
      check: () => timed("kotoba", ["check", source, "--kind", "source", "--json"]),
      clean: () => rmSync(join(dir, "target"), { recursive: true, force: true }),
      build: () => timed("kotoba", ["build", "--project", dir, "--json"]),
      artifact: () => artifact,
      run: wasmRunner(artifact), incremental: "project command; current implementation re-emits the target",
    });
  }

  {
    const dir = join(directory, "rust-app");
    write(join(dir, "Cargo.toml"), `[package]\nname = "bench-rust"\nversion = "0.1.0"\nedition = "2024"\n`);
    write(join(dir, "src", "main.rs"), `fn answer() -> i64 { 40 + 2 }\nfn main() { assert_eq!(answer(), 42); }\n`);
    const target = join(dir, "target");
    const artifact = join(target, "debug", "bench-rust");
    tools.push({
      id: "rust", label: "Rust / Cargo", target: "arm64 macOS native", artifactKind: "Mach-O executable",
      version: optionalVersion("rustc", ["--version"]), source: join(dir, "src", "main.rs"),
      resolve: () => timed("cargo", ["metadata", "--offline", "--no-deps", "--format-version", "1"], { cwd: dir }),
      check: () => timed("cargo", ["check", "--offline", "--target-dir", join(dir, "check-target")], { cwd: dir }),
      clean: () => rmSync(target, { recursive: true, force: true }),
      build: () => timed("cargo", ["build", "--offline", "--target-dir", target], { cwd: dir }),
      artifact: () => artifact, run: nativeRunner(artifact), incremental: "Cargo no-change build cache",
    });
  }

  {
    const dir = join(directory, "c-app");
    const source = join(dir, "main.c");
    const artifact = join(dir, "main");
    write(source, `static long long answer(void) { return 40 + 2; }\nint main(void) { return answer() == 42 ? 0 : 1; }\n`);
    tools.push({
      id: "c", label: "C / Clang", target: "arm64 macOS native", artifactKind: "Mach-O executable",
      version: optionalVersion("clang", ["--version"]), source,
      resolve: null, check: () => timed("clang", ["-fsyntax-only", source]),
      clean: () => rmSync(artifact, { force: true }),
      build: () => timed("clang", ["-O0", "-o", artifact, source]),
      artifact: () => artifact, run: nativeRunner(artifact), incremental: "direct compiler recompile; no project cache",
    });
  }

  {
    const dir = join(directory, "zig-app");
    const source = join(dir, "main.zig");
    const artifact = join(dir, "main.wasm");
    const cache = join(dir, "zig-cache");
    const globalCache = join(dir, "zig-global-cache");
    write(source, `export fn main() i64 { return 40 + 2; }\n`);
    const common = [source, "-target", "wasm32-freestanding", "-O", "Debug", `--cache-dir`, cache, `--global-cache-dir`, globalCache];
    tools.push({
      id: "zig", label: "Zig", target: "WebAssembly", artifactKind: "WebAssembly module",
      version: optionalVersion("zig", ["version"]), source,
      resolve: null, check: () => timed("zig", ["build-obj", ...common, "-fno-emit-bin"]),
      clean: () => { rmSync(artifact, { force: true }); rmSync(cache, { recursive: true, force: true }); },
      build: () => timed("zig", ["build-exe", ...common, "-fno-entry", "-rdynamic", `-femit-bin=${artifact}`]),
      artifact: () => artifact, run: wasmRunner(artifact), incremental: "Zig local cache",
    });
  }

  {
    const dir = join(directory, "tinygo-app");
    const source = join(dir, "main.go");
    const artifact = join(dir, "main");
    const cache = join(dir, "go-cache");
    write(source, `package main\nfunc answer() int64 { return 40 + 2 }\nfunc main() { if answer() != 42 { panic("wrong result") } }\n`);
    const env = { ...process.env, GOCACHE: cache };
    tools.push({
      id: "tinygo", label: "TinyGo", target: "arm64 macOS native", artifactKind: "Mach-O executable",
      version: optionalVersion("tinygo", ["version"]), source,
      resolve: null, check: null,
      clean: () => { rmSync(artifact, { force: true }); rmSync(cache, { recursive: true, force: true }); },
      build: () => timed("tinygo", ["build", "-o", artifact, source], { env }),
      artifact: () => artifact, run: nativeRunner(artifact), incremental: "TinyGo/Go build cache",
    });
  }

  {
    const dir = join(directory, "go-app");
    const source = join(dir, "main.go");
    const artifact = join(dir, "main");
    const cache = join(dir, "go-cache");
    write(join(dir, "go.mod"), `module example.invalid/bench-go\n\ngo 1.25\n`);
    write(source, `package main\nfunc answer() int64 { return 40 + 2 }\nfunc main() { if answer() != 42 { panic("wrong result") } }\n`);
    const env = { ...process.env, GOCACHE: cache, GOMODCACHE: join(dir, "go-mod-cache") };
    tools.push({
      id: "go", label: "Go", target: "arm64 macOS native", artifactKind: "Mach-O executable",
      version: optionalVersion("go", ["version"]), source,
      resolve: () => timed("go", ["mod", "download"], { cwd: dir, env }),
      check: () => timed("go", ["vet", "./..."], { cwd: dir, env }),
      clean: () => { rmSync(artifact, { force: true }); rmSync(cache, { recursive: true, force: true }); },
      build: () => timed("go", ["build", "-trimpath", "-o", artifact, "."], { cwd: dir, env }),
      artifact: () => artifact, run: nativeRunner(artifact), incremental: "Go build cache",
    });
  }

  {
    const dir = join(directory, "swift-app");
    const source = join(dir, "Sources", "BenchSwift", "main.swift");
    const scratch = join(dir, ".build");
    write(join(dir, "Package.swift"), `// swift-tools-version: 6.2\nimport PackageDescription\nlet package = Package(name: "BenchSwift", targets: [.executableTarget(name: "BenchSwift")])\n`);
    write(source, `func answer() -> Int64 { 40 + 2 }\nprecondition(answer() == 42)\n`);
    let artifact = null;
    const build = () => {
      const elapsed = timed("swift", ["build", "-c", "debug", "--package-path", dir, "--scratch-path", scratch]);
      artifact = findFile(scratch, "BenchSwift");
      if (!artifact) throw new Error("Swift build did not emit BenchSwift");
      return elapsed;
    };
    tools.push({
      id: "swift", label: "Swift / SwiftPM", target: "arm64 macOS native", artifactKind: "Mach-O executable",
      version: optionalVersion("swift", ["--version"]), source,
      resolve: () => timed("swift", ["package", "resolve", "--package-path", dir]),
      check: () => timed("swiftc", ["-typecheck", source]),
      clean: () => { rmSync(scratch, { recursive: true, force: true }); artifact = null; },
      build, artifact: () => artifact, run: () => timed(artifact, []), incremental: "SwiftPM no-change build cache",
    });
  }

  {
    const dir = join(directory, "java-app");
    const source = join(dir, "Main.java");
    const classes = join(dir, "classes");
    const artifact = join(classes, "Main.class");
    write(source, `public final class Main {\n  static long answer() { return 40L + 2L; }\n  public static void main(String[] args) { if (answer() != 42L) throw new AssertionError(); }\n}\n`);
    tools.push({
      id: "jvm", label: "JVM / javac", target: "JVM class", artifactKind: "JVM class",
      version: optionalVersion("javac", ["-version"]), source,
      resolve: null, check: null,
      clean: () => rmSync(classes, { recursive: true, force: true }),
      build: () => { mkdirSync(classes, { recursive: true }); return timed("javac", ["-g:none", "-d", classes, source]); },
      artifact: () => artifact,
      run: () => timed("java", ["-cp", classes, "Main"]), incremental: "javac direct recompile; no project cache",
    });
  }

  {
    const dir = join(directory, "assemblyscript-app");
    const source = join(dir, "main.ts");
    const artifact = join(dir, "main.wasm");
    const asc = join(root, "node_modules", "assemblyscript", "bin", "asc.js");
    write(source, `export function main(): i64 { return 40 + 2; }\n`);
    const args = [asc, source, "--outFile", artifact, "--runtime", "stub", "--optimizeLevel", "0", "--shrinkLevel", "0", "--noAssert"];
    tools.push({
      id: "assemblyscript", label: "AssemblyScript", target: "WebAssembly", artifactKind: "WebAssembly module",
      version: `assemblyscript ${JSON.parse(readFileSync(join(root, "node_modules", "assemblyscript", "package.json"))).version}`, source,
      resolve: null, check: () => timed(process.execPath, [asc, source, "--noEmit"]),
      clean: () => rmSync(artifact, { force: true }),
      build: () => timed(process.execPath, args), artifact: () => artifact,
      run: wasmRunner(artifact), incremental: "direct compiler recompile; no project cache",
    });
  }

  function dotnetTool(id, label, nativeAot) {
    const dir = join(directory, `${id}-app`);
    const out = join(dir, "out");
    const csproj = join(dir, "Bench.csproj");
    write(csproj, `<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net10.0</TargetFramework><AssemblyName>Bench</AssemblyName><PublishAot>${nativeAot}</PublishAot><InvariantGlobalization>true</InvariantGlobalization></PropertyGroup></Project>`);
    const source = join(dir, "Program.cs");
    write(source, `static long Answer() => 40L + 2L;\nif (Answer() != 42L) throw new System.Exception("wrong result");\n`);
    const restoreArgs = nativeAot ? ["restore", "-r", "osx-arm64"] : ["restore"];
    const restore = () => timed("dotnet", restoreArgs, { cwd: dir, env: dotnetEnv });
    untimed("dotnet", restoreArgs, { cwd: dir, env: dotnetEnv });
    const artifact = nativeAot ? join(out, "Bench") : join(out, "Bench.dll");
    return {
      id, label, target: nativeAot ? "arm64 macOS Native AOT" : ".NET IL", artifactKind: nativeAot ? "Mach-O executable" : ".NET assembly",
      version: `dotnet ${optionalVersion("dotnet", ["--version"])}`, source,
      resolve: restore, check: null,
      clean: () => {
        rmSync(join(dir, "bin"), { recursive: true, force: true });
        rmSync(join(dir, "obj"), { recursive: true, force: true });
        rmSync(out, { recursive: true, force: true });
        untimed("dotnet", restoreArgs, { cwd: dir, env: dotnetEnv });
      },
      build: () => nativeAot
        ? timed("dotnet", ["publish", "--no-restore", "-c", "Release", "-r", "osx-arm64", "-o", out, "/p:UseSharedCompilation=false", "/nodeReuse:false"], { cwd: dir, env: dotnetEnv })
        : timed("dotnet", ["build", "--no-restore", "-c", "Debug", "-o", out, "/p:UseSharedCompilation=false", "/nodeReuse:false"], { cwd: dir, env: dotnetEnv }),
      artifact: () => artifact,
      run: nativeAot ? nativeRunner(artifact) : () => timed("dotnet", [artifact], { env: dotnetEnv }),
      incremental: nativeAot ? ".NET Native AOT publish cache" : "MSBuild no-change build cache",
    };
  }
  tools.push(dotnetTool("dotnet-il", ".NET IL", false));
  tools.push(dotnetTool("dotnet-aot", ".NET Native AOT", true));

  return tools;
}

const observedLoad1First = load1();
try {
  const tools = makeTools();
  const samples = Object.fromEntries(tools.map(({ id }) => [id, {
    resolve: [], check: [], cleanBuild: [], noChangeBuild: [], startupExecute: [],
    cleanBuildAndRun: [], noChangeBuildAndRun: [],
  }]));

  for (const tool of tools) {
    if (tool.resolve) tool.resolve();
    if (tool.check) tool.check();
    tool.clean();
    tool.build();
    tool.run();
    tool.build();
    tool.run();
  }

  for (let index = 0; index < runs; index += 1) {
    const offset = index % tools.length;
    const order = tools.slice(offset).concat(tools.slice(0, offset));
    for (const tool of order) {
      const bucket = samples[tool.id];
      if (tool.resolve) bucket.resolve.push(tool.resolve());
      if (tool.check) bucket.check.push(tool.check());
      tool.clean();
      const cleanBuild = tool.build();
      if (!existsSync(tool.artifact())) throw new Error(`${tool.id} did not emit ${tool.artifact()}`);
      const cleanRun = tool.run();
      const noChangeBuild = tool.build();
      const noChangeRun = tool.run();
      bucket.cleanBuild.push(cleanBuild);
      bucket.noChangeBuild.push(noChangeBuild);
      bucket.startupExecute.push(cleanRun);
      bucket.cleanBuildAndRun.push(cleanBuild + cleanRun);
      bucket.noChangeBuildAndRun.push(noChangeBuild + noChangeRun);
    }
  }

  const observedLoad1Last = load1();
  const quietLoad1Limit = 1.0;
  const speedQualified = observedLoad1First !== null && observedLoad1Last !== null
    && observedLoad1First <= quietLoad1Limit && observedLoad1Last <= quietLoad1Limit;
  const results = Object.fromEntries(tools.map((tool) => {
    const artifact = tool.artifact();
    const bucket = samples[tool.id];
    return [tool.id, {
      label: tool.label,
      version: tool.version,
      target: tool.target,
      artifactKind: tool.artifactKind,
      sourceSha256: sha256(tool.source),
      artifactBytes: statSync(artifact).size,
      artifactSha256: sha256(artifact),
      incrementalContract: tool.incremental,
      stages: {
        dependencyResolution: tool.resolve ? summary(bucket.resolve) : na("direct dependency-free compiler path has no separate resolver stage"),
        checkOrAdmission: tool.check ? summary(bucket.check) : na("toolchain exposes no separate check-only phase in this harness"),
        cleanBuild: summary(bucket.cleanBuild),
        noChangeBuild: summary(bucket.noChangeBuild),
        processColdStartupAndExecution: summary(bucket.startupExecute),
      },
      loops: {
        cleanBuildAndFirstRun: summary(bucket.cleanBuildAndRun),
        noChangeBuildAndRun: summary(bucket.noChangeBuildAndRun),
      },
    }];
  }));
  const report = {
    format: "kotoba.public-end-to-end-comparison/v1",
    generatedAt: new Date().toISOString(),
    scope: "dependency-free tiny project whose observable result is 42; natural project/native targets except Kotoba, Zig, and AssemblyScript WebAssembly",
    interpretation: "Targets, language semantics, runtime contracts, separate-stage availability, and compiler work differ. N/A is never treated as zero. This is a developer-loop observation, not a universal language ranking.",
    method: {
      runs,
      warmupsPerTool: 1,
      ordering: "all toolchains rotate through each starting position",
      cleanPreparationOutsideTimedRegion: true,
      correctness: "every emitted artifact is started in a fresh process and rejects a result other than 42",
      totals: "paired per-round build plus process-cold startup/execution wall time",
    },
    speedQualification: {
      verdict: speedQualified ? "qualified-host-load" : "unqualified-host-load",
      endToEndRankingQualified: speedQualified,
      quietLoad1Limit,
      observedLoad1First,
      observedLoad1Last,
      explanation: speedQualified
        ? "The host-load gate passed; conclusions remain limited to this exact tiny workload."
        : "Correctness and timing samples are retained, but the host-load gate failed; do not use this run for a fastest claim.",
    },
    environment: {
      os: text("uname", ["-srv"]), architecture: text("uname", ["-m"]),
      chip: text("sysctl", ["-n", "machdep.cpu.brand_string"]),
      memoryBytes: Number(text("sysctl", ["-n", "hw.memsize"])), node: process.version,
      repositoryCommit: text("git", ["rev-parse", "HEAD"], { cwd: root }),
      repositoryDirty: text("git", ["status", "--porcelain"], { cwd: root }) !== "",
    },
    coverage: {
      measuredToolchains: tools.map(({ id }) => id),
      runtimeSuite: "bench/public-runtime-comparison/latest.json covers six native compute/control-flow workloads for Amu, Rust, C, Zig, Go, and Swift",
      notYetComparable: ["strings", "collections", "allocation", "I/O", "concurrency", "whole applications"],
    },
    results,
  };
  mkdirSync(resolve(output, ".."), { recursive: true });
  writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
} finally {
  rmSync(directory, { recursive: true, force: true });
}
