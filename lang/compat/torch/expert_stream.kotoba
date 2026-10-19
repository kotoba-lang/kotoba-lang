(ns torch.expert-stream
  "Host-side NVMe expert slice reader.

  Tensor math stays in num and model structure stays in torch. This namespace
  owns the JVM resource boundary: positional reads, direct buffers, bounded
  resident bytes and asynchronous next-layer prefetch."
  (:require [num.expert-cache :as cache])
  (:import [java.io Closeable]
           [java.nio ByteBuffer]
           [java.nio.channels FileChannel]
           [java.nio.file Path StandardOpenOption]
           [java.util.concurrent Executors ExecutorService Future]))

(defrecord ExpertStream
  [^FileChannel channel path file-bytes windows state buffers key-locks bookkeeping-lock
   ^ExecutorService executor]
  Closeable
  (close [_]
    (.shutdownNow executor)
    (reset! buffers {})
    (.close channel)))

(defn- path* [path]
  (if (instance? Path path) path (Path/of (str path) (make-array String 0))))

(defn open-stream
  "Open a file and a key→`{:offset :bytes}` expert-slice index.

  The byte budget is a hard ceiling for retained direct buffers. `io-threads`
  controls speculative reads only; synchronous misses use positional reads on
  the same thread that requested the expert."
  [path windows {:keys [cache-bytes io-threads]
                 :or {cache-bytes 0 io-threads 2}}]
  (let [p (path* path)
        channel (FileChannel/open p (into-array StandardOpenOption
                                                 [StandardOpenOption/READ]))
        size (.size channel)]
    (try
      (doseq [[key {:keys [offset bytes]}] windows]
        (when-not (and (integer? offset) (integer? bytes)
                       (<= 0 offset) (pos? bytes) (<= (+ offset bytes) size)
                       (<= bytes Integer/MAX_VALUE))
          (throw (ex-info "expert window is outside the file"
                          {:key key :offset offset :bytes bytes
                           :file-bytes size}))))
      (->ExpertStream channel (str p) size windows
                      (atom (cache/new-cache cache-bytes)) (atom {}) (atom {}) (Object.)
                      (Executors/newFixedThreadPool (max 1 (int io-threads))))
      (catch Throwable error
        (.close channel)
        (throw error)))))

(defn- read-window! [^ExpertStream stream {:keys [offset bytes]} ^ByteBuffer buffer]
  (loop [position (long offset)]
    (when (.hasRemaining buffer)
      (let [n (.read ^FileChannel (:channel stream) buffer position)]
        (when (neg? n)
          (throw (ex-info "expert slice was truncated"
                          {:path (:path stream) :offset offset :bytes bytes})))
        (recur (+ position n)))))
  (.flip buffer)
  buffer)

(defn- key-lock [^ExpertStream stream key]
  (or (get @(:key-locks stream) key)
      (get (swap! (:key-locks stream)
                  #(if (contains? % key) % (assoc % key (Object.))))
           key)))

(defn acquire!
  "Return a read-only direct buffer for one expert slice.

  The returned buffer has independent position/limit state. A `:bypass`
  decision is intentionally not retained after this call."
  [^ExpertStream stream key]
  (let [{:keys [bytes] :as window} (or (get (:windows stream) key)
                                       (throw (ex-info "unknown expert slice"
                                                       {:key key})))]
    (locking (key-lock stream key)
      (let [{:keys [status buffer]}
            (locking (:bookkeeping-lock stream)
              (let [decision* (volatile! nil)]
                (swap! (:state stream)
                       (fn [state]
                         (let [[next-state decision] (cache/access state key bytes)]
                           (vreset! decision* decision)
                           next-state)))
                (let [{:keys [status evicted]} @decision*
                      _ (swap! (:buffers stream) #(apply dissoc % evicted))
                      buffer (if (= :hit status)
                               (or (get @(:buffers stream) key)
                                   (throw (ex-info "cache metadata/buffer divergence"
                                                   {:key key})))
                               (ByteBuffer/allocateDirect (int bytes)))]
                  ;; Publish the allocation before another key can evict it.
                  ;; If it is evicted while its positional read is in flight,
                  ;; the caller still owns this buffer but it is not retained.
                  (when (= :miss status)
                    (swap! (:buffers stream) assoc key buffer))
                  {:status status :buffer buffer})))]
        (when-not (= :hit status)
          (read-window! stream window buffer))
        (.asReadOnlyBuffer ^ByteBuffer buffer)))))

(defn prefetch!
  "Schedule unique expert keys on the bounded I/O executor.

  Returns Java Futures so the decoder can join only at the expert matmul
  boundary and overlap routing/attention with NVMe reads."
  [^ExpertStream stream keys]
  (mapv (fn [key]
          (.submit ^ExecutorService (:executor stream)
                   ^java.util.concurrent.Callable
                   (reify java.util.concurrent.Callable
                     (call [_] (acquire! stream key)))))
        (distinct keys)))

(defn await!
  "Join prefetch futures and return their read-only buffers."
  [futures]
  (mapv #(.get ^Future %) futures))

(defn stats [^ExpertStream stream]
  (let [state @(:state stream)]
    {:path (:path stream)
     :file-bytes (:file-bytes stream)
     :budget-bytes (:budget-bytes state)
     :resident-bytes (:resident-bytes state)
     :resident-slices (count (:entries state))
     :hit-rate (cache/hit-rate state)
     :metrics (:metrics state)}))

(defn split-placement
  "Return the execution placement required by pointer-rebound expert streams.

  Expert and PLE tensors must remain CPU-backed; only non-expert tensors are
  accelerator eligible. The runtime-specific layer count stays explicit and
  defaults to zero so a unified-memory host cannot opt in accidentally."
  [{:keys [gpu-layers expert-buffer ple-buffer]
    :or {gpu-layers 0 expert-buffer :cpu ple-buffer :cpu}}]
  (when-not (and (integer? gpu-layers) (not (neg? gpu-layers)))
    (throw (ex-info "gpu-layers must be a non-negative integer"
                    {:gpu-layers gpu-layers})))
  (when-not (= :cpu expert-buffer)
    (throw (ex-info "streamed expert tensors must remain CPU-backed"
                    {:expert-buffer expert-buffer})))
  (when-not (= :cpu ple-buffer)
    (throw (ex-info "streamed PLE table must remain CPU-backed"
                    {:ple-buffer ple-buffer})))
  {:torch/gpu-layers gpu-layers
   :torch/expert-buffer :cpu
   :torch/ple-buffer :cpu
   :torch/non-expert-buffer (if (pos? gpu-layers) :metal :cpu)})
