(ns torch.registry-ollama
  "Ollama callbacks routed through lazy resident model resources."
  (:require [torch.continuous-ollama :as continuous]
            [torch.ollama :as ollama]
            [torch.registry-runtime :as registry-runtime]))

(defn router [runtime*]
  {:runtime runtime* :routes (atom {})})

(defn- acquire-route! [{:keys [runtime routes]} request context]
  (let [now (.now js/Date)
        _ (registry-runtime/expire! runtime now)
        acquired (registry-runtime/acquire! runtime (:model request) now)
        route {:resource (:resource acquired) :model (:model request)
               :keep-alive-ms (:keep-alive-ms request)
               :released? (atom false)}]
    (swap! routes assoc (:request-id context) route)
    route))

(defn- release-route! [{:keys [runtime routes]} request-id route]
  (when (compare-and-set! (:released? route) false true)
    (registry-runtime/release! runtime (:model route) (.now js/Date)
                               (:keep-alive-ms route))
    (swap! routes dissoc request-id))
  nil)

(defn- prepare-request [{:keys [runtime]} request]
  (if (:chat? request)
    (let [descriptor (registry-runtime/describe runtime (:model request))]
      (assoc request :prompt
             (ollama/render-chat-prompt (:messages request)
                                        (:chat-template descriptor))))
    request))

(defn- routed-stream [router* request context]
  (let [request (prepare-request router* request)
        request-id (:request-id context)
        route (acquire-route! router* request context)
        source (continuous/submit-stream! (get-in route [:resource :host])
                                          request context)
        reader (.getReader source)]
    (js/ReadableStream.
     #js {:start
          (fn [controller]
            (letfn [(pump []
                      (-> (.read reader)
                          (.then (fn [result]
                                   (if (.-done result)
                                     (do (release-route! router* request-id route)
                                         (.close controller))
                                     (do (.enqueue controller (.-value result))
                                         (pump)))))
                          (.catch (fn [error]
                                    (release-route! router* request-id route)
                                    (.error controller error)))))]
              (pump)))
          :cancel
          (fn [reason]
            (continuous/cancel! (get-in route [:resource :host])
                                request-id reason)
            (release-route! router* request-id route)
            (.cancel reader reason))})))

(defn- routed-generate [router* request context]
  (let [request (prepare-request router* request)
        request-id (:request-id context)
        route (acquire-route! router* request context)]
    (-> (continuous/submit! (get-in route [:resource :host]) request context)
        (.finally #(release-route! router* request-id route)))))

(defn- routed-embed [router* request context]
  (let [request-id (:request-id context)
        route (acquire-route! router* request context)
        embed! (get-in route [:resource :embed!])]
    (if-not (fn? embed!)
      (do (release-route! router* request-id route)
          (js/Promise.reject
           (ex-info "model does not support embeddings" {:status 400})))
      (try
        (-> (js/Promise.resolve (embed! request))
            (.finally #(release-route! router* request-id route)))
        (catch :default error
          (release-route! router* request-id route)
          (js/Promise.reject error))))))

(defn service
  "Build callbacks consumed by `torch.ollama-http/handler`."
  ([router*] (service router* {}))
  ([router* {:keys [version] :or {version "0.0.0"}}]
   {:version version
    :models #(registry-runtime/tags (:runtime router*))
    :running-models
    #(mapv (fn [row]
             (-> row
                 (assoc :expires_at
                        (when-let [value (:expires-at-ms row)]
                          (.toISOString (js/Date. value))))
                 (assoc :size_vram (or (:size-vram row) (:size row)))
                 (assoc :context_length (:context-length row))
                 (dissoc :path :loaded :active :expires-at-ms
                         :size-vram :context-length :show :chat-template)))
           (registry-runtime/running-models (:runtime router*)))
    :show-model
    (fn [name verbose?]
      (let [descriptor (registry-runtime/describe (:runtime router*) name)
            show (:show descriptor)]
        (cond-> (merge {:parameters "" :license ""
                        :capabilities ["completion"]
                        :template (:chat-template descriptor "")
                        :details (:details descriptor)
                        :model_info (:model-info descriptor)}
                       show)
          (not verbose?) (update :model_info
                                 #(into {} (remove (fn [[key _]]
                                                    (re-find #"tokenizer.*(tokens|scores|merges)"
                                                             (str key)))) %)))))
    :copy-model! #(registry-runtime/copy-model! (:runtime router*) %1 %2)
    :delete-model! #(registry-runtime/delete-model! (:runtime router*) %)
    :generate-stream! #(js/Promise.resolve (routed-stream router* %1 %2))
    :generate! #(routed-generate router* %1 %2)
    :embed! #(routed-embed router* %1 %2)
    :cancel! (fn [request-id reason]
               (when-let [route (get @(:routes router*) request-id)]
                 (continuous/cancel! (get-in route [:resource :host])
                                     request-id reason)))}))
