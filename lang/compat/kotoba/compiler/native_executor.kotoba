(ns kotoba.compiler.native-executor
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kotoba.compiler.admission :as admission]
            [kotoba.compiler.artifact :as artifact]
            [kotoba.compiler.runtime-identity :as runtime-identity]
            [kotoba.compiler.signing :as signing]
            [kotoba.compiler.target :as target-profile])
  (:import [java.nio.file Files LinkOption Path Paths]
           [java.lang ProcessHandle]
           [java.nio.charset StandardCharsets]
           [java.nio.file.attribute FileAttribute]
           [java.io ByteArrayOutputStream]
           [java.security MessageDigest]
           [java.util.concurrent TimeUnit]))

(def loader-source-sha256 runtime-identity/loader-source-sha256)
(def windows-loader-source-sha256 runtime-identity/windows-loader-source-sha256)

(defn- windows-host? [] (= :windows (let [os (str/lower-case (System/getProperty "os.name"))]
                                      (when (str/includes? os "win") :windows))))

(defn- raw-sha256 [bytes]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") bytes)]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) digest))))

(defn- file-sha256 [file]
  (raw-sha256 (Files/readAllBytes (.toPath ^java.io.File file))))

(defn- resolve-executable [name]
  (when-not (and (string? name) (seq name) (not (str/includes? name "/"))
                 (not (str/includes? name "\\")))
    (throw (ex-info "invalid toolchain executable name" {:phase :execute})))
  (let [path (or (System/getenv "PATH") "")
        suffixes (if (windows-host?) ["" ".exe" ".cmd" ".bat"] [""])
        candidates (for [entry (str/split path
                                          (re-pattern (java.util.regex.Pattern/quote
                                                       java.io.File/pathSeparator)) -1)
                         suffix suffixes]
                     (.resolve (if (empty? entry)
                                 (.toAbsolutePath (Paths/get "." (make-array String 0)))
                                 (.toAbsolutePath (Paths/get entry (make-array String 0))))
                               (str name suffix)))
        candidate (first (filter #(and (Files/isRegularFile % (make-array LinkOption 0))
                                       (Files/isExecutable %))
                                 candidates))]
    (when-not candidate
      (throw (ex-info "required toolchain executable was not found"
                      {:phase :execute})))
    (.toRealPath ^Path candidate (make-array LinkOption 0))))

