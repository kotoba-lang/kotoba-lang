(ns num.wgsl-backend
  "WgslBackend — a host-side `IBackend` that runs num-clj on the GPU by dispatching
  the `num.wgsl` compute shaders through an injected `IGpuDevice`. It is the
  Clojure dispatch logic; the shaders themselves are verified on Apple M4 Metal
  (`verify/metal_contract.js`), and a live device makes this deftype reproduce that
  contract on any wgpu target.

  Sync vs async: this backend is SYNCHRONOUS (it returns host values from
  `-copy-to-host`). WebGPU buffer readback is fundamentally async, so a synchronous
  WgslBackend needs a device whose `-read-buffer` BLOCKS — i.e. a native wgpu
  binding (JVM Panama/FFM → wgpu-native) or a vendor backend. The browser/Deno
  WebGPU path is async — `num.deno-gpu/WgslBackendAsync` (ADR-2607051400 §Phase 2)
  reuses `ceil-div`/`uni`/`get-pipeline` below (public for that reuse) and mirrors
  this dispatch logic exactly, except the host-value-returning ops
  (`-copy-to-host`/`-reduce`/`-nrm2`) return a JS Promise instead of an immediate
  value, because a Deno/browser `IGpuDevice`'s `-read-buffer` cannot block."
  (:require [num.protocol :as p]
            [num.wgsl :as w]
            [num.cpu :as cpu]))

(defn ceil-div
  "⌈a/b⌉ for workgroup-count sizing. Public: shared by num.deno-gpu's async backend."
  [a b] (quot (+ (long a) (long b) -1) (long b)))

(defn u32-tag
  "Tag `xs` as u32-typed payload (index/dims/op-selector data) via metadata, so an
  IGpuDevice's `-write-buffer` can pick the correct byte pattern — a plain Clojure
  number carries no int-vs-float distinction under ClojureScript (unlike the JVM,
  where Long vs Double could disambiguate this; num.deno-gpu's IGpuDevice runs as
  cljs, so it cannot use that trick). Untagged `xs` defaults to f32 (every payload
  data buffer — vector/matrix values, CSR `vals` — is f32; only CSR `row-ptr`/
  `col-idx` and the uniform dims/op-selector below are u32). Public: shared by
  num.deno-gpu's async backend."
  [xs]
  (with-meta (vec xs) {:num.wgsl/dtype :u32}))

(defn pack-bytes-u32
  "Pack unsigned bytes into little-endian u32 words for storage shaders."
  [bytes]
  (u32-tag
   (mapv (fn [offset]
           (reduce (fn [word lane]
                     (bit-or word
                             (bit-shift-left
                              (bit-and 0xff (nth bytes (+ offset lane) 0))
                              (* lane 8))))
                   0 (range 4)))
         (range 0 (count bytes) 4))))

(defn uni
  "Create + fill a uniform buffer from host seq `xs` (device pads to alignment).
  `xs` defaults to f32; pass it through `u32-tag` first for a u32 uniform (dims,
  op-selector). Public: shared by num.deno-gpu's async backend."
  [dev xs]
  (let [b (w/-create-buffer dev (count xs) :uniform)] (w/-write-buffer dev b xs) b))

(defn get-pipeline
  "Lazily compile + cache the pipeline for op keyword `op`.
  Public: shared by num.deno-gpu's async backend."
  [dev pipes op]
  (or (get @pipes op)
      (let [pl (w/-compile dev (get w/shaders op) "main")]
        (swap! pipes assoc op pl) pl)))

