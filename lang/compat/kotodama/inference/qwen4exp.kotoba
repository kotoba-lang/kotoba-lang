(ns kotodama.inference.qwen4exp
  "Checkpoint admission for Qwen3.8-Flash-Next / Qwen4Exp MTP execution.

  This namespace consumes config and Safetensors index data, not filenames
  guessed from a quant label. A target checkpoint without its MTP tensors is
  rejected before a host attempts speculative setup."
  (:require [kotoba.lang.text :as str]
            [kotodama.inference.runtime :as runtime]))

(def ^:private mtp-root-tensors
  ["mtp.fc_embedding.weight"
   "mtp.fc_hidden.weight"
   "mtp.hyper_connection_mixer.hc_norm.weight"
   "mtp.hyper_connection_mixer.input_mix_weight_down.weight"
   "mtp.hyper_connection_mixer.input_mix_weight_up.weight"
   "mtp.pre_fc_norm_embedding.weight"
   "mtp.pre_fc_norm_hidden.weight"])

(def ^:private mtp-layer-suffixes
  ["attn_hyper_connection.block_inject_weight.weight"
   "attn_hyper_connection.hc_norm.weight"
   "attn_hyper_connection.input_mix_weight_down.weight"
   "attn_hyper_connection.input_mix_weight_up.weight"
   "mlp.experts.down_proj"
   "mlp.experts.gate_up_proj"
   "mlp.gate.weight"
   "mlp.shared_expert.down_proj.weight"
   "mlp.shared_expert.gate_proj.weight"
   "mlp.shared_expert.up_proj.weight"
   "mlp.shared_expert_gate.weight"
   "mlp_hyper_connection.block_inject_weight.weight"
   "mlp_hyper_connection.hc_norm.weight"
   "mlp_hyper_connection.input_mix_weight_down.weight"
   "mlp_hyper_connection.input_mix_weight_up.weight"
   "self_attn.indexer.index_qk_proj.weight"
   "self_attn.indexer.k_layernorm.weight"
   "self_attn.indexer.q_layernorm.weight"
   "self_attn.k_norm.weight"
   "self_attn.k_proj.weight"
   "self_attn.o_proj.weight"
   "self_attn.q_norm.weight"
   "self_attn.q_proj.weight"
   "self_attn.v_proj.weight"])

(defn- get-key [m k]
  (or (get m k) (get m (keyword k))))

(defn- text-config [config]
  (or (get-key config "text_config") config))

(defn mtp-layer-count [config]
  (let [text (text-config config)
        mtp (get-key text "mtp")]
    (long (or (get-key text "mtp_num_hidden_layers")
              (get-key mtp "num_hidden_layers")
              0))))

(defn required-mtp-tensors [layer-count]
  (into mtp-root-tensors
        (for [layer (range layer-count)
              suffix mtp-layer-suffixes]
          (str "mtp.layers." layer "." suffix))))

(defn required-expert-tensors
  "Packed routed-expert tensors required by the target decoder.

  Qwen4Exp stores the expert axis first. A host may therefore read exactly one
  expert from each packed tensor without materialising the other 511."
  [layer-count]
  (vec
   (mapcat (fn [layer]
             [(str "model.language_model.layers." layer ".mlp.experts.gate_up_proj")
              (str "model.language_model.layers." layer ".mlp.experts.down_proj")])
           (range layer-count))))

(def ^:private decoder-hyper-connection-root-suffixes
  ["hyper_connection_mixer.hc_norm.weight"
   "hyper_connection_mixer.input_mix_weight_down.weight"
   "hyper_connection_mixer.input_mix_weight_up.weight"])

(def ^:private decoder-hyper-connection-layer-suffixes
  ["attn_hyper_connection.block_inject_weight.weight"
   "attn_hyper_connection.hc_norm.weight"
   "attn_hyper_connection.input_mix_weight_down.weight"
   "attn_hyper_connection.input_mix_weight_up.weight"
   "mlp_hyper_connection.block_inject_weight.weight"
   "mlp_hyper_connection.hc_norm.weight"
   "mlp_hyper_connection.input_mix_weight_down.weight"
   "mlp_hyper_connection.input_mix_weight_up.weight"])

(defn required-decoder-hyper-connection-tensors
  "All base-decoder Hyper-Connection tensors, distinct from the MTP head."
  [layer-count]
  (into (mapv #(str "model.language_model." %)
              decoder-hyper-connection-root-suffixes)
        (for [layer (range layer-count)
              suffix decoder-hyper-connection-layer-suffixes]
          (str "model.language_model.layers." layer "." suffix))))

