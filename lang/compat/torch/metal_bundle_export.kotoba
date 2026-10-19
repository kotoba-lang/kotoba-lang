(ns torch.metal-bundle-export
  "Streaming JVM writer for portable TGBNDL1 Metal bundles."
  (:require [num.array :as arr]
            [num.quantized :as quantized])
  (:import [java.io BufferedInputStream BufferedOutputStream FileInputStream
            FileOutputStream]
           [java.nio ByteBuffer ByteOrder]
           [java.nio.charset StandardCharsets]
           [java.nio.file Files Path StandardCopyOption]
           [java.nio.file.attribute FileAttribute]))

(def ^:private magic (.getBytes "TGBNDL1\n" StandardCharsets/US_ASCII))

(defn- write-dense! [^BufferedOutputStream output offset values]
  (let [values (vec values)
        length (* 4 (count values))
        buffer (doto (ByteBuffer/allocate (* 4 16384))
                 (.order ByteOrder/LITTLE_ENDIAN))]
    (loop [index 0]
      (when (< index (count values))
        (.clear buffer)
        (let [end (min (count values) (+ index 16384))]
          (doseq [i (range index end)]
            (.putFloat buffer (float (nth values i))))
          (.flip buffer)
          (.write output (.array buffer) 0 (.remaining buffer))
          (recur end))))
    {:offset offset :length length}))

(defn- write-bytes! [^BufferedOutputStream output offset bytes]
  (let [bytes (byte-array (map unchecked-byte bytes))]
    (.write output bytes)
    {:offset offset :length (alength bytes)}))

(defn- portable-weight! [output offset weight]
  (cond
    (quantized/matrix? weight)
    (let [payload (write-bytes! output offset (get-in weight [:handle :bytes]))]
      [{:kind :quantized-matrix :shape (:shape weight)
        :source-shape (vec (reverse (:shape weight)))
        :quant-type (:quant-type weight) :encoding :u8 :payload payload}
       (+ offset (:length payload))])

    (quantized/table? weight)
    (let [payload (write-bytes! output offset (get-in weight [:handle :bytes]))]
      [{:kind :quantized-table :shape (:shape weight)
        :quant-type (:quant-type weight) :encoding :u8 :payload payload}
       (+ offset (:length payload))])

    :else
    (let [payload (write-dense! output offset (arr/->vec weight))]
      [{:kind :dense :shape (:shape weight) :encoding :f32-le :payload payload}
       (+ offset (:length payload))])))

(defn- portable-weights! [output weights]
  (reduce
   (fn [[entries offset] entry]
     (let [[portable offset]
           (reduce-kv (fn [[result offset] key weight]
                        (let [[descriptor next-offset]
                              (portable-weight! output offset weight)]
                          [(assoc result key descriptor) next-offset]))
                      [{} offset] entry)]
       [(conj entries portable) offset]))
   [[] 0] weights))

(defn- write-u32-le! [^BufferedOutputStream output value]
  (when-not (<= 0 value 0xffffffff)
    (throw (ex-info "Metal bundle manifest exceeds u32 length"
                    {:bytes value})))
  (.write output
          (byte-array [(unchecked-byte (bit-and value 0xff))
                       (unchecked-byte (bit-and (bit-shift-right value 8) 0xff))
                       (unchecked-byte (bit-and (bit-shift-right value 16) 0xff))
                       (unchecked-byte (bit-and (bit-shift-right value 24) 0xff))])))

(defn export!
  "Atomically write `manifest` plus model `weights` without retaining a second
  full payload copy in heap. Returns the final manifest with tensor offsets."
  [destination manifest weights]
  (let [destination (.toAbsolutePath ^Path (if (instance? Path destination)
                                              destination
                                              (Path/of (str destination)
                                                       (make-array String 0))))
        parent (or (.getParent destination)
                   (.toAbsolutePath (Path/of "." (make-array String 0))))
        payload-path (Files/createTempFile parent ".torch-metal-payload-" ".tmp"
                                           (make-array FileAttribute 0))
        output-path (Files/createTempFile parent ".torch-metal-bundle-" ".tmp"
                                          (make-array FileAttribute 0))]
    (try
      (let [[portable payload-length]
            (with-open [payload (BufferedOutputStream.
                                 (FileOutputStream. (.toFile payload-path)))]
              (let [result (portable-weights! payload weights)]
                (.flush payload)
                result))
            manifest (assoc manifest :weights portable)
            manifest-bytes (.getBytes (pr-str manifest) StandardCharsets/UTF_8)]
        (when-not (= payload-length (Files/size payload-path))
          (throw (ex-info "Metal bundle payload length mismatch"
                          {:expected payload-length
                           :actual (Files/size payload-path)})))
        (with-open [file (FileOutputStream. (.toFile output-path))
                    output (BufferedOutputStream. file)
                    input (BufferedInputStream.
                           (FileInputStream. (.toFile payload-path)))]
          (.write output magic)
          (write-u32-le! output (alength manifest-bytes))
          (.write output manifest-bytes)
          (.transferTo input output)
          (.flush output)
          (.force (.getChannel file) true))
        (try
          (Files/move output-path destination
                      (into-array StandardCopyOption
                                  [StandardCopyOption/ATOMIC_MOVE
                                   StandardCopyOption/REPLACE_EXISTING]))
          (catch java.nio.file.AtomicMoveNotSupportedException _
            (Files/move output-path destination
                        (into-array StandardCopyOption
                                    [StandardCopyOption/REPLACE_EXISTING]))))
        manifest)
      (finally
        (Files/deleteIfExists payload-path)
        (Files/deleteIfExists output-path)))))
