;; murakumo.infer.relay-server — a runnable WSS work relay (bb + http-kit).
;;
;; The transport the browser tier actually dials. Browser/wasm workers open ONE
;; outbound WebSocket to this relay (NAT-free), send :hello, then loop
;; :ready → :job → compute → :result. The pure protocol/queue lives in
;; murakumo.infer.relay; this file is just the socket shell + a lease-expiry
;; tick + a tiny HTTP surface to enqueue jobs and read stats.
;;
;;   bb murakumo infer relay [port=8091]         start the relay
;;   POST /enqueue  {:kind :input :price}         queue a job (dispatcher side)
;;   GET  /stats                                  workers/queued/in-flight/settled
;;   WS   /ws                                     workers connect here
;;
;; Settled results are appended to a local ledger (and, when wired, POSTed to
;; cloud-murakumo /infer/runs as signed run records).

(ns murakumo.infer.relay-server
  (:require [babashka.process :as p]
            [json.compat :as json]
            [kotoba.lang.edn :as edn]
            [murakumo.infer.credits :as credits]
            [murakumo.infer.postproc :as pp]
            [murakumo.infer.relay :as relay]
            [murakumo.config :as config]
            [murakumo.secret :as secret]
            [kotoba.lang.http.host.httpkit :as http]))

(defonce ^:private state (atom (relay/init)))
(defonce ^:private chans (atom {}))          ; worker-id → channel
(defonce ^:private ledger-file ".murakumo-relay-ledger.edn")
;; when set (MURAKUMO_CLOUD env), each settled job is POSTed to cloud-murakumo
;; /infer/runs as a signed run record — the dispatcher loop closes here.
;; Config URL (not a secret) — exact name only via config inject.
(defn- cloud-url []
  (config/config-string-or-nil "MURAKUMO_CLOUD"))
;; ADR-2607995000 Fix #6: /infer/runs service secret via named secret fetch
;; (murakumo-service-token → exact MURAKUMO_SERVICE_TOKEN).
(defn- send! [ch msg] (http/send! ch (json/generate-string msg)))

(defn- now [] (System/currentTimeMillis))

