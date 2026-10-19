(ns aiueos.network-topic
  "Authenticated cross-machine transport envelope for the aiueos topic bus.

  Ed25519 binds channel, publisher, topic, monotonically increasing sequence,
  epoch and value. Receiver state admits only the next sequence authorized for
  that publisher/topic. Partition rejoin applies a contiguous batch atomically;
  replay, gaps, forgery and cross-channel substitution fail closed."
  (:refer-clojure :exclude [partition])
  (:require [aiueos.topic :as topic]
            [clojure.edn :as edn])
  (:import [java.nio.charset StandardCharsets]
           [java.security KeyFactory KeyPairGenerator PrivateKey PublicKey Signature]
           [java.security.spec X509EncodedKeySpec]
           [java.util Base64]))

(def envelope-version 1)
(def max-wire-bytes 65536)
(def ^:private envelope-keys
  #{:version :channel-id :publisher :topic :sequence :epoch-ms :value
    :signature})

(defn generate-key-pair []
  (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519")))

(defn public-key-base64 [key-pair]
  (.encodeToString (Base64/getEncoder) (.getEncoded (.getPublic key-pair))))

(defn encode-envelope
  "Canonical bounded EDN wire representation."
  [envelope]
  (let [encoded (.getBytes (pr-str envelope) StandardCharsets/UTF_8)]
    (when (> (alength encoded) max-wire-bytes)
      (throw (ex-info "network topic envelope exceeds wire bound"
                      {:type :network-topic-wire-too-large
                       :actual-bytes (alength encoded)
                       :maximum-bytes max-wire-bytes})))
    encoded))

(defn decode-envelope
  "Decode one bounded EDN envelope. Unknown keys and non-map payloads fail."
  [^bytes encoded]
  (when (> (alength encoded) max-wire-bytes)
    (throw (ex-info "network topic envelope exceeds wire bound"
                    {:type :network-topic-wire-too-large
                     :actual-bytes (alength encoded)
                     :maximum-bytes max-wire-bytes})))
  (let [value (try
                (edn/read-string (String. encoded StandardCharsets/UTF_8))
                (catch Exception error
                  (throw (ex-info "invalid network topic wire EDN"
                                  {:type :invalid-network-topic-wire}
                                  error))))]
    (when-not (and (map? value) (= envelope-keys (set (keys value))))
      (throw (ex-info "network topic wire shape is invalid"
                      {:type :invalid-network-topic-wire-shape})))
    value))

(defn- decode-public-key [value]
  (cond
    (instance? PublicKey value) value
    (string? value)
    (.generatePublic
     (KeyFactory/getInstance "Ed25519")
     (X509EncodedKeySpec. (.decode (Base64/getDecoder) ^String value)))
    :else nil))

(defn- signed-fields [envelope]
  (array-map
   :version (:version envelope)
   :channel-id (:channel-id envelope)
   :publisher (:publisher envelope)
   :topic (:topic envelope)
   :sequence (:sequence envelope)
   :epoch-ms (:epoch-ms envelope)
   :value (:value envelope)))

(defn- signed-bytes [envelope]
  (.getBytes (pr-str (signed-fields envelope)) StandardCharsets/UTF_8))

(defn- sign [^PrivateKey private-key envelope]
  (let [signature (Signature/getInstance "Ed25519")]
    (.initSign signature private-key)
    (.update signature (signed-bytes envelope))
    (.encodeToString (Base64/getEncoder) (.sign signature))))

(defn sender-state
  "Create sender state. Sequence space is per [publisher topic]."
  [channel-id publisher]
  {:channel-id channel-id :publisher publisher :sequences {}})

(defn emit
  "Return [new-sender-state signed-envelope]."
  [state ^PrivateKey private-key topic-id value epoch-ms]
  (when-not (and (keyword? (:channel-id state))
                 (keyword? (:publisher state))
                 (integer? topic-id)
                 (integer? value)
                 (nat-int? epoch-ms))
    (throw (ex-info "invalid network topic emission"
                    {:type :invalid-network-topic-emission})))
  (let [stream [(:publisher state) topic-id]
        sequence (inc (get-in state [:sequences stream] 0))
        envelope {:version envelope-version
                  :channel-id (:channel-id state)
                  :publisher (:publisher state)
                  :topic topic-id
                  :sequence sequence
                  :epoch-ms epoch-ms
                  :value value}
        envelope (assoc envelope :signature (sign private-key envelope))]
    [(assoc-in state [:sequences stream] sequence) envelope]))

(defn receiver-state
  "Create receiver state, optionally restoring the anti-replay checkpoint.
  The checkpoint is intentionally small so a host can persist it through its
  sealed state/KMS adapter before acknowledging delivery."
  ([channel-id]
   (receiver-state channel-id nil))
  ([channel-id checkpoint]
   (when (and checkpoint
              (not (and (= 1 (:version checkpoint))
                        (= channel-id (:channel-id checkpoint))
                        (map? (:last-sequences checkpoint))
                        (every? (fn [[[publisher topic-id] sequence]]
                                  (and (keyword? publisher)
                                       (integer? topic-id)
                                       (nat-int? sequence)))
                                (:last-sequences checkpoint)))))
     (throw (ex-info "invalid network topic replay checkpoint"
                     {:type :invalid-network-topic-checkpoint})))
   {:channel-id channel-id
    :connected? true
    :last-sequences (or (:last-sequences checkpoint) {})
    :bus topic/empty-bus}))

(defn checkpoint
  "Serializable anti-replay state to persist before acknowledging messages."
  [state]
  {:version 1
   :channel-id (:channel-id state)
   :last-sequences (:last-sequences state)})

(defn partition [state]
  (assoc state :connected? false))

(defn- valid-signature? [public-key envelope]
  (try
    (let [verifier (Signature/getInstance "Ed25519")]
      (.initVerify verifier public-key)
      (.update verifier (signed-bytes envelope))
      (.verify verifier
               (.decode (Base64/getDecoder) ^String (:signature envelope))))
    (catch Exception _ false)))

(defn- reject [state reason envelope]
  {:ok? false :reason reason :state state
   :publisher (:publisher envelope)
   :topic (:topic envelope)
   :sequence (:sequence envelope)})

(defn receive
  "Authenticate and authorize one envelope against REGISTRY.

  Registry entries are
  `publisher -> {:public-key <PublicKey-or-X509-base64> :status :active
  :topics #{topic-ids}}`."
  [registry state envelope]
  (let [publisher (:publisher envelope)
        topic-id (:topic envelope)
        stream [publisher topic-id]
        entry (get registry publisher)
        public-key (try (decode-public-key (:public-key entry))
                        (catch Exception _ nil))
        expected (inc (get-in state [:last-sequences stream] 0))]
    (cond
      (not (:connected? state)) (reject state :partitioned envelope)
      (not (and (map? envelope)
                (keyword? publisher)
                (integer? topic-id)
                (pos-int? (:sequence envelope))
                (nat-int? (:epoch-ms envelope))
                (integer? (:value envelope))
                (string? (:signature envelope))))
      (reject state :malformed-envelope envelope)
      (not= envelope-version (:version envelope))
      (reject state :unsupported-version envelope)
      (not= (:channel-id state) (:channel-id envelope))
      (reject state :wrong-channel envelope)
      (not= :active (:status entry)) (reject state :publisher-inactive envelope)
      (not (contains? (:topics entry) topic-id))
      (reject state :topic-unauthorized envelope)
      (nil? public-key) (reject state :invalid-publisher-key envelope)
      (not (valid-signature? public-key envelope))
      (reject state :bad-signature envelope)
      (< (:sequence envelope) expected) (reject state :replay envelope)
      (> (:sequence envelope) expected) (reject state :sequence-gap envelope)
      :else
      {:ok? true
       :state (-> state
                  (assoc-in [:last-sequences stream] (:sequence envelope))
                  (update :bus topic/publish topic-id (:value envelope)))
       :publisher publisher
       :topic topic-id
       :sequence (:sequence envelope)})))

(defn rejoin
  "Atomically validate a contiguous backlog while reconnecting. On any
  rejection, none of the backlog is published and the receiver remains
  partitioned."
  [registry state envelopes]
  (let [candidate (assoc state :connected? true)
        result
        (reduce
         (fn [result envelope]
           (if-not (:ok? result)
             (reduced result)
             (receive registry (:state result) envelope)))
         {:ok? true :state candidate}
         envelopes)]
    (if (:ok? result)
      result
      (assoc result :state state))))
