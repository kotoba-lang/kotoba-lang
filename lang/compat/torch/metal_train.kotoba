(ns torch.metal-train
  "Real WebGPU/Metal training step for a two-layer torch.model MLP. All
  forward, backward, and SGD kernels stay on the GPU until final reporting."
  (:require [num.wgsl :as wgsl]
            [torch.model :as model]))

(def usage (.-GPUBufferUsage js/globalThis))
(def map-mode (.-GPUMapMode js/globalThis))

(defn- fail [message data] (throw (ex-info (str "torch.metal-train: " message) data)))

(defn- buffer [device values]
  (let [values (js/Float32Array. (clj->js values))
        result (.createBuffer device #js {:size (max 4 (.-byteLength values))
                                          :usage (bit-or (.-STORAGE usage)
                                                         (.-COPY_SRC usage)
                                                         (.-COPY_DST usage))
                                          :mappedAtCreation true})]
    (.set (js/Float32Array. (.getMappedRange result)) values)
    (.unmap result) result))

(defn- empty-buffer [device n]
  (.createBuffer device #js {:size (max 4 (* n 4))
                              :usage (bit-or (.-STORAGE usage) (.-COPY_SRC usage)
                                             (.-COPY_DST usage))}))

(defn- uniform-u32 [device values]
  (let [typed (js/Uint32Array. (clj->js values))
        result (.createBuffer device #js {:size (max 16 (.-byteLength typed))
                                          :usage (bit-or (.-UNIFORM usage)
                                                         (.-COPY_DST usage))})]
    (.writeBuffer (.-queue device) result 0 typed) result))

(defn- uniform-sgd [device learning-rate n]
  (let [raw (js/ArrayBuffer. 16) view (js/DataView. raw)
        result (.createBuffer device #js {:size 16
                                          :usage (bit-or (.-UNIFORM usage)
                                                         (.-COPY_DST usage))})]
    (.setFloat32 view 0 learning-rate true) (.setUint32 view 4 n true)
    (.writeBuffer (.-queue device) result 0 raw) result))

(defn- pipeline [device source]
  (.createComputePipeline device
                          #js {:layout "auto"
                               :compute #js {:module (.createShaderModule device #js {:code source})
                                             :entryPoint "main"}}))

