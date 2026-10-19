(ns torch.gguf
  "GGUF v2/v3 metadata and tensor loader with long file offsets."
  (:refer-clojure :exclude [load-file])
  (:require [clojure.string :as str]
            [num.array :as arr]
            [num.dtype :as dtype]
            [num.quantized :as quantized]
            [torch.model :as model]
            [torch.tokenizer :as tokenizer])
  (:import [java.io RandomAccessFile]
           [java.nio ByteBuffer ByteOrder]
           [java.nio.charset StandardCharsets]
           [java.nio.file Files Path Paths]))

(def value-types
  {0 :uint8 1 :int8 2 :uint16 3 :int16 4 :uint32 5 :int32
   6 :float32 7 :bool 8 :string 9 :array 10 :uint64 11 :int64 12 :float64})

(def tensor-types {0 :f32 1 :f16 6 :q5-0 8 :q8-0 12 :q4-k 14 :q6-k})

(defn- source-size [{:keys [bytes path]}]
  (if bytes (alength ^bytes bytes) (Files/size ^Path path)))

(defn- read-range [{:keys [bytes path] :as source} offset n]
  (let [size (source-size source)]
    (when-not (and (<= 0 offset) (<= 0 n) (<= (+ offset n) size)
                   (<= n Integer/MAX_VALUE))
      (throw (ex-info "GGUF read is outside source bounds"
                      {:offset offset :count n :size size})))
    (if bytes
      (java.util.Arrays/copyOfRange ^bytes bytes (int offset) (int (+ offset n)))
      (with-open [file (RandomAccessFile. (.toFile ^Path path) "r")]
        (.seek file offset)
        (let [output (byte-array (int n))]
          (.readFully file output)
          output)))))

(defn- cursor [source] {:source source :position (atom 0)})

(defn- take-bytes [{:keys [source position]} n]
  (let [offset @position bytes (read-range source offset n)]
    (swap! position + n)
    bytes))

(defn- little-buffer [bytes]
  (doto (ByteBuffer/wrap bytes) (.order ByteOrder/LITTLE_ENDIAN)))

(defn- u8 [reader] (bit-and 0xff (aget ^bytes (take-bytes reader 1) 0)))
(defn- i8 [reader] (int (aget ^bytes (take-bytes reader 1) 0)))
(defn- u16 [reader] (bit-and 0xffff (.getShort (little-buffer (take-bytes reader 2)))))
(defn- i16 [reader] (.getShort (little-buffer (take-bytes reader 2))))
(defn- u32 [reader] (bit-and 0xffffffff (.getInt (little-buffer (take-bytes reader 4)))))
(defn- i32 [reader] (.getInt (little-buffer (take-bytes reader 4))))
(defn- u64 [reader]
  (let [value (.getLong (little-buffer (take-bytes reader 8)))]
    (when (neg? value)
      (throw (ex-info "GGUF uint64 exceeds signed JVM range" {:value value})))
    value))
(defn- i64 [reader] (.getLong (little-buffer (take-bytes reader 8))))
(defn- f32 [reader] (.getFloat (little-buffer (take-bytes reader 4))))
(defn- f64 [reader] (.getDouble (little-buffer (take-bytes reader 8))))

(defn- gguf-string [reader]
  (let [n (u64 reader)]
    (when (> n Integer/MAX_VALUE)
      (throw (ex-info "GGUF string is too large" {:bytes n})))
    (String. ^bytes (take-bytes reader n) StandardCharsets/UTF_8)))

(declare read-value)