(defn expert-stream-audit
  "Fail-closed admission for lossless Expert-aware NVMe streaming.

  This is deliberately separate from MTP admission: the streaming seam is
  single-token decode (`n=1`), while MTP verifies multiple draft rows."
  [config index]
  (let [text (text-config config)
        model-type (or (get-key config "model_type") (get-key text "model_type"))
        layers (long (or (get-key text "num_hidden_layers") 0))
        experts (long (or (get-key text "num_experts") 0))
        active (long (or (get-key text "num_experts_per_tok") 0))
        hidden (long (or (get-key text "hidden_size") 0))
        moe-inter (long (or (get-key text "moe_intermediate_size") 0))
        weight-map (or (get-key index "weight_map") index {})
        tensor-names (set (map name (keys weight-map)))
        required (required-expert-tensors layers)
        hyper-required (required-decoder-hyper-connection-tensors layers)
        missing (vec (remove tensor-names required))
        hyper-missing (vec (remove tensor-names hyper-required))
        shards (vec (sort (distinct (keep #(or (get weight-map %)
                                               (get weight-map (keyword %)))
                                          required))))
        official-shape? (and (= "qwen4_exp" model-type)
                             (= 48 layers) (= 512 experts) (= 10 active)
                             (= 2560 hidden) (= 640 moe-inter))
        ;; Official checkpoint is BF16. Each routed expert has a fused
        ;; [2*inter, hidden] gate/up plus [hidden, inter] down matrix.
        bf16-bytes-per-expert (* 2 (+ (* 2 moe-inter hidden)
                                      (* hidden moe-inter)))]
    {:kotodama/architecture :qwen4exp
     :kotodama/layers layers
     :kotodama/experts experts
     :kotodama/active-experts active
     :kotodama/expert-axis 0
     :kotodama/expert-tensor-count (count required)
     :kotodama/hyper-connection-tensor-count (count hyper-required)
     :kotodama/expert-shards shards
     :kotodama/expert-missing missing
     :kotodama/hyper-connection-missing hyper-missing
     :kotodama/bf16-bytes-per-expert bf16-bytes-per-expert
     :kotodama/bf16-token-working-set-bytes (* layers active bf16-bytes-per-expert)
     :kotodama/expert-stream-admitted? (and official-shape?
                                            (empty? missing)
                                            (empty? hyper-missing))
     :kotodama/mtp-compatible? false}))

(defn expert-stream-spec
  "Create the exact lossless streaming contract consumed by torch/murakumo."
  [model config index opts]
  (let [audit (expert-stream-audit config index)
        gpu-layers (long (or (:kotodama/gpu-layers opts) 0))]
    (when-not (:kotodama/expert-stream-admitted? audit)
      (throw (ex-info "Qwen4Exp checkpoint is not expert-stream complete" audit)))
    (when (neg? gpu-layers)
      (throw (ex-info "Qwen4Exp gpu-layers must be non-negative"
                      {:kotodama/gpu-layers gpu-layers})))
    {:kotodama/model model
     :kotodama/architecture :qwen4exp
     :kotodama/decoder :qwen4exp-hyper-connection-moe
     :kotodama/execution :expert-aware-nvme
     :kotodama/placement
     {:kotodama/gpu-layers gpu-layers
      :kotodama/expert-buffer :cpu
      :kotodama/ple-buffer :cpu
      :kotodama/non-expert-buffer :metal-eligible}
     :kotodama/expert-stream
     (merge {:kotodama/lossless? true
             :kotodama/drop-cold-experts 0.0
             :kotodama/active-experts (:kotodama/active-experts audit)
             :kotodama/io-threads 4
             :kotodama/prefetch-layers 0
             :kotodama/mtp-enabled? false}
            (dissoc opts :kotodama/gpu-layers))
     :kotodama/checkpoint-audit audit}))

