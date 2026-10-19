(ns num.wgsl
  "The WGSL/WebGPU backend SPEC — the portable-primary GPU path.

  One WGSL compute shader, written once, is compiled by wgpu/WebGPU to each GPU's
  NATIVE ISA: **Metal MSL** on Apple, **SPIR-V** on Vulkan (AMD/ROCm-class and
  NVIDIA), **DXIL** on D3D12, and runs in a browser tab unchanged. That is how
  num-clj reaches 'optimized for each GPU' without writing three vendor kernels —
  the driver does the per-GPU codegen and scheduling.

  num-clj ships NO native code. Execution is HOST-INJECTED through `IGpuDevice`
  (the same port-injection pattern as the actors' Store/Advisor): a browser host
  implements it over the `navigator.gpu` WebGPU API from ClojureScript; a JVM/
  native host implements it over a wgpu binding. A `WgslBackend` (host-side)
  satisfies `num.protocol/IBackend` by binding buffers and dispatching the shaders
  below, falling back to a host read-compute-write for any op it has not yet
  accelerated — so it is always a COMPLETE backend, GPU-fast on the hot kernels.

  The shaders here are the real, reviewable artifacts; the contract test
  (`num.contract`) is what proves a live WgslBackend ≡ the CPU reference."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Host port — the seam the browser / native host fills in (no native code here)
;; ---------------------------------------------------------------------------

(defprotocol IGpuDevice
  "WebGPU/wgpu primitives provided by the host. Buffers are f32 storage unless
  noted; `usage` ∈ #{:storage :uniform :read}. Pure data in, handles out."
  (-create-buffer [dev n usage] "Allocate an n-element device buffer → buf handle.")
  (-write-buffer [dev buf xs] "Upload host seq `xs` into `buf`.")
  (-read-buffer [dev buf n] "Download `n` elements from `buf` → host vector.")
  (-compile [dev wgsl-src entry] "Compile a WGSL module's `entry` fn → pipeline.")
  (-dispatch [dev pipeline buffers workgroups]
    "Run `pipeline` with `buffers` bound at @binding 0.. and `workgroups`
     [x y z] workgroup counts."))

(defprotocol IGpuDeviceLifecycle
  "Optional explicit lifetime hook for device buffers."
  (-destroy-buffer [dev buffer]))

(defprotocol IGpuDeviceDType
  "Optional physical typed-buffer operations. `n` is an element count."
  (-create-buffer-dtype [dev n usage dtype])
  (-write-buffer-dtype [dev buf xs dtype])
  (-read-buffer-dtype [dev buf n dtype]))

;; ---------------------------------------------------------------------------
;; Compute shaders (WGSL) — compiled per-GPU by wgpu
;; ---------------------------------------------------------------------------

(def axpy-wgsl
  "Level-1 AXPY: y ← αx + y. One invocation per element, 64-wide workgroups."
  "
@group(0) @binding(0) var<storage, read>       x: array<f32>;
@group(0) @binding(1) var<storage, read_write> y: array<f32>;
@group(0) @binding(2) var<uniform>             alpha: f32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i >= arrayLength(&y)) { return; }
  y[i] = alpha * x[i] + y[i];
}")

(def spmv-csr-wgsl
  "Sparse mat-vec y = A·x, A in CSR. One thread per row accumulates its nonzeros —
  the standard scalar-CSR kernel (coalescing-friendly enough for the
  verification-scale systems nagare/kudaki Krylov solvers throw at it)."
  "
@group(0) @binding(0) var<storage, read>       row_ptr: array<u32>;
@group(0) @binding(1) var<storage, read>       col_idx: array<u32>;
@group(0) @binding(2) var<storage, read>       vals:    array<f32>;
@group(0) @binding(3) var<storage, read>       x:       array<f32>;
@group(0) @binding(4) var<storage, read_write> y:       array<f32>;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i >= arrayLength(&y)) { return; }
  var s: f32 = 0.0;
  let a = row_ptr[i];
  let b = row_ptr[i + 1u];
  for (var p: u32 = a; p < b; p = p + 1u) {
    s = s + vals[p] * x[col_idx[p]];
  }
  y[i] = s;
}")

(def gemm-tiled-wgsl
  "Dense GEMM C = A·B (A m×k, B k×n, row-major) with 16×16 SHARED-MEMORY TILING —
  the canonical optimized GPU kernel: each 16×16 tile of C is computed by a
  workgroup that streams matching tiles of A and B through workgroup memory,
  cutting global-memory traffic by ~16×. This is where the GPU win lives."
  "
const TILE: u32 = 16u;
@group(0) @binding(0) var<storage, read>       A: array<f32>;
@group(0) @binding(1) var<storage, read>       B: array<f32>;
@group(0) @binding(2) var<storage, read_write> C: array<f32>;
@group(0) @binding(3) var<uniform>             dims: vec3<u32>;   // (m, k, n)
var<workgroup> As: array<array<f32, 16>, 16>;
var<workgroup> Bs: array<array<f32, 16>, 16>;
@compute @workgroup_size(16, 16)
fn main(@builtin(local_invocation_id) lid: vec3<u32>,
        @builtin(workgroup_id)        wid: vec3<u32>) {
  let m = dims.x; let k = dims.y; let n = dims.z;
  let row = wid.y * TILE + lid.y;
  let col = wid.x * TILE + lid.x;
  var acc: f32 = 0.0;
  let ntiles = (k + TILE - 1u) / TILE;
  for (var t: u32 = 0u; t < ntiles; t = t + 1u) {
    let aCol = t * TILE + lid.x;
    let bRow = t * TILE + lid.y;
    As[lid.y][lid.x] = select(0.0, A[row * k + aCol], row < m && aCol < k);
    Bs[lid.y][lid.x] = select(0.0, B[bRow * n + col], bRow < k && col < n);
    workgroupBarrier();
    for (var i: u32 = 0u; i < TILE; i = i + 1u) {
      acc = acc + As[lid.y][i] * Bs[i][lid.x];
    }
    workgroupBarrier();
  }
  if (row < m && col < n) { C[row * n + col] = acc; }
}")

(def reduce-sum-wgsl
  "Tree reduction: each 256-wide workgroup sums its slice into `partials[wid]`;
  the host (or a second pass) sums the partials. The pattern `dot`/`nrm2`/`sum`
  share."
  "
@group(0) @binding(0) var<storage, read>       x:        array<f32>;
@group(0) @binding(1) var<storage, read_write> partials: array<f32>;
var<workgroup> sdata: array<f32, 256>;
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) gid: vec3<u32>,
        @builtin(local_invocation_id)  lid: vec3<u32>,
        @builtin(workgroup_id)         wid: vec3<u32>) {
  sdata[lid.x] = select(0.0, x[gid.x], gid.x < arrayLength(&x));
  workgroupBarrier();
  var s: u32 = 128u;
  loop {
    if (s == 0u) { break; }
    if (lid.x < s) { sdata[lid.x] = sdata[lid.x] + sdata[lid.x + s]; }
    workgroupBarrier();
    s = s / 2u;
  }
  if (lid.x == 0u) { partials[wid.x] = sdata[0]; }
}")

(def scal-wgsl
  "Level-1 SCAL: x ← αx, in place."
  "
@group(0) @binding(0) var<storage, read_write> x: array<f32>;
@group(0) @binding(1) var<uniform>             alpha: f32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i >= arrayLength(&x)) { return; }
  x[i] = alpha * x[i];
}")

(def ewise-wgsl
  "Elementwise z = op(x,y); op ∈ {0:add 1:sub 2:mul 3:div} via a uniform."
  "
@group(0) @binding(0) var<storage, read>       x: array<f32>;
@group(0) @binding(1) var<storage, read>       y: array<f32>;
@group(0) @binding(2) var<storage, read_write> z: array<f32>;
@group(0) @binding(3) var<uniform>             op: u32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i >= arrayLength(&z)) { return; }
  let a = x[i]; let b = y[i]; var r: f32 = 0.0;
  switch op { case 0u { r = a + b; } case 1u { r = a - b; }
              case 2u { r = a * b; } case 3u { r = a / b; } default { r = 0.0; } }
  z[i] = r;
}")

(def ewise1-wgsl
  "UNARY elementwise activation/derivative selected by a uniform. Same
  shape as ewise-wgsl, one input instead of two — the primitive softmax/attention
  need that the level-1/level-2/level-3 BLAS set doesn't provide."
  "
fn erf_approx(x: f32) -> f32 {
  let s = select(1.0, -1.0, x < 0.0);
  let a = abs(x);
  let t = 1.0 / (1.0 + 0.3275911 * a);
  let p = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 +
          t * (-1.453152027 + t * 1.061405429))));
  return s * (1.0 - p * exp(-a * a));
}
fn gelu_exact(x: f32) -> f32 {
  return 0.5 * x * (1.0 + erf_approx(x * 0.7071067811865476));
}
fn gelu_exact_grad(x: f32) -> f32 {
  return 0.5 * (1.0 + erf_approx(x * 0.7071067811865476)) +
         x * 0.3989422804014327 * exp(-0.5 * x * x);
}
@group(0) @binding(0) var<storage, read>       x: array<f32>;
@group(0) @binding(1) var<storage, read_write> z: array<f32>;
@group(0) @binding(2) var<uniform>             op: u32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i >= arrayLength(&z)) { return; }
  let a = x[i]; var r: f32 = 0.0;
  switch op { case 0u { r = exp(a); } case 1u { r = max(a, 0.0); }
              case 2u { r = -a; }
              case 3u { r = a / (1.0 + exp(-a)); }
              case 4u { r = 1.0 / (1.0 + exp(-a)); }
              case 5u { r = tanh(a); }
              case 6u { r = a * (1.0 - a); }
              case 7u { r = 1.0 - a * a; }
              case 8u { r = gelu_exact(a); }
              case 9u { r = gelu_exact_grad(a); }
              default { r = 0.0; } }
  z[i] = r;
}")

(def reduce-wgsl
  "Tree reduction with op ∈ {0:sum 1:max 2:min} via a uniform; each 256-wide
  workgroup writes its partial to `partials[wid]`, the host combines partials with
  the same op. `dot` = reduce(sum, ewise(mul,x,y)); `nrm2` = √(dot(x,x))."
  "
@group(0) @binding(0) var<storage, read>       x: array<f32>;
@group(0) @binding(1) var<storage, read_write> partials: array<f32>;
@group(0) @binding(2) var<uniform>             op: u32;
var<workgroup> s: array<f32, 256>;
fn cmb(a: f32, b: f32, op: u32) -> f32 {
  if (op == 1u) { return max(a, b); } if (op == 2u) { return min(a, b); } return a + b;
}
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) g: vec3<u32>,
        @builtin(local_invocation_id)  l: vec3<u32>,
        @builtin(workgroup_id)         w: vec3<u32>) {
  let ident = select(select(3.4e38, -3.4e38, op == 1u), 0.0, op == 0u);
  s[l.x] = select(ident, x[g.x], g.x < arrayLength(&x));
  workgroupBarrier();
  var d: u32 = 128u;
  loop { if (d == 0u) { break; }
         if (l.x < d) { s[l.x] = cmb(s[l.x], s[l.x + d], op); }
         workgroupBarrier(); d = d / 2u; }
  if (l.x == 0u) { partials[w.x] = s[0]; }
}")

