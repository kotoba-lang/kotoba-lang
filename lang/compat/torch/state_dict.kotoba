(ns torch.state-dict
  "Stable PyTorch-style parameter names and checkpoint layout conversion.

  torch-clj stores dense matrices as `[in,out]` because execution is `x @ W`;
  PyTorch checkpoints store Linear weights as `[out,in]`. This namespace owns
  that boundary and transposes only matrix weights, never biases/Conv/Norm."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [num.tensor :as t]
            [torch.model :as model]))

(defn- matrix-spec [prefix key rows cols]
  {:name (str prefix ".weight") :key key
   :external-shape [cols rows] :internal-shape [rows cols] :transpose? true})

(defn- bias-spec [prefix key width]
  {:name (str prefix ".bias") :key key
   :external-shape [width] :internal-shape [width] :transpose? false})

(defn- layer-specs [prefix layer]
  (let [type (model/layer-type layer)
        args (model/layer-args layer)]
    (case type
      :linear
      (let [[in out] args]
        [(matrix-spec prefix :w in out) (bias-spec prefix :b out)])

      :conv2d
      (let [[in out kernel _stride _padding _dilation groups] args
            groups (or groups 1)
            [kh kw] (if (sequential? kernel) kernel [kernel kernel])]
        [{:name (str prefix ".weight") :key :w
          :external-shape [out (quot in groups) kh kw]
          :internal-shape [out (quot in groups) kh kw] :transpose? false}
         (bias-spec prefix :b out)])

      :groupnorm
      (let [[_groups channels] args]
        [(assoc (bias-spec prefix :w channels) :name (str prefix ".weight"))
         (bias-spec prefix :b channels)])

      :multihead-attention
      (let [[embed] args]
        (vec
         (mapcat (fn [[external weight-key bias-key]]
                   [(matrix-spec (str prefix "." external) weight-key embed embed)
                    (bias-spec (str prefix "." external) bias-key embed)])
                 [["q_proj" :qw :qb] ["k_proj" :kw :kb]
                  ["v_proj" :vw :vb] ["out_proj" :ow :ob]])))

      [])))

(defn manifest
  "Ordered checkpoint manifest for `model*`. Each entry has stable external
  name, layer index, internal key, external/internal shape, and transpose flag."
  [model*]
  (vec
   (mapcat (fn [[flat-index {:keys [layer path]}]]
             (let [prefix (str "layers." (str/join ".layers." path))]
               (map #(assoc % :layer-index flat-index :layer-path path
                            :layer-type (model/layer-type layer))
                    (layer-specs prefix layer))))
           (map-indexed vector (model/layer-entries model*)))))

(defn- ensure-shape! [label expected array]
  (when-not (= (mapv long expected) (mapv long (:shape array)))
    (throw (ex-info (str "torch.state-dict: " label " shape mismatch")
                    {:expected expected :actual (:shape array)})))
  array)

(defn state-dict
  "Export aligned internal `weights` as a name→NDArray map in PyTorch layout."
  [model* weights]
  (let [layers (model/execution-layers model*)]
    (when-not (= (count layers) (count weights))
      (throw (ex-info "torch.state-dict: weights count mismatch"
                      {:layers (count layers) :weights (count weights)})))
    (into {}
          (map (fn [{:keys [name layer-index key internal-shape transpose?]}]
                 (let [array (get (nth weights layer-index) key)]
                   (when-not array
                     (throw (ex-info "torch.state-dict: missing internal parameter"
                                     {:name name :layer-index layer-index :key key})))
                   (ensure-shape! name internal-shape array)
                   [name (if transpose? (t/transpose array) array)])))
          (manifest model*))))

(defn load-state-dict
  "Convert a name→NDArray state dict into the aligned internal weight vector.

  Strict mode (default) rejects missing and unexpected tensor names. Matrix
  tensors are transposed from PyTorch `[out,in]` into internal `[in,out]`."
  ([model* tensors] (load-state-dict model* tensors {}))
  ([model* tensors {:keys [strict?] :or {strict? true}}]
   (let [layers (model/execution-layers model*)
         specs (manifest model*)
         required (set (map :name specs))
         provided (set (keys tensors))
         missing (vec (sort (set/difference required provided)))
         unexpected (vec (sort (set/difference provided required)))]
     (when (seq missing)
       (throw (ex-info "torch.state-dict: missing tensors" {:missing missing})))
     (when (and strict? (seq unexpected))
       (throw (ex-info "torch.state-dict: unexpected tensors"
                       {:unexpected unexpected})))
     (reduce (fn [weights {:keys [name layer-index key external-shape transpose?]}]
               (let [external (ensure-shape! name external-shape (get tensors name))
                     internal (if transpose? (t/transpose external) external)]
                 (assoc-in weights [layer-index key] internal)))
             (vec (repeat (count layers) nil))
             specs))))
