(ns kotobase.blockcodec.statemix
  "Host-side research adapter for the upstream StateSMix executable.

  This namespace is intentionally JVM-only and is not called by `core/frame`.
  StateSMix uses floating-point online training, AVX2/FMA, OpenMP, about 6 GiB
  for its large benchmark configuration, and an external tokenizer.  Those
  properties are incompatible with the portable byte-for-byte CID contract of
  the production `.cljc` codec."
  (:import [java.io File]
           [java.nio.file Files StandardCopyOption]
           [java.security MessageDigest]
           [java.util.concurrent TimeUnit]))

(def upstream
  {:url "https://github.com/robtacconelli/StateSMix"
   :revision "f9a55efd861610ce0232bbd0307317541d094bbd"
   :format "SSM6"})

(def model
  "The complete predictor identity needed to interpret an experimental run.
  StateSMix initialises its internal xorshift state from the C zero-initialised
  global; upstream currently exposes no user-selectable seed."
  {:id "statemix-mamba-dm32-ds16-di64-nl2"
   :codec :statemix
   :predictor :mamba-style-ssm
   :dm 32 :ds 16 :di 64 :nl 2
   :tokenizer :gpt-neox-bpe-49152
   :arithmetic-scale 65536
   :seed 0})

(defn compression-request
  "Creates an auditable host request without adding StateSMix to the CID path."
  [input-path output-path]
  {:operation :compress
   :model (:id model)
   :seed (:seed model)
   :input-path (str input-path)
   :output-path (str output-path)})

(defn problems [request]
  (cond-> []
    (not= :compress (:operation request))
    (conj {:problem :unsupported-operation})
    (not= (:id model) (:model request))
    (conj {:problem :model-mismatch :expected (:id model)})
    (not= (:seed model) (:seed request))
    (conj {:problem :seed-mismatch :expected (:seed model)})
    (not (seq (:input-path request)))
    (conj {:problem :input-path-required})
    (not (seq (:output-path request)))
    (conj {:problem :output-path-required})))

(defn- invoke!
  [binary args cwd timeout-ms]
  (let [pb (doto (ProcessBuilder. ^java.util.List (into [binary] args))
             (.directory (File. (str cwd)))
             (.inheritIO))
        ;; Online training is part of the decoder. OpenMP reduction order is
        ;; not stable across independent processes, so multiple threads can
        ;; make decoder probabilities diverge from the encoder. One thread is
        ;; a format-safety requirement, not a performance preference.
        _ (doto (.environment pb)
            (.put "OMP_NUM_THREADS" "1")
            (.put "OMP_DYNAMIC" "FALSE"))
        started (System/nanoTime)
        process (.start pb)
        completed? (.waitFor process (long timeout-ms) TimeUnit/MILLISECONDS)]
    (when-not completed?
      (.destroyForcibly process)
      (.waitFor process 10 TimeUnit/SECONDS)
      (throw (ex-info "StateSMix process timed out" {:timeout-ms timeout-ms})))
    (let [exit (.exitValue process)
          duration-ms (long (/ (- (System/nanoTime) started) 1000000))]
      (when-not (zero? exit)
        (throw (ex-info "StateSMix process failed" {:exit exit :args args})))
      duration-ms)))

(defn- sha256 [bytes]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") bytes)]
    (apply str (map #(format "%02x" (bit-and % 0xff)) digest))))

(defn- resolve-file [cwd path]
  (let [f (File. (str path))]
    (if (.isAbsolute f) f (File. (str cwd) (str path)))))

(defn benchmark!
  "Compresses, decompresses, and byte-verifies one file with an explicit
  StateSMix binary.  A compression result is never reported as lossless unless
  the independent decode matches the input.

  Returns measured byte sizes, bpb, ratio and separate wall-clock durations."
  [{:keys [binary cwd input-path output-path timeout-ms]
    :or {cwd "." timeout-ms 600000}}]
  (let [request (compression-request input-path output-path)
        issues (problems request)]
    (when (seq issues)
      (throw (ex-info "Invalid StateSMix request" {:problems issues})))
    (let [input-file (resolve-file cwd input-path)
          output-file (resolve-file cwd output-path)
          output-parent (or (.getParentFile output-file) (File. (str cwd)))
          snapshot (Files/createTempFile (.toPath output-parent)
                                         ".statemix-input-" ".bin"
                                         (make-array java.nio.file.attribute.FileAttribute 0))
          candidate (Files/createTempFile (.toPath output-parent)
                                          ".statemix-candidate-" ".ssm"
                                          (make-array java.nio.file.attribute.FileAttribute 0))
          recovered (Files/createTempFile (.toPath output-parent)
                                          ".statemix-recovered-" ".bin"
                                          (make-array java.nio.file.attribute.FileAttribute 0))]
      (try
        (when-not (and (some? binary) (seq (str binary)))
          (throw (ex-info "StateSMix binary is required" {})))
        (when-not (and (.isFile input-file) (pos? (.length input-file)))
          (throw (ex-info "StateSMix input must be a non-empty file"
                          {:input-path (.getAbsolutePath input-file)})))
        ;; Encode an immutable private snapshot so a concurrent writer cannot
        ;; change the bytes between compression, verification, and hashing.
        (Files/copy (.toPath input-file) snapshot
                    (into-array StandardCopyOption
                                [StandardCopyOption/REPLACE_EXISTING]))
        (let [compress-ms (invoke! (str binary)
                                   ["c" (str snapshot) (str candidate)]
                                   cwd timeout-ms)
              decompress-ms (invoke! (str binary)
                                     ["d" (str candidate) (str recovered)]
                                     cwd timeout-ms)
              input (Files/readAllBytes snapshot)
              recovered-bytes (Files/readAllBytes recovered)
              output-bytes (Files/size candidate)
              input-bytes (alength input)]
          (when-not (java.util.Arrays/equals input recovered-bytes)
            (throw (ex-info "StateSMix round-trip mismatch"
                            {:input-bytes input-bytes
                             :recovered-bytes (alength recovered-bytes)})))
          ;; No StateSMix stream becomes visible at output-path until a separate
          ;; decoder process has reproduced every input byte.
          (Files/move candidate (.toPath output-file)
                      (into-array StandardCopyOption
                                  [StandardCopyOption/ATOMIC_MOVE
                                   StandardCopyOption/REPLACE_EXISTING]))
          {:codec :statemix
           :model (:id model)
           :seed (:seed model)
           :input-bytes input-bytes
           :output-bytes output-bytes
           :ratio (/ (double output-bytes) input-bytes)
           :bits-per-byte (/ (* 8.0 output-bytes) input-bytes)
           :compress-duration_ms compress-ms
           :decompress-duration_ms decompress-ms
           :sha256 (sha256 input)
           :roundtrip-verified? true
           :published-after-verification? true})
        (finally
          (Files/deleteIfExists snapshot)
          (Files/deleteIfExists candidate)
          (Files/deleteIfExists recovered))))))
