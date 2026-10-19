(ns kagi.rotation
  "Content-addressed hybrid key-rotation events.

  Normal continuity requires signatures from both the old and new hybrid keys.
  Compromise recovery deliberately does not trust the old key and is accepted only
  through an injected threshold/witness policy."
  (:require [cbor.core :as cbor]
            [kagi.crypto :as crypto])
  (:import [java.util Base64]
           [java.time Instant]))

(def domain "kotoba/key-rotation/v1")
(def ^:private signature-keys #{:rotation/old-signature :rotation/new-signature
                                :rotation/authorizer-signature
                                :rotation/recovery-approvals :rotation/witness-checkpoint
                                :rotation/witness-checkpoints})

(defn signing-bytes ^bytes [event]
  (cbor/encode (apply dissoc event signature-keys)))

(defn event-id [event]
  (-> (Base64/getUrlEncoder)
      (.withoutPadding)
      (.encodeToString (crypto/sha256 (signing-bytes event)))))

(defn new-event
  [{:keys [subject purpose from-key to-key from-epoch reason parents policy-cid not-before]}]
  (when-not (and subject purpose from-key to-key (nat-int? from-epoch))
    (throw (ex-info "incomplete rotation event" {:subject subject :purpose purpose
                                                  :from-epoch from-epoch})))
  (let [e #:rotation{:version 1
                     :domain domain
                     :subject subject
                     :purpose purpose
                     :from-key from-key
                     :to-key to-key
                     :from-epoch from-epoch
                     :to-epoch (inc from-epoch)
                     :reason (or reason :scheduled)
                     :not-before (or not-before (str (Instant/now)))
                     :parents (vec parents)
                     :policy-cid policy-cid}]
    (assoc e :rotation/id (event-id e))))

(defn sign-normal [p event old-secret new-secret]
  (let [msg (signing-bytes event)]
    (assoc event
           :rotation/old-signature (crypto/sign-with p old-secret msg)
           :rotation/new-signature (crypto/sign-with p new-secret msg))))

(defn sign-recovery
  "A compromise transition proves possession of the replacement key. The old
  key is intentionally not trusted; admission additionally requires recovery
  and witness quorums."
  [p event new-secret]
  (when-not (= :compromise (:rotation/reason event))
    (throw (ex-info "recovery signature requires compromise reason" {})))
  (assoc event :rotation/new-signature
         (crypto/sign-with p new-secret (signing-bytes event))))

(defn sign-authorized
  "Sign a non-authority rotation (for example a per-item DEK epoch) with the
  active vault authority key."
  [p event authorizer-id secret]
  (let [event* (assoc event :rotation/authorizer authorizer-id)
        event** (assoc event* :rotation/id (event-id (dissoc event* :rotation/id)))]
    (assoc event** :rotation/authorizer-signature
           (crypto/sign-with p secret (signing-bytes event**)))))

(defn valid-authorized? [p event authorizer-public]
  (and (= (:rotation/id event) (event-id (dissoc event :rotation/id)))
       (crypto/verify* p authorizer-public (signing-bytes event)
                       (:rotation/authorizer-signature event))))

(defn valid-normal?
  [p event old-public new-public]
  (let [expected-id (event-id (dissoc event :rotation/id))
        msg (signing-bytes event)]
    (boolean
     (and (= domain (:rotation/domain event))
          (= 1 (:rotation/version event))
          (= (inc (:rotation/from-epoch event)) (:rotation/to-epoch event))
          (= expected-id (:rotation/id event))
          (crypto/verify* p old-public msg (:rotation/old-signature event))
          (crypto/verify* p new-public msg (:rotation/new-signature event))))))

(defn valid-recovery?
  "Validate a compromised-old-key transition. recovery-authorized? and
  witness-authorized? are policy callbacks over the complete event."
  [p event new-public recovery-authorized? witness-authorized?]
  (let [expected-id (event-id (dissoc event :rotation/id))
        msg (signing-bytes event)]
    (boolean
     (and (= :compromise (:rotation/reason event))
          (= expected-id (:rotation/id event))
          (= (inc (:rotation/from-epoch event)) (:rotation/to-epoch event))
          (crypto/verify* p new-public msg (:rotation/new-signature event))
          (recovery-authorized? event)
          (witness-authorized? event)))))

(defn competing-children
  "Return events which attempt to advance the same subject/purpose/epoch."
  [events]
  (->> events
       (group-by (juxt :rotation/subject :rotation/purpose :rotation/from-epoch))
       vals
       (filter #(> (count %) 1))
       (mapcat identity)
       vec))
