(ns authorization.adapters.edn-decision-ledger
  (:require [authorization.adapters.decision-ledger :as ledger]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]))

(defn- read-ledger [file]
  (if (.exists (io/file file))
    (edn/read-string (slurp file))
    {:txs [] :datoms [] :decision-ids #{}}))

(defn- write-ledger! [file state]
  (let [f (io/file file)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit f (pr-str state))
    state))

(defn- decision-ids [datoms]
  (into #{}
        (keep #(when (contains? % :authz.decision/decision)
                 (:db/id %)))
        datoms))

(defn- duplicate-decision-ids [state datoms]
  (seq (set/intersection (:decision-ids state) (decision-ids datoms))))

(defn edn-decision-ledger [file]
  (let [lock (Object.)]
    (reify ledger/IDecisionLedger
      (transact! [_ datoms opts]
        (locking lock
          (let [state (read-ledger file)
                duplicates (duplicate-decision-ids state datoms)]
            (when (and duplicates (not (:allow-replay? opts)))
              (throw (ex-info "authorization decision already persisted"
                              {:error :authz.decision/replay
                               :request-ids (vec duplicates)})))
            (let [tx-id (or (:tx/id opts) (str "tx-" (inc (count (:txs state)))))
                  tx {:tx/id tx-id
                      :tx/datoms (count datoms)
                      :tx/request-id (:request-id opts)
                      :tx/at (:at opts)}
                  next-state (-> state
                                 (update :txs conj tx)
                                 (update :datoms into (map #(assoc % :tx/id tx-id) datoms))
                                 (update :decision-ids into (decision-ids datoms)))]
              (write-ledger! file next-state)
              tx)))))))

(defn all-datoms [file]
  (:datoms (read-ledger file)))

(defn transactions [file]
  (:txs (read-ledger file)))
