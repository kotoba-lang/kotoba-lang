(ns authentication.adapters.decision-ledger
  "The port a host implements to make authentication decisions durable.
  `authentication.adapters.journal-ledger` is the implementation this repo
  ships; a host with its own transactor implements the same one method."
  (:require [authentication.datom :as datom]))

(defprotocol IDecisionLedger
  (transact! [ledger datoms opts]))

(defn persist-factor!
  ([ledger factor] (persist-factor! ledger factor {}))
  ([ledger factor opts]
   (transact! ledger (datom/factor-datoms factor) opts)))

(defn persist-decision!
  "Persists a decision and the factors it aggregated as one transaction --
  the unit `authentication.datom/decision-tx` defines, and the unit
  `:authn.decision/factors` needs in order to resolve."
  ([ledger decision] (persist-decision! ledger decision {}))
  ([ledger decision opts]
   (transact! ledger (datom/decision-tx decision) opts)))