(deftype WgslBackend [dev pipes fallback]
  p/IBackend
  (-backend-name [_] :wgsl)
  (-alloc [_ n] (w/-create-buffer dev n :storage))
  (-free [_ h]
    (when (satisfies? w/IGpuDeviceLifecycle dev)
      (w/-destroy-buffer dev h))
    nil)
  (-copy-from-host [_ xs]
    ;; Force `double` here exactly like num.cpu's -copy-from-host does: callers
    ;; routinely pass literal integers (e.g. `(arr/from-vec b [1 2 3 4] [4])`
    ;; in num.contract's own fixtures) that MUST land as f32 bit patterns on the
    ;; GPU (every num.wgsl shader declares its data buffers `array<f32>`), not
    ;; be reinterpreted as u32 by an IGpuDevice that infers dtype from Clojure's
    ;; runtime number type (see num.deno-gpu). Pre-existing gap fixed in
    ;; ADR-2607051400 §Phase 2 — never caught before because no live device
    ;; exercised this path.
    (let [b (w/-create-buffer dev (count xs) :storage)] (w/-write-buffer dev b (map double xs)) b))
  (-copy-to-host [_ h n] (w/-read-buffer dev h n))      ; BLOCKING readback (native device)

  (-axpy [_ alpha xh yh n]
    (w/-dispatch dev (get-pipeline dev pipes :axpy) [xh yh (uni dev [(double alpha)])]
                 [(ceil-div n 64) 1 1])
    yh)

  (-scal [_ alpha xh n]
    (w/-dispatch dev (get-pipeline dev pipes :scal) [xh (uni dev [(double alpha)])]
                 [(ceil-div n 64) 1 1])
    xh)

  (-ewise [_ op xh yh n]
    (let [z (w/-create-buffer dev n :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :ewise)
                   [xh yh z (uni dev (u32-tag [({:add 0 :sub 1 :mul 2 :div 3} op)]))]
                   [(ceil-div n 64) 1 1])
      z))

  (-ewise1 [_ op xh n]
    (let [z (w/-create-buffer dev n :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :ewise1)
                   [xh z (uni dev (u32-tag
                                   [({:exp 0 :relu 1 :neg 2 :silu 3
                                      :sigmoid 4 :tanh 5
                                      :sigmoid-gradient 6 :tanh-gradient 7
                                      :gelu 8 :gelu-gradient 9} op)]))]
                   [(ceil-div n 64) 1 1])
      z))

  (-reduce [_ op xh n]
    (let [nwg (ceil-div n 256)
          parts (w/-create-buffer dev nwg :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :reduce)
                   [xh parts (uni dev (u32-tag [({:sum 0 :max 1 :min 2} op)]))] [nwg 1 1])
      (reduce (case op :sum + :max max :min min) (w/-read-buffer dev parts nwg))))

  (-dot [this xh yh n] (p/-reduce this :sum (p/-ewise this :mul xh yh n) n))
  (-nrm2 [this xh n] (Math/sqrt (p/-dot this xh xh n)))

  (-gemv [this alpha Ah m n xh beta yh]
    (w/-dispatch dev (get-pipeline dev pipes :gemv) [Ah xh yh (uni dev (u32-tag [m n]))]
                 [(ceil-div m 64) 1 1])
    (when (or (not= 1.0 alpha) (not= 0.0 beta)) (p/-scal this alpha yh m))
    yh)

  (-gemm [this alpha Ah m k Bh n beta Ch]
    (w/-dispatch dev (get-pipeline dev pipes :gemm) [Ah Bh Ch (uni dev (u32-tag [m k n 0]))]
                 [(ceil-div n 16) (ceil-div m 16) 1])
    (when (not= 1.0 alpha) (p/-scal this alpha Ch (* m n)))
    Ch)

  (-spmv [_ csr xh]
    (let [m (:n-rows csr)
          rp (w/-create-buffer dev (inc m) :storage)
          ci (w/-create-buffer dev (:nnz csr) :storage)
          v  (w/-create-buffer dev (:nnz csr) :storage)
          y  (w/-create-buffer dev m :storage)]
      (w/-write-buffer dev rp (u32-tag (seq (:row-ptr csr))))
      (w/-write-buffer dev ci (u32-tag (seq (:col-idx csr))))
      (w/-write-buffer dev v (seq (:vals csr)))
      (w/-dispatch dev (get-pipeline dev pipes :spmv) [rp ci v xh y] [(ceil-div m 64) 1 1])
      y))

  p/IQuantizedOps
  (-quantized-from-host [_ bytes _params]
    (let [words (pack-bytes-u32 bytes)
          buffer (w/-create-buffer dev (count words) :storage)]
      (w/-write-buffer dev buffer words)
      buffer))
  (-quantized-matmul [_ input-h weight-h
                      {:keys [quant-type m k n blocks-per-row]}]
    (when-not (#{:q5-0 :q4-k :q6-k :q8-0} quant-type)
      (throw (ex-info "unsupported WGSL quantized matmul" {:quant-type quant-type})))
    (let [output (w/-create-buffer dev (* m n) :storage)]
      (w/-dispatch dev (get-pipeline dev pipes
                                     ({:q5-0 :q5-0-matmul
                                       :q4-k :q4-k-matmul
                                       :q6-k :q6-k-matmul
                                       :q8-0 :q8-0-matmul} quant-type))
                   [input-h weight-h output
                    (uni dev (u32-tag [m k n blocks-per-row]))]
                   [(* (ceil-div m 4) n) 1 1])
      output))
  (-quantized-embedding [_ indices-h table-h
                         {:keys [quant-type rows dim count blocks-per-row total]}]
    (let [pipeline ({:q5-0 :q5-0-embedding
                     :q4-k :q4-k-embedding :q6-k :q6-k-embedding
                     :q8-0 :q8-0-embedding} quant-type)]
      (when-not pipeline
        (throw (ex-info "unsupported WGSL quantized embedding"
                        {:quant-type quant-type})))
      (let [output (w/-create-buffer dev total :storage)]
        (w/-dispatch dev (get-pipeline dev pipes pipeline)
                     [indices-h table-h output
                      (uni dev (u32-tag [rows dim count blocks-per-row
                                         total 0 0 0]))]
                     [(ceil-div total 64) 1 1])
        output)))

  p/IMutableBufferOps
  (-copy-into! [_ destination source offset n dtype*]
    (when-not (= dtype* :f32)
      (throw (ex-info "synchronous WGSL copy-into supports f32 only"
                      {:dtype dtype*})))
    (w/-dispatch dev (get-pipeline dev pipes :copy-into)
                 [destination source (uni dev (u32-tag [offset n 0 0]))]
                 [(ceil-div n 64) 1 1])
    destination)

  p/ITensorBackend
  (-conv2d-nchw [_ input-h weight-h bias-h
                 {:keys [n cin h width cout cin-group kh kw oh ow sh sw ph pw dh dw groups]}]
    (let [total (* n cout oh ow)
          output (w/-create-buffer dev total :storage)
          bias (or bias-h (w/-create-buffer dev cout :storage))
          params [n cin h width cout cin-group kh kw oh ow sh sw ph pw dh dw
                  groups 0 0 0]]
      (let [oc4? (and (= groups 1) (zero? (mod cout 4)))
            invocations (if oc4? (quot total 4) total)]
        (w/-dispatch dev (get-pipeline dev pipes
                                      (if oc4? :conv2d-nchw-oc4 :conv2d-nchw))
                     [input-h weight-h bias output (uni dev (u32-tag params))]
                     [(ceil-div invocations 64) 1 1]))
      output))
  (-group-norm-nchw [_ input-h weight-h bias-h
                     {:keys [n c h width groups channels-group group-size eps]
                      :as params}]
    (let [total (* n c h width)
          output (w/-create-buffer dev total :storage)
          weight (or weight-h
                     (let [buffer (w/-create-buffer dev c :storage)]
                       (w/-write-buffer dev buffer (repeat c 1.0)) buffer))
          bias (or bias-h (w/-create-buffer dev c :storage))
          dims [n c h width groups channels-group group-size (* h width)]]
      (w/-dispatch dev (get-pipeline dev pipes
                                    (if (:silu? params)
                                      :group-norm-silu-nchw :group-norm-nchw))
                   [input-h weight bias output (uni dev (u32-tag dims))
                    (uni dev [(double eps)])]
                   [(* n groups) 1 1])
      output))
  (-embedding [_ indices-h weight-h {:keys [tokens rows dim]}]
    (let [total (* tokens dim)
          output (w/-create-buffer dev total :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :embedding)
                   [indices-h weight-h output
                    (uni dev (u32-tag [tokens rows dim 0]))]
                   [(ceil-div total 64) 1 1])
      output))
  (-rms-norm [_ input-h weight-h {:keys [rows dim eps]}]
    (let [output (w/-create-buffer dev (* rows dim) :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :rms-norm)
                   [input-h weight-h output (uni dev (u32-tag [rows dim 0 0]))
                    (uni dev [(double eps)])]
                   [rows 1 1])
      output))
  (-rotary-embedding [_ input-h {:keys [batch sequence embed heads head-dim
                                         position-offset theta direction]}]
    (let [total (* batch sequence embed)
          output (w/-create-buffer dev total :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :rotary-embedding)
                   [input-h output
                    (uni dev (u32-tag [batch sequence embed heads head-dim
                                       position-offset 0 0]))
                    (uni dev [(double theta)]) (uni dev [(double direction)])]
                   [(ceil-div total 64) 1 1])
      output))
  (-rgb-image-to-nchw [_ input-h {:keys [height width total]}]
    (let [output (w/-create-buffer dev total :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :rgb-image-to-nchw)
                   [input-h output (uni dev (u32-tag [height width total 0]))]
                   [(ceil-div total 64) 1 1])
      output))
  (-nchw-to-rgb-image [_ input-h {:keys [height width total]}]
    (let [output (w/-create-buffer dev total :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :nchw-to-rgb-image)
                   [input-h output (uni dev (u32-tag [height width total 0]))]
                   [(ceil-div total 64) 1 1])
      output))
  (-upsample-nearest2d [_ input-h {:keys [n c h width oh ow scale-h scale-w]}]
    (let [total (* n c oh ow)
          output (w/-create-buffer dev total :storage)
          dims [n c h width oh ow scale-h scale-w]]
      (w/-dispatch dev (get-pipeline dev pipes :upsample-nearest2d)
                   [input-h output (uni dev (u32-tag dims))]
                   [(ceil-div total 64) 1 1])
      output))
  (-cat [_ input-handles {:keys [total-output output-block inputs]}]
    (let [output (w/-create-buffer dev total-output :storage)]
      (doseq [[input-h {:keys [total block axis-offset]}]
              (map vector input-handles inputs)]
        (w/-dispatch dev (get-pipeline dev pipes :cat-copy)
                     [input-h output (uni dev (u32-tag [total block output-block axis-offset]))]
                     [(ceil-div total 64) 1 1]))
      output))
  (-slice-axis [_ input-h {:keys [total input-block output-block input-offset]}]
    (let [output (w/-create-buffer dev total :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :slice-axis)
                   [input-h output
                    (uni dev (u32-tag [total input-block output-block input-offset]))]
                   [(ceil-div total 64) 1 1])
      output))
  (-pad-right-bottom-nchw [_ input-h {:keys [total h width output-width]}]
    (let [output (w/-create-buffer dev total :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :pad-right-bottom-nchw)
                   [input-h output (uni dev (u32-tag [total h width output-width]))]
                   [(ceil-div total 64) 1 1])
      output))
  (-add-last-axis-bias [_ input-h bias-h {:keys [total width]}]
    (let [output (w/-create-buffer dev total :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :add-last-axis-bias)
                   [input-h bias-h output (uni dev (u32-tag [total width 0 0]))]
                   [(ceil-div total 64) 1 1])
      output))
  (-transpose-2d [_ input-h {:keys [rows cols]}]
    (let [output (w/-create-buffer dev (* rows cols) :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :transpose-2d)
                   [input-h output (uni dev (u32-tag [rows cols]))]
                   [(ceil-div cols 16) (ceil-div rows 16) 1])
      output))
  (-transpose-nd [_ input-h {:keys [rank total input-shape output-shape perm]}]
    (let [pad4 #(vec (take 4 (concat % (repeat 0))))
          params (concat [rank total 0 0] (pad4 input-shape)
                         (pad4 output-shape) (pad4 perm))
          output (w/-create-buffer dev total :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :transpose-nd)
                   [input-h output (uni dev (u32-tag params))]
                   [(ceil-div total 64) 1 1])
      output))
  (-batched-matmul [_ a-h b-h {:keys [batch-shape batch-a batch-b batch-rank
                                       batches m k n total]}]
    (let [align (fn [shape]
                  (vec (concat (repeat (- batch-rank (count shape)) 1) shape
                               (repeat (- 4 batch-rank) 1))))
          out-shape (vec (concat batch-shape (repeat (- 4 batch-rank) 1)))
          params (concat [batch-rank batches m n] [k total 0 0]
                         out-shape (align batch-a) (align batch-b))
          output (w/-create-buffer dev total :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :batched-matmul)
                   [a-h b-h output (uni dev (u32-tag params))]
                   [(ceil-div total 64) 1 1])
      output))
  (-sum-rows [_ input-h {:keys [rows cols]}]
    (let [output (w/-create-buffer dev cols :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :bias-gradient)
                   [input-h output (uni dev (u32-tag [rows cols]))]
                   [(ceil-div cols 64) 1 1])
      output))
  (-mse-loss [_ prediction-h target-h {:keys [count]}]
    (let [loss (w/-create-buffer dev 1 :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :mse-loss)
                   [prediction-h target-h loss (uni dev (u32-tag [count]))]
                   [1 1 1])
      loss))
  (-mse-gradient [_ prediction-h target-h upstream-h {:keys [count]}]
    (let [gradient (w/-create-buffer dev count :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :mse-gradient)
                   [prediction-h target-h upstream-h gradient
                    (uni dev (u32-tag [count]))]
                   [(ceil-div count 64) 1 1])
      gradient))
  (-sgd-step [_ parameter-h gradient-h {:keys [count learning-rate]}]
    (let [output (w/-create-buffer dev count :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :sgd-step)
                   [parameter-h gradient-h output
                    (uni dev [(double learning-rate)])
                    (uni dev (u32-tag [count]))]
                   [(ceil-div count 64) 1 1])
      output))
  (-adamw-step [_ parameter-h gradient-h moment-h variance-h
                {:keys [count learning-rate beta1 beta2 eps weight-decay
                        correction1 correction2]}]
    (let [moment (or moment-h (w/-create-buffer dev count :storage))
          variance (or variance-h (w/-create-buffer dev count :storage))
          next-parameter (w/-create-buffer dev count :storage)
          next-moment (w/-create-buffer dev count :storage)
          next-variance (w/-create-buffer dev count :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :adamw-step)
                   [parameter-h gradient-h moment variance
                    next-parameter next-moment next-variance
                    (uni dev (mapv double
                                   [learning-rate beta1 beta2 eps weight-decay
                                    correction1 correction2 0.0]))
                    (uni dev (u32-tag [count]))]
                   [(ceil-div count 64) 1 1])
      {:parameter next-parameter :moment next-moment
       :variance next-variance}))
  (-unscale-gradient [_ gradient-h {:keys [count inverse-scale]}]
    (let [output (w/-create-buffer dev count :storage)
          found-inf (w/-create-buffer dev 1 :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :unscale-gradient)
                   [gradient-h output found-inf
                    (uni dev [(double inverse-scale)])
                    (uni dev (u32-tag [count]))]
                   [(ceil-div count 64) 1 1])
      {:gradient output :found-inf found-inf}))
  (-multi-head-attention [_ query-h key-h value-h key-padding-mask-h
                          {:keys [batch seq-q seq-k d-model kv-d-model heads kv-heads head-dim total
                                  causal? has-key-padding-mask?]}]
    (let [output (w/-create-buffer dev total :storage)
          mask (or key-padding-mask-h
                   (w/-create-buffer dev (* batch seq-k) :storage))]
      (w/-dispatch dev (get-pipeline dev pipes :multi-head-attention)
                   [query-h key-h value-h mask output
                    (uni dev (u32-tag [batch seq-q seq-k d-model kv-d-model
                                       heads kv-heads head-dim total
                                       (if causal? 1 0)
                                       (if has-key-padding-mask? 1 0) 0]))]
                   [(ceil-div total 64) 1 1])
      output))
  (-multi-head-attention-backward [_ query-h key-h value-h key-padding-mask-h
                                   grad-output-h
                                   {:keys [batch seq-q seq-k d-model kv-d-model heads kv-heads head-dim
                                           causal? has-key-padding-mask?]}]
    (let [total-q (* batch seq-q d-model)
          total-k (* batch seq-k kv-d-model)
          total (max total-q total-k)
          mask (or key-padding-mask-h
                   (w/-create-buffer dev (* batch seq-k) :storage))
          grad-query (w/-create-buffer dev total-q :storage)
          grad-key (w/-create-buffer dev total-k :storage)
          grad-value (w/-create-buffer dev total-k :storage)]
      (w/-dispatch dev (get-pipeline dev pipes :multi-head-attention-backward)
                   [query-h key-h value-h mask grad-output-h
                    grad-query grad-key grad-value
                    (uni dev (u32-tag [batch seq-q seq-k d-model kv-d-model
                                       heads kv-heads head-dim total-q total-k total
                                       (if causal? 1 0)
                                       (if has-key-padding-mask? 1 0) 0 0 0]))]
                   [(ceil-div total 64) 1 1])
      {:query grad-query :key grad-key :value grad-value})))

(defn wgsl-backend
  "Construct a WgslBackend over an injected `IGpuDevice` (native, blocking
  readback). Pipelines compile lazily on first use."
  [device]
  (->WgslBackend device (atom {}) (cpu/cpu-backend)))