(defn- host-target []
  (let [os (str/lower-case (System/getProperty "os.name"))
        arch (str/lower-case (System/getProperty "os.arch"))]
    (when-not (or (str/includes? os "linux") (str/includes? os "mac")
                  (str/includes? os "win"))
      (throw (ex-info "native execution is unsupported on this OS"
                      {:phase :execute :os os})))
    (cond
      (contains? #{"amd64" "x86_64"} arch) :x86_64-kotoba-v1
      (contains? #{"aarch64" "arm64"} arch) :aarch64-kotoba-v1
      :else (throw (ex-info "native execution is unsupported on this architecture"
                            {:phase :execute :arch arch})))))

(defn- host-os []
  (let [os (str/lower-case (System/getProperty "os.name"))]
    (cond
      (str/includes? os "linux") :linux
      (str/includes? os "mac") :macos
      (str/includes? os "win") :windows
      :else nil)))

(defn- explicit-host-target []
  (let [backend (host-target)
        os (host-os)]
    (case [backend os]
      [:x86_64-kotoba-v1 :linux] :x86_64-linux-kotoba-v1
      [:x86_64-kotoba-v1 :macos] :x86_64-macos-kotoba-v1
      [:x86_64-kotoba-v1 :windows] :x86_64-windows-kotoba-v1
      [:aarch64-kotoba-v1 :linux] :aarch64-linux-kotoba-v1
      [:aarch64-kotoba-v1 :macos] :aarch64-macos-kotoba-v1
      [:aarch64-kotoba-v1 :windows] :aarch64-windows-kotoba-v1
      (throw (ex-info "native host has no explicit target profile" {:phase :execute})))))

(defn- deterministic-linker-flags []
  (case (host-os)
    :macos []
    :windows ["-fuse-ld=lld" "-Wl,/timestamp:0,/Brepro"]
    ["-Wl,--build-id=none"]))

(def ^:private max-process-output-bytes (* 1024 1024))

(defn- terminate-process-tree! [^Process process]
  (let [handle (.toHandle process)]
    (with-open [descendants (.descendants handle)]
      (doseq [child (reverse (iterator-seq (.iterator descendants)))]
        (.destroyForcibly ^ProcessHandle child)))
    (.destroyForcibly handle)))

(defn- read-bounded [stream limit overflow!]
  (with-open [input stream
              output (ByteArrayOutputStream.)]
    (let [buffer (byte-array 8192)]
      (loop [total 0]
        (let [read (.read input buffer)]
          (cond
            (neg? read) (.toString output "UTF-8")
            (> (+ total read) limit)
            (do (overflow!) (.toString output "UTF-8"))
            :else
            (do (.write output buffer 0 read)
                (recur (+ total read)))))))))

(defn- run-process [command env {:keys [timeout-ms output-limit]
                                 :or {timeout-ms 5000
                                      output-limit max-process-output-bytes}}]
  (let [builder (ProcessBuilder. ^java.util.List (mapv str command))
        process-env (.environment builder)
        _ (.clear process-env)
        _ (.putAll process-env env)
        process (.start builder)
        stdout (atom nil)
        stderr (atom nil)
        output-exceeded? (atom false)
        overflow! #(when (compare-and-set! output-exceeded? false true)
                     (terminate-process-tree! process))
        stdout-reader (doto (Thread. #(reset! stdout
                                               (read-bounded (.getInputStream process)
                                                             output-limit overflow!)))
                        (.setDaemon true) (.start))
        stderr-reader (doto (Thread. #(reset! stderr
                                               (read-bounded (.getErrorStream process)
                                                             output-limit overflow!)))
                        (.setDaemon true) (.start))
        completed? (.waitFor process timeout-ms TimeUnit/MILLISECONDS)
        _ (when-not completed? (terminate-process-tree! process))
        _ (.waitFor process)
        exit (.exitValue process)]
    (.join stdout-reader)
    (.join stderr-reader)
    {:exit exit :stdout @stdout :stderr @stderr
     :timed-out? (not completed?) :output-exceeded? @output-exceeded?}))

(defn- toolchain-environment [^Path compiler-path]
  (let [system-root (System/getenv "SystemRoot")
        injection-vars #{"CFLAGS" "CPPFLAGS" "LDFLAGS" "CL" "_CL_" "LINK"
                         "CPATH" "C_INCLUDE_PATH" "CPLUS_INCLUDE_PATH"
                         "OBJC_INCLUDE_PATH" "LIBRARY_PATH" "COMPILER_PATH"
                         "GCC_EXEC_PREFIX"}
        host-env (when (windows-host?)
                   (into {}
                         (remove (fn [[name _]]
                                   (contains? injection-vars (str/upper-case name))))
                         (System/getenv)))
        windows-vars (when (windows-host?)
                       (into {}
                             (keep (fn [name]
                                     (when-let [value (System/getenv name)]
                                       [name value])))
                             ["INCLUDE" "LIB" "LIBPATH"
                              "ProgramFiles" "ProgramFiles(x86)" "ProgramW6432"
                              "VCINSTALLDIR" "VCToolsInstallDir"
                              "WindowsSdkDir" "WindowsSDKVersion"
                              "UniversalCRTSdkDir" "UCRTVersion"
                              "TEMP" "TMP"]))]
    (cond->
     (merge host-env
      {"PATH" (if (windows-host?)
                (str (.getParent compiler-path) java.io.File/pathSeparator
                     system-root "\\System32" java.io.File/pathSeparator
                     (System/getenv "PATH"))
                (str (.getParent compiler-path) java.io.File/pathSeparator
                     "/usr/bin" java.io.File/pathSeparator "/bin"))
       "LANG" "C"
       "LC_ALL" "C"
       "TZ" "UTC"
       "SOURCE_DATE_EPOCH" "0"
       "ZERO_AR_DATE" "1"}
      windows-vars)
      system-root (assoc "SystemRoot" system-root))))

