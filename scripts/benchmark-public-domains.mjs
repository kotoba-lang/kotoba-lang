#!/usr/bin/env node

import { execFileSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { performance } from "node:perf_hooks";

const root = resolve(import.meta.dirname, "..");
const output = resolve(root, option("--output", "bench/public-domain-comparison/latest.json"));
const kotobaCliOption = option("--kotoba-cli", "kotoba");
const kotobaCli = kotobaCliOption.includes("/") ? resolve(kotobaCliOption) : kotobaCliOption;
const runs = Number(option("--runs", "7"));
if (!Number.isInteger(runs) || runs < 7 || runs > 31 || runs % 2 === 0) {
  throw new Error("--runs must be an odd integer from 7 through 31");
}

function option(name, fallback) {
  const index = process.argv.indexOf(name);
  return index === -1 ? fallback : process.argv[index + 1];
}
function command(command, args = [], options = {}) {
  return execFileSync(command, args, { encoding: "utf8", ...options }).trim();
}
function optionalVersion(commandName, args = []) {
  const result = spawnSync(commandName, args, { encoding: "utf8" });
  return result.status === 0
    ? `${result.stdout || ""}${result.stderr || ""}`.trim().split("\n")[0]
    : "unavailable";
}
function write(path, contents) {
  mkdirSync(resolve(path, ".."), { recursive: true });
  writeFileSync(path, contents);
}
function sha256(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}
function rounded(value) { return Number(value.toFixed(3)); }
function percentile(values, fraction) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}
function measured(values) {
  return {
    status: "measured",
    medianMilliseconds: rounded(percentile(values, 0.5)),
    p95Milliseconds: rounded(percentile(values, 0.95)),
    minimumMilliseconds: rounded(Math.min(...values)),
    maximumMilliseconds: rounded(Math.max(...values)),
    samplesMilliseconds: values.map(rounded),
  };
}
function normalizedMeasured(values, divisor) {
  return measured(values.map(value => value / divisor));
}
function na(reason) { return { status: "not-applicable", reason }; }
function load1() {
  const result = spawnSync("sysctl", ["-n", "vm.loadavg"], { encoding: "utf8" });
  const match = result.stdout?.match(/\{\s*([0-9.]+)/);
  return match ? rounded(Number(match[1])) : null;
}
function runTimed(commandName, args, expected, options = {}) {
  const started = performance.now();
  const result = spawnSync(commandName, args, {
    encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], ...options,
  });
  const elapsed = performance.now() - started;
  if (result.status !== 0) {
    throw new Error(`${commandName} ${args.join(" ")} failed (${result.status}): ${result.stderr || result.stdout}`);
  }
  if (result.stdout.trim() !== String(expected)) {
    throw new Error(`${commandName} ${args.join(" ")} returned ${JSON.stringify(result.stdout.trim())}, expected ${expected}`);
  }
  return elapsed;
}
function compile(commandName, args, options = {}) {
  const result = spawnSync(commandName, args, { encoding: "utf8", ...options });
  if (result.status !== 0) {
    throw new Error(`${commandName} ${args.join(" ")} failed (${result.status}): ${result.stderr || result.stdout}`);
  }
}

const workloads = {
  string: {
    label: "String",
    iterations: 400,
    amortizedMultiplier: 100,
    expected: 6400,
    contract: "For each iteration, concatenate 'kotoba-' and 'language', then add its character count and one when it contains 'lang'.",
  },
  collection: {
    label: "Collection",
    iterations: 8,
    amortizedMultiplier: 1000,
    expected: 1216,
    contract: "Reuse a 16-element collection, map increment over it, and reduce by addition; one iteration returns 152.",
  },
  allocation: {
    label: "Allocation",
    iterations: 5,
    amortizedMultiplier: 500,
    expected: 760,
    contract: "For each iteration, allocate a 16-element collection and two mapped collections, then reduce; one iteration returns 152.",
  },
  io: {
    label: "I/O",
    iterations: 12,
    amortizedMultiplier: 3,
    expected: 1604321280,
    contract: "Read the same deterministic 1 MiB file 12 times and sum every unsigned byte.",
  },
  concurrency: {
    label: "Concurrency",
    iterations: 1000000,
    amortizedMultiplier: 8,
    expected: 4985653002,
    contract: "Run four workers, each performing 1,000,000 wrapping 32-bit LCG steps from seeds 1..4, and sum the final unsigned states.",
  },
  realApp: {
    label: "Real application",
    iterations: 8,
    amortizedMultiplier: 1000,
    expected: 64,
    contract: "Evaluate a fixed 16-request risk batch against an admission threshold and count admitted requests; one batch returns 8.",
  },
};

