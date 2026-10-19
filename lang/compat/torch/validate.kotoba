(ns torch.validate
  "Structural validation of a torch-clj model. Pure: returns a vector of problem
  maps `{:torch/severity :error|:warn :torch/code :torch/path :torch/msg}`.
  `valid?` is true iff there are no :error-level problems (warnings are
  advisory). Shape errors (which depend on an input shape) are reported by
  `torch.core/summary`; this namespace checks the static structure of the model
  itself — well-formed layers, known types, sane arity."
  (:require [kotoba.dsl.problem :as problem]
            [torch.model :as model]
            [torch.ports :as ports]))

(defn- torch-problem [severity code path msg]
  (problem/problem :torch :path severity code path msg))

(def ^:private min-arity
  "Minimum positional-arg count for built-in layer types that need args."
  {:linear 2 :conv2d 3 :maxpool2d 1 :avgpool2d 1 :embedding 2
   :batchnorm 1 :layernorm 1 :rmsnorm 1 :groupnorm 2 :multihead-attention 2
   :llama-block 3 :lm-head 2})

(defn- check-layer [ports* path lyr]
  (cond
    (model/model? lyr)
    (mapcat (fn [i l] (check-layer ports* (str path "/" i) l))
            (range) (:torch/layers (model/normalize lyr)))

    (not (model/layer-type lyr))
    [(torch-problem :error :layer/malformed path
                    (str "not a one-key {type args} layer: " (pr-str lyr)))]

    :else
    (let [t    (model/layer-type lyr)
          args (model/layer-args lyr)
          need (get min-arity t)]
      (cond-> []
        (not (ports/custom-layer? ports* t))
        (conj (torch-problem :error :layer/unknown path (str "unknown layer type: " t)))

        (and need (or (not (vector? args)) (< (count args) need)))
        (conj (torch-problem :error :layer/arity path
                             (str t " needs ≥ " need " positional args, got "
                                  (pr-str args))))))))

(defn problems
  "All structural problems in `model`, as a vector of problem maps. Validates
  against `ports*` (default: built-in vocabulary)."
  ([model*] (problems ports/default-ports model*))
  ([ports* model*]
   (let [norm (model/normalize model*)]
     (vec
      (concat
       (when-not (= :sequential (:torch/module norm))
         [(torch-problem :error :module/unknown ""
                         (str "unsupported module kind: " (:torch/module norm)))])
       (when (empty? (:torch/layers norm))
         [(torch-problem :warn :module/empty "" "model has no layers")])
       (mapcat (fn [i l] (check-layer ports* (str i) l))
               (range) (:torch/layers norm)))))))

(defn valid?
  "True iff `model` has no :error-level structural problems."
  ([model*] (valid? ports/default-ports model*))
  ([ports* model*]
   (problem/valid? :torch (problems ports* model*))))
