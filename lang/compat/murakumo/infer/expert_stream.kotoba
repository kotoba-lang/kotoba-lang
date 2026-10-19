(ns murakumo.infer.expert-stream
  "Qwen4Exp-aware single-node NVMe streaming plan and benchmark contract.

  This is intentionally not the generic waste/mlx-moe planner. It pins the
  exact lossless Qwen3.8-Flash-Next recipe and records macOS page-cache limits
  instead of claiming Linux O_DIRECT semantics on Apple NVMe."
  (:require [kotoba.lang.text :as str]
            [murakumo.infer.plan :as plan]))

(def engine-revision "039b8f1e2f4abd40f167da8ca7879135a4dee429")
;; M4/16 GiB qualification: 2,000 MiB displaced the OS page cache and reduced
;; 8-token decode from 1.937 tok/s to 0.306/0.287 tok/s. Keep expert selection
;; streaming enabled, but retain no user-space expert cache on this profile.
(def default-cache-mib 0)
(def default-io-threads 4)
(def default-gpu-layers 0)
(def disk-reserve-bytes (* 8 plan/GiB))

(defn plan
  "Choose one node that can hold all GGUF shards plus an 8 GiB safety floor."
  [model nodes]
  (let [weight-bytes (long (:model/weight-bytes model 0))
        candidates (->> nodes
                        (filter #(or (nil? (:model/target-node model))
                                     (= (:model/target-node model) (:name %))))
                        (map (fn [node]
                               (let [present (min weight-bytes
                                                  (long (:model-present-bytes node 0)))
                                     required (+ (- weight-bytes present)
                                                 disk-reserve-bytes)]
                                 (assoc node :required-new-bytes required))))
                        (filter #(and (>= (:disk-free-bytes % 0)
                                          (:required-new-bytes %))
                                      (>= (:mem-bytes % 0) (* 16 plan/GiB))))
                        (sort-by (juxt (comp - :disk-free-bytes) :name)))
        node (first candidates)]
    {:engine :expert-aware-nvme
     :engine-revision engine-revision
     :model (select-keys model [:model/id :model/family :model/weight-bytes
                                :model/experts :model/active-experts
                                :model/layers :model/gguf :model/target-node])
     :node node
     :required-disk-bytes (or (:required-new-bytes node)
                              (+ weight-bytes disk-reserve-bytes))
     :cache-mib (or (:model/expert-cache-mib model) default-cache-mib)
     :io-threads (or (:model/expert-io-threads model) default-io-threads)
     :gpu-layers (or (:model/gpu-layers model) default-gpu-layers)
     :lossless? true
     :page-cache-bypassed? false
     :mtp-enabled? false
     :fits? (boolean node)}))

(defn command
  "Build one reproducible bmoe-cli command as argv, never a shell string."
  [{:keys [bin model-path prompt tokens cache-mib io-threads gpu-layers csv stream?]
    :or {tokens 64 cache-mib default-cache-mib
         io-threads default-io-threads gpu-layers default-gpu-layers
         stream? true}}]
  (cond-> [bin "-m" model-path "-p" prompt "-n" (str tokens)
           "--seed" "42" "--temp" "0" "-t" "4"
           "--gpu-layers" (str gpu-layers)]
    stream? (into ["--moe-stream"
                   "--cache-mb" (str cache-mib)
                   "--cache-ceil-mb" (str cache-mib)
                   "--io-threads" (str io-threads)
                   "--dense-weights" "mmap"
                   "--prefetch" "0"])
    csv (into ["--csv" csv "--progress"])))

(defn parse-summary
  "Extract comparable fields from bmoe's stderr/stdout transcript."
  [text]
  (let [capture (fn [pattern]
                  (some-> (re-find pattern text) second parse-double))]
    {:tok-s (or (capture #"(?i)([0-9]+(?:\.[0-9]+)?)\s+tok/s")
                (capture #"(?i)tok/s[=:]\s*([0-9]+(?:\.[0-9]+)?)"))
     :cache-hit-pct (capture #"(?i)moe-cache:\s*([0-9]+(?:\.[0-9]+)?)%\s+hit")
     :flash-mib-per-token (capture #"(?i)([0-9]+(?:\.[0-9]+)?)\s*MiB/token")
     :requested-o-direct? (boolean (re-find #"o_direct=1" text))
     ;; BigMoe 0.22 documents that macOS compiles O_DIRECT away and does not
     ;; call F_NOCACHE. Do not promote its requested flag to an observed fact.
     :page-cache-bypassed? false}))

(defn qualify
  "Compare cache-off/cache-on lossless runs before accepting a speed claim."
  [{:keys [off-token-ids on-token-ids on-repeat-token-ids off-tok-s on-tok-s]}]
  (let [parity? (= (vec off-token-ids) (vec on-token-ids))
        deterministic? (= (vec on-token-ids) (vec on-repeat-token-ids))
        speedup (if (pos? (double (or off-tok-s 0)))
                  (/ (double (or on-tok-s 0)) (double off-tok-s)) 0.0)
        execution? (and parity? deterministic? (pos? (double (or on-tok-s 0))))]
    {:execution-qualified? execution?
     :speed-qualified? (and execution? (> speedup 1.0))
     :token-parity? parity?
     :deterministic? deterministic?
     :speedup speedup
     :page-cache-bypassed? false
     :mtp-enabled? false}))
