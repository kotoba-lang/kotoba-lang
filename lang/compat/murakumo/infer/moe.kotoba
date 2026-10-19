;; murakumo.infer.moe — mlx-moe single-node MoE serving (pure cljc).
;;
;; W6 product-shell + T6.4: capacity-default / expert-ratio / verdict-name /
;; resident-est require the shipped `:infer-moe` KIR on **every** platform.
;; Host pure mirrors are gone — cljs/nbb must preload shipped KIR before
;; requiring this ns (ADR-260731-w6-t64-infer-small-mirror-delete).
;; Custom capacity tiers and plan ranking stay host.

(ns murakumo.infer.moe
  (:require [murakumo.infer.plan :as plan]
            [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :infer-moe)

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

(def ^:private capacity-tiers
  [[128 512] [64 432] [48 320] [32 208]])

(defn- capacity-from-tiers
  [tiers usable-bytes]
  (some (fn [[gib cap]] (when (>= usable-bytes (* gib plan/GiB)) cap))
        (sort-by (comp - first) tiers)))

(defn capacity-for-usable
  "Usable bytes → mlx-moe capacity, or nil below the smallest measured tier."
  ([usable-bytes]
   (let [c (oracle/i64->host (o-record 'capacity-default {:usable-bytes usable-bytes} [[:usable-bytes :i64]]))]
     (when (pos? c) c)))
  ([model usable-bytes]
   (if-let [custom (:model/mlx-moe-capacity-tiers model)]
     (capacity-from-tiers custom usable-bytes)
     (capacity-for-usable usable-bytes))))

(def ^:private ratio-schema
  "T5.2 native guest record for expert-ratio-milli."
  [:record :moe/ratio [[:experts :i64] [:active :i64]]])

(def ^:private verdict-schema
  [:record :moe/verdict [[:experts :i64] [:active :i64] [:shared :bool]]])

(def ^:private resident-schema
  [:record :moe/resident
   [[:weight-bytes :i64] [:experts :i64] [:capacity :i64]]])

(defn expert-ratio
  "experts / active-experts (top-k).
   T5.2 native guest record wire: single :moe/ratio argument."
  [{:model/keys [experts active-experts]}]
  (when (and experts active-experts (pos? active-experts))
    (let [milli (oracle/i64->host
                 (o-record 'expert-ratio-milli
                           {:ratio (oracle/record ratio-schema
                                                  {:experts experts
                                                   :active active-experts})}
                           [[:ratio :raw]]))]
      (when (pos? milli) (/ (double milli) 1000.0)))))

(defn verdict
  "mu-hashmi/mlx-moe 'which models benefit' heuristic as data.
   T5.2 native guest record wire: single :moe/verdict argument."
  [model]
  (let [ratio (expert-ratio model)
        shared? (boolean (:model/moe-shared-expert? model))
        name (o-record 'verdict-name
                       {:v (oracle/record verdict-schema
                                          {:experts (or (:model/experts model) 0)
                                           :active (or (:model/active-experts model) 0)
                                           :shared shared?})}
                       [[:v :raw]])
        v (keyword name)]
    (case v
      :unknown
      {:verdict :unknown :ratio nil
       :why "registry entry has no :model/experts / :model/active-experts"}
      :recommended
      {:verdict :recommended :ratio ratio
       :why "expert ratio >=10x + shared expert — quality holds at reduced coverage"}
      :workable
      {:verdict :workable :ratio ratio
       :why "shared expert but ratio <10x — needs high coverage, verify output quality"}
      {:verdict :not-recommended :ratio ratio
       :why "no shared expert — quality likely degrades below ~75% coverage (README)"})))

(defn resident-bytes-estimate
  "Approximate RAM mlx-moe holds resident at `capacity` experts/layer cached.
   T5.2 native guest record wire: single :moe/resident argument."
  [{:model/keys [weight-bytes experts]} capacity]
  (oracle/i64->host
   (o-record 'resident-est
             {:r (oracle/record resident-schema
                                {:weight-bytes (or weight-bytes 0)
                                 :experts (or experts 0)
                                 :capacity (or capacity 0)})}
             [[:r :raw]])))

(defn plan
  "Single-node mlx-moe plan for `model` over `nodes`."
  [{:model/keys [layers] :as model} nodes]
  (let [ranked (->> nodes
                    (map (fn [n] {:node (assoc n :head? true) :usable (plan/usable-bytes n)}))
                    (sort-by (comp - :usable)))
        best (first ranked)
        usable (:usable best 0)
        cap (capacity-for-usable model usable)
        span (or layers 1)
        est (resident-bytes-estimate model cap)]
    {:engine :mlx-moe
     :model (select-keys model [:model/id :model/family :model/format :model/layers
                                :model/weight-bytes :model/experts :model/active-experts])
     :assignments (if best
                    [{:node (:node best) :layers [0 span] :span span
                      :est-bytes est :fits? (boolean cap)}]
                    [])
     :capacity cap
     :verdict (verdict model)
     :total-usable-bytes usable
     :fits? (boolean cap)}))
