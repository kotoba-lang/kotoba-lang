(ns torch.huggingface-resource
  "Hugging Face safetensors-backed factory for torch.model-registry."
  (:require [torch.huggingface :as hf]
            [torch.num-backend :as nb]
            [torch.safetensors :as safe])
  (:import [java.nio.file Files Path]))

(defn- path [value]
  (.toAbsolutePath ^Path (if (instance? Path value) value
                           (Path/of (str value) (make-array String 0)))))

(defn- checkpoint-paths [checkpoint-path]
  (with-open [checkpoint (safe/open-checkpoint checkpoint-path)]
    (if (instance? torch.safetensors.SafeTensorIndex checkpoint)
      (into [(path checkpoint-path)]
            (map #(path (:path %))) (vals (:shards checkpoint)))
      [(path checkpoint-path)])))

(defn descriptor
  "Describe all files that make one HF model. `:size` is the deduplicated byte
  total of config, index, shards, tokenizer JSON, and tokenizer config so the
  model registry enforces residency against the actual checkpoint footprint."
  ([name config-path checkpoint-path]
   (descriptor name config-path checkpoint-path {}))
  ([name config-path checkpoint-path options]
   (let [artifact-paths
         (into #{(path config-path)}
               (concat (checkpoint-paths checkpoint-path)
                       (keep #(some-> (get options %) path)
                             [:tokenizer-path :tokenizer-config-path])))
         bytes (reduce + (map #(Files/size ^Path %) artifact-paths))]
     (merge options
            {:name name :model name :format "safetensors"
             :config-path (str (path config-path))
             :checkpoint-path (str (path checkpoint-path))
             :artifacts (mapv str (sort-by str artifact-paths))
             :size bytes}))))

(defn load-resource
  "Load model, tokenizer, and weights; optionally construct a serving engine.
  `engine-fn` receives the resource before `:engine` is associated."
  ([backend descriptor*] (load-resource backend descriptor* nil))
  ([backend descriptor* engine-fn]
   (let [loaded (hf/load-llama-resource
                 (:config-path descriptor*) (:checkpoint-path descriptor*) backend
                 (select-keys descriptor* [:tokenizer-path :tokenizer-config-path
                                           :dtype :strict?]))
         resource (assoc loaded :descriptor descriptor* :backend backend)]
     (cond-> resource engine-fn (assoc :engine (engine-fn resource))))))

(defn unload-resource!
  "Release all distinct dense/tied weight handles exactly once."
  [resource]
  (nb/release-weights! (:weights resource))
  nil)

(defn callbacks
  "Lifecycle callbacks suitable for torch.model-registry/registry."
  ([backend] (callbacks backend nil))
  ([backend engine-fn]
   {:load-fn #(load-resource backend % engine-fn)
    :unload-fn unload-resource!}))
