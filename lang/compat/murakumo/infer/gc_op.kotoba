;; murakumo.infer.gc-op — the fleet-facing disk GC operator (bb).
;;
;; Probes each node's reclaimable caches over SSH, runs the pure
;; murakumo.infer.gc/plan, and reports what it WOULD reclaim. --apply actually
;; deletes (rpc cache dirs, LRU-stale HF model dirs, old ComfyUI temp) — never
;; ollama models, active checkpoints, the ledger, or murakumo's own files.
;;
;;   bb murakumo infer gc            report per-node reclaim plan (dry-run)
;;   bb murakumo infer gc --apply    reclaim until each node hits the target
;;   bb murakumo infer gc --target 30 --apply    target 30 GiB free per node

(ns murakumo.infer.gc-op
  (:require [kotoba.lang.text :as str]
            [murakumo.fleet :as fleet]
            [murakumo.infer.gc :as gc]
            [murakumo.ssh :as ssh]))

(defn- gib [b] (/ (double b) gc/GiB))

;; One remote scan: for each reclaimable root, list immediate children as
;; `<atime-epoch> <bytes> <path>`. atime via stat, bytes via du -sk. Cheap,
;; parseable, and it never descends into protected trees.
(def ^:private scan-cmd
  (str/join " ; "
    ["set -e"
     "now=$(date +%s)"
     ;; rpc cache: each file is one tensor; treat the whole dir as one entry
     "d=$HOME/Library/Caches/llama.cpp/rpc; [ -d \"$d\" ] && "
     "  echo \"rpc-cache $(stat -f %a \"$d\" 2>/dev/null || echo $now) $(du -sk \"$d\" | cut -f1) $d\""
     ;; HF: one entry per model dir
     "for m in $HOME/.cache/huggingface/hub/models--*; do [ -d \"$m\" ] && "
     "  echo \"hf-stale $(stat -f %a \"$m\" 2>/dev/null || echo $now) $(du -sk \"$m\" | cut -f1) $m\"; done"
     ;; ComfyUI temp + output
     "for t in $HOME/comfyui/temp $HOME/comfyui/output; do [ -d \"$t\" ] && "
     "  echo \"comfy-temp $(stat -f %a \"$t\" 2>/dev/null || echo $now) $(du -sk \"$t\" | cut -f1) $t\"; done"
     ;; free bytes (KB)
     "echo \"free $(df -k / | tail -1 | awk '{print $4}')\""]))

(defn- parse-scan [out]
  (let [now-days (/ (System/currentTimeMillis) 1000.0 86400.0)
        lines (remove str/blank? (str/split-lines (str out)))
        free-kb (some (fn [l] (when (str/starts-with? l "free ")
                                (parse-long (str/trim (subs l 5))))) lines)
        entries (for [l lines
                      :let [[cls a bytes-kb & pathv] (str/split l #"\s+")]
                      :when (gc/reclaimable (keyword cls))]
                  {:class (keyword cls)
                   :atime-days (max 0.0 (- now-days (/ (double (parse-long a)) 86400.0)))
                   :bytes (* 1024 (parse-long bytes-kb))
                   :path (str/join " " pathv)})]
    {:free-bytes (* 1024 (or free-kb 0)) :entries (vec entries)}))

(defn- node-plan [node policy]
  (let [{:keys [exit out]} (ssh/sh (:host node) scan-cmd)]
    (when (zero? exit)
      (let [{:keys [free-bytes entries]} (parse-scan out)]
        (assoc (gc/plan entries free-bytes policy)
               :node (:name node) :host (:host node) :free-before free-bytes)))))

(defn -main [& args]
  (let [apply? (some #{"--apply"} args)
        target (when-let [t (second (drop-while #(not= "--target" %) args))]
                 (* (parse-long t) gc/GiB))
        policy (cond-> {} target (assoc :target-free-bytes target))
        fleet (fleet/enrich (fleet/load-fleet))
        plans (->> (:nodes fleet) (pmap #(node-plan % policy)) (filter some?))]
    (println (format "%-10s %8s %8s %9s  %s" "NODE" "FREE" "RECLAIM" "→FREE" "ACTION"))
    (doseq [{:keys [node host free-before reclaim-bytes free-after target-met? evict]} plans]
      (println (format "%-10s %6.1fG %6.1fG %8.1fG  %s"
                       node (gib free-before) (gib reclaim-bytes) (gib free-after)
                       (if apply? (str "evict " (count evict)) (str (count evict) " evictable"
                                                                     (when-not target-met? " (target NOT met)")))))
      (when apply?
        (doseq [{:keys [path bytes class]} evict]
          (let [{:keys [exit]} (ssh/sh host (format "rm -rf %s" (pr-str path)))]
            (println (format "  %-10s %6.1fG %s %s" (name class) (gib bytes) path
                             (if (zero? exit) "✓" "FAILED")))))))
    (when-not apply?
      (println "\ndry-run — add --apply to reclaim (ollama models + active checkpoints are never touched)"))))