(defn- resolve-reported-tool [reported env]
  (try
    (let [lines (str/split-lines reported)
          value (str/trim reported)]
      (when-not (and (= 1 (count lines)) (seq value) (<= (count value) 4096))
        (throw (ex-info "compiler reported a malformed tool path" {:phase :execute})))
      (let [candidate (Paths/get value (make-array String 0))
            resolved (cond
                       (.isAbsolute candidate) candidate
                       (not (str/includes? value "/"))
                       (some (fn [entry]
                               (let [path (.resolve
                                           (.toAbsolutePath
                                            (Paths/get entry (make-array String 0)))
                                           value)]
                                 (when (and (Files/isRegularFile path (make-array LinkOption 0))
                                            (Files/isExecutable path)) path)))
                             (str/split (get env "PATH" "")
                                        (re-pattern (java.util.regex.Pattern/quote
                                                     java.io.File/pathSeparator)) -1))
                       :else nil)]
        (when-not (and resolved
                       (Files/isRegularFile resolved (make-array LinkOption 0))
                       (Files/isExecutable resolved))
          (throw (ex-info "compiler-reported tool is not an executable file"
                          {:phase :execute})))
        (.toRealPath ^Path resolved (make-array LinkOption 0))))
    (catch clojure.lang.ExceptionInfo error (throw error))
    (catch Exception error
      (throw (ex-info "compiler reported a malformed tool path"
                      {:phase :execute} error)))))

(defn- compiler-tool [compiler-path name env]
  (let [query (run-process [(str compiler-path) (str "-print-prog-name=" name)]
                           env {:timeout-ms 5000 :output-limit 4096})]
    (when-not (and (zero? (:exit query))
                   (not (:timed-out? query))
                   (not (:output-exceeded? query))
                   (empty? (:stderr query)))
      (throw (ex-info "native compiler tool query failed" {:phase :execute})))
    (resolve-reported-tool (:stdout query) env)))

(defn- compiler-resource-directory [compiler-path env]
  (let [query (run-process [(str compiler-path) "-print-file-name=include"]
                           env {:timeout-ms 5000 :output-limit 4096})
        value (str/trim (:stdout query))]
    (when-not (and (zero? (:exit query))
                   (not (:timed-out? query))
                   (not (:output-exceeded? query))
                   (empty? (:stderr query))
                   (= 1 (count (str/split-lines (:stdout query))))
                   (seq value) (<= (count value) 4096))
      (throw (ex-info "native compiler resource query failed" {:phase :execute})))
    (try
      (let [path (Paths/get value (make-array String 0))]
        (when-not (and (.isAbsolute path)
                       (Files/isDirectory path (make-array LinkOption 0)))
          (throw (ex-info "compiler resource directory is not absolute"
                          {:phase :execute})))
        (.toRealPath path (make-array LinkOption 0)))
      (catch clojure.lang.ExceptionInfo error (throw error))
      (catch Exception error
        (throw (ex-info "compiler reported a malformed resource directory"
                        {:phase :execute} error))))))

