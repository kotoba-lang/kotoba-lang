(ns kotodama.inference.encrypted
  "Client/server contract for an encrypted LLM output-head slice.

  The client encrypts a fixed-point hidden-state vector.  The server evaluates
  candidate LM-head rows with plaintext model weights through `paillier.core`
  and returns encrypted logits.  Only the client decrypts and selects a token.

  This is a real Paillier PHE path for a linear LLM operation, not a claim of a
  fully homomorphic Transformer."
  (:require [paillier.core :as phe]))

(def ^:private wire-version 1)
(def ^:private scheme :paillier-phe-v1)
(def ^:private minimum-key-bits 2048)

(defn- positive-integer! [label value]
  (when-not (and (integer? value) (pos? value))
    (throw (ex-info (str (name label) " must be a positive integer")
                    {label value})))
  value)

(defn- finite-number! [label value]
  (let [value (double value)]
    (when-not (Double/isFinite value)
      (throw (ex-info (str (name label) " must be finite") {label value})))
    value))

(defn- quantize [value scale]
  (let [scaled (* (finite-number! :value value) (double scale))]
    (when (or (> scaled (double Long/MAX_VALUE))
              (< scaled (double Long/MIN_VALUE)))
      (throw (ex-info "fixed-point value exceeds the supported host integer range"
                      {:value value :scale scale})))
    (bigint (Math/round scaled))))

