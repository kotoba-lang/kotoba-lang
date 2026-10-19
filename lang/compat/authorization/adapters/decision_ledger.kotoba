(ns authorization.adapters.decision-ledger
  (:require [authorization.datom :as datom]))

(defprotocol IDecisionLedger
  (transact! [ledger datoms opts]))

(defn persist-request!
  ([ledger request] (persist-request! ledger request {}))
  ([ledger request opts]
   (transact! ledger (datom/request-datoms request) opts)))

(defn persist-decision!
  ([ledger request decision] (persist-decision! ledger request decision {}))
  ([ledger request decision opts]
   (transact! ledger
              (vec (concat (datom/request-datoms request)
                           (datom/decision-datoms decision)))
              opts)))
