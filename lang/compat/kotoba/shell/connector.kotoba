(ns kotoba.shell.connector
  "Shell-free subprocess boundary shared by native Kotoba applications.

  Callers own the domain payload and codec. This namespace only guarantees an
  argv-only process, stdin/stdout framing, bounded result shape, and redacted
  failure data."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io ByteArrayOutputStream InputStream]
           [java.util.concurrent TimeUnit]))

(defn- env-long [name fallback minimum]
  (let [raw (System/getenv name)]
    (if (str/blank? raw) fallback
        (try (let [value (Long/parseLong raw)]
               (if (>= value minimum) value fallback))
             (catch Exception _ fallback)))))

(defn- read-bounded [^InputStream stream max-bytes]
  (let [buffer (byte-array 8192) out (ByteArrayOutputStream.)]
    (loop [total 0]
      (let [n (.read stream buffer)]
        (if (neg? n)
          (.toString out "UTF-8")
          (let [next-total (+ total n)]
            (when (> next-total max-bytes)
              (throw (ex-info "connector output exceeded limit" {:max-bytes max-bytes})))
            (.write out buffer 0 n)
            (recur next-total)))))))

(defn- destroy-tree! [^Process proc]
  ;; A connector commonly owns `git`/`gh` grandchildren. Killing only the
  ;; wrapper leaves those processes and their pipes alive indefinitely.
  (doseq [handle (reverse (vec (iterator-seq (.iterator (.descendants (.toHandle proc))))))]
    (try (.destroyForcibly handle) (catch Exception _)))
  (try (.destroyForcibly proc) (catch Exception _)))

(defn- read-async [stream max-bytes]
  (let [result (promise)
        thread (Thread. (fn []
                          (deliver result
                                   (try (read-bounded stream max-bytes)
                                        (catch Exception _ ""))))
                        "kotoba-connector-stream-reader")]
    (.setDaemon thread true)
    (.start thread)
    result))

(defn argv
  "Decode and validate an argv value from an environment string."
  [env-name raw decode]
  (when-not (str/blank? (str raw))
    (let [value (decode raw)]
      (when-not (and (vector? value) (seq value) (every? string? value))
        (throw (ex-info "connector argv must be a non-empty vector of strings"
                        {:env env-name})))
      value)))

(defn invoke!
  "Invoke argv without a command shell. `encode` and `decode` frame one value.
  Success is decided by `success?`; stdout/stderr are never included together
  with credentials or the input payload."
  [{:keys [argv input encode decode success? timeout-ms max-output-bytes]}]
  (let [timeout-ms (or timeout-ms (env-long "KOTOBA_CONNECTOR_TIMEOUT_MS" 120000 1))
        max-output-bytes (or max-output-bytes
                             (env-long "KOTOBA_CONNECTOR_MAX_OUTPUT_BYTES" 16777216 1024))
        proc (.start (ProcessBuilder. ^java.util.List argv))]
    (with-open [w (io/writer (.getOutputStream proc))]
      (.write w (encode input))
      (.write w "\n"))
    (let [stdout-future (read-async (.getInputStream proc) max-output-bytes)
          stderr-future (read-async (.getErrorStream proc) max-output-bytes)
          completed? (.waitFor proc timeout-ms TimeUnit/MILLISECONDS)]
      (when-not completed?
        (destroy-tree! proc))
      (let [stdout (try (deref stdout-future 5000 "") (catch Exception _ ""))
          stderr (try (deref stderr-future 5000 "") (catch Exception _ ""))
          exit (if completed? (.exitValue proc) 124)
          result (when-not (str/blank? stdout)
                   (try (decode stdout) (catch Exception _ nil)))]
      (if (and completed? (zero? exit) result (success? result))
        result
        {:ok? false :error (if completed? :connector-failed :connector-timeout) :exit exit
         :timeout-ms (when-not completed? timeout-ms)
         :message (if (str/blank? stderr)
                    "connector returned no valid success value"
                    (subs stderr 0 (min 4096 (count stderr))))})))))
