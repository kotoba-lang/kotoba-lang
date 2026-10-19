(ns torch.gguf-resource
  "GGUF-backed model resource factory for torch.model-registry."
  (:require [torch.gguf :as gguf]
            [torch.num-backend :as nb])
  (:import [java.nio.file Files Paths]))

(defn descriptor
  "Build a registry descriptor from a GGUF path without loading its tensors."
  ([name path] (descriptor name path {}))
  ([name path metadata]
   (let [path* (Paths/get (str path) (make-array String 0))]
     (merge {:name name :path (str path*) :size (Files/size path*)}
            metadata))))

(defn load-resource
  "Parse GGUF, build model/tokenizer, upload weights, and optionally construct
  a serving engine with `engine-fn`. The callback receives the resource map
  before `:engine` is associated."
  ([backend descriptor*] (load-resource backend descriptor* nil))
  ([backend descriptor* engine-fn]
   (let [file (gguf/load-file (:path descriptor*))
         model (gguf/llama-model file)
         tokenizer (gguf/gguf-tokenizer file)
         weights (gguf/load-llama-weights file backend)
         resource {:descriptor descriptor* :gguf file :model model
                   :tokenizer tokenizer :weights weights :backend backend}]
     (cond-> resource engine-fn (assoc :engine (engine-fn resource))))))

(defn unload-resource!
  "Release all distinct dense/packed/tied weight handles exactly once."
  [resource]
  (nb/release-weights! (:weights resource))
  nil)

(defn callbacks
  "Return `{:load-fn :unload-fn}` callbacks suitable for model-registry."
  ([backend] (callbacks backend nil))
  ([backend engine-fn]
   {:load-fn #(load-resource backend % engine-fn)
    :unload-fn unload-resource!}))
