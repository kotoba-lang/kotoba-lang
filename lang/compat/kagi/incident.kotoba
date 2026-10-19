(ns kagi.incident
  "Fail-closed compromise response: detect input is converted to an ordered,
  immutable plan; injected authorities perform containment, revocation,
  rotation, restore and verification. Every completed step returns bounded
  evidence and receives a hybrid-signed receipt."
  (:require [cbor.core :as cbor]
            [clojure.string :as str]
            [kagi.crypto :as crypto]))

(def receipt-domain "kagi/incident-step-receipt/v1")
(def action-order {:contain 0 :revoke 1 :rotate 2 :restore 3 :verify 4})

(defn- hex256? [x]
  (and (string? x) (boolean (re-matches #"[0-9a-f]{64}" x))))

(defn plan
  "Create the only permitted response ordering. Keys and signals are sorted
  and deduplicated so the same detection event produces the same action list."
  [{:incident/keys [id detected-at signals compromised-keys clean-backup-id]}]
  (when-not (and (not (str/blank? id)) (not (str/blank? detected-at))
                 (seq signals) (seq compromised-keys)
                 (not (str/blank? clean-backup-id)))
    (throw (ex-info "incomplete incident detection" {:incident/id id})))
  (let [keys (vec (sort (distinct compromised-keys)))]
    #:incident{:id id
               :detected-at detected-at
               :signals (vec (sort (distinct signals)))
               :clean-backup-id clean-backup-id
               :actions (vec
                         (concat
                          [{:action :contain :target id}]
                          (map #(hash-map :action :revoke :target %) keys)
                          (map #(hash-map :action :rotate :target %) keys)
                          [{:action :restore :target clean-backup-id}
                           {:action :verify :target id}]))}))

(defn receipt-bytes [receipt]
  (cbor/encode (assoc (dissoc receipt :receipt/signature)
                      :receipt/domain receipt-domain)))

(defn- signed-receipt [provider signer incident-id index action result]
  (let [receipt #:receipt{:incident-id incident-id
                          :index index
                          :action (:action action)
                          :target (:target action)
                          :status :passed
                          :artifact-sha256 (:artifact-sha256 result)}]
    (assoc receipt :receipt/signature
           (crypto/sign* provider signer (receipt-bytes receipt)))))

(defn valid-receipt? [provider public receipt]
  (and (= :passed (:receipt/status receipt))
       (nat-int? (:receipt/index receipt))
       (contains? action-order (:receipt/action receipt))
       (hex256? (:receipt/artifact-sha256 receipt))
       (crypto/verify* provider public (receipt-bytes receipt)
                       (:receipt/signature receipt))))

(defn execute!
  "Execute in strict order. `handlers` is action keyword -> function. A handler
  must return `{:ok? true :artifact-sha256 <64 lowercase hex>}`. Missing,
  throwing, false or malformed evidence stops the loop; no later recovery step
  can make a failed containment/revocation look successful."
  [provider signer incident-plan handlers]
  (loop [index 0
         actions (:incident/actions incident-plan)
         receipts []]
    (if-let [action (first actions)]
      (let [handler (get handlers (:action action))]
        (if-not (fn? handler)
          #:incident{:id (:incident/id incident-plan) :status :blocked
                     :failed-action action :reason :missing-authority
                     :receipts receipts}
          (let [result (try (handler action incident-plan)
                            (catch Exception e
                              {:ok? false :error (or (ex-message e) "handler failed")}))]
            (if-not (and (= true (:ok? result))
                         (hex256? (:artifact-sha256 result)))
              #:incident{:id (:incident/id incident-plan) :status :blocked
                         :failed-action action :reason :invalid-or-failed-evidence
                         :receipts receipts}
              (recur (inc index) (next actions)
                     (conj receipts
                           (signed-receipt provider signer (:incident/id incident-plan)
                                           index action result)))))))
      #:incident{:id (:incident/id incident-plan) :status :recovered
                 :receipts receipts})))
