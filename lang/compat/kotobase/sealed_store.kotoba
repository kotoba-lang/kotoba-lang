(ns kotobase.sealed-store
  "Fail-closed encrypted-at-rest adapter for remote Kotobase values.

  The host owns cryptographic operations. This adapter proves that plaintext
  values never reach XRPC on write and remote values never reach a local view
  unless sealing/unsealing, hybrid-policy admission, and ciphertext digest
  verification all succeed."
  (:require [kotoba.security.crypto-policy :as crypto]))

(def default-policy
  {:kotoba.security/crypto-policy-version 1
   :mode :hybrid-required
   :hybrid-epoch-floor 1})

(def payload-keys {:put :val :append :event})

(defn- present-ciphertext? [ciphertext]
  (and (some? ciphertext)
       (or (not (coll? ciphertext)) (seq ciphertext))))

(defn evaluate-seal
  [{:keys [seal-fn ciphertext-digest-fn crypto-policy]
    :or {crypto-policy default-policy}}
   plaintext]
  (let [sealed (when (ifn? seal-fn) (seal-fn plaintext))
        crypto-result (crypto/check-production-envelope crypto-policy sealed)
        computed-digest (when (and sealed (ifn? ciphertext-digest-fn))
                          (ciphertext-digest-fn (:sealed/ciphertext sealed)))
        violations (cond-> []
                     (not (ifn? seal-fn)) (conj :sealer-required)
                     (not (:valid? crypto-result)) (conj :hybrid-envelope)
                     (not (present-ciphertext? (:sealed/ciphertext sealed)))
                     (conj :ciphertext-required)
                     (not (and (some? computed-digest)
                               (= computed-digest
                                  (:sealed/ciphertext-digest sealed))))
                     (conj :ciphertext-digest))]
    {:sealed/allowed? (empty? violations)
     :sealed/violations violations
     :sealed/crypto crypto-result
     :sealed/value sealed}))

(defn seal-value! [options plaintext]
  (let [result (evaluate-seal options plaintext)]
    (when-not (:sealed/allowed? result)
      (throw (ex-info "kotobase sealed write denied" result)))
    (:sealed/value result)))

(defn sealed-envelope?
  "True only for the envelope shape this adapter writes. Public so a host can
  distinguish a sealed value from transport/control metadata without
  guessing from collection type."
  [value]
  (and (map? value) (contains? value :sealed/ciphertext)))

(defn evaluate-open
  [{:keys [unseal-fn ciphertext-digest-fn crypto-policy]
    :or {crypto-policy default-policy}}
   sealed]
  (let [crypto-result (crypto/check-production-envelope crypto-policy sealed)
        computed-digest (when (and (sealed-envelope? sealed)
                                   (ifn? ciphertext-digest-fn))
                          (ciphertext-digest-fn (:sealed/ciphertext sealed)))
        violations (cond-> []
                     (not (ifn? unseal-fn)) (conj :unsealer-required)
                     (not (:valid? crypto-result)) (conj :hybrid-envelope)
                     (not (present-ciphertext? (:sealed/ciphertext sealed)))
                     (conj :ciphertext-required)
                     (not (and (some? computed-digest)
                               (= computed-digest
                                  (:sealed/ciphertext-digest sealed))))
                     (conj :ciphertext-digest))]
    {:sealed/allowed? (empty? violations)
     :sealed/violations violations
     :sealed/crypto crypto-result
     :sealed/value sealed}))

(defn open-value!
  "Verify the production envelope and ciphertext digest before host-provided
  decryption. A configured sealed store never treats a non-nil plaintext
  remote value as a cache miss; it fails closed."
  [options sealed]
  (when (some? sealed)
    (let [result (evaluate-open options sealed)]
      (when-not (:sealed/allowed? result)
        (throw (ex-info "kotobase sealed read denied" result)))
      ((:unseal-fn options) sealed))))

(defn- seal-put! [options put]
  (when-not (and (vector? put) (= 3 (count put)))
    (throw (ex-info "invalid sealed transaction put"
                    {:type :kotobase/invalid-transaction-put})))
  (update put 2 #(seal-value! options %)))

(defn- seal-append! [options append]
  (when-not (and (vector? append) (= 2 (count append)))
    (throw (ex-info "invalid sealed transaction append"
                    {:type :kotobase/invalid-transaction-append})))
  (update append 1 #(seal-value! options %)))

(defn seal-transaction!
  "Seal every value-bearing operation while preserving transaction metadata."
  [options request]
  (when-not (map? request)
    (throw (ex-info "invalid sealed transaction request"
                    {:type :kotobase/invalid-transaction-request})))
  (-> request
      (update :puts #(mapv (partial seal-put! options) (or % [])))
      (update :appends #(mapv (partial seal-append! options) (or % [])))))

(defn- open-stamped-event! [options event]
  (let [sequence (:seq event)
        opened (open-value! options (dissoc event :seq))]
    (when-not (map? opened)
      (throw (ex-info "kotobase sealed stream event must open to a map"
                      {:type :kotobase/invalid-opened-event})))
    (cond-> opened (some? sequence) (assoc :seq sequence))))

(defn- open-snapshot! [options snapshot]
  (-> snapshot
      (update :docs
              (fn [collections]
                (into {}
                      (for [[collection documents] (or collections {})]
                        [collection
                         (into {}
                               (for [[key value] documents]
                                 [key (open-value! options value)]))]))))
      (update :streams
              (fn [streams]
                (into {}
                      (for [[stream events] (or streams {})]
                        [stream (mapv (partial open-stamped-event! options)
                                      events)]))))))

(defn- open-transaction-receipt! [options receipt]
  (if (and (map? receipt) (contains? receipt :appends))
    (update receipt :appends
            (fn [appends]
              (mapv (fn [[stream event]]
                      [stream (open-stamped-event! options event)])
                    appends)))
    receipt))

(defn- open-response! [options method response]
  (case method
    :get (open-value! options response)
    :read (mapv (partial open-stamped-event! options) response)
    :snapshot (open-snapshot! options response)
    :transact (open-transaction-receipt! options response)
    ;; Some transports return the appended event, others only an ack. Open
    ;; the former and preserve the latter for compatibility.
    :append (if (sealed-envelope? response)
              (open-stamped-event! options response)
              response)
    :put (if (sealed-envelope? response)
           (open-value! options response)
           response)
    response))

(defn wrap-xrpc
  "Encrypt writes and verify/decrypt value-bearing reads around XRPC.
  Any invalid write denies the entire batch before transport; any plaintext,
  downgraded or digest-mismatched remote value denies the read before it can
  enter a local query view."
  [xrpc options]
  (fn [method params]
    (let [request (if-let [payload-key (get payload-keys method)]
                    (update params payload-key #(seal-value! options %))
                    (if (= :transact method)
                      (seal-transaction! options params)
                      params))]
      (open-response! options method (xrpc method request)))))
