(ns kagi.key-registry
  "Fail-closed key lifecycle metadata and use-time policy checks."
  (:require [clojure.string :as str]))

(def states #{:preactive :active :decrypt-or-verify-only :revoked :destroyed})
(def originator-ops #{:encrypt :sign :encapsulate :wrap})
(def recipient-ops #{:decrypt :verify :decapsulate :unwrap})

(defn key-record
  [{:keys [id purpose suite epoch created-at not-before originator-not-after
           custody-ref parent public-cid]}]
  (when-not (and (not (str/blank? (str id))) purpose suite (nat-int? epoch)
                 created-at not-before originator-not-after custody-ref)
    (throw (ex-info "incomplete key metadata" {:id id :purpose purpose :epoch epoch})))
  #:key{:id id :purpose purpose :suite suite :epoch epoch :state :preactive
        :created-at created-at :not-before not-before
        :originator-not-after originator-not-after :custody-ref custody-ref
        :parent parent :public-cid public-cid})

(defn transition
  [key next-state at]
  (let [allowed? (contains?
                  #{[:preactive :active]
                   [:preactive :revoked]
                   [:active :decrypt-or-verify-only]
                   [:active :revoked]
                   [:decrypt-or-verify-only :revoked]
                   [:decrypt-or-verify-only :destroyed]
                   [:revoked :destroyed]}
                  [(:key/state key) next-state])]
    (when-not (and (states next-state) allowed?)
      (throw (ex-info "invalid key state transition"
                      {:from (:key/state key) :to next-state :key/id (:key/id key)})))
    (cond-> (assoc key :key/state next-state)
      (= next-state :active) (assoc :key/activated-at at)
      (= next-state :decrypt-or-verify-only) (assoc :key/retired-at at)
      (= next-state :revoked) (assoc :key/revoked-at at)
      (= next-state :destroyed) (assoc :key/destroyed-at at))))

(defn allowed-use?
  "ISO-8601 timestamps compare lexically when normalized to UTC. Callers must pass
  their trusted current time rather than allowing this namespace to read a wall clock."
  [key op now]
  (let [state (:key/state key)
        within? (and (not (neg? (compare now (:key/not-before key))))
                     (not (pos? (compare now (:key/originator-not-after key)))))]
    (cond
      (originator-ops op) (and (= :active state) within?)
      (recipient-ops op) (contains? #{:active :decrypt-or-verify-only} state)
      :else false)))

(defn authorize!
  "Return key when its lifecycle permits op at now; otherwise fail closed."
  [key op now]
  (when-not (and key now (allowed-use? key op now))
    (throw (ex-info "key lifecycle denied cryptographic use"
                    {:key/id (:key/id key) :key/purpose (:key/purpose key)
                     :key/epoch (:key/epoch key) :key/state (:key/state key)
                     :op op :now now})))
  key)

(defn current-key
  "Return the sole active key for a purpose. Duplicate active epochs fail closed."
  [registry purpose]
  (let [active (filter #(and (= purpose (:key/purpose %))
                             (= :active (:key/state %)))
                       (vals registry))]
    (when (> (count active) 1)
      (throw (ex-info "multiple active keys" {:purpose purpose
                                               :ids (mapv :key/id active)})))
    (first active)))