(defn- directory-manifest-sha256 [^Path root]
  (with-open [stream (Files/walk root (make-array java.nio.file.FileVisitOption 0))]
    (let [iterator (.iterator stream)
          paths (loop [out []]
                  (if (.hasNext iterator)
                    (do
                      (when (>= (count out) 20000)
                        (throw (ex-info "compiler resource entry count exceeds limit"
                                        {:phase :execute :limit 20000})))
                      (recur (conj out (.next iterator))))
                    out))]
      (doseq [path paths]
        (when (Files/isSymbolicLink path)
          (throw (ex-info "compiler resource directory contains a symlink"
                          {:phase :execute})))
        (when-not (or (Files/isDirectory path (make-array LinkOption 0))
                      (Files/isRegularFile path (make-array LinkOption 0)))
          (throw (ex-info "compiler resource directory contains a special file"
                          {:phase :execute}))))
      (let [files (vec (filter #(Files/isRegularFile % (make-array LinkOption 0)) paths))
            _ (when (> (count files) 10000)
                (throw (ex-info "compiler resource file count exceeds limit"
                                {:phase :execute :limit 10000})))
            total (reduce + 0 (map #(Files/size ^Path %) files))
            _ (when (> total (* 64 1024 1024))
                (throw (ex-info "compiler resource bytes exceed limit"
                                {:phase :execute :limit (* 64 1024 1024)})))
            entries (mapv (fn [^Path path]
                            (let [relative (str/replace (str (.relativize root path)) "\\" "/")
                                  size (Files/size path)]
                              (when (> (count relative) 4096)
                                (throw (ex-info "compiler resource path exceeds limit"
                                                {:phase :execute :limit 4096})))
                              [relative size (file-sha256 (.toFile path))]))
                          (sort-by #(str (.relativize root ^Path %)) files))]
        (artifact/sha256 {:format :kotoba.directory-manifest/v1
                          :files entries :total-bytes total})))))

(defn- parse-dependency-file [text]
  (when-not (and (string? text) (<= (count text) (* 1024 1024))
                 (not (str/includes? text (str \u0000))))
    (throw (ex-info "compiler dependency file exceeds syntax limits"
                    {:phase :execute})))
  (let [colon (loop [index 0]
                (if (>= index (dec (count text)))
                  -1
                  (if (and (= \: (.charAt ^String text index))
                           (Character/isWhitespace (.charAt ^String text (inc index))))
                    index
                    (recur (inc index)))))]
    (when (neg? colon)
      (throw (ex-info "compiler dependency file has no target separator"
                      {:phase :execute})))
    (loop [index (inc colon) token (StringBuilder.) out []]
      (when (> (count out) 10000)
        (throw (ex-info "compiler dependency count exceeds limit"
                        {:phase :execute :limit 10000})))
      (if (>= index (count text))
        (cond-> out (pos? (.length token)) (conj (str token)))
        (let [ch (.charAt ^String text index)]
          (cond
            (= ch \\)
            (if (>= (inc index) (count text))
              (throw (ex-info "compiler dependency file ends in an escape"
                              {:phase :execute}))
              (let [next-ch (.charAt ^String text (inc index))]
                (cond
                  (= next-ch \newline)
                  (recur (+ index 2) token out)

                  (and (= next-ch \return)
                       (< (+ index 2) (count text))
                       (= \newline (.charAt ^String text (+ index 2))))
                  (recur (+ index 3) token out)

                  (or (Character/isWhitespace next-ch)
                      (contains? #{\# \: \\} next-ch))
                  (do (.append token next-ch)
                      (when (> (.length token) 4096)
                        (throw (ex-info "compiler dependency path exceeds limit"
                                        {:phase :execute :limit 4096})))
                      (recur (+ index 2) token out))

                  :else
                  ;; Clang emits native Windows separators without escaping
                  ;; them.  A backslash before an ordinary path character is
                  ;; therefore data, not Make escape syntax.
                  (do (.append token ch)
                      (recur (inc index) token out)))))

            (Character/isWhitespace ch)
            (if (zero? (.length token))
              (recur (inc index) token out)
              (recur (inc index) (StringBuilder.) (conj out (str token))))

            :else
            (do (.append token ch)
                (when (> (.length token) 4096)
                  (throw (ex-info "compiler dependency path exceeds limit"
                                  {:phase :execute :limit 4096})))
                (recur (inc index) token out))))))))

(defn- dependency-manifest-sha256 [dependencies]
  (let [paths (mapv (fn [value]
                      (try
                        (let [path (Paths/get value (make-array String 0))
                              absolute (if (.isAbsolute path) path (.toAbsolutePath path))]
                          (when-not (Files/isRegularFile absolute (make-array LinkOption 0))
                            (throw (ex-info "compiler dependency is not a regular file"
                                            {:phase :execute})))
                          (.toRealPath absolute (make-array LinkOption 0)))
                        (catch clojure.lang.ExceptionInfo error (throw error))
                        (catch Exception error
                          (throw (ex-info "compiler dependency path is malformed"
                                          {:phase :execute} error)))))
                    (distinct dependencies))
        _ (when (> (count paths) 10000)
            (throw (ex-info "compiler dependency count exceeds limit"
                            {:phase :execute :limit 10000})))
        total (reduce + 0 (map #(Files/size ^Path %) paths))
        _ (when (> total (* 64 1024 1024))
            (throw (ex-info "compiler dependency bytes exceed limit"
                            {:phase :execute :limit (* 64 1024 1024)})))
        entries (mapv (fn [^Path path]
                        [(str path) (Files/size path) (file-sha256 (.toFile path))])
                      (sort-by str paths))]
    (artifact/sha256 {:format :kotoba.dependency-manifest/v1
                      :files entries :total-bytes total})))

(defn- delete-tree! [file]
  (when (.exists ^java.io.File file)
    (doseq [child (reverse (file-seq file))] (io/delete-file child true))))

(defn- allowed-capabilities [policy]
  (->> (:allow policy #{})
       (keep (fn [effect]
               (when (and (vector? effect) (= :cap/call (first effect))
                          (integer? (second effect)))
                 (second effect))))
       sort
       (str/join ",")))

(defn- build-runtime! [directory]
  (let [windows? (= :windows (host-os))
        loader (io/file directory (if windows? "kexe-loader.exe" "kexe-loader"))
        dependency-file (io/file directory "loader.d")
        dependency-object (io/file directory "loader-deps.o")
        loader-source (io/file (if windows? "tools/kexe_loader_windows.c"
                                   "tools/kexe_loader.c"))
        expected-source-sha (runtime-identity/loader-source-for-profile
                             (target-profile/profile (explicit-host-target)))
        compiler-path (resolve-executable (if windows? "clang" "cc"))
        compiler-file (.toFile compiler-path)
        compiler-binary-sha (file-sha256 compiler-file)
        toolchain-env (toolchain-environment compiler-path)
        assembler-path (if windows? compiler-path
                            (compiler-tool compiler-path "as" toolchain-env))
        linker-path (if windows? (resolve-executable "lld-link")
                          (compiler-tool compiler-path "ld" toolchain-env))
        resource-path (compiler-resource-directory compiler-path toolchain-env)
        assembler-sha (file-sha256 (.toFile assembler-path))
        linker-sha (file-sha256 (.toFile linker-path))
        resource-sha (directory-manifest-sha256 resource-path)
        actual-source-sha (file-sha256 loader-source)]
    (when-not (= expected-source-sha actual-source-sha)
      (throw (ex-info "native loader source identity mismatch"
                      {:phase :execute :expected expected-source-sha
                       :actual actual-source-sha})))
    (let [compiler (run-process [(str compiler-path) "--version"] toolchain-env
                                {:timeout-ms 5000})
          _ (when-not (and (zero? (:exit compiler))
                           (not (:timed-out? compiler))
                           (not (:output-exceeded? compiler)))
              (throw (ex-info "native C compiler identity query failed"
                              {:phase :execute :stderr (:stderr compiler)})))
          compiler-text (str (:stdout compiler) (:stderr compiler))
          common-flags [(str compiler-path) "-std=c11" "-O2" "-Wall" "-Wextra" "-Werror"]
          dependency-command (vec (concat common-flags
                                          ["-MD" "-MF" (.getPath dependency-file)
                                           "-c" (.getPath loader-source)
                                           "-o" (.getPath dependency-object)]))
          dependency-build (run-process dependency-command toolchain-env
                                        {:timeout-ms 30000})
          _ (when-not (and (zero? (:exit dependency-build))
                           (not (:timed-out? dependency-build))
                           (not (:output-exceeded? dependency-build)))
              (throw (ex-info "native compiler dependency scan failed"
                              {:phase :execute :stderr (:stderr dependency-build)})))
          _ (when (> (.length dependency-file) (* 1024 1024))
              (throw (ex-info "compiler dependency file exceeds byte limit"
                              {:phase :execute :limit (* 1024 1024)})))
          dependencies (parse-dependency-file (slurp dependency-file))
          dependency-sha (dependency-manifest-sha256 dependencies)
          build-command (fn []
                          (vec (concat
                                common-flags
                                (deterministic-linker-flags)
                                [(.getPath loader-source) "-o" (.getPath loader)]
                                (when windows? ["-ladvapi32"]))))
          build (run-process (build-command) toolchain-env {:timeout-ms 30000})
          first-loader-sha (when (zero? (:exit build)) (file-sha256 loader))
          reproduced-build (run-process (build-command) toolchain-env {:timeout-ms 30000})]
      (when-not (and (zero? (:exit build)) (zero? (:exit reproduced-build))
                     (not-any? #(or (:timed-out? %) (:output-exceeded? %))
                               [build reproduced-build]))
        (throw (ex-info "native loader build failed"
                        {:phase :execute :stderr (str (:stderr build)
                                                     (:stderr reproduced-build))})))
      (let [loader-sha (file-sha256 loader)]
        (when-not (= compiler-binary-sha (file-sha256 compiler-file))
          (throw (ex-info "native C compiler changed during measurement"
                          {:phase :execute})))
        (when-not (and (= assembler-sha (file-sha256 (.toFile assembler-path)))
                       (= linker-sha (file-sha256 (.toFile linker-path))))
          (throw (ex-info "native assembler or linker changed during measurement"
                          {:phase :execute})))
        (when-not (= resource-sha (directory-manifest-sha256 resource-path))
          (throw (ex-info "compiler resource directory changed during measurement"
                          {:phase :execute})))
        (when-not (= dependency-sha (dependency-manifest-sha256 dependencies))
          (throw (ex-info "compiler dependency closure changed during measurement"
                          {:phase :execute})))
        (when-not (= first-loader-sha loader-sha)
          (throw (ex-info "native loader build is not reproducible"
                          {:phase :execute :first first-loader-sha
                           :second loader-sha})))
        {:loader loader
         :runtime {:format :kotoba.native-runtime/v6
                   :target-profile (target-profile/profile (explicit-host-target))
                   :loader-source-sha256 expected-source-sha
                   :loader-binary-sha256 loader-sha
                   :compiler-binary-sha256 compiler-binary-sha
                   :compiler-version-sha256
                   (raw-sha256 (.getBytes compiler-text StandardCharsets/UTF_8))
                   :assembler-binary-sha256 assembler-sha
                   :linker-binary-sha256 linker-sha
                   :compiler-resource-sha256 resource-sha
                   :system-header-closure-sha256 dependency-sha}}))))

(defn measure-runtime
  "Build the reviewed loader twice and return its identity and exact bytes."
  []
  ;; Refuse unsupported hosts here, before invoking a host toolchain.
  (host-target)
  (let [directory (.toFile (Files/createTempDirectory
                            "kotoba-measure-" (make-array FileAttribute 0)))]
    (try
      (let [{:keys [loader runtime]} (build-runtime! directory)]
        {:runtime runtime :loader-bytes (Files/readAllBytes (.toPath ^java.io.File loader))})
      (finally (delete-tree! directory)))))

(defn- trap-value [stderr]
  (when-let [[_ value] (re-find #"(?m)^KEXE_TRAP (\{.*\})$" stderr)]
    (edn/read-string value)))

(defn- valid-supervisor-report? [report exit]
  (let [status (:status report)
        expected-keys (case status
                        :ok #{:status :result :fuel :heap}
                        :trap #{:status :exit :fuel :heap}
                        nil)
        fuel (:fuel report)
        heap (:heap report)]
    (and (map? report)
         (= expected-keys (set (keys report)))
         (= (zero? exit) (= status :ok))
         (= #{:initial :remaining} (set (keys fuel)))
         (= 256 (:initial fuel))
         (integer? (:remaining fuel)) (<= 0 (:remaining fuel) 256)
         (= #{:capacity :used} (set (keys heap)))
         (= 4096 (:capacity heap))
         (integer? (:used heap)) (<= 0 (:used heap) 4096)
         (or (not= status :trap) (= exit (:exit report)))
         (or (not= status :ok) (integer? (:result report))))))

(defn execute
  "Verify and execute a signed native artifact. Returns measured supervisor evidence."
  [envelope trust policy input {:keys [now entry runtime loader-path]}]
  (let [{artifact :artifact signer :signer} (signing/verify envelope trust now)
        host-backend (host-target)
        host-os-value (host-os)
        artifact-backend (target-profile/backend (:target artifact))
        artifact-profile (:target-profile artifact)
        runtime-profile (:target-profile runtime)
        entry (or entry 'main)
        export (get (:exports artifact) entry)
        args (:args input)]
    (admission/check {:effects (:effects artifact)} policy)
    (when-not (and (= host-backend artifact-backend)
                   (contains? #{:unspecified host-os-value} (:os artifact-profile)))
      (throw (ex-info "artifact target does not match execution host"
                      {:phase :execute :artifact-target (:target artifact)
                       :host-target host-backend})))
    (when-not export
      (throw (ex-info "unknown native entry" {:phase :execute :entry entry})))
    (when-not (and (map? input) (vector? args) (every? integer? args)
                   (every? #(<= Long/MIN_VALUE % Long/MAX_VALUE) args)
                   (= (:arity export) (count args)) (<= (count args) 5))
      (throw (ex-info "execution input does not match entry arity"
                      {:phase :execute :entry entry :arity (:arity export)})))
    (when-not (= (target-profile/profile (explicit-host-target)) runtime-profile)
      (throw (ex-info "runtime target profile does not match execution host"
                      {:phase :runtime-identity})))
    (when-not (and (= (:execution artifact-profile) (:execution runtime-profile))
                   (= (:isa artifact-profile) (:isa runtime-profile))
                   (= (:abi artifact-profile) (:abi runtime-profile))
                   (contains? #{:unspecified (:os runtime-profile)} (:os artifact-profile))
                   (or (= :kotoba-supervisor-v1 (:runtime artifact-profile))
                       (= (:runtime artifact-profile) (:runtime runtime-profile))))
      (throw (ex-info "artifact and runtime target profiles do not match"
                      {:phase :runtime-identity})))
    (runtime-identity/admit! runtime trust)
    (when-not (and (string? loader-path) (seq loader-path))
      (throw (ex-info "native execution requires a measured loader"
                      {:phase :execute})))
    (let [loader-source (io/file loader-path)
          loader-bytes (when (.isFile loader-source)
                         (Files/readAllBytes (.toPath loader-source)))]
      (when-not (and loader-bytes (.canExecute loader-source)
                     (= (:loader-binary-sha256 runtime) (raw-sha256 loader-bytes)))
        (throw (ex-info "measured native loader does not match runtime identity"
                        {:phase :runtime-identity})))
      (let [directory (.toFile (Files/createTempDirectory
                                "kotoba-native-" (make-array FileAttribute 0)))
            code-file (io/file directory "program.bin")
            loader (io/file directory (if (= :windows host-os-value)
                                        "kexe-loader.exe" "kexe-loader"))]
      (try
        (with-open [out (io/output-stream loader)]
          (.write out ^bytes loader-bytes))
        (when-not (or (= :windows host-os-value) (.setExecutable loader true true))
          (throw (ex-info "cannot make measured loader executable"
                          {:phase :execute})))
        (with-open [out (io/output-stream code-file)]
          (.write out ^bytes (byte-array (map unchecked-byte (:code artifact)))))
        (let [isa (if (= host-backend :x86_64-kotoba-v1) "x86_64" "aarch64")
              allow (let [ids (allowed-capabilities policy)] (if (empty? ids) "-" ids))
              command (into [(.getPath loader) (.getPath code-file)
                             (str (:offset export)) (str (:arity export)) isa allow]
                            (map str args))
              started-at (quot (System/currentTimeMillis) 1000)
              process (run-process command {"KEXE_STRUCTURED_REPORT" "1"}
                                   {:timeout-ms 5000 :output-limit 65536})
              finished-at (quot (System/currentTimeMillis) 1000)
              report (edn/read-string (str/trim (:stdout process)))
              trap (trap-value (:stderr process))
              status (:status report)
              evidence (cond-> {:status status :runtime runtime}
                         (= status :ok) (assoc :result (:result report))
                         trap (assoc :trap trap))]
          (when-not (valid-supervisor-report? report (:exit process))
            (throw (ex-info "malformed native supervisor evidence"
                            {:phase :execute :exit (:exit process)
                             :stdout (:stdout process) :stderr (:stderr process)})))
          {:artifact artifact :signer signer :target (:target artifact) :entry entry
           :input input :evidence evidence :report report
           :started-at started-at :finished-at finished-at})
        (finally (delete-tree! directory)))))))
