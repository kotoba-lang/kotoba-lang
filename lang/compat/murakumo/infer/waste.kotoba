;; murakumo.infer.waste — sqliteai/waste single-node serving: trunk resident,
;; experts streamed from disk, the rest of RAM a bounded expert cache.
;;
;; This is the same shape as murakumo.infer.moe (one node, partial expert
;; residency) with one structural difference that changes the plan: for
;; mlx-moe the binding constraint is RAM, for waste it is DISK. A K3-class
;; container is ~1 TB and the fleet probe already collects :disk-free-bytes,
;; so the gate is here rather than in schedule.cljc's media-only check.
;;
;; W6 product-shell + T6.4: every scalar formula requires the shipped
;; `:infer-waste` KIR on **every** platform — memory floor, the engine's own
;; budget resolution, the Gate-5 hit curve. Host keeps config.json shape
;; extraction, node ranking and plan map assembly.
;;
;; Two models, both ported rather than invented (see kotoba/infer_waste_core.kotoba):
;;   pre-flight floor ← waste tools/memplan.py  (config.json only, no weights)
;;   budget resolution ← waste src/waste.c waste_open  (the page-fault cliff)

(ns murakumo.infer.waste
  (:require [murakumo.infer.plan :as plan]
            [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :infer-waste)

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

(defn- i64 [export host-map field-specs]
  (oracle/i64->host (o-record export host-map field-specs)))

(def ^:private verdict-schema
  [:record :waste/verdict [[:ram-ok :bool] [:disk-ok :bool] [:cap-ok :bool]]])

(def GiB
  "Binary GiB in bytes. Kotoba `gib` (requires oracle)."
  (oracle/i64->host (o 'gib [])))

;; ── conversion assumptions (waste tools/memplan.py defaults) ────────────

(def default-trunk-milli-bits
  "Trunk stored at 4.25 bits/weight (4- or 8-bit shared weights, mixed)."
  4250)

(def default-expert-milli-bits
  "Routed experts at 2.12 bits/weight (3-bit residual vector quantization)."
  2120)

(def default-ctx
  "waste_cfg.ctx_tokens default. MLA KV is allocated to exactly this length."
  4096)

(def default-threads 10)

(def default-disk-mb-s
  "Random-read MB/s. 12780 is waste's measured internal-SSD figure; a USB
   enclosure measured 940 on the same machine, which is the difference
   between a usable K3 and a K3 that reads for 18 seconds per token."
  12780)

;; ── HuggingFace config.json → model shape ──────────────────────────────

(defn- pick [m & ks]
  (some #(get m %) ks))

(defn- text-config
  "A multimodal config nests the language model under text_config (K3 does;
   Kimi-Linear does not). memplan.py takes the first nested map carrying
   num_hidden_layers, and so does this."
  [cfg]
  (if (contains? cfg "num_hidden_layers")
    cfg
    (or (some (fn [v] (when (and (map? v) (contains? v "num_hidden_layers")) v))
              (vals cfg))
        cfg)))

(defn config->shape
  "Parsed HuggingFace config.json (string keys) → murakumo :model/* shape keys.

   Lets a waste model be registered from upstream metadata instead of
   hand-transcribed arithmetic. Mirrors memplan.py load_shape, including its
   fallbacks: moe_intermediate_size defaults to intermediate_size, and a null
   q_lora_rank means the q down-projection is full width."
  [cfg0]
  (let [cfg (text-config cfg0)
        lac (get cfg "linear_attn_config" {})
        layers (get cfg "num_hidden_layers")
        kda (get lac "kda_layers")]
    {:model/layers layers
     :model/hidden (get cfg "hidden_size")
     :model/vocab (get cfg "vocab_size")
     :model/experts (pick cfg "n_routed_experts" "num_experts" "num_local_experts")
     :model/active-experts (pick cfg "num_experts_per_tok" "num_experts_per_token"
                                 "moe_top_k")
     :model/moe-inter (or (get cfg "moe_intermediate_size")
                          (get cfg "intermediate_size"))
     :model/dense-inter (get cfg "intermediate_size")
     :model/shared-experts (or (pick cfg "n_shared_experts" "num_shared_experts") 0)
     :model/first-dense (or (get cfg "first_k_dense_replace") 0)
     :model/tie-embeddings? (boolean (get cfg "tie_word_embeddings"))
     :model/attn-heads (get cfg "num_attention_heads")
     :model/kv-lora-rank (or (get cfg "kv_lora_rank") 0)
     :model/q-lora-rank (get cfg "q_lora_rank")
     :model/qk-rope-dim (or (get cfg "qk_rope_head_dim") 64)
     :model/qk-nope-dim (or (get cfg "qk_nope_head_dim") 128)
     :model/v-head-dim (or (pick cfg "v_head_dim" "head_dim") 128)
     :model/kda-layers (count kda)
     :model/kda-heads (or (get lac "num_heads") (get cfg "num_attention_heads"))
     :model/kda-head-dim (get lac "head_dim")
     :model/conv-kernel (or (get lac "short_conv_kernel_size") 4)
     :model/moe-shared-expert? (pos? (or (pick cfg "n_shared_experts"
                                              "num_shared_experts") 0))
     ;; full-attention layers are whatever KDA does not claim
     :model/mla-layers (- layers (count kda))}))

;; ── pre-flight memory plan (memplan.py port) ───────────────────────────

(defn memplan
  "Model shape → the RAM breakdown waste will need, from config alone.

   `waste plan` is exact but reads the CONVERTED container's manifest.json,
   which does not exist until ~5 hours of conversion have run. murakumo has
   to answer 'which node, and does it fit' before that, so this is the
   estimate the plan is made from — and `waste plan` is what confirms it."
  ([model] (memplan model {}))
  ([{:model/keys [layers hidden vocab experts active-experts moe-inter
                  dense-inter shared-experts first-dense tie-embeddings?
                  attn-heads kv-lora-rank q-lora-rank qk-rope-dim qk-nope-dim
                  v-head-dim kda-layers kda-heads kda-head-dim conv-kernel
                  mla-layers]
     :as model}
    {:keys [ctx threads trunk-milli-bits expert-milli-bits]
     :or {ctx default-ctx threads default-threads}}]
   (let [dense (or first-dense 0)
         moe-layers (- layers dense)
         qlora (or q-lora-rank hidden)          ; null q_lora_rank ⇒ full width
         ;; memplan.py's defaults are assumptions about what conversion WILL
         ;; produce, and on Kimi-Linear the 2.12 default is 30% low against a
         ;; converted container's measured 3.01 bits/weight. A registry entry
         ;; that has measured its own family should say so.
         trunk-milli-bits (or trunk-milli-bits (:model/trunk-milli-bits model)
                              default-trunk-milli-bits)
         expert-milli-bits (or expert-milli-bits (:model/expert-milli-bits model)
                               default-expert-milli-bits)
         p-expert (i64 'expert-params {:hidden hidden :moe-inter moe-inter}
                       [[:hidden :i64] [:moe-inter :i64]])
         expert-rec (i64 'expert-rec-bytes
                         {:hidden hidden :moe-inter moe-inter
                          :bits expert-milli-bits}
                         [[:hidden :i64] [:moe-inter :i64] [:bits :i64]])
         emb-head (i64 'emb-head-bytes
                       {:vocab vocab :hidden hidden :bits trunk-milli-bits
                        :tie (boolean tie-embeddings?)}
                       [[:vocab :i64] [:hidden :i64] [:bits :i64] [:tie :bool]])
         p-attn (+ (i64 'mla-q-params
                        {:hidden hidden :qlora qlora :nheads attn-heads
                         :qk (+ qk-nope-dim qk-rope-dim)}
                        [[:hidden :i64] [:qlora :i64] [:nheads :i64] [:qk :i64]])
                   (i64 'mla-kv-params
                        {:hidden hidden :kv-lora kv-lora-rank
                         :qk-rope qk-rope-dim :nheads attn-heads
                         :nope-v (+ qk-nope-dim v-head-dim)}
                        [[:hidden :i64] [:kv-lora :i64] [:qk-rope :i64]
                         [:nheads :i64] [:nope-v :i64]])
                   (i64 'mla-o-params
                        {:nheads attn-heads :v-head v-head-dim :hidden hidden}
                        [[:nheads :i64] [:v-head :i64] [:hidden :i64]]))
         attn (i64 'attn-bytes
                   {:layers layers :p-attn p-attn :bits trunk-milli-bits}
                   [[:layers :i64] [:p-attn :i64] [:bits :i64]])
         router-norms (i64 'router-norms-bytes
                           {:moe-layers moe-layers :experts experts
                            :layers layers :hidden hidden}
                           [[:moe-layers :i64] [:experts :i64] [:layers :i64]
                            [:hidden :i64]])
         shared (i64 'shared-bytes
                     {:moe-layers moe-layers :n-shared (or shared-experts 0)
                      :p-expert p-expert :bits trunk-milli-bits}
                     [[:moe-layers :i64] [:n-shared :i64] [:p-expert :i64]
                      [:bits :i64]])
         dense-ffn (i64 'dense-ffn-bytes
                        {:dense dense :hidden hidden :dense-inter dense-inter
                         :bits trunk-milli-bits}
                        [[:dense :i64] [:hidden :i64] [:dense-inter :i64]
                         [:bits :i64]])
         trunk (i64 'trunk-bytes
                    {:emb-head emb-head :attn attn :router-norms router-norms
                     :shared shared :dense-ffn dense-ffn}
                    [[:emb-head :i64] [:attn :i64] [:router-norms :i64]
                     [:shared :i64] [:dense-ffn :i64]])
         kda (i64 'kda-state-bytes
                  {:kda-layers (or kda-layers 0) :heads (or kda-heads 0)
                   :dstate (or kda-head-dim 0) :conv-k (or conv-kernel 4)}
                  [[:kda-layers :i64] [:heads :i64] [:dstate :i64]
                   [:conv-k :i64]])
         mla-kv (i64 'mla-kv-bytes
                     {:mla-layers (or mla-layers (- layers (or kda-layers 0)))
                      :ctx ctx :kv-lora kv-lora-rank :qk-rope qk-rope-dim}
                     [[:mla-layers :i64] [:ctx :i64] [:kv-lora :i64]
                      [:qk-rope :i64]])
         state (i64 'state-bytes {:kda kda :mla-kv mla-kv}
                    [[:kda :i64] [:mla-kv :i64]])
         scratch (i64 'scratch-bytes
                      {:hidden hidden :moe-inter moe-inter :vocab vocab
                       :threads threads}
                      [[:hidden :i64] [:moe-inter :i64] [:vocab :i64]
                       [:threads :i64]])
         min-cache (or (:model/min-cache-bytes model)
                       (i64 'min-cache-bytes
                            {:top-k active-experts :expert-rec expert-rec}
                            [[:top-k :i64] [:expert-rec :i64]]))
         floor (i64 'floor-bytes
                    {:trunk trunk :state state :scratch scratch
                     :min-cache min-cache}
                    [[:trunk :i64] [:state :i64] [:scratch :i64]
                     [:min-cache :i64]])
         routed-disk (i64 'routed-disk-bytes
                          {:moe-layers moe-layers :experts experts
                           :p-expert p-expert :bits expert-milli-bits}
                          [[:moe-layers :i64] [:experts :i64] [:p-expert :i64]
                           [:bits :i64]])
         working-set (i64 'working-set-bytes
                          {:moe-layers moe-layers :top-k active-experts
                           :p-expert p-expert :bits expert-milli-bits}
                          [[:moe-layers :i64] [:top-k :i64] [:p-expert :i64]
                           [:bits :i64]])]
     ;; Measured values win over the analytic ones. This matters more than it
     ;; looks: memplan.py's own docstring notes that its accurate path reads
     ;; exact tensor sizes from the safetensors shard headers, and the
     ;; analytic estimate here is the rough one.
     ;;
     ;; Measured against a Kimi-Linear-48B container converted on this fleet
     ;; (2026-08-03), the analytic path is low ACROSS THE BOARD, and by more
     ;; than a rounding:
     ;;
     ;;   RAM floor    1.18 GiB analytic  vs  1.28 GiB measured   -8%
     ;;   expert set  11.63 GiB analytic  vs 16.53 GiB measured  -30%
     ;;   working set  0.36 GiB analytic  vs  0.54 GiB measured  -32%
     ;;
     ;; Two causes, both checkable. (1) memplan.py assumes 2.12 bits/weight
     ;; for experts; the container convert.py actually produced stores
     ;; 682,622,976 bytes per layer for 256 x 3*2304*1024 params = 3.01
     ;; bits/weight. (2) waste leaves embed_tokens on disk (src/waste.c skips
     ;; it when summing the resident trunk) while memplan.py counts it, worth
     ;; 383 MB here — which is why the floor is only 8% off while the expert
     ;; set is 30% off: the two errors partly cancel.
     ;;
     ;; On K3 the same path says 17.5 GiB against a real 29.06 GB floor and
     ;; 1344 GB of experts against a real 982 GB container — there it is low
     ;; on RAM and HIGH on disk, so the direction does not generalize either.
     ;;
     ;; Analytic is for "which node should I even consider". `waste plan
     ;; --json` on the converted container is the truth, and its numbers
     ;; belong in the registry as the overrides read here.
     {:trunk-bytes trunk
      :state-bytes state
      :scratch-bytes scratch
      :min-cache-bytes min-cache
      :floor-bytes (or (:model/ram-floor-bytes model) floor)
      :floor-source (if (:model/ram-floor-bytes model) :measured :estimated)
      :expert-rec-bytes expert-rec
      :routed-disk-bytes (or (:model/expert-set-bytes model) routed-disk)
      ;; one token's experts — the engine's allocation quantum AND the cold
      ;; per-token read, which is why the same number names both
      :working-set-bytes (or (:model/working-set-bytes model) working-set)
      :estimated-floor-bytes floor
      :moe-layers moe-layers
      :ctx ctx})))

;; ── the engine's own budget resolution (waste_open port) ───────────────

(defn budget
  "What ceiling waste will actually run under on a node with `usable` bytes.

   The engine never spends the remainder up to the cap: it starts at
   floor + 3 working sets and steps DOWN a whole working set at a time. The
   last fraction buys a little hit rate and risks the OS paging the cache
   out, which costs far more than it gains — measured on K3 at 0.07 tok/s
   against 0.63."
  [{:keys [floor-bytes working-set-bytes min-cache-bytes routed-disk-bytes]}
   usable]
  (let [cap (i64 'os-cap-bytes {:usable usable} [[:usable :i64]])
        recommended (i64 'recommended-bytes
                         {:floor floor-bytes :ws working-set-bytes}
                         [[:floor :i64] [:ws :i64]])
        resolved (i64 'resolve-budget
                      {:floor floor-bytes :ws working-set-bytes :cap cap}
                      [[:floor :i64] [:ws :i64] [:cap :i64]])
        cache (i64 'cache-bytes
                   {:budget resolved :floor floor-bytes
                    :min-cache min-cache-bytes}
                   [[:budget :i64] [:floor :i64] [:min-cache :i64]])
        saturating (i64 'saturating-budget
                        {:floor floor-bytes :routed routed-disk-bytes
                         :min-cache min-cache-bytes :cap cap}
                        [[:floor :i64] [:routed :i64] [:min-cache :i64]
                         [:cap :i64]])]
    {:os-cap-bytes cap
     :recommended-bytes recommended
     :budget-bytes resolved
     :cache-bytes cache
     ;; The budget at which disk leaves the decode loop entirely. The engine
     ;; cannot reach this itself — its recommendation is floor + 3 working
     ;; sets, which on a small model is far under what the machine holds. 0
     ;; means the whole expert set does not fit under cap.
     :saturating-budget-bytes saturating
     :over-cap? (oracle/bool->host
                 (o-record 'budget-over-cap? {:budget resolved :cap cap}
                           [[:budget :i64] [:cap :i64]]))}))

(defn throughput
  "Cache size → hit rate → bytes read per token → disk-bound tok/s ceiling.

   Counts disk I/O only, so it is a ceiling and not a prediction: compute is
   not in it. The hit curve is waste's Gate 5, measured by the C engine's own
   cache on Kimi-Linear batch-1 decode; K3's 896-expert routing may differ."
  ([memplan cache-bytes] (throughput memplan cache-bytes default-disk-mb-s))
  ([{:keys [routed-disk-bytes working-set-bytes]} cache-bytes disk-mb-s]
   (let [frac (if (pos? routed-disk-bytes)
                (min 1000 (quot (* 1000 cache-bytes) routed-disk-bytes))
                0)
         hit (i64 'hit-rate-milli {:frac frac} [[:frac :i64]])
         io (i64 'io-per-token-bytes
                 {:per-token working-set-bytes :hit hit}
                 [[:per-token :i64] [:hit :i64]])
         tps (i64 'tok-per-s-milli {:io io :disk disk-mb-s}
                  [[:io :i64] [:disk :i64]])]
     {:cache-frac-milli frac
      :hit-rate-milli hit
      :io-per-token-bytes io
      ;; nil, not 0: zero I/O means disk is out of the loop, and reporting
      ;; that as "0 tok/s" inverts the meaning
      :disk-bound-tok-s (when (pos? io) (/ (double tps) 1000.0))})))

;; ── verdict ────────────────────────────────────────────────────────────

(def ^:private verdict-why
  {"below-ram-floor"
   "node cannot open the model at all — floor exceeds usable RAM"
   "no-container-space"
   "not enough free disk for the converted container (waste streams experts from it)"
   "budget-over-os-cap"
   "resolved budget leaves under 12% to the OS — the cache gets paged out and throughput collapses"
   "fits" "trunk resident, experts streamed from disk"})

(defn verdict
  [{:keys [ram-ok? disk-ok? cap-ok?]}]
  (let [name (o-record 'verdict-name
                       {:v (oracle/record verdict-schema
                                          {:ram-ok (boolean ram-ok?)
                                           :disk-ok (boolean disk-ok?)
                                           :cap-ok (boolean cap-ok?)})}
                       [[:v :raw]])]
    {:verdict (keyword name) :why (get verdict-why name)}))

;; ── plan ───────────────────────────────────────────────────────────────

(defn- container-bytes
  "Converted container size. Falls back to routed experts + trunk when the
   registry has not measured one — an estimate the caller can see is an
   estimate, rather than a silent zero that passes every disk gate."
  [model mp]
  (or (:model/container-bytes model)
      (+ (:routed-disk-bytes mp) (:trunk-bytes mp))))

(defn plan
  "Single-node waste plan for `model` over `nodes`.

   Same 2-arg shape as murakumo.infer.moe/plan, so infer.clj's probe-and-plan
   drives either. Ranking differs: mlx-moe picks the node with the most RAM,
   waste picks among nodes that can hold the CONTAINER and only then by RAM.
   A 512 GB node with 40 GB free disk cannot serve K3 at any speed."
  ([model nodes] (plan model nodes {}))
  ([{:model/keys [id family layers] :as model} nodes opts]
   (let [mp (memplan model opts)
         want-disk (container-bytes model mp)
         scored (->> nodes
                     (map (fn [n]
                            (let [usable (plan/usable-bytes (assoc n :head? true))
                                  free (:disk-free-bytes n 0)
                                  b (budget mp usable)]
                              {:node (assoc n :head? true)
                               :usable usable
                               :disk-free-bytes free
                               :ram-ok? (>= usable (:floor-bytes mp))
                               :disk-ok? (>= free want-disk)
                               :budget b})))
                     ;; disk first: it is the constraint that no amount of RAM
                     ;; substitutes for
                     (sort-by (juxt (comp not :disk-ok?) (comp not :ram-ok?)
                                    (comp - :usable))))
         best (first scored)
         b (:budget best)
         cap-ok? (not (:over-cap? b true))
         v (verdict {:ram-ok? (:ram-ok? best) :disk-ok? (:disk-ok? best)
                     :cap-ok? cap-ok?})
         fits? (and (:ram-ok? best) (:disk-ok? best) cap-ok?)
         tp (when best (throughput mp (:cache-bytes b)
                                   (or (:disk-mb-s opts) default-disk-mb-s)))]
     {:engine :waste
      :model (select-keys model [:model/id :model/family :model/layers
                                 :model/experts :model/active-experts
                                 :model/container-bytes])
      :memplan mp
      :assignments (if best
                     [{:node (:node best) :layers [0 (or layers 1)]
                       :span (or layers 1)
                       :est-bytes (:budget-bytes b)
                       :disk-free-bytes (:disk-free-bytes best)
                       :fits? fits?}]
                     [])
      :container-bytes want-disk
      :budget b
      :throughput tp
      :total-usable-bytes (:usable best 0)
      :verdict v
      :fits? (boolean fits?)})))
