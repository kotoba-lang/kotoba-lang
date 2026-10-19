(ns kotoba.compiler.bounded-edn
  (:refer-clojure :exclude [read-string])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream PushbackReader]
           [java.nio ByteBuffer]
           [java.nio.charset CodingErrorAction StandardCharsets]
           [java.nio.file Files LinkOption Paths]))

(def max-edn-bytes (* 8 1024 1024))
(def max-source-bytes (* 1024 1024))
(def ^:private max-depth 128)
(def ^:private max-token-chars 4096)
(def ^:private max-nodes 200000)
(def ^:private max-string-chars (* 1024 1024))

(defn- reject! [message data]
  (throw (ex-info message (merge {:phase :decode} data))))

(defn- read-bytes [path limit kind]
  (when-not (and (string? path) (seq path))
    (reject! "input path is required" {:kind kind}))
  (try
    (when-not (Files/isRegularFile (Paths/get path (make-array String 0))
                                   (make-array LinkOption 0))
      (reject! "input must be a regular file" {:kind kind :path path}))
    (with-open [input (io/input-stream path)
                output (ByteArrayOutputStream.)]
      (let [buffer (byte-array 8192)]
        (loop [total 0]
          (let [read (.read input buffer)]
            (if (neg? read)
              (.toByteArray output)
              (let [next-total (+ total read)]
                (when (> next-total limit)
                  (reject! "input exceeds byte limit"
                           {:kind kind :limit limit :path path}))
                (.write output buffer 0 read)
                (recur next-total)))))))
    (catch clojure.lang.ExceptionInfo error (throw error))
    (catch Exception error
      (throw (ex-info "input could not be read"
                      {:phase :decode :kind kind :path path} error)))))

(defn- decode-utf8 [bytes kind]
  (try
    (str (.decode (doto (.newDecoder StandardCharsets/UTF_8)
                    (.onMalformedInput CodingErrorAction/REPORT)
                    (.onUnmappableCharacter CodingErrorAction/REPORT))
                  (ByteBuffer/wrap bytes)))
    (catch Exception error
      (throw (ex-info "input is not valid UTF-8" {:phase :decode :kind kind} error)))))

(defn read-text-file
  ([path] (read-text-file path max-source-bytes))
  ([path limit] (decode-utf8 (read-bytes path limit :source) :source)))

(defn- preflight! [text]
  (loop [index 0 depth 0 token-length 0 in-string? false escaped? false in-comment? false]
    (when (< index (count text))
      (let [ch (.charAt ^String text index)]
        (cond
          in-comment?
          (recur (inc index) depth 0 false false (not= ch \newline))

          (and in-string? escaped?)
          (recur (inc index) depth 0 true false false)

          (and in-string? (= ch \\))
          (recur (inc index) depth 0 true true false)

          (= ch \newline)
          (recur (inc index) depth 0 in-string? false false)

          (and (not in-string?) (= ch \;))
          (recur (inc index) depth 0 false false true)

          (= ch \")
          (recur (inc index) depth 0 (not in-string?) false false)

          in-string?
          (recur (inc index) depth 0 true false false)

          (and (= ch \#)
               (or (= (inc index) (count text))
                   (not= (.charAt ^String text (inc index)) \{)))
          (reject! "EDN dispatch forms are forbidden" {})

          (#{\( \[ \{} ch)
          (let [next-depth (inc depth)]
            (when (> next-depth max-depth)
              (reject! "EDN nesting exceeds limit" {:limit max-depth}))
            (recur (inc index) next-depth 0 false false false))

          (#{\) \] \}} ch)
          (recur (inc index) (max 0 (dec depth)) 0 false false false)

          (or (Character/isWhitespace ch) (= ch \,))
          (recur (inc index) depth 0 false false false)

          :else
          (let [next-length (inc token-length)]
            (when (> next-length max-token-chars)
              (reject! "EDN token exceeds limit" {:limit max-token-chars}))
            (recur (inc index) depth next-length false false false)))))))

(defn- validate-shape! [value]
  (let [nodes (volatile! 0)]
    (letfn [(walk [x depth]
              (when (> depth max-depth)
                (reject! "EDN value nesting exceeds limit" {:limit max-depth}))
              (when (> (vswap! nodes inc) max-nodes)
                (reject! "EDN value contains too many nodes" {:limit max-nodes}))
              (when (and (string? x) (> (count x) max-string-chars))
                (reject! "EDN string exceeds limit" {:limit max-string-chars}))
              (cond
                (map? x) (doseq [[k v] x] (walk k (inc depth)) (walk v (inc depth)))
                (coll? x) (doseq [item x] (walk item (inc depth))))) ]
      (walk value 0)
      value)))

(defn read-string [text]
  (when-not (string? text)
    (reject! "EDN input must be text" {}))
  (preflight! text)
  (try
    (with-open [reader (PushbackReader. (java.io.StringReader. text))]
      (let [eof (Object.)
            value (edn/read {:eof eof} reader)]
        (when (identical? eof value)
          (reject! "EDN input is empty" {}))
        (when-not (identical? eof (edn/read {:eof eof} reader))
          (reject! "EDN input contains trailing forms" {}))
        (validate-shape! value)))
    (catch clojure.lang.ExceptionInfo error (throw error))
    (catch Exception error
      (throw (ex-info "EDN input was rejected" {:phase :decode} error)))))

(defn read-file [path]
  (read-string (decode-utf8 (read-bytes path max-edn-bytes :edn) :edn)))