const directory = mkdtempSync(join(tmpdir(), "kotoba-public-domains-"));
const ioPath = join(directory, "payload.bin");
const ioBytes = Buffer.alloc(1024 * 1024);
let ioSum = 0;
for (let i = 0; i < ioBytes.length; i += 1) {
  ioBytes[i] = (i * 17 + 23) & 255;
  ioSum += ioBytes[i];
}
writeFileSync(ioPath, ioBytes);
workloads.io.expected = ioSum * workloads.io.iterations;

const kotobaRunner = join(directory, "run-kotoba.mjs");
write(kotobaRunner, String.raw`
import { readFile } from "node:fs/promises";
const [path, workload, iterationsText] = process.argv.slice(2);
const bytes = await readFile(path);
const literals = workload === "string" ? ["kotoba-", "kotoba-language", "lang", "language"] : [];
const imports = new Proxy({
  scratch: new WebAssembly.Memory({ initial: 2, maximum: 2 }),
  literal: (index) => literals[index],
  new: (tag) => ({ tag, items: [] }),
  "push-i64": (value, item) => { value.items.push(item); return value; },
  seal: (_descriptor, value) => value,
  "assert-ref": (_descriptor, value) => value,
  count: (_descriptor, value) => BigInt(value.length ?? value.items.length),
  "vector-at-i64": (_descriptor, value, index) => value.items[Number(index)],
  "vector-conj-i64": (_descriptor, value, item) => ({ tag: value.tag, items: [...value.items, item] }),
  "vector-from-memory-i64": (descriptor, offset, count) => {
    const view = new DataView(imports.scratch.buffer);
    const items = [];
    for (let index = 0; index < count; index += 1) {
      items.push(view.getBigInt64(offset + index * 8, true));
    }
    return Object.freeze({ tag: descriptor, items: Object.freeze(items) });
  },
  "string-concat": (_descriptor, left, right) => left + right,
  "string-contains": (_descriptor, value, part) => value.includes(part) ? 1 : 0,
}, { get(target, property) { return target[property] ?? (() => { throw new Error("unexpected typed import: " + String(property)); }); } });
const { instance } = await WebAssembly.instantiate(bytes, { "kotoba:typed": imports });
let answer = 0n;
for (let i = 0; i < Number(iterationsText); i += 1) answer += instance.exports.main();
console.log(answer.toString());
`);

const cSource = join(directory, "domain.c");
const cArtifact = join(directory, "domain-c");
write(cSource, String.raw`
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
typedef struct { uint32_t seed; uint64_t n; uint32_t out; } job;
static void *volatile allocation_sink;
static void *lcg(void *p) { job *j=p; uint32_t x=j->seed; for(uint64_t i=0;i<j->n;i++) x=x*1664525u+1013904223u; j->out=x; return 0; }
static uint64_t file_sum(const char *path) { FILE *f=fopen(path,"rb"); if(!f) exit(2); uint64_t s=0; unsigned char b[65536]; size_t n; while((n=fread(b,1,sizeof b,f))) for(size_t i=0;i<n;i++) s+=b[i]; fclose(f); return s; }
int main(int argc,char **argv){ const char *w=argv[1]; uint64_t n=strtoull(argv[2],0,10),s=0;
 if(!strcmp(w,"string")){for(uint64_t i=0;i<n;i++){char x[32]; strcpy(x,"kotoba-"); strcat(x,"language"); s+=strlen(x)+(strstr(x,"lang")!=0);}}
 else if(!strcmp(w,"collection")){int a[16]; for(int i=0;i<16;i++)a[i]=i+1; for(uint64_t k=0;k<n;k++)for(int i=0;i<16;i++)s+=a[i]+1;}
 else if(!strcmp(w,"allocation")){for(uint64_t k=0;k<n;k++){int *a=malloc(16*sizeof(int)),*b=malloc(16*sizeof(int)),*c=malloc(16*sizeof(int)); for(int i=0;i<16;i++){a[i]=i;b[i]=a[i]+1;c[i]=b[i]+1;s+=c[i];} allocation_sink=c; free(a);free(b);free(c);}}
 else if(!strcmp(w,"io")){for(uint64_t i=0;i<n;i++)s+=file_sum(argv[3]);}
 else if(!strcmp(w,"concurrency")){pthread_t t[4];job j[4];for(int i=0;i<4;i++){j[i]=(job){i+1,n,0};pthread_create(&t[i],0,lcg,&j[i]);}for(int i=0;i<4;i++){pthread_join(t[i],0);s+=j[i].out;}}
 else if(!strcmp(w,"realApp")){int risk[16]={12,55,33,91,4,50,49,72,18,66,40,88,7,51,21,99};for(uint64_t k=0;k<n;k++)for(int i=0;i<16;i++)s+=risk[i]<50;} printf("%llu\n",(unsigned long long)s); }
`);

