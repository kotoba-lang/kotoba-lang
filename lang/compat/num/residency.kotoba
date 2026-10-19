(ns num.residency
  "Exact byte-accounting policy for admitting an edge model into unified memory.")

(defn admission
  "Return a measured residency decision. All inputs are bytes; none are inferred.

  `:runtime-bytes` covers weights/projector/runtime allocations,
  `:speculative-bytes` covers draft-head and verification scratch, and
  `:context-bytes` covers the configured KV/cache window. The OS reserve and
  explicit headroom stay unavailable to the model."
  [{:keys [memory-bytes os-reserve-bytes headroom-bytes
           runtime-bytes speculative-bytes context-bytes]
    :or {speculative-bytes 0}}]
  (let [values [memory-bytes os-reserve-bytes headroom-bytes
                runtime-bytes speculative-bytes context-bytes]]
    (when-not (every? #(and (integer? %) (not (neg? %))) values)
      (throw (ex-info "residency admission requires non-negative integer bytes"
                      {:values values})))
    (let [budget (max 0 (- memory-bytes os-reserve-bytes headroom-bytes))
          required (+ runtime-bytes speculative-bytes context-bytes)]
      {:admitted? (<= required budget)
       :memory-bytes memory-bytes
       :budget-bytes budget
       :runtime-bytes runtime-bytes
       :speculative-bytes speculative-bytes
       :context-bytes context-bytes
       :required-bytes required
       :free-after-bytes (max 0 (- budget required))})))

(defn split-backend-admission
  "Admit a unified-memory CPU/accelerator placement using measured bytes.

  `expert-page-reserve-bytes` is the file-page working set that must remain
  available while accelerator weights are resident. This prevents a placement
  from looking valid merely because mapped expert files are not counted as
  anonymous allocations."
  [{:keys [memory-bytes os-reserve-bytes headroom-bytes cpu-resident-bytes
           accelerator-model-bytes accelerator-compute-bytes context-bytes
           expert-page-reserve-bytes]}]
  (let [values [memory-bytes os-reserve-bytes headroom-bytes cpu-resident-bytes
                accelerator-model-bytes accelerator-compute-bytes context-bytes
                expert-page-reserve-bytes]]
    (when-not (every? #(and (integer? %) (not (neg? %))) values)
      (throw (ex-info "split backend admission requires measured non-negative integer bytes"
                      {:values values})))
    (let [budget (max 0 (- memory-bytes os-reserve-bytes headroom-bytes))
          required (+ cpu-resident-bytes accelerator-model-bytes
                      accelerator-compute-bytes context-bytes
                      expert-page-reserve-bytes)]
      {:admitted? (<= required budget)
       :memory-bytes memory-bytes
       :budget-bytes budget
       :required-bytes required
       :cpu-resident-bytes cpu-resident-bytes
       :accelerator-model-bytes accelerator-model-bytes
       :accelerator-compute-bytes accelerator-compute-bytes
       :context-bytes context-bytes
       :expert-page-reserve-bytes expert-page-reserve-bytes
       :free-after-bytes (max 0 (- budget required))})))