(defn- read-value [reader type-id]
  (case (get value-types type-id)
    :uint8 (u8 reader) :int8 (i8 reader)
    :uint16 (u16 reader) :int16 (i16 reader)
    :uint32 (u32 reader) :int32 (i32 reader)
    :float32 (f32 reader)
    :bool (let [value (u8 reader)]
            (when-not (#{0 1} value)
              (throw (ex-info "GGUF boolean must be encoded as 0 or 1"
                              {:value value})))
            (= value 1))
    :string (gguf-string reader) :uint64 (u64 reader) :int64 (i64 reader)
    :float64 (f64 reader)
    :array (let [element-type (u32 reader) n (u64 reader)]
             (when (> n Integer/MAX_VALUE)
               (throw (ex-info "GGUF metadata array is too large" {:count n})))
             (mapv (fn [_] (read-value reader element-type)) (range n)))
    (throw (ex-info "unknown GGUF metadata value type" {:type-id type-id}))))

(defn- align-up [value alignment]
  (* (quot (+ value (dec alignment)) alignment) alignment))

(defn- parse-source [source]
  (let [reader (cursor source)
        magic (String. ^bytes (take-bytes reader 4) StandardCharsets/US_ASCII)
        _ (when-not (= magic "GGUF")
            (throw (ex-info "not a GGUF file" {:magic magic})))
        version (u32 reader)
        _ (when-not (#{2 3} version)
            (throw (ex-info "unsupported GGUF version" {:version version})))
        tensor-count (u64 reader) metadata-count (u64 reader)
        metadata (into {}
                       (repeatedly metadata-count
                                   (fn [] (let [key (gguf-string reader)
                                                type-id (u32 reader)]
                                            [key (read-value reader type-id)]))))
        tensors (mapv (fn [_]
                        (let [name (gguf-string reader)
                              rank (u32 reader)
                              dimensions (mapv (fn [_] (u64 reader)) (range rank))
                              type-id (u32 reader)
                              offset (u64 reader)]
                          {:name name :rank rank :ggml-shape dimensions
                           :shape (vec (reverse dimensions))
                           :type-id type-id :type (get tensor-types type-id :unsupported)
                           :offset offset}))
                      (range tensor-count))
        alignment (long (get metadata "general.alignment" 32))
        _ (when-not (and (pos? alignment) (zero? (bit-and alignment (dec alignment))))
            (throw (ex-info "GGUF alignment must be a positive power of two"
                            {:alignment alignment})))
        data-offset (align-up @(:position reader) alignment)
        _ (when-not (= (count metadata) metadata-count)
            (throw (ex-info "GGUF contains duplicate metadata keys" {})))
        _ (when-not (every? #(zero? (mod (:offset %) alignment)) tensors)
            (throw (ex-info "GGUF tensor offset violates general.alignment"
                            {:alignment alignment})))
        tensors (mapv #(assoc % :data-offset (+ data-offset (:offset %))) tensors)]
    (when-not (= (count (set (map :name tensors))) tensor-count)
      (throw (ex-info "GGUF contains duplicate tensor names" {})))
    {:version version :metadata metadata :tensors tensors
     :tensor-map (into {} (map (juxt :name identity) tensors))
     :alignment alignment :data-offset data-offset :source source}))

(defn parse-bytes [bytes]
  (parse-source {:bytes bytes}))

(defn load-file [path]
  (parse-source {:path (Paths/get (str path) (make-array String 0))}))

(defn- nelems [shape] (reduce * 1 shape))

(defn- tensor-byte-count [{:keys [type shape]}]
  (let [n (nelems shape)
        row-width (long (or (last shape) 1))
        require-row-block!
        (fn [block label]
          (when-not (zero? (mod row-width block))
            (throw (ex-info (str label " tensor row width must be divisible by " block)
                            {:shape shape :row-width row-width :block block}))))]
    (case type
      :f32 (* 4 n)
      :f16 (* 2 n)
      ;; Legacy Q5_0 is encoded as consecutive 32-value blocks across the
      ;; complete tensor. Small GGUF matrices can therefore have logical rows
      ;; narrower than a block (the public tiny-random Llama uses width 16).
      :q5-0 (do (when-not (zero? (mod n 32))
                  (throw (ex-info "Q5_0 tensor size must be divisible by 32"
                                  {:shape shape :elements n :block 32})))
                ;; fp16 scale + 32 high bits + 32 packed low nibbles
                (* 22 (quot n 32)))
      :q8-0 (do (when-not (zero? (mod n 32))
                  (throw (ex-info "Q8_0 tensor size must be divisible by 32"
                                  {:shape shape :elements n :block 32})))
                (* 34 (quot n 32)))
      :q4-k (do (require-row-block! 256 "Q4_K")
                ;; fp16 d + fp16 dmin + 12 packed scale/min bytes + 128 quants
                (* 144 (quot n 256)))
      :q6-k (do (require-row-block! 256 "Q6_K")
                ;; 128 low-nibble + 64 high-2-bit + 16 int8 scales + fp16 d
                (* 210 (quot n 256)))
      (throw (ex-info "unsupported GGUF tensor type" {:type type})))))

(defn- q4-k-scale-min
  "Unpack one of eight 6-bit `(scale,min)` pairs from Q4_K's 12-byte table."
  [scales index]
  (if (< index 4)
    [(bit-and (nth scales index) 0x3f)
     (bit-and (nth scales (+ index 4)) 0x3f)]
    [(bit-or (bit-and (nth scales (+ index 4)) 0x0f)
             (bit-shift-left (bit-shift-right (nth scales (- index 4)) 6) 4))
     (bit-or (bit-shift-right (nth scales (+ index 4)) 4)
             (bit-shift-left (bit-shift-right (nth scales index) 6) 4))]))

(defn- decode-q4-k-block [^ByteBuffer buffer]
  (let [d (dtype/f16-bits->f32 (.getShort buffer))
        dmin (dtype/f16-bits->f32 (.getShort buffer))
        scales (mapv (fn [_] (bit-and 0xff (int (.get buffer)))) (range 12))
        quants (mapv (fn [_] (bit-and 0xff (int (.get buffer)))) (range 128))]
    (vec
     (mapcat
      (fn [pair]
        (let [[scale0 min0] (q4-k-scale-min scales (* 2 pair))
              [scale1 min1] (q4-k-scale-min scales (inc (* 2 pair)))
              offset (* pair 32)]
          (concat
           (map (fn [byte]
                  (- (* d scale0 (bit-and byte 0x0f)) (* dmin min0)))
                (subvec quants offset (+ offset 32)))
           (map (fn [byte]
                  (- (* d scale1 (bit-shift-right byte 4)) (* dmin min1)))
                (subvec quants offset (+ offset 32))))))
      (range 4)))))

(defn- decode-q6-k-block [^ByteBuffer buffer]
  (let [low (mapv (fn [_] (bit-and 0xff (int (.get buffer)))) (range 128))
        high (mapv (fn [_] (bit-and 0xff (int (.get buffer)))) (range 64))
        scales (mapv (fn [_] (int (.get buffer))) (range 16))
        d (dtype/f16-bits->f32 (.getShort buffer))
        output (double-array 256)]
    (dotimes [half 2]
      (let [low-offset (* half 64) high-offset (* half 32)
            scale-offset (* half 8) output-offset (* half 128)]
        (dotimes [l 32]
          (let [scale-pair (quot l 16)
                lo0 (nth low (+ low-offset l))
                lo1 (nth low (+ low-offset l 32))
                hi (nth high (+ high-offset l))
                quants [(- (bit-or (bit-and lo0 0x0f)
                                   (bit-shift-left (bit-and hi 0x03) 4)) 32)
                        (- (bit-or (bit-and lo1 0x0f)
                                   (bit-shift-left (bit-and (bit-shift-right hi 2) 0x03) 4)) 32)
                        (- (bit-or (bit-shift-right lo0 4)
                                   (bit-shift-left (bit-and (bit-shift-right hi 4) 0x03) 4)) 32)
                        (- (bit-or (bit-shift-right lo1 4)
                                   (bit-shift-left (bit-shift-right hi 6) 4)) 32)]
                positions [l (+ l 32) (+ l 64) (+ l 96)]
                scale-indices [(+ scale-offset scale-pair)
                               (+ scale-offset scale-pair 2)
                               (+ scale-offset scale-pair 4)
                               (+ scale-offset scale-pair 6)]]
            (dotimes [group 4]
              (aset output (+ output-offset (nth positions group))
                    (* d (nth scales (nth scale-indices group))
                       (nth quants group))))))))
    (vec output)))

(defn- decode-tensor [{:keys [type shape data-offset] :as tensor} source]
  (let [bytes (read-range source data-offset (tensor-byte-count tensor))
        buffer (little-buffer bytes)
        n (nelems shape)]
    (case type
      :f32 (mapv (fn [_] (double (.getFloat buffer))) (range n))
      :f16 (mapv (fn [_] (dtype/f16-bits->f32 (.getShort buffer))) (range n))
      :q5-0
      (vec
       (mapcat
        (fn [_]
          (let [scale (dtype/f16-bits->f32 (.getShort buffer))
                high (bit-and 0xffffffff (.getInt buffer))
                low (mapv (fn [_] (bit-and 0xff (int (.get buffer)))) (range 16))]
            (concat
             (map-indexed
              (fn [index byte]
                (* scale (- (bit-or (bit-and byte 0x0f)
                                    (bit-shift-left (bit-and (unsigned-bit-shift-right high index) 1) 4))
                            16)))
              low)
             (map-indexed
              (fn [index byte]
                (* scale (- (bit-or (bit-shift-right byte 4)
                                    (bit-shift-left (bit-and (unsigned-bit-shift-right high (+ index 16)) 1) 4))
                            16)))
              low))))
        (range (quot n 32))))
      :q8-0
      (vec (mapcat (fn [_]
                     (let [scale (dtype/f16-bits->f32 (.getShort buffer))]
                       (mapv (fn [_] (* scale (int (.get buffer)))) (range 32))))
                   (range (quot n 32))))
      :q4-k (vec (mapcat (fn [_] (decode-q4-k-block buffer))
                         (range (quot n 256))))
      :q6-k (vec (mapcat (fn [_] (decode-q6-k-block buffer))
                         (range (quot n 256)))))))

(defn read-tensor
  "Decode a named F32/F16/Q5_0/Q8_0/Q4_K/Q6_K tensor to `{:shape :values :type}`."
  [gguf name]
  (let [tensor (get-in gguf [:tensor-map name])]
    (when-not tensor
      (throw (ex-info "GGUF tensor not found" {:name name})))
    (assoc tensor :values (decode-tensor tensor (:source gguf)))))

(defn read-packed-tensor
  "Read exact encoded bytes without dequantizing."
  [gguf name]
  (let [tensor (get-in gguf [:tensor-map name])]
    (when-not tensor
      (throw (ex-info "GGUF tensor not found" {:name name})))
    (when-not (#{:q5-0 :q4-k :q6-k :q8-0} (:type tensor))
      (throw (ex-info "GGUF tensor is not packed quantized data"
                      {:name name :type (:type tensor)})))
    (assoc tensor :bytes
           (mapv #(bit-and 0xff %)
                 (read-range (:source gguf) (:data-offset tensor)
                             (tensor-byte-count tensor))))))

(defn llama-config
  "Extract the Llama architecture fields required by torch's decoder model."
  [gguf]
  (let [m (:metadata gguf)
        architecture (get m "general.architecture")
        head-count (get m "llama.attention.head_count")
        config {:architecture architecture
                :block-count (get m "llama.block_count")
                :embed-dim (get m "llama.embedding_length")
                :hidden-dim (get m "llama.feed_forward_length")
                :head-count head-count
                :kv-head-count (get m "llama.attention.head_count_kv" head-count)
                :context-length (get m "llama.context_length")
                :rope-theta (double (get m "llama.rope.freq_base" 10000.0))}]
    (when-not (and (= architecture "llama")
                   (every? pos-int? ((juxt :block-count :embed-dim :hidden-dim
                                           :head-count :kv-head-count
                                           :context-length) config))
                   (zero? (mod (:embed-dim config) (:head-count config)))
                   (zero? (mod (:head-count config) (:kv-head-count config))))
      (throw (ex-info "GGUF lacks a valid Llama architecture configuration"
                      config)))
    config))

(defn llama-model
  "Construct a complete torch.model Llama LM description from GGUF metadata."
  [gguf]
  (let [{:keys [block-count embed-dim hidden-dim head-count kv-head-count rope-theta]}
        (llama-config gguf)
        vocab (count (get-in gguf [:metadata "tokenizer.ggml.tokens"]))]
    (apply model/sequential
           (concat [(model/embedding vocab embed-dim)]
                   (repeat block-count
                           (model/llama-block embed-dim head-count hidden-dim
                                              {:rope-theta rope-theta
                                               :kv-heads kv-head-count}))
                   [(model/rmsnorm embed-dim) (model/lm-head embed-dim vocab)]))))

(defn gguf-tokenizer
  "Build torch.tokenizer from standard GGUF tokenizer metadata."
  [gguf]
  (let [m (:metadata gguf)
        tokens (vec (get m "tokenizer.ggml.tokens"))
        tokenizer-model (get m "tokenizer.ggml.model")
        merges (mapv #(vec (str/split % #" " 2))
                     (get m "tokenizer.ggml.merges" []))]
    (tokenizer/tokenizer
     {:tokens tokens :merges merges
      :scores (vec (get m "tokenizer.ggml.scores" []))
      :model (when (= tokenizer-model "llama") :sentencepiece)
      :space-prefix (when (= tokenizer-model "llama") "▁")
      :unk-id (get m "tokenizer.ggml.unknown_token_id")
      :bos-id (get m "tokenizer.ggml.bos_token_id")
      :eos-id (get m "tokenizer.ggml.eos_token_id")
      :add-bos? (get m "tokenizer.ggml.add_bos_token" true)
      :add-eos? (get m "tokenizer.ggml.add_eos_token" false)})))

(defn- upload-vector [gguf backend name]
  (let [tensor (get-in gguf [:tensor-map name])]
    (if (and (= 2 (count (:shape tensor)))
             (#{:q5-0 :q4-k :q6-k :q8-0} (:type tensor))
             (or (= :q5-0 (:type tensor))
                 (zero? (mod (last (:shape tensor))
                             (if (= :q8-0 (:type tensor)) 32 256)))))
      (let [{:keys [shape bytes]} (read-packed-tensor gguf name)]
        (try
          (quantized/table backend bytes shape (:type tensor))
          (catch clojure.lang.ExceptionInfo error
            ;; Allows torch to remain usable with an older pinned num while a
            ;; newly added packed format rolls forward; only Q5_0 has a dense
            ;; compatibility path here.
            (if (= :q5-0 (:type tensor))
              (let [{:keys [shape values]} (read-tensor gguf name)]
                (arr/from-vec backend values shape))
              (throw error)))))
      (let [{:keys [shape values]} (read-tensor gguf name)]
        (arr/from-vec backend values shape)))))

(defn load-matrix
  "GGUF linear matrices are `[out in]`; torch.num-backend uses `[in out]`."
  [gguf backend name]
  (let [tensor (get-in gguf [:tensor-map name])]
    (if (and (#{:q5-0 :q4-k :q6-k :q8-0} (:type tensor))
             (or (= :q5-0 (:type tensor))
                 (zero? (mod (last (:shape tensor))
                             (if (= :q8-0 (:type tensor)) 32 256)))))
      (let [{:keys [shape bytes]} (read-packed-tensor gguf name)]
        (try
          (quantized/matrix backend bytes shape (:type tensor))
          (catch clojure.lang.ExceptionInfo error
            (if (= :q5-0 (:type tensor))
              (let [{[out in] :shape values :values} (read-tensor gguf name)
                    transposed (mapv (fn [i]
                                       (let [input-index (quot i out)
                                             output-index (mod i out)]
                                         (nth values (+ (* output-index in)
                                                        input-index))))
                                     (range (* in out)))]
                (arr/from-vec backend transposed [in out]))
              (throw error)))))
      (let [{[out in] :shape values :values} (read-tensor gguf name)
            transposed (mapv (fn [i]
                               (let [input-index (quot i out) output-index (mod i out)]
                                 (nth values (+ (* output-index in) input-index))))
                             (range (* in out)))]
        (arr/from-vec backend transposed [in out])))))

(defn load-llama-weights
  "Decode and upload standard Llama GGUF tensors into the weights vector used
  by `torch.num-backend`. Supported row-addressable and cross-row Q5_0 tensors
  remain packed; incompatible legacy layouts fall back to decoded f32."
  [gguf backend]
  (let [{:keys [block-count]} (llama-config gguf)
        embedding (upload-vector gguf backend "token_embd.weight")
        blocks
        (mapv (fn [index]
                (let [prefix (str "blk." index ".")]
                  {:attn-norm (upload-vector gguf backend (str prefix "attn_norm.weight"))
                   :qw (load-matrix gguf backend (str prefix "attn_q.weight"))
                   :kw (load-matrix gguf backend (str prefix "attn_k.weight"))
                   :vw (load-matrix gguf backend (str prefix "attn_v.weight"))
                   :ow (load-matrix gguf backend (str prefix "attn_output.weight"))
                   :ffn-norm (upload-vector gguf backend (str prefix "ffn_norm.weight"))
                   :gate (load-matrix gguf backend (str prefix "ffn_gate.weight"))
                   :up (load-matrix gguf backend (str prefix "ffn_up.weight"))
                   :down (load-matrix gguf backend (str prefix "ffn_down.weight"))}))
              (range block-count))
        final-norm (upload-vector gguf backend "output_norm.weight")
        output (if (contains? (:tensor-map gguf) "output.weight")
                 (load-matrix gguf backend "output.weight")
                 ;; Tied embeddings need a transposed materialization for the
                 ;; engine's `[embed vocab]` LM-head layout.
                 (if (quantized/table? embedding)
                   (quantized/as-matrix embedding)
                   (let [[vocab embed] (:shape embedding)
                         values (vec (arr/->vec embedding))
                         transposed (mapv (fn [i]
                                            (let [e (quot i vocab) v (mod i vocab)]
                                              (nth values (+ (* v embed) e))))
                                          (range (* vocab embed)))]
                     (arr/from-vec backend transposed [embed vocab]))))]
    (vec (concat [{:w embedding}] blocks [{:w final-norm} {:w output}]))))
