;; murakumo.dash — fleet state persistence + a web dashboard.
;;
;; A background snapshotter polls the fleet every `interval` seconds (each node's
;; /health + live libp2p link count + the component CIDs it HOSTS) and:
;;   1. PERSISTS the snapshot to the kotoba Datom log (append-only as-of history of
;;      the fleet's heartbeat + placement state) via a node's atproto.repo.write;
;;   2. caches it for the dashboard.
;; The HTTP server (babashka's built-in http-kit) renders `murakumo status` as an
;; auto-refreshing web page, plus a JSON API at /api.

(ns murakumo.dash
  (:require [babashka.process :as p]
            [json.compat :as json]
            [murakumo.config :as config]
            [murakumo.dash.state :as state]
            [murakumo.fleet :as fleet]
            [murakumo.identity :as identity]
            [murakumo.persist :as persist]
            [murakumo.provision.plan :as provision]
            [murakumo.report :as report]
            [murakumo.ssh :as ssh]
            [kotoba.lang.http.host.httpkit :as http]))

;; ── identity / graph (kept self-contained — mirrors core's operator token) ────

(def ^:private runtime
  (config/current-runtime-context))

(defn- operator-seed [fleet]
  (config/current-operator-seed fleet))

(defn- bin []
  (:cli-kotoba runtime))

(defn- did-for [seed]
  (identity/did-from-command-result
   (apply p/sh (identity/did-derive-argv (bin) seed))))

;; ── collect ──────────────────────────────────────────────────────────────────

(defn- node-port [fleet n] (fleet/node-port fleet n))
(defn- p2p-port [fleet n] (provision/node-p2p-port fleet n))

(defn- probe
  "One round-trip per node: health JSON + distinct connected peers + the distinct
   component CIDs this node has executed (= what the lattice placed here)."
  [fleet n]
  (let [port (node-port fleet n)
        ;; the log colourises with ANSI codes (cid<ESC>=<ESC>bafy…), so extract the
        ;; bafy… CID value directly from executed-trigger lines, not a `cid=` literal.
        out (:out (ssh/sh (:host n) (state/probe-command port)))
        lines (state/probe-lines out)
        h (state/parse-health #(json/parse-string % true) (get lines "H" ""))]
    (state/probe-node n h lines (p2p-port fleet n))))

(defn collect [fleet]
  (let [fleet (fleet/enrich fleet)
        ts (.toString (java.time.Instant/now))]
    (state/snapshot
     fleet
     ts
     (mapv (fn [{:keys [action node]}]
             (case action
               :probe (probe fleet node)
               :down (state/down-node node)))
           (state/collect-node-plans
            (:nodes fleet)
            #(ssh/reachable? (:host %)))))))

;; ── persist (append-only as-of history on the kotoba Datom log) ───────────────

(def ^:private snap-seq (atom 0))

(def ^:private trust-warned? (atom false))

(defn- internal-trust!
  "Read the internal-trust secret for this write, announcing ONCE per process when
   it is unconfigured. This write is a background path whose only signal is a
   boolean, so a silently missing credential is exactly the failure this warning
   exists to prevent (ADR-2608124000)."
  []
  (let [trust (persist/internal-trust)]
    (when (and (nil? trust) (compare-and-set! trust-warned? false true))
      (binding [*out* *err*]
        (println (str "WARN murakumo.dash: " persist/internal-trust-env
                      " is unset — fleet snapshot writes carry NO "
                      persist/internal-trust-header-name " header ("
                      (name (persist/internal-trust-status trust))
                      "). Harmless while kotoba-server's require_internal_trust"
                      " gate is disabled fleet-wide (ADR-2608124000); the moment"
                      " that gate is armed these background writes start failing"
                      " and only the persisted counter would show it."))))
    trust))

(defn persist!
  "Write the snapshot as an atproto record into graph 'murakumo-fleet' on a node's
   Datom log — one tx per snapshot ⇒ tamper-evident, queryable as-of history."
  [fleet token target snap]
  (let [seq (swap! snap-seq inc)
        write-plan (persist/snapshot-write-plan
                    (System/currentTimeMillis)
                    seq
                    snap
                    (json/generate-string snap))]
    (p/sh "bash" "-c" (persist/write-forward-command write-plan (node-port fleet target) (:host target)))
    (Thread/sleep persist/forward-settle-ms)
    (let [body (json/generate-string (:envelope write-plan))
          r (apply p/sh (persist/write-curl-argv write-plan token body (internal-trust!)))]
      (persist/write-ok? (:out r)))))

;; ── history + liveness alerts ────────────────────────────────────────────────

(def ^:private cache (atom {:ts "—" :nodes []}))
(def ^:private persisted (atom 0))
(def ^:private history (atom []))     ; ring of recent snapshots (time-travel)
(def ^:private alerts (atom []))      ; ring of liveness alerts
(def ^:private hist-cap 240)          ; ~1h at 15s
(def ^:private alert-cap 100)

(def diff-alerts state/diff-alerts)

;; ── web ──────────────────────────────────────────────────────────────────────

(defn- query-at [req]
  (state/query-at (:query-string req)))

(defn- handler [req]
  (case (:uri req)
    "/api"         (state/json-response (json/generate-string @cache))
    "/api/history" (state/json-response (json/generate-string @history))
    "/api/alerts"  (state/json-response (json/generate-string @alerts))
    (let [{:keys [snapshot at total live?]} (state/selected-snapshot @history @cache (query-at req))]
      (state/html-response (state/render-html snapshot at total live? @persisted @alerts)))))

(defn- snapshotter [fleet token target interval]
  (future
    (loop []
      (try
        (let [prev @cache
              snap (collect fleet)
              al (diff-alerts (when (seq (:nodes prev)) prev) snap)]
          (reset! cache snap)
          (swap! history #(state/append-capped % hist-cap snap))
          (when (seq al)
            (swap! alerts #(state/concat-capped % alert-cap al))
            (doseq [a al] (println (report/alert-line a))))
          (when (and token target (persist! fleet token target snap)) (swap! persisted inc)))
        (catch Exception e (binding [*out* *err*] (println (report/snapshot-error-line (.getMessage e))))))
      (Thread/sleep (state/interval-sleep-ms interval))
      (recur))))

(defn -main [& args]
  (let [fleet (fleet/load-fleet)
        {:keys [port interval]} (state/dashboard-options args)
        seed (operator-seed fleet)
        token (when seed (identity/op-token (did-for seed)))
        target (fleet/node-named fleet "asher")]
    (when-not token (println report/dashboard-no-persistence-line))
    (snapshotter fleet token target interval)
    (http/run-server handler {:port port})
    (println (report/dashboard-start-line port interval))
    @(promise)))
