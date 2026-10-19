(ns torch.safetensors
  "Validated safetensors reader/writer and torch state-dict loader."
  (:require [json.data-json :as json]
            [clojure.set :as set]
            [num.array :as arr]
            [torch.state-dict :as state])
  (:import [java.io Closeable RandomAccessFile]
           [java.nio ByteBuffer ByteOrder]
           [java.nio.channels FileChannel]
           [java.nio.charset StandardCharsets]
           [java.nio.file Files Path StandardCopyOption StandardOpenOption]
           [java.nio.file.attribute FileAttribute]))

(def ^:private max-header-bytes (* 128 1024 1024))
(def ^:private dtype-bytes
  {"F16" 2 "BF16" 2 "F32" 4 "F64" 8
   "I8" 1 "U8" 1 "I16" 2 "U16" 2 "I32" 4 "U32" 4 "I64" 8 "U64" 8})

(defrecord SafeTensorFile
  [^RandomAccessFile file ^FileChannel channel path data-start tensors metadata]
  Closeable
  (close [_] (.close channel) (.close file)))

(defrecord SafeTensorIndex [path metadata tensor-map shards]
  Closeable
  (close [_]
    (doseq [checkpoint (vals shards)]
      (.close ^Closeable checkpoint))))

(defn- read-fully-at! [^FileChannel channel ^ByteBuffer buffer position]
  (loop [position (long position)]
    (when (.hasRemaining buffer)
      (let [read-count (.read channel buffer position)]
        (when (neg? read-count)
          (throw (ex-info "torch.safetensors: truncated file"
                          {:position position})))
        (recur (+ position read-count)))))
  (.flip buffer)
  buffer)

(defn- nelems [shape] (reduce * 1 (map long shape)))

