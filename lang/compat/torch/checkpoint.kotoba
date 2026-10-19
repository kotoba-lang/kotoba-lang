(ns torch.checkpoint
  "Restartable training checkpoints backed by one atomic safetensors file."
  (:require [json.data-json :as json]
            [clojure.set :as set]
            [torch.safetensors :as safe]
            [torch.state-dict :as state]))

(def ^:private format-version 1)
(def ^:private metadata-key "torch-clj.checkpoint")

(defn- prefixed [prefix tensors]
  (into {} (map (fn [[name tensor]] [(str prefix name) tensor])) tensors))

(defn- strip-prefix [prefix tensors]
  (into {} (keep (fn [[name tensor]]
                   (when (.startsWith ^String name prefix)
                     [(subs name (count prefix)) tensor])))
        tensors))

(defn- slot-values [slots value-key]
  (mapv (fn [slot]
          (when slot
            (into {} (map (fn [[parameter values]]
                            [parameter (get values value-key)])) slot)))
        slots))

(defn- optimizer-tensors [model* optimizer-state]
  (when optimizer-state
    (let [slots (:slots optimizer-state)]
      (merge
       (prefixed "optimizer.moment."
                 (state/state-dict model* (slot-values slots :moment)))
       (prefixed "optimizer.variance."
                 (state/state-dict model* (slot-values slots :variance)))))))

(defn save-checkpoint!
  "Atomically save model and optional AdamW/GradScaler training state.

  Options may contain `:optimizer-state`, `:optimizer-options`, `:scaler`, and
  JSON-serializable `:training-state` (epoch, global step, sampler position,
  etc.). Returns the destination Path."
  ([path model* weights] (save-checkpoint! path model* weights {}))
  ([path model* weights {:keys [optimizer-state optimizer-options scaler
                                training-state]}]
   (when (and optimizer-state (not= (count weights)
                                    (count (:slots optimizer-state))))
     (throw (ex-info "torch.checkpoint: optimizer slot count mismatch"
                     {:weights (count weights)
                      :slots (count (:slots optimizer-state))})))
   (let [descriptor {:version format-version
                     :optimizer (when optimizer-state
                                  {:type "AdamW" :step (:step optimizer-state)
                                   :options optimizer-options})
                     :scaler scaler
                     :training-state training-state}
         tensors (merge (prefixed "model." (state/state-dict model* weights))
                        (optimizer-tensors model* optimizer-state))]
     (safe/write-file! path tensors
                       {"format" "pt"
                        "torch-clj.kind" "training-checkpoint"
                        metadata-key (json/write-str descriptor)}
                       ;; CPU reference arrays retain double intermediates even
                       ;; when logically f32. Preserve them so resume is exactly
                       ;; trajectory-equivalent, not merely numerically close.
                       {:storage-dtype :f64}))))

(defn- required-names [model* optimizer?]
  (let [names (map :name (state/manifest model*))]
    (set (concat (map #(str "model." %) names)
                 (when optimizer? (map #(str "optimizer.moment." %) names))
                 (when optimizer? (map #(str "optimizer.variance." %) names))))))

(defn load-checkpoint
  "Strictly restore a training checkpoint onto `backend`.

  Returns model `:weights`, optional `:optimizer-state` and `:scaler`, plus
  persisted `:optimizer-options` and `:training-state`."
  ([path backend model*] (load-checkpoint path backend model* {}))
  ([path backend model* {:keys [strict? dtype] :or {strict? true dtype :f32}}]
   (with-open [checkpoint (safe/open-file path)]
     (let [raw-descriptor (get (:metadata checkpoint) metadata-key)]
       (when-not raw-descriptor
         (throw (ex-info "torch.checkpoint: missing checkpoint metadata"
                         {:path (str path)})))
       (let [{:keys [version optimizer scaler training-state] :as descriptor}
             (json/read-str raw-descriptor :key-fn keyword)
             _ (when-not (= format-version version)
                 (throw (ex-info "torch.checkpoint: unsupported version"
                                 {:expected format-version :actual version})))
             provided (set (safe/tensor-names checkpoint))
             required (required-names model* (some? optimizer))
             missing (vec (sort (set/difference required provided)))
             unexpected (vec (sort (set/difference provided required)))]
         (when (seq missing)
           (throw (ex-info "torch.checkpoint: missing tensors" {:missing missing})))
         (when (and strict? (seq unexpected))
           (throw (ex-info "torch.checkpoint: unexpected tensors"
                           {:unexpected unexpected})))
         (let [loaded (into {} (map (fn [name]
                                      [name (safe/read-tensor checkpoint backend
                                                              name dtype)]))
                            required)
               weights (state/load-state-dict model* (strip-prefix "model." loaded))
               optimizer-state
               (when optimizer
                 (let [moments (state/load-state-dict
                                model* (strip-prefix "optimizer.moment." loaded))
                       variances (state/load-state-dict
                                  model* (strip-prefix "optimizer.variance." loaded))]
                   {:step (:step optimizer)
                    :slots (mapv (fn [moment variance]
                                   (when moment
                                     (into {} (map (fn [[key tensor]]
                                                     [key {:moment tensor
                                                           :variance (get variance key)}]))
                                           moment)))
                                 moments variances)}))]
           {:weights weights
            :optimizer-state optimizer-state
            :optimizer-options (:options optimizer)
            :scaler scaler
            :training-state training-state
            :format-version version
            :metadata descriptor}))))))
