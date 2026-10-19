(ns num.protocol
  "IBackend — the ONE seam every compute backend implements, and the whole reason
  num-clj can be `.cljc`-portable yet GPU-fast.

  A `handle` is an opaque per-backend buffer reference: a `double-array` for the
  CPU reference backend, a `GPUBuffer` for the WGSL/WebGPU backend, a device
  pointer for CUDA/Metal/ROCm. The array layer (`num.array`) never touches a
  handle's internals — it only moves them through these methods. So the SAME
  high-level program (`num.core/matmul`, `spmv`, …) runs unchanged whether the
  active backend is pure Clojure on a phone, a WGSL compute pipeline in a browser
  tab, or cuBLAS on an H100 — exactly the Store-injection / cae-solver-dispatch
  pattern used across com-junkawasaki.

  Every op is EAGER and explicit (no lazy graph at this layer); dims travel
  alongside handles BLAS-style so a backend never has to infer shape. Dense
  matrices are row-major. Reductions/dot return host scalars; everything else
  returns a handle (often mutated in place, BLAS convention).")

(defprotocol IBackend
  ;; identity / lifecycle ----------------------------------------------------
  (-backend-name [b] "Keyword id, e.g. :cpu / :wgsl / :cuda.")
  (-alloc [b n] "Allocate an uninitialized buffer of `n` scalars → handle.")
  (-free [b h] "Release a handle (no-op where GC handles it).")
  (-copy-to-host [b h n] "Device→host: return a Clojure vector of `n` doubles.")
  (-copy-from-host [b xs] "Host→device: `xs` (seq of numbers) → handle.")

  ;; level-1 BLAS ------------------------------------------------------------
  (-axpy [b alpha xh yh n] "y ← αx + y (in place on yh); returns yh.")
  (-scal [b alpha xh n] "x ← αx (in place); returns xh.")
  (-dot [b xh yh n] "Σ xᵢ yᵢ → host scalar.")
  (-nrm2 [b xh n] "‖x‖₂ → host scalar.")

  ;; elementwise + reduction -------------------------------------------------
  (-ewise [b op xh yh n] "op ∈ #{:add :sub :mul :div}; returns a NEW handle z=op(x,y).")
  (-ewise1 [b op xh n] "UNARY elementwise activation/derivative; returns a NEW handle z=op(x).")
  (-reduce [b op xh n] "op ∈ #{:sum :max :min}; → host scalar.")

  ;; level-2 / level-3 BLAS (dense, row-major) -------------------------------
  (-gemv [b alpha Ah m n xh beta yh] "y ← αAx + βy, A is m×n; returns yh.")
  (-gemm [b alpha Ah m k Bh n beta Ch] "C ← αAB + βC, A m×k, B k×n, C m×n; returns Ch.")

  ;; sparse (CSR) ------------------------------------------------------------
  (-spmv [b csr xh] "Sparse mat-vec y = A·x for a CSR `csr` (see num.sparse); → new handle."))

(defn backend?
  "Does `x` satisfy IBackend?"
  [x]
  (satisfies? IBackend x))

(defprotocol IDTypeStorage
  "Optional physical storage contract for non-f32 arrays. Implementations must
  allocate and transfer the requested dtype rather than merely tagging values."
  (-alloc-dtype [b n dtype] "Allocate `n` physical elements of `dtype`.")
  (-copy-from-host-dtype [b xs dtype] "Quantize/upload host values as `dtype`.")
  (-copy-to-host-dtype [b h n dtype] "Decode/download `n` `dtype` elements."))

(defprotocol ICastOps
  "Optional device-native conversion between physical storage dtypes."
  (-cast-dtype [b h n source-dtype target-dtype]))

(defprotocol IDTypeOps
  "Optional compute contract over physical non-f32 storage. Accumulation policy
  is backend-defined; outputs must be materialized in the requested dtype."
  (-ewise-dtype [b op xh yh n dtype])
  (-ewise1-dtype [b op xh n dtype])
  (-scale-dtype [b alpha xh n dtype])
  (-gemm-dtype [b Ah m k Bh n dtype]))

(defprotocol IMutableBufferOps
  "Optional bounded device-to-device writes used by preallocated caches."
  (-copy-into! [b destination-h source-h destination-offset n dtype]
    "Copy `n` contiguous elements into destination at an element offset."))

(defprotocol IQuantizedOps
  "Packed inference-weight storage and compute. Handles preserve the original
  GGML bytes; outputs accumulate in f32 without materializing a dense weight."
  (-quantized-from-host [b bytes params]
    "Upload unsigned packed bytes and return an opaque quantized handle.")
  (-quantized-matmul [b input-h weight-h params]
    "Multiply f32 `[m,k]` input by a packed logical `[k,n]` weight.")
  (-quantized-embedding [b indices-h table-h params]
    "Gather packed quantized table rows into a dense f32 output."))

(defprotocol IDeviceSelection
  "Optional device-native token selection. These boundaries return only token
  indices or bounded candidate pairs to the host instead of transferring a
  full logits matrix."
  (-argmax-rows [b logits-h rows cols]
    "Argmax each row of contiguous f32 `[rows,cols]` logits. Ties choose the
    lowest column index. Async devices may return a Promise of indices.")
  (-top-k-row [b logits-h rows cols row k previous-tokens repetition-penalty]
    "Apply repetition penalty once to the unique previous-token columns of one
    row, then return its best `[token logit]` pairs in descending order. The
    logits row may be mutated. Async devices may return a Promise.")
  (-sample-top-k-row
    [b logits-h rows cols row k previous-tokens repetition-penalty
     temperature top-p random-value]
    "Apply repetition penalty and exact top-k, then perform temperature/top-p
    sampling on device. Returns only the selected token index.")
  (-sample-softmax-row
    [b logits-h rows cols row previous-tokens repetition-penalty
     temperature random-value]
    "Apply repetition penalty and sample the full temperature-softmax
    distribution on device. Returns only the selected token index.")
  (-sample-nucleus-row
    [b logits-h rows cols row previous-tokens repetition-penalty
     temperature top-p random-value]
    "Stable-sort a full logits row, apply exact nucleus truncation, and sample
    on device. Returns only the selected token index.")
  (-speculative-rejection-row
    [b target-h draft-h rows cols row draft-token temperature
     acceptance-random residual-random]
    "Leviathan accept/reject for one proposed token over target/draft logits.
    Rejection samples the positive residual distribution on device."))

(defprotocol IDTypeTensorOps
  "Optional N-D compute operations over physical typed storage."
  (-conv2d-nchw-dtype [b input-h weight-h bias-h params dtype])
  (-group-norm-nchw-dtype [b input-h weight-h bias-h params dtype])
  (-upsample-nearest2d-dtype [b input-h params dtype])
  (-slice-axis-dtype [b input-h params dtype])
  (-nchw-to-rgb-image-dtype [b input-h params dtype])
  (-transpose-dtype [b input-h params dtype])
  (-multi-head-attention-dtype [b query-h key-h value-h params dtype])
  (-multi-head-attention-backward-dtype
    [b query-h key-h value-h grad-output-h params dtype])
  (-add-last-axis-bias-dtype [b input-h bias-h params dtype])
  (-embedding-dtype [b indices-h weight-h params dtype])
  (-embedding-backward-dtype [b indices-h grad-output-h params dtype])
  (-rms-norm-dtype [b input-h weight-h params dtype])
  (-rms-norm-backward-dtype [b input-h weight-h grad-output-h params dtype])
  (-cross-entropy-forward-dtype [b logits-h labels-h params dtype])
  (-cross-entropy-backward-dtype
    [b logits-h labels-h stats-h upstream-h params dtype])
  (-rotary-embedding-dtype [b input-h params dtype]))

(defprotocol ITensorBackend
  "Optional device-native N-D operations. Backends that do not implement this
  protocol continue to use num.tensor's portable host oracle."
  (-conv2d-nchw [b input-h weight-h bias-h params]
    "NCHW cross-correlation. `params` contains validated dimensions/options;
    returns a newly allocated output handle.")
  (-group-norm-nchw [b input-h weight-h bias-h params]
    "NCHW GroupNorm with optional affine parameters; returns a new handle.")
  (-embedding [b indices-h weight-h params]
    "Gather embedding rows for contiguous f32 token indices.")
  (-rms-norm [b input-h weight-h params]
    "RMS-normalize contiguous rows over their final dimension.")
  (-rotary-embedding [b input-h params]
    "Apply head-wise Llama rotary position embedding.")
  (-rgb-image-to-nchw [b input-h params]
    "Convert NHWC RGB [0,1] into NCHW model input [-1,1].")
  (-nchw-to-rgb-image [b input-h params]
    "Convert NCHW RGB model output [-1,1] into clamped NHWC [0,1].")
  (-upsample-nearest2d [b input-h params]
    "Integer nearest-neighbor NCHW upsampling; returns a new handle.")
  (-cat [b input-handles params]
    "Concatenate contiguous tensors along an arbitrary axis; returns a new handle.")
  (-slice-axis [b input-h params]
    "Copy a contiguous range along one tensor axis into a new handle.")
  (-pad-right-bottom-nchw [b input-h params]
    "Append one zero column and row to a contiguous NCHW tensor.")
  (-add-last-axis-bias [b input-h bias-h params]
    "Broadcast-add a rank-1 bias over every contiguous last-axis row.")
  (-transpose-2d [b input-h params]
    "Out-of-place transpose of a contiguous f32 matrix.")
  (-transpose-nd [b input-h params]
    "Out-of-place axis permutation for a contiguous rank-1 through rank-4 tensor.")
  (-batched-matmul [b a-h b-h params]
    "Batched f32 matrix multiplication with broadcast leading dimensions.")
  (-sum-rows [b input-h params]
    "Reduce a contiguous f32 matrix over its row axis.")
  (-mse-loss [b prediction-h target-h params]
    "Mean squared error as a device-resident scalar handle.")
  (-mse-gradient [b prediction-h target-h upstream-h params]
    "MSE vector-Jacobian product as a device-resident tensor handle.")
  (-sgd-step [b parameter-h gradient-h params]
    "Out-of-place immutable SGD update as a new device tensor handle.")
  (-adamw-step [b parameter-h gradient-h moment-h variance-h params]
    "Fused immutable AdamW update; returns {:parameter :moment :variance} handles.")
  (-unscale-gradient [b gradient-h params]
    "Unscale gradient and detect non-finite values; returns gradient/flag handles.")
  (-multi-head-attention [b query-h key-h value-h key-padding-mask-h params]
    "Fused batched scaled dot-product attention with causal/padding masks.")
  (-multi-head-attention-backward [b query-h key-h value-h key-padding-mask-h
                                   grad-output-h params]
    "Fused attention backward; returns {:query :key :value} gradient handles."))
