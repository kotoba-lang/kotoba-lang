(ns kotodama.inference.host.native-kdot
  "JDK 24 FFM bridge to the mmap-friendly native K-quant matvec kernel."
  (:import (java.io File)
           (java.lang.foreign Arena FunctionDescriptor Linker Linker$Option MemoryLayout MemorySegment SymbolLookup ValueLayout)
           (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio.file Path StandardOpenOption)
           (java.util List)))

(defn platform-library-name []
  (case (System/getProperty "os.name")
    "Mac OS X" "libkotodama_kdot.dylib"
    "Linux" "libkotodama_kdot.so"
    (throw (ex-info "unsupported native kdot platform"
                    {:os (System/getProperty "os.name")}))))

(defn default-library-path []
  (.getAbsolutePath (File. (str "target/native/" (platform-library-name)))))

(defonce ^:private loaded-path (atom nil))
(defonce ^:private matvec-handle (atom nil))

(defn load-library!
  ([] (load-library! (default-library-path)))
  ([path]
   (let [absolute (.getAbsolutePath (File. path))]
     (when-not (= absolute @loaded-path)
       (System/load absolute)
       (let [symbol (-> (SymbolLookup/loaderLookup)
                        (.find "kotodama_kdot_matvec")
                        (.orElseThrow))
             descriptor (FunctionDescriptor/of
                         ValueLayout/JAVA_INT
                         (into-array MemoryLayout
                                     [ValueLayout/JAVA_INT
                                      ValueLayout/ADDRESS
                                      ValueLayout/JAVA_LONG
                                      ValueLayout/JAVA_LONG
                                      ValueLayout/ADDRESS
                                      ValueLayout/JAVA_LONG
                                      ValueLayout/ADDRESS]))
             handle (.downcallHandle (Linker/nativeLinker) symbol descriptor
                                     (make-array Linker$Option 0))]
         (reset! matvec-handle handle)
         (reset! loaded-path absolute)))
     absolute)))

(defn- invoke-matvec!
  [tensor-type ^MemorySegment weights rows cols ^floats input-flat positions ^floats output-flat]
  (when-not @matvec-handle (load-library!))
  (with-open [arena (Arena/ofConfined)]
    (let [input-heap (MemorySegment/ofArray input-flat)
          output-heap (MemorySegment/ofArray output-flat)
          input-segment (.allocate arena (.byteSize input-heap) 4)
          output-segment (.allocate arena (.byteSize output-heap) 4)
          _ (.copyFrom input-segment input-heap)
          status (.invokeWithArguments
                  ^java.lang.invoke.MethodHandle @matvec-handle
                  (List/of (object-array [(int tensor-type) weights (long rows) (long cols)
                                          input-segment (long positions) output-segment])))]
      (when-not (zero? (long status))
        (throw (ex-info "native kdot matvec failed" {:status status :type tensor-type
                                                     :rows rows :cols cols :positions positions})))
      (.copyFrom output-heap output-segment)
      output-flat)))

(defn matvec-bytes
  "Run native K-dot over an in-memory GGUF tensor byte array. Test/tooling API."
  [tensor-type ^bytes weights rows cols inputs]
  (let [positions (count inputs)
        input-flat (float-array (mapcat identity inputs))
        output-flat (float-array (* positions rows))]
    (with-open [arena (Arena/ofConfined)]
      (let [heap (MemorySegment/ofArray weights)
            native-weights (.allocate arena (.byteSize heap) 1)]
        (.copyFrom native-weights heap)
        (invoke-matvec! tensor-type native-weights rows cols input-flat positions output-flat)))
    (mapv (fn [p]
            (mapv #(double (aget output-flat (+ (* p rows) %))) (range rows)))
          (range positions))))

(defn matvec-mapped
  "Map only the requested GGUF tensor window and run native K-dot without
  copying or dequantizing its weights."
  [tensor-type model-path tensor-offset tensor-bytes rows cols inputs]
  (let [positions (count inputs)
        input-flat (float-array (mapcat identity inputs))
        output-flat (float-array (* positions rows))]
    (with-open [arena (Arena/ofConfined)
                channel (FileChannel/open (Path/of model-path (make-array String 0))
                                          (into-array StandardOpenOption [StandardOpenOption/READ]))]
      (let [weights (.map channel FileChannel$MapMode/READ_ONLY
                          (long tensor-offset) (long tensor-bytes) arena)]
        (invoke-matvec! tensor-type weights rows cols input-flat positions output-flat)))
    (mapv (fn [p]
            (double-array
             (map #(double (aget output-flat (+ (* p rows) %))) (range rows))))
          (range positions))))
