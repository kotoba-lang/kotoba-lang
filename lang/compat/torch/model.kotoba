(ns torch.model
  "torch-clj as EDN — plain-data neural-network module graph. Portable .cljc
  (JVM, ClojureScript, SCI).

  A *layer* IS data: a single-key map whose key is the layer type and whose
  value is the positional args (a vector) — the same `{op args}` shape as a
  JSONLogic rule. Compact form is canonical:

    {:linear  [784 256]}   ; nn.Linear(784, 256)
    {:relu    {}}          ; nn.ReLU()
    {:conv2d  [3 16 3]}    ; nn.Conv2d(3, 16, kernel=3)
    {:dropout [0.1]}       ; nn.Dropout(p=0.1)

  A *model* is `{:torch/module :sequential :torch/layers [<layer> …]}` — or a
  bare vector of layers, which `normalize` reads as an implicit sequential.
  Nested sequentials compose: a layer may itself be a sub-model.

  This namespace only builds/normalizes the data. Shape propagation lives in
  `torch.shape`, validation in `torch.validate`, host-injected custom layers in
  `torch.ports`, and the top-level ops in `torch.core`. No tensor math runs
  here — the model is a description, not an execution."
  (:refer-clojure :exclude [flatten])
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; layer constructors — each returns a one-key {type args} map.
;; ---------------------------------------------------------------------------

(defn layer
  "A layer literal: `{type args}`. `args` defaults to `{}` (a nullary layer)."
  ([type] (layer type {}))
  ([type args] {type args}))

(defn linear     [in out]            {:linear [in out]})
(defn conv2d     [in out k & more]   {:conv2d (into [in out k] more)})
(defn maxpool2d  [k & more]          {:maxpool2d (into [k] more)})
(defn avgpool2d  [k & more]          {:avgpool2d (into [k] more)})
(defn embedding  [num-emb dim]       {:embedding [num-emb dim]})
(defn batchnorm  [features]          {:batchnorm [features]})
(defn layernorm  [features]          {:layernorm [features]})
(defn rmsnorm
  "Llama-style RMSNorm over the final `features` dimension."
  ([features] {:rmsnorm [features]})
  ([features eps] {:rmsnorm [features eps]}))
(defn groupnorm
  "PyTorch-style GroupNorm(num-groups, num-channels, eps=1e-5)."
  ([num-groups num-channels] {:groupnorm [num-groups num-channels]})
  ([num-groups num-channels eps] {:groupnorm [num-groups num-channels eps]}))
(defn dropout    ([] {:dropout [0.5]}) ([p] {:dropout [p]}))
(defn flatten    [] {:flatten {}})
(defn relu       [] {:relu {}})
(defn silu       [] {:silu {}})
(defn gelu       [] {:gelu {}})
(defn sigmoid    [] {:sigmoid {}})
(defn tanh       [] {:tanh {}})
(defn softmax    [] {:softmax {}})
(defn attention
  "Parameter-free self-attention over `[sequence embedding]`. Zero-arg is
  single-head (unchanged `{:attention {}}` shape); `num-heads` selects
  multi-head — `embedding` must divide evenly by it (checked by
  `torch.shape/layer-shape`, not here — this constructor is pure data)."
  ([] {:attention {}})
  ([num-heads] {:attention [num-heads]}))
(defn multihead-attention
  "Learned self-attention with independent Q/K/V and output projections.
  `opts` supports attention masks plus Llama RoPE via `:rope?`, `:rope-theta`,
  `:position-offset`, and `:context-position-offset`; rank-2 and batch-first
  rank-3 inputs share the same layer description."
  ([embed-dim num-heads]
   {:multihead-attention [embed-dim num-heads]})
  ([embed-dim num-heads opts]
   {:multihead-attention [embed-dim num-heads opts]}))

(defn llama-block
  "Pre-normalized Llama decoder block: RoPE causal attention plus SwiGLU.
  `opts` accepts `:kv-heads` (grouped-query attention), `:rope-theta`,
  `:position-offset`, and RMSNorm `:eps`."
  ([embed-dim num-heads hidden-dim]
   {:llama-block [embed-dim num-heads hidden-dim]})
  ([embed-dim num-heads hidden-dim opts]
   {:llama-block [embed-dim num-heads hidden-dim opts]}))

(defn lm-head
  "Bias-free hidden-state to vocabulary-logit projection."
  [embed-dim vocab-size]
  {:lm-head [embed-dim vocab-size]})

;; ---------------------------------------------------------------------------
;; model container
;; ---------------------------------------------------------------------------

(defn sequential
  "A sequential module over `layers` (a seq of layer maps / sub-models)."
  [& layers]
  {:torch/module :sequential
   :torch/layers (vec layers)})

(defn model?
  "True if `x` is a canonical module map (vs a bare layer or vector)."
  [x]
  (and (map? x) (contains? x :torch/module)))

(defn layer-type
  "The single key of a layer literal, or nil if `x` is not a 1-key map."
  [x]
  (when (and (map? x) (not (model? x)) (= 1 (count x)))
    (key (first x))))

(defn layer-args
  "The args (vector/map) of a layer literal."
  [x]
  (when-let [t (layer-type x)] (get x t)))

(declare normalize-layer)

(defn normalize
  "Coerce any accepted input into a canonical sequential module map.

  - a vector/seq of layers → an implicit sequential
  - a module map           → returned as-is (layers normalized recursively)
  - a single layer literal → a sequential of one"
  [x]
  (cond
    (model? x)
    (update x :torch/layers #(mapv normalize-layer %))

    (sequential? x)               ; a bare vector/seq of layers
    {:torch/module :sequential
     :torch/layers (mapv normalize-layer x)}

    :else
    {:torch/module :sequential
     :torch/layers [(normalize-layer x)]}))

(defn- normalize-layer
  "A layer is left as-is unless it is itself a sub-model (then recurse)."
  [x]
  (if (model? x) (normalize x) x))

(defn layers
  "The (normalized) layer seq of a model."
  [model]
  (:torch/layers (normalize model)))

(defn layer-entries
  "Leaf layers in recursive Sequential execution order. Each entry contains
  `:layer` and its stable nested `:path` of layer indexes."
  [model]
  (letfn [(walk [module path]
            (mapcat (fn [[index child]]
                      (let [child-path (conj path index)]
                        (if (model? child)
                          (walk child child-path)
                          [{:layer child :path child-path}])))
                    (map-indexed vector (:torch/layers module))))]
    (vec (walk (normalize model) []))))

(defn execution-layers
  "Leaf layers flattened from nested Sequentials in canonical forward order."
  [model]
  (mapv :layer (layer-entries model)))

(defn describe
  "A short human string for a layer, e.g. \"linear(784, 256)\"."
  [lyr]
  (let [t (layer-type lyr)
        a (layer-args lyr)]
    (str (name t)
         "(" (cond
               (map? a)  (str/join ", " (map (fn [[k v]] (str (name k) "=" v)) a))
               (coll? a) (str/join ", " a)
               :else     (str a))
         ")")))
