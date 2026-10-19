(ns torch.metal-bundle
  "Reader for TGBNDL1 manifest + binary tensor payload containers."
  (:require [cljs.reader :as reader]
            [num.array :as arr]
            [num.quantized :as quantized]
            [torch.model :as model]
            [torch.tokenizer :as tokenizer]))

(def magic "TGBNDL1\n")

(defn- u32-le [view offset]
  (.getUint32 view offset true))

(defn- payload-values [bytes payload-base encoding {:keys [offset length]}]
  (let [start (+ payload-base offset)
        end (+ start length)]
    (when-not (and (int? offset) (<= 0 offset) (int? length) (<= 0 length)
                   (<= payload-base start end (.-byteLength bytes))
                   (or (not= encoding :f32-le) (zero? (mod length 4))))
      (throw (js/Error. "bundle tensor payload is out of bounds or misaligned")))
    (case encoding
      :u8 (mapv #(aget bytes %) (range start (+ start length)))
      :f32-le
      (let [view (js/DataView. (.-buffer bytes) (+ (.-byteOffset bytes) start) length)]
        (mapv #(.getFloat32 view (* 4 %) true) (range (quot length 4))))
      (throw (js/Error. "unsupported bundle tensor encoding")))))

(defn- read-container [path]
  (let [bytes (js/Deno.readFileSync path)
        decoder (js/TextDecoder.)
        _ (when (< (.-byteLength bytes) 12)
            (throw (js/Error. "truncated TGBNDL1 header")))
        prefix (.decode decoder (.slice bytes 0 8))]
    (when-not (= magic prefix)
      (throw (js/Error. "invalid TGBNDL1 bundle magic")))
    (let [view (js/DataView. (.-buffer bytes) (.-byteOffset bytes) (.-byteLength bytes))
          manifest-length (u32-le view 8)
          manifest-start 12
          payload-base (+ manifest-start manifest-length)
          _ (when (> payload-base (.-byteLength bytes))
              (throw (js/Error. "bundle manifest is outside file bounds")))
          manifest (reader/read-string
                    (.decode decoder (.slice bytes manifest-start payload-base)))]
      (when-not (#{:torch/gguf-metal-bundle-v2
                   :torch/llama-metal-bundle-v3} (:format manifest))
        (throw (js/Error. "unsupported Metal bundle version")))
      {:bytes bytes :manifest manifest :payload-base payload-base})))

(defn inspect-bundle
  "Read and validate the manifest without decoding tensor payloads."
  [path]
  (:manifest (read-container path)))

(defn load-bundle
  "Synchronously read and validate a compact bundle from Deno filesystem."
  [path]
  (let [{:keys [bytes manifest payload-base]} (read-container path)]
    (update manifest :weights
              (fn [weights]
                (mapv (fn [entry]
                        (into {}
                              (map (fn [[key descriptor]]
                                     (let [values (payload-values
                                                   bytes payload-base
                                                   (:encoding descriptor)
                                                   (:payload descriptor))
                                           _ (when (and (= :f32-le (:encoding descriptor))
                                                        (not= (count values)
                                                              (reduce * 1 (:shape descriptor))))
                                               (throw (js/Error.
                                                       "dense bundle tensor shape mismatch")))]
                                       [key (-> descriptor
                                                (dissoc :payload :encoding)
                                                (assoc (if (= :u8 (:encoding descriptor))
                                                         :bytes :values)
                                                       values))])))
                              entry))
                      weights)))))

(defn build-model [{:keys [vocab embed-dim hidden-dim head-count kv-head-count
                           block-count rope-theta norm-eps]}]
  (apply model/sequential
         (concat [(model/embedding vocab embed-dim)]
                 (repeat block-count
                         (model/llama-block embed-dim head-count hidden-dim
                                            {:kv-heads kv-head-count
                                             :rope-theta rope-theta
                                             :eps (or norm-eps 1.0e-6)}))
                 [(model/rmsnorm embed-dim (or norm-eps 1.0e-6))
                  (model/lm-head embed-dim vocab)])))

(defn- upload-weight [backend {:keys [kind shape source-shape quant-type bytes values]}]
  (case kind
    :quantized-matrix (quantized/matrix backend bytes source-shape quant-type)
    :quantized-table (quantized/table backend bytes shape quant-type)
    :dense (arr/from-vec backend values shape)))

(defn upload-weights [backend weights]
  (mapv (fn [entry]
          (into {} (map (fn [[key weight]] [key (upload-weight backend weight)]))
                entry))
        weights))

(defn instantiate
  "Upload one loaded bundle into a backend and construct runtime model/tokenizer."
  [backend manifest]
  {:manifest manifest :model (build-model (:config manifest))
   :weights (upload-weights backend (:weights manifest))
   :tokenizer (tokenizer/tokenizer (:tokenizer manifest))
   :backend backend})
