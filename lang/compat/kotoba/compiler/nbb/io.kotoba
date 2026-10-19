(ns kotoba.compiler.nbb.io
  "Minimal Node-`fs`-based file I/O for the nbb-native compile/check path
  (`bin/amu`'s Wasm and ordinary-native fast paths). This is a primary compiler
  boundary, so publication follows the same durable shape as the JVM path:
  an OS-created private temporary location, exclusive/no-follow file creation,
  complete write, file `fsync`, close, then same-filesystem atomic rename.
  Hardened EDN parsing remains in `kotoba.compiler.nbb.cli-support`; Windows
  owner-only ACL provisioning remains a JVM-only requirement because this path
  emits no private keys."
  (:require ["node:fs" :as fs]
            ["node:path" :as path]))

;; Matches `kotoba.compiler.bounded-edn/max-source-bytes` exactly (the
;; stricter of its two limits -- `.kotoba` source and `--policy` EDN both
;; go through this same function, so both get the tighter cap). Checked
;; against the RAW byte count before UTF-8 decoding, same order the JVM
;; path's `read-bytes` uses -- confirmed live: without this, a >1MiB
;; `.kotoba` source silently reached `sema/analyze`'s own (differently
;; worded) size check instead of failing here with the exact message
;; `scripts/conformance.cljs` asserts on.
(def ^:private max-bytes (* 1024 1024))

