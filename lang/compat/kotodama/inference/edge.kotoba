(ns kotodama.inference.edge
  "Pinned model and OpenAI wire contract for Murakumo edge replicas.

  inference owns the model/protocol contract, torch builds the host runtime
  resource plan, and num (through torch) decides unified-memory admission."
  (:require [torch.edge-runtime :as torch-edge]))

(def ornith-1-5-9b
  {:model-id "murakumo-edge"
   :upstream-model "ornith-ai/Ornith-1.5-9B"
   :artifact-repo "ornith-ai/Ornith-1.5-9B-GGUF"
   :artifact-revision "abdd624b12ebf020b767fff532ff44fe552b28c3"
   :model-file "Ornith-1.5-9B-Q4_K_M.gguf"
   :model-bytes 5780090816
   :mmproj-file "mmproj-Ornith-1.5-9B-BF16.gguf"
   :mmproj-bytes 921704672
   :native-context 262144
   :edge-context 65536
   :vision? true
   :tool-calling? true})

(def ornith-1-5-9b-mtp
  {:model-id "murakumo-edge-mtp-canary"
   :upstream-model "ornith-ai/Ornith-1.5-9B"
   :artifact-repo "protoLabsAI/Ornith-1.5-9B-MTP-GGUF"
   :artifact-revision "5957ee5dcb88e9a9f4cd9a23c649320d20a574cd"
   :model-file "Ornith-1.5-9B-MTP-IQ4_XS.gguf"
   :model-bytes 5454999360
   :model-sha256 "962cf048bd64066003e8253c5b7317fde5fa70f0d4c70dc6e892a33e0f240981"
   :mmproj-file "mmproj-Ornith-1.5-9B-BF16.gguf"
   :mmproj-bytes 921704416
   :mmproj-sha256 "3b8b6b4f357aae0135a2826dbbf2d72cdd096f1878b50709449f5070a0e6d32f"
   :llama-tag "b10472"
   :llama-revision "60eeeb6082c1126bb8bc72902c83123cd056811b"
   :mtp? true
   :draft-token-count 3
   :speculative-bytes 805306368
   :native-context 262144
   :edge-context 65536
   :vision? true
   :tool-calling? true})

(def qwen3-8-27b
  "The dedicated-node artifact: one 27B model, one mac mini, nothing else.

  Owner instruction 2026-09-10 -- \"1 台を 27B 専用機にして待てる用途に使う\" --
  and it is dedicated out of arithmetic, not tidiness. Every number below was
  measured on issachar (M4, 16 GiB) that day, with `vm_stat` before and after
  loading, not derived from a parameter count:

    OS footprint, every model plane evicted   1,871,740,928 wired
    the same node with this model serving    15,961,800,704 wired
    difference minus the weights                546,190,368  = KV + compute
                                                              + runtime, all of it
    swap in use, idle vs. serving              425 MB vs 417 MB (it does not swap)

  Two consequences that are the whole reason this is a separate artifact:

  - `:os-reserve-bytes` is 2 GiB, not the 3 GiB `ornith-1-5-9b` reserves. That
    GiB of difference is not slack the 9B was wasting -- it is the ComfyUI, the
    ollama and the RPC shard that were sharing those nodes. Reserve for
    neighbours you no longer have and this model is refused on a machine it
    demonstrably runs on; keep the neighbours and it is admitted onto a machine
    that will swap.
  - `:wired-limit-mb` is 15360 and the macOS default on these minis is 13312 --
    BELOW the 15,222 MiB this model actually wires. `sysctl -w` does not
    survive a boot, so a dedicated node that is merely provisioned, rather than
    given the limit as a resident daemon, comes back from a reboot serving the
    same model from the CPU and reporting itself healthy.

  Batch 128/32 against llama.cpp's 2048/512 for the same reason: the compute
  buffer is charged on top of weights and KV, and there are 280 MB left."
  {:model-id "murakumo-27b"
   :upstream-model "Qwen/Qwen3.8-27B"
   :artifact-repo "jrell/Qwen3.8-27B-i1-IQ4_XS-GGUF-Smaller"
   :model-file "Qwen3.8-27B-i1-IQ4_XS.gguf"
   :model-bytes 13543869408
   :model-sha256 "4dff967bae0798f04cbd250ddef212e6c131fdc50c8113cf63fe74f145c94cea"
   :native-context 262144
   :edge-context 8192
   :serve-port 8093
   :batch 128
   :ubatch 32
   ;; 521 MiB was the WHOLE non-weight footprint -- KV, compute buffers and
   ;; runtime together. Split here as 384 + 256 = 640 MiB declared over it,
   ;; because the measurement is one request's peak and not a bound. Note the
   ;; overhead is stated rather than left to the shared-node default: 512 MiB
   ;; of runtime allowance ON TOP of a context figure already derived from the
   ;; measured total is the same bytes counted twice, and it refuses the node
   ;; by 256 MB.
   :context-bytes 402653184
   :runtime-overhead-bytes 268435456
   :os-reserve-bytes 2147483648
   :headroom-bytes 536870912
   :wired-limit-mb 15360
   ;; A reasoning model: measured the same day, an 80-token budget was spent
   ;; entirely inside reasoning_content and the caller got an empty `content`
   ;; with finish_reason "length" -- a successful HTTP 200 carrying nothing.
   ;; 99 tokens in 17 s produced the reasoning AND the answer.
   :reasoning? true
   :vision? false
   :tool-calling? true})

