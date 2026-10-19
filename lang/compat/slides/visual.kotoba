(ns slides.visual
  "PPTX visual rendering and comparison helpers for JVM test hosts.

  The renderer intentionally shells out to installed document renderers instead
  of adding heavyweight Java dependencies to slides itself."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.nio.file CopyOption Files Path StandardCopyOption]
           [java.util.concurrent TimeUnit]))

(def default-dpi 160)
(def default-timeout-seconds 120)
(def supported-renderers [:libreoffice])

(defn- abs-path [path]
  (.getAbsolutePath (io/file path)))

(defn- basename [path]
  (let [name (.getName (io/file path))]
    (str/replace name #"\.[^.]+$" "")))

(defn- ensure-dir! [path]
  (let [dir (io/file path)]
    (.mkdirs dir)
    (.getAbsolutePath dir)))

(defn- file-uri [path]
  (str (.toURI (io/file path))))

(defn- copy-file! [from to]
  (Files/copy ^Path (.toPath (io/file from))
              ^Path (.toPath (io/file to))
              (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))
  (abs-path to))

(defn- process-result [cmd timeout-seconds]
  (let [pb (doto (ProcessBuilder. ^java.util.List (mapv str cmd))
             (.redirectErrorStream false))
        process (.start pb)
        stdout-stream (.getInputStream process)
        stderr-stream (.getErrorStream process)
        finished? (.waitFor process (long timeout-seconds) TimeUnit/SECONDS)]
    (if finished?
      {:cmd (mapv str cmd)
       :exit (.exitValue process)
       :stdout (String. (.readAllBytes stdout-stream) "UTF-8")
       :stderr (String. (.readAllBytes stderr-stream) "UTF-8")}
      (do
        (.destroyForcibly process)
        (.close stdout-stream)
        (.close stderr-stream)
        (throw (ex-info (str "command timed out: " (str/join " " (mapv str cmd)))
                        {:cmd (mapv str cmd)
                         :timeout-seconds timeout-seconds}))))))

(defn run-command!
  ([cmd] (run-command! cmd {}))
  ([cmd {:keys [allow-exit timeout-seconds]
         :or {allow-exit #{0}
              timeout-seconds default-timeout-seconds}}]
   (let [result (process-result cmd timeout-seconds)
         allowed? (contains? (set allow-exit) (:exit result))]
     (when-not allowed?
       (throw (ex-info (str "command failed: " (str/join " " (:cmd result)))
                       result)))
     result)))

(defn command-path [name]
  (let [result (run-command! ["sh" "-lc" (str "command -v " name)] {:allow-exit #{0 1}})]
    (when (zero? (:exit result))
      (not-empty (str/trim (:stdout result))))))

(defn available-tools []
  {:libreoffice (or (command-path "soffice")
                    (command-path "libreoffice"))
   :pdftoppm (command-path "pdftoppm")
   :compare (command-path "compare")
   :magick (command-path "magick")})

(defn require-tools!
  ([] (require-tools! (available-tools)))
  ([tools]
   (let [missing (cond-> []
                   (nil? (:libreoffice tools)) (conj :libreoffice)
                   (nil? (:pdftoppm tools)) (conj :pdftoppm))]
     (when (seq missing)
       (throw (ex-info "PPTX visual rendering requires LibreOffice/soffice and pdftoppm"
                       {:missing missing
                        :tools tools
                        :install {:macos "brew install --cask libreoffice && brew install poppler"
                                  :ubuntu "apt-get install libreoffice poppler-utils"}})))
     tools)))

(defn- png-files [dir]
  (->> (file-seq (io/file dir))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".png"))
       (sort-by #(.getName %))
       (mapv #(.getAbsolutePath %))))

(defn render-pptx-pngs!
  "Render a PPTX to one PNG per slide via LibreOffice PDF export and pdftoppm.

  Returns a map containing the intermediate PDF path and generated PNG paths.
  Throws ex-info with :missing when required external tools are unavailable."
  ([pptx-path out-dir] (render-pptx-pngs! pptx-path out-dir {}))
  ([pptx-path out-dir {:keys [dpi tools] :or {dpi default-dpi} :as opts}]
   (let [tools (require-tools! (or tools (available-tools)))
         source-pptx (abs-path pptx-path)
         out-dir (ensure-dir! out-dir)
         pptx (copy-file! source-pptx (str out-dir "/input.pptx"))
         pdf-path (str out-dir "/input.pdf")
         png-prefix (str out-dir "/slide")]
     (run-command! [(:libreoffice tools)
                    "--headless"
                    "--invisible"
                    "--nologo"
                    "--nodefault"
                    "--nofirststartwizard"
                    "--nolockcheck"
                    (str "-env:UserInstallation=" (file-uri (str out-dir "/lo-profile")))
                    "--convert-to" "pdf"
                    "--outdir" out-dir
                    pptx]
                   {:timeout-seconds (:timeout-seconds opts default-timeout-seconds)})
     (when-not (.exists (io/file pdf-path))
       (throw (ex-info "LibreOffice did not produce the expected PDF"
                       {:pptx pptx
                        :pdf pdf-path
                        :out-dir out-dir})))
     (run-command! [(:pdftoppm tools)
                    "-png"
                    "-r" (str dpi)
                    pdf-path
                    png-prefix]
                   {:timeout-seconds (:timeout-seconds opts default-timeout-seconds)})
     (let [pngs (png-files out-dir)]
       (when-not (seq pngs)
         (throw (ex-info "pdftoppm did not produce slide PNGs"
                         {:pptx pptx
                          :pdf pdf-path
                          :out-dir out-dir})))
        {:slides/renderer :libreoffice
        :slides/dpi dpi
        :slides/pptx source-pptx
        :slides/render-input pptx
        :slides/pdf pdf-path
        :slides/pngs pngs
        :slides/slides (count pngs)}))))

(defn- compare-command [tools expected actual diff metric]
  (cond
    (:compare tools) [(:compare tools) "-metric" metric expected actual diff]
    (:magick tools) [(:magick tools) "compare" "-metric" metric expected actual diff]
    :else (throw (ex-info "PNG visual comparison requires ImageMagick compare or magick"
                          {:missing [:imagemagick]
                           :tools tools
                           :install {:macos "brew install imagemagick"
                                     :ubuntu "apt-get install imagemagick"}}))))

(defn compare-pngs!
  "Compare expected and actual PNG sequences with ImageMagick.

  ImageMagick exits 1 when images differ, so both 0 and 1 are accepted.
  The metric text is returned per slide; a non-empty diff image is written for
  each compared pair."
  ([expected-pngs actual-pngs diff-dir] (compare-pngs! expected-pngs actual-pngs diff-dir {}))
  ([expected-pngs actual-pngs diff-dir {:keys [metric tools] :or {metric "RMSE"}}]
   (let [tools (or tools (available-tools))
         diff-dir (ensure-dir! diff-dir)
         expected (mapv abs-path expected-pngs)
         actual (mapv abs-path actual-pngs)]
     (when-not (= (count expected) (count actual))
       (throw (ex-info "PNG sequence lengths differ"
                       {:expected (count expected)
                        :actual (count actual)})))
     {:slides/metric metric
      :slides/comparisons
      (mapv (fn [idx expected actual]
              (let [diff (str diff-dir "/slide-" (format "%03d" (inc idx)) "-diff.png")
                    result (run-command! (compare-command tools expected actual diff metric)
                                         {:allow-exit #{0 1}})]
                {:slides/slide (inc idx)
                 :slides/expected expected
                 :slides/actual actual
                 :slides/diff diff
                 :slides/exit (:exit result)
                 :slides/value (str/trim (:stderr result))}))
            (range)
            expected
            actual)})))

(defn compare-pptx!
  "Render two PPTX files and compare each slide image."
  ([expected-pptx actual-pptx out-dir] (compare-pptx! expected-pptx actual-pptx out-dir {}))
  ([expected-pptx actual-pptx out-dir opts]
   (let [out-dir (ensure-dir! out-dir)
         expected-render (render-pptx-pngs! expected-pptx (str out-dir "/expected") opts)
         actual-render (render-pptx-pngs! actual-pptx (str out-dir "/actual") opts)
         comparison (compare-pngs! (:slides/pngs expected-render)
                                   (:slides/pngs actual-render)
                                   (str out-dir "/diff")
                                   opts)]
     {:slides/expected expected-render
      :slides/actual actual-render
      :slides/comparison comparison})))
