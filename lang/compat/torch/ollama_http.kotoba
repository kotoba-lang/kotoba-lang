(ns torch.ollama-http
  "Deno/browser-standard HTTP surface for the Ollama compatibility contract."
  (:require [torch.ollama :as ollama]
            [torch.openai :as openai]))

(defn- response [status body content-type]
  (js/Response.
   body #js {:status status
             :headers #js {"content-type" content-type}}))

(defn- json-response [status value]
  (response status (js/JSON.stringify (clj->js value)) "application/json"))

(defn- error-response [status error]
  (json-response status (ollama/error-body (or (.-message error) (str error)))))

(defn- openai-error-response [status error]
  (json-response status (openai/error-body (or (.-message error) (str error)))))

(defn- ndjson-stream [source cleanup!]
  (let [reader (.getReader source)
        encoder (js/TextEncoder.)]
    (js/ReadableStream.
     #js {:start
          (fn [controller]
            (letfn [(pump []
                      (-> (.read reader)
                          (.then
                           (fn [result]
                             (if (.-done result)
                               (do (cleanup!) (.close controller))
                               (do
                                 (.enqueue
                                  controller
                                  (.encode encoder
                                           (str (js/JSON.stringify
                                                 (clj->js (.-value result)))
                                                "\n")))
                                 (pump)))))
                          (.catch (fn [error]
                                    (cleanup!)
                                    (.error controller error)))))]
              (pump)))
          :cancel (fn [reason]
                    (cleanup!)
                    (.cancel reader reason))})))

(defn- map-stream [source f]
  (let [reader (.getReader source)]
    (js/ReadableStream.
     #js {:start
          (fn [controller]
            (letfn [(pump []
                      (-> (.read reader)
                          (.then (fn [result]
                                   (if (.-done result)
                                     (.close controller)
                                     (do (.enqueue controller (f (.-value result)))
                                         (pump)))))
                          (.catch #(.error controller %))))]
              (pump)))
          :cancel #(.cancel reader %)})))

(defn- openai-sse-stream [source id created cleanup!]
  (let [reader (.getReader source)
        encoder (js/TextEncoder.)
        first? (atom true)
        encode! (fn [controller value]
                  (.enqueue controller
                            (.encode encoder
                                     (str "data: "
                                          (js/JSON.stringify (clj->js value))
                                          "\n\n"))))
        finish! (fn [controller]
                  (.enqueue controller (.encode encoder "data: [DONE]\n\n"))
                  (cleanup!)
                  (.close controller))]
    (js/ReadableStream.
     #js {:start
          (fn [controller]
            (letfn [(pump []
                      (-> (.read reader)
                          (.then
                           (fn [result]
                             (if (.-done result)
                               (finish! controller)
                               (let [native (.-value result)]
                                 (encode! controller
                                          (openai/chat-chunk id created native @first?))
                                 (reset! first? false)
                                 (if (:done native)
                                   (finish! controller)
                                   (pump))))))
                          (.catch (fn [error]
                                    (cleanup!)
                                    (.error controller error)))))]
              (pump)))
          :cancel (fn [reason]
                    (cleanup!)
                    (.cancel reader reason))})))

(defn handler
  "Build a standard Fetch handler. Service keys:

  - `:version` string
  - `:models` vector of Ollama tag maps
  - `:copy-model!` source, destination and `:delete-model!` model (optional)
  - `POST /api/generate`, text-only `POST /api/chat`, and `POST /api/embed`
  - `:generate!` normalized-request, request-context -> Promise/vector chunks
  - `:generate-stream!` normalized-request, context -> Promise/ReadableStream
  - `:cancel!` request-id, reason (optional)
  - `:request-id-fn` zero-arg ID supplier (optional)

  The returned function can be passed directly to `Deno.serve`."
  [{:keys [version models running-models show-model copy-model! delete-model!
           generate! generate-stream! embed! cancel! request-id-fn]
    :or {version "0.0.0" models [] cancel! (fn [& _])
         request-id-fn #(str (random-uuid))}}]
  (when-not (or (fn? generate!) (fn? generate-stream!))
    (throw (ex-info "Ollama HTTP service requires a generate callback" {})))
  (fn [request]
    (let [url (js/URL. (.-url request))
          path (.-pathname url)
          method (.-method request)]
      (cond
        (and (= method "GET") (= path "/api/version"))
        (js/Promise.resolve (json-response 200 {:version version}))

        (and (= method "GET") (= path "/v1/models"))
        (js/Promise.resolve
         (json-response 200
                        (openai/models-response
                         (if (fn? models) (models) models))))

        (and (= method "GET") (.startsWith path "/v1/models/"))
        (let [model-id (js/decodeURIComponent (subs path (count "/v1/models/")))
              rows (if (fn? models) (models) models)
              descriptor (some #(when (= model-id (or (:model %) (:name %))) %) rows)]
          (js/Promise.resolve
           (if descriptor
             (json-response 200 (openai/model-row descriptor))
             (json-response 404 (openai/error-body (str "model '" model-id "' not found"))))))

        (and (= method "GET") (= path "/api/tags"))
        (js/Promise.resolve
         (json-response 200 {:models (if (fn? models) (models) models)}))

        (and (= method "GET") (= path "/api/ps"))
        (js/Promise.resolve
         (json-response 200 {:models (if (fn? running-models)
                                       (running-models) [])}))

        (and (= method "POST") (= path "/api/show"))
        (if-not (fn? show-model)
          (js/Promise.resolve (json-response 404 {:error "model details unavailable"}))
          (-> (.json request)
              (.then (fn [body]
                       (let [{:keys [model verbose]}
                             (js->clj body :keywordize-keys true)]
                         (when-not (and (string? model) (seq model)
                                        (or (nil? verbose) (boolean? verbose)))
                           (throw (ex-info "invalid Ollama show request"
                                           {:status 400})))
                         (json-response 200 (show-model model (boolean verbose))))))
              (.catch (fn [error]
                        (error-response (or (:status (ex-data error)) 400) error)))))

        (and (= method "POST") (= path "/api/copy"))
        (if-not (fn? copy-model!)
          (js/Promise.resolve (json-response 404 {:error "model copy unavailable"}))
          (-> (.json request)
              (.then (fn [body]
                       (let [{:keys [source destination]}
                             (js->clj body :keywordize-keys true)]
                         (when-not (and (string? source) (seq source)
                                        (string? destination) (seq destination))
                           (throw (ex-info "invalid Ollama copy request"
                                           {:status 400})))
                         (copy-model! source destination)
                         (json-response 200 {}))))
              (.catch (fn [error]
                        (error-response (or (:status (ex-data error)) 400) error)))))

        (and (= method "DELETE") (= path "/api/delete"))
        (if-not (fn? delete-model!)
          (js/Promise.resolve (json-response 404 {:error "model delete unavailable"}))
          (-> (.json request)
              (.then (fn [body]
                       (let [{:keys [model]}
                             (js->clj body :keywordize-keys true)]
                         (when-not (and (string? model) (seq model))
                           (throw (ex-info "invalid Ollama delete request"
                                           {:status 400})))
                         (delete-model! model)
                         (json-response 200 {}))))
              (.catch (fn [error]
                        (error-response (or (:status (ex-data error)) 400) error)))))

        (and (= method "POST") (= path "/v1/embeddings"))
        (if-not (fn? embed!)
          (js/Promise.resolve
           (json-response 404 (openai/error-body "embeddings unavailable")))
          (let [context {:request-id (request-id-fn) :signal (.-signal request)}]
            (-> (.json request)
                (.then (fn [body]
                         (let [normalized
                               (openai/normalize-embed-request
                                (js->clj body :keywordize-keys true))]
                           (-> (js/Promise.resolve (embed! normalized context))
                               (.then
                                (fn [result]
                                  (json-response
                                   200 (openai/embeddings-response
                                        (assoc result :model (:model normalized))))))))))
                (.catch (fn [error]
                          (openai-error-response
                           (or (:status (ex-data error)) 400) error))))))

        (and (= method "POST") (= path "/v1/chat/completions"))
        (let [request-id (request-id-fn)
              completion-id (str "chatcmpl-" request-id)
              created (long (js/Math.floor (/ (.now js/Date) 1000)))
              signal (.-signal request)
              cancelled? (atom false)
              on-abort (fn [_]
                         (when (compare-and-set! cancelled? false true)
                           (cancel! request-id :client-disconnect)))
              cleaned? (atom false)
              cleanup! (fn []
                         (when (compare-and-set! cleaned? false true)
                           (.removeEventListener signal "abort" on-abort)))
              context {:request-id request-id :signal signal}]
          (.addEventListener signal "abort" on-abort #js {:once true})
          (when (.-aborted signal) (on-abort nil))
          (-> (.json request)
              (.then
               (fn [body]
                 (let [normalized
                       (openai/normalize-chat-request
                        (js->clj body :keywordize-keys true))]
                   (if (and (:stream normalized) (fn? generate-stream!))
                     (-> (js/Promise.resolve
                          (generate-stream! normalized context))
                         (.then
                          (fn [source]
                            (response
                             200
                             (openai-sse-stream source completion-id created cleanup!)
                             "text/event-stream"))))
                     (-> (js/Promise.resolve (generate! normalized context))
                         (.then
                          (fn [chunks]
                            (cleanup!)
                            (json-response
                             200 (openai/chat-response completion-id created chunks)))))))))
              (.catch (fn [error]
                        (cleanup!)
                        (openai-error-response
                         (or (:status (ex-data error)) 400) error)))))

        (and (= method "POST") (= path "/api/embed"))
        (if-not (fn? embed!)
          (js/Promise.resolve (json-response 404 {:error "embeddings unavailable"}))
          (let [started (.now js/performance)
                context {:request-id (request-id-fn) :signal (.-signal request)}]
            (-> (.json request)
                (.then (fn [body]
                         (let [normalized
                               (ollama/normalize-embed-request
                                (js->clj body :keywordize-keys true))]
                           (-> (js/Promise.resolve (embed! normalized context))
                               (.then
                                (fn [{:keys [embeddings prompt-eval-count]}]
                                  (json-response
                                   200 {:model (:model normalized)
                                        :embeddings embeddings
                                        :total_duration
                                        (* 1.0e6 (- (.now js/performance) started))
                                        :load_duration 0
                                        :prompt_eval_count prompt-eval-count})))))))
                (.catch (fn [error]
                          (error-response (or (:status (ex-data error)) 400)
                                          error))))))

        (and (= method "POST")
             (or (= path "/api/generate") (= path "/api/chat")))
        (let [request-id (request-id-fn)
              chat? (= path "/api/chat")
              signal (.-signal request)
              cancelled? (atom false)
              on-abort (fn [_]
                         (when (compare-and-set! cancelled? false true)
                           (cancel! request-id :client-disconnect)))
              cleaned? (atom false)
              cleanup! (fn []
                         (when (compare-and-set! cleaned? false true)
                           (.removeEventListener signal "abort" on-abort)))
              context {:request-id request-id :signal signal}
              _ (.addEventListener signal "abort" on-abort #js {:once true})]
          (when (.-aborted signal) (on-abort nil))
          (-> (.json request)
              (.then
               (fn [body]
                 (let [normalized
                       ((if chat? ollama/normalize-chat-request
                            ollama/normalize-generate-request)
                        (js->clj body :keywordize-keys true))]
                   (if (and (:stream normalized) (fn? generate-stream!))
                     (-> (js/Promise.resolve
                          (generate-stream! normalized context))
                         (.then
                          (fn [source]
                            (response 200
                                      (ndjson-stream
                                       (if chat? (map-stream source ollama/chat-chunk)
                                           source)
                                       cleanup!)
                                      "application/x-ndjson"))))
                     (-> (js/Promise.resolve (generate! normalized context))
                         (.then
                          (fn [chunks]
                            (let [chunks (vec chunks)
                                  output-chunks (if chat?
                                                  (mapv ollama/chat-chunk chunks)
                                                  chunks)
                                  result
                                  (if (:stream normalized)
                                    (response
                                     200
                                     (apply str
                                            (map #(str (js/JSON.stringify (clj->js %))
                                                       "\n") output-chunks))
                                     "application/x-ndjson")
                                    (let [final (or (last chunks) {})
                                          text (apply str (map :response chunks))]
                                      (json-response
                                       200 (if chat?
                                             (ollama/chat-chunk
                                              (assoc final :response text))
                                             (assoc final :response text)))))]
                              (cleanup!)
                              result))))))))
              (.catch (fn [error]
                        (cleanup!)
                        (error-response (or (:status (ex-data error)) 400) error)))))

        :else
        (js/Promise.resolve
         (json-response 404 {:error "not found"}))))))

(defn serve!
  "Start an actual Deno HTTP listener. Returns Deno's HttpServer, whose
  `.shutdown()` Promise performs graceful stop. Port 0 requests an ephemeral
  port, useful for integration tests."
  ([service] (serve! service {}))
  ([service {:keys [hostname port]
             :or {hostname "127.0.0.1" port 11434}}]
   (js/Deno.serve #js {:hostname hostname :port port} (handler service))))
