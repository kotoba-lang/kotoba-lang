(ns torch.metal-resource
  "Compact-bundle model factory with shared paged continuous Metal execution."
  (:require [num.array :as arr]
            [torch.continuous :as continuous]
            [torch.continuous-ollama :as continuous-ollama]
            [torch.kv-cache :as kv]
            [torch.metal-bundle :as bundle]
            [torch.metal-paged :as metal-paged]
            [torch.generate :as generate]
            [torch.num-backend :as nb]
            [torch.paged-runtime :as paged]
            [torch.tokenizer :as tokenizer]))

(defn descriptor [name path]
  (let [stat (js/Deno.statSync path)
        manifest (bundle/inspect-bundle path)
        config (:config manifest)]
    {:name name :path path :size (.-size stat)
     :context-length (:context-length config)
     :details (:details manifest)
     :model-info (:model-info manifest)
     :chat-template (:chat-template manifest)
     :show {:modified_at (some-> (.-mtime stat) .toISOString)
            :parameters (:parameters manifest "")
            :license (:license manifest "")
            :capabilities (:capabilities manifest ["completion"])}}))

(defn- host-logits-result [logits step vocab]
  (-> (arr/->vec logits)
      (.then (fn [values]
               (arr/release! logits)
               (assoc step :logits
                      (if vocab (mapv vec (partition vocab values))
                          (vec values)))))))

(defn- device-logits-result [logits step vocab]
  (let [rows (if vocab (quot (arr/nelems (:shape logits)) vocab) 1)
        cols (or vocab (arr/nelems (:shape logits)))
        shared {:tensor logits :rows rows :cols cols
                :argmax-promise (atom nil) :host-promise (atom nil)
                :remaining (atom rows) :released? (atom false)}
        refs (mapv #(assoc shared ::device-logits true :row %)
                   (range rows))]
    (assoc step :logits (if vocab refs (first refs)))))

(defn- release-device-logits! [logits]
  (when (::device-logits logits)
    (let [remaining (swap! (:remaining logits) dec)]
      (when (neg? remaining)
        (throw (ex-info "device logits released more than once" {})))
      (when (and (zero? remaining)
                 (compare-and-set! (:released? logits) false true))
        (arr/release! (:tensor logits)))))
  nil)

(defn- request-sampling-options
  [{:keys [prompt-tokens generated options]}]
  (let [index (count generated)
        random-values (:random-values options)]
    (assoc (dissoc options :max-new-tokens :eos-id :random-values)
           :previous-tokens (into prompt-tokens generated)
           :random-value (or (nth random-values index nil) 0.5))))

(defn- simple-greedy? [logits options]
  (let [{:keys [temperature top-k top-p repetition-penalty random-value]
         :or {temperature 1.0 top-p 1.0 repetition-penalty 1.0
              random-value 0.5}} options
        vocab (:cols logits)]
    (and (number? temperature) (zero? temperature)
         (or (nil? top-k) (and (pos-int? top-k) (<= top-k vocab)))
         (number? top-p) (< 0.0 top-p) (<= top-p 1.0)
         (= 1.0 repetition-penalty)
         (number? random-value) (<= 0.0 random-value) (< random-value 1.0))))

(defn- device-candidate-options [logits options]
  (let [{:keys [temperature top-k top-p repetition-penalty random-value]
         :or {temperature 1.0 top-p 1.0 repetition-penalty 1.0
              random-value 0.5}} options
        vocab (:cols logits)
        k (if (zero? temperature) 1 top-k)]
    (when (and (number? temperature) (<= 0.0 temperature)
               (pos-int? k) (<= k vocab) (<= k 256)
               (number? top-p) (< 0.0 top-p) (<= top-p 1.0)
               (number? repetition-penalty) (<= 1.0 repetition-penalty)
               (number? random-value) (<= 0.0 random-value) (< random-value 1.0))
      {:k k :repetition-penalty repetition-penalty})))

(defn- device-full-softmax? [logits options]
  (let [{:keys [temperature top-k top-p repetition-penalty random-value]
         :or {temperature 1.0 top-p 1.0 repetition-penalty 1.0
              random-value 0.5}} options]
    (and (number? temperature) (pos? temperature)
         (nil? top-k)
         (number? top-p) (= 1.0 top-p)
         (number? repetition-penalty) (<= 1.0 repetition-penalty)
         (number? random-value) (<= 0.0 random-value) (< random-value 1.0)
         (pos-int? (:cols logits)))))

