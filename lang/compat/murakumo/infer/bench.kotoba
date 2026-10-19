;; murakumo.infer.bench — media generation throughput evaluation (bb).
;;
;; The question media generation actually poses to a fleet is throughput, not
;; latency: one mini renders an SDXL image in ~130s, but N minis render N images
;; in ~130s too — the scheduler spreads a batch. This measures that speedup
;; empirically: dispatch a batch of identical jobs, let murakumo.infer.schedule
;; place them, and report images/min for 1 node vs the whole fleet.
;;
;;   bb murakumo infer bench image [batch=6]     SDXL throughput, 1 vs fleet
;;   bb murakumo infer bench video [seconds=1]   SVD i2v, single-node speed
;;   bb murakumo infer bench audio [seconds=10]  Stable-Audio, single-node speed

(ns murakumo.infer.bench
  (:require [kotoba.lang.edn :as edn]
            [murakumo.fleet :as fleet]
            [murakumo.infer.media :as media]
            [murakumo.infer.schedule :as sched]))

(defn- now-ms [] (System/currentTimeMillis))

(defn- run-one!
  "Render one job on `node`, return its wall-clock ms (nil on failure)."
  [node kind opts]
  (try
    (let [t0 (now-ms)]
      (media/run-job! node kind opts)              ; blocking single render
      (- (now-ms) t0))
    (catch Exception e
      (println (format "  [%s] failed: %s" (:name node) (.getMessage e)))
      nil)))

(defn- throughput [n-jobs total-ms]
  (if (and total-ms (pos? total-ms))
    (/ (* 60000.0 n-jobs) total-ms)
    0.0))

(defn cmd-image [[batch]]
  (let [batch (or (some-> batch parse-long) 6)
        cfg (edn/read-string (slurp "infer.edn"))
        model (first (filter #(= :image (:model/kind %)) (vals (:models cfg))))
        f (fleet/enrich (fleet/load-fleet))
        live (media/live-fleet f)
        eligible (filter #(sched/eligible? % model) live)
        opts {:prompt "1girl, cloud spirit, masterpiece, best quality" :steps 20
              :negative "lowres, watermark" :width 832 :height 1216}]
    (println (format "SDXL throughput — batch %d, %d eligible node(s): %s"
                     batch (count eligible) (mapv :name eligible)))
    (when (empty? eligible) (println "no eligible node") (System/exit 1))
    ;; 1) single node: run `batch` jobs serially on one node
    (let [n1 (first eligible)
          node1 (first (filter #(= (:name n1) (:name %)) (:nodes f)))
          t0 (now-ms)
          _ (dotimes [_ batch] (run-one! node1 :image opts))
          single-ms (- (now-ms) t0)]
      (println (format "  1 node  (%s): %d imgs in %.0fs = %.2f imgs/min"
                       (:name n1) batch (/ single-ms 1000.0) (throughput batch single-ms)))
      ;; 2) fleet: scheduler spreads the batch across eligible nodes, in parallel
      (let [assign (sched/assign eligible (repeat batch {:model model}))
            by-node (group-by :node assign)
            t0 (now-ms)
            futures (for [[nname jobs] by-node
                          :let [node (first (filter #(= nname (:name %)) (:nodes f)))]]
                      (future (doseq [_ jobs] (run-one! node :image opts))))
            _ (doseq [fut futures] @fut)
            fleet-ms (- (now-ms) t0)]
        (println (format "  fleet   (%d nodes): %d imgs in %.0fs = %.2f imgs/min  (%.1fx)"
                         (count by-node) batch (/ fleet-ms 1000.0)
                         (throughput batch fleet-ms)
                         (/ (double single-ms) (max 1 fleet-ms))))))))

(defn cmd-video [[which seconds]]
  ;; `which` ∈ {ltx, svd}; default ltx (16GB-friendly DiT)
  (let [ltx? (not= which "svd")
        seconds (or (some-> (if ltx? seconds which) parse-long) 3)
        kind (if ltx? :ltx-video :video)
        cfg (edn/read-string (slurp "infer.edn"))
        model (first (filter #(= kind (:model/kind %)) (vals (:models cfg))))
        f (fleet/enrich (fleet/load-fleet))
        live (media/live-fleet f)
        node (some #(when (sched/eligible? % model) %) live)
        fps (if ltx? 24 16)]
    (if-not node
      (println (format "no node holds %s with enough free memory" (:model/id model)))
      (let [fleet-node (first (filter #(= (:name node) (:name %)) (:nodes f)))
            ms (run-one! fleet-node kind {:seconds seconds :fps fps :steps (if ltx? 24 20)
                                          :ckpt (:model/checkpoint model)
                                          :prompt "a cloud spirit drifting over a stormy sky, cinematic"})]
        (when ms
          (let [frames (int (* fps seconds))]
            (println (format "%s: %ds (%d frames) in %.0fs on %s = %.2f frames/s (%.2fx realtime)"
                             (:model/family model) seconds frames (/ ms 1000.0) (:name node)
                             (/ (double frames) (/ ms 1000.0))
                             (/ (double seconds) (/ ms 1000.0))))))))))

(defn cmd-audio [[seconds]]
  (let [seconds (or (some-> seconds parse-long) 10)
        cfg (edn/read-string (slurp "infer.edn"))
        model (first (filter #(= :audio (:model/kind %)) (vals (:models cfg))))
        f (fleet/enrich (fleet/load-fleet))
        live (media/live-fleet f)
        node (some #(when (sched/eligible? % model) %) live)]
    (if-not node
      (println "no node holds an audio checkpoint with enough free memory")
      (let [fleet-node (first (filter #(= (:name node) (:name %)) (:nodes f)))
            ms (run-one! fleet-node :audio {:seconds seconds :steps 50
                                            :prompt "rain on a tin roof, ambient"})]
        (when ms
          (println (format "Stable-Audio: %ds in %.0fs on %s = %.1fx realtime"
                           seconds (/ ms 1000.0) (:name node)
                           (/ (double seconds) (/ ms 1000.0)))))))))

(defn -main [& [kind & args]]
  (case kind
    "image" (cmd-image args)
    "video" (cmd-video args)
    "audio" (cmd-audio args)
    (println "usage: bb murakumo infer bench image [batch] | video [seconds] | audio [seconds]")))
