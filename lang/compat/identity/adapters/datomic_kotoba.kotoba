(ns identity.adapters.datomic-kotoba
  (:require [identity.adapters.ledger :as ledger]))

(defprotocol IDatomicKotoba
  (transact-datoms! [conn datoms opts])
  (tx-log [conn]))

(defn ledger-backend
  ([conn] (ledger-backend conn {}))
  ([conn default-opts]
   (reify ledger/ILedger
     (transact! [_ datoms opts]
       (transact-datoms! conn datoms (merge default-opts opts))))))

(defn memory-datomic-kotoba
  ([] (memory-datomic-kotoba (atom [])))
  ([txs]
   (reify IDatomicKotoba
     (transact-datoms! [_ datoms opts]
       (let [tx {:tx/data datoms :tx/opts opts}]
         (swap! txs conj tx)
         tx))
     (tx-log [_] @txs))))
