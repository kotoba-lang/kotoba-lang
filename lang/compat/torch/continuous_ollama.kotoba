(ns torch.continuous-ollama
  "Serialized async bridge from a shared continuous engine to Ollama streams."
  (:require [torch.continuous :as continuous]
            [torch.ollama :as ollama]
            [torch.tokenizer :as tokenizer]))

(defn host
  "Own one mutable async scheduling lane. GPU ticks, admission, and cancellation
  are serialized through `:tail`; HTTP clients remain independent stream sinks."
  [engine* tokenizer*]
  {:engine (atom engine*) :tokenizer tokenizer* :clients (atom {})
   :tail (atom (js/Promise.resolve nil)) :pump-scheduled? (atom false)
   :closed? (atom false)})

(defn- transact! [host* operation]
  (let [next (-> @(:tail host*)
                 (.catch (fn [_] nil))
                 (.then (fn [_] (operation @(:engine host*))))
                 (.then (fn [engine*]
                          (reset! (:engine host*) engine*) engine*)))]
    (reset! (:tail host*) next)
    next))

(defn- emit-token-deltas! [host* request-id generated]
  (when-let [{:keys [controller emitted decode-state cancelled?] :as client}
             (get @(:clients host*) request-id)]
    (when-not cancelled?
      (let [{:keys [state emitted-count]}
            (reduce
             (fn [{:keys [state emitted-count]} token-id]
               (let [decoded (tokenizer/decode-step (:tokenizer host*) state token-id)]
                 (.enqueue controller
                           (ollama/token-chunk (:model client) (:created-at client)
                                               (:text decoded)))
                 {:state (:state decoded) :emitted-count (inc emitted-count)}))
             {:state decode-state :emitted-count emitted}
             (drop emitted generated))]
        (swap! (:clients host*) assoc request-id
               (assoc client :decode-state state :emitted emitted-count))))))

(defn- publish! [host* engine*]
  (doseq [[request-id _] @(:clients host*)]
    (let [running (get-in engine* [:running request-id])
          completed (get-in engine* [:completed request-id])
          generated (or (:generated running) (:generated-ids completed) [])]
      (emit-token-deltas! host* request-id generated)
      (when completed
        (when-let [{:keys [controller cancelled? model created-at prompt-count started]}
                   (get @(:clients host*) request-id)]
          (when-not cancelled?
            (let [elapsed (* 1.0e6 (- (.now js/performance) started))]
              (.enqueue controller
                        (ollama/done-chunk
                         model created-at (:ids completed)
                         {:done-reason (name (:reason completed))
                          :prompt-eval-count prompt-count
                          :eval-count (count (:generated-ids completed))
                          :total-duration elapsed :eval-duration elapsed})))
            (.close controller)))
        (swap! (:clients host*) dissoc request-id))))
  engine*)

(declare schedule-pump! cancel!)

(defn- pump-once! [host*]
  (reset! (:pump-scheduled? host*) false)
  (when-not @(:closed? host*)
    (-> (transact!
         host*
         (fn [engine*]
           (let [engine* (continuous/expire engine* (.now js/Date))]
             (-> (continuous/admit-async engine*)
                 (.then (fn [admitted]
                          (if (seq (:running admitted))
                            (if (fn? (:speculative-step-fn admitted))
                              (continuous/tick-speculative-async admitted)
                              (continuous/tick-batched-async admitted))
                            admitted)))
                 (.then #(publish! host* %))))))
        (.then (fn [engine*]
                 (when (or (seq (:waiting engine*)) (seq (:running engine*)))
                   (schedule-pump! host*))))
        (.catch
         (fn [error]
           (doseq [[_ {:keys [controller cancelled?]}] @(:clients host*)]
             (when-not cancelled? (.error controller error)))
           (reset! (:clients host*) {}))))))

(defn schedule-pump! [host*]
  (when (compare-and-set! (:pump-scheduled? host*) false true)
    ;; One event-loop turn coalesces concurrent HTTP submissions before admit.
    (js/setTimeout #(pump-once! host*) 5))
  nil)

(defn submit-stream!
  "Submit one normalized Ollama request and return its live chunk stream."
  [host* request context]
  (let [request-id (:request-id context)
        prompt-ids (tokenizer/encode (:tokenizer host*) (:prompt request))
        options (cond-> (:sampling request)
                  (:timeout-ms request)
                  (assoc :deadline-ms (+ (.now js/Date) (:timeout-ms request))))]
    (js/ReadableStream.
     #js {:start
          (fn [controller]
            (swap! (:clients host*) assoc request-id
                   {:controller controller :model (:model request)
                    :created-at (.toISOString (js/Date.))
                    :started (.now js/performance) :prompt-count (count prompt-ids)
                    :emitted 0 :decode-state nil :cancelled? false})
            (-> (transact!
                 host*
                 (fn [engine*]
                   (let [{:keys [engine accepted? reason]}
                         (continuous/submit engine* request-id prompt-ids options)]
                     (if accepted? engine
                         (throw (ex-info "continuous admission rejected"
                                         {:reason reason
                                          :status (if (= :sequence-capacity reason)
                                                    400 503)}))))))
                (.then (fn [_] (schedule-pump! host*)))
                (.catch #(.error controller %))))
          :cancel (fn [_] (cancel! host* request-id :client-disconnect))})))

(defn cancel!
  "Serialize cancellation against in-flight GPU ticks and release paged blocks."
  [host* request-id _reason]
  (when-let [client (get @(:clients host*) request-id)]
    (swap! (:clients host*) assoc request-id (assoc client :cancelled? true)))
  (-> (transact! host*
                 (fn [engine*] (:engine (continuous/cancel engine* request-id))))
      (.then (fn [_] (swap! (:clients host*) dissoc request-id))))
  nil)

(defn submit!
  "Collect the same live stream for Ollama's `stream:false` response path."
  [host* request context]
  (let [reader (.getReader (submit-stream! host* request context))]
    (letfn [(pump [chunks]
              (-> (.read reader)
                  (.then (fn [result]
                           (if (.-done result)
                             chunks
                             (pump (conj chunks (.-value result))))))))]
      (pump []))))

(defn close!
  "Stop future pumps. Caller releases physical pools and model weights after
  `:tail` resolves."
  [host*]
  (reset! (:closed? host*) true)
  @(:tail host*))
