(ns identity.adapters.edn-ledger
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [identity.adapters.ledger :as ledger]))

(defn- read-ledger [file]
  (if (.exists (io/file file))
    (edn/read-string (slurp file))
    {:txs [] :datoms []}))

(defn- write-ledger! [file state]
  (let [f (io/file file)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit f (pr-str state))
    state))

(defn edn-ledger [file]
  (let [lock (Object.)]
    (reify ledger/ILedger
      (transact! [_ datoms opts]
        (locking lock
          (let [state (read-ledger file)
                tx-id (or (:tx/id opts) (str "tx-" (inc (count (:txs state)))))
                tx {:tx/id tx-id
                    :tx/datoms (count datoms)
                    :tx/case-ref (:case-ref opts)
                    :tx/at (:at opts)}
                next-state (-> state
                               (update :txs conj tx)
                               (update :datoms into (map #(assoc % :tx/id tx-id) datoms)))]
            (write-ledger! file next-state)
            tx))))))

(defn all-datoms [file]
  (:datoms (read-ledger file)))

(defn transactions [file]
  (:txs (read-ledger file)))
