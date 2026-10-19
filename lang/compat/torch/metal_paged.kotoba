(ns torch.metal-paged
  "Physical paged KV storage callbacks backed by num's WebGPU/Metal kernels."
  (:require [num.array :as arr]
            [num.deno-gpu :as gpu]
            [num.tensor :as tensor]))

(defn storage [backend block-count block-size heads kv-heads head-dim]
  (let [kv-embed (* kv-heads head-dim)
        key-pool (arr/zeros backend [block-count block-size kv-embed])
        value-pool (arr/zeros backend [block-count block-size kv-embed])]
    {:key-pool key-pool :value-pool value-pool
     :write! #(gpu/paged-kv-write! key-pool value-pool %1 %2 %3 %4)
     :copy-block! #(gpu/paged-kv-copy-block! key-pool value-pool %1 %2 %3)
     :attention
     (fn [query blocks length]
       (let [table (arr/from-vec backend blocks [(count blocks)])
             output (gpu/paged-gqa-attention query key-pool value-pool
                                             table length heads kv-heads)]
         (arr/release! table) output))
     :attention-many
     (fn [query block-tables lengths]
       (let [batch (count lengths)
             max-blocks (apply max (map count block-tables))
             tables (arr/from-vec
                     backend
                     (vec (mapcat #(take max-blocks (concat % (repeat 0)))
                                  block-tables))
                     [batch max-blocks])
             lengths* (arr/from-vec backend lengths [batch])
             output (gpu/paged-gqa-attention-batch
                     (tensor/reshape query [batch (last (:shape query))])
                     key-pool value-pool tables lengths* heads kv-heads)]
         (arr/release-all! [tables lengths*])
         (tensor/reshape output [batch 1 (last (:shape output))])))}))

(defn release! [{:keys [key-pool value-pool]}]
  (arr/release-all! [key-pool value-pool]))
