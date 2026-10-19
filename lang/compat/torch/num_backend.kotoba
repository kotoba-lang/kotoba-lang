(ns torch.num-backend
  "ADR-2607131500 Phase 1 — the first real `torch.ports/IBackend`: wires
  torch-clj's described layer graph to num-clj's verified tensor ops
  (`num.core`/`num.tensor`), so `torch.core/run` actually computes instead of
  throwing \"torch-clj is shape-only here\".

  SCOPE (deliberately not full PyTorch-layer coverage): `:linear`, `:relu`,
  `:silu`, `:sigmoid`, `:tanh`, `:gelu`, `:softmax`, `:rmsnorm`, affine `:layernorm`/`:groupnorm`, and full NCHW `:conv2d` execute
  through verified num ops. Convolution supports batches, groups/depthwise,
  bias, stride, padding, and dilation. Learned `:multihead-attention` supports
  batch-first inputs plus causal, key-padding, and separate context sequences
  through runtime layer options. Parameter-free `:attention` remains the original
  rank-2 form. `num-heads` selects multi-head via
  `num.tensor/multi-head-attention`, which is EXACTLY `num.tensor/attention`
  at num-heads=1 (verified in num's own test suite). Every other layer type
  throws.

  WEIGHTS: torch-clj's model EDN is shape-and-parameter-COUNT only — it
  never carries actual weight values (by design, see torch.model). This
  backend therefore takes weights as an explicit, caller-supplied vector
  (one entry per layer of the NORMALIZED model, `nil` for parameterless
  layers), not something it invents or loads. `random-weights` below is a
  small, deterministic (not cryptographic, not torch/numpy-matching)
  generator for exercising a model end-to-end without hand-writing every
  number. Real checkpoint weights are loaded through `torch.safetensors` and
  `torch.state-dict`.

  BACKEND CHOICE: this wraps whatever `num.protocol/IBackend` the caller
  passes to `num-backend`. CPU is the reference oracle; the device-native
  learned-attention path is verified through Deno WebGPU on Apple Metal."
  (:require [num.array :as arr]
            [num.core :as nm]
            [num.quantized :as quantized]
            [num.protocol :as p]
            [num.tensor :as t]
            [torch.model :as model]
            [torch.paged-runtime :as paged]
            [torch.ports :as ports]))

(defn- matmul-weight [input weight]
  (if (quantized/matrix? weight)
    (quantized/matmul input weight)
    (nm/matmul input weight)))

;; --- deterministic weight generation (NOT torch/numpy-matching — see ns doc) --

(defn- lcg-seq
  "A small deterministic pseudo-random sequence of doubles in [-0.5, 0.5),
  portable .cljc (no java.util.Random — that's JVM-only and this repo
  targets JVM+cljs). A linear congruential generator is plenty for
  exercising a forward pass end-to-end; it is NOT cryptographic and NOT a
  claim of statistical quality."
  [seed n]
  (->> (iterate (fn [s] (mod (+ (* 1103515245 s) 12345) 2147483648)) (long seed))
       (drop 1) (take n)
       (map (fn [s] (- (/ (double s) 2147483648.0) 0.5)))))

(defn- flatten-batch-shape [shape]
  (if (<= (count shape) 1)
    shape
    [(first shape) (arr/nelems (subvec (vec shape) 1))]))

(defn random-weights
  "A weights vector for `model*` (one entry per NORMALIZED layer, `nil` for
  parameterless layers) on `backend`, seeded from `seed` for reproducibility.
  `:linear [in out]` -> `{:w [in out] :b [out]}`; `:conv2d` ->
  `{:w [out-ch in-ch/groups kh kw] :b [out-ch]}`; `:groupnorm` -> affine
  `{:w [channels] :b [channels]}`; `:layernorm` uses the same affine layout;
  `:attention` has no weights
  (parameter-free) regardless of num-heads, so it's `nil` here like any other
  parameterless layer."
  ([backend model* seed] (random-weights backend model* seed {}))
  ([backend model* seed {:keys [dtype] :or {dtype :f32}}]
  (let [lyrs (model/execution-layers model*)
        seed* (atom (long seed))
        next-vec! (fn [n] (let [xs (lcg-seq @seed* n)] (swap! seed* + n) xs))
        upload (fn [xs shape] (arr/from-vec backend xs shape dtype))]
    (mapv (fn [lyr]
            (let [t' (model/layer-type lyr) a (model/layer-args lyr)]
              (case t'
                :linear (let [[in out] a]
                          {:w (upload (next-vec! (* in out)) [in out])
                           :b (upload (next-vec! out) [out])})
                :conv2d (let [[in-ch out-ch k _stride _padding _dilation groups] a
                              groups (or groups 1)
                              in-per-group (quot in-ch groups)
                              [kh kw] (if (sequential? k) k [k k])]
                          {:w (upload (next-vec! (* out-ch in-per-group kh kw))
                                      [out-ch in-per-group kh kw])
                           :b (upload (next-vec! out-ch) [out-ch])})
                :groupnorm (let [[_groups channels] a]
                             {:w (upload (repeat channels 1.0) [channels])
                              :b (upload (repeat channels 0.0) [channels])})
                :layernorm (let [[features] a]
                             {:w (upload (repeat features 1.0) [features])
                              :b (upload (repeat features 0.0) [features])})
                :embedding (let [[num-embeddings dim] a]
                             {:w (upload (next-vec! (* num-embeddings dim))
                                         [num-embeddings dim])})
                :rmsnorm (let [[features] a]
                           {:w (upload (repeat features 1.0) [features])})
                :multihead-attention
                (let [[embed _heads] a
                      matrix (fn [] (upload (next-vec! (* embed embed)) [embed embed]))
                      bias (fn [] (upload (next-vec! embed) [embed]))]
                  {:qw (matrix) :qb (bias) :kw (matrix) :kb (bias)
                   :vw (matrix) :vb (bias) :ow (matrix) :ob (bias)})
                :llama-block
                (let [[embed heads hidden opts] a
                      kv-heads (long (or (:kv-heads opts) heads))
                      kv-embed (* kv-heads (quot embed heads))
                      matrix (fn [in out]
                               (upload (next-vec! (* in out)) [in out]))]
                  {:attn-norm (upload (repeat embed 1.0) [embed])
                   :qw (matrix embed embed) :kw (matrix embed kv-embed)
                   :vw (matrix embed kv-embed) :ow (matrix embed embed)
                   :ffn-norm (upload (repeat embed 1.0) [embed])
                   :gate (matrix embed hidden) :up (matrix embed hidden)
                   :down (matrix hidden embed)})
                :lm-head (let [[embed vocab] a]
                           {:w (upload (next-vec! (* embed vocab)) [embed vocab])})
                nil)))
          lyrs))))

;; --- forward pass --------------------------------------------------------------

(defn- layer-forward
  "Apply one layer to `x` (an NDArray, batch-first), given its weights entry
  (or nil). Throws on any layer type not in this backend's documented scope."
  [lyr weights x runtime-options]
  (let [t' (model/layer-type lyr) largs (model/layer-args lyr)]
    (case t'
      :linear (t/add (matmul-weight x (:w weights)) (:b weights))
      :relu   (nm/relu x)
      :silu   (t/silu x)
      :sigmoid (nm/sigmoid x)
      :tanh (nm/tanh x)
      :gelu (nm/gelu x)
      :softmax (t/softmax x)
      :flatten (t/reshape x (flatten-batch-shape (:shape x)))
      :attention (let [num-heads (if (and (vector? largs) (seq largs)) (first largs) 1)]
                   (when-not (= 2 (count (:shape x)))
                     (throw (ex-info "torch.num-backend: :attention expects [sequence embedding]"
                                     {:shape (:shape x)})))
                   (t/multi-head-attention x x x num-heads))
      :multihead-attention
      (let [[embed num-heads opts] largs
            context (or (:context runtime-options) x)
            merged-options (merge opts (dissoc runtime-options :context))
            rope? (:rope? merged-options)
            rope-options {:theta (or (:rope-theta merged-options) 10000.0)
                          :position-offset (or (:position-offset merged-options) 0)}
            key-rope-options (assoc rope-options :position-offset
                                    (or (:context-position-offset merged-options)
                                        (:position-offset rope-options)))
            attention-options (apply dissoc merged-options
                                     [:rope? :rope-theta :position-offset
                                      :context-position-offset])
            layout (fn [source]
                     (let [shape (:shape source) rank (count shape)
                           [batch sequence] (if (= rank 3) (take 2 shape)
                                                [1 (first shape)])]
                       {:rank rank :batch batch :sequence sequence
                        :flat (if (= rank 3)
                                (t/reshape source [(* batch sequence) embed])
                                source)}))
            query-layout (layout x) context-layout (layout context)
            restore (fn [array {:keys [rank batch sequence]}]
                      (if (= rank 3)
                        (t/reshape array [batch sequence embed]) array))
            project (fn [source-layout wk bk]
                      (restore
                       (t/add (matmul-weight (:flat source-layout) (get weights wk))
                              (get weights bk))
                       source-layout))
            q0 (project query-layout :qw :qb)
            k0 (project context-layout :kw :kb)
            q (if rope? (t/rotary-embedding q0 num-heads rope-options) q0)
            k (if rope? (t/rotary-embedding k0 num-heads key-rope-options) k0)
            v (project context-layout :vw :vb)
            attended (if (seq attention-options)
                       (t/multi-head-attention q k v num-heads attention-options)
                       (t/multi-head-attention q k v num-heads))
            attended-flat (if (= 3 (:rank query-layout))
                             (t/reshape attended
                                        [(* (:batch query-layout)
                                            (:sequence query-layout)) embed])
                             attended)]
        (restore (t/add (matmul-weight attended-flat (:ow weights)) (:ob weights))
                 query-layout))
      :conv2d (let [[_in _out _k stride padding dilation groups] largs
                    weight (:w weights)
                    weight (if (= 2 (count (:shape weight)))
                             (t/reshape weight (into [1 1] (:shape weight)))
                             weight)]
                (t/conv2d-nchw x weight (:b weights)
                               {:stride (or stride 1)
                                :padding (or padding 0)
                                :dilation (or dilation 1)
                                :groups (or groups 1)}))
      :groupnorm (let [[groups _channels eps] largs]
                   (t/group-norm-nchw x groups (:w weights) (:b weights)
                                      (or eps 1.0e-5)))
      :layernorm (t/layer-norm-last x (:w weights) (:b weights) 1.0e-5)
      :embedding (if (quantized/table? (:w weights))
                   (quantized/embedding x (:w weights))
                   (t/embedding x (:w weights)))
      :rmsnorm (let [[_features eps] largs]
                 (t/rms-norm-last x (:w weights) (or eps 1.0e-5)))
      :llama-block
      (let [[embed heads hidden opts] largs
            opts (merge {:causal? true :rope? true} opts runtime-options)
            kv-heads (long (or (:kv-heads opts) heads))
            kv-embed (* kv-heads (quot embed heads))
            eps (or (:eps opts) 1.0e-5)
            rank (count (:shape x))
            [batch sequence] (if (= rank 3) (take 2 (:shape x)) [1 (first (:shape x))])
            flatten (fn [a] (if (= rank 3) (t/reshape a [(* batch sequence) (last (:shape a))]) a))
            restore (fn [a features]
                      (if (= rank 3) (t/reshape a [batch sequence features]) a))
            linear (fn [a w out]
                     (restore (matmul-weight (flatten a) w) out))
            normalized (t/rms-norm-last x (:attn-norm weights) eps)
            q0 (linear normalized (:qw weights) embed)
            k0 (linear normalized (:kw weights) kv-embed)
            v (linear normalized (:vw weights) kv-embed)
            rope-opts {:theta (or (:rope-theta opts) 10000.0)
                       :position-offset (or (:position-offset opts) 0)}
            q (t/rotary-embedding q0 heads rope-opts)
            k (t/rotary-embedding k0 kv-heads rope-opts)
            attended (t/multi-head-attention q k v heads
                                             {:causal? true :kv-heads kv-heads})
            attention-output (linear attended (:ow weights) embed)
            residual (t/add x attention-output)
            ffn-input (t/rms-norm-last residual (:ffn-norm weights) eps)
            gate (t/silu (linear ffn-input (:gate weights) hidden))
            up (linear ffn-input (:up weights) hidden)
            down (linear (nm/mul gate up) (:down weights) embed)]
        (t/add residual down))
      :lm-head
      (let [[_embed vocab] largs
            shape (:shape x) rank (count shape)]
        (if (= rank 3)
          (let [[batch sequence _] shape]
            (t/reshape (matmul-weight (t/reshape x [(* batch sequence) (last shape)])
                                  (:w weights))
                       [batch sequence vocab]))
          (matmul-weight x (:w weights))))
      (throw (ex-info (str "torch.num-backend: layer type not supported: " t')
                      {:layer lyr :supported #{:linear :relu :silu :sigmoid :tanh :gelu :softmax :flatten :conv2d :layernorm :rmsnorm :embedding :llama-block :lm-head
                                               :groupnorm :attention
                                               :multihead-attention}})))))

(defn multihead-attention-step
  "Incrementally execute one learned MultiheadAttention layer for exactly one
  new token per batch. Returns `{:output array :cache {:key :value :length}}`.
  Cached K is already RoPE-rotated and both K/V remain on the input backend;
  callers may release the previous cache after the returned work is complete.

  `input` is `[1 embed]` or `[batch 1 embed]`. `cache` is nil for the first
  token. Layer options are merged with model options and support RoPE."
  ([layer weights input cache]
   (multihead-attention-step layer weights input cache {}))
  ([layer weights input cache runtime-options]
   (let [layer-type (model/layer-type layer)
         [embed heads opts] (model/layer-args layer)
         shape (:shape input) rank (count shape)
         [batch sequence] (if (= rank 3) (take 2 shape) [1 (first shape)])]
     (when-not (and (= layer-type :multihead-attention)
                    (#{2 3} rank) (= sequence 1) (= embed (last shape)))
       (throw (ex-info "incremental attention requires one-token rank-2/3 input"
                       {:layer layer :shape shape})))
     (let [merged (merge opts runtime-options)
           old-length (long (or (:length cache) 0))
           base-offset (long (or (:position-offset merged) 0))
           rope-options {:theta (or (:rope-theta merged) 10000.0)
                         :position-offset (+ base-offset old-length)}
           layout {:rank rank :batch batch :sequence 1
                   :flat (if (= rank 3) (t/reshape input [batch embed]) input)}
           restore (fn [array]
                     (if (= rank 3) (t/reshape array [batch 1 embed]) array))
           project (fn [wk bk]
                     (restore (t/add (matmul-weight (:flat layout) (get weights wk))
                                     (get weights bk))))
           query0 (project :qw :qb)
           key0 (project :kw :kb)
           value (project :vw :vb)
           query (if (:rope? merged)
                   (t/rotary-embedding query0 heads rope-options) query0)
           key (if (:rope? merged)
                 (t/rotary-embedding key0 heads rope-options) key0)
           fixed? (:fixed-capacity? cache)
           cache-batch (long (or (:batch cache) 1))
           _ (when (and fixed? (not= batch cache-batch))
               (throw (ex-info "fixed-capacity KV cache batch size mismatch"
                               {:input-batch batch :cache-batch cache-batch})))
           _ (when (and fixed? (>= old-length (:capacity cache)))
               (throw (ex-info "fixed-capacity KV cache is full"
                               {:length old-length :capacity (:capacity cache)})))
           axis (if (= rank 3) 1 0)
           active-fixed (fn [backing source]
                          (t/copy-into! backing source
                                        (* old-length batch embed))
                          (if (= rank 3)
                            ;; The backing is token-major, so each append is one
                            ;; contiguous `[batch,embed]` device copy. Materialize
                            ;; a batch-first view only for the attention kernel.
                            (t/transpose
                             (assoc backing :shape [(inc old-length) batch embed])
                             [1 0 2])
                            (assoc backing :shape [(inc old-length) embed])))
           all-key (if fixed?
                     (active-fixed (:key cache) key)
                     (if-let [old (:key cache)] (t/cat [old key] axis) key))
           all-value (if fixed?
                       (active-fixed (:value cache) value)
                       (if-let [old (:value cache)] (t/cat [old value] axis) value))
           attended (t/multi-head-attention query all-key all-value heads)
           _ (when (and fixed? (= rank 3))
               (arr/release-all! [all-key all-value]))
           attended-flat (if (= rank 3) (t/reshape attended [batch embed]) attended)
           output (restore (t/add (matmul-weight attended-flat (:ow weights))
                                  (:ob weights)))]
       {:output output
        :cache (if fixed?
                 (assoc cache :length (inc old-length))
                 {:key all-key :value all-value :length (inc old-length)})}))))

(declare release-kv-cache!)

(defn init-kv-cache
  "Preallocate a fixed-capacity KV cache on `backend`. No backing buffer is
  reallocated while decoding up to `capacity` tokens. For `batch > 1`, storage
  is token-major so each decode step is one contiguous device-to-device copy."
  ([backend capacity embed]
   (init-kv-cache backend capacity embed :f32 1))
  ([backend capacity embed dtype]
   (init-kv-cache backend capacity embed dtype 1))
  ([backend capacity embed dtype batch]
   (when-not (and (pos-int? capacity) (pos-int? embed) (pos-int? batch))
     (throw (ex-info "KV cache capacity, embedding, and batch must be positive integers"
                     {:capacity capacity :embed embed :batch batch})))
   (let [shape (if (= batch 1) [capacity embed] [capacity batch embed])]
     {:key (arr/zeros backend shape dtype)
      :value (arr/zeros backend shape dtype)
      :length 0 :capacity capacity :embed embed :batch batch
      :fixed-capacity? true})))

(defn llama-block-step
  "Incrementally execute one token per batch through a Llama block. Input is
  `[1,embed]` for batch 1 or batch-first `[batch,1,embed]`."
  [layer weights input cache]
  (let [[embed heads _hidden opts] (model/layer-args layer)
        opts (merge {:causal? true :rope? true} opts)
        kv-heads (long (or (:kv-heads opts) heads))
        kv-embed (* kv-heads (quot embed heads))
        shape (:shape input)
        rank (count shape)
        batch (if (= rank 3) (long (first shape)) 1)
        length (long (or (:length cache) 0))
        eps (or (:eps opts) 1.0e-5)]
    (when-not (and (= :llama-block (model/layer-type layer))
                   (or (= [1 embed] shape)
                       (and (= rank 3) (= 1 (second shape))
                            (= embed (last shape)))))
      (throw (ex-info "llama block step requires [1,embed] or [batch,1,embed] input"
                      {:shape shape :embed embed})))
    (let [linear (fn [array weight-key]
                   (if (= rank 3)
                     (let [flat (t/reshape array [batch (last (:shape array))])
                           projected (matmul-weight flat (get weights weight-key))]
                       (t/reshape projected [batch 1 (last (:shape projected))]))
                     (matmul-weight array (get weights weight-key))))
          normalized (t/rms-norm-last input (:attn-norm weights) eps)
          q0 (linear normalized :qw) k0 (linear normalized :kw)
          value (linear normalized :vw)
          rope-opts {:theta (or (:rope-theta opts) 10000.0)
                     :position-offset (+ (or (:position-offset opts) 0) length)}
          query (t/rotary-embedding q0 heads rope-opts)
          key (t/rotary-embedding k0 kv-heads rope-opts)
          fixed? (:fixed-capacity? cache)
          cache-batch (long (or (:batch cache) 1))
          _ (when (and fixed? (not= batch cache-batch))
              (throw (ex-info "fixed-capacity Llama KV cache batch size mismatch"
                              {:input-batch batch :cache-batch cache-batch})))
          _ (when (and fixed? (>= length (:capacity cache)))
              (throw (ex-info "Llama KV cache is full"
                              {:length length :capacity (:capacity cache)})))
          active-fixed (fn [backing source]
                         (t/copy-into! backing source (* length batch kv-embed))
                         (if (= rank 3)
                           (t/transpose
                            (assoc backing :shape [(inc length) batch kv-embed])
                            [1 0 2])
                           (assoc backing :shape [(inc length) kv-embed])))
          axis (if (= rank 3) 1 0)
          all-key (if fixed?
                    (active-fixed (:key cache) key)
                    (if-let [old (:key cache)] (t/cat [old key] axis) key))
          all-value (if fixed?
                      (active-fixed (:value cache) value)
                      (if-let [old (:value cache)] (t/cat [old value] axis) value))
          attended (t/multi-head-attention query all-key all-value heads
                                           {:kv-heads kv-heads})
          _ (when (and fixed? (= rank 3))
              (arr/release-all! [all-key all-value]))
          attention-output (linear attended :ow)
          residual (t/add input attention-output)
          ffn-input (t/rms-norm-last residual (:ffn-norm weights) eps)
          gate-projection (linear ffn-input :gate)
          gate (t/silu gate-projection)
          up (linear ffn-input :up)
          gated (nm/mul gate up)
          down (linear gated :down)
          output (t/add residual down)
          ;; Every command consuming these buffers has already been encoded.
          ;; The output and cache are the only values escaping this scope.
          _ (arr/release-all!
             (cond-> [normalized q0 k0 query attended attention-output
                      ffn-input gate-projection gate up gated down residual]
               fixed? (into [key value])))]
      {:output output
       :cache (if fixed? (assoc cache :length (inc length))
                  {:key all-key :value all-value :length (inc length)
                   :batch batch})})))

(defn llama-block-paged-step
  "Execute one batch-1 Llama token using a `torch.paged-runtime` physical cache.
  The storage callback owns device payloads; the runtime owns the logical block
  table, prefix sharing, COW, and sequence length."
  [layer weights input runtime* sequence-id]
  (let [[embed heads _hidden opts] (model/layer-args layer)
        opts (merge {:causal? true :rope? true} opts)
        kv-heads (long (or (:kv-heads opts) heads))
        {:keys [length]} (get-in runtime* [:pool :sequences sequence-id])
        eps (or (:eps opts) 1.0e-5)]
    (when-not (and (= :llama-block (model/layer-type layer))
                   (= [1 embed] (:shape input))
                   (some? length))
      (throw (ex-info "paged Llama step requires a registered sequence and [1,embed] input"
                      {:shape (:shape input) :embed embed
                       :sequence-id sequence-id})))
    (let [linear #(matmul-weight %1 (get weights %2))
          normalized (t/rms-norm-last input (:attn-norm weights) eps)
          q0 (linear normalized :qw)
          k0 (linear normalized :kw)
          value (linear normalized :vw)
          rope-options {:theta (or (:rope-theta opts) 10000.0)
                        :position-offset (+ (or (:position-offset opts) 0)
                                            length)}
          query (t/rotary-embedding q0 heads rope-options)
          key (t/rotary-embedding k0 kv-heads rope-options)
          appended (paged/append-kv! runtime* sequence-id key value)
          runtime* (:runtime appended)
          attended (paged/attention runtime* sequence-id query)
          attention-output (linear attended :ow)
          residual (t/add input attention-output)
          ffn-input (t/rms-norm-last residual (:ffn-norm weights) eps)
          gate-projection (linear ffn-input :gate)
          gate (t/silu gate-projection)
          up (linear ffn-input :up)
          gated (nm/mul gate up)
          down (linear gated :down)
          output (t/add residual down)
          _ (arr/release-all!
             [normalized q0 k0 value query key attended attention-output
              ffn-input gate-projection gate up gated down residual])]
      {:output output :runtime runtime* :placement (:placement appended)})))

(defn llama-block-paged-batch-step
  "Execute one token for several paged sequences. Projection/FFN tensors remain
  batched and the storage runtime performs one fused multi-request attention."
  [layer weights input runtime* sequence-ids]
  (let [[embed heads _hidden opts] (model/layer-args layer)
        opts (merge {:causal? true :rope? true} opts)
        kv-heads (long (or (:kv-heads opts) heads))
        shape (:shape input)
        batch (first shape)
        lengths (mapv #(get-in runtime* [:pool :sequences % :length]) sequence-ids)
        eps (or (:eps opts) 1.0e-5)]
    (when-not (and (= :llama-block (model/layer-type layer))
                   (= [batch 1 embed] shape) (= batch (count sequence-ids))
                   (every? some? lengths))
      (throw (ex-info "paged batched Llama step requires registered sequences and [batch,1,embed]"
                      {:shape shape :sequence-ids sequence-ids})))
    (let [linear (fn [array weight-key]
                   (let [flat (t/reshape array [batch (last (:shape array))])
                         projected (matmul-weight flat (get weights weight-key))]
                     (t/reshape projected [batch 1 (last (:shape projected))])))
          normalized (t/rms-norm-last input (:attn-norm weights) eps)
          q0 (linear normalized :qw)
          k0 (linear normalized :kw)
          value (linear normalized :vw)
          ;; RoPE offsets differ per request. Apply it row-wise, then concatenate;
          ;; the expensive attention itself remains one fused dispatch.
          rotate-rows
          (fn [array head-count]
            (let [source-rows (mapv #(t/slice-axis array 0 % (inc %))
                                    (range batch))
                  rotated (mapv (fn [index row]
                                  (t/rotary-embedding
                                   row head-count
                                   {:theta (or (:rope-theta opts) 10000.0)
                                    :position-offset
                                    (+ (or (:position-offset opts) 0)
                                       (nth lengths index))}))
                                (range batch) source-rows)
                  output (t/cat rotated 0)]
              (arr/release-all! (concat source-rows rotated))
              output))
          query (rotate-rows q0 heads)
          key (rotate-rows k0 kv-heads)
          key-rows (mapv #(t/slice-axis key 0 % (inc %)) (range batch))
          value-rows (mapv #(t/slice-axis value 0 % (inc %)) (range batch))
          appended (paged/append-kv-many! runtime* sequence-ids
                                            key-rows value-rows)
          _ (arr/release-all! (concat key-rows value-rows))
          runtime* (:runtime appended)
          attended (paged/attention-many runtime* sequence-ids query)
          attention-output (linear attended :ow)
          residual (t/add input attention-output)
          ffn-input (t/rms-norm-last residual (:ffn-norm weights) eps)
          gate-projection (linear ffn-input :gate)
          gate (t/silu gate-projection)
          up (linear ffn-input :up)
          gated (nm/mul gate up)
          down (linear gated :down)
          output (t/add residual down)
          _ (arr/release-all!
             [normalized q0 k0 value query key attended attention-output
              ffn-input gate-projection gate up gated down residual])]
      {:output output :runtime runtime* :placements (:placements appended)})))

(defn llama-lm-paged-step
  "Execute one token ID through Embedding, paged Llama blocks, final norm, and
  LM head. `runtimes` contains one independently backed paged runtime per block."
  [model* weights token runtimes sequence-id]
  (let [layers (model/execution-layers model*)]
    (when-not (= (count layers) (count weights))
      (throw (ex-info "Llama LM layers and weights must align" {})))
    (loop [remaining (seq (map vector layers weights))
           value token value-owned? false runtime-index 0 updated [] placements []]
      (if-let [[layer weight] (first remaining)]
        (if (= :llama-block (model/layer-type layer))
          (let [runtime* (nth runtimes runtime-index nil)
                _ (when-not runtime*
                    (throw (ex-info "missing per-block paged runtime"
                                    {:runtime-index runtime-index})))
                step (llama-block-paged-step layer weight value runtime*
                                              sequence-id)
                _ (when value-owned? (arr/release! value))]
            (recur (next remaining) (:output step) true (inc runtime-index)
                   (conj updated (:runtime step))
                   (conj placements (:placement step))))
          (let [output (layer-forward layer weight value nil)
                _ (when value-owned? (arr/release! value))]
            (recur (next remaining) output true runtime-index updated placements)))
        (do
          (when-not (= runtime-index (count runtimes))
            (throw (ex-info "unused paged Llama runtimes"
                            {:used runtime-index :provided (count runtimes)})))
          {:logits value :runtimes updated :placements placements})))))

(defn llama-lm-paged-batch-step
  "Batched twin of `llama-lm-paged-step`. `token` is `[batch,1]`; each block
  consumes one shared runtime and performs one multi-request paged attention."
  [model* weights token runtimes sequence-ids]
  (let [layers (model/execution-layers model*)]
    (when-not (and (= (count layers) (count weights))
                   (= (first (:shape token)) (count sequence-ids)))
      (throw (ex-info "batched paged Llama inputs do not align" {})))
    (loop [remaining (seq (map vector layers weights))
           value token value-owned? false runtime-index 0 updated [] placements []]
      (if-let [[layer weight] (first remaining)]
        (if (= :llama-block (model/layer-type layer))
          (let [runtime* (nth runtimes runtime-index nil)
                _ (when-not runtime*
                    (throw (ex-info "missing per-block paged runtime"
                                    {:runtime-index runtime-index})))
                step (llama-block-paged-batch-step
                      layer weight value runtime* sequence-ids)
                _ (when value-owned? (arr/release! value))]
            (recur (next remaining) (:output step) true (inc runtime-index)
                   (conj updated (:runtime step))
                   (conj placements (:placements step))))
          (let [output (layer-forward layer weight value nil)
                _ (when value-owned? (arr/release! value))]
            (recur (next remaining) output true runtime-index updated placements)))
        (do
          (when-not (= runtime-index (count runtimes))
            (throw (ex-info "unused paged Llama runtimes"
                            {:used runtime-index :provided (count runtimes)})))
          {:logits value :runtimes updated :placements placements})))))

(defn init-llama-caches
  "Preallocate one fixed KV cache per Llama block in `model*`."
  ([backend model* capacity]
   (init-llama-caches backend model* capacity :f32 1))
  ([backend model* capacity dtype]
   (init-llama-caches backend model* capacity dtype 1))
  ([backend model* capacity dtype batch]
   (->> (model/execution-layers model*)
        (filter #(= :llama-block (model/layer-type %)))
        (mapv (fn [layer]
                (let [[embed heads _hidden opts] (model/layer-args layer)
                      kv-heads (long (or (:kv-heads opts) heads))]
                  (init-kv-cache backend capacity
                                 (* kv-heads (quot embed heads)) dtype batch)))))))

(defn llama-model-step
  "Run one token through every Llama block and update each layer's KV cache."
  [model* weights input caches]
  (let [layers (model/execution-layers model*)]
    (when-not (= (count layers) (count weights) (count caches))
      (throw (ex-info "Llama layers, weights, and caches must align"
                      {:layers (count layers) :weights (count weights)
                       :caches (count caches)})))
    (reduce (fn [{:keys [output caches]} [layer weight cache]]
              (let [step (llama-block-step layer weight output cache)]
                {:output (:output step) :caches (conj caches (:cache step))}))
            {:output input :caches []}
            (map vector layers weights caches))))

(defn release-llama-caches!
  [caches]
  (doseq [cache caches] (release-kv-cache! cache))
  nil)

(defn llama-lm-step
  "Execute one token ID through Embedding, any number of Llama blocks, final
  normalization, and LM head. Returns vocabulary logits and updated block
  caches."
  [model* weights token caches]
  (let [layers (model/execution-layers model*)]
    (when-not (= (count layers) (count weights))
      (throw (ex-info "Llama LM layers and weights must align" {})))
    (loop [remaining (seq (map vector layers weights))
           value token value-owned? false cache-index 0 updated []]
      (if-let [[layer weight] (first remaining)]
        (if (= :llama-block (model/layer-type layer))
          (let [cache (nth caches cache-index nil)
                _ (when-not cache
                    (throw (ex-info "missing per-block KV cache"
                                    {:cache-index cache-index})))
                step (llama-block-step layer weight value cache)
                _ (when value-owned? (arr/release! value))]
            (recur (next remaining) (:output step) true (inc cache-index)
                   (conj updated (:cache step))))
          (let [output (layer-forward layer weight value nil)
                _ (when value-owned? (arr/release! value))]
            (recur (next remaining) output true cache-index updated)))
        (do
          (when-not (= cache-index (count caches))
            (throw (ex-info "unused Llama KV caches"
                            {:used cache-index :provided (count caches)})))
          {:logits value :caches updated})))))

(defn llama-embedding-step
  "Execute one token through the embedding, Llama blocks, and final norm,
  stopping before the LM head. Returns the final hidden row and updated caches."
  [model* weights token caches]
  (let [layers (model/execution-layers model*)]
    (when-not (= (count layers) (count weights))
      (throw (ex-info "Llama embedding layers and weights must align" {})))
    (loop [remaining (seq (map vector layers weights))
           value token value-owned? false cache-index 0 updated []]
      (if-let [[layer weight] (first remaining)]
        (cond
          (= :lm-head (model/layer-type layer))
          (do
            (when-not (= cache-index (count caches))
              (throw (ex-info "unused Llama embedding caches"
                              {:used cache-index :provided (count caches)})))
            {:embedding value :caches updated})

          (= :llama-block (model/layer-type layer))
          (let [cache (nth caches cache-index nil)
                _ (when-not cache
                    (throw (ex-info "missing per-block embedding KV cache"
                                    {:cache-index cache-index})))
                step (llama-block-step layer weight value cache)
                _ (when value-owned? (arr/release! value))]
            (recur (next remaining) (:output step) true (inc cache-index)
                   (conj updated (:cache step))))

          :else
          (let [output (layer-forward layer weight value nil)
                _ (when value-owned? (arr/release! value))]
            (recur (next remaining) output true cache-index updated)))
        (throw (ex-info "Llama embedding model has no LM-head boundary" {}))))))

(defn release-kv-cache!
  "Explicitly release the two device arrays in a superseded KV cache."
  [cache]
  (doseq [array [(:key cache) (:value cache)] :when array]
    (arr/release! array))
  nil)

(defn release-weights!
  "Release every distinct tensor handle in a model weight vector exactly once.
  This is safe for tied embedding/output views that share packed GPU storage."
  [weights]
  (arr/release-all! (mapcat (fn [entry] (when (map? entry) (vals entry))) weights)))

(defn num-backend
  "An `IBackend` running `forward` through `backend` (a `num.protocol/IBackend`
  — typically `(num.cpu/cpu-backend)` or a synchronous WgslBackend), using
  `weights` (see `random-weights`, or hand-supply your own in the same shape)
  for the model it's about to run. One `num-backend` is good for one
  model+weights pairing (weights are matched to the model by layer index)."
  ([backend weights] (num-backend backend weights {}))
  ([backend weights {:keys [autocast-dtype]}]
   (when (and autocast-dtype (not (contains? #{:f16 :bf16} autocast-dtype)))
     (throw (ex-info "torch.num-backend: autocast dtype must be :f16 or :bf16"
                     {:dtype autocast-dtype})))
   (let [cast-weight (fn [weight]
                       (when weight
                         (into {} (map (fn [[key value]]
                                         [key (arr/cast value autocast-dtype)])) weight)))
         autocast-weights (if autocast-dtype (mapv cast-weight weights) weights)
         run* (fn [model* input options]
         (let [lyrs (model/execution-layers model*)
               layer-options (or (:layer-options options)
                                 (repeat (count lyrs) nil))
               typed-attention? (and (= :f16 autocast-dtype)
                                     (satisfies? p/IDTypeTensorOps backend))
               supported (cond-> #{:linear :relu :silu :sigmoid :tanh :gelu
                                   :flatten :conv2d :groupnorm :layernorm
                                   :rmsnorm :embedding}
                           typed-attention? (conj :attention :multihead-attention))
               unsupported (when autocast-dtype
                             (seq (remove supported (map model/layer-type lyrs))))]
           (when unsupported
             (throw (ex-info
                     "torch.num-backend: autocast layer lacks a typed backend kernel"
                     {:unsupported (vec unsupported) :supported supported
                      :dtype autocast-dtype})))
           (when (and autocast-dtype
                      (some (fn [[lyr options]]
                              (and (= :multihead-attention (model/layer-type lyr))
                                   (:key-padding-mask options)))
                            (map vector lyrs layer-options)))
             (throw (ex-info
                     "torch.num-backend: F16 attention does not support key-padding-mask"
                     {:dtype autocast-dtype})))
           (when-not (= (count lyrs) (count layer-options))
             (throw (ex-info "torch.num-backend: layer-options count mismatch"
                             {:layers (count lyrs)
                              :layer-options (count layer-options)})))
           (let [x0 (if (= backend (:backend input)) input
                      (arr/from-vec backend (arr/->vec input) (:shape input)))
                 embedding-input? (= :embedding (model/layer-type (first lyrs)))
                 x0 (if (and autocast-dtype (not embedding-input?))
                      (arr/cast x0 autocast-dtype) x0)]
             (reduce (fn [x [lyr w runtime-options]]
                       (layer-forward lyr w x runtime-options))
                     x0
                     (map vector lyrs autocast-weights layer-options)))))]
     (reify
       ports/IBackend
       (forward [_ model* input] (run* model* input {}))
       ports/IRuntimeBackend
       (forward-with-options [_ model* input options]
         (run* model* input options))))))
