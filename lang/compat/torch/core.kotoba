(ns torch.core
  "Top-level torch-clj operations: normalize a model, propagate shapes, and
  summarize parameters — all pure, all over host-injected `torch.ports`.

  The single invariant: torch-clj *describes* a network (shapes, parameter
  counts, a per-layer trace) and never executes tensor math unless a host
  supplies an `IBackend`. Results are plain EDN — inspectable, replayable,
  diffable, Datomic-storable."
  (:require [torch.model :as model]
            [torch.ports :as ports]))

(declare summary)

(defn- step
  "Apply one layer to `in-shape` via `ports`, recursing into sub-models.
  Returns `{:torch/layer … :torch/out-shape … :torch/params … :torch/error?}`."
  [ports* lyr in-shape]
  (if (model/model? lyr)
    ;; nested sub-model: summarize it, splice its result as a group
    (let [sub (summary ports* lyr in-shape)]
      {:torch/layer    :sequential
       :torch/sub      (:torch/layers sub)
       :torch/out-shape (:torch/out-shape sub)
       :torch/params   (:torch/total-params sub)
       :torch/error    (:torch/error sub)})
    (let [t    (model/layer-type lyr)
          args (model/layer-args lyr)
          [tag out] (ports/shape-of ports* t args in-shape)]
      (cond-> {:torch/layer t
               :torch/args  args
               :torch/params (ports/params-of ports* t args)}
        (= tag :ok)    (assoc :torch/out-shape out)
        (= tag :error) (assoc :torch/error out)))))

(defn summary
  "Walk `model` from `in-shape`, returning a full description:

    {:torch/in-shape    [...]
     :torch/layers      [{:torch/layer … :torch/out-shape … :torch/params …} …]
     :torch/out-shape   [...]          ; shape after the last layer
     :torch/total-params N
     :torch/error       msg-or-nil}    ; first shape error, if any

  Stops propagating shapes at the first error but still reports later layers'
  parameter counts. Pure."
  ([model*] (summary ports/default-ports model* nil))
  ([model* in-shape] (summary ports/default-ports model* in-shape))
  ([ports* model* in-shape]
   (let [lyrs (model/execution-layers model*)]
     (loop [cur in-shape, acc [], total 0, err nil, [l & more] lyrs]
       (if (nil? l)
         {:torch/in-shape     in-shape
          :torch/layers       acc
          :torch/out-shape    (when-not err cur)
          :torch/total-params total
          :torch/error        err}
         (let [r     (step ports* l cur)
               err'  (or err (:torch/error r))
               nxt   (if (and (not err) (:torch/out-shape r))
                       (:torch/out-shape r) cur)]
           (recur nxt
                  (conj acc r)
                  (+ total (long (:torch/params r 0)))
                  err'
                  more)))))))

(defn infer-shape
  "The output shape of `model` given `in-shape`, or nil if a shape error occurs
  (see `summary` for the error). Pure."
  ([model* in-shape] (infer-shape ports/default-ports model* in-shape))
  ([ports* model* in-shape]
   (:torch/out-shape (summary ports* model* in-shape))))

(defn total-params
  "Total learnable parameter count of `model`. Pure."
  ([model*] (total-params ports/default-ports model*))
  ([ports* model*]
   (:torch/total-params (summary ports* model* nil))))

(defn run
  "Execute a real forward pass through a host `IBackend`. Requires `ports*` to
  satisfy `IBackend`; otherwise throws (pure shape engines do not run tensors).
  The four-argument form requires `IRuntimeBackend` and carries aligned runtime
  inputs such as cross-attention context and key-padding masks."
  ([ports* model* input]
   (if (satisfies? ports/IBackend ports*)
     (ports/forward ports* (model/normalize model*) input)
     (throw (ex-info "no IBackend bound — torch-clj is shape-only here"
                     {:torch/model (model/normalize model*)}))))
  ([ports* model* input options]
   (if (satisfies? ports/IRuntimeBackend ports*)
     (ports/forward-with-options ports* (model/normalize model*) input options)
     (throw (ex-info "backend does not support runtime layer options"
                     {:torch/model (model/normalize model*)
                      :options options})))))