(def argmax-rows-wgsl
  "One 256-wide workgroup reduces each logits row to a token index. Each lane
  scans a strided slice, then a pairwise workgroup reduction preserves the
  lowest index on exact ties. Only one u32 per row crosses the host boundary."
  "
struct Dims { rows: u32, cols: u32, _pad0: u32, _pad1: u32 }
@group(0) @binding(0) var<storage, read> logits: array<f32>;
@group(0) @binding(1) var<storage, read_write> indices: array<u32>;
@group(0) @binding(2) var<uniform> dims: Dims;
var<workgroup> best_values: array<f32, 256>;
var<workgroup> best_indices: array<u32, 256>;

fn better(value: f32, index: u32, current: f32, current_index: u32) -> bool {
  return value > current || (value == current && index < current_index);
}

@compute @workgroup_size(256)
fn main(@builtin(local_invocation_id) lane: vec3<u32>,
        @builtin(workgroup_id) group: vec3<u32>) {
  let row = group.x;
  if (row >= dims.rows) { return; }
  var value = -3.402823466e38;
  var index = 0xffffffffu;
  var col = lane.x;
  loop {
    if (col >= dims.cols) { break; }
    let candidate = logits[row * dims.cols + col];
    if (better(candidate, col, value, index)) {
      value = candidate;
      index = col;
    }
    col = col + 256u;
  }
  best_values[lane.x] = value;
  best_indices[lane.x] = index;
  workgroupBarrier();
  var width = 128u;
  loop {
    if (width == 0u) { break; }
    if (lane.x < width) {
      let other_value = best_values[lane.x + width];
      let other_index = best_indices[lane.x + width];
      if (better(other_value, other_index,
                 best_values[lane.x], best_indices[lane.x])) {
        best_values[lane.x] = other_value;
        best_indices[lane.x] = other_index;
      }
    }
    workgroupBarrier();
    width = width / 2u;
  }
  if (lane.x == 0u) { indices[row] = best_indices[0]; }
}")

(def repetition-penalty-row-wgsl
  "Apply the standard repetition penalty to unique token columns in one row.
  Callers deduplicate token IDs before dispatch, avoiding write races."
  "
struct Dims { row: u32, cols: u32, count: u32, _pad0: u32 }
@group(0) @binding(0) var<storage, read_write> logits: array<f32>;
@group(0) @binding(1) var<storage, read> tokens: array<u32>;
@group(0) @binding(2) var<uniform> dims: Dims;
@group(0) @binding(3) var<uniform> penalty: f32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  if (gid.x >= dims.count) { return; }
  let token = tokens[gid.x];
  if (token >= dims.cols) { return; }
  let index = dims.row * dims.cols + token;
  let value = logits[index];
  logits[index] = select(value / penalty, value * penalty, value < 0.0);
}")

(def top-k-row-wgsl
  "Exact bounded row top-k for k <= 256. Lanes repeatedly scan their strided
  vocabulary slice, reduce the next maximum, and exclude prior winners. Output
  is interleaved f32 `[token-id, adjusted-logit]`, permitting one small readback."
  "
struct Dims { row: u32, cols: u32, k: u32, _pad0: u32 }
@group(0) @binding(0) var<storage, read> logits: array<f32>;
@group(0) @binding(1) var<storage, read_write> candidates: array<f32>;
@group(0) @binding(2) var<uniform> dims: Dims;
var<workgroup> lane_values: array<f32, 256>;
var<workgroup> lane_indices: array<u32, 256>;
var<workgroup> reduce_values: array<f32, 256>;
var<workgroup> reduce_indices: array<u32, 256>;
var<workgroup> winner_lane: u32;

fn better(value: f32, index: u32, current: f32, current_index: u32) -> bool {
  return value > current || (value == current && index < current_index);
}

fn worse(value: f32, index: u32, prior: f32, prior_index: u32) -> bool {
  return value < prior || (value == prior && index > prior_index);
}

@compute @workgroup_size(256)
fn main(@builtin(local_invocation_id) lane: vec3<u32>) {
  var initial_value = -3.402823466e38;
  var initial_index = 0xffffffffu;
  var initial_col = lane.x;
  loop {
    if (initial_col >= dims.cols) { break; }
    let candidate = logits[dims.row * dims.cols + initial_col];
    if (better(candidate, initial_col, initial_value, initial_index)) {
      initial_value = candidate;
      initial_index = initial_col;
    }
    initial_col = initial_col + 256u;
  }
  lane_values[lane.x] = initial_value;
  lane_indices[lane.x] = initial_index;
  workgroupBarrier();
  var rank = 0u;
  loop {
    if (rank >= dims.k) { break; }
    reduce_values[lane.x] = lane_values[lane.x];
    reduce_indices[lane.x] = lane_indices[lane.x];
    workgroupBarrier();
    var width = 128u;
    loop {
      if (width == 0u) { break; }
      if (lane.x < width) {
        let other_value = reduce_values[lane.x + width];
        let other_index = reduce_indices[lane.x + width];
        if (better(other_value, other_index,
                   reduce_values[lane.x], reduce_indices[lane.x])) {
          reduce_values[lane.x] = other_value;
          reduce_indices[lane.x] = other_index;
        }
      }
      workgroupBarrier();
      width = width / 2u;
    }
    if (lane.x == 0u) {
      candidates[rank * 2u] = f32(reduce_indices[0]);
      candidates[rank * 2u + 1u] = reduce_values[0];
      winner_lane = reduce_indices[0] % 256u;
    }
    workgroupBarrier();
    if (lane.x == winner_lane) {
      let prior_value = lane_values[lane.x];
      let prior_index = lane_indices[lane.x];
      var next_value = -3.402823466e38;
      var next_index = 0xffffffffu;
      var col = lane.x;
      loop {
        if (col >= dims.cols) { break; }
        let candidate = logits[dims.row * dims.cols + col];
        if (worse(candidate, col, prior_value, prior_index) &&
            better(candidate, col, next_value, next_index)) {
          next_value = candidate;
          next_index = col;
        }
        col = col + 256u;
      }
      lane_values[lane.x] = next_value;
      lane_indices[lane.x] = next_index;
    }
    workgroupBarrier();
    rank = rank + 1u;
  }
}")

(def sample-candidates-wgsl
  "Temperature, nucleus truncation, and categorical selection over already
  ranked top-k candidates. One invocation returns one u32 token index."
  "
struct Params { temperature: f32, top_p: f32, random_value: f32, _pad0: f32 }
@group(0) @binding(0) var<storage, read> candidates: array<f32>;
@group(0) @binding(1) var<storage, read_write> selected: array<u32>;
@group(0) @binding(2) var<uniform> count: u32;
@group(0) @binding(3) var<uniform> params: Params;
@compute @workgroup_size(1)
fn main() {
  if (params.temperature == 0.0) {
    selected[0u] = u32(candidates[0u]);
    return;
  }
  let maximum = candidates[1u];
  var total = 0.0;
  var i = 0u;
  loop {
    if (i >= count) { break; }
    total = total + exp((candidates[i * 2u + 1u] - maximum) /
                        params.temperature);
    i = i + 1u;
  }
  var nucleus_weight = 0.0;
  var nucleus_count = 0u;
  loop {
    if (nucleus_count >= count) { break; }
    nucleus_weight = nucleus_weight +
      exp((candidates[nucleus_count * 2u + 1u] - maximum) /
          params.temperature);
    nucleus_count = nucleus_count + 1u;
    if (nucleus_weight / total >= params.top_p) { break; }
  }
  let threshold = params.random_value * nucleus_weight;
  var cumulative = 0.0;
  var chosen = u32(candidates[0u]);
  i = 0u;
  loop {
    if (i >= nucleus_count) { break; }
    cumulative = cumulative +
      exp((candidates[i * 2u + 1u] - maximum) / params.temperature);
    chosen = u32(candidates[i * 2u]);
    if (cumulative > threshold || i + 1u == nucleus_count) { break; }
    i = i + 1u;
  }
  selected[0u] = chosen;
}")

(def sample-softmax-row-wgsl
  "Full-distribution temperature-softmax sampling for one logits row. Parallel
  contiguous chunks compute max and probability mass; only the selected chunk
  performs the final token-order CDF scan."
  "
struct Dims { row: u32, cols: u32, _pad0: u32, _pad1: u32 }
struct Params { temperature: f32, random_value: f32, _pad0: f32, _pad1: f32 }
@group(0) @binding(0) var<storage, read> logits: array<f32>;
@group(0) @binding(1) var<storage, read_write> selected: array<u32>;
@group(0) @binding(2) var<uniform> dims: Dims;
@group(0) @binding(3) var<uniform> params: Params;
var<workgroup> reduce_values: array<f32, 256>;
var<workgroup> chunk_weights: array<f32, 256>;
var<workgroup> maximum: f32;
var<workgroup> total: f32;
var<workgroup> threshold: f32;
var<workgroup> prefix: f32;
var<workgroup> chosen_lane: u32;

@compute @workgroup_size(256)
fn main(@builtin(local_invocation_id) lane: vec3<u32>) {
  let base = dims.row * dims.cols;
  let chunk = (dims.cols + 255u) / 256u;
  let begin = lane.x * chunk;
  let end = min(dims.cols, begin + chunk);
  var local_max = -3.402823466e38;
  var i = begin;
  loop {
    if (i >= end) { break; }
    local_max = max(local_max, logits[base + i]);
    i = i + 1u;
  }
  reduce_values[lane.x] = local_max;
  workgroupBarrier();
  var width = 128u;
  loop {
    if (width == 0u) { break; }
    if (lane.x < width) {
      reduce_values[lane.x] = max(reduce_values[lane.x],
                                  reduce_values[lane.x + width]);
    }
    workgroupBarrier();
    width = width / 2u;
  }
  if (lane.x == 0u) { maximum = reduce_values[0u]; }
  workgroupBarrier();
  var local_sum = 0.0;
  i = begin;
  loop {
    if (i >= end) { break; }
    local_sum = local_sum +
      exp((logits[base + i] - maximum) / params.temperature);
    i = i + 1u;
  }
  chunk_weights[lane.x] = local_sum;
  reduce_values[lane.x] = local_sum;
  workgroupBarrier();
  width = 128u;
  loop {
    if (width == 0u) { break; }
    if (lane.x < width) {
      reduce_values[lane.x] = reduce_values[lane.x] +
                              reduce_values[lane.x + width];
    }
    workgroupBarrier();
    width = width / 2u;
  }
  if (lane.x == 0u) {
    total = reduce_values[0u];
    threshold = params.random_value * total;
    var cumulative = 0.0;
    var lane_index = 0u;
    chosen_lane = 255u;
    loop {
      if (lane_index >= 256u) { break; }
      let cumulative_next = cumulative + chunk_weights[lane_index];
      if (cumulative_next > threshold || lane_index == 255u) {
        chosen_lane = lane_index;
        prefix = cumulative;
        break;
      }
      cumulative = cumulative_next;
      lane_index = lane_index + 1u;
    }
  }
  workgroupBarrier();
  if (lane.x == chosen_lane) {
    var cumulative = prefix;
    var chosen = max(1u, end) - 1u;
    i = begin;
    loop {
      if (i >= end) { break; }
      cumulative = cumulative +
        exp((logits[base + i] - maximum) / params.temperature);
      chosen = i;
      if (cumulative > threshold || i + 1u == end) { break; }
      i = i + 1u;
    }
    selected[0u] = chosen;
  }
}")

(def nucleus-sort-init-wgsl
  "Copy one logits row into power-of-two padded sortable value/token buffers."
  "
struct Dims { row: u32, cols: u32, padded: u32, _pad0: u32 }
@group(0) @binding(0) var<storage, read> logits: array<f32>;
@group(0) @binding(1) var<storage, read_write> values: array<f32>;
@group(0) @binding(2) var<storage, read_write> tokens: array<u32>;
@group(0) @binding(3) var<uniform> dims: Dims;
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  if (gid.x >= dims.padded) { return; }
  if (gid.x < dims.cols) {
    values[gid.x] = logits[dims.row * dims.cols + gid.x];
    tokens[gid.x] = gid.x;
  } else {
    values[gid.x] = -3.402823466e38;
    tokens[gid.x] = 0xffffffffu;
  }
}")

(def nucleus-bitonic-step-wgsl
  "One globally synchronized bitonic compare/swap stage. Repeated dispatches
  produce stable descending `(logit, inverse-token-id)` order."
  "
struct Stage { padded: u32, span: u32, stride: u32, _pad0: u32 }
@group(0) @binding(0) var<storage, read_write> values: array<f32>;
@group(0) @binding(1) var<storage, read_write> tokens: array<u32>;
@group(0) @binding(2) var<uniform> stage: Stage;

fn better(av: f32, ai: u32, bv: f32, bi: u32) -> bool {
  return av > bv || (av == bv && ai < bi);
}

@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let left = gid.x;
  if (left >= stage.padded) { return; }
  let right = left ^ stage.stride;
  if (right <= left || right >= stage.padded) { return; }
  let left_value = values[left];
  let left_token = tokens[left];
  let right_value = values[right];
  let right_token = tokens[right];
  let descending = (left & stage.span) == 0u;
  let swap = select(better(left_value, left_token, right_value, right_token),
                    better(right_value, right_token, left_value, left_token),
                    descending);
  if (swap) {
    values[left] = right_value;
    tokens[left] = right_token;
    values[right] = left_value;
    tokens[right] = left_token;
  }
}")

(def sample-ranked-nucleus-wgsl
  "Exact nucleus boundary and categorical selection over stable sorted logits.
  Parallel contiguous chunks reduce softmax mass; only the boundary and selected
  chunks scan individual tokens."
  "
struct Dims { cols: u32, padded: u32, _pad0: u32, _pad1: u32 }
struct Params { temperature: f32, top_p: f32, random_value: f32, _pad0: f32 }
@group(0) @binding(0) var<storage, read> values: array<f32>;
@group(0) @binding(1) var<storage, read> tokens: array<u32>;
@group(0) @binding(2) var<storage, read_write> selected: array<u32>;
@group(0) @binding(3) var<uniform> dims: Dims;
@group(0) @binding(4) var<uniform> params: Params;
var<workgroup> reduce_values: array<f32, 256>;
var<workgroup> chunk_weights: array<f32, 256>;
var<workgroup> total: f32;
var<workgroup> nucleus_lane: u32;
var<workgroup> nucleus_prefix: f32;
var<workgroup> nucleus_end: u32;
var<workgroup> nucleus_weight: f32;
var<workgroup> sample_lane: u32;
var<workgroup> sample_prefix: f32;
var<workgroup> sample_threshold: f32;

@compute @workgroup_size(256)
fn main(@builtin(local_invocation_id) lane: vec3<u32>) {
  let chunk = (dims.cols + 255u) / 256u;
  let begin = lane.x * chunk;
  let end = min(dims.cols, begin + chunk);
  let maximum = values[0u];
  var local_sum = 0.0;
  var i = begin;
  loop {
    if (i >= end) { break; }
    local_sum = local_sum + exp((values[i] - maximum) / params.temperature);
    i = i + 1u;
  }
  chunk_weights[lane.x] = local_sum;
  reduce_values[lane.x] = local_sum;
  workgroupBarrier();
  var width = 128u;
  loop {
    if (width == 0u) { break; }
    if (lane.x < width) {
      reduce_values[lane.x] = reduce_values[lane.x] +
                              reduce_values[lane.x + width];
    }
    workgroupBarrier();
    width = width / 2u;
  }
  if (lane.x == 0u) {
    total = reduce_values[0u];
    let target_weight = params.top_p * total;
    var cumulative = 0.0;
    var lane_index = 0u;
    nucleus_lane = 255u;
    loop {
      if (lane_index >= 256u) { break; }
      let cumulative_next = cumulative + chunk_weights[lane_index];
      if (cumulative_next >= target_weight || lane_index == 255u) {
        nucleus_lane = lane_index;
        nucleus_prefix = cumulative;
        break;
      }
      cumulative = cumulative_next;
      lane_index = lane_index + 1u;
    }
  }
  workgroupBarrier();
  if (lane.x == nucleus_lane) {
    let target_weight = params.top_p * total;
    var cumulative = nucleus_prefix;
    var boundary = max(1u, end) - 1u;
    i = begin;
    loop {
      if (i >= end) { break; }
      cumulative = cumulative + exp((values[i] - maximum) / params.temperature);
      boundary = i;
      if (cumulative >= target_weight || i + 1u == end) { break; }
      i = i + 1u;
    }
    nucleus_end = boundary;
    nucleus_weight = cumulative;
    chunk_weights[lane.x] = cumulative - nucleus_prefix;
  }
  workgroupBarrier();
  if (lane.x == 0u) {
    sample_threshold = params.random_value * nucleus_weight;
    var cumulative = 0.0;
    var lane_index = 0u;
    sample_lane = nucleus_lane;
    loop {
      if (lane_index > nucleus_lane) { break; }
      let cumulative_next = cumulative + chunk_weights[lane_index];
      if (cumulative_next > sample_threshold || lane_index == nucleus_lane) {
        sample_lane = lane_index;
        sample_prefix = cumulative;
        break;
      }
      cumulative = cumulative_next;
      lane_index = lane_index + 1u;
    }
  }
  workgroupBarrier();
  if (lane.x == sample_lane) {
    let limit = select(end, nucleus_end + 1u, sample_lane == nucleus_lane);
    var cumulative = sample_prefix;
    var chosen = max(1u, limit) - 1u;
    i = begin;
    loop {
      if (i >= limit) { break; }
      cumulative = cumulative + exp((values[i] - maximum) / params.temperature);
      chosen = i;
      if (cumulative > sample_threshold || i + 1u == limit) { break; }
      i = i + 1u;
    }
    selected[0u] = tokens[chosen];
  }
}")

(def speculative-rejection-row-wgsl
  "Exact full-distribution speculative accept/reject and residual sampling for
  one row. Contiguous vocabulary chunks reduce softmax and residual mass in
  parallel; only the selected chunk performs the final ordered CDF scan."
  "
struct Dims { row: u32, cols: u32, draft_token: u32, _pad0: u32 }
struct Params { temperature: f32, accept_random: f32, residual_random: f32, _pad0: f32 }
@group(0) @binding(0) var<storage, read> target_logits: array<f32>;
@group(0) @binding(1) var<storage, read> draft_logits: array<f32>;
@group(0) @binding(2) var<storage, read_write> result: array<u32>;
@group(0) @binding(3) var<uniform> dims: Dims;
@group(0) @binding(4) var<uniform> params: Params;
var<workgroup> target_reduce: array<f32, 256>;
var<workgroup> draft_reduce: array<f32, 256>;
var<workgroup> scratch: array<f32, 256>;
var<workgroup> target_chunks: array<f32, 256>;
var<workgroup> residual_chunks: array<f32, 256>;
var<workgroup> target_maximum: f32;
var<workgroup> draft_maximum: f32;
var<workgroup> target_total: f32;
var<workgroup> draft_total: f32;
var<workgroup> chosen_lane: u32;
var<workgroup> use_target: u32;
var<workgroup> threshold: f32;
var<workgroup> prefix: f32;
var<workgroup> accepted: u32;

@compute @workgroup_size(256)
fn main(@builtin(local_invocation_id) lane: vec3<u32>) {
  let base = dims.row * dims.cols;
  let chunk = (dims.cols + 255u) / 256u;
  let begin = lane.x * chunk;
  let end = min(dims.cols, begin + chunk);
  var local_target_max = -3.402823466e38;
  var local_draft_max = -3.402823466e38;
  var i = begin;
  loop {
    if (i >= end) { break; }
    local_target_max = max(local_target_max, target_logits[base + i]);
    local_draft_max = max(local_draft_max, draft_logits[base + i]);
    i = i + 1u;
  }
  target_reduce[lane.x] = local_target_max;
  draft_reduce[lane.x] = local_draft_max;
  workgroupBarrier();
  var width = 128u;
  loop {
    if (width == 0u) { break; }
    if (lane.x < width) {
      target_reduce[lane.x] = max(target_reduce[lane.x],
                                  target_reduce[lane.x + width]);
      draft_reduce[lane.x] = max(draft_reduce[lane.x],
                                 draft_reduce[lane.x + width]);
    }
    workgroupBarrier();
    width = width / 2u;
  }
  if (lane.x == 0u) {
    target_maximum = target_reduce[0u];
    draft_maximum = draft_reduce[0u];
  }
  workgroupBarrier();
  var local_target_sum = 0.0;
  var local_draft_sum = 0.0;
  i = begin;
  loop {
    if (i >= end) { break; }
    local_target_sum = local_target_sum +
      exp((target_logits[base + i] - target_maximum) / params.temperature);
    local_draft_sum = local_draft_sum +
      exp((draft_logits[base + i] - draft_maximum) / params.temperature);
    i = i + 1u;
  }
  target_chunks[lane.x] = local_target_sum;
  target_reduce[lane.x] = local_target_sum;
  draft_reduce[lane.x] = local_draft_sum;
  workgroupBarrier();
  width = 128u;
  loop {
    if (width == 0u) { break; }
    if (lane.x < width) {
      target_reduce[lane.x] = target_reduce[lane.x] + target_reduce[lane.x + width];
      draft_reduce[lane.x] = draft_reduce[lane.x] + draft_reduce[lane.x + width];
    }
    workgroupBarrier();
    width = width / 2u;
  }
  if (lane.x == 0u) {
    target_total = target_reduce[0u];
    draft_total = draft_reduce[0u];
    let p = exp((target_logits[base + dims.draft_token] - target_maximum) /
                params.temperature) / target_total;
    let q = exp((draft_logits[base + dims.draft_token] - draft_maximum) /
                params.temperature) / draft_total;
    accepted = select(0u, 1u,
                      params.accept_random <= min(1.0, p / max(q, 1.0e-30)));
    if (accepted == 1u) {
      result[0u] = 1u;
      result[1u] = dims.draft_token;
    }
  }
  workgroupBarrier();
  if (accepted == 1u) { return; }
  var local_residual = 0.0;
  i = begin;
  loop {
    if (i >= end) { break; }
    let pi = exp((target_logits[base + i] - target_maximum) / params.temperature) /
             target_total;
    let qi = exp((draft_logits[base + i] - draft_maximum) / params.temperature) /
             draft_total;
    local_residual = local_residual + max(0.0, pi - qi);
    i = i + 1u;
  }
  residual_chunks[lane.x] = local_residual;
  scratch[lane.x] = local_residual;
  workgroupBarrier();
  width = 128u;
  loop {
    if (width == 0u) { break; }
    if (lane.x < width) { scratch[lane.x] = scratch[lane.x] + scratch[lane.x + width]; }
    workgroupBarrier();
    width = width / 2u;
  }
  if (lane.x == 0u) {
    use_target = select(0u, 1u, scratch[0u] <= 0.0);
    let mass = select(scratch[0u], 1.0, use_target == 1u);
    threshold = params.residual_random * mass;
    var cumulative = 0.0;
    chosen_lane = 255u;
    var lane_index = 0u;
    loop {
      if (lane_index >= 256u) { break; }
      let lane_mass = select(residual_chunks[lane_index],
                             target_chunks[lane_index] / target_total,
                             use_target == 1u);
      if (threshold <= cumulative + lane_mass || lane_index == 255u) {
        chosen_lane = lane_index;
        prefix = cumulative;
        break;
      }
      cumulative = cumulative + lane_mass;
      lane_index = lane_index + 1u;
    }
    result[0u] = 0u;
  }
  workgroupBarrier();
  if (lane.x == chosen_lane) {
    var cumulative = prefix;
    var chosen = max(1u, end) - 1u;
    i = begin;
    loop {
      if (i >= end) { break; }
      let pi = exp((target_logits[base + i] - target_maximum) /
                   params.temperature) / target_total;
      let qi = exp((draft_logits[base + i] - draft_maximum) /
                   params.temperature) / draft_total;
      cumulative = cumulative + select(max(0.0, pi - qi), pi,
                                       use_target == 1u);
      chosen = i;
      if (threshold <= cumulative || i + 1u == end) { break; }
      i = i + 1u;
    }
    result[1u] = chosen;
  }
}")

(def gemv-wgsl
  "Dense GEMV: y = A·x, A is m×n row-major. One thread per row."
  "
@group(0) @binding(0) var<storage, read>       A: array<f32>;
@group(0) @binding(1) var<storage, read>       x: array<f32>;
@group(0) @binding(2) var<storage, read_write> y: array<f32>;
@group(0) @binding(3) var<uniform>             d: vec2<u32>;   // (m, n)
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; let m = d.x; let n = d.y;
  if (i >= m) { return; }
  var s: f32 = 0.0;
  for (var j: u32 = 0u; j < n; j = j + 1u) { s = s + A[i * n + j] * x[j]; }
  y[i] = s;
}")

(def q8-0-gemv-wgsl
  "GGML Q8_0 GEMV without an intermediate dequantized matrix. Quants are
  packed four signed bytes per u32; scales are one f32 per 32-value block."
  "
@group(0) @binding(0) var<storage, read>       q: array<u32>;
@group(0) @binding(1) var<storage, read>       scales: array<f32>;
@group(0) @binding(2) var<storage, read>       x: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<uniform>             d: vec3<u32>; // rows, cols, blocks/row

fn signed_byte(byte_index: u32) -> f32 {
  let word = q[byte_index / 4u];
  let raw = (word >> ((byte_index % 4u) * 8u)) & 255u;
  return f32(select(i32(raw), i32(raw) - 256, raw >= 128u));
}

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let row = gid.x;
  if (row >= d.x) { return; }
  var sum: f32 = 0.0;
  for (var block: u32 = 0u; block < d.z; block = block + 1u) {
    let scale = scales[row * d.z + block];
    let qbase = (row * d.z + block) * 32u;
    let xbase = block * 32u;
    for (var i: u32 = 0u; i < 32u; i = i + 1u) {
      sum = sum + scale * signed_byte(qbase + i) * x[xbase + i];
    }
  }
  y[row] = sum;
}")

(def q4-0-gemv-wgsl
  "GGML Q4_0 GEMV without dense dequantization. Each 32-value block stores
  sixteen bytes; low nibbles are columns 0..15 and high nibbles 16..31."
  "
@group(0) @binding(0) var<storage, read>       q: array<u32>;
@group(0) @binding(1) var<storage, read>       scales: array<f32>;
@group(0) @binding(2) var<storage, read>       x: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<uniform>             d: vec3<u32>; // rows, cols, blocks/row

fn packed_byte(byte_index: u32) -> u32 {
  let word = q[byte_index / 4u];
  return (word >> ((byte_index % 4u) * 8u)) & 255u;
}

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let row = gid.x;
  if (row >= d.x) { return; }
  var sum: f32 = 0.0;
  for (var block: u32 = 0u; block < d.z; block = block + 1u) {
    let scale = scales[row * d.z + block];
    let qbase = (row * d.z + block) * 16u;
    let xbase = block * 32u;
    for (var i: u32 = 0u; i < 16u; i = i + 1u) {
      let packed = packed_byte(qbase + i);
      sum = sum + scale * (f32(packed & 15u) - 8.0) * x[xbase + i];
      sum = sum + scale * (f32(packed >> 4u) - 8.0) * x[xbase + i + 16u];
    }
  }
  y[row] = sum;
}")

(def q4-k-gemv-wgsl
  "GGML Q4_K GEMV directly over its 256-value, 144-byte superblocks."
  "
@group(0) @binding(0) var<storage, read>       raw: array<u32>;
@group(0) @binding(1) var<storage, read>       dummy: array<f32>;
@group(0) @binding(2) var<storage, read>       x: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<uniform>             d: vec3<u32>; // rows, cols, blocks/row

fn byte_at(index: u32) -> u32 {
  return (raw[index / 4u] >> ((index % 4u) * 8u)) & 255u;
}
fn group_scale(base: u32, group: u32) -> u32 {
  if (group < 4u) { return byte_at(base + 4u + group) & 63u; }
  return (byte_at(base + 8u + group) & 15u) |
         (((byte_at(base + group) >> 6u) & 3u) << 4u);
}
fn group_min(base: u32, group: u32) -> u32 {
  if (group < 4u) { return byte_at(base + 8u + group) & 63u; }
  return ((byte_at(base + 8u + group) >> 4u) & 15u) |
         (((byte_at(base + 4u + group) >> 6u) & 3u) << 4u);
}

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let row = gid.x;
  if (row >= d.x) { return; }
  var sum: f32 = dummy[0] * 0.0;
  for (var block: u32 = 0u; block < d.z; block = block + 1u) {
    let base = (row * d.z + block) * 144u;
    let dm = unpack2x16float(raw[base / 4u]);
    for (var group: u32 = 0u; group < 8u; group = group + 1u) {
      let scale = f32(group_scale(base, group));
      let minimum = f32(group_min(base, group));
      let qbase = base + 16u + (group / 2u) * 32u;
      let xbase = block * 256u + group * 32u;
      for (var i: u32 = 0u; i < 32u; i = i + 1u) {
        let packed = byte_at(qbase + i);
        let q = select(packed & 15u, packed >> 4u, group % 2u == 1u);
        sum = sum + (dm.x * scale * f32(q) - dm.y * minimum) * x[xbase + i];
      }
    }
  }
  y[row] = sum;
}")

(def q5-0-gemv-wgsl
  "GGML Q5_0 GEMV over 32-value, 22-byte blocks."
  "
@group(0) @binding(0) var<storage, read>       raw: array<u32>;
@group(0) @binding(1) var<storage, read>       dummy: array<f32>;
@group(0) @binding(2) var<storage, read>       x: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<uniform>             d: vec3<u32>;
fn byte_at(index: u32) -> u32 {
  return (raw[index / 4u] >> ((index % 4u) * 8u)) & 255u;
}
fn high_bit(base: u32, index: u32) -> u32 {
  return ((byte_at(base + 2u + index / 8u) >> (index % 8u)) & 1u) << 4u;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let row = gid.x;
  if (row >= d.x) { return; }
  var sum: f32 = dummy[0] * 0.0;
  for (var block: u32 = 0u; block < d.z; block = block + 1u) {
    let base = (row * d.z + block) * 22u;
    let pair = unpack2x16float(raw[base / 4u]);
    let scale = select(pair.x, pair.y, base % 4u == 2u);
    let xbase = block * 32u;
    for (var i: u32 = 0u; i < 16u; i = i + 1u) {
      let packed = byte_at(base + 6u + i);
      let lo = (packed & 15u) | high_bit(base, i);
      let hi = (packed >> 4u) | high_bit(base, i + 16u);
      sum = sum + scale * (f32(lo) - 16.0) * x[xbase + i];
      sum = sum + scale * (f32(hi) - 16.0) * x[xbase + i + 16u];
    }
  }
  y[row] = sum;
}")

(def q5-1-gemv-wgsl
  "GGML Q5_1 GEMV over 32-value, 24-byte blocks."
  "
@group(0) @binding(0) var<storage, read>       raw: array<u32>;
@group(0) @binding(1) var<storage, read>       dummy: array<f32>;
@group(0) @binding(2) var<storage, read>       x: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<uniform>             d: vec3<u32>;
fn byte_at(index: u32) -> u32 {
  return (raw[index / 4u] >> ((index % 4u) * 8u)) & 255u;
}
fn high_bit(base: u32, index: u32) -> u32 {
  return ((byte_at(base + 4u + index / 8u) >> (index % 8u)) & 1u) << 4u;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let row = gid.x;
  if (row >= d.x) { return; }
  var sum: f32 = dummy[0] * 0.0;
  for (var block: u32 = 0u; block < d.z; block = block + 1u) {
    let base = (row * d.z + block) * 24u;
    let dm = unpack2x16float(raw[base / 4u]);
    let xbase = block * 32u;
    for (var i: u32 = 0u; i < 16u; i = i + 1u) {
      let packed = byte_at(base + 8u + i);
      let lo = (packed & 15u) | high_bit(base, i);
      let hi = (packed >> 4u) | high_bit(base, i + 16u);
      sum = sum + (dm.x * f32(lo) + dm.y) * x[xbase + i];
      sum = sum + (dm.x * f32(hi) + dm.y) * x[xbase + i + 16u];
    }
  }
  y[row] = sum;
}")

(def q5-k-gemv-wgsl
  "GGML Q5_K GEMV directly over 176-byte superblocks."
  "
@group(0) @binding(0) var<storage, read> raw: array<u32>;
@group(0) @binding(1) var<storage, read> dummy: array<f32>;
@group(0) @binding(2) var<storage, read> x: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<uniform> d: vec3<u32>;
fn byte_at(i: u32) -> u32 { return (raw[i / 4u] >> ((i % 4u) * 8u)) & 255u; }
fn gs(b: u32, g: u32) -> u32 {
  if (g < 4u) { return byte_at(b + 4u + g) & 63u; }
  return (byte_at(b + 8u + g) & 15u) | (((byte_at(b + g) >> 6u) & 3u) << 4u);
}
fn gm(b: u32, g: u32) -> u32 {
  if (g < 4u) { return byte_at(b + 8u + g) & 63u; }
  return ((byte_at(b + 8u + g) >> 4u) & 15u) |
         (((byte_at(b + 4u + g) >> 6u) & 3u) << 4u);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let row = gid.x; if (row >= d.x) { return; }
  var sum: f32 = dummy[0] * 0.0;
  for (var block: u32 = 0u; block < d.z; block = block + 1u) {
    let b = (row * d.z + block) * 176u;
    let dm = unpack2x16float(raw[b / 4u]);
    for (var g: u32 = 0u; g < 8u; g = g + 1u) {
      for (var i: u32 = 0u; i < 32u; i = i + 1u) {
        let packed = byte_at(b + 48u + (g / 2u) * 32u + i);
        let low = select(packed & 15u, packed >> 4u, g % 2u == 1u);
        let high = ((byte_at(b + 16u + i) >> g) & 1u) << 4u;
        let q = low | high;
        sum = sum + (dm.x * f32(gs(b, g)) * f32(q) - dm.y * f32(gm(b, g))) *
                    x[block * 256u + g * 32u + i];
      }
    }
  }
  y[row] = sum;
}")

(def q6-k-gemv-wgsl
  "GGML Q6_K GEMV directly over signed 6-bit, 210-byte superblocks."
  "
@group(0) @binding(0) var<storage, read> raw: array<u32>;
@group(0) @binding(1) var<storage, read> dummy: array<f32>;
@group(0) @binding(2) var<storage, read> x: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<uniform> d: vec3<u32>;
fn byte_at(i: u32) -> u32 { return (raw[i / 4u] >> ((i % 4u) * 8u)) & 255u; }
fn signed_byte(i: u32) -> i32 {
  let v = byte_at(i); return select(i32(v), i32(v) - 256, v >= 128u);
}
fn half_at(i: u32) -> f32 {
  let p = unpack2x16float(raw[i / 4u]); return select(p.x, p.y, i % 4u == 2u);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let row = gid.x; if (row >= d.x) { return; }
  var sum: f32 = dummy[0] * 0.0;
  for (var block: u32 = 0u; block < d.z; block = block + 1u) {
    let b = (row * d.z + block) * 210u; let scale = half_at(b + 208u);
    for (var hb: u32 = 0u; hb < 2u; hb = hb + 1u) {
      for (var i: u32 = 0u; i < 32u; i = i + 1u) {
        let la = byte_at(b + hb * 64u + i);
        let lb = byte_at(b + hb * 64u + 32u + i);
        let hi = byte_at(b + 128u + hb * 32u + i);
        let qs = array<i32, 4>(i32((la & 15u) | ((hi & 3u) << 4u)) - 32,
          i32((lb & 15u) | (((hi >> 2u) & 3u) << 4u)) - 32,
          i32((la >> 4u) | (((hi >> 4u) & 3u) << 4u)) - 32,
          i32((lb >> 4u) | (((hi >> 6u) & 3u) << 4u)) - 32);
        for (var seg: u32 = 0u; seg < 4u; seg = seg + 1u) {
          let si = hb * 8u + seg * 2u + i / 16u;
          let weight = scale * f32(signed_byte(b + 192u + si)) * f32(qs[seg]);
          sum = sum + weight * x[block * 256u + hb * 128u + seg * 32u + i];
        }
      }
    }
  }
  y[row] = sum;
}")

(def kv-cache-write-wgsl
  "Write the current token's RoPE-transformed K/V vectors into a persistent,
  layer-major KV cache."
  "
struct Params { heads: u32, kv_heads: u32, head_dim: u32, position: u32,
                context: u32, layer: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> k: array<f32>;
@group(0) @binding(1) var<storage, read> v: array<f32>;
@group(0) @binding(2) var<storage, read_write> keys: array<f32>;
@group(0) @binding(3) var<storage, read_write> values: array<f32>;
@group(0) @binding(4) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; let width = p.kv_heads * p.head_dim;
  if (i >= width) { return; }
  let base = (p.layer * p.context + p.position) * width;
  keys[base + i] = k[i]; values[base + i] = v[i];
}")

(def causal-gqa-attention-wgsl
  "Causal grouped-query attention over a persistent KV cache. Each invocation
  computes one output component using stable online softmax passes."
  "
struct Params { heads: u32, kv_heads: u32, head_dim: u32, position: u32,
                context: u32, layer: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> q: array<f32>;
@group(0) @binding(1) var<storage, read> keys: array<f32>;
@group(0) @binding(2) var<storage, read> values: array<f32>;
@group(0) @binding(3) var<storage, read_write> output: array<f32>;
@group(0) @binding(4) var<uniform> p: Params;
fn score(head: u32, kv_head: u32, past: u32) -> f32 {
  let width = p.kv_heads * p.head_dim;
  let kb = (p.layer * p.context + past) * width + kv_head * p.head_dim;
  let qb = head * p.head_dim; var sum: f32 = 0.0;
  for (var i: u32 = 0u; i < p.head_dim; i = i + 1u) {
    sum = sum + q[qb + i] * keys[kb + i];
  }
  return sum * inverseSqrt(f32(p.head_dim));
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x; if (index >= p.heads * p.head_dim) { return; }
  let head = index / p.head_dim; let component = index % p.head_dim;
  let grouped = p.heads / p.kv_heads; let kv_head = head / grouped;
  var maximum: f32 = -3.402823e38;
  for (var past: u32 = 0u; past <= p.position; past = past + 1u) {
    maximum = max(maximum, score(head, kv_head, past));
  }
  var denominator: f32 = 0.0; var result: f32 = 0.0;
  let width = p.kv_heads * p.head_dim;
  for (var past: u32 = 0u; past <= p.position; past = past + 1u) {
    let weight = exp(score(head, kv_head, past) - maximum);
    let vb = (p.layer * p.context + past) * width + kv_head * p.head_dim;
    denominator = denominator + weight;
    result = result + weight * values[vb + component];
  }
  output[index] = result / denominator;
}")

(def transpose-2d-wgsl
  "Out-of-place row-major matrix transpose used by GPU backpropagation."
  "
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> dims: vec2<u32>;
@compute @workgroup_size(16, 16)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let col = gid.x; let row = gid.y;
  if (row < dims.x && col < dims.y) {
    output[col * dims.x + row] = input[row * dims.y + col];
  }
}")

(def transpose-nd-wgsl
  "Generic contiguous rank-1 through rank-4 axis permutation."
  "
struct Params {
  info: vec4<u32>, input_shape: vec4<u32>, output_shape: vec4<u32>, perm: vec4<u32>
}
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let output_index = gid.x;
  let rank = p.info.x; if (output_index >= p.info.y) { return; }
  var remaining = output_index; var output_coord = vec4<u32>(0u);
  var axis = rank;
  loop {
    if (axis == 0u) { break; }
    axis = axis - 1u;
    output_coord[axis] = remaining % p.output_shape[axis];
    remaining = remaining / p.output_shape[axis];
  }
  var input_coord = vec4<u32>(0u); var i = 0u;
  loop {
    if (i >= rank) { break; }
    input_coord[p.perm[i]] = output_coord[i]; i = i + 1u;
  }
  var input_index = 0u; i = 0u;
  loop {
    if (i >= rank) { break; }
    input_index = input_index * p.input_shape[i] + input_coord[i]; i = i + 1u;
  }
  output[output_index] = input[input_index];
}")

(def transpose-nd-f16-wgsl
  "Generic packed-F16 rank-1 through rank-4 axis permutation."
  "
struct Params {
  info: vec4<u32>, input_shape: vec4<u32>, output_shape: vec4<u32>, perm: vec4<u32>
}
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read_write> output: array<u32>;
@group(0) @binding(2) var<uniform> p: Params;
fn load(index: u32) -> f32 {
  let pair = unpack2x16float(input[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn source(output_index: u32) -> u32 {
  let rank = p.info.x;
  var remaining = output_index; var output_coord = vec4<u32>(0u);
  var axis = rank;
  loop {
    if (axis == 0u) { break; } axis = axis - 1u;
    output_coord[axis] = remaining % p.output_shape[axis];
    remaining = remaining / p.output_shape[axis];
  }
  var input_coord = vec4<u32>(0u); var i = 0u;
  loop {
    if (i >= rank) { break; }
    input_coord[p.perm[i]] = output_coord[i]; i = i + 1u;
  }
  var input_index = 0u; i = 0u;
  loop {
    if (i >= rank) { break; }
    input_index = input_index * p.input_shape[i] + input_coord[i]; i = i + 1u;
  }
  return input_index;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>,
        @builtin(num_workgroups) grid: vec3<u32>) {
  let word = gid.x + gid.y * grid.x * 64u;
  let first = word * 2u; if (first >= p.info.y) { return; }
  var second = 0.0;
  if (first + 1u < p.info.y) { second = load(source(first + 1u)); }
  output[word] = pack2x16float(vec2<f32>(load(source(first)), second));
}")

(def batched-matmul-wgsl
  "Batched matmul with up to two NumPy-broadcast leading dimensions."
  "
struct Params {
  info: vec4<u32>, dims: vec4<u32>, batch_out: vec4<u32>,
  batch_a: vec4<u32>, batch_b: vec4<u32>
}
@group(0) @binding(0) var<storage, read> a: array<f32>;
@group(0) @binding(1) var<storage, read> b: array<f32>;
@group(0) @binding(2) var<storage, read_write> output: array<f32>;
@group(0) @binding(3) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x; let rank = p.info.x; let m = p.info.z; let n = p.info.w;
  let k = p.dims.x; if (index >= p.dims.y) { return; }
  let matrix_size = m * n; let batch = index / matrix_size;
  let local = index % matrix_size; let row = local / n; let column = local % n;
  var coords = vec4<u32>(0u); var remaining = batch; var axis = rank;
  loop {
    if (axis == 0u) { break; } axis = axis - 1u;
    coords[axis] = remaining % p.batch_out[axis];
    remaining = remaining / p.batch_out[axis];
  }
  var a_batch = 0u; var b_batch = 0u; var i = 0u;
  loop {
    if (i >= rank) { break; }
    a_batch = a_batch * p.batch_a[i] + select(coords[i], 0u, p.batch_a[i] == 1u);
    b_batch = b_batch * p.batch_b[i] + select(coords[i], 0u, p.batch_b[i] == 1u);
    i = i + 1u;
  }
  var sum = 0.0; i = 0u;
  loop {
    if (i >= k) { break; }
    sum = sum + a[(a_batch * m + row) * k + i] * b[(b_batch * k + i) * n + column];
    i = i + 1u;
  }
  output[index] = sum;
}")

(def add-last-axis-bias-wgsl
  "Broadcast a rank-1 bias over contiguous rows."
  "
struct Params { total: u32, width: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read> bias: array<f32>;
@group(0) @binding(2) var<storage, read_write> output: array<f32>;
@group(0) @binding(3) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= p.total) { return; }
  output[i] = input[i] + bias[i % p.width];
}")

(def add-last-axis-bias-f16-wgsl
  "Packed-F16 last-axis bias broadcasting, one invocation per output pair."
  "
struct Params { total: u32, width: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read> bias: array<u32>;
@group(0) @binding(2) var<storage, read_write> output: array<u32>;
@group(0) @binding(3) var<uniform> p: Params;
fn load(buffer: u32, index: u32) -> f32 {
  var pair = vec2<f32>(0.0);
  if (buffer == 0u) { pair = unpack2x16float(input[index / 2u]); }
  else { pair = unpack2x16float(bias[index / 2u]); }
  return select(pair.x, pair.y, index % 2u == 1u);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let first = gid.x * 2u; if (first >= p.total) { return; }
  let a = load(0u, first) + load(1u, first % p.width);
  var b = 0.0;
  if (first + 1u < p.total) {
    b = load(0u, first + 1u) + load(1u, (first + 1u) % p.width);
  }
  output[gid.x] = pack2x16float(vec2<f32>(a, b));
}")

(def multi-head-attention-wgsl
  "Fused rank-2 Q/K/V multi-head attention with stable online softmax passes."
  "
struct Params { batch: u32, seq_q: u32, seq_k: u32, model: u32,
                kv_model: u32, heads: u32, kv_heads: u32, head_dim: u32,
                total: u32, causal: u32, has_mask: u32, pad0: u32 }
@group(0) @binding(0) var<storage, read> query: array<f32>;
@group(0) @binding(1) var<storage, read> key: array<f32>;
@group(0) @binding(2) var<storage, read> value: array<f32>;
@group(0) @binding(3) var<storage, read> key_padding_mask: array<f32>;
@group(0) @binding(4) var<storage, read_write> output: array<f32>;
@group(0) @binding(5) var<uniform> p: Params;
fn allowed(batch: u32, row: u32, token: u32) -> bool {
  return !((p.causal != 0u && token > row) ||
           (p.has_mask != 0u && key_padding_mask[batch * p.seq_k + token] != 0.0));
}
fn score(batch: u32, row: u32, head: u32, token: u32) -> f32 {
  let kv_head = head / (p.heads / p.kv_heads);
  let qbase = (batch * p.seq_q + row) * p.model + head * p.head_dim;
  let kbase = (batch * p.seq_k + token) * p.kv_model + kv_head * p.head_dim;
  var sum: f32 = 0.0;
  for (var d: u32 = 0u; d < p.head_dim; d = d + 1u) {
    sum = sum + query[qbase + d] * key[kbase + d];
  }
  return sum * inverseSqrt(f32(p.head_dim));
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= p.total) { return; }
  let flat_row = i / p.model; let batch = flat_row / p.seq_q;
  let row = flat_row % p.seq_q; let component = i % p.model;
  let head = component / p.head_dim;
  let kv_head = head / (p.heads / p.kv_heads);
  var maximum: f32 = -3.402823e38;
  for (var token: u32 = 0u; token < p.seq_k; token = token + 1u) {
    if (allowed(batch, row, token)) {
      maximum = max(maximum, score(batch, row, head, token));
    }
  }
  var denominator: f32 = 0.0; var result: f32 = 0.0;
  for (var token: u32 = 0u; token < p.seq_k; token = token + 1u) {
    if (allowed(batch, row, token)) {
      let weight = exp(score(batch, row, head, token) - maximum);
      denominator = denominator + weight;
      result = result + weight * value[(batch * p.seq_k + token) * p.kv_model +
                                       kv_head * p.head_dim + component % p.head_dim];
    }
  }
  output[i] = select(0.0, result / denominator, denominator > 0.0);
}")

(def multi-head-attention-f16-wgsl
  "Packed-F16 fused attention with f32 dot products and stable online softmax."
  "
struct Params { batch: u32, seq_q: u32, seq_k: u32, model: u32,
                kv_model: u32, heads: u32, kv_heads: u32, head_dim: u32,
                total: u32, causal: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> query: array<u32>;
@group(0) @binding(1) var<storage, read> key: array<u32>;
@group(0) @binding(2) var<storage, read> value: array<u32>;
@group(0) @binding(3) var<storage, read_write> output: array<u32>;
@group(0) @binding(4) var<uniform> p: Params;
fn load_query(index: u32) -> f32 {
  let pair = unpack2x16float(query[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_key(index: u32) -> f32 {
  let pair = unpack2x16float(key[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_value(index: u32) -> f32 {
  let pair = unpack2x16float(value[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn score(batch: u32, row: u32, head: u32, token: u32) -> f32 {
  let kv_head = head / (p.heads / p.kv_heads);
  let qbase = (batch * p.seq_q + row) * p.model + head * p.head_dim;
  let kbase = (batch * p.seq_k + token) * p.kv_model + kv_head * p.head_dim;
  var sum = 0.0;
  for (var d = 0u; d < p.head_dim; d = d + 1u) {
    sum = sum + load_query(qbase + d) * load_key(kbase + d);
  }
  return sum * inverseSqrt(f32(p.head_dim));
}
fn attend(index: u32) -> f32 {
  let flat_row = index / p.model; let batch = flat_row / p.seq_q;
  let row = flat_row % p.seq_q; let component = index % p.model;
  let head = component / p.head_dim;
  let kv_head = head / (p.heads / p.kv_heads);
  var maximum = -3.402823e38;
  for (var token = 0u; token < p.seq_k; token = token + 1u) {
    if (!(p.causal != 0u && token > row)) {
      maximum = max(maximum, score(batch, row, head, token));
    }
  }
  var denominator = 0.0; var result = 0.0;
  for (var token = 0u; token < p.seq_k; token = token + 1u) {
    if (!(p.causal != 0u && token > row)) {
      let weight = exp(score(batch, row, head, token) - maximum);
      let vb = (batch * p.seq_k + token) * p.kv_model
               + kv_head * p.head_dim + component % p.head_dim;
      denominator = denominator + weight;
      result = result + weight * load_value(vb);
    }
  }
  return result / denominator;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let first = gid.x * 2u; if (first >= p.total) { return; }
  var second = 0.0;
  if (first + 1u < p.total) { second = attend(first + 1u); }
  output[gid.x] = pack2x16float(vec2<f32>(attend(first), second));
}")

(def multi-head-attention-backward-wgsl
  "Recompute stable softmax and produce Q/K/V gradients without host tensors."
  "
struct Params { batch: u32, seq_q: u32, seq_k: u32, model: u32,
                kv_model: u32, heads: u32, kv_heads: u32, head_dim: u32,
                total_q: u32, total_k: u32, total: u32, causal: u32,
                has_mask: u32, pad0: u32, pad1: u32, pad2: u32 }
@group(0) @binding(0) var<storage, read> query: array<f32>;
@group(0) @binding(1) var<storage, read> key: array<f32>;
@group(0) @binding(2) var<storage, read> value: array<f32>;
@group(0) @binding(3) var<storage, read> key_padding_mask: array<f32>;
@group(0) @binding(4) var<storage, read> grad_output: array<f32>;
@group(0) @binding(5) var<storage, read_write> grad_query: array<f32>;
@group(0) @binding(6) var<storage, read_write> grad_key: array<f32>;
@group(0) @binding(7) var<storage, read_write> grad_value: array<f32>;
@group(0) @binding(8) var<uniform> p: Params;
fn allowed(batch: u32, row: u32, token: u32) -> bool {
  return !((p.causal != 0u && token > row) ||
           (p.has_mask != 0u && key_padding_mask[batch * p.seq_k + token] != 0.0));
}
fn score(batch: u32, row: u32, head: u32, token: u32) -> f32 {
  let kv_head = head / (p.heads / p.kv_heads);
  let qb = (batch * p.seq_q + row) * p.model + head * p.head_dim;
  let kb = (batch * p.seq_k + token) * p.kv_model + kv_head * p.head_dim;
  var sum: f32 = 0.0;
  for (var d: u32 = 0u; d < p.head_dim; d = d + 1u) {
    sum = sum + query[qb + d] * key[kb + d];
  }
  return sum * inverseSqrt(f32(p.head_dim));
}
fn softmax_max(batch: u32, row: u32, head: u32) -> f32 {
  var maximum: f32 = -3.402823e38;
  for (var k: u32 = 0u; k < p.seq_k; k = k + 1u) {
    if (allowed(batch, row, k)) {
      maximum = max(maximum, score(batch, row, head, k));
    }
  }
  return maximum;
}
fn softmax_denominator(batch: u32, row: u32, head: u32, maximum: f32) -> f32 {
  var denominator: f32 = 0.0;
  for (var k: u32 = 0u; k < p.seq_k; k = k + 1u) {
    if (allowed(batch, row, k)) {
      denominator = denominator + exp(score(batch, row, head, k) - maximum);
    }
  }
  return denominator;
}
fn probability(batch: u32, row: u32, head: u32, token: u32,
               maximum: f32, denominator: f32) -> f32 {
  if (!allowed(batch, row, token) || denominator == 0.0) { return 0.0; }
  return exp(score(batch, row, head, token) - maximum) / denominator;
}
fn grad_probability(batch: u32, row: u32, head: u32, token: u32) -> f32 {
  let kv_head = head / (p.heads / p.kv_heads);
  let ob = (batch * p.seq_q + row) * p.model + head * p.head_dim;
  let vb = (batch * p.seq_k + token) * p.kv_model + kv_head * p.head_dim;
  var sum: f32 = 0.0;
  for (var d: u32 = 0u; d < p.head_dim; d = d + 1u) {
    sum = sum + grad_output[ob + d] * value[vb + d];
  }
  return sum;
}
fn grad_expectation(batch: u32, row: u32, head: u32,
                    maximum: f32, denominator: f32) -> f32 {
  var expected: f32 = 0.0;
  for (var k: u32 = 0u; k < p.seq_k; k = k + 1u) {
    expected = expected + probability(batch, row, head, k, maximum, denominator) *
                          grad_probability(batch, row, head, k);
  }
  return expected;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; let scale = inverseSqrt(f32(p.head_dim));
  if (i < p.total_q) {
    let flat_row = i / p.model; let batch = flat_row / p.seq_q;
    let row = flat_row % p.seq_q; let component = i % p.model;
    let head = component / p.head_dim;
    let maximum = softmax_max(batch, row, head);
    let denominator = softmax_denominator(batch, row, head, maximum);
    let expected = grad_expectation(batch, row, head, maximum, denominator);
    var dq: f32 = 0.0;
    for (var token: u32 = 0u; token < p.seq_k; token = token + 1u) {
      let prob = probability(batch, row, head, token, maximum, denominator);
      let ds = prob * (grad_probability(batch, row, head, token) - expected);
      dq = dq + ds * key[(batch * p.seq_k + token) * p.kv_model +
                         (head / (p.heads / p.kv_heads)) * p.head_dim +
                         component % p.head_dim] * scale;
    }
    grad_query[i] = dq;
  }
  if (i < p.total_k) {
    let flat_token = i / p.kv_model; let batch = flat_token / p.seq_k;
    let token = flat_token % p.seq_k; let component = i % p.kv_model;
    let kv_head = component / p.head_dim; let local = component % p.head_dim;
    let group_size = p.heads / p.kv_heads;
    var dk: f32 = 0.0; var dv: f32 = 0.0;
    for (var offset: u32 = 0u; offset < group_size; offset = offset + 1u) {
      let head = kv_head * group_size + offset;
      let q_component = head * p.head_dim + local;
      for (var row: u32 = 0u; row < p.seq_q; row = row + 1u) {
        let maximum = softmax_max(batch, row, head);
        let denominator = softmax_denominator(batch, row, head, maximum);
        let prob = probability(batch, row, head, token, maximum, denominator);
        let expected = grad_expectation(batch, row, head, maximum, denominator);
        let ds = prob * (grad_probability(batch, row, head, token) - expected);
        dk = dk + ds * query[(batch * p.seq_q + row) * p.model + q_component] * scale;
        dv = dv + prob * grad_output[(batch * p.seq_q + row) * p.model + q_component];
      }
    }
    grad_key[i] = dk; grad_value[i] = dv;
  }
}")

(def add-bias-rows-wgsl
  "Add one shared bias vector to every matrix row."
  "
@group(0) @binding(0) var<storage, read_write> matrix: array<f32>;
@group(0) @binding(1) var<storage, read> bias: array<f32>;
@group(0) @binding(2) var<uniform> dims: vec2<u32>;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= dims.x * dims.y) { return; }
  matrix[i] = matrix[i] + bias[i % dims.y];
}")

(def mse-loss-wgsl
  "Mean squared error reduced into one device-resident scalar."
  "
@group(0) @binding(0) var<storage, read> prediction: array<f32>;
@group(0) @binding(1) var<storage, read> expected: array<f32>;
@group(0) @binding(2) var<storage, read_write> loss: array<f32>;
@group(0) @binding(3) var<uniform> count: u32;
var<workgroup> partial: array<f32, 64>;
@compute @workgroup_size(64)
fn main(@builtin(local_invocation_id) lid3: vec3<u32>) {
  let lid = lid3.x;
  var sum: f32 = 0.0;
  for (var i: u32 = lid; i < count; i = i + 64u) {
    let difference = prediction[i] - expected[i];
    sum = sum + difference * difference;
  }
  partial[lid] = sum;
  workgroupBarrier();
  var stride: u32 = 32u;
  loop {
    if (stride == 0u) { break; }
    if (lid < stride) { partial[lid] = partial[lid] + partial[lid + stride]; }
    workgroupBarrier();
    stride = stride / 2u;
  }
  if (lid == 0u) { loss[0] = partial[0] / f32(count); }
}")

(def mse-gradient-wgsl
  "MSE vector-Jacobian product, including the upstream scalar seed."
  "
@group(0) @binding(0) var<storage, read> prediction: array<f32>;
@group(0) @binding(1) var<storage, read> expected: array<f32>;
@group(0) @binding(2) var<storage, read> upstream: array<f32>;
@group(0) @binding(3) var<storage, read_write> gradient: array<f32>;
@group(0) @binding(4) var<uniform> count: u32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= count) { return; }
  gradient[i] = upstream[0] * 2.0 * (prediction[i] - expected[i]) / f32(count);
}")

(def cross-entropy-stats-f16-wgsl
  "Stable per-row max and exponential denominator for packed-F16 logits."
  "
struct Params { rows: u32, classes: u32, total: u32, ignore_index: u32 }
@group(0) @binding(0) var<storage, read> logits: array<u32>;
@group(0) @binding(1) var<storage, read_write> stats: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
var<workgroup> scratch: array<f32, 64>;
fn load_logit(index: u32) -> f32 {
  let pair = unpack2x16float(logits[index / 2u]);
  return select(pair.x, pair.y, (index & 1u) == 1u);
}
@compute @workgroup_size(64)
fn main(@builtin(workgroup_id) wid: vec3<u32>,
        @builtin(local_invocation_id) lid: vec3<u32>) {
  let row = wid.x; let lane = lid.x;
  if (row >= p.rows) { return; }
  var maximum = -3.402823466e+38;
  for (var c = lane; c < p.classes; c += 64u) {
    maximum = max(maximum, load_logit(row * p.classes + c));
  }
  scratch[lane] = maximum; workgroupBarrier();
  var stride = 32u;
  loop {
    if (lane < stride) { scratch[lane] = max(scratch[lane], scratch[lane + stride]); }
    workgroupBarrier();
    if (stride == 1u) { break; }
    stride /= 2u;
  }
  let row_max = scratch[0];
  var denominator = 0.0;
  for (var c = lane; c < p.classes; c += 64u) {
    denominator += exp(load_logit(row * p.classes + c) - row_max);
  }
  scratch[lane] = denominator; workgroupBarrier();
  stride = 32u;
  loop {
    if (lane < stride) { scratch[lane] += scratch[lane + stride]; }
    workgroupBarrier();
    if (stride == 1u) { break; }
    stride /= 2u;
  }
  if (lane == 0u) { stats[row * 2u] = row_max; stats[row * 2u + 1u] = scratch[0]; }
}")

(def cross-entropy-loss-f16-wgsl
  "Mean stable NLL and inverse valid-row count for integer labels."
  "
struct Params { rows: u32, classes: u32, total: u32, ignore_index: u32 }
@group(0) @binding(0) var<storage, read> logits: array<u32>;
@group(0) @binding(1) var<storage, read> labels: array<f32>;
@group(0) @binding(2) var<storage, read> stats: array<f32>;
@group(0) @binding(3) var<storage, read_write> scalar: array<f32>;
@group(0) @binding(4) var<uniform> p: Params;
fn load_logit(index: u32) -> f32 {
  let pair = unpack2x16float(logits[index / 2u]);
  return select(pair.x, pair.y, (index & 1u) == 1u);
}
@compute @workgroup_size(1)
fn main() {
  var sum = 0.0; var valid = 0u;
  for (var row = 0u; row < p.rows; row += 1u) {
    let signed_label = i32(labels[row]);
    if (bitcast<u32>(signed_label) != p.ignore_index) {
      let label = u32(signed_label);
      if (label < p.classes) {
        sum += stats[row * 2u] + log(stats[row * 2u + 1u]) -
               load_logit(row * p.classes + label);
        valid += 1u;
      }
    }
  }
  let inverse = select(0.0, 1.0 / f32(valid), valid > 0u);
  scalar[0] = sum * inverse; scalar[1] = inverse;
}")

(def cross-entropy-gradient-f16-wgsl
  "Packed-F16 `(softmax-onehot)/valid * upstream` VJP."
  "
struct Params { rows: u32, classes: u32, total: u32, ignore_index: u32 }
@group(0) @binding(0) var<storage, read> logits: array<u32>;
@group(0) @binding(1) var<storage, read> labels: array<f32>;
@group(0) @binding(2) var<storage, read> stats: array<f32>;
@group(0) @binding(3) var<storage, read> scalar: array<f32>;
@group(0) @binding(4) var<storage, read> upstream: array<f32>;
@group(0) @binding(5) var<storage, read_write> grad_logits: array<u32>;
@group(0) @binding(6) var<uniform> p: Params;
fn load_logit(index: u32) -> f32 {
  let pair = unpack2x16float(logits[index / 2u]);
  return select(pair.x, pair.y, (index & 1u) == 1u);
}
fn gradient(index: u32) -> f32 {
  if (index >= p.total) { return 0.0; }
  let row = index / p.classes; let c = index % p.classes;
  let signed_label = i32(labels[row]);
  if (bitcast<u32>(signed_label) == p.ignore_index) { return 0.0; }
  let probability = exp(load_logit(index) - stats[row * 2u]) /
                    stats[row * 2u + 1u];
  return (probability - select(0.0, 1.0, c == u32(signed_label))) *
         scalar[1] * upstream[0];
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let word = gid.x; let base = word * 2u;
  if (base >= p.total) { return; }
  grad_logits[word] = pack2x16float(vec2<f32>(gradient(base), gradient(base + 1u)));
}")

(def relu-backward-wgsl
  "ReLU vector-Jacobian product using saved pre-activation values."
  "
@group(0) @binding(0) var<storage, read> upstream: array<f32>;
@group(0) @binding(1) var<storage, read> preactivation: array<f32>;
@group(0) @binding(2) var<storage, read_write> gradient: array<f32>;
@group(0) @binding(3) var<uniform> count: u32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= count) { return; }
  gradient[i] = select(0.0, upstream[i], preactivation[i] > 0.0);
}")

(def bias-gradient-wgsl
  "Sum a matrix gradient over rows into its shared bias gradient."
  "
@group(0) @binding(0) var<storage, read> gradient: array<f32>;
@group(0) @binding(1) var<storage, read_write> bias_gradient: array<f32>;
@group(0) @binding(2) var<uniform> dims: vec2<u32>;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let col = gid.x; if (col >= dims.y) { return; }
  var sum: f32 = 0.0;
  for (var row: u32 = 0u; row < dims.x; row = row + 1u) {
    sum = sum + gradient[row * dims.y + col];
  }
  bias_gradient[col] = sum;
}")

(def sgd-update-wgsl
  "In-place SGD parameter update."
  "
@group(0) @binding(0) var<storage, read_write> parameter: array<f32>;
@group(0) @binding(1) var<storage, read> gradient: array<f32>;
struct Params { learning_rate: f32, count: u32, pad0: u32, pad1: u32 }
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= p.count) { return; }
  parameter[i] = parameter[i] - p.learning_rate * gradient[i];
}")

(def sgd-step-wgsl
  "Out-of-place parameter - learning_rate * gradient update."
  "
@group(0) @binding(0) var<storage, read> parameter: array<f32>;
@group(0) @binding(1) var<storage, read> gradient: array<f32>;
@group(0) @binding(2) var<storage, read_write> output: array<f32>;
@group(0) @binding(3) var<uniform> learning_rate: f32;
@group(0) @binding(4) var<uniform> count: u32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= count) { return; }
  output[i] = parameter[i] - learning_rate * gradient[i];
}")

(def adamw-step-wgsl
  "Fused out-of-place AdamW parameter and optimizer-slot update."
  "
@group(0) @binding(0) var<storage, read> parameter: array<f32>;
@group(0) @binding(1) var<storage, read> gradient: array<f32>;
@group(0) @binding(2) var<storage, read> moment: array<f32>;
@group(0) @binding(3) var<storage, read> variance: array<f32>;
@group(0) @binding(4) var<storage, read_write> next_parameter: array<f32>;
@group(0) @binding(5) var<storage, read_write> next_moment: array<f32>;
@group(0) @binding(6) var<storage, read_write> next_variance: array<f32>;
struct Hyper {
  learning_rate: f32, beta1: f32, beta2: f32, eps: f32,
  weight_decay: f32, correction1: f32, correction2: f32, pad0: f32,
}
@group(0) @binding(7) var<uniform> h: Hyper;
@group(0) @binding(8) var<uniform> count: u32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= count) { return; }
  let m = h.beta1 * moment[i] + (1.0 - h.beta1) * gradient[i];
  let v = h.beta2 * variance[i] + (1.0 - h.beta2) * gradient[i] * gradient[i];
  let adaptive = (m / h.correction1) /
                 (sqrt(v / h.correction2) + h.eps);
  next_moment[i] = m;
  next_variance[i] = v;
  next_parameter[i] = parameter[i] - h.learning_rate *
                      (adaptive + h.weight_decay * parameter[i]);
}")

(def unscale-gradient-wgsl
  "Fused loss-scale removal and atomic non-finite detection."
  "
@group(0) @binding(0) var<storage, read> gradient: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<storage, read_write> found_inf: atomic<u32>;
@group(0) @binding(3) var<uniform> inverse_scale: f32;
@group(0) @binding(4) var<uniform> count: u32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= count) { return; }
  let value = gradient[i];
  if (value != value || abs(value) > 3.402823e+38) {
    atomicOr(&found_inf, 1u);
  }
  output[i] = value * inverse_scale;
}")

(def conv2d-nchw-wgsl
  "Direct NCHW convolution/cross-correlation. One invocation computes one
  output element; supports bias, groups/depthwise, stride, padding, dilation."
  "
struct Params {
  n: u32, cin: u32, h: u32, w: u32,
  cout: u32, cin_group: u32, kh: u32, kw: u32,
  oh: u32, ow: u32, sh: u32, sw: u32,
  ph: u32, pw: u32, dh: u32, dw: u32,
  groups: u32, pad0: u32, pad1: u32, pad2: u32,
}
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read> weight: array<f32>;
@group(0) @binding(2) var<storage, read> bias: array<f32>;
@group(0) @binding(3) var<storage, read_write> output: array<f32>;
@group(0) @binding(4) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x;
  let total = p.n * p.cout * p.oh * p.ow;
  if (index >= total) { return; }
  let oj = index % p.ow;
  let oi = (index / p.ow) % p.oh;
  let oc = (index / (p.ow * p.oh)) % p.cout;
  let batch = index / (p.ow * p.oh * p.cout);
  let outputs_per_group = p.cout / p.groups;
  let group = oc / outputs_per_group;
  let input_channel_base = group * p.cin_group;
  var sum = bias[oc];
  for (var icg: u32 = 0u; icg < p.cin_group; icg = icg + 1u) {
    let ic = input_channel_base + icg;
    for (var ki: u32 = 0u; ki < p.kh; ki = ki + 1u) {
      let ih = i32(oi * p.sh + ki * p.dh) - i32(p.ph);
      if (ih < 0 || ih >= i32(p.h)) { continue; }
      for (var kj: u32 = 0u; kj < p.kw; kj = kj + 1u) {
        let iw = i32(oj * p.sw + kj * p.dw) - i32(p.pw);
        if (iw < 0 || iw >= i32(p.w)) { continue; }
        let x_index = ((batch * p.cin + ic) * p.h + u32(ih)) * p.w + u32(iw);
        let w_index = ((oc * p.cin_group + icg) * p.kh + ki) * p.kw + kj;
        sum = sum + input[x_index] * weight[w_index];
      }
    }
  }
  output[index] = sum;
}")

(def conv2d-nchw-oc4-wgsl
  "Groups=1 fast path computing four output channels per invocation so each
  input element and coordinate calculation is reused across four FMAs."
  "
struct Params {
  n: u32, cin: u32, h: u32, w: u32,
  cout: u32, cin_group: u32, kh: u32, kw: u32,
  oh: u32, ow: u32, sh: u32, sw: u32,
  ph: u32, pw: u32, dh: u32, dw: u32,
  groups: u32, pad0: u32, pad1: u32, pad2: u32,
}
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read> weight: array<f32>;
@group(0) @binding(2) var<storage, read> bias: array<f32>;
@group(0) @binding(3) var<storage, read_write> output: array<f32>;
@group(0) @binding(4) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x;
  let spatial = p.oh * p.ow;
  let cout4 = p.cout / 4u;
  let total = p.n * cout4 * spatial;
  if (index >= total) { return; }
  let position = index % spatial;
  let oj = position % p.ow;
  let oi = position / p.ow;
  let oc_block = (index / spatial) % cout4;
  let batch = index / (spatial * cout4);
  let oc = oc_block * 4u;
  var sum = vec4<f32>(bias[oc], bias[oc + 1u], bias[oc + 2u], bias[oc + 3u]);
  let kernel_size = p.cin * p.kh * p.kw;
  for (var ic: u32 = 0u; ic < p.cin; ic = ic + 1u) {
    for (var ki: u32 = 0u; ki < p.kh; ki = ki + 1u) {
      let ih = i32(oi * p.sh + ki * p.dh) - i32(p.ph);
      if (ih < 0 || ih >= i32(p.h)) { continue; }
      for (var kj: u32 = 0u; kj < p.kw; kj = kj + 1u) {
        let iw = i32(oj * p.sw + kj * p.dw) - i32(p.pw);
        if (iw < 0 || iw >= i32(p.w)) { continue; }
        let kernel_index = (ic * p.kh + ki) * p.kw + kj;
        let x_index = ((batch * p.cin + ic) * p.h + u32(ih)) * p.w + u32(iw);
        let x = input[x_index];
        let weights = vec4<f32>(weight[oc * kernel_size + kernel_index],
                                weight[(oc + 1u) * kernel_size + kernel_index],
                                weight[(oc + 2u) * kernel_size + kernel_index],
                                weight[(oc + 3u) * kernel_size + kernel_index]);
        sum = sum + vec4<f32>(x) * weights;
      }
    }
  }
  output[(batch * p.cout + oc) * spatial + position] = sum.x;
  output[(batch * p.cout + oc + 1u) * spatial + position] = sum.y;
  output[(batch * p.cout + oc + 2u) * spatial + position] = sum.z;
  output[(batch * p.cout + oc + 3u) * spatial + position] = sum.w;
}")

(def group-norm-nchw-wgsl
  "Parallel GroupNorm: one 256-thread workgroup reduces and normalizes one
  `[channels/group,H,W]` slice. Variance uses the biased PyTorch definition."
  "
struct Dims {
  n: u32, c: u32, h: u32, w: u32,
  groups: u32, channels_group: u32, group_size: u32, spatial: u32,
}
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read> weight: array<f32>;
@group(0) @binding(2) var<storage, read> bias: array<f32>;
@group(0) @binding(3) var<storage, read_write> output: array<f32>;
@group(0) @binding(4) var<uniform> d: Dims;
@group(0) @binding(5) var<uniform> epsilon: f32;
var<workgroup> sums: array<f32, 256>;
var<workgroup> squares: array<f32, 256>;
@compute @workgroup_size(256)
fn main(@builtin(local_invocation_id) lid3: vec3<u32>,
        @builtin(workgroup_id) wid3: vec3<u32>) {
  let lid = lid3.x;
  let slice = wid3.x;
  if (slice >= d.n * d.groups) { return; }
  let batch = slice / d.groups;
  let group = slice % d.groups;
  let base = batch * d.c * d.h * d.w + group * d.group_size;
  var sum = 0.0;
  var sum_square = 0.0;
  for (var i = lid; i < d.group_size; i = i + 256u) {
    let value = input[base + i];
    sum = sum + value;
    sum_square = sum_square + value * value;
  }
  sums[lid] = sum;
  squares[lid] = sum_square;
  workgroupBarrier();
  var offset = 128u;
  loop {
    if (offset == 0u) { break; }
    if (lid < offset) {
      sums[lid] = sums[lid] + sums[lid + offset];
      squares[lid] = squares[lid] + squares[lid + offset];
    }
    workgroupBarrier();
    offset = offset / 2u;
  }
  let mean = sums[0] / f32(d.group_size);
  let variance = max(squares[0] / f32(d.group_size) - mean * mean, 0.0);
  let inv_std = inverseSqrt(variance + epsilon);
  for (var i = lid; i < d.group_size; i = i + 256u) {
    let channel = group * d.channels_group + i / d.spatial;
    output[base + i] = (input[base + i] - mean) * inv_std * weight[channel] + bias[channel];
  }
}")

(def group-norm-silu-nchw-wgsl
  (str/replace
   group-norm-nchw-wgsl
   "output[base + i] = (input[base + i] - mean) * inv_std * weight[channel] + bias[channel];"
   "let normalized = (input[base + i] - mean) * inv_std * weight[channel] + bias[channel];\n    output[base + i] = normalized / (1.0 + exp(-normalized));"))

(def upsample-nearest2d-wgsl
  "Nearest-neighbor NCHW upsampling, one invocation per output element."
  "
struct Dims {
  n: u32, c: u32, h: u32, w: u32,
  oh: u32, ow: u32, scale_h: u32, scale_w: u32,
}
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> d: Dims;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x;
  let total = d.n * d.c * d.oh * d.ow;
  if (index >= total) { return; }
  let oj = index % d.ow;
  let oi = (index / d.ow) % d.oh;
  let channel = (index / (d.ow * d.oh)) % d.c;
  let batch = index / (d.ow * d.oh * d.c);
  let input_index = ((batch * d.c + channel) * d.h + oi / d.scale_h) * d.w
                    + oj / d.scale_w;
  output[index] = input[input_index];
}")

(def cat-copy-wgsl
  "Copy one contiguous tensor into its slice of a concatenated output. Repeated
  queue-ordered dispatches fill one output buffer without host readback."
  "
struct Dims {
  total: u32, block: u32, output_block: u32, axis_offset: u32,
}
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> d: Dims;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x;
  if (index >= d.total) { return; }
  let outer = index / d.block;
  let within = index % d.block;
  output[outer * d.output_block + d.axis_offset + within] = input[index];
}")

(def slice-axis-wgsl
  "Copy a contiguous range from each flattened outer block."
  "
struct Dims {
  total: u32, input_block: u32, output_block: u32, input_offset: u32,
}
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> d: Dims;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x;
  if (index >= d.total) { return; }
  let outer = index / d.output_block;
  let within = index % d.output_block;
  output[index] = input[outer * d.input_block + d.input_offset + within];
}")

(def upsample-nearest2d-f16-wgsl
  "Packed-F16 nearest-neighbor NCHW upsampling, one invocation per output pair."
  "
struct Dims {
  n: u32, c: u32, h: u32, w: u32,
  oh: u32, ow: u32, scale_h: u32, scale_w: u32, total: u32,
}
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read_write> output: array<u32>;
@group(0) @binding(2) var<uniform> d: Dims;
fn load(index: u32) -> f32 {
  let pair = unpack2x16float(input[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn source(index: u32) -> u32 {
  let x = index % d.ow;
  let y = (index / d.ow) % d.oh;
  let channel = (index / (d.ow * d.oh)) % d.c;
  let batch = index / (d.ow * d.oh * d.c);
  return ((batch * d.c + channel) * d.h + y / d.scale_h) * d.w
         + x / d.scale_w;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>,
        @builtin(num_workgroups) grid: vec3<u32>) {
  let word = gid.x + gid.y * grid.x * 64u;
  let first = word * 2u;
  if (first >= d.total) { return; }
  var second = 0.0;
  if (first + 1u < d.total) { second = load(source(first + 1u)); }
  output[word] = pack2x16float(vec2<f32>(load(source(first)), second));
}")

(def slice-axis-f16-wgsl
  "Packed-F16 contiguous axis slicing, including unaligned input offsets."
  "
struct Dims {
  total: u32, input_block: u32, output_block: u32, input_offset: u32,
}
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read_write> output: array<u32>;
@group(0) @binding(2) var<uniform> d: Dims;
fn load(index: u32) -> f32 {
  let pair = unpack2x16float(input[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn source(index: u32) -> u32 {
  return (index / d.output_block) * d.input_block + d.input_offset
         + index % d.output_block;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>,
        @builtin(num_workgroups) grid: vec3<u32>) {
  let word = gid.x + gid.y * grid.x * 64u;
  let first = word * 2u;
  if (first >= d.total) { return; }
  var second = 0.0;
  if (first + 1u < d.total) { second = load(source(first + 1u)); }
  output[word] = pack2x16float(vec2<f32>(load(source(first)), second));
}")

(def pad-right-bottom-nchw-wgsl
  "Append a zero-valued right column and bottom row to every NCHW plane."
  "
struct Dims { total: u32, h: u32, w: u32, output_w: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> d: Dims;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x;
  if (index >= d.total) { return; }
  let x = index % d.output_w;
  let y = (index / d.output_w) % (d.h + 1u);
  if (x == d.w || y == d.h) {
    output[index] = 0.0;
  } else {
    let plane = index / (d.output_w * (d.h + 1u));
    output[index] = input[(plane * d.h + y) * d.w + x];
  }
}")

(def ewise-f16-wgsl
  "Packed physical f16 elementwise arithmetic with f32 evaluation."
  "
struct Params { op: u32, n: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> x: array<u32>;
@group(0) @binding(1) var<storage, read> y: array<u32>;
@group(0) @binding(2) var<storage, read_write> z: array<u32>;
@group(0) @binding(3) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>,
        @builtin(num_workgroups) grid: vec3<u32>) {
  let word = gid.x + gid.y * grid.x * 64u;
  let base = word * 2u;
  if (base >= p.n) { return; }
  let xv = unpack2x16float(x[word]);
  let yv = unpack2x16float(y[word]);
  var out: vec2<f32>;
  if (p.op == 0u) { out = xv + yv; }
  else if (p.op == 1u) { out = xv - yv; }
  else if (p.op == 2u) { out = xv * yv; }
  else { out = xv / yv; }
  if (base + 1u >= p.n) { out.y = 0.0; }
  z[word] = pack2x16float(out);
}")

(def ewise1-f16-wgsl
  "Packed physical f16 unary ops, with transcendental evaluation in f32."
  "
struct Params { op: u32, n: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> x: array<u32>;
@group(0) @binding(1) var<storage, read_write> z: array<u32>;
@group(0) @binding(2) var<uniform> p: Params;
fn erf_approx(x: f32) -> f32 {
  let s = select(1.0, -1.0, x < 0.0);
  let a = abs(x);
  let t = 1.0 / (1.0 + 0.3275911 * a);
  let q = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 +
          t * (-1.453152027 + t * 1.061405429))));
  return s * (1.0 - q * exp(-a * a));
}
fn apply(v: f32) -> f32 {
  if (p.op == 0u) { return exp(v); }
  if (p.op == 1u) { return max(v, 0.0); }
  if (p.op == 2u) { return -v; }
  if (p.op == 3u) { return v / (1.0 + exp(-v)); }
  if (p.op == 4u) { return 1.0 / (1.0 + exp(-v)); }
  if (p.op == 5u) { return tanh(v); }
  if (p.op == 6u) { return v * (1.0 - v); }
  if (p.op == 7u) { return 1.0 - v * v; }
  if (p.op == 8u) {
    return 0.5 * v * (1.0 + erf_approx(v * 0.7071067811865476));
  }
  return 0.5 * (1.0 + erf_approx(v * 0.7071067811865476)) +
         v * 0.3989422804014327 * exp(-0.5 * v * v);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>,
        @builtin(num_workgroups) grid: vec3<u32>) {
  let word = gid.x + gid.y * grid.x * 64u;
  let base = word * 2u;
  if (base >= p.n) { return; }
  let v = unpack2x16float(x[word]);
  let second = select(apply(v.y), 0.0, base + 1u >= p.n);
  z[word] = pack2x16float(vec2<f32>(apply(v.x), second));
}")

(def gemm-f16-wgsl
  "Packed physical f16 GEMM with f32 accumulation and f16 output."
  "
struct Dims { m: u32, k: u32, n: u32, pad: u32 }
@group(0) @binding(0) var<storage, read> A: array<u32>;
@group(0) @binding(1) var<storage, read> B: array<u32>;
@group(0) @binding(2) var<storage, read_write> C: array<u32>;
@group(0) @binding(3) var<uniform> d: Dims;
fn load_a(index: u32) -> f32 {
  let pair = unpack2x16float(A[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_b(index: u32) -> f32 {
  let pair = unpack2x16float(B[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn dot_at(index: u32) -> f32 {
  let row = index / d.n;
  let col = index % d.n;
  var sum: f32 = 0.0;
  for (var l: u32 = 0u; l < d.k; l = l + 1u) {
    sum = sum + load_a(row * d.k + l) * load_b(l * d.n + col);
  }
  return sum;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let word = gid.x;
  let base = word * 2u;
  let total = d.m * d.n;
  if (base >= total) { return; }
  var second: f32 = 0.0;
  if (base + 1u < total) { second = dot_at(base + 1u); }
  C[word] = pack2x16float(vec2<f32>(dot_at(base), second));
}")

(def conv2d-nchw-f16-wgsl
  "Packed-f16 NCHW grouped convolution with f32 accumulation."
  "
struct Params {
  n: u32, cin: u32, h: u32, w: u32,
  cout: u32, cin_group: u32, kh: u32, kw: u32,
  oh: u32, ow: u32, sh: u32, sw: u32,
  ph: u32, pw: u32, dh: u32, dw: u32,
  groups: u32, pad0: u32, pad1: u32, pad2: u32,
}
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read> bias: array<u32>;
@group(0) @binding(3) var<storage, read_write> output: array<u32>;
@group(0) @binding(4) var<uniform> p: Params;
fn load_input(index: u32) -> f32 {
  let pair = unpack2x16float(input[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_weight(index: u32) -> f32 {
  let pair = unpack2x16float(weight[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_bias(index: u32) -> f32 {
  let pair = unpack2x16float(bias[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn convolve(index: u32) -> f32 {
  let oj = index % p.ow;
  let oi = (index / p.ow) % p.oh;
  let oc = (index / (p.ow * p.oh)) % p.cout;
  let batch = index / (p.ow * p.oh * p.cout);
  let outputs_per_group = p.cout / p.groups;
  let input_channel_base = (oc / outputs_per_group) * p.cin_group;
  var sum = load_bias(oc);
  for (var icg = 0u; icg < p.cin_group; icg = icg + 1u) {
    let ic = input_channel_base + icg;
    for (var ki = 0u; ki < p.kh; ki = ki + 1u) {
      let ih = i32(oi * p.sh + ki * p.dh) - i32(p.ph);
      if (ih < 0 || ih >= i32(p.h)) { continue; }
      for (var kj = 0u; kj < p.kw; kj = kj + 1u) {
        let iw = i32(oj * p.sw + kj * p.dw) - i32(p.pw);
        if (iw < 0 || iw >= i32(p.w)) { continue; }
        let xi = ((batch * p.cin + ic) * p.h + u32(ih)) * p.w + u32(iw);
        let wi = ((oc * p.cin_group + icg) * p.kh + ki) * p.kw + kj;
        sum = sum + load_input(xi) * load_weight(wi);
      }
    }
  }
  return sum;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let base = gid.x * 2u;
  let total = p.n * p.cout * p.oh * p.ow;
  if (base >= total) { return; }
  var second = 0.0;
  if (base + 1u < total) { second = convolve(base + 1u); }
  output[gid.x] = pack2x16float(vec2<f32>(convolve(base), second));
}")

(def conv2d-nchw-f16-oc4-wgsl
  "Packed-f16 groups=1 fast path. One invocation computes four output
  channels at two adjacent spatial positions, reusing each input load across
  four FMAs and emitting four naturally aligned packed-f16 words."
  "
struct Params {
  n: u32, cin: u32, h: u32, w: u32,
  cout: u32, cin_group: u32, kh: u32, kw: u32,
  oh: u32, ow: u32, sh: u32, sw: u32,
  ph: u32, pw: u32, dh: u32, dw: u32,
  groups: u32, pad0: u32, pad1: u32, pad2: u32,
}
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read> bias: array<u32>;
@group(0) @binding(3) var<storage, read_write> output: array<u32>;
@group(0) @binding(4) var<uniform> p: Params;
fn load_input_oc4(index: u32) -> f32 {
  let pair = unpack2x16float(input[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_weight_oc4(index: u32) -> f32 {
  let pair = unpack2x16float(weight[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_bias_oc4(index: u32) -> f32 {
  let pair = unpack2x16float(bias[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn convolve4(batch: u32, oc: u32, position: u32) -> vec4<f32> {
  let oi = position / p.ow; let oj = position % p.ow;
  let kernel_size = p.cin * p.kh * p.kw;
  var sum = vec4<f32>(load_bias_oc4(oc), load_bias_oc4(oc + 1u),
                      load_bias_oc4(oc + 2u), load_bias_oc4(oc + 3u));
  for (var ic = 0u; ic < p.cin; ic = ic + 1u) {
    for (var ki = 0u; ki < p.kh; ki = ki + 1u) {
      let ih = i32(oi * p.sh + ki * p.dh) - i32(p.ph);
      if (ih < 0 || ih >= i32(p.h)) { continue; }
      for (var kj = 0u; kj < p.kw; kj = kj + 1u) {
        let iw = i32(oj * p.sw + kj * p.dw) - i32(p.pw);
        if (iw < 0 || iw >= i32(p.w)) { continue; }
        let kernel_index = (ic * p.kh + ki) * p.kw + kj;
        let xi = ((batch * p.cin + ic) * p.h + u32(ih)) * p.w + u32(iw);
        let x = load_input_oc4(xi);
        let weights = vec4<f32>(load_weight_oc4(oc * kernel_size + kernel_index),
          load_weight_oc4((oc + 1u) * kernel_size + kernel_index),
          load_weight_oc4((oc + 2u) * kernel_size + kernel_index),
          load_weight_oc4((oc + 3u) * kernel_size + kernel_index));
        sum = sum + vec4<f32>(x) * weights;
      }
    }
  }
  return sum;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let spatial = p.oh * p.ow; let pairs = spatial / 2u; let cout4 = p.cout / 4u;
  let total = p.n * cout4 * pairs; let index = gid.x;
  if (index >= total) { return; }
  let pair_position = index % pairs; let position = pair_position * 2u;
  let oc_block = (index / pairs) % cout4; let oc = oc_block * 4u;
  let batch = index / (pairs * cout4);
  let first = convolve4(batch, oc, position);
  let second = convolve4(batch, oc, position + 1u);
  for (var lane = 0u; lane < 4u; lane = lane + 1u) {
    let output_index = ((batch * p.cout + oc + lane) * spatial + position) / 2u;
    output[output_index] = pack2x16float(vec2<f32>(first[lane], second[lane]));
  }
}")

(def group-norm-nchw-f16-reference-wgsl
  "Packed-f16 GroupNorm reference kernel with f32 statistics."
  "
struct Dims {
  n: u32, c: u32, h: u32, w: u32,
  groups: u32, channels_group: u32, group_size: u32, spatial: u32,
  activation: u32,
}
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read> bias: array<u32>;
@group(0) @binding(3) var<storage, read_write> output: array<u32>;
@group(0) @binding(4) var<uniform> d: Dims;
@group(0) @binding(5) var<uniform> epsilon: f32;
fn load_input_value(index: u32) -> f32 {
  let pair = unpack2x16float(input[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_weight_value(index: u32) -> f32 {
  let pair = unpack2x16float(weight[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_bias_value(index: u32) -> f32 {
  let pair = unpack2x16float(bias[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn normalize(index: u32) -> f32 {
  let batch = index / (d.c * d.h * d.w);
  let channel = (index / d.spatial) % d.c;
  let group = channel / d.channels_group;
  let base = batch * d.c * d.h * d.w + group * d.group_size;
  var sum = 0.0;
  var sum_square = 0.0;
  for (var i = 0u; i < d.group_size; i = i + 1u) {
    let v = load_input_value(base + i);
    sum = sum + v;
    sum_square = sum_square + v * v;
  }
  let mean = sum / f32(d.group_size);
  let variance = max(sum_square / f32(d.group_size) - mean * mean, 0.0);
  let normalized = (load_input_value(index) - mean) * inverseSqrt(variance + epsilon)
                   * load_weight_value(channel) + load_bias_value(channel);
  if (d.activation == 1u) { return normalized / (1.0 + exp(-normalized)); }
  return normalized;
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let base = gid.x * 2u;
  let total = d.n * d.c * d.h * d.w;
  if (base >= total) { return; }
  var second = 0.0;
  if (base + 1u < total) { second = normalize(base + 1u); }
  output[gid.x] = pack2x16float(vec2<f32>(normalize(base), second));
}")

(def group-norm-nchw-f16-wgsl
  "Parallel packed-F16 GroupNorm. One workgroup computes statistics once per
  group and owns complete packed pairs; callers use the reference kernel for
  odd group sizes where a packed word would straddle two groups."
  "
struct Dims {
  n: u32, c: u32, h: u32, w: u32,
  groups: u32, channels_group: u32, group_size: u32, spatial: u32,
  activation: u32,
}
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read> bias: array<u32>;
@group(0) @binding(3) var<storage, read_write> output: array<u32>;
@group(0) @binding(4) var<uniform> d: Dims;
@group(0) @binding(5) var<uniform> epsilon: f32;
var<workgroup> sums: array<f32, 256>;
var<workgroup> squares: array<f32, 256>;
fn load_input(index: u32) -> f32 {
  let pair = unpack2x16float(input[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_weight(index: u32) -> f32 {
  let pair = unpack2x16float(weight[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn load_bias(index: u32) -> f32 {
  let pair = unpack2x16float(bias[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
fn activate(value: f32) -> f32 {
  if (d.activation == 1u) { return value / (1.0 + exp(-value)); }
  return value;
}
@compute @workgroup_size(256)
fn main(@builtin(local_invocation_id) lid3: vec3<u32>,
        @builtin(workgroup_id) wid3: vec3<u32>) {
  let lid = lid3.x;
  let slice = wid3.x;
  if (slice >= d.n * d.groups) { return; }
  let batch = slice / d.groups;
  let group = slice % d.groups;
  let base = batch * d.c * d.h * d.w + group * d.group_size;
  var sum = 0.0;
  var sum_square = 0.0;
  for (var i = lid; i < d.group_size; i = i + 256u) {
    let value = load_input(base + i);
    sum = sum + value;
    sum_square = sum_square + value * value;
  }
  sums[lid] = sum;
  squares[lid] = sum_square;
  workgroupBarrier();
  var offset = 128u;
  loop {
    if (offset == 0u) { break; }
    if (lid < offset) {
      sums[lid] = sums[lid] + sums[lid + offset];
      squares[lid] = squares[lid] + squares[lid + offset];
    }
    workgroupBarrier();
    offset = offset / 2u;
  }
  let mean = sums[0] / f32(d.group_size);
  let variance = max(squares[0] / f32(d.group_size) - mean * mean, 0.0);
  let inv_std = inverseSqrt(variance + epsilon);
  let pairs = d.group_size / 2u;
  for (var pair = lid; pair < pairs; pair = pair + 256u) {
    let first_index = base + pair * 2u;
    let second_index = first_index + 1u;
    let first_channel = group * d.channels_group + (pair * 2u) / d.spatial;
    let second_channel = group * d.channels_group + (pair * 2u + 1u) / d.spatial;
    let first = activate((load_input(first_index) - mean) * inv_std
                         * load_weight(first_channel) + load_bias(first_channel));
    let second = activate((load_input(second_index) - mean) * inv_std
                          * load_weight(second_channel) + load_bias(second_channel));
    output[first_index / 2u] = pack2x16float(vec2<f32>(first, second));
  }
}")

(def embedding-wgsl
  "Embedding row gather. Token IDs are exactly represented non-negative f32
  integers; validation is performed by the portable CPU oracle/caller."
  "
struct Params { tokens: u32, rows: u32, dim: u32, pad: u32 }
@group(0) @binding(0) var<storage, read> indices: array<f32>;
@group(0) @binding(1) var<storage, read> weight: array<f32>;
@group(0) @binding(2) var<storage, read_write> output: array<f32>;
@group(0) @binding(3) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i >= p.tokens * p.dim) { return; }
  let token = i / p.dim;
  let feature = i % p.dim;
  let row = u32(indices[token]);
  if (row >= p.rows) { output[i] = 0.0; return; }
  output[i] = weight[row * p.dim + feature];
}")

(def embedding-f16-wgsl
  "Packed f16 embedding row gather with f32 token indices."
  "
struct Params { tokens: u32, rows: u32, dim: u32, pad: u32 }
@group(0) @binding(0) var<storage, read> indices: array<f32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read_write> output: array<u32>;
@group(0) @binding(3) var<uniform> p: Params;
fn load_f16(i: u32) -> f32 {
  let pair = unpack2x16float(weight[i / 2u]);
  return select(pair.x, pair.y, i % 2u == 1u);
}
fn gather(i: u32) -> f32 {
  let token = i / p.dim;
  let feature = i % p.dim;
  let row = u32(indices[token]);
  if (row >= p.rows) { return 0.0; }
  return load_f16(row * p.dim + feature);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let word = gid.x;
  let base = word * 2u;
  let n = p.tokens * p.dim;
  if (base >= n) { return; }
  let second = select(gather(base + 1u), 0.0, base + 1u >= n);
  output[word] = pack2x16float(vec2<f32>(gather(base), second));
}")

(def embedding-backward-f16-wgsl
  "Conflict-free embedding-table VJP. One invocation owns one f32 weight
  gradient and gathers every occurrence, so repeated token IDs sum
  deterministically without atomics."
  "
struct Params { tokens: u32, rows: u32, dim: u32, total: u32 }
@group(0) @binding(0) var<storage, read> indices: array<f32>;
@group(0) @binding(1) var<storage, read> grad_output: array<u32>;
@group(0) @binding(2) var<storage, read_write> grad_weight: array<f32>;
@group(0) @binding(3) var<uniform> p: Params;
fn load_grad(index: u32) -> f32 {
  let pair = unpack2x16float(grad_output[index / 2u]);
  return select(pair.x, pair.y, (index & 1u) == 1u);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let weight_index = gid.x;
  if (weight_index >= p.total) { return; }
  let row = weight_index / p.dim;
  let feature = weight_index % p.dim;
  var sum = 0.0;
  for (var token = 0u; token < p.tokens; token += 1u) {
    if (u32(indices[token]) == row) {
      sum += load_grad(token * p.dim + feature);
    }
  }
  grad_weight[weight_index] = sum;
}")

(def rms-norm-wgsl
  "One workgroup per row RMSNorm. Each lane accumulates strided features and
  the workgroup reduction avoids recomputing row statistics per output."
  "
struct Params { rows: u32, dim: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read> weight: array<f32>;
@group(0) @binding(2) var<storage, read_write> output: array<f32>;
@group(0) @binding(3) var<uniform> p: Params;
@group(0) @binding(4) var<uniform> eps: f32;
var<workgroup> sums: array<f32, 64>;
@compute @workgroup_size(64)
fn main(@builtin(workgroup_id) wid: vec3<u32>,
        @builtin(local_invocation_id) lid: vec3<u32>) {
  let row = wid.x;
  let lane = lid.x;
  if (row >= p.rows) { return; }
  var sum = 0.0;
  for (var f = lane; f < p.dim; f += 64u) {
    let v = input[row * p.dim + f]; sum += v * v;
  }
  sums[lane] = sum;
  workgroupBarrier();
  var stride = 32u;
  loop {
    if (lane < stride) { sums[lane] += sums[lane + stride]; }
    workgroupBarrier();
    if (stride == 1u) { break; }
    stride /= 2u;
  }
  let inv_rms = inverseSqrt(sums[0] / f32(p.dim) + eps);
  for (var f = lane; f < p.dim; f += 64u) {
    output[row * p.dim + f] = input[row * p.dim + f] * inv_rms * weight[f];
  }
}")

(def rms-norm-f16-wgsl
  "Packed f16 RMSNorm. Requires an even final dimension so packed words never
  straddle rows; accumulation remains f32 like mixed-precision frameworks."
  "
struct Params { rows: u32, dim: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read_write> output: array<u32>;
@group(0) @binding(3) var<uniform> p: Params;
@group(0) @binding(4) var<uniform> eps: f32;
var<workgroup> sums: array<f32, 64>;
@compute @workgroup_size(64)
fn main(@builtin(workgroup_id) wid: vec3<u32>,
        @builtin(local_invocation_id) lid: vec3<u32>) {
  let row = wid.x; let lane = lid.x; let words = p.dim / 2u;
  if (row >= p.rows) { return; }
  var sum = 0.0;
  for (var w = lane; w < words; w += 64u) {
    let v = unpack2x16float(input[row * words + w]); sum += dot(v, v);
  }
  sums[lane] = sum;
  workgroupBarrier();
  var stride = 32u;
  loop {
    if (lane < stride) { sums[lane] += sums[lane + stride]; }
    workgroupBarrier();
    if (stride == 1u) { break; }
    stride /= 2u;
  }
  let inv_rms = inverseSqrt(sums[0] / f32(p.dim) + eps);
  for (var w = lane; w < words; w += 64u) {
    let v = unpack2x16float(input[row * words + w]);
    let gamma = unpack2x16float(weight[w]);
    output[row * words + w] = pack2x16float(v * inv_rms * gamma);
  }
}")

(def rms-norm-backward-stats-f16-wgsl
  "Per-row f32 RMSNorm backward statistics for packed-f16 tensors."
  "
struct Params { rows: u32, dim: u32, total: u32, pad: u32 }
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read> grad: array<u32>;
@group(0) @binding(3) var<storage, read_write> stats: array<f32>;
@group(0) @binding(4) var<uniform> p: Params;
@group(0) @binding(5) var<uniform> eps: f32;
var<workgroup> square_sums: array<f32, 64>;
var<workgroup> dot_sums: array<f32, 64>;
@compute @workgroup_size(64)
fn main(@builtin(workgroup_id) wid: vec3<u32>,
        @builtin(local_invocation_id) lid: vec3<u32>) {
  let row = wid.x; let lane = lid.x; let words = p.dim / 2u;
  if (row >= p.rows) { return; }
  var square_sum = 0.0; var dot_sum = 0.0;
  for (var word = lane; word < words; word += 64u) {
    let x = unpack2x16float(input[row * words + word]);
    let g = unpack2x16float(grad[row * words + word]);
    let w = unpack2x16float(weight[word]);
    square_sum += dot(x, x); dot_sum += dot(g * w, x);
  }
  square_sums[lane] = square_sum; dot_sums[lane] = dot_sum;
  workgroupBarrier();
  var stride = 32u;
  loop {
    if (lane < stride) {
      square_sums[lane] += square_sums[lane + stride];
      dot_sums[lane] += dot_sums[lane + stride];
    }
    workgroupBarrier();
    if (stride == 1u) { break; }
    stride /= 2u;
  }
  if (lane == 0u) {
    let inv = inverseSqrt(square_sums[0] / f32(p.dim) + eps);
    stats[row * 2u] = inv;
    stats[row * 2u + 1u] = dot_sums[0] * inv * inv * inv / f32(p.dim);
  }
}")

(def rms-norm-backward-f16-wgsl
  "Packed-f16 input VJP and conflict-free f32 affine VJP for RMSNorm."
  "
struct Params { rows: u32, dim: u32, total: u32, pad: u32 }
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read> grad: array<u32>;
@group(0) @binding(3) var<storage, read> stats: array<f32>;
@group(0) @binding(4) var<storage, read_write> grad_input: array<u32>;
@group(0) @binding(5) var<storage, read_write> grad_weight: array<f32>;
@group(0) @binding(6) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x;
  let packed_count = (p.total + 1u) / 2u;
  if (index < packed_count) {
    let base = index * 2u;
    let x = unpack2x16float(input[index]);
    let g = unpack2x16float(grad[index]);
    let features = vec2<u32>(base % p.dim, (base + 1u) % p.dim);
    let rows = vec2<u32>(base / p.dim, (base + 1u) / p.dim);
    let w0 = unpack2x16float(weight[features.x / 2u]);
    let w1 = unpack2x16float(weight[features.y / 2u]);
    let gamma = vec2<f32>(select(w0.x, w0.y, (features.x & 1u) == 1u),
                          select(w1.x, w1.y, (features.y & 1u) == 1u));
    let inv = vec2<f32>(stats[rows.x * 2u], stats[rows.y * 2u]);
    let correction = vec2<f32>(stats[rows.x * 2u + 1u],
                                stats[rows.y * 2u + 1u]);
    var dx = g * gamma * inv - x * correction;
    if (base + 1u >= p.total) { dx.y = 0.0; }
    grad_input[index] = pack2x16float(dx);
  }
  if (index < p.dim) {
    var sum = 0.0;
    for (var row = 0u; row < p.rows; row += 1u) {
      let element = row * p.dim + index;
      let xv = unpack2x16float(input[element / 2u]);
      let gv = unpack2x16float(grad[element / 2u]);
      let x = select(xv.x, xv.y, (element & 1u) == 1u);
      let g = select(gv.x, gv.y, (element & 1u) == 1u);
      sum += g * x * stats[row * 2u];
    }
    grad_weight[index] = sum;
  }
}")

(def rotary-embedding-wgsl
  "Llama half-rotation RoPE over `[batch, sequence, embedding]`."
  "
struct Params { batch: u32, sequence: u32, embed: u32, heads: u32,
                head_dim: u32, position_offset: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
@group(0) @binding(3) var<uniform> theta: f32;
@group(0) @binding(4) var<uniform> direction: f32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; let total = p.batch * p.sequence * p.embed;
  if (i >= total) { return; }
  let feature = i % p.embed;
  let local = feature % p.head_dim;
  let half = p.head_dim / 2u;
  let pair_local = select(local + half, local - half, local >= half);
  let pair = i - local + pair_local;
  let frequency_index = select(local, local - half, local >= half);
  let position = (i / p.embed) % p.sequence + p.position_offset;
  let angle = direction * f32(position) *
              pow(theta, -2.0 * f32(frequency_index) / f32(p.head_dim));
  let c = cos(angle); let s = sin(angle);
  output[i] = select(input[i] * c - input[pair] * s,
                     input[i] * c + input[pair] * s, local >= half);
}")

(def rotary-embedding-f16-wgsl
  "Packed f16 RoPE with f32 trigonometric evaluation."
  "
struct Params { batch: u32, sequence: u32, embed: u32, heads: u32,
                head_dim: u32, position_offset: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read_write> output: array<u32>;
@group(0) @binding(2) var<uniform> p: Params;
@group(0) @binding(3) var<uniform> theta: f32;
@group(0) @binding(4) var<uniform> direction: f32;
fn load(i: u32) -> f32 {
  let pair = unpack2x16float(input[i / 2u]);
  return select(pair.x, pair.y, i % 2u == 1u);
}
fn rotate(i: u32) -> f32 {
  let feature = i % p.embed; let local = feature % p.head_dim;
  let half = p.head_dim / 2u;
  let pair_local = select(local + half, local - half, local >= half);
  let pair = i - local + pair_local;
  let frequency_index = select(local, local - half, local >= half);
  let position = (i / p.embed) % p.sequence + p.position_offset;
  let angle = direction * f32(position) *
              pow(theta, -2.0 * f32(frequency_index) / f32(p.head_dim));
  let c = cos(angle); let s = sin(angle);
  return select(load(i) * c - load(pair) * s,
                load(i) * c + load(pair) * s, local >= half);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let word = gid.x; let base = word * 2u;
  let total = p.batch * p.sequence * p.embed;
  if (base >= total) { return; }
  let second = select(rotate(base + 1u), 0.0, base + 1u >= total);
  output[word] = pack2x16float(vec2<f32>(rotate(base), second));
}")

(def copy-into-wgsl
  "Bounded f32 device-buffer copy into a preallocated destination."
  "
struct Params { offset: u32, n: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read_write> destination: array<f32>;
@group(0) @binding(1) var<storage, read> source: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i < p.n) { destination[p.offset + i] = source[i]; }
}")

(def copy-into-f16-wgsl
  "Packed f16 device-buffer copy; caller guarantees even offset/count."
  "
struct Params { word_offset: u32, words: u32, pad0: u32, pad1: u32 }
@group(0) @binding(0) var<storage, read_write> destination: array<u32>;
@group(0) @binding(1) var<storage, read> source: array<u32>;
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i < p.words) { destination[p.word_offset + i] = source[i]; }
}")

(def q4-k-matmul-wgsl
  "Dense f32 activations times GGML Q4_K weights, decoded directly from packed
  u32 storage. One invocation computes one output element with f32 accumulation."
  "
struct Params { m: u32, k: u32, n: u32, blocks_per_row: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read_write> output: array<f32>;
@group(0) @binding(3) var<uniform> p: Params;
fn byte_at(offset: u32) -> u32 {
  let word = weight[offset / 4u];
  return (word >> ((offset % 4u) * 8u)) & 255u;
}
fn scale_min(block: u32, index: u32) -> vec2<u32> {
  let base = block + 4u;
  if (index < 4u) {
    return vec2<u32>(byte_at(base + index) & 63u,
                     byte_at(base + index + 4u) & 63u);
  }
  return vec2<u32>((byte_at(base + index + 4u) & 15u) |
                   ((byte_at(base + index - 4u) >> 6u) << 4u),
                   (byte_at(base + index + 4u) >> 4u) |
                   ((byte_at(base + index) >> 6u) << 4u));
}
fn value_at(row: u32, column: u32) -> f32 {
  let block_index = row * p.blocks_per_row + column / 256u;
  let block = block_index * 144u;
  let dm = unpack2x16float(weight[block / 4u]);
  let local = column % 256u; let subblock = local / 32u;
  let sm = scale_min(block, subblock);
  let packed = byte_at(block + 16u + (subblock / 2u) * 32u + local % 32u);
  let quant = select(packed & 15u, packed >> 4u, subblock % 2u == 1u);
  return dm.x * f32(sm.x) * f32(quant) - dm.y * f32(sm.y);
}
var<workgroup> partial: array<f32, 256>;
@compute @workgroup_size(64)
fn main(@builtin(workgroup_id) wid: vec3<u32>,
        @builtin(local_invocation_id) lid3: vec3<u32>) {
  let tile = wid.x / p.n; let column = wid.x % p.n;
  let row = tile * 4u; let lid = lid3.x;
  var sums = vec4<f32>(0.0);
  for (var inner: u32 = lid; inner < p.k; inner = inner + 64u) {
    let weight_value = value_at(column, inner);
    if (row + 0u < p.m) { sums.x = sums.x + input[(row + 0u) * p.k + inner] * weight_value; }
    if (row + 1u < p.m) { sums.y = sums.y + input[(row + 1u) * p.k + inner] * weight_value; }
    if (row + 2u < p.m) { sums.z = sums.z + input[(row + 2u) * p.k + inner] * weight_value; }
    if (row + 3u < p.m) { sums.w = sums.w + input[(row + 3u) * p.k + inner] * weight_value; }
  }
  partial[lid] = sums.x; partial[64u + lid] = sums.y;
  partial[128u + lid] = sums.z; partial[192u + lid] = sums.w;
  workgroupBarrier();
  for (var stride: u32 = 32u; stride > 0u; stride = stride / 2u) {
    if (lid < stride) {
      partial[lid] = partial[lid] + partial[lid + stride];
      partial[64u + lid] = partial[64u + lid] + partial[64u + lid + stride];
      partial[128u + lid] = partial[128u + lid] + partial[128u + lid + stride];
      partial[192u + lid] = partial[192u + lid] + partial[192u + lid + stride];
    }
    workgroupBarrier();
  }
  if (lid == 0u) {
    if (row + 0u < p.m) { output[(row + 0u) * p.n + column] = partial[0]; }
    if (row + 1u < p.m) { output[(row + 1u) * p.n + column] = partial[64]; }
    if (row + 2u < p.m) { output[(row + 2u) * p.n + column] = partial[128]; }
    if (row + 3u < p.m) { output[(row + 3u) * p.n + column] = partial[192]; }
  }
}")

(def q6-k-matmul-wgsl
  "Dense f32 activations times packed GGML Q6_K weights."
  "
struct Params { m: u32, k: u32, n: u32, blocks_per_row: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read_write> output: array<f32>;
@group(0) @binding(3) var<uniform> p: Params;
fn byte_at(offset: u32) -> u32 {
  return (weight[offset / 4u] >> ((offset % 4u) * 8u)) & 255u;
}
fn signed_byte(offset: u32) -> i32 {
  let raw = byte_at(offset);
  return select(i32(raw), i32(raw) - 256, raw >= 128u);
}
fn value_at(row: u32, column: u32) -> f32 {
  let block_index = row * p.blocks_per_row + column / 256u;
  let block = block_index * 210u; let local = column % 256u;
  let half = local / 128u; let position = local % 128u;
  let group = position / 32u; let lane = position % 32u;
  let low = byte_at(block + half * 64u + lane + select(0u, 32u, group % 2u == 1u));
  let high = byte_at(block + 128u + half * 32u + lane);
  let low_bits = select(low & 15u, low >> 4u, group >= 2u);
  let high_bits = (high >> (group * 2u)) & 3u;
  let quant = i32(low_bits | (high_bits << 4u)) - 32;
  let scale = signed_byte(block + 192u + half * 8u + lane / 16u + group * 2u);
  let d_bits = byte_at(block + 208u) | (byte_at(block + 209u) << 8u);
  let d = unpack2x16float(d_bits).x;
  return d * f32(scale * quant);
}
var<workgroup> partial: array<f32, 256>;
@compute @workgroup_size(64)
fn main(@builtin(workgroup_id) wid: vec3<u32>,
        @builtin(local_invocation_id) lid3: vec3<u32>) {
  let tile = wid.x / p.n; let column = wid.x % p.n;
  let row = tile * 4u; let lid = lid3.x;
  var sums = vec4<f32>(0.0);
  for (var inner: u32 = lid; inner < p.k; inner = inner + 64u) {
    let weight_value = value_at(column, inner);
    if (row + 0u < p.m) { sums.x = sums.x + input[(row + 0u) * p.k + inner] * weight_value; }
    if (row + 1u < p.m) { sums.y = sums.y + input[(row + 1u) * p.k + inner] * weight_value; }
    if (row + 2u < p.m) { sums.z = sums.z + input[(row + 2u) * p.k + inner] * weight_value; }
    if (row + 3u < p.m) { sums.w = sums.w + input[(row + 3u) * p.k + inner] * weight_value; }
  }
  partial[lid] = sums.x; partial[64u + lid] = sums.y;
  partial[128u + lid] = sums.z; partial[192u + lid] = sums.w;
  workgroupBarrier();
  for (var stride: u32 = 32u; stride > 0u; stride = stride / 2u) {
    if (lid < stride) {
      partial[lid] = partial[lid] + partial[lid + stride];
      partial[64u + lid] = partial[64u + lid] + partial[64u + lid + stride];
      partial[128u + lid] = partial[128u + lid] + partial[128u + lid + stride];
      partial[192u + lid] = partial[192u + lid] + partial[192u + lid + stride];
    }
    workgroupBarrier();
  }
  if (lid == 0u) {
    if (row + 0u < p.m) { output[(row + 0u) * p.n + column] = partial[0]; }
    if (row + 1u < p.m) { output[(row + 1u) * p.n + column] = partial[64]; }
    if (row + 2u < p.m) { output[(row + 2u) * p.n + column] = partial[128]; }
    if (row + 3u < p.m) { output[(row + 3u) * p.n + column] = partial[192]; }
  }
}")

(def q8-0-matmul-wgsl
  "Dense f32 activations times original packed GGML Q8_0 blocks."
  "
struct Params { m: u32, k: u32, n: u32, blocks_per_row: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read> weight: array<u32>;
@group(0) @binding(2) var<storage, read_write> output: array<f32>;
@group(0) @binding(3) var<uniform> p: Params;
fn byte_at(offset: u32) -> u32 {
  return (weight[offset / 4u] >> ((offset % 4u) * 8u)) & 255u;
}
fn value_at(row: u32, column: u32) -> f32 {
  let block_index = row * p.blocks_per_row + column / 32u;
  let block = block_index * 34u;
  let d_bits = byte_at(block) | (byte_at(block + 1u) << 8u);
  let d = unpack2x16float(d_bits).x;
  let raw = byte_at(block + 2u + column % 32u);
  let quant = select(i32(raw), i32(raw) - 256, raw >= 128u);
  return d * f32(quant);
}
var<workgroup> partial: array<f32, 256>;
@compute @workgroup_size(64)
fn main(@builtin(workgroup_id) wid: vec3<u32>,
        @builtin(local_invocation_id) lid3: vec3<u32>) {
  let tile = wid.x / p.n; let column = wid.x % p.n;
  let row = tile * 4u; let lid = lid3.x;
  var sums = vec4<f32>(0.0);
  for (var inner: u32 = lid; inner < p.k; inner = inner + 64u) {
    let weight_value = value_at(column, inner);
    if (row + 0u < p.m) { sums.x = sums.x + input[(row + 0u) * p.k + inner] * weight_value; }
    if (row + 1u < p.m) { sums.y = sums.y + input[(row + 1u) * p.k + inner] * weight_value; }
    if (row + 2u < p.m) { sums.z = sums.z + input[(row + 2u) * p.k + inner] * weight_value; }
    if (row + 3u < p.m) { sums.w = sums.w + input[(row + 3u) * p.k + inner] * weight_value; }
  }
  partial[lid] = sums.x; partial[64u + lid] = sums.y;
  partial[128u + lid] = sums.z; partial[192u + lid] = sums.w;
  workgroupBarrier();
  for (var stride: u32 = 32u; stride > 0u; stride = stride / 2u) {
    if (lid < stride) {
      partial[lid] = partial[lid] + partial[lid + stride];
      partial[64u + lid] = partial[64u + lid] + partial[64u + lid + stride];
      partial[128u + lid] = partial[128u + lid] + partial[128u + lid + stride];
      partial[192u + lid] = partial[192u + lid] + partial[192u + lid + stride];
    }
    workgroupBarrier();
  }
  if (lid == 0u) {
    if (row + 0u < p.m) { output[(row + 0u) * p.n + column] = partial[0]; }
    if (row + 1u < p.m) { output[(row + 1u) * p.n + column] = partial[64]; }
    if (row + 2u < p.m) { output[(row + 2u) * p.n + column] = partial[128]; }
    if (row + 3u < p.m) { output[(row + 3u) * p.n + column] = partial[192]; }
  }
}")

(def q5-0-matmul-wgsl
  "Dense f32 activations times original packed GGML Q5_0 blocks."
  (str/replace
   q8-0-matmul-wgsl
   "fn value_at(row: u32, column: u32) -> f32 {
  let block_index = row * p.blocks_per_row + column / 32u;
  let block = block_index * 34u;
  let d_bits = byte_at(block) | (byte_at(block + 1u) << 8u);
  let d = unpack2x16float(d_bits).x;
  let raw = byte_at(block + 2u + column % 32u);
  let quant = select(i32(raw), i32(raw) - 256, raw >= 128u);
  return d * f32(quant);
}"
   "fn value_at(row: u32, column: u32) -> f32 {
  let linear = row * p.k + column;
  let block = (linear / 32u) * 22u; let local = linear % 32u;
  let d_bits = byte_at(block) | (byte_at(block + 1u) << 8u);
  let packed = byte_at(block + 6u + local % 16u);
  let low = select(packed & 15u, packed >> 4u, local >= 16u);
  let high = (byte_at(block + 2u + local / 8u) >> (local % 8u)) & 1u;
  let quant = i32(low | (high << 4u)) - 16;
  return unpack2x16float(d_bits).x * f32(quant);
}"))

(defn- packed-embedding-wgsl [value-function]
  (str
   "struct Params { rows: u32, dim: u32, count: u32, blocks_per_row: u32,
                    total: u32, pad0: u32, pad1: u32, pad2: u32 }
@group(0) @binding(0) var<storage, read> indices: array<f32>;
@group(0) @binding(1) var<storage, read> table: array<u32>;
@group(0) @binding(2) var<storage, read_write> output: array<f32>;
@group(0) @binding(3) var<uniform> p: Params;
fn byte_at(offset: u32) -> u32 {
  return (table[offset / 4u] >> ((offset % 4u) * 8u)) & 255u;
}
" value-function "
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x;
  if (index >= p.total) { return; }
  let position = index / p.dim; let feature = index % p.dim;
  let raw_row = indices[position];
  if (raw_row < 0.0 || raw_row >= f32(p.rows) || raw_row != floor(raw_row)) {
    output[index] = 0.0; return;
  }
  output[index] = value_at(u32(raw_row), feature);
}"))

(def q4-k-embedding-wgsl
  (packed-embedding-wgsl
   "fn scale_min(block: u32, index: u32) -> vec2<u32> {
  let base = block + 4u;
  if (index < 4u) {
    return vec2<u32>(byte_at(base + index) & 63u,
                     byte_at(base + index + 4u) & 63u);
  }
  return vec2<u32>((byte_at(base + index + 4u) & 15u) |
                   ((byte_at(base + index - 4u) >> 6u) << 4u),
                   (byte_at(base + index + 4u) >> 4u) |
                   ((byte_at(base + index) >> 6u) << 4u));
}
fn value_at(row: u32, column: u32) -> f32 {
  let block = (row * p.blocks_per_row + column / 256u) * 144u;
  let dm = unpack2x16float(table[block / 4u]);
  let local = column % 256u; let subblock = local / 32u;
  let sm = scale_min(block, subblock);
  let packed = byte_at(block + 16u + (subblock / 2u) * 32u + local % 32u);
  let quant = select(packed & 15u, packed >> 4u, subblock % 2u == 1u);
  return dm.x * f32(sm.x) * f32(quant) - dm.y * f32(sm.y);
}"))

(def q6-k-embedding-wgsl
  (packed-embedding-wgsl
   "fn signed_byte(offset: u32) -> i32 {
  let raw = byte_at(offset);
  return select(i32(raw), i32(raw) - 256, raw >= 128u);
}
fn value_at(row: u32, column: u32) -> f32 {
  let block = (row * p.blocks_per_row + column / 256u) * 210u;
  let local = column % 256u; let half = local / 128u;
  let position = local % 128u; let group = position / 32u;
  let lane = position % 32u;
  let low = byte_at(block + half * 64u + lane + select(0u, 32u, group % 2u == 1u));
  let high = byte_at(block + 128u + half * 32u + lane);
  let low_bits = select(low & 15u, low >> 4u, group >= 2u);
  let quant = i32(low_bits | (((high >> (group * 2u)) & 3u) << 4u)) - 32;
  let scale = signed_byte(block + 192u + half * 8u + lane / 16u + group * 2u);
  let d_bits = byte_at(block + 208u) | (byte_at(block + 209u) << 8u);
  return unpack2x16float(d_bits).x * f32(scale * quant);
}"))

(def q8-0-embedding-wgsl
  (packed-embedding-wgsl
   "fn value_at(row: u32, column: u32) -> f32 {
  let block = (row * p.blocks_per_row + column / 32u) * 34u;
  let d_bits = byte_at(block) | (byte_at(block + 1u) << 8u);
  let raw = byte_at(block + 2u + column % 32u);
  let quant = select(i32(raw), i32(raw) - 256, raw >= 128u);
  return unpack2x16float(d_bits).x * f32(quant);
}"))

(def q5-0-embedding-wgsl
  (packed-embedding-wgsl
   "fn value_at(row: u32, column: u32) -> f32 {
  let linear = row * p.dim + column;
  let block = (linear / 32u) * 22u; let local = linear % 32u;
  let d_bits = byte_at(block) | (byte_at(block + 1u) << 8u);
  let packed = byte_at(block + 6u + local % 16u);
  let low = select(packed & 15u, packed >> 4u, local >= 16u);
  let high = (byte_at(block + 2u + local / 8u) >> (local % 8u)) & 1u;
  let quant = i32(low | (high << 4u)) - 16;
  return unpack2x16float(d_bits).x * f32(quant);
}"))

(def rgb-image-to-nchw-wgsl
  "Fused NHWC→NCHW layout and [0,1]→[-1,1] range conversion."
  "
struct Params { height: u32, width: u32, total: u32, pad0: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x; if (index >= p.total) { return; }
  let plane = p.height * p.width; let spatial = index % plane;
  let channel = (index / plane) % 3u; let batch = index / (3u * plane);
  let source = (batch * plane + spatial) * 3u + channel;
  output[index] = 2.0 * input[source] - 1.0;
}")

(def nchw-to-rgb-image-wgsl
  "Fused NCHW→NHWC layout and [-1,1]→clamped [0,1] conversion."
  "
struct Params { height: u32, width: u32, total: u32, pad0: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x; if (index >= p.total) { return; }
  let plane = p.height * p.width; let channel = index % 3u;
  let spatial = (index / 3u) % plane; let batch = index / (3u * plane);
  let source = (batch * 3u + channel) * plane + spatial;
  output[index] = clamp(0.5 * (input[source] + 1.0), 0.0, 1.0);
}")

(def nchw-to-rgb-image-f16-wgsl
  "Read packed F16 NCHW and emit f32 NHWC RGB in one dispatch."
  "
struct Params { height: u32, width: u32, total: u32, pad0: u32 }
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
fn load(index: u32) -> f32 {
  let pair = unpack2x16float(input[index / 2u]);
  return select(pair.x, pair.y, index % 2u == 1u);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>,
        @builtin(num_workgroups) grid: vec3<u32>) {
  let index = gid.x + gid.y * grid.x * 64u;
  if (index >= p.total) { return; }
  let plane = p.height * p.width; let channel = index % 3u;
  let spatial = (index / 3u) % plane; let batch = index / (3u * plane);
  let source = (batch * 3u + channel) * plane + spatial;
  output[index] = clamp(0.5 * (load(source) + 1.0), 0.0, 1.0);
}")

(def scale-f16-wgsl
  "Immutable packed-F16 scalar multiplication with f32 evaluation."
  "
struct Params { count: u32, pad0: u32, pad1: u32, pad2: u32 }
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read_write> output: array<u32>;
@group(0) @binding(2) var<uniform> p: Params;
@group(0) @binding(3) var<uniform> alpha: f32;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>,
        @builtin(num_workgroups) grid: vec3<u32>) {
  let word = gid.x + gid.y * grid.x * 64u;
  let first = word * 2u; if (first >= p.count) { return; }
  var values = unpack2x16float(input[word]) * alpha;
  if (first + 1u >= p.count) { values.y = 0.0; }
  output[word] = pack2x16float(values);
}")

(def f16-to-f32-wgsl
  "Expand packed IEEE binary16 storage into one f32 per output element."
  "
struct Params { count: u32, pad0: u32, pad1: u32, pad2: u32 }
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= p.count) { return; }
  let pair = unpack2x16float(input[i / 2u]);
  output[i] = select(pair.x, pair.y, (i & 1u) == 1u);
}")

(def f32-to-f16-wgsl
  "Pack pairs of f32 values into physical IEEE binary16 storage."
  "
struct Params { count: u32, pad0: u32, pad1: u32, pad2: u32 }
@group(0) @binding(0) var<storage, read> input: array<f32>;
@group(0) @binding(1) var<storage, read_write> output: array<u32>;
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let word = gid.x; let first = word * 2u;
  if (first >= p.count) { return; }
  let second = select(0.0, input[first + 1u], first + 1u < p.count);
  output[word] = pack2x16float(vec2<f32>(input[first], second));
}")

(def bf16-to-f32-wgsl
  "Expand packed bfloat16 storage into one f32 per output element."
  "
struct Params { count: u32, pad0: u32, pad1: u32, pad2: u32 }
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read_write> output: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= p.count) { return; }
  let word = input[i / 2u];
  let bits = select((word & 0xffffu) << 16u, word & 0xffff0000u,
                    (i & 1u) == 1u);
  output[i] = bitcast<f32>(bits);
}")

(def paged-kv-write-wgsl
  "Write one projected K/V token into a physical paged-cache slot."
  "
struct Params {
  block: u32, offset: u32, block_size: u32, kv_width: u32,
}
@group(0) @binding(0) var<storage, read> key: array<f32>;
@group(0) @binding(1) var<storage, read> value: array<f32>;
@group(0) @binding(2) var<storage, read_write> key_pool: array<f32>;
@group(0) @binding(3) var<storage, read_write> value_pool: array<f32>;
@group(0) @binding(4) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= p.kv_width) { return; }
  let destination = ((p.block * p.block_size + p.offset) * p.kv_width) + i;
  key_pool[destination] = key[i];
  value_pool[destination] = value[i];
}")

(def paged-kv-copy-block-wgsl
  "Copy the used prefix of one physical K/V block for prefix COW."
  "
struct Params {
  source: u32, destination: u32, tokens: u32, block_size: u32,
  kv_width: u32, total: u32, pad0: u32, pad1: u32,
}
@group(0) @binding(0) var<storage, read_write> key_pool: array<f32>;
@group(0) @binding(1) var<storage, read_write> value_pool: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x; if (i >= p.total) { return; }
  let source_index = p.source * p.block_size * p.kv_width + i;
  let destination_index = p.destination * p.block_size * p.kv_width + i;
  key_pool[destination_index] = key_pool[source_index];
  value_pool[destination_index] = value_pool[source_index];
}")

(def paged-gqa-attention-wgsl
  "One-token grouped-query attention reading K/V through a physical block table."
  "
struct Params {
  length: u32, block_size: u32, kv_width: u32, heads: u32,
  kv_heads: u32, head_dim: u32, model: u32, pad0: u32,
}
@group(0) @binding(0) var<storage, read> query: array<f32>;
@group(0) @binding(1) var<storage, read> key_pool: array<f32>;
@group(0) @binding(2) var<storage, read> value_pool: array<f32>;
// Block IDs are numerically exact f32 values, allowing the ordinary NDArray
// upload path while the kernel casts each entry to an address index.
@group(0) @binding(3) var<storage, read> block_table: array<f32>;
@group(0) @binding(4) var<storage, read_write> output: array<f32>;
@group(0) @binding(5) var<uniform> p: Params;
fn pool_index(token: u32, component: u32) -> u32 {
  let logical_block = token / p.block_size;
  let offset = token % p.block_size;
  let physical_block = u32(block_table[logical_block]);
  return ((physical_block * p.block_size + offset) * p.kv_width) + component;
}
fn score(head: u32, token: u32) -> f32 {
  let kv_head = head / (p.heads / p.kv_heads);
  var dot: f32 = 0.0;
  for (var d: u32 = 0u; d < p.head_dim; d = d + 1u) {
    dot = dot + query[head * p.head_dim + d] *
                key_pool[pool_index(token, kv_head * p.head_dim + d)];
  }
  return dot * inverseSqrt(f32(p.head_dim));
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let component = gid.x; if (component >= p.model) { return; }
  let head = component / p.head_dim;
  let local = component % p.head_dim;
  let kv_head = head / (p.heads / p.kv_heads);
  var maximum: f32 = -3.402823e38;
  for (var token: u32 = 0u; token < p.length; token = token + 1u) {
    maximum = max(maximum, score(head, token));
  }
  var denominator: f32 = 0.0;
  var weighted: f32 = 0.0;
  for (var token: u32 = 0u; token < p.length; token = token + 1u) {
    let weight = exp(score(head, token) - maximum);
    denominator = denominator + weight;
    weighted = weighted + weight *
      value_pool[pool_index(token, kv_head * p.head_dim + local)];
  }
  output[component] = weighted / denominator;
}")

(def paged-gqa-attention-batch-wgsl
  "Batched one-token GQA with independent lengths and physical block tables."
  "
struct Params {
  batch: u32, max_blocks: u32, block_size: u32, kv_width: u32,
  heads: u32, kv_heads: u32, head_dim: u32, model: u32,
  total: u32, pad0: u32, pad1: u32, pad2: u32,
}
@group(0) @binding(0) var<storage, read> query: array<f32>;
@group(0) @binding(1) var<storage, read> key_pool: array<f32>;
@group(0) @binding(2) var<storage, read> value_pool: array<f32>;
@group(0) @binding(3) var<storage, read> block_tables: array<f32>;
@group(0) @binding(4) var<storage, read> lengths: array<f32>;
@group(0) @binding(5) var<storage, read_write> output: array<f32>;
@group(0) @binding(6) var<uniform> p: Params;
fn pool_index(batch: u32, token: u32, component: u32) -> u32 {
  let logical_block = token / p.block_size;
  let offset = token % p.block_size;
  let physical = u32(block_tables[batch * p.max_blocks + logical_block]);
  return ((physical * p.block_size + offset) * p.kv_width) + component;
}
fn score(batch: u32, head: u32, token: u32) -> f32 {
  let kv_head = head / (p.heads / p.kv_heads);
  var dot: f32 = 0.0;
  for (var d: u32 = 0u; d < p.head_dim; d = d + 1u) {
    dot = dot + query[batch * p.model + head * p.head_dim + d] *
                key_pool[pool_index(batch, token,
                                    kv_head * p.head_dim + d)];
  }
  return dot * inverseSqrt(f32(p.head_dim));
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x; if (index >= p.total) { return; }
  let batch = index / p.model;
  let component = index % p.model;
  let head = component / p.head_dim;
  let local = component % p.head_dim;
  let kv_head = head / (p.heads / p.kv_heads);
  let length = u32(lengths[batch]);
  var maximum: f32 = -3.402823e38;
  for (var token: u32 = 0u; token < length; token = token + 1u) {
    maximum = max(maximum, score(batch, head, token));
  }
  var denominator: f32 = 0.0;
  var weighted: f32 = 0.0;
  for (var token: u32 = 0u; token < length; token = token + 1u) {
    let weight = exp(score(batch, head, token) - maximum);
    denominator = denominator + weight;
    weighted = weighted + weight *
      value_pool[pool_index(batch, token, kv_head * p.head_dim + local)];
  }
  output[index] = weighted / denominator;
}")

(def shaders
  "All compute kernels by op keyword — the menu a WgslBackend compiles on init.
  Verified on Apple M4 Metal (wgpu via WebGPU): the full IBackend contract
  (axpy/scal/dot/nrm2/ewise/reduce/gemv/gemm/spmv) reproduces the CPU reference —
  see verify/metal_contract.js."
  {:axpy   axpy-wgsl
   :scal   scal-wgsl
   :ewise  ewise-wgsl
   :ewise1 ewise1-wgsl
   :reduce reduce-wgsl
   :argmax-rows argmax-rows-wgsl
   :repetition-penalty-row repetition-penalty-row-wgsl
   :top-k-row top-k-row-wgsl
   :sample-candidates sample-candidates-wgsl
   :sample-softmax-row sample-softmax-row-wgsl
   :nucleus-sort-init nucleus-sort-init-wgsl
   :nucleus-bitonic-step nucleus-bitonic-step-wgsl
   :sample-ranked-nucleus sample-ranked-nucleus-wgsl
   :speculative-rejection-row speculative-rejection-row-wgsl
   :gemv   gemv-wgsl
   :gemm   gemm-tiled-wgsl
   :conv2d-nchw conv2d-nchw-wgsl
   :conv2d-nchw-oc4 conv2d-nchw-oc4-wgsl
   :group-norm-nchw group-norm-nchw-wgsl
   :embedding embedding-wgsl
   :rms-norm rms-norm-wgsl
   :rotary-embedding rotary-embedding-wgsl
   :copy-into copy-into-wgsl
   :q5-0-matmul q5-0-matmul-wgsl
   :q4-k-matmul q4-k-matmul-wgsl
   :q6-k-matmul q6-k-matmul-wgsl
   :q8-0-matmul q8-0-matmul-wgsl
   :q5-0-embedding q5-0-embedding-wgsl
   :q4-k-embedding q4-k-embedding-wgsl
   :q6-k-embedding q6-k-embedding-wgsl
   :q8-0-embedding q8-0-embedding-wgsl
   :rgb-image-to-nchw rgb-image-to-nchw-wgsl
   :nchw-to-rgb-image nchw-to-rgb-image-wgsl
   :nchw-to-rgb-image-f16 nchw-to-rgb-image-f16-wgsl
   :scale-f16 scale-f16-wgsl
   :f16-to-f32 f16-to-f32-wgsl
   :f32-to-f16 f32-to-f16-wgsl
   :bf16-to-f32 bf16-to-f32-wgsl
   :paged-kv-write paged-kv-write-wgsl
   :paged-kv-copy-block paged-kv-copy-block-wgsl
   :paged-gqa-attention paged-gqa-attention-wgsl
   :paged-gqa-attention-batch paged-gqa-attention-batch-wgsl
   :group-norm-silu-nchw group-norm-silu-nchw-wgsl
   :upsample-nearest2d upsample-nearest2d-wgsl
   :upsample-nearest2d-f16 upsample-nearest2d-f16-wgsl
   :cat-copy cat-copy-wgsl
   :slice-axis slice-axis-wgsl
   :slice-axis-f16 slice-axis-f16-wgsl
   :pad-right-bottom-nchw pad-right-bottom-nchw-wgsl
   :add-last-axis-bias add-last-axis-bias-wgsl
   :add-last-axis-bias-f16 add-last-axis-bias-f16-wgsl
   :transpose-2d transpose-2d-wgsl
   :transpose-nd transpose-nd-wgsl
   :transpose-nd-f16 transpose-nd-f16-wgsl
   :batched-matmul batched-matmul-wgsl
   :bias-gradient bias-gradient-wgsl
   :mse-loss mse-loss-wgsl
   :mse-gradient mse-gradient-wgsl
   :cross-entropy-stats-f16 cross-entropy-stats-f16-wgsl
   :cross-entropy-loss-f16 cross-entropy-loss-f16-wgsl
   :cross-entropy-gradient-f16 cross-entropy-gradient-f16-wgsl
   :sgd-step sgd-step-wgsl
   :adamw-step adamw-step-wgsl
   :unscale-gradient unscale-gradient-wgsl
   :multi-head-attention multi-head-attention-wgsl
   :multi-head-attention-f16 multi-head-attention-f16-wgsl
   :multi-head-attention-backward multi-head-attention-backward-wgsl
   :ewise-f16 ewise-f16-wgsl
   :ewise1-f16 ewise1-f16-wgsl
   :gemm-f16 gemm-f16-wgsl
   :conv2d-nchw-f16 conv2d-nchw-f16-wgsl
   :conv2d-nchw-f16-oc4 conv2d-nchw-f16-oc4-wgsl
   :group-norm-nchw-f16 group-norm-nchw-f16-wgsl
   :group-norm-nchw-f16-reference group-norm-nchw-f16-reference-wgsl
   :embedding-f16 embedding-f16-wgsl
   :embedding-backward-f16 embedding-backward-f16-wgsl
   :rms-norm-f16 rms-norm-f16-wgsl
   :rms-norm-backward-stats-f16 rms-norm-backward-stats-f16-wgsl
   :rms-norm-backward-f16 rms-norm-backward-f16-wgsl
   :rotary-embedding-f16 rotary-embedding-f16-wgsl
   :copy-into-f16 copy-into-f16-wgsl
   :spmv   spmv-csr-wgsl})

;; ---------------------------------------------------------------------------
;; IBackend ⇄ shader mapping (what a host-side WgslBackend wires up)
;; ---------------------------------------------------------------------------

(def dispatch-plan
  "For each IBackend op: the shader and how to size the grid. A WgslBackend turns
  this into (-compile) + (-dispatch) calls; ops absent here fall back to a host
  read-compute-write so the backend stays COMPLETE."
  {:-axpy  {:shader :axpy   :workgroups (fn [n] [(quot (+ n 63) 64) 1 1])}
   :-spmv  {:shader :spmv   :workgroups (fn [rows] [(quot (+ rows 63) 64) 1 1])}
   :-gemm  {:shader :gemm   :workgroups (fn [m n] [(quot (+ n 15) 16) (quot (+ m 15) 16) 1])}
   :-dot   {:shader :reduce :workgroups (fn [n] [(quot (+ n 255) 256) 1 1]) :then :host-sum}})
