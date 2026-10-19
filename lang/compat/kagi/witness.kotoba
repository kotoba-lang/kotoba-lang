(ns kagi.witness
  "Signed Merkle checkpoints and split-view detection for rotation DAG gossip."
  (:require [cbor.core :as cbor]
            [kagi.crypto :as crypto]
            [kagi.recovery :as recovery])
  (:import [java.util Base64]))

(def checkpoint-domain "kotoba/rotation-checkpoint/v1")

(declare merge-gossip)

(defn- hash-pair ^bytes [^bytes left ^bytes right]
  (crypto/sha256 (byte-array (concat (seq left) (seq right)))))

(defn- decode-id ^bytes [id]
  (.decode (Base64/getUrlDecoder) ^String id))

(defn merkle-root
  "Deterministic root over sorted rotation IDs. Empty root is SHA-256(empty)."
  [ids]
  (loop [level (mapv decode-id (sort ids))]
    (cond
      (empty? level) (crypto/sha256 (byte-array 0))
      (= 1 (count level)) (first level)
      :else (recur (mapv (fn [[a b]] (hash-pair a (or b a)))
                          (partition-all 2 level))))))

(defn- b64url [^bytes b]
  (-> (Base64/getUrlEncoder) (.withoutPadding) (.encodeToString b)))

(defn checkpoint-bytes ^bytes [cp]
  (cbor/encode (dissoc cp :checkpoint/signature)))

(defn create-checkpoint [p witness-id seq-no event-ids previous-id signer]
  (when-not (and (pos-int? seq-no)
                 (if (= 1 seq-no) (nil? previous-id) (seq previous-id)))
    (throw (ex-info "invalid witness checkpoint sequence/previous"
                    {:seq seq-no :previous previous-id})))
  (let [ids (vec (sort (distinct event-ids)))
        cp #:checkpoint{:domain checkpoint-domain :witness witness-id :seq seq-no
                        :event-count (count ids)
                        :event-ids ids
                        :root (b64url (merkle-root ids))
                        :previous previous-id}
        id (b64url (crypto/sha256 (checkpoint-bytes cp)))
        with-id (assoc cp :checkpoint/id id)]
    (assoc with-id :checkpoint/signature
           (crypto/sign-with p signer (checkpoint-bytes with-id)))))

(defn valid-checkpoint? [p cp witness-public]
  (let [unsigned (dissoc cp :checkpoint/signature :checkpoint/id)
        expected (b64url (crypto/sha256 (checkpoint-bytes unsigned)))
        ids (:checkpoint/event-ids cp)]
    (and (= checkpoint-domain (:checkpoint/domain cp))
         (pos-int? (:checkpoint/seq cp))
         (if (= 1 (:checkpoint/seq cp))
           (nil? (:checkpoint/previous cp))
           (seq (:checkpoint/previous cp)))
         (= ids (vec (sort (distinct ids))))
         (= (:checkpoint/event-count cp) (count ids))
         (= (:checkpoint/root cp) (b64url (merkle-root ids)))
         (= expected (:checkpoint/id cp))
         (crypto/verify* p witness-public (checkpoint-bytes cp)
                         (:checkpoint/signature cp)))))

(defn witnesses-authorize?
  "Require k distinct, allowlisted, valid checkpoints that commit to event-id.
  Reject an equivocation at any supplied witness sequence."
  [p event-id checkpoints {:keys [k members public-of]}]
  (when-not (and (pos-int? k) (<= k (count members)))
    (throw (ex-info "invalid witness quorum" {:k k :members (count members)})))
  (let [resolved (recovery/validate-distinct-members! "witness" members public-of)
        {:keys [split-view?]} (merge-gossip [] checkpoints)
        allowed (set members)
        valid (->> checkpoints
                   (filter #(contains? allowed (:checkpoint/witness %)))
                   (filter #(some #{event-id} (:checkpoint/event-ids %)))
                   (filter #(when-let [pub (get resolved (:checkpoint/witness %))]
                              (valid-checkpoint? p % pub)))
                   (map :checkpoint/witness)
                   distinct
                   count)]
    (and (not split-view?) (>= valid k))))

(defn merge-gossip
  "Merge checkpoints by id and report equivocation: same witness+seq but a
  different content-addressed checkpoint ID. Comparing only roots misses a
  fork that reuses the event set while changing the previous chain link."
  [local remote]
  (let [all (vals (merge (into {} (map (juxt :checkpoint/id identity)) local)
                         (into {} (map (juxt :checkpoint/id identity)) remote)))
        splits (->> all
                    (group-by (juxt :checkpoint/witness :checkpoint/seq))
                    vals
                    (filter #(> (count (set (map :checkpoint/id %))) 1))
                    (mapcat identity)
                    vec)]
    {:checkpoints (vec all) :split-view? (boolean (seq splits)) :conflicts splits}))
