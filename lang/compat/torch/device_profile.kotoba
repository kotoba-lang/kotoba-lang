(ns torch.device-profile
  "Validated accelerator profiles used by native and distributed inference."
  (:require [clojure.string :as str]
            [num.device-profile :as num-profile]))

(defn classify
  "Classify a WebGPU adapter from its public information map/string."
  [adapter]
  (let [s (str/lower-case (str adapter))
        num-kind (num-profile/classify adapter)]
    (cond
      (or (str/includes? s "llvmpipe") (str/includes? s "software")) :software
      (= :intel-arc-pro-b70 num-kind) :intel-arc-b70-vulkan
      (= :amd-strix-halo-8060s num-kind) :amd-strix-halo-vulkan
      (= :nvidia-jetson-agx-xavier num-kind) :nvidia-jetson-xavier-cuda
      (or (str/includes? s "apple") (str/includes? s "metal")) :apple-metal
      (or (str/includes? s "vulkan") (str/includes? s "discretegpu")) :vulkan-gpu
      :else :unknown)))

(def profiles
  {:intel-arc-b70-vulkan
   {:backend :webgpu-vulkan
    :collective :host-staged
    :peer-to-peer? false
    :preferred-features #{:shader-f16}
    :device-ids #{0xe223}}
   :amd-strix-halo-vulkan
   {:backend :vulkan :collective :host-staged
    :peer-to-peer? false :preferred-features #{:shader-f16}}
   :nvidia-jetson-xavier-cuda
   {:backend :cuda :collective :host-staged
    :peer-to-peer? false :preferred-features #{:shader-f16}}
   :apple-metal
   {:backend :webgpu-metal :collective :shared-memory
    :peer-to-peer? false :preferred-features #{:shader-f16}}
   :vulkan-gpu
   {:backend :webgpu-vulkan :collective :host-staged
    :peer-to-peer? false :preferred-features #{:shader-f16}}})

(defn profile [adapter]
  (let [kind (classify adapter)]
    (assoc (get profiles kind {:backend :unsupported}) :kind kind)))

(defn validate!
  ([adapter] (validate! adapter nil))
  ([adapter expected]
   (let [{:keys [kind backend] :as p} (profile adapter)]
     (when (= :software kind)
       (throw (ex-info "software WebGPU adapters are not inference devices"
                       {:adapter adapter :kind kind})))
     (when (= :unsupported backend)
       (throw (ex-info "unsupported WebGPU adapter" {:adapter adapter})))
     (when (and expected (not= expected kind))
       (throw (ex-info "WebGPU adapter does not match requested device"
                       {:expected expected :actual kind :adapter adapter})))
     p)))

(defn serving-profile
  "Translate num's physical execution hints into torch scheduler settings.

  The result keeps the hardware profile attached for observability. Latency and
  fallback intents use one decode slot; throughput may use the measured device
  maximum."
  ([adapter] (serving-profile adapter :latency))
  ([adapter intent]
   (let [physical (num-profile/execution-hints adapter intent)
         speculative (:num/speculative physical)]
     {:torch/device (profile adapter)
      :torch/execution-intent intent
      :torch/max-running (:num/parallel physical)
      :torch/batch-size (:num/batch-size physical)
      :torch/ubatch-size (:num/ubatch-size physical)
      :torch/flash-attention? (:num/flash-attention? physical)
      :torch/speculative speculative
      :torch/host-prerequisites (:num/host-prerequisites physical)})))
