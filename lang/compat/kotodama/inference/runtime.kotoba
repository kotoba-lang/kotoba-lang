(ns kotodama.inference.runtime
  "Portable .cljc model-runtime data. This is the kotodama equivalent of the
  public surface people normally reach for through model runtimes: model,
  session, generate, and forward are plain EDN maps. Model graphs are torch-clj
  data; tensor execution is num-clj over an injected backend such as WGSL/WebGPU."
  (:require [torch.model :as torch]
            [torch.device-profile :as device-profile]))

(def supported-backends #{:webgpu :webgl :wasm :native})
(def supported-adapter-kinds #{:browser :wasm :native :remote :edge})
(def supported-runtimes #{:torch-transformer
                          :torch-diffusion
                          :torch-audio})
(def supported-compute-backends #{:num/cpu :num/wgsl :num/webgpu :num/webgl :num/wasm :num/native})
(def supported-tasks #{:text-generation
                       :text-generation/mtp
                       :image-generation
                       :image-to-image
                       :speech-to-text
                       :audio-transcription
                       :audio-translation})
(def supported-schedulers #{:diffusion/ddim
                            :diffusion/euler
                            :diffusion/euler-a
                            :diffusion/dpm-solver})
(def supported-execution-intents #{:latency :throughput :fallback})

(def distributed-defaults
  {:kotodama/distributed? false
   :kotodama/shard-strategy :none
   :kotodama/tensor-parallel-size 1
   :kotodama/pipeline-parallel-size 1
   :kotodama/paged-kv-cache? false})

(def default-generation
  {:kotodama/max-new-tokens 64
   :kotodama/do-sample? false})

(defn normalize-backend [backend]
  (let [b (keyword (or backend :webgpu))]
    (if (contains? supported-backends b)
      b
      (throw (ex-info (str "unsupported inference backend: " backend)
                      {:kotodama/backend backend})))))

(defn generation
  "Generation options as data. Keys stay namespaced so hosts can map them to
  their local decoder implementation."
  ([] default-generation)
  ([opts] (merge default-generation opts)))

(defn performance
  "Resolve a physical adapter into portable serving data.

  Hosts may override the returned measurements after a local benchmark, but
  the execution intent and hardware prerequisites remain explicit in the
  runtime spec."
  ([adapter] (performance adapter :latency))
  ([adapter intent]
   (when-not (contains? supported-execution-intents intent)
     (throw (ex-info "unsupported inference execution intent" {:intent intent})))
   (let [profile (device-profile/serving-profile adapter intent)]
     {:kotodama/execution-intent intent
      :kotodama/device (:torch/device profile)
      :kotodama/max-running (:torch/max-running profile)
      :kotodama/batch-size (:torch/batch-size profile)
      :kotodama/ubatch-size (:torch/ubatch-size profile)
      :kotodama/flash-attention? (:torch/flash-attention? profile)
      :kotodama/speculative (:torch/speculative profile)
      :kotodama/host-prerequisites (:torch/host-prerequisites profile)})))

(defn distributed
  "Distributed inference execution options, intentionally expressed as data so
  vLLM-like hosts can bind schedulers, paged KV cache, and shard placement
  without changing kotodama's portable runtime surface."
  ([] distributed-defaults)
  ([opts] (merge distributed-defaults opts)))

(defn normalize-compute-backend [backend]
  (let [b (keyword (or backend :num/wgsl))]
    (if (contains? supported-compute-backends b)
      b
      (throw (ex-info (str "unsupported compute backend: " backend)
                      {:kotodama/compute-backend backend})))))

(defn transformer-block
  "A torch-clj EDN approximation of a decoder transformer block. It is a graph
  contract, not a fused kernel: hosts lower these layer names to num-clj ops or
  custom torch layers."
  [{:keys [hidden-size intermediate-size vocab-size]
    :or {hidden-size 768 intermediate-size 3072 vocab-size 50257}}]
  (torch/sequential
    (torch/embedding vocab-size hidden-size)
    (torch/layernorm hidden-size)
    (torch/layer :causal-self-attention {:hidden-size hidden-size})
    (torch/layernorm hidden-size)
    (torch/linear hidden-size intermediate-size)
    (torch/gelu)
    (torch/linear intermediate-size hidden-size)
    (torch/layernorm hidden-size)
    (torch/linear hidden-size vocab-size)
    (torch/softmax)))

(defn transformer
  "A decoder-transformer text runtime spec backed by torch-clj model data and
  num-clj compute. Browser WebGPU hosts should bind `:num/wgsl` or
  `:num/webgpu`; native hosts may bind native num backends. WebGL is kept as a
  host compatibility target, but the portable primary path is WGSL/WebGPU."
  ([model] (transformer model {}))
  ([model opts]
   (let [backend (normalize-backend (:kotodama/backend opts :webgpu))
         compute-backend (normalize-compute-backend (:kotodama/compute-backend opts :num/wgsl))
         model-graph (or (:kotodama/model-graph opts)
                         (transformer-block {:hidden-size (:kotodama/hidden-size opts 768)
                                             :intermediate-size (:kotodama/intermediate-size opts 3072)
                                             :vocab-size (:kotodama/vocab-size opts 50257)}))]
     (merge {:kotodama/runtime :torch-transformer
             :kotodama/model model
             :kotodama/task (:kotodama/task opts :text-generation)
             :kotodama/backend backend
             :kotodama/compute-backend compute-backend
             :kotodama/model-graph model-graph}
            (select-keys opts [:kotodama/dtype
                               :kotodama/revision
                               :kotodama/cache-dir
                               :kotodama/local-files-only?
                               :kotodama/tokenizer
                               :kotodama/distributed
                               :kotodama/mtp
                               :kotodama/performance])))))

(defn optimized-transformer
  "Build a transformer spec with a measured hardware serving profile."
  ([model adapter] (optimized-transformer model adapter :latency {}))
  ([model adapter intent] (optimized-transformer model adapter intent {}))
  ([model adapter intent opts]
   (transformer model
                (assoc opts :kotodama/performance
                       (performance adapter intent)))))

(defn mtp-transformer
  "Spec for multi-token prediction LLM execution. Hosts may lower the same
  torch graph to speculative/MTP heads and return multiple draft tokens per
  forward call."
  ([model] (mtp-transformer model {}))
  ([model opts]
   (transformer model
                (merge {:kotodama/task :text-generation/mtp
                        :kotodama/mtp {:kotodama/draft-token-count 4
                                       :kotodama/verify-draft? true}}
                       opts))))

(defn distributed-transformer
  "Spec for vLLM-like distributed LLM execution: tensor/pipeline parallelism,
  paged KV cache, and host scheduling are data, not a separate runtime API."
  ([model] (distributed-transformer model {}))
  ([model opts]
   (let [distributed-opts (merge {:kotodama/distributed? true
                                  :kotodama/shard-strategy :tensor-parallel
                                  :kotodama/paged-kv-cache? true}
                                 (select-keys opts [:kotodama/tensor-parallel-size
                                                    :kotodama/pipeline-parallel-size
                                                    :kotodama/shard-strategy
                                                    :kotodama/paged-kv-cache?])
                                 (:kotodama/distributed opts))]
     (transformer model
                  (assoc opts :kotodama/distributed (distributed distributed-opts))))))

(defn diffusion
  "Diffusion/image generation runtime over a torch-clj graph and num backend.
  ONNX is deliberately not a runtime foundation; hosts lower UNet/VAE/text
  encoder/scheduler components from this EDN contract."
  ([model] (diffusion model {}))
  ([model opts]
   (let [backend (normalize-backend (:kotodama/backend opts :webgpu))
         compute-backend (normalize-compute-backend (:kotodama/compute-backend opts :num/wgsl))
         model-graph (or (:kotodama/model-graph opts)
                         (torch/sequential
                          (torch/layer :text-encoder {:model model})
                          (torch/layer :diffusion-denoiser {:model model})
                          (torch/layer :vae-decoder {:model model})))]
     (merge {:kotodama/runtime :torch-diffusion
             :kotodama/model model
             :kotodama/task (:kotodama/task opts :image-generation)
             :kotodama/backend backend
             :kotodama/compute-backend compute-backend
             :kotodama/model-graph model-graph
             :kotodama/scheduler (:kotodama/scheduler opts :diffusion/euler)}
            (select-keys opts [:kotodama/dtype
                               :kotodama/revision
                               :kotodama/cache-dir
                               :kotodama/local-files-only?
                               :kotodama/tokenizer
                               :kotodama/width
                               :kotodama/height
                               :kotodama/steps
                               :kotodama/guidance-scale])))))

(defn audio
  "Speech/audio runtime, including Whisper-like transcription and translation.
  Hosts bind audio feature extraction and encoder/decoder lowering behind the
  same IModelRuntime port used for text and diffusion."
  ([model] (audio model {}))
  ([model opts]
   (let [backend (normalize-backend (:kotodama/backend opts :webgpu))
         compute-backend (normalize-compute-backend (:kotodama/compute-backend opts :num/wgsl))
         model-graph (or (:kotodama/model-graph opts)
                         (torch/sequential
                          (torch/layer :audio-feature-extractor {:model model})
                          (torch/layer :audio-encoder {:model model})
                          (torch/layer :text-decoder {:model model})))]
     (merge {:kotodama/runtime :torch-audio
             :kotodama/model model
             :kotodama/task (:kotodama/task opts :speech-to-text)
             :kotodama/backend backend
             :kotodama/compute-backend compute-backend
             :kotodama/model-graph model-graph}
            (select-keys opts [:kotodama/dtype
                               :kotodama/revision
                               :kotodama/cache-dir
                               :kotodama/local-files-only?
                               :kotodama/tokenizer
                               :kotodama/sample-rate
                               :kotodama/language
                               :kotodama/translate?])))))

(defn load-op [runtime-spec]
  {:kotodama/op :load
   :kotodama/runtime-spec runtime-spec})

(defn generate-op
  ([session prompt-or-token-ids] (generate-op session prompt-or-token-ids {}))
  ([session prompt-or-token-ids opts]
   {:kotodama/op :generate
    :kotodama/session session
    :kotodama/input prompt-or-token-ids
    :kotodama/generation (generation opts)}))

(defn forward-op
  ([session token-ids] (forward-op session token-ids {}))
  ([session token-ids opts]
   {:kotodama/op :forward
    :kotodama/session session
    :kotodama/input-ids (if (sequential? token-ids)
                           (vec token-ids)
                           token-ids)
    :kotodama/options opts}))

(defn kototama-infer-op
  "The data shape behind kototama's `(llm-infer model prompt)` capability."
  ([model prompt] (kototama-infer-op model prompt {}))
  ([model prompt opts]
   {:kotodama/op :llm-infer
    :kotodama/model model
    :kotodama/prompt prompt
    :kotodama/generation (generation opts)}))

(defn adapter-spec
  "Describe a host adapter that consumes the portable CLJC inference contract.
  Native runtimes are allowed only as adapter-owned implementations outside this
  repository."
  [id opts]
  (merge {:kotodama.adapter/id id
          :kotodama.adapter/kind (:kotodama.adapter/kind opts :native)
          :kotodama.adapter/consumes #{:kotodama/runtime-spec
                                       :kotodama/generation
                                       :kotodama/forward}
          :kotodama.adapter/provides #{:kotodama/session
                                       :kotodama/text
                                       :kotodama/logits}}
         opts))