const rustSource = join(directory, "domain.rs");
const rustArtifact = join(directory, "domain-rust");
write(rustSource, String.raw`
use std::{env,fs,thread};
fn lcg(seed:u32,n:u64)->u32{let mut x=seed;for _ in 0..n{x=x.wrapping_mul(1664525).wrapping_add(1013904223);}x}
fn main(){let a:Vec<String>=env::args().collect();let w=&a[1];let n:u64=a[2].parse().unwrap();let mut s=0u64;
match w.as_str(){
"string"=>for _ in 0..n{let x=["kotoba-","language"].concat();s+=x.chars().count() as u64+x.contains("lang") as u64},
"collection"=>{let a:Vec<u64>=(1..=16).collect();for _ in 0..n{s+=a.iter().map(|x|x+1).sum::<u64>()}},
"allocation"=>for _ in 0..n{let a:Vec<u64>=(0..16).collect();let b:Vec<u64>=a.iter().map(|x|x+1).collect();let c:Vec<u64>=b.iter().map(|x|x+1).collect();s+=std::hint::black_box(&c).iter().sum::<u64>()},
"io"=>for _ in 0..n{s+=fs::read(&a[3]).unwrap().iter().map(|x|*x as u64).sum::<u64>()},
"concurrency"=>{let mut hs=Vec::new();for seed in 1..=4{hs.push(thread::spawn(move||lcg(seed,n)));}for h in hs{s+=h.join().unwrap() as u64}},
"realApp"=>{let risk=[12,55,33,91,4,50,49,72,18,66,40,88,7,51,21,99];for _ in 0..n{s+=risk.iter().filter(|x|**x<50).count() as u64}},_=>panic!()};println!("{}",s)}
`);

const goSource = join(directory, "domain.go");
const goArtifact = join(directory, "domain-go");
write(goSource, String.raw`
package main
import("fmt";"os";"strconv";"strings")
var allocationSink any
func lcg(seed uint32,n uint64)uint32{x:=seed;for i:=uint64(0);i<n;i++{x=x*1664525+1013904223};return x}
func fileSum(path string)uint64{b,e:=os.ReadFile(path);if e!=nil{panic(e)};s:=uint64(0);for _,x:=range b{s+=uint64(x)};return s}
func main(){w:=os.Args[1];n,_:=strconv.ParseUint(os.Args[2],10,64);s:=uint64(0);switch w{case"string":for i:=uint64(0);i<n;i++{x:="kotoba-"+"language";s+=uint64(len([]rune(x)));if strings.Contains(x,"lang"){s++}};case"collection":a:=[]uint64{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};for k:=uint64(0);k<n;k++{b:=make([]uint64,len(a));for i,x:=range a{b[i]=x+1};for _,x:=range b{s+=x}};case"allocation":for k:=uint64(0);k<n;k++{a:=make([]uint64,16);b:=make([]uint64,16);c:=make([]uint64,16);for i:=range a{a[i]=uint64(i);b[i]=a[i]+1;c[i]=b[i]+1;s+=c[i]};allocationSink=c};case"io":for i:=uint64(0);i<n;i++{s+=fileSum(os.Args[3])};case"concurrency":ch:=make(chan uint32,4);for seed:=uint32(1);seed<=4;seed++{go func(x uint32){ch<-lcg(x,n)}(seed)};for i:=0;i<4;i++{s+=uint64(<-ch)};case"realApp":risk:=[]uint64{12,55,33,91,4,50,49,72,18,66,40,88,7,51,21,99};for k:=uint64(0);k<n;k++{for _,x:=range risk{if x<50{s++}}}};fmt.Println(s)}
`);

