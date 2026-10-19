(ns kotodama.inference.cli.vllm
  "JVM reference client for the native vLLM CLI.

  The OpenAI request policy is executed from the shipped Kotoba KIR oracle.
  Transport remains a host concern and is deliberately limited to loopback."
  (:require [json.data-json :as json]
            [kotodama.inference.host.oracle :as oracle])
  (:import (java.net URI)
           (java.net.http HttpClient HttpClient$Version HttpRequest HttpRequest$BodyPublishers
                          HttpResponse$BodyHandlers)
           (java.time Duration)))

(defn- fail! [message]
  (binding [*out* *err*] (println message))
  (System/exit 2))

(defn- parse-long! [label value]
  (try
    (Long/parseLong value)
    (catch NumberFormatException _
      (fail! (str label " must be an integer")))))

(defn- options [args]
  (loop [remaining args
         result {:endpoint "http://127.0.0.1:8090/v1/chat/completions"
                 :model "qwen3.8-27b"
                 :max-tokens 64
                 :temperature 0.0}]
    (if (empty? remaining)
      result
      (let [[flag value & more] remaining]
        (when (nil? value) (fail! (str "missing value for " flag)))
        (recur more
               (case flag
                 "--endpoint" (assoc result :endpoint value)
                 "--model" (assoc result :model value)
                 "--prompt" (assoc result :prompt value)
                 "--max-tokens" (assoc result :max-tokens (parse-long! flag value))
                 "--temperature" (assoc result :temperature (Double/parseDouble value))
                 (fail! (str "unknown option " flag))))))))

(defn- loopback-endpoint? [endpoint]
  (let [uri (URI/create endpoint)]
    (and (= "http" (.getScheme uri))
         (contains? #{"127.0.0.1" "::1" "[::1]"} (.getHost uri)))))

(defn -main [& args]
  (let [{:keys [endpoint model prompt max-tokens temperature]} (options args)]
    (when-not (seq prompt) (fail! "--prompt is required"))
    (when-not (loopback-endpoint? endpoint) (fail! "endpoint must be loopback http"))
    (let [bounded-max (long (oracle/call :vllm-infer :max-output-tokens [max-tokens]))
          request-body (json/write-str
                        {:model model
                         :messages [{:role "user" :content prompt}]
                         :max_tokens bounded-max
                         :temperature temperature
                         :stream false})
          client (-> (HttpClient/newBuilder)
                     (.connectTimeout (Duration/ofSeconds 5))
                     (.version HttpClient$Version/HTTP_1_1)
                     .build)
          request (-> (HttpRequest/newBuilder (URI/create endpoint))
                      (.timeout (Duration/ofMinutes 5))
                      (.header "content-type" "application/json")
                      (.POST (HttpRequest$BodyPublishers/ofString request-body))
                      .build)
          started (System/nanoTime)
          response (.send client request (HttpResponse$BodyHandlers/ofString))
          elapsed-ms (/ (double (- (System/nanoTime) started)) 1000000.0)
          response-body (.body response)
          parsed (json/read-str response-body)
          tokens (long (or (get-in parsed ["usage" "completion_tokens"]) 0))]
      (println response-body)
      (binding [*out* *err*]
        (println (json/write-str
                  {:client "jvm"
                   :http_status (.statusCode response)
                   :request_ms elapsed-ms
                   :completion_tokens tokens
                   :tokens_per_second (if (pos? elapsed-ms)
                                        (/ (* 1000.0 tokens) elapsed-ms)
                                        0.0)})))
      (when-not (<= 200 (.statusCode response) 299) (System/exit 1)))))