(def artifacts
  "Model id -> artifact. The registry a caller names a model through, so that
  adding a model to the fleet is adding a map here rather than a new plan
  function beside `replica-plan` and `mtp-replica-plan`."
  {"murakumo-edge" ornith-1-5-9b
   "murakumo-edge-mtp-canary" ornith-1-5-9b-mtp
   "murakumo-27b" qwen3-8-27b})

(defn artifact
  "The artifact for `model-id`, or a refusal naming what is registered.

  Refuses rather than returning nil: a nil artifact flows into `plan-for` and
  comes back out as a plan for a model with no path and no bytes, which the
  residency check admits (zero required bytes always fit)."
  [model-id]
  (or (get artifacts model-id)
      (throw (ex-info "no such edge model artifact"
                      {:model-id model-id :registered (vec (sort (keys artifacts)))}))))

(defn openai-request
  "Preserve OpenAI messages/content parts/tools; add only bounded edge defaults."
  [request]
  (when-not (and (map? request) (seq (:messages request)))
    (throw (ex-info "edge inference needs non-empty OpenAI messages" {})))
  (-> request
      (assoc :model (:model-id ornith-1-5-9b))
      (update :max_tokens #(min 2048 (or % 512)))
      (update :stream boolean)))

(defn- plan-for
  "Render one replica plan. Reserves come from the artifact when it states them
  and fall back to the shared-node policy when it does not -- the 9B replicas
  were sized for minis that also ran ComfyUI, ollama and an RPC shard, and that
  policy is right for them and wrong for a node dedicated to one model."
  [artifact {:keys [home llama-server port memory-bytes context-bytes api-key-file]}]
  (let [{:keys [model-id model-file model-bytes mmproj-file mmproj-bytes edge-context
                mtp? draft-token-count speculative-bytes serve-port batch ubatch]}
        artifact
        gib 1073741824
        root (str home "/.murakumo/models/" model-id)]
    (torch-edge/replica-plan
     {:model-id model-id
      :model-path (str root "/" model-file)
      :mmproj-path (when mmproj-file (str root "/" mmproj-file))
      :llama-server llama-server
      :port (or port serve-port 8092)
      :context edge-context :parallel 1
      :batch batch :ubatch ubatch
      :memory-bytes memory-bytes
      :os-reserve-bytes (or (:os-reserve-bytes artifact) (* 3 gib))
      :headroom-bytes (or (:headroom-bytes artifact) gib)
      :runtime-bytes (+ model-bytes (or mmproj-bytes 0)
                        (or (:runtime-overhead-bytes artifact) 536870912))
      :speculative-bytes (or speculative-bytes 0)
      :context-bytes (or context-bytes (:context-bytes artifact) (* 2 gib))
      :mtp? (boolean mtp?)
      :draft-token-count (or draft-token-count 3)
      :api-key-file api-key-file})))

(defn plan-for-model
  "Replica plan for a registered model id. The entry point for a node that
  hosts something other than murakumo-edge."
  [model-id options]
  (plan-for (artifact model-id) options))

(defn replica-plan [options]
  (plan-for ornith-1-5-9b options))

(defn mtp-replica-plan [options]
  (plan-for ornith-1-5-9b-mtp options))