(defn open-file [path]
  (let [file (RandomAccessFile. (str path) "r") channel (.getChannel file)]
    (try
      (let [length-buffer (doto (ByteBuffer/allocate 8)
                            (.order ByteOrder/LITTLE_ENDIAN))
            _ (read-fully-at! channel length-buffer 0)
            header-length (.getLong length-buffer)
            file-length (.size channel)]
        (when (or (neg? header-length) (> header-length max-header-bytes)
                  (> (+ 8 header-length) file-length))
          (throw (ex-info "torch.safetensors: invalid header length"
                          {:header-length header-length :file-length file-length})))
        (let [header-buffer (ByteBuffer/allocate (int header-length))
              _ (read-fully-at! channel header-buffer 8)
              header (json/read-str
                      (String. (.array header-buffer) StandardCharsets/UTF_8))
              _ (when-not (map? header)
                  (throw (ex-info "torch.safetensors: header must be an object"
                                  {:path (str path)})))
              metadata (get header "__metadata__" {})
              tensors (dissoc header "__metadata__")
              data-start (+ 8 header-length)
              payload-length (- file-length data-start)]
          (when-not (and (map? metadata)
                         (every? string? (keys metadata))
                         (every? string? (vals metadata)))
            (throw (ex-info "torch.safetensors: invalid header metadata"
                            {:path (str path)})))
          (doseq [[name {:strs [dtype shape data_offsets] :as entry}] tensors]
            (let [[start end] data_offsets
                  valid-shape? (and (vector? shape)
                                    (every? #(and (integer? %) (not (neg? %))) shape))
                  width (get dtype-bytes dtype)
                  expected (when (and width valid-shape?)
                             (* width (nelems shape)))]
              (when-not (and (string? name) (not (.isBlank ^String name))
                             width valid-shape? (= 2 (count data_offsets))
                             (integer? start) (integer? end)
                             (<= 0 start end payload-length)
                             (= expected (- end start)))
                (throw (ex-info "torch.safetensors: invalid tensor entry"
                                {:tensor name :entry entry
                                 :payload-length payload-length
                                 :expected-bytes expected})))))
          (let [windows (sort-by (juxt first second last)
                                 (map (fn [[name entry]]
                                        (let [[start end] (get entry "data_offsets")]
                                          [start end name]))
                                      tensors))
                covered (reduce (fn [cursor [start end name]]
                                  (when-not (= cursor start)
                                    (throw (ex-info
                                            "torch.safetensors: tensor windows overlap or leave holes"
                                            {:tensor name :expected-offset cursor
                                             :actual-offset start})))
                                  end)
                                0 windows)]
            (when-not (= covered payload-length)
              (throw (ex-info "torch.safetensors: unreferenced payload bytes"
                              {:covered covered :payload-length payload-length}))))
          (->SafeTensorFile file channel (str path) data-start tensors metadata)))
      (catch Throwable error
        (.close channel) (.close file) (throw error)))))

(defn- index-path? [path]
  (.endsWith (str path) ".safetensors.index.json"))

(defn- safe-shard-path [^Path parent shard-name]
  (when-not (and (string? shard-name)
                 (not (.isBlank ^String shard-name))
                 (.endsWith ^String shard-name ".safetensors"))
    (throw (ex-info "torch.safetensors: invalid shard filename"
                    {:shard shard-name})))
  (let [candidate (.normalize (.resolve parent ^String shard-name))]
    (when (or (.isAbsolute (Path/of shard-name (make-array String 0)))
              (not (.startsWith candidate parent)))
      (throw (ex-info "torch.safetensors: shard escapes index directory"
                      {:shard shard-name :directory (str parent)})))
    (let [options (make-array java.nio.file.LinkOption 0)
          real-parent (.toRealPath parent options)
          real-candidate (.toRealPath candidate options)]
      (when-not (.startsWith real-candidate real-parent)
        (throw (ex-info "torch.safetensors: shard symlink escapes index directory"
                        {:shard shard-name :directory (str real-parent)})))
      real-candidate)))

(defn open-index
  "Open a Hugging Face sharded safetensors index. Every declared shard is
  confined to the index directory and its actual tensor names must match the
  `weight_map` exactly. Payloads remain lazy in each RandomAccessFile."
  [path]
  (let [index-path (.toAbsolutePath ^Path (if (instance? Path path)
                                            path (Path/of (str path) (make-array String 0))))
        length (Files/size index-path)]
    (when (> length max-header-bytes)
      (throw (ex-info "torch.safetensors: index is too large"
                      {:bytes length :limit max-header-bytes})))
    (let [document (json/read-str (String. (Files/readAllBytes index-path)
                                           StandardCharsets/UTF_8))
          weight-map (get document "weight_map")
          metadata (get document "metadata" {})]
      (when-not (and (map? document) (map? metadata)
                     (map? weight-map) (seq weight-map)
                     (every? #(and (string? %) (not (.isBlank ^String %)))
                             (keys weight-map)))
        (throw (ex-info "torch.safetensors: invalid sharded index"
                        {:path (str index-path)})))
      (let [parent (.normalize (.getParent index-path))
            shard-paths (into (sorted-map)
                              (map (fn [name] [name (safe-shard-path parent name)]))
                              (set (vals weight-map)))
            opened (atom {})]
        (try
          (doseq [[name shard-path] shard-paths]
            (swap! opened assoc name (open-file shard-path)))
          (let [actual-locations
                (reduce-kv
                 (fn [locations shard checkpoint]
                   (reduce (fn [m tensor]
                             (update m tensor (fnil conj []) shard))
                           locations (keys (:tensors checkpoint))))
                 {} @opened)
                declared (set (keys weight-map))
                actual (set (keys actual-locations))
                missing (vec (sort (set/difference declared actual)))
                unexpected (vec (sort (set/difference actual declared)))
                ambiguous (into (sorted-map)
                                (filter (fn [[_ locations]]
                                          (> (count locations) 1)))
                                actual-locations)
                misplaced (into (sorted-map)
                                (keep (fn [[tensor shard]]
                                        (when (and (contains? actual tensor)
                                                   (not= [shard]
                                                         (get actual-locations tensor)))
                                          [tensor {:declared shard
                                                   :actual (get actual-locations tensor)}])))
                                weight-map)]
            (when (or (seq missing) (seq unexpected) (seq ambiguous)
                      (seq misplaced))
              (throw (ex-info "torch.safetensors: index does not match shard contents"
                              {:missing missing :unexpected unexpected
                               :ambiguous ambiguous :misplaced misplaced})))
            (->SafeTensorIndex (str index-path) metadata weight-map @opened))
          (catch Throwable error
            (doseq [checkpoint (vals @opened)]
              (.close ^Closeable checkpoint))
            (throw error)))))))

(defn open-checkpoint
  "Open either one `.safetensors` file or a `.safetensors.index.json` manifest."
  [path]
  (if (index-path? path) (open-index path) (open-file path)))

(defn tensor-names [checkpoint]
  (vec (sort (keys (if (instance? SafeTensorIndex checkpoint)
                     (:tensor-map checkpoint) (:tensors checkpoint))))))

(defn tensor-info [checkpoint name]
  (if (instance? SafeTensorIndex checkpoint)
    (when-let [shard (get (:tensor-map checkpoint) name)]
      (tensor-info (get (:shards checkpoint) shard) name))
    (get (:tensors checkpoint) name)))

(defn- write-fully! [^FileChannel channel ^ByteBuffer buffer]
  (while (.hasRemaining buffer) (.write channel buffer)))

(defn- padded-header-bytes [header]
  (let [raw (.getBytes (json/write-str header) StandardCharsets/UTF_8)
        padded-length (* 8 (quot (+ (alength raw) 7) 8))
        padded (byte-array padded-length)]
    (java.util.Arrays/fill padded (byte 0x20))
    (System/arraycopy raw 0 padded 0 (alength raw))
    padded))

(defn- encode-tensor [array storage-dtype]
  (let [values (arr/->vec array)
        [dtype width put-value]
        (case (or storage-dtype (:dtype array))
          :f32 ["F32" 4 #(.putFloat ^ByteBuffer %1 (float %2))]
          :f64 ["F64" 8 #(.putDouble ^ByteBuffer %1 (double %2))]
          (throw (ex-info "torch.safetensors: unsupported output dtype"
                          {:dtype (:dtype array) :supported [:f32 :f64]})))
        buffer (doto (ByteBuffer/allocate (* width (count values)))
                 (.order ByteOrder/LITTLE_ENDIAN))]
    (doseq [value values] (put-value buffer value))
    (.flip buffer)
    {:dtype dtype :shape (mapv long (:shape array)) :buffer buffer}))

(defn write-file!
  "Write a deterministic safetensors file and atomically replace `path`.

  Tensor names are sorted, metadata keys and values must be strings, and each
  tensor's current f32/f64 dtype is preserved. Returns the destination Path."
  ([path tensors] (write-file! path tensors {} {}))
  ([path tensors metadata] (write-file! path tensors metadata {}))
  ([path tensors metadata {:keys [storage-dtype]}]
   (when-not (and (map? tensors) (seq tensors)
                  (every? string? (keys tensors)))
     (throw (ex-info "torch.safetensors: tensors must be a non-empty string-keyed map"
                     {:tensor-names (keys tensors)})))
   (when-not (and (map? metadata) (every? string? (keys metadata))
                  (every? string? (vals metadata)))
     (throw (ex-info "torch.safetensors: metadata must contain only strings"
                     {:metadata metadata})))
   (let [destination (.toAbsolutePath ^Path (if (instance? Path path)
                                               path (Path/of (str path) (make-array String 0))))
         parent (or (.getParent destination)
                    (.toAbsolutePath (Path/of "." (make-array String 0))))
         encoded (mapv (fn [[name array]]
                         [name (encode-tensor array storage-dtype)])
                       (sort-by key tensors))
         [header _]
         (reduce (fn [[entries offset] [name {:keys [dtype shape buffer]}]]
                   (let [end (+ offset (.remaining ^ByteBuffer buffer))]
                     [(assoc entries name {"dtype" dtype "shape" shape
                                           "data_offsets" [offset end]})
                      end]))
                 [(cond-> {} (seq metadata) (assoc "__metadata__" metadata)) 0]
                 encoded)
         header-bytes (padded-header-bytes header)
         temp (Files/createTempFile parent ".torch-safetensors-" ".tmp"
                                    (make-array FileAttribute 0))]
     (try
       (with-open [channel (FileChannel/open temp
                                             (into-array StandardOpenOption
                                                         [StandardOpenOption/WRITE
                                                          StandardOpenOption/TRUNCATE_EXISTING]))]
         (write-fully! channel (doto (ByteBuffer/allocate 8)
                                (.order ByteOrder/LITTLE_ENDIAN)
                                (.putLong (long (alength header-bytes)))
                                (.flip)))
         (write-fully! channel (ByteBuffer/wrap header-bytes))
         (doseq [[_ {:keys [buffer]}] encoded]
           (write-fully! channel (.duplicate ^ByteBuffer buffer)))
         (.force channel true))
       (try
         (Files/move temp destination
                     (into-array StandardCopyOption
                                 [StandardCopyOption/ATOMIC_MOVE
                                  StandardCopyOption/REPLACE_EXISTING]))
         (catch java.nio.file.AtomicMoveNotSupportedException _
           (Files/move temp destination
                       (into-array StandardCopyOption
                                   [StandardCopyOption/REPLACE_EXISTING]))))
       destination
       (finally (Files/deleteIfExists temp))))))

(defn save-weights!
  "Save model weights under stable PyTorch-compatible state-dict names."
  ([path model* weights] (save-weights! path model* weights {}))
  ([path model* weights metadata]
   (write-file! path (state/state-dict model* weights)
                (merge {"format" "pt" "torch-clj.kind" "model"} metadata))))

(defn- half->double [bits]
  (let [sign (if (zero? (bit-and bits 0x8000)) 1.0 -1.0)
        exponent (bit-and (unsigned-bit-shift-right bits 10) 0x1f)
        fraction (bit-and bits 0x3ff)]
    (cond
      (zero? exponent) (* sign (Math/pow 2.0 -14.0) (/ fraction 1024.0))
      (= exponent 31) (if (zero? fraction)
                        (* sign Double/POSITIVE_INFINITY) Double/NaN)
      :else (* sign (Math/pow 2.0 (- exponent 15.0))
               (+ 1.0 (/ fraction 1024.0))))))

(defn- decode-values [^ByteBuffer buffer dtype count]
  (mapv (fn [_]
          (double
           (case dtype
             "F16" (half->double (bit-and 0xffff (int (.getShort buffer))))
             "BF16" (Float/intBitsToFloat
                      (unchecked-int
                       (bit-shift-left (bit-and 0xffff (int (.getShort buffer))) 16)))
             "F32" (.getFloat buffer) "F64" (.getDouble buffer)
             "I8" (.get buffer) "U8" (bit-and 0xff (int (.get buffer)))
             "I16" (.getShort buffer)
             "U16" (bit-and 0xffff (int (.getShort buffer)))
             "I32" (.getInt buffer) "U32" (Integer/toUnsignedLong (.getInt buffer))
             "I64" (.getLong buffer)
             "U64" (Double/parseDouble (Long/toUnsignedString (.getLong buffer))))))
        (range count)))

(defn read-tensor-data
  "Decode one tensor window without uploading it. Returns dtype, shape, and a
  flat row-major value vector; useful at checkpoint-layout conversion seams."
  [checkpoint name]
  (if (instance? SafeTensorIndex checkpoint)
    (if-let [shard (get (:tensor-map checkpoint) name)]
      (read-tensor-data (get (:shards checkpoint) shard) name)
      (throw (ex-info "torch.safetensors: tensor not found"
                      {:tensor name :path (:path checkpoint)})))
    (let [{:strs [dtype shape data_offsets] :as info} (tensor-info checkpoint name)]
      (when-not info
        (throw (ex-info "torch.safetensors: tensor not found"
                        {:tensor name :path (:path checkpoint)})))
      (let [[start end] data_offsets
            length (- end start)]
        (when (> length Integer/MAX_VALUE)
          (throw (ex-info "torch.safetensors: tensor exceeds JVM decode window"
                          {:tensor name :bytes length
                           :limit Integer/MAX_VALUE})))
        (let [buffer (doto (ByteBuffer/allocate (int length))
                       (.order ByteOrder/LITTLE_ENDIAN))]
          (read-fully-at! (:channel checkpoint) buffer
                          (+ (:data-start checkpoint) start))
          {:dtype dtype :shape (mapv long shape)
           :values (decode-values buffer dtype (nelems shape))})))))

(defn read-tensor
  "Read one tensor window and upload it to `backend`. `target-dtype` defaults
  to f32 so f16/bf16 checkpoints can execute through current f32 kernels."
  ([checkpoint backend name] (read-tensor checkpoint backend name :f32))
  ([checkpoint backend name target-dtype]
   (let [{:keys [shape values]} (read-tensor-data checkpoint name)]
     (arr/from-vec backend values shape target-dtype))))

(defn load-weights
  "Load exactly the tensors required by `model*` and return its aligned weight
  vector. Strict mode rejects unrelated checkpoint tensors."
  ([path backend model*] (load-weights path backend model* {}))
  ([path backend model* {:keys [strict? dtype] :or {strict? true dtype :f32}}]
   (with-open [checkpoint (open-checkpoint path)]
     (let [required (set (map :name (state/manifest model*)))
           provided (set (tensor-names checkpoint))
           missing (vec (sort (set/difference required provided)))
           unexpected (vec (sort (set/difference provided required)))]
       (when (seq missing)
         (throw (ex-info "torch.safetensors: missing tensors" {:missing missing})))
       (when (and strict? (seq unexpected))
         (throw (ex-info "torch.safetensors: unexpected tensors"
                         {:unexpected unexpected})))
       (state/load-state-dict
        model*
        (into {} (map (fn [name]
                        [name (read-tensor checkpoint backend name dtype)]))
              required)
        {:strict? false})))))
