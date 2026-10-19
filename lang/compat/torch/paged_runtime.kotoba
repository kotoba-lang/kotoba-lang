(ns torch.paged-runtime
  "Connect `torch.kv-cache` ownership decisions to a physical storage runtime."
  (:require [torch.kv-cache :as kv]))

(def required-storage-ops
  #{:write! :copy-block! :attention})

(defn runtime
  "Create a paged runtime. `storage` supplies effectful callbacks:

  - `:write! key value block offset`
  - `:copy-block! source destination used-tokens`
  - `:attention query block-table length`

  Metal implementations map these directly to num.deno-gpu's paged kernels."
  [pool* storage]
  (let [missing (remove #(fn? (get storage %)) required-storage-ops)]
    (when (seq missing)
      (throw (ex-info "paged storage runtime is missing callbacks"
                      {:missing (vec (sort missing))})))
    {:pool pool* :storage storage}))

(defn allocate-sequence [runtime* sequence-id]
  (update runtime* :pool kv/allocate-sequence sequence-id))

(defn fork-sequence [runtime* parent-id child-id]
  (update runtime* :pool kv/fork-sequence parent-id child-id))

(defn append-kv!
  "Reserve and write one projected key/value token. A required prefix COW copy
  is submitted before the new token write; one GPU queue therefore preserves
  the dependency without host synchronization."
  [runtime* sequence-id key value]
  (let [placement (kv/append-token (:pool runtime*) sequence-id)
        storage (:storage runtime*)]
    (when-let [{:keys [from to tokens]} (:copy placement)]
      ((:copy-block! storage) from to tokens))
    ((:write! storage) key value (:block placement) (:offset placement))
    {:runtime (assoc runtime* :pool (:pool placement))
     :placement (dissoc placement :pool)}))

(defn attention
  "Run one-token attention through the current logical block table."
  [runtime* sequence-id query]
  (let [{:keys [blocks length]} (kv/block-table (:pool runtime*) sequence-id)]
    (when (zero? length)
      (throw (ex-info "cannot attend an empty paged sequence"
                      {:sequence-id sequence-id})))
    ((get-in runtime* [:storage :attention]) query blocks length)))

(defn append-kv-many!
  "Append one K/V token for each sequence through one shared physical pool.
  Writes are submitted in `sequence-ids` order and return one updated runtime."
  [runtime* sequence-ids keys values]
  (when-not (= (count sequence-ids) (count keys) (count values))
    (throw (ex-info "paged multi-append inputs must have equal counts"
                    {:sequences (count sequence-ids) :keys (count keys)
                     :values (count values)})))
  (reduce (fn [{:keys [runtime placements]} [sequence-id key value]]
            (let [result (append-kv! runtime sequence-id key value)]
              {:runtime (:runtime result)
               :placements (conj placements (:placement result))}))
          {:runtime runtime* :placements []}
          (map vector sequence-ids keys values)))

(defn attention-many
  "Run one fused attention callback for multiple logical sequence tables."
  [runtime* sequence-ids query]
  (let [callback (get-in runtime* [:storage :attention-many])]
    (when-not (fn? callback)
      (throw (ex-info "paged storage runtime lacks :attention-many callback" {})))
    (let [entries (mapv #(kv/block-table (:pool runtime*) %) sequence-ids)]
      (when (some #(zero? (:length %)) entries)
        (throw (ex-info "cannot attend an empty paged sequence" {})))
      (callback query (mapv :blocks entries) (mapv :length entries)))))

(defn release-sequence
  "Release logical ownership. Physical storage remains one bounded pool and is
  reused; the storage runtime therefore needs no per-sequence destruction."
  [runtime* sequence-id]
  (update runtime* :pool kv/release-sequence sequence-id))