const javaSource = join(directory, "Domain.java");
const javaClasses = join(directory, "java-classes");
write(javaSource, String.raw`
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
public final class Domain{
 static long lcg(int seed,long n){int x=seed;for(long i=0;i<n;i++)x=x*1664525+1013904223;return Integer.toUnsignedLong(x);}
 static long fileSum(String p)throws Exception{long s=0;for(byte x:Files.readAllBytes(Path.of(p)))s+=Byte.toUnsignedInt(x);return s;}
 public static void main(String[]a)throws Exception{String w=a[0];long n=Long.parseLong(a[1]),s=0;switch(w){
 case"string"->{for(long i=0;i<n;i++){String x="kotoba-"+"language";s+=x.codePointCount(0,x.length())+(x.contains("lang")?1:0);}}
 case"collection"->{List<Long>x=new ArrayList<>();for(long i=1;i<=16;i++)x.add(i);for(long k=0;k<n;k++)s+=x.stream().mapToLong(v->v+1).sum();}
 case"allocation"->{for(long k=0;k<n;k++){List<Long>x=new ArrayList<>(),y=new ArrayList<>(),z=new ArrayList<>();for(long i=0;i<16;i++)x.add(i);for(long v:x)y.add(v+1);for(long v:y)z.add(v+1);for(long v:z)s+=v;}}
 case"io"->{for(long i=0;i<n;i++)s+=fileSum(a[2]);}
 case"concurrency"->{ExecutorService e=Executors.newFixedThreadPool(4);List<Future<Long>>f=new ArrayList<>();for(int seed=1;seed<=4;seed++){int q=seed;f.add(e.submit(()->lcg(q,n)));}for(Future<Long>q:f)s+=q.get();e.shutdown();}
 case"realApp"->{long[]risk={12,55,33,91,4,50,49,72,18,66,40,88,7,51,21,99};for(long k=0;k<n;k++)for(long x:risk)if(x<50)s++;}}
 System.out.println(s);}}
`);

const nodeSource = join(directory, "domain-node.mjs");
write(nodeSource, String.raw`
import{readFileSync}from"node:fs";import{Worker,isMainThread,parentPort,workerData}from"node:worker_threads";
function lcg(seed,n){let x=seed>>>0;for(let i=0;i<n;i++)x=(Math.imul(x,1664525)+1013904223)>>>0;return x}
if(!isMainThread){parentPort.postMessage(lcg(workerData.seed,workerData.n));}else{const[w,nt,path]=process.argv.slice(2),n=Number(nt);let s=0;
if(w==="string")for(let i=0;i<n;i++){const x="kotoba-"+"language";s+=[...x].length+(x.includes("lang")?1:0)}
else if(w==="collection"){const a=Array.from({length:16},(_,i)=>i+1);for(let k=0;k<n;k++)s+=a.map(x=>x+1).reduce((x,y)=>x+y,0)}
else if(w==="allocation")for(let k=0;k<n;k++)s+=Array.from({length:16},(_,i)=>i).map(x=>x+1).map(x=>x+1).reduce((x,y)=>x+y,0)
else if(w==="io")for(let i=0;i<n;i++)for(const x of readFileSync(path))s+=x;
else if(w==="concurrency"){s=await Promise.all([1,2,3,4].map(seed=>new Promise((ok,no)=>{const w=new Worker(new URL(import.meta.url),{workerData:{seed,n}});w.once("message",ok);w.once("error",no)}))).then(x=>x.reduce((a,b)=>a+b,0))}
else if(w==="realApp"){const risk=[12,55,33,91,4,50,49,72,18,66,40,88,7,51,21,99];for(let k=0;k<n;k++)for(const x of risk)if(x<50)s++}console.log(s)}
`);

