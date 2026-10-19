(ns erasure.codec
  "Encode and decode whole shards.

  This is the only namespace in the library that touches shard *contents*.
  `erasure.lrc` decides, `erasure.matrix` supplies coefficients, and
  everything byte-shaped happens here so that a host provider replacing
  `erasure.gf/scale-add` has exactly one seam to prove itself against
  (ADR-2607299200 section 5).

  A shard is a vector of integers in [0,255]. That is deliberately the
  slowest possible representation and deliberately the most portable one:
  this namespace is the reference a fast implementation is compared to, not
  the thing you run over a petabyte. `kura-node` is expected to hand the
  byte-level work to a typed-array or SIMD provider and keep this as the
  oracle in its conformance suite."
  (:require [erasure.gf :as gf]
            [erasure.lrc :as lrc]
            [erasure.matrix :as matrix]))

(defn encode
  "Produce all `n` shards from `k` data shards of equal length.

  Data shards pass through unchanged — the code is systematic, which is what
  makes a Range GET able to read one shard directly instead of reconstructing
  a whole stripe (ADR-2607299200 section 7)."
  [{:keys [k l g] :as lay} data]
  (assert (= k (count data)) "wrong number of data shards")
  (let [len (count (first data))]
    (assert (every? #(= len (count %)) data) "shards must be equal length")
    (into (vec data)
          (concat
           ;; local parity: plain xor over the group, no multiplies
           (map (fn [q]
                  (gf/xor-shards (map #(nth data %) (lrc/group-members lay q))
                                 len))
                (range l))
           ;; global parity: Cauchy rows over every data shard
           (map (fn [row] (matrix/apply-row row data len))
                (matrix/cauchy-rows k g))))))

(defn- run-step
  "Apply one plan step to the working shard vector."
  [{:keys [k] :as lay} shards len {:keys [op target targets reads]}]
  (case op
    :local
    (assoc shards target (gf/xor-shards (map #(nth shards %) reads) len))

    :recompute-global
    (assoc shards target
           (matrix/apply-row (nth (matrix/cauchy-rows k (:g lay))
                                  (lrc/global-parity-index lay target))
                             (subvec shards 0 k)
                             len))

    :global
    (let [m (mapv #(lrc/generator-row lay %) reads)
          inv (matrix/invert m)]
      (when (nil? inv)
        (throw (ex-info "singular decode matrix — the plan picked dependent rows"
                        {:reads reads})))
      (let [observed (mapv #(nth shards %) reads)
            recovered (mapv (fn [row] (matrix/apply-row row observed len))
                            inv)]
        (reduce (fn [acc j] (assoc acc j (nth recovered j)))
                shards
                targets)))))

(defn decode
  "Rebuild every missing shard in `shards`, a vector of `n` entries where a
  missing shard is `nil`.

  Returns the complete shard vector, or `nil` when the erasure pattern is
  beyond the code. The read set actually used is the plan's — `decode` does
  not touch shards the plan did not ask for, so a caller can fetch lazily by
  running `erasure.lrc/recovery-plan` first and supplying only those."
  [{:keys [n] :as lay} shards]
  (assert (= n (count shards)) "wrong number of shard slots")
  (let [erased (into #{} (filter #(nil? (nth shards %))) (range n))
        plan (lrc/recovery-plan lay erased)]
    (when (:recoverable? plan)
      (let [len (count (some identity shards))
            zeroed (mapv #(or % (gf/zero-shard len)) shards)]
        (reduce (fn [acc step] (run-step lay acc len step))
                zeroed
                (:steps plan))))))

(defn decodable?
  "Whether `erased` can be recovered at all. Cheap — no shard contents."
  [lay erased]
  (:recoverable? (lrc/recovery-plan lay erased)))
