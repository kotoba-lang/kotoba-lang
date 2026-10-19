(ns torch.native-server
  "Standalone JVM-free WebGPU Ollama/OpenAI inference server."
  (:require [num.deno-gpu :as gpu]
            [torch.device-profile :as device-profile]
            [torch.metal-resource :as resource]
            [torch.model-registry :as registry]
            [torch.ollama-http :as http]
            [torch.registry-ollama :as registry-ollama]
            [torch.registry-runtime :as registry-runtime]))

(defn- options [args]
  (loop [args args out {:host "127.0.0.1" :port 11434
                        :model "kotoba:latest" :device :auto}]
    (if (empty? args) out
        (let [[flag value & rest] args
              key (case flag
                    "--bundle" :bundle "--model" :model "--host" :host
                    "--port" :port "--device" :device
                    (throw (ex-info "unknown native server option" {:option flag})))
              value (case key
                      :port (js/parseInt value 10)
                      :device (keyword value)
                      value)]
          (recur rest (assoc out key value))))))

(defn- linux-pci-identity []
  (try
    (str "Intel "
         (.trim (js/Deno.readTextFileSync "/sys/class/drm/renderD128/device/vendor"))
         " "
         (.trim (js/Deno.readTextFileSync "/sys/class/drm/renderD128/device/device")))
    (catch :default _ nil)))

(defn- validate-device! [request expected]
  (let [description (or (gpu/adapter-description request)
                        (linux-pci-identity)
                        "unknown")
        expected (case expected
                   :intel-b70 :intel-arc-b70-vulkan
                   :auto nil
                   expected)]
    (device-profile/validate! description expected)))

(defn -main [& args]
  (let [{:keys [bundle model host port device]} (options args)]
    (when-not bundle
      (throw (ex-info "--bundle is required" {})))
    (-> (gpu/request-device)
        (.then
         (fn [request]
           (let [profile (validate-device! request device)
                 backend (gpu/backend request)
                 descriptor (resource/descriptor model bundle)
                 callbacks (resource/callbacks
                            backend {:pool-blocks 256 :block-size 16
                                     :max-running 8 :max-waiting 256})
                 registry* (registry/register
                            (registry/registry (* 2 (:size descriptor))
                                               (:load-fn callbacks)
                                               (:unload-fn callbacks))
                            descriptor)
                 runtime* (registry-runtime/runtime registry*)
                 router* (registry-ollama/router runtime*)
                 service (registry-ollama/service
                          router* {:version "0.13.0-native-webgpu"})
                 server (http/serve! service {:hostname host :port port})]
             (println (js/JSON.stringify
                       (clj->js {:status "ready" :model model :host host
                                 :port (.-port (.-addr server))
                                 :device profile :runtime-jvm false})))
             (doseq [signal ["SIGINT" "SIGTERM"]]
               (js/Deno.addSignalListener signal #(.shutdown server)))
             server)))
        (.catch (fn [error]
                  (js/console.error (or (.-stack error) error))
                  (js/Deno.exit 1))))))

(set! *main-cli-fn* -main)