(defn- device-nucleus? [logits options]
  (let [{:keys [temperature top-k top-p repetition-penalty random-value]
         :or {temperature 1.0 top-p 1.0 repetition-penalty 1.0
              random-value 0.5}} options]
    (and (number? temperature) (pos? temperature)
         (nil? top-k)
         (number? top-p) (< 0.0 top-p) (< top-p 1.0)
         (number? repetition-penalty) (<= 1.0 repetition-penalty)
         (number? random-value) (<= 0.0 random-value) (< random-value 1.0)
         (pos-int? (:cols logits)) (<= (:cols logits) 1048576))))

(defn- shared-promise! [slot create]
  (or @slot
      (let [promise (create)]
        (reset! slot promise)
        promise)))

(defn- sample-device-logits [sampling-stats request]
  (let [logits (:logits request)
        options (request-sampling-options request)]
    (if-not (::device-logits logits)
      (generate/sample-token logits options)
      (let [greedy? (simple-greedy? logits options)
            candidate-options (when-not greedy?
                                (device-candidate-options logits options))
            full-softmax? (and (not greedy?) (nil? candidate-options)
                               (device-full-softmax? logits options))
            nucleus? (and (not greedy?) (nil? candidate-options)
                          (not full-softmax?)
                          (device-nucleus? logits options))
            promise
            (cond
              greedy?
              (-> (shared-promise! (:argmax-promise logits)
                                   #(arr/argmax-rows
                                     (assoc (:tensor logits)
                                            :shape [(:rows logits) (:cols logits)])))
                  (.then #(nth % (:row logits))))

              candidate-options
              (arr/sample-top-k-row
                   (assoc (:tensor logits)
                          :shape [(:rows logits) (:cols logits)])
                   (:row logits)
                   {:top-k (:k candidate-options)
                    :previous-tokens
                    (filter #(and (int? %) (<= 0 %) (< % (:cols logits)))
                            (:previous-tokens options))
                    :repetition-penalty (:repetition-penalty candidate-options)
                    :temperature (:temperature options 1.0)
                    :top-p (:top-p options 1.0)
                    :random-value (:random-value options 0.5)})

              full-softmax?
              (arr/sample-softmax-row
               (assoc (:tensor logits)
                      :shape [(:rows logits) (:cols logits)])
               (:row logits)
               {:previous-tokens
                (filter #(and (int? %) (<= 0 %) (< % (:cols logits)))
                        (:previous-tokens options))
                :repetition-penalty (:repetition-penalty options 1.0)
                :temperature (:temperature options 1.0)
                :random-value (:random-value options 0.5)})

              nucleus?
              (arr/sample-nucleus-row
               (assoc (:tensor logits)
                      :shape [(:rows logits) (:cols logits)])
               (:row logits)
               {:previous-tokens
                (filter #(and (int? %) (<= 0 %) (< % (:cols logits)))
                        (:previous-tokens options))
                :repetition-penalty (:repetition-penalty options 1.0)
                :temperature (:temperature options 1.0)
                :top-p (:top-p options 1.0)
                :random-value (:random-value options 0.5)})

              :else
              (-> (shared-promise! (:host-promise logits)
                                   #(arr/->vec (:tensor logits)))
                  (.then
                   (fn [values]
                     (let [start (* (:row logits) (:cols logits))
                           row (subvec (vec values) start (+ start (:cols logits)))]
                       (generate/sample-token row options))))))]
        (-> promise
            (.then (fn [token]
                     (swap! sampling-stats update
                            (cond greedy? :device-greedy-tokens
                                  (or candidate-options full-softmax? nucleus?)
                                  :device-sampled-tokens
                                  :else :host-sampled-tokens)
                            (fnil inc 0))
                     token))
            (.finally #(release-device-logits! logits)))))))

(defn- l2-normalize [values]
  (let [norm (js/Math.sqrt (reduce + (map #(* % %) values)))]
    (if (zero? norm) (vec values) (mapv #(/ % norm) values))))

(defn- embed-one! [backend model weights tokenizer* context-length text truncate?]
  (let [encoded (tokenizer/encode tokenizer* text)
        _ (when (and (> (count encoded) context-length) (not truncate?))
            (throw (ex-info "embedding input exceeds model context length"
                            {:status 400 :tokens (count encoded)
                             :context-length context-length})))
        ids (vec (take context-length encoded))
        caches* (atom (nb/init-llama-caches backend model (count ids)))]
    (try
      (let [embedding
            (reduce (fn [previous token-id]
                      (let [token (arr/from-vec backend [token-id] [1])
                            step (nb/llama-embedding-step model weights token @caches*)]
                        (arr/release! token)
                        (when previous (arr/release! previous))
                        (reset! caches* (:caches step))
                        (:embedding step)))
                    nil ids)]
        (-> (arr/->vec embedding)
            (.then #(hash-map :embedding (l2-normalize %) :tokens (count ids)))
            (.finally #(do (arr/release! embedding)
                           (nb/release-llama-caches! @caches*)))))
      (catch :default error
        (nb/release-llama-caches! @caches*)
        (throw error)))))

(defn- embed-batch! [backend model weights tokenizer* config request]
  (let [embed-dim (:embed-dim config)
        dimensions (:dimensions request)]
    (when (and dimensions (not= dimensions embed-dim))
      (throw (ex-info "requested embedding dimensions are unsupported by this model"
                      {:status 400 :requested dimensions :available embed-dim})))
    (reduce
     (fn [promise text]
       (.then promise
              (fn [results]
                (-> (embed-one! backend model weights tokenizer*
                                (:context-length config) text (:truncate? request))
                    (.then #(conj results %))))))
     (js/Promise.resolve []) (:inputs request))))

(defn load-resource
  ([backend descriptor*] (load-resource backend descriptor* {}))
  ([backend descriptor* {:keys [pool-blocks block-size max-running max-waiting
                                device-sampling?]
                         :or {pool-blocks 64 block-size 16 max-running 4
                              max-waiting 128 device-sampling? true}}]
   (let [manifest (bundle/load-bundle (:path descriptor*))
         {:keys [model weights tokenizer] :as instance}
         (bundle/instantiate backend manifest)
         {:keys [embed-dim head-count kv-head-count block-count vocab]}
         (:config manifest)
         head-dim (quot embed-dim head-count)
         storages (mapv (fn [_]
                          (metal-paged/storage backend pool-blocks block-size
                                               head-count kv-head-count head-dim))
                        (range block-count))
         runtimes (mapv #(paged/runtime (kv/pool pool-blocks block-size) %)
                        storages)
         sampling-stats (atom {:device-greedy-tokens 0
                               :device-sampled-tokens 0
                               :host-sampled-tokens 0})
         finish-logits (if device-sampling? device-logits-result host-logits-result)
         step-fn
         (fn [token runtimes request-id]
           (let [token* (arr/from-vec backend [token] [1])
                 step (nb/llama-lm-paged-step model weights token* runtimes request-id)
                 logits (:logits step)]
             (arr/release! token*)
             (finish-logits logits step nil)))
         batch-step-fn
         (fn [tokens runtimes request-ids]
           (let [tokens* (arr/from-vec backend tokens [(count tokens) 1])
                 step (nb/llama-lm-paged-batch-step
                       model weights tokens* runtimes request-ids)
                 logits (:logits step)]
             (arr/release! tokens*)
             (finish-logits logits step vocab)))
         engine (continuous/engine runtimes step-fn batch-step-fn max-running
                                   (cond-> {:max-waiting max-waiting}
                                     device-sampling?
                                     (assoc :sample-token-fn
                                            #(sample-device-logits sampling-stats %)
                                            :release-logits-fn
                                            release-device-logits!)))
         host* (continuous-ollama/host engine tokenizer)]
     (merge instance
            {:descriptor descriptor* :storages storages :host host*
             :embed! #(-> (embed-batch! backend model weights tokenizer
                                        (:config manifest) %)
                          (.then (fn [rows]
                                   {:embeddings (mapv :embedding rows)
                                    :prompt-eval-count
                                    (reduce + (map :tokens rows))})))
             :pool-blocks pool-blocks :block-size block-size
             :device-sampling? device-sampling?
             :sampling-stats sampling-stats}))))

(defn unload-resource! [resource]
  (let [host* (:host resource)
        engine* @(:engine host*)]
    (when (or (seq @(:clients host*)) (seq (:waiting engine*))
              (seq (:running engine*)))
      (throw (ex-info "cannot unload an active Metal model resource"
                      {:clients (count @(:clients host*))
                       :waiting (count (:waiting engine*))
                       :running (count (:running engine*))})))
    (continuous-ollama/close! host*)
    (doseq [storage (:storages resource)] (metal-paged/release! storage))
    (nb/release-weights! (:weights resource))
    nil))

(defn callbacks [backend options]
  {:load-fn #(load-resource backend % options)
   :unload-fn unload-resource!})
