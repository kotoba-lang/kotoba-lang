(ns kekkai.node.application
  "Bounded, multiplexed application messages carried inside authenticated
  kekkai peer data frames."
  (:require [clojure.string :as str]
            ["node:crypto" :as crypto]))

(def protocol-version 1)
(def default-chunk-bytes 700)
(def max-message-bytes (* 1024 1024))
(def max-parts 2048)
(def max-assemblies 128)
(def max-buffered-bytes (* 4 1024 1024))

(defn- buffer->bytes [buffer] (vec (js/Uint8Array. buffer)))
(defn- bytes->buffer [bytes] (.from js/Buffer (clj->js bytes)))

(defn encode
  "Encode one JSON-compatible message as bounded frame byte vectors."
  ([message] (encode message default-chunk-bytes))
  ([message chunk-bytes]
   (let [payload (.from js/Buffer (js/JSON.stringify (clj->js message)) "utf8")
         size (.-length payload)]
     (when (> size max-message-bytes)
       (throw (ex-info "application message exceeds limit" {:bytes size})))
     (let [message-id (.randomUUID crypto)
           part-count (max 1 (js/Math.ceil (/ size chunk-bytes)))]
       (when (> part-count max-parts)
         (throw (ex-info "application message has too many parts"
                         {:parts part-count})))
       (mapv
        (fn [part-index]
          (let [start (* part-index chunk-bytes)
                end (min size (+ start chunk-bytes))
                part (.subarray payload start end)
                frame {:version protocol-version
                       :messageId message-id
                       :partIndex part-index
                       :partCount part-count
                       :payload (.toString part "base64")}]
            (buffer->bytes
             (.from js/Buffer (js/JSON.stringify (clj->js frame)) "utf8"))))
        (range part-count))))))

(defn empty-reassembly [] {})

(defn accept
  "Accept one peer frame. Returns {:state next :message decoded-or-nil}.
  Malformed and oversized input fails closed with :error."
  [state peer-id bytes]
  (try
    (let [frame (-> (bytes->buffer bytes) (.toString "utf8")
                    js/JSON.parse (js->clj :keywordize-keys true))
          {:keys [version messageId partIndex partCount payload]} frame
          part (when (string? payload) (.from js/Buffer payload "base64"))
          key [peer-id messageId]]
      (when-not (and (= protocol-version version)
                     (string? messageId) (<= 1 (count messageId) 160)
                     (int? partIndex) (int? partCount)
                     (<= 1 partCount max-parts)
                     (<= 0 partIndex) (< partIndex partCount)
                     part (<= (.-length part) default-chunk-bytes))
        (throw (ex-info "invalid application frame" {})))
      (when (and (not (contains? state key))
                 (>= (count state) max-assemblies))
        (throw (ex-info "too many incomplete application messages" {})))
      (let [assembly (get state key {:part-count partCount :parts {}})
            _ (when-not (= partCount (:part-count assembly))
                (throw (ex-info "application part-count changed" {})))
            assembly (assoc-in assembly [:parts partIndex] part)
            total (reduce + (map #(.-length %) (vals (:parts assembly))))
            _ (when (> total max-message-bytes)
                (throw (ex-info "application message exceeds limit" {})))
            state (assoc state key assembly)
            buffered
            (reduce
             +
             (for [entry (vals state)
                   buffered-part (vals (:parts entry))]
               (.-length buffered-part)))
            _ (when (> buffered max-buffered-bytes)
                (throw (ex-info "application reassembly buffer is full" {})))]
        (if (= partCount (count (:parts assembly)))
          (let [ordered (mapv #(get-in assembly [:parts %]) (range partCount))
                message (-> (.concat js/Buffer (into-array ordered))
                            (.toString "utf8") js/JSON.parse
                            (js->clj :keywordize-keys true))]
            {:state (dissoc state key) :message message :peer peer-id})
          {:state state})))
    (catch :default error
      {:state state :error (or (ex-message error) "invalid application frame")
       :peer peer-id})))
