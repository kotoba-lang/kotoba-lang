(ns num.device-profile
  "Portable accelerator classification and measured execution hints.

  These maps contain no driver calls. Hosts supply public PCI/architecture/name
  data and may override any hint after a local benchmark."
  (:require [clojure.string :as str]))

(def profiles
  {:intel-arc-pro-b70
   {:num/backend :sycl
    :num/architecture :battlemage
    :num/memory-kind :discrete
    :num/preferred-dtype :q4-k-m
    :num/flash-attention? true
    :num/batch-size 4096
    :num/ubatch-size 2048
    :num/max-parallel 1
    :num/speculative {:type :mtp :draft-token-count 3}}

   :amd-strix-halo-8060s
   {:num/backend :vulkan
    :num/architecture :gfx1151
    :num/memory-kind :unified
    :num/preferred-dtype :q4-k-m
    :num/flash-attention? true
    :num/max-parallel 2
    :num/speculative {:type :ngram-cache}}

   :nvidia-jetson-agx-xavier
   {:num/backend :cuda
    :num/architecture :tegra194
    :num/memory-kind :unified
    :num/preferred-dtype :q4-k-m
    :num/flash-attention? true
    :num/max-parallel 1
    :num/speculative {:type :none}
    :num/host-prerequisites #{:max-performance-power-mode :locked-clocks}}})

(defn- searchable [device]
  (str/lower-case
   (str (if (map? device)
          (select-keys device [:name :vendor :vendor-id :device-id
                               :architecture :backend :machine])
          device))))

(defn classify
  "Return a stable hardware kind from public adapter information."
  [device]
  (let [s (searchable device)]
    (cond
      (or (str/includes? s "e223")
          (and (str/includes? s "intel")
               (or (str/includes? s "arc pro b70")
                   (str/includes? s "battlemage"))))
      :intel-arc-pro-b70

      (or (str/includes? s "gfx1151")
          (str/includes? s "radeon 8060s")
          (str/includes? s "strix halo"))
      :amd-strix-halo-8060s

      (or (str/includes? s "tegra194")
          (str/includes? s "jetson agx xavier"))
      :nvidia-jetson-agx-xavier

      :else :unknown)))

(defn profile
  "Return the classified profile. Unknown devices fail closed to CPU hints."
  [device]
  (let [kind (classify device)]
    (assoc (get profiles kind
                {:num/backend :cpu
                 :num/max-parallel 1
                 :num/speculative {:type :none}})
           :num/device-kind kind)))

(defn execution-hints
  "Select intent-specific hints without hiding the physical device profile."
  ([device] (execution-hints device :latency))
  ([device intent]
   (let [p (profile device)]
     (case intent
       :latency (assoc p :num/execution-intent :latency
                         :num/parallel 1)
       :throughput (assoc p :num/execution-intent :throughput
                            :num/parallel (:num/max-parallel p 1))
       :fallback (assoc p :num/execution-intent :fallback
                          :num/parallel 1)
       (throw (ex-info "unsupported execution intent" {:intent intent}))))))