(defn expert-stream-qualification
  "Qualify one resident-vs-streamed benchmark report.

  Speed is only comparable after exact token parity and lossless settings.
  `page_cache_bypassed` is recorded separately because macOS has no O_DIRECT."
  [report]
  (let [baseline (vec (or (get-key report "baseline_token_ids") []))
        streamed (vec (or (get-key report "streamed_token_ids") []))
        deterministic? (true? (get-key report "streamed_deterministic"))
        lossless? (and (= baseline streamed)
                       (zero? (double (or (get-key report "drop_cold_experts") 0.0)))
                       (= 10 (long (or (get-key report "active_experts") 0))))
        baseline-tps (double (or (get-key report "baseline_tok_s") 0.0))
        streamed-tps (double (or (get-key report "streamed_tok_s") 0.0))
        speedup (if (pos? baseline-tps) (/ streamed-tps baseline-tps) 0.0)
        execution? (and deterministic? lossless? (pos? streamed-tps))]
    {:kotodama/expert-stream-execution-qualified? execution?
     :kotodama/expert-stream-speed-qualified? (and execution? (> speedup 1.0))
     :kotodama/token-parity? (= baseline streamed)
     :kotodama/lossless-settings? lossless?
     :kotodama/page-cache-bypassed? (true? (get-key report "page_cache_bypassed"))
     :kotodama/baseline-tok-s baseline-tps
     :kotodama/streamed-tok-s streamed-tps
     :kotodama/end-to-end-speedup speedup
     :kotodama/disqualification
     (cond
       (not deterministic?) :unstable-stream
       (not= baseline streamed) :token-parity-failed
       (not lossless?) :lossy-settings
       (not (pos? streamed-tps)) :no-real-generation
       (<= speedup 1.0) :no-end-to-end-speedup
       :else nil)}))

(defn checkpoint-audit
  "Return a fail-closed Qwen4Exp MTP checkpoint audit.

  `index` may be a complete `model.safetensors.index.json` map or its weight-map.
  The returned shard list is the exact acquisition set for the MTP head."
  [config index]
  (let [text (text-config config)
        architecture (first (or (get-key config "architectures") []))
        model-type (or (get-key config "model_type")
                       (get-key text "model_type"))
        layer-count (mtp-layer-count config)
        weight-map (or (get-key index "weight_map") index {})
        tensor-names (set (map name (keys weight-map)))
        required (if (pos? layer-count)
                   (required-mtp-tensors layer-count)
                   mtp-root-tensors)
        missing (vec (remove tensor-names required))
        mtp-tensors (vec (sort (filter #(str/starts-with? % "mtp.") tensor-names)))
        shards (vec (sort (distinct (keep #(or (get weight-map %)
                                               (get weight-map (keyword %)))
                                          mtp-tensors))))
        qwen4exp? (and (= "qwen4_exp" model-type)
                       (or (nil? architecture)
                           (contains? #{"Qwen4ExpForConditionalGeneration"
                                        "Qwen4ExpForCausalLM"}
                                      architecture)))
        admitted? (and qwen4exp? (pos? layer-count) (empty? missing))]
    {:kotodama/architecture :qwen4exp
     :kotodama/model-type model-type
     :kotodama/mtp-layer-count layer-count
     :kotodama/mtp-tensor-count (count mtp-tensors)
     :kotodama/mtp-shards shards
     :kotodama/mtp-missing missing
     :kotodama/mtp-admitted? admitted?}))

(defn runtime-spec
  "Create an admitted MTP runtime spec from official checkpoint metadata."
  ([model config index] (runtime-spec model config index {}))
  ([model config index opts]
   (let [audit (checkpoint-audit config index)]
     (when-not (:kotodama/mtp-admitted? audit)
       (throw (ex-info "Qwen4Exp checkpoint is not MTP-complete" audit)))
     (merge (runtime/mtp-transformer
             model
             (merge {:kotodama/mtp
                     {:kotodama/draft-token-count 4
                      :kotodama/verify-draft? true}}
                    opts))
            {:kotodama/architecture :qwen4exp
             :kotodama/checkpoint-audit audit}))))

(defn execution-qualification
  "Qualify a deterministic MTP benchmark without confusing checkpoint
  completeness with decoder correctness or speed.  `report` is the compact
  output of `verify/qwen4exp-b70-bench.sh`.

  Execution requires stable target and draft runs plus exact token parity.
  Optimization additionally requires end-to-end speedup above 1.0."
  [report]
  (let [off-stable? (true? (get-key report "off_deterministic"))
        on-stable? (true? (get-key report "on_deterministic"))
        parity? (true? (get-key report "token_parity"))
        speedup (double (or (get-key report "end_to_end_speedup") 0.0))
        execution? (and off-stable? on-stable? parity?)
        optimization? (and execution? (> speedup 1.0))]
    {:kotodama/mtp-execution-qualified? execution?
     :kotodama/mtp-optimization-qualified? optimization?
     :kotodama/token-parity? parity?
     :kotodama/off-deterministic? off-stable?
     :kotodama/on-deterministic? on-stable?
     :kotodama/end-to-end-speedup speedup
     :kotodama/disqualification
     (cond
       (not off-stable?) :unstable-target
       (not on-stable?) :unstable-mtp
       (not parity?) :token-parity-failed
       (<= speedup 1.0) :no-end-to-end-speedup
       :else nil)}))
