(ns torch.generate
  "Host-side token selection and synchronous text-generation orchestration."
  (:require [torch.tokenizer :as tokenizer]))

(defn- penalize [logit penalty]
  (if (neg? logit) (* logit penalty) (/ logit penalty)))

(defn- sample-ranked [ranked temperature top-p random-value]
  (if (zero? temperature)
    (ffirst ranked)
    (let [max-logit (second (first ranked))
          weighted (mapv (fn [[token logit]]
                           [token (Math/exp (/ (- logit max-logit)
                                               temperature))])
                         ranked)
          total (reduce + (map second weighted))
          with-prob (mapv (fn [[token weight]] [token weight (/ weight total)])
                          weighted)
          nucleus (loop [remaining with-prob cumulative 0.0 chosen []]
                    (let [[entry & more] remaining
                          cumulative' (+ cumulative (nth entry 2))
                          chosen' (conj chosen entry)]
                      (if (or (>= cumulative' top-p) (empty? more))
                        chosen'
                        (recur more cumulative' chosen'))))
          nucleus-total (reduce + (map second nucleus))
          threshold (* random-value nucleus-total)]
      (loop [remaining nucleus cumulative 0.0]
        (let [[[token weight] & more] remaining
              cumulative' (+ cumulative weight)]
          (if (or (> cumulative' threshold) (empty? more))
            token
            (recur more cumulative')))))))

(defn- sample-full-softmax
  "Sample an untruncated softmax in token-ID order. Sorting is distributionally
  unnecessary when top-p is one; token order makes the explicit random-value
  mapping portable across host and device implementations."
  [adjusted temperature random-value]
  (let [maximum (reduce max adjusted)
        weights (mapv #(Math/exp (/ (- % maximum) temperature)) adjusted)
        threshold (* random-value (reduce + weights))]
    (loop [token 0 cumulative 0.0]
      (let [cumulative' (+ cumulative (nth weights token))]
        (if (or (> cumulative' threshold) (= token (dec (count weights))))
          token
          (recur (inc token) cumulative'))))))

(defn sample-candidates
  "Sample from bounded `[token adjusted-logit]` candidates already selected by
  device top-k. Re-sorting makes tie behavior identical to `sample-token`.
  Repetition penalty and vocabulary truncation must already have been applied."
  [candidates {:keys [temperature top-p random-value]
               :or {temperature 1.0 top-p 1.0 random-value 0.5}}]
  (let [ranked (vec (sort-by (fn [[token logit]] [(- logit) token]) candidates))]
    (when-not (and (seq ranked)
                   (every? (fn [[token logit]]
                             (and (int? token) (number? logit))) ranked)
                   (number? temperature) (<= 0.0 temperature)
                   (number? top-p) (< 0.0 top-p) (<= top-p 1.0)
                   (number? random-value) (<= 0.0 random-value)
                   (< random-value 1.0))
      (throw (ex-info "invalid candidate sampling options"
                      {:candidate-count (count ranked) :temperature temperature
                       :top-p top-p :random-value random-value})))
    (sample-ranked ranked temperature top-p random-value)))

(defn sample-token
  "Select a token from a vector of logits.

  Options: `:temperature` (0 = greedy), `:top-k`, `:top-p`,
  `:repetition-penalty`, `:previous-tokens`, and deterministic
  `:random-value` in `[0,1)`. Supplying randomness explicitly keeps this pure
  and lets a serving runtime own its RNG stream."
  ([logits] (sample-token logits {}))
  ([logits {:keys [temperature top-k top-p repetition-penalty previous-tokens
                   random-value]
            :or {temperature 1.0 top-p 1.0 repetition-penalty 1.0
                 previous-tokens [] random-value 0.5}}]
   (let [logits (vec logits) vocab (count logits)]
     (when-not (and (pos? vocab) (number? temperature) (<= 0.0 temperature)
                    (or (nil? top-k) (and (pos-int? top-k) (<= top-k vocab)))
                    (number? top-p) (< 0.0 top-p) (<= top-p 1.0)
                    (number? repetition-penalty) (<= 1.0 repetition-penalty)
                    (number? random-value) (<= 0.0 random-value) (< random-value 1.0))
       (throw (ex-info "invalid sampling options"
                       {:vocab vocab :temperature temperature :top-k top-k
                        :top-p top-p :repetition-penalty repetition-penalty
                        :random-value random-value})))
     (let [seen (set previous-tokens)
           adjusted (mapv (fn [token logit]
                            (if (contains? seen token)
                              (penalize (double logit) repetition-penalty)
                              (double logit)))
                          (range vocab) logits)
           full-softmax? (and (pos? temperature) (nil? top-k) (= 1.0 top-p))]
       (if full-softmax?
         (sample-full-softmax adjusted temperature random-value)
         (let [ranked (vec (sort-by (fn [[token logit]] [(- logit) token])
                                    (map vector (range vocab) adjusted)))]
           (sample-ranked (if (and (not (zero? temperature)) top-k)
                            (subvec ranked 0 top-k)
                            ranked)
                          temperature top-p random-value)))))))

(defn generate-text
  "Encode `prompt`, prefill one token at a time through `step-fn`, then sample.
  `step-fn` receives `[token-id caches]` and returns
  `{:logits vector :caches updated}`. GPU runtimes can provide an equivalent
  async loop around the same pure `sample-token` policy.

  Returns prompt+generated IDs/text, generated IDs, and final caches."
  [tokenizer* step-fn initial-caches prompt
   {:keys [max-new-tokens eos-id random-values] :as sampling-options
    :or {max-new-tokens 32 random-values (repeat 0.5)}}]
  (let [prompt-ids (tokenizer/encode tokenizer* prompt)]
    (when (empty? prompt-ids)
      (throw (ex-info "generation prompt encoded to no tokens" {})))
    (let [prefill (reduce (fn [{:keys [caches]} token]
                            (step-fn token caches))
                          {:caches initial-caches} prompt-ids)]
      (loop [logits (:logits prefill) caches (:caches prefill)
             generated [] random-values (seq random-values)]
        (if (>= (count generated) max-new-tokens)
          (let [ids (into (vec prompt-ids) generated)]
            {:ids ids :generated-ids generated
             :text (tokenizer/decode tokenizer* ids) :caches caches})
          (let [token (sample-token
                       logits
                       (assoc (dissoc sampling-options :max-new-tokens :eos-id
                                      :random-values)
                              :previous-tokens (into (vec prompt-ids) generated)
                              :random-value (or (first random-values) 0.5)))
                generated' (conj generated token)]
            (if (= token eos-id)
              (let [ids (into (vec prompt-ids) generated')]
                {:ids ids :generated-ids generated'
                 :text (tokenizer/decode tokenizer* ids) :caches caches})
              (let [step (step-fn token caches)]
                (recur (:logits step) (:caches step) generated'
                       (next random-values))))))))))

(defn generate-text-batch
  "Generate several prompts through one statically batched model/cache.

  `batch-step-fn` receives `[token-ids caches]`, where `token-ids` has one ID
  per request, and returns `{:logits [[...vocab] ...batch] :caches ...}`.
  Prompts must currently encode to the same length (continuous/ragged batching
  requires per-sequence cache positions). Finished rows are advanced with
  `:pad-id` while unfinished rows continue, keeping the fixed batch/cache
  layout stable. `:random-values` may be one sequence per request.

  Returns one result map per prompt plus the final shared caches."
  [tokenizer* batch-step-fn initial-caches prompts
   {:keys [max-new-tokens eos-id pad-id random-values] :as sampling-options
    :or {max-new-tokens 32 pad-id 0}}]
  (let [prompts (vec prompts)
        prompt-ids (mapv #(vec (tokenizer/encode tokenizer* %)) prompts)
        batch (count prompts)
        lengths (mapv count prompt-ids)]
    (when-not (pos? batch)
      (throw (ex-info "batched generation requires at least one prompt" {})))
    (when (some zero? lengths)
      (throw (ex-info "batched generation prompt encoded to no tokens"
                      {:lengths lengths})))
    (when-not (apply = lengths)
      (throw (ex-info "static batched generation requires equal prompt lengths"
                      {:lengths lengths})))
    (let [random-streams
          (if random-values
            (mapv #(or (seq %) (repeat 0.5)) random-values)
            (vec (repeat batch (repeat 0.5))))
          prefill
          (reduce (fn [{:keys [caches]} position]
                    (batch-step-fn (mapv #(nth % position) prompt-ids) caches))
                  {:caches initial-caches} (range (first lengths)))]
      (loop [logits (vec (:logits prefill)) caches (:caches prefill)
             generated (vec (repeat batch []))
             done (vec (repeat batch false))]
        (when-not (= batch (count logits))
          (throw (ex-info "batch step returned the wrong logits batch"
                          {:expected batch :actual (count logits)})))
        (if (every? true? done)
          {:results
           (mapv (fn [prompt prompt-token-ids generated-ids]
                   (let [ids (into prompt-token-ids generated-ids)]
                     {:prompt prompt :ids ids :generated-ids generated-ids
                      :text (tokenizer/decode tokenizer* ids)}))
                 prompts prompt-ids generated)
           :caches caches}
          (let [sampled
                (mapv (fn [index]
                        (when-not (nth done index)
                          (let [previous (into (nth prompt-ids index)
                                               (nth generated index))
                                random-stream (nth random-streams index)]
                            (sample-token
                             (nth logits index)
                             (assoc (dissoc sampling-options :max-new-tokens
                                            :eos-id :pad-id :random-values)
                                    :previous-tokens previous
                                    :random-value
                                    (or (nth random-stream (count (nth generated index))
                                             nil)
                                        0.5))))))
                      (range batch))
                generated'
                (mapv (fn [old token finished?]
                        (if finished? old (conj old token)))
                      generated sampled done)
                done'
                (mapv (fn [finished? token ids]
                        (or finished? (= token eos-id)
                            (>= (count ids) max-new-tokens)))
                      done sampled generated')]
            (if (every? true? done')
              (recur logits caches generated' done')
              (let [next-ids (mapv (fn [token finished?]
                                     (if finished? pad-id token))
                                   sampled done')
                    step (batch-step-fn next-ids caches)]
                (recur (vec (:logits step)) (:caches step)
                       generated' done')))))))))