(defn prepare-lm-head-request
  "Client: quantize and encrypt one hidden-state vector.

  `:activation-scale` and `:hidden-bound` are mandatory public contracts.
  The latter is data-independent and bounds every hidden element; only its
  quantized form is sent.  The returned EDN map contains public key material,
  dimensions, scales, and ciphertexts, never the private key or plaintext
  hidden values."
  [public-key hidden {:keys [activation-scale hidden-bound]}]
  (when-not (phe/public-key? public-key)
    (throw (ex-info "encrypted LM-head request requires a Paillier public key" {})))
  (positive-integer! :activation-scale activation-scale)
  (let [hidden (vec hidden)
        hidden-bound (finite-number! :hidden-bound hidden-bound)]
    (when-not (and (seq hidden) (pos? hidden-bound))
      (throw (ex-info "encrypted LM-head hidden state and bound must be positive"
                      {:hidden-size (count hidden) :hidden-bound hidden-bound})))
    (doseq [value hidden]
      (let [value (finite-number! :hidden-value value)]
        (when (> (Math/abs value) hidden-bound)
          (throw (ex-info "hidden value exceeds the declared public bound"
                          {:hidden-bound hidden-bound})))))
    (let [encoded (mapv #(quantize % activation-scale) hidden)
          input-bound (quantize hidden-bound activation-scale)]
      {:kotodama.encrypted/version wire-version
       :kotodama.encrypted/scheme scheme
       :kotodama.encrypted/public-key (phe/public-key->data public-key)
       :kotodama.encrypted/hidden
       {:shape [(count hidden)]
        :ciphertexts (mapv (comp phe/ciphertext->data
                                 #(phe/encrypt public-key %))
                           encoded)}
       :kotodama.encrypted/quantization
       {:activation-scale activation-scale
        :input-bound input-bound}})))

(defn- assert-request! [request]
  (when-not (and (= wire-version (:kotodama.encrypted/version request))
                 (= scheme (:kotodama.encrypted/scheme request)))
    (throw (ex-info "unsupported encrypted LM-head request"
                    {:version (:kotodama.encrypted/version request)
                     :scheme (:kotodama.encrypted/scheme request)})))
  request)

(defn evaluate-lm-head
  "Server: compute encrypted candidate logits from plaintext model weights.

  `weights` is `[candidate, hidden]`; `bias` and `token-ids` have one entry per
  candidate.  The function accepts no private key and returns no plaintext
  activation or logit.  Model parameters remain server-side and are not placed
  in the response."
  [request weights bias {:keys [weight-scale token-ids allow-insecure-test-key?]
                         :or {allow-insecure-test-key? false}}]
  (assert-request! request)
  (positive-integer! :weight-scale weight-scale)
  (let [public-key (phe/data->public-key
                    (:kotodama.encrypted/public-key request))
        _ (when (and (< (:bits public-key) minimum-key-bits)
                     (not allow-insecure-test-key?))
            (throw (ex-info "encrypted inference requires a 2048-bit Paillier key"
                            {:bits (:bits public-key)
                             :minimum minimum-key-bits})))
        hidden-wire (:kotodama.encrypted/hidden request)
        hidden-size (first (:shape hidden-wire))
        encrypted-hidden (mapv #(phe/data->ciphertext public-key %)
                               (:ciphertexts hidden-wire))
        activation-scale (get-in request
                                 [:kotodama.encrypted/quantization
                                  :activation-scale])
        input-bound (get-in request
                            [:kotodama.encrypted/quantization :input-bound])
        weights (mapv vec weights)
        bias (vec bias)
        token-ids (vec token-ids)
        candidate-count (count weights)]
    (positive-integer! :activation-scale activation-scale)
    (when-not (and (pos-int? hidden-size)
                   (= hidden-size (count encrypted-hidden))
                   (pos? candidate-count)
                   (= candidate-count (count bias) (count token-ids))
                   (every? #(= hidden-size (count %)) weights)
                   (every? #(and (integer? %) (not (neg? %))) token-ids))
      (throw (ex-info "encrypted LM-head model/request dimensions are incompatible"
                      {:hidden-size hidden-size
                       :ciphertext-count (count encrypted-hidden)
                       :weight-shape [candidate-count
                                      (when (seq weights) (count (first weights)))]
                       :bias-count (count bias)
                       :token-id-count (count token-ids)})))
    (let [output-scale (*' activation-scale weight-scale)
          integer-weights (mapv (fn [row]
                                  (mapv #(quantize % weight-scale) row))
                                weights)
          integer-bias (mapv #(quantize % output-scale) bias)
          evaluated (phe/encrypted-matvec
                     public-key integer-weights encrypted-hidden integer-bias
                     {:input-bound input-bound})]
      {:kotodama.encrypted/version wire-version
       :kotodama.encrypted/scheme scheme
       :kotodama.encrypted/key-id (:key-id public-key)
       :kotodama.encrypted/token-ids token-ids
       :kotodama.encrypted/logits
       {:shape [candidate-count]
        :ciphertexts (mapv phe/ciphertext->data (:ciphertexts evaluated))
        :output-bounds (mapv str (:output-bounds evaluated))}
       :kotodama.encrypted/quantization
       {:activation-scale activation-scale
        :weight-scale weight-scale
        :output-scale output-scale}})))

(defn- argmax-index [values]
  (first
   (reduce (fn [[best-index best-value] [index value]]
             (if (> (double value) (double best-value))
               [index value]
               [best-index best-value]))
           [0 (first values)]
           (map-indexed (fn [index value] [(inc index) value])
                        (rest values)))))

(defn decrypt-lm-head-response
  "Client: decrypt candidate logits and select the first maximum token id."
  [private-key response]
  (when-not (phe/private-key? private-key)
    (throw (ex-info "encrypted LM-head response requires a Paillier private key" {})))
  (when-not (and (= wire-version (:kotodama.encrypted/version response))
                 (= scheme (:kotodama.encrypted/scheme response)))
    (throw (ex-info "unsupported encrypted LM-head response"
                    {:version (:kotodama.encrypted/version response)
                     :scheme (:kotodama.encrypted/scheme response)})))
  (let [public-key (:public-key private-key)
        _ (when-not (= (:key-id public-key)
                       (:kotodama.encrypted/key-id response))
            (throw (ex-info "encrypted LM-head response belongs to another key"
                            {:expected (:key-id public-key)
                             :actual (:kotodama.encrypted/key-id response)})))
        output-scale (get-in response
                             [:kotodama.encrypted/quantization :output-scale])
        _ (positive-integer! :output-scale output-scale)
        ciphertexts (mapv #(phe/data->ciphertext public-key %)
                          (get-in response
                                  [:kotodama.encrypted/logits :ciphertexts]))
        token-ids (vec (:kotodama.encrypted/token-ids response))
        _ (when-not (and (pos? (count ciphertexts))
                         (= (count ciphertexts) (count token-ids)
                            (first (get-in response
                                           [:kotodama.encrypted/logits :shape])))
                         (every? #(and (integer? %) (not (neg? %))) token-ids))
            (throw (ex-info "encrypted LM-head response dimensions are incompatible"
                            {:ciphertext-count (count ciphertexts)
                             :token-id-count (count token-ids)})))
        integer-logits (mapv #(phe/decrypt private-key %) ciphertexts)
        logits (mapv #(/ (.doubleValue ^java.math.BigInteger %)
                         (double output-scale))
                     integer-logits)
        selected-index (argmax-index logits)]
    {:kotodama/token-ids token-ids
     :kotodama/integer-logits (mapv bigint integer-logits)
     :kotodama/logits logits
     :kotodama/selected-token-id (nth token-ids selected-index)}))

(defn plaintext-lm-head
  "Independent plaintext floating-point oracle for candidate LM-head rows."
  [hidden weights bias]
  (let [hidden (mapv #(finite-number! :hidden-value %) hidden)]
    (mapv (fn [row b]
            (+ (finite-number! :bias b)
               (reduce + (map #(* (finite-number! :weight %1) %2)
                              row hidden))))
          weights bias)))

(defn quantized-lm-head
  "Exact fixed-point oracle used to distinguish crypto correctness from the
  approximation error introduced by quantization."
  [hidden weights bias activation-scale weight-scale]
  (let [x (mapv #(quantize % activation-scale) hidden)
        w (mapv #(mapv (fn [value] (quantize value weight-scale)) %) weights)
        output-scale (*' activation-scale weight-scale)
        b (mapv #(quantize % output-scale) bias)]
    {:integer-logits
     (mapv (fn [row offset]
             (+ offset (reduce +' (map *' row x))))
           w b)
     :output-scale output-scale}))