(defn read-text-file [p]
  (when-not (and (string? p) (seq p))
    (throw (ex-info "input path is required" {:phase :decode})))
  (let [buf (try
              (.readFileSync fs p)
              (catch :default error
                (throw (ex-info "input could not be read" {:phase :decode :path p} error))))]
    (when (> (.-length buf) max-bytes)
      (throw (ex-info "input exceeds byte limit" {:phase :decode :path p})))
    (let [decoder (js/TextDecoder. "utf-8" #js {:fatal true})]
      (try
        (.decode decoder buf)
        (catch :default error
          (throw (ex-info "input is not valid UTF-8" {:phase :decode :path p} error)))))))

(defn read-bounded-bytes-file [p maximum]
  (when-not (and (string? p) (seq p))
    (throw (ex-info "input path is required" {:phase :verify})))
  (let [constants (.-constants fs)
        no-follow (or (.-O_NOFOLLOW constants) 0)
        descriptor (volatile! nil)]
    (try
      (when (.isSymbolicLink (.lstatSync fs p))
        (throw (ex-info "output-set member must not be a symbolic link"
                        {:phase :verify :path p})))
      (let [fd (.openSync fs p (bit-or (.-O_RDONLY constants) no-follow))
            _ (vreset! descriptor fd)
            info (.fstatSync fs fd)]
        (when-not (.isFile info)
          (throw (ex-info "output-set member is not a regular file"
                          {:phase :verify :path p})))
        (when (> (.-size info) maximum)
          (throw (ex-info "output-set member exceeds byte limit"
                          {:phase :verify :path p :limit maximum})))
        ;; Do not use readFileSync here: the file may grow after fstat and turn
        ;; a nominally bounded verifier into an unbounded allocation. Read at
        ;; most the observed size plus one byte; that extra byte detects growth.
        (let [limit (inc (.-size info))
              bytes (.alloc js/Buffer limit)]
          (loop [offset 0]
            (let [read-count (.readSync fs fd bytes offset (- limit offset) nil)
                  next-offset (+ offset read-count)]
              (cond
                (= next-offset limit)
                (throw (ex-info "output-set member changed while being read"
                                {:phase :verify :path p}))

                (zero? read-count)
                (.subarray bytes 0 next-offset)

                :else
                (recur next-offset))))))
      (catch :default error
        (if (ex-data error)
          (throw error)
          (throw (ex-info "output-set member could not be read"
                          {:phase :verify :path p} error))))
      (finally
        (when-some [fd @descriptor]
          (try (.closeSync fs fd) (catch :default _ nil)))))))

(defn- open-output! [temporary]
  (let [constants (.-constants fs)
        no-follow (or (.-O_NOFOLLOW constants) 0)
        flags (bit-or (.-O_CREAT constants)
                      (.-O_EXCL constants)
                      (.-O_WRONLY constants)
                      no-follow)]
    ;; 0600 is also the JVM temporary-file posture on POSIX. The NBB path does
    ;; not emit keys, but private creation prevents another local user from
    ;; observing an artifact before publication.
    (.openSync fs temporary flags 384)))

(defn- fsync-directory! [dir]
  ;; Node cannot open directory handles on Windows. Keep that platform's
  ;; rename durability as an explicit non-claim instead of silently treating
  ;; a skipped directory flush as equivalent to POSIX fsync.
  (when-not (= "win32" (.-platform js/process))
    (let [fd (.openSync fs dir (.. fs -constants -O_RDONLY))]
      (try
        (.fsyncSync fs fd)
        (finally
          (.closeSync fs fd))))))

(defn- target! [output-path]
  (when-not (and (string? output-path) (seq output-path))
    (throw (ex-info "output path is required" {:phase :output})))
  (let [target (.resolve path output-path)
        dir (.dirname path target)]
    (when-not (try (.isDirectory (.statSync fs dir)) (catch :default _ false))
      (throw (ex-info "output parent must be a directory"
                      {:phase :output :path output-path})))
    {:path output-path :target target :dir dir}))

(defn write-set!
  "Stage and fsync every entry, then publish in vector order. Callers put a
  digest-bound commit marker last, so a partial rename can never advertise a
  matching committed set."
  [entries]
  (when-not (and (vector? entries) (seq entries))
    (throw (ex-info "output set is required" {:phase :output})))
  (let [resolved (mapv #(merge % (target! (:path %))) entries)
        dirs (set (map :dir resolved))
        targets (mapv :target resolved)]
    (when-not (= 1 (count dirs))
      (throw (ex-info "output set must share one parent directory" {:phase :output})))
    (when-not (= (count targets) (count (set targets)))
      (throw (ex-info "output set contains duplicate paths" {:phase :output})))
    (let [dir (first dirs)
          temporary-dir (.mkdtempSync fs (.join path dir ".kotoba-output-"))
          descriptor (volatile! nil)
          active-path (volatile! (:path (first resolved)))
          operation (volatile! :open)]
      (try
        (let [staged
              (mapv
               (fn [index {:keys [target bytes text] entry-path :path}]
                 (vreset! active-path entry-path)
                 (let [temporary (.join path temporary-dir (str index ".tmp"))
                       fd (open-output! temporary)]
                   (vreset! descriptor fd)
                   (vreset! operation :write)
                   (cond
                     (some? bytes) (.writeFileSync fs fd bytes)
                     (string? text) (.writeFileSync fs fd text "utf8")
                     :else (throw (js/Error. "output entry has no bytes or text")))
                   (vreset! operation :fsync)
                   (.fsyncSync fs fd)
                   (.closeSync fs fd)
                   (vreset! descriptor nil)
                   {:temporary temporary :target target :path entry-path}))
               (range) resolved)]
          (doseq [{:keys [temporary target path]} staged]
            (vreset! active-path path)
            (vreset! operation :rename)
            ;; Destination symlinks are replaced, never followed. The commit
            ;; marker is the last entry and therefore the last rename.
            (.renameSync fs temporary target)
            ;; Persist each namespace transition before publishing the next
            ;; member. Payload renames therefore reach stable storage before
            ;; the final marker rename is allowed to become durable.
            (vreset! operation :directory-fsync)
            (fsync-directory! dir)))
        (mapv :path resolved)
        (catch :default error
          (throw (ex-info "atomic output set failed"
                          {:phase :output :path @active-path :reason @operation}
                          error)))
        (finally
          (when-some [fd @descriptor]
            (try (.closeSync fs fd) (catch :default _ nil)))
          (try (.rmSync fs temporary-dir #js {:recursive true :force true})
               (catch :default _ nil)))))))

(defn- write-atomic! [output-path entry]
  (write-set! [(assoc entry :path output-path)])
  output-path)

(defn write-bytes! [output-path ^js bytes]
  (write-atomic! output-path {:bytes bytes}))

;; Same fsync-before-rename boundary as `write-bytes!`, for the native compile
;; path's `.kexe`/`.provenance.edn` output.
(defn write-text! [output-path ^string text]
  (write-atomic! output-path {:text text}))
