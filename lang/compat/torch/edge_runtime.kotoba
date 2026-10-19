(ns torch.edge-runtime
  "Host-runtime plan for one resident OpenAI-compatible llama.cpp edge replica.

  num owns byte admission; torch owns the executable/model resource contract."
  (:require [num.residency :as residency]))

(defn replica-plan
  [{:keys [model-id model-path mmproj-path port context parallel
           memory-bytes os-reserve-bytes headroom-bytes
           runtime-bytes speculative-bytes context-bytes api-key-file llama-server
           mtp? draft-token-count]
    :or {parallel 1 mtp? false draft-token-count 3 speculative-bytes 0}}]
  (when-not (and (string? model-id) (seq model-id)
                 (string? model-path) (seq model-path)
                 (string? llama-server) (seq llama-server)
                 (pos-int? port) (pos-int? context) (pos-int? parallel)
                 (boolean? mtp?)
                 (or (not mtp?)
                     (and (pos-int? draft-token-count)
                          (<= draft-token-count 8))))
    (throw (ex-info "invalid edge replica plan" {:model model-id :port port})))
  (let [capacity (residency/admission
                  {:memory-bytes memory-bytes
                   :os-reserve-bytes os-reserve-bytes
                   :headroom-bytes headroom-bytes
                   :runtime-bytes runtime-bytes
                   :speculative-bytes speculative-bytes
                   :context-bytes context-bytes})]
    (assoc capacity
           :model-id model-id
           :mtp-enabled? mtp?
           :draft-token-count (when mtp? draft-token-count)
           :argv (cond-> [llama-server
                          "--model" model-path
                          "--alias" model-id
                          "--host" "127.0.0.1"
                          "--port" (str port)
                          "--ctx-size" (str context)
                          "--parallel" (str parallel)
                          "--flash-attn" "on"
                          "--cache-type-k" "q8_0"
                          "--cache-type-v" "q8_0"
                          "--jinja"
                          "--no-webui"]
                   mtp? (into ["--n-gpu-layers" "99"
                               "-fit" "off"
                               "--spec-type" "draft-mtp"
                               "--spec-draft-n-max" (str draft-token-count)])
                   (seq mmproj-path) (into ["--mmproj" mmproj-path])
                   (seq api-key-file) (into ["--api-key-file" api-key-file])))))
