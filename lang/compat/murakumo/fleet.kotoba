;; murakumo.fleet — fleet inventory + Tailscale enrichment.

(ns murakumo.fleet
  (:require [babashka.process :as p]
            [murakumo.config :as config]
            [murakumo.fleet.inventory :as inv]
            [murakumo.kekkai :as kekkai]))

(defn load-fleet
  "Read fleet.edn from the repo root (or an explicit path)."
  ([] (load-fleet config/default-fleet-path))
  ([path] (config/read-edn-file path)))

(def node-port inv/node-port)
(def node-health-url inv/node-health-url)

(defn tailscale-status
  "Map of tailscale-name → {:ip :online?} from `tailscale status`.
   Empty map if tailscale is absent (the control plane still works via plain ssh)."
  []
  (inv/tailscale-status-result
   (p/sh "tailscale" "status")))

(defn enrich
  "Annotate each fleet node with its Tailscale ip/online state."
  [fleet]
  (inv/enrich fleet (tailscale-status)))

(defn select
  "Resolve a node selector to node maps, then gate through kekkai admission
   (murakumo.kekkai/apply-gate is a pass-through no-op unless a kekkai ledger
   is configured — see README ## Fleet admission — kekkai)."
  [fleet sel]
  (kekkai/apply-gate (inv/select fleet sel)))

(def node-named inv/node-named)