compile("clang", ["-O2", "-pthread", "-o", cArtifact, cSource]);
compile("rustc", ["-C", "opt-level=2", "-o", rustArtifact, rustSource]);
compile("go", ["build", "-trimpath", "-o", goArtifact, goSource]);
mkdirSync(javaClasses);
compile("javac", ["-d", javaClasses, javaSource]);

const kotobaArtifacts = {};
for (const id of ["string", "collection", "allocation", "realApp"]) {
  const source = join(root, "bench", "public-domain-comparison", "probes", `${id}.kotoba`);
  const artifact = join(directory, `${id}.wasm`);
  compile(kotobaCli, ["compile", source, "--target", "wasm", "--fuel", "1048576", "-o", artifact]);
  kotobaArtifacts[id] = { source, artifact };
}

function lcg(seed, iterations) {
  let value = seed >>> 0;
  for (let index = 0; index < iterations; index += 1) {
    value = (Math.imul(value, 1664525) + 1013904223) >>> 0;
  }
  return value;
}
function expectedFor(id, multiplier = 1) {
  if (id === "concurrency") {
    const iterations = workloads[id].iterations * multiplier;
    return [1, 2, 3, 4].map(seed => lcg(seed, iterations)).reduce((sum, value) => sum + value, 0);
  }
  return workloads[id].expected * multiplier;
}
const tools = [
  { id:"kotoba", label:"Kotoba / Wasm + typed JS host", target:"WebAssembly in Node.js", version:option("--kotoba-version",optionalVersion(kotobaCli,["--version"])), artifact:kotobaArtifacts,
    run:(id,w,multiplier=1)=>runTimed(process.execPath,[kotobaRunner,w.artifact,id,String(workloads[id].iterations * multiplier)],expectedFor(id,multiplier)) },
  { id:"rust", label:"Rust", target:"arm64 macOS native", version:optionalVersion("rustc",["--version"]), artifact:rustArtifact,
    run:(id,_w,multiplier=1)=>runTimed(rustArtifact,[id,String(workloads[id].iterations * multiplier),ioPath],expectedFor(id,multiplier)) },
  { id:"c", label:"C / Clang", target:"arm64 macOS native", version:optionalVersion("clang",["--version"]), artifact:cArtifact,
    run:(id,_w,multiplier=1)=>runTimed(cArtifact,[id,String(workloads[id].iterations * multiplier),ioPath],expectedFor(id,multiplier)) },
  { id:"go", label:"Go", target:"arm64 macOS native", version:optionalVersion("go",["version"]), artifact:goArtifact,
    run:(id,_w,multiplier=1)=>runTimed(goArtifact,[id,String(workloads[id].iterations * multiplier),ioPath],expectedFor(id,multiplier)) },
  { id:"jvm", label:"JVM / Java", target:"JVM", version:optionalVersion("java",["-version"]), artifact:join(javaClasses,"Domain.class"),
    run:(id,_w,multiplier=1)=>runTimed("java",["-cp",javaClasses,"Domain",id,String(workloads[id].iterations * multiplier),ioPath],expectedFor(id,multiplier)) },
  { id:"javascript", label:"JavaScript / Node.js", target:"Node.js", version:optionalVersion("node",["--version"]), artifact:nodeSource,
    run:(id,_w,multiplier=1)=>runTimed(process.execPath,[nodeSource,id,String(workloads[id].iterations * multiplier),ioPath],expectedFor(id,multiplier)) },
];

const unsupported = {
  kotoba: {
    io: "The standalone Kotoba Wasm artifact has no ambient filesystem import; an admitted filesystem capability host is outside this comparison.",
    concurrency: "The current standalone Kotoba Wasm target exposes no shared-memory/thread contract.",
  },
};
const samples = Object.fromEntries(tools.map(t=>[t.id,{}]));
const amortizedSamples = Object.fromEntries(tools.map(t=>[t.id,{}]));
const loadBefore = load1();