(defn swarm-run-record
  "Pure: build the run record for a completed browser-swarm job. `settled` is
   `{:did :credits ...}` — the total credits already earned for this job
   (settled upstream of this ns; there is no memory×time plan to run through
   `murakumo.infer.credits/settle` here, since a browser-swarm job is a
   single ad-hoc worker, not a fleet-planned shard job).

   The treasury cut uses `credits/default-protocol-frac` — the SAME named
   constant `settle` uses for fleet jobs — instead of a second, independently
   hardcoded fraction (ADR-2607995000: two definitions of \"the treasury
   cut\" drifting apart was the bug this fixes). There is no separate
   head-frac cut here: a single browser worker IS its own conducting node,
   so `settle`'s head-cut + memory-time-pool-share would both land on that
   same worker anyway — net-equivalent to \"worker gets everything except
   the treasury cut\"."
  [{:keys [did credits] :as _settled} protocol-frac]
  (let [treasury (* protocol-frac credits)]
    {:model "browser-swarm" :units {:jobs 1}
     :run/total credits
     :run/shares {did (- credits treasury)}
     :run/treasury treasury
     :run/proof :verified}))

(defn- post-run! [settled]
  ;; report the settled job to cloud-murakumo as a run record: the worker's did
  ;; earns :run/shares, the protocol takes its treasury cut — same ledger as
  ;; text/media generation. Fire-and-forget (best-effort; the local ledger is
  ;; the durable record). Bearer-authenticates with MURAKUMO_SERVICE_TOKEN when
  ;; configured (unconfigured => the write is now rejected server-side per
  ;; Fix #6, same honesty as any other dropped-write audit gap).
  (when-let [cloud-url (cloud-url)]
    (let [run (swarm-run-record settled credits/default-protocol-frac)
          service-token (secret/resolve-service-token)
          args (cond-> ["curl" "-s" "-m" "5" "-X" "POST" (str cloud-url "/infer/runs")
                        "-H" "Content-Type: application/json"]
                 service-token (into ["-H" (str "Authorization: Bearer " service-token)])
                 true (into ["-d" (json/generate-string run)]))]
      (try (apply p/sh args)
           (catch Exception _ nil)))))

(defn- append-ledger! [settled]
  (let [l (or (try (edn/read-string (slurp ledger-file)) (catch Exception _ nil)) [])]
    (spit ledger-file (pr-str (conj l (select-keys settled [:job-id :did :credits :ms])))))
  (post-run! settled))

(defn- handle-msg [ch raw]
  (let [{:keys [msg] :as m} (json/parse-string raw true)]
    (case (keyword msg)
      :hello
      (let [[wid st] (relay/on-hello @state m)]
        (reset! state st)
        (swap! chans assoc wid ch)
        (swap! chans assoc ch wid)                ; reverse lookup for on-close
        (send! ch {:msg :welcome :worker-id wid}))

      :ready
      (when-let [wid (get @chans ch)]
        (let [[reply st] (relay/on-ready @state wid (now))]
          (reset! state st)
          (send! ch reply)))

      :result
      (when-let [wid (get @chans ch)]
        ;; proof-of-compute: for feature-extraction jobs, verify the returned
        ;; histogram against the pure reference BEFORE crediting. A bogus result
        ;; is rejected (requeued via lease), so a lying worker earns nothing.
        (let [job (get-in @state [:assigned (:job-id m) :job])
              {:keys [pixels op w h samples]} (:input job)
              ;; :kind arrives as a JSON string ("media-postproc"), keep string-safe
              postproc? (contains? #{"media-postproc" :media-postproc} (:kind job))
              ok? (cond
                    (not postproc?) true
                    (contains? #{"upscale-2x" :upscale-2x} op)
                    (pp/verify-upscale pixels w h (get-in m [:output :pixels]) samples)
                    (some? pixels)
                    (pp/verify pixels (get-in m [:output :histogram]))
                    :else true)]
          (if-not ok?
            (send! ch {:msg :rejected :job-id (:job-id m) :reason "verification failed"})
            (let [[settled st] (relay/on-result @state wid m)]
              (reset! state st)
              (when settled
                (append-ledger! settled)
                (send! ch settled))))))

      :ping (send! ch {:msg :pong})
      nil)))

(defn- ws-handler [req]
  (http/as-channel req
    {:on-receive (fn [ch raw] (handle-msg ch raw))
     :on-close (fn [ch _]
                 (when-let [wid (get @chans ch)]
                   (swap! state relay/drop-worker wid (now))
                   (swap! chans dissoc ch wid)))}))

(defn- http-handler [req]
  (case [(:request-method req) (:uri req)]
    [:get "/ws"]     (ws-handler req)
    [:get "/stats"]  {:status 200 :headers {"content-type" "application/json"}
                      :body (json/generate-string (relay/stats @state))}
    [:post "/enqueue"] (let [job (json/parse-string (slurp (:body req)) true)
                             [jid st] (relay/enqueue @state job)]
                         (reset! state st)
                         {:status 201 :headers {"content-type" "application/json"}
                          :body (json/generate-string {:job-id jid})})
    {:status 404 :body "not found"}))

(defn -main [& [port]]
  (let [port (or (some-> port parse-long) 8091)]
    ;; lease-expiry tick: requeue jobs from vanished workers every 10s
    (future (loop [] (Thread/sleep 10000)
                  (swap! state relay/expire-leases (now) 60000) (recur)))
    (http/run-server http-handler {:port port})
    (println (format "murakumo relay on ws://0.0.0.0:%d/ws  (POST /enqueue, GET /stats)" port))
    @(promise)))
