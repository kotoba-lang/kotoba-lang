;; murakumo.infer.plan — exo-style memory-weighted shard planning (pure cljc).
;;
;; Given a model descriptor and the fleet's live memory map, decide which nodes
;; participate and which CONTIGUOUS layer range each node serves, proportional to
;; its usable memory (exo's ring memory-weighted partitioning). Pure data → data:
;; no SSH, no engine, no platform — so the same planner runs in bb (the terminal
;; operator), on the JVM (tests), in the CF Worker (cloud-murakumo), and inside a
;; kotoba WASM component.
;;
;; The planner is engine-agnostic: it emits layer ranges + byte estimates. Engine
;; adapters (murakumo.infer.engine) turn a plan into concrete process commands
;; (llama.cpp --rpc/--tensor-split, mlx.launch ring, …).
;;
;; W6 product-shell + T6.4: GiB/defaults/usable-bytes/choose-strategy name require
;; the shipped `:infer-plan` KIR on **every** platform. Host pure mirrors are gone
;; — cljs/nbb must preload shipped KIR (resources/ via nbb cwd, register-kir!, or
;; set-resource-loader!) before requiring this ns
;; (ADR-260731-w6-t64-infer-plan-credits-mirror-delete).
;; Partition walk and plan map assembly stay cljc (float/vector host path).

(ns murakumo.infer.plan
  "Shard planning. Pure helpers require kotoba/infer_plan_core (T6.4)."
  (:require [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :infer-plan)

(defn- o
  "Call a pure export. Requires the shipped oracle on every platform (T6.4)."
  [export args]
  (oracle/require-ready! oid)
  (oracle/call oid export args))

(defn- o-record
  "T5.2: structural host map → call-record (requires shipped oracle)."
  [export host-map field-specs]
  (oracle/require-ready! oid)
  (oracle/call-record oid export host-map field-specs))

(def ^:private node-cap-schema
  "Guest :plan/node-cap — T5.2 native record for usable-bytes."
  [:record :plan/node-cap
   [[:mem :i64] [:os :i64] [:head :i64] [:wired [:option :i64]]]])

(def ^:private strategy-in-schema
  "Guest :plan/strategy-in — T5.2 native record for choose-strategy-name."
  [:record :plan/strategy-in
   [[:link-gbps :i64] [:ranks :i64] [:experts :i64] [:kv-heads :i64]]])

;; ── constants (oracle SSoT) ────────────────────────────────────────────

(def GiB
  "Binary GiB in bytes. Kotoba `gib` (requires oracle)."
  (oracle/i64->host (o 'gib [])))

(def default-os-reserve
  "Default OS reserve bytes. Kotoba `default-os-reserve` (requires oracle)."
  (oracle/i64->host (o 'default-os-reserve [])))

(def default-headroom
  "Default per-node headroom bytes. Kotoba `default-headroom` (requires oracle)."
  (oracle/i64->host (o 'default-headroom [])))

(defn usable-bytes
  "Bytes of weights a node can realistically hold resident.
   Kotoba `usable-bytes` (required). Profile 5: wired-limit is [:option :i64].
   T5.2 native guest record: one :plan/node-cap arg."
  [{:keys [mem-bytes os-reserve-bytes headroom-bytes wired-limit-bytes]}]
  (let [os (or os-reserve-bytes default-os-reserve)
        head (or headroom-bytes default-headroom)
        cap (oracle/record
             node-cap-schema
             {:mem mem-bytes :os os :head head :wired wired-limit-bytes})]
    (oracle/i64->host
     (o-record 'usable-bytes {:node cap} [[:node :raw]]))))

(defn- largest-remainder
  "Apportion `total` integer units over `quotas` (seq of non-negative reals that
   sum to ~total) — floor everything, then hand the remaining units to the largest
   fractional parts. Deterministic: ties break to the earlier index."
  [total quotas]
  (let [floors (mapv long quotas)
        short (- total (reduce + floors))
        order (->> (map-indexed (fn [i q] [i (- q (nth floors i))]) quotas)
                   (sort-by (fn [[i frac]] [(- frac) i]))
                   (map first))
        bump (set (take short order))]
    (vec (map-indexed (fn [i f] (+ f (if (bump i) 1 0))) floors))))

(defn layer-weights
  "Per-layer byte estimates for the model's decoder stack. MoE models often open
   with a few DENSE layers (`:model/dense-layers`, e.g. GLM-5.2 first_k_dense=3)
   that weigh a fraction (`:model/dense-layer-frac`, default 1/10) of an
   expert-bearing layer — the first shard can therefore take MORE layers, which
   is exactly what lets a 78-layer GLM-5.2 sit on eleven 16 GiB ranks."
  [{:model/keys [layers weight-bytes dense-layers dense-layer-frac]}]
  (let [d (or dense-layers 0)
        f (or dense-layer-frac 1/10)
        units (+ (* d (double f)) (- layers d))          ; total in MoE-layer units
        moe-bytes (/ (double weight-bytes) units)]
    (mapv #(if (< % d) (* f moe-bytes) moe-bytes) (range layers))))

(defn partition-layers
  "Memory-weighted contiguous partition of the decoder stack over `nodes` (ring
   order = given order): walk the per-layer weight vector, cutting each node a
   contiguous slice whose BYTES (not count) match its share of usable memory.
   Returns [{:node <node> :layers [lo hi) :span n :est-bytes b :fits? bool} …] —
   nodes with zero usable memory get :span 0 and are dropped from serving."
  [{:model/keys [layers] :as model} nodes]
  (let [usable (mapv usable-bytes nodes)
        total (reduce + usable)
        lw (layer-weights model)
        wsum (reduce + lw)]
    (loop [i 0, lo 0, acc 0.0, out []]
      (if (= i (count nodes))
        out
        (let [;; cumulative byte target through node i, mapped onto the layer axis
              target (if (pos? total)
                       (* wsum (/ (double (reduce + (take (inc i) usable))) total))
                       0.0)
              last? (= i (dec (count nodes)))
              hi (if last?
                   layers
                   ;; advance while adding the next layer keeps us nearer target
                   (loop [h lo, a acc]
                     (if (or (= h layers)
                             (> (+ a (nth lw h)) target)) h (recur (inc h) (+ a (nth lw h))))))
              est (long (reduce + (subvec lw lo hi)))]
          (recur (inc i) hi (+ acc (double (reduce + (subvec lw lo hi))))
                 (conj out {:node (nth nodes i)
                            :layers [lo hi]
                            :span (- hi lo)
                            :est-bytes est
                            :fits? (<= est (nth usable i))})))))))

(defn plan
  "Full shard plan: {:model :assignments :total-usable-bytes :fits?}.
   :fits? is the go/no-go gate — total usable memory ≥ model weights AND every
   node's contiguous slice fits its own budget (largest-remainder keeps slices
   proportional, so a per-node overflow means the fleet is genuinely too small,
   not badly balanced)."
  [model nodes]
  (let [asg (partition-layers model nodes)
        total (reduce + (map (comp usable-bytes :node) asg))]
    {:model (select-keys model [:model/id :model/family :model/format
                                :model/layers :model/weight-bytes])
     :assignments asg
     :total-usable-bytes total
     :fits? (and (>= total (:model/weight-bytes model))
                 (every? :fits? (filter (comp pos? :span) asg)))}))

(def ^:private strategy-why
  {:tensor "fast interconnect and kv-heads divide the ranks — all-reduce per layer is affordable"
   :expert "fast interconnect and enough experts for every rank to hold whole ones"
   :pipeline "GbE-class link: one activation handoff per boundary is all it can pay for"})

(defn choose-strategy
  "Pick the parallelism the interconnect can actually pay for.
   Strategy name from kotoba `choose-strategy-name` (required); :why from host table.
   T5.2 native guest record: one :plan/strategy-in arg."
  [{:keys [link-gbps ranks model]}]
  (let [name (o-record 'choose-strategy-name
                       {:in (oracle/record
                             strategy-in-schema
                             {:link-gbps (or link-gbps 0)
                              :ranks (or ranks 0)
                              :experts (or (:model/experts model) 0)
                              :kv-heads (or (:model/kv-heads model) 0)})}
                       [[:in :raw]])
        strat (keyword name)]
    {:strategy strat
     :why (get strategy-why strat (:pipeline strategy-why))}))

(defn report
  "Human-oriented rows for the plan table (pure; printing is the caller's job)."
  [{:keys [assignments] :as plan}]
  (for [{:keys [node layers span est-bytes fits?]} assignments]
    {:name (:name node)
     :mem-gib (/ (double (:mem-bytes node)) GiB)
     :usable-gib (/ (double (usable-bytes node)) GiB)
     :layers layers
     :span span
     :est-gib (/ (double est-bytes) GiB)
     :ok (if fits? "✓" "✗")}))