// Correctness warm-up, then rotate tool order on every sample to reduce drift bias.
for (const tool of tools) for (const id of Object.keys(workloads)) {
  if (!unsupported[tool.id]?.[id]) {
    tool.run(id, kotobaArtifacts[id]);
    tool.run(id, kotobaArtifacts[id], workloads[id].amortizedMultiplier);
  }
}
for (let sample = 0; sample < runs; sample += 1) {
  for (const id of Object.keys(workloads)) {
    const rotated = [...tools.slice(sample % tools.length), ...tools.slice(0, sample % tools.length)];
    for (const tool of rotated) {
      if (!unsupported[tool.id]?.[id]) (samples[tool.id][id] ??= []).push(tool.run(id, kotobaArtifacts[id]));
      if (!unsupported[tool.id]?.[id]) {
        const multiplier = workloads[id].amortizedMultiplier;
        (amortizedSamples[tool.id][id] ??= []).push(tool.run(id, kotobaArtifacts[id], multiplier));
      }
    }
  }
}
const loadAfter = load1();
const maxLoad = Math.max(loadBefore ?? 0, loadAfter ?? 0);
const logicalCpu = Number(command("sysctl",["-n","hw.logicalcpu"]));
const qualified = maxLoad <= logicalCpu * 0.75;

const report = {
  schema:"kotoba.public-domain-comparison.v2",
  generatedAt:new Date().toISOString(),
  repository:{commit:command("git",["rev-parse","HEAD"],{cwd:root}),dirty:command("git",["status","--porcelain"],{cwd:root})!==""},
  machine:{os:optionalVersion("sw_vers",["-productVersion"]),architecture:process.arch,cpu:command("sysctl",["-n","machdep.cpu.brand_string"]),logicalCpu,load1Before:loadBefore,load1After:loadAfter},
  qualification:{qualified,rule:"max(load1 before, load1 after) <= 0.75 * logical CPU count",note:qualified?"Medians may be compared only within each workload and target/runtime contract.":"Host load exceeded the publication gate; values are observations, not qualified rankings."},
  method:{runs,warmups:1,processBoundary:"Every sample starts a fresh process; concurrency workers are created inside that process.",measurement:"processCold is wall-clock process startup plus workload execution. amortizedExecution runs a larger in-process batch and divides elapsed time by its workload-specific multiplier; it amortizes but does not fully remove process, VM, or module startup, and is not claimed as a perfectly warmed steady-state value. Compilation is excluded.",correctness:"Every process-cold and amortized sample must print its independently scaled reference checksum exactly.",scope:"Representative six-runtime comparison; targets, allocation models, JIT/AOT, and host contracts differ.",kotobaFuel:1048576},
  kotobaOptimization:{scope:"Pure unary inc/dec map chains consumed by a pure primitive reduce",semantics:"Input collection is evaluated once; callbacks outside the proven subset retain eager materialization.",structuralEvidence:"Fused HIR contains no vector-conj; the fallback regression retains vector-conj.",revisions:{sema:"8676e3d60a8372667b3b427b01d214f7684d443f",amu:"1e9b99617871f19fc28fa1bba80fc17544571b64",cli:"b94228d5e3d60d35fe1e642b7f52706a3426645e"}},
  workloads,
  tools:tools.map(tool=>({id:tool.id,label:tool.label,target:tool.target,version:tool.version,artifactSha256:tool.id==="kotoba"?Object.fromEntries(Object.entries(kotobaArtifacts).map(([id,x])=>[id,sha256(x.artifact)])):sha256(tool.artifact),results:Object.fromEntries(Object.keys(workloads).map(id=>[id,unsupported[tool.id]?.[id]?na(unsupported[tool.id][id]):measured(samples[tool.id][id])])),amortizedResults:Object.fromEntries(Object.keys(workloads).map(id=>[id,unsupported[tool.id]?.[id]?na(unsupported[tool.id][id]):normalizedMeasured(amortizedSamples[tool.id][id],workloads[id].amortizedMultiplier)]))})),
 };
write(output,`${JSON.stringify(report,null,2)}\n`);
rmSync(directory,{recursive:true,force:true});
console.log(output);
