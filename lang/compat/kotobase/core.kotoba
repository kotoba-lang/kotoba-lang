(ns kotobase.core
  "Public Promise-based Kotobase API for JavaScript and Worker deployments."
  (:require [kotobase.engine :as engine]))

(defn open [options] (engine/open options))
(defn head [database] (engine/head database))
(defn transact! [database tx-data] (engine/transact! database tx-data))
(defn datoms
  ([database] (engine/datoms database))
  ([database options] (engine/datoms database options)))
(defn q [database pattern] (engine/q database pattern))
(defn query
  ([database query] (engine/query database query))
  ([database query inputs] (engine/query database query inputs)))
(defn pull [database entity pattern] (engine/pull database entity pattern))