(defn- dispatch! [device pipeline buffers workgroups]
  (let [bind (.createBindGroup device
                               #js {:layout (.getBindGroupLayout pipeline 0)
                                    :entries (clj->js
                                              (map-indexed
                                               (fn [index b]
                                                 {:binding index :resource {:buffer b}})
                                               buffers))})
        encoder (.createCommandEncoder device) pass (.beginComputePass encoder)]
    (.setPipeline pass pipeline) (.setBindGroup pass 0 bind)
    (apply #(.dispatchWorkgroups pass %1 %2 %3) workgroups)
    (.end pass) (.submit (.-queue device) #js [(.finish encoder)])))

(defn- gemm! [device pipe a m k b n]
  (let [out (empty-buffer device (* m n))]
    (dispatch! device pipe [a b out (uniform-u32 device [m k n 0])]
               [(Math/ceil (/ n 16)) (Math/ceil (/ m 16)) 1])
    out))

(defn- transpose! [device pipe input rows cols]
  (let [out (empty-buffer device (* rows cols))]
    (dispatch! device pipe [input out (uniform-u32 device [rows cols 0 0])]
               [(Math/ceil (/ cols 16)) (Math/ceil (/ rows 16)) 1]) out))

(defn- read-buffer [device source n]
  (let [read (.createBuffer device #js {:size (* n 4)
                                        :usage (bit-or (.-COPY_DST usage) (.-MAP_READ usage))})
        encoder (.createCommandEncoder device)]
    (.copyBufferToBuffer encoder source 0 read 0 (* n 4))
    (.submit (.-queue device) #js [(.finish encoder)])
    (-> (.mapAsync read (.-READ map-mode))
        (.then (fn [] (vec (js/Float32Array. (.getMappedRange read))))))))

(defn train-step!
  "Train `{linear, relu, linear}` once with MSE and SGD on real WebGPU.
  `weights` is `[{:w flat :b flat} nil {:w flat :b flat}]`; input/target are
  flat host vectors. Returns a Promise with prediction, loss, and new weights."
  [device-result model* weights input target
   {:keys [batch-size learning-rate] :or {learning-rate 0.01}}]
  (let [layers (model/execution-layers model*) types (mapv model/layer-type layers)]
    (when-not (= [:linear :relu :linear] types)
      (fail "expected exactly linear/relu/linear" {:layers types}))
    (let [[[in hidden] _ [hidden2 out]] (mapv model/layer-args layers)
          batch (long batch-size)]
      (when-not (and (= hidden hidden2) (= (count input) (* batch in))
                     (= (count target) (* batch out)))
        (fail "shape mismatch" {:batch batch :input in :hidden hidden :output out}))
      (let [device (.-device device-result)
            gemm (pipeline device wgsl/gemm-tiled-wgsl)
            transpose (pipeline device wgsl/transpose-2d-wgsl)
            bias-add (pipeline device wgsl/add-bias-rows-wgsl)
            relu (pipeline device wgsl/ewise1-wgsl)
            mse-grad (pipeline device wgsl/mse-gradient-wgsl)
            relu-backward (pipeline device wgsl/relu-backward-wgsl)
            bias-grad (pipeline device wgsl/bias-gradient-wgsl)
            sgd (pipeline device wgsl/sgd-update-wgsl)
            x (buffer device input) target-buffer (buffer device target)
            w1 (buffer device (get-in weights [0 :w])) b1 (buffer device (get-in weights [0 :b]))
            w2 (buffer device (get-in weights [2 :w])) b2 (buffer device (get-in weights [2 :b]))
            z1 (gemm! device gemm x batch in w1 hidden)
            _ (dispatch! device bias-add [z1 b1 (uniform-u32 device [batch hidden 0 0])]
                         [(Math/ceil (/ (* batch hidden) 64)) 1 1])
            activation (empty-buffer device (* batch hidden))
            _ (dispatch! device relu [z1 activation (uniform-u32 device [1 0 0 0])]
                         [(Math/ceil (/ (* batch hidden) 64)) 1 1])
            prediction (gemm! device gemm activation batch hidden w2 out)
            _ (dispatch! device bias-add [prediction b2 (uniform-u32 device [batch out 0 0])]
                         [(Math/ceil (/ (* batch out) 64)) 1 1])
            dy (empty-buffer device (* batch out))
            mse-upstream (buffer device [1.0])
            _ (dispatch! device mse-grad [prediction target-buffer mse-upstream dy
                                          (uniform-u32 device [(* batch out) 0 0 0])]
                         [(Math/ceil (/ (* batch out) 64)) 1 1])
            at (transpose! device transpose activation batch hidden)
            dw2 (gemm! device gemm at hidden batch dy out)
            db2 (empty-buffer device out)
            _ (dispatch! device bias-grad [dy db2 (uniform-u32 device [batch out 0 0])]
                         [(Math/ceil (/ out 64)) 1 1])
            w2t (transpose! device transpose w2 hidden out)
            da (gemm! device gemm dy batch out w2t hidden)
            dz (empty-buffer device (* batch hidden))
            _ (dispatch! device relu-backward [da z1 dz
                                               (uniform-u32 device [(* batch hidden) 0 0 0])]
                         [(Math/ceil (/ (* batch hidden) 64)) 1 1])
            xt (transpose! device transpose x batch in)
            dw1 (gemm! device gemm xt in batch dz hidden)
            db1 (empty-buffer device hidden)
            _ (dispatch! device bias-grad [dz db1 (uniform-u32 device [batch hidden 0 0])]
                         [(Math/ceil (/ hidden 64)) 1 1])
            update! (fn [parameter gradient n]
                      (dispatch! device sgd [parameter gradient
                                             (uniform-sgd device learning-rate n)]
                                 [(Math/ceil (/ n 64)) 1 1]))
            _ (update! w1 dw1 (* in hidden)) _ (update! b1 db1 hidden)
            _ (update! w2 dw2 (* hidden out)) _ (update! b2 db2 out)]
        (-> (js/Promise.all
             #js [(read-buffer device prediction (* batch out))
                  (read-buffer device w1 (* in hidden)) (read-buffer device b1 hidden)
                  (read-buffer device w2 (* hidden out)) (read-buffer device b2 out)])
            (.then (fn [values]
                     (let [prediction (aget values 0)
                           loss (/ (reduce + (map (fn [p t] (let [d (- p t)] (* d d)))
                                                  prediction target))
                                   (count target))]
                       {:prediction prediction :loss loss
                        :weights [{:w (aget values 1) :b (aget values 2)} nil
                                  {:w (aget values 3) :b (aget values 4)}]}))))))))
