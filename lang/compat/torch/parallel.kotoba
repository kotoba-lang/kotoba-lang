(ns torch.parallel
  "Deterministic tensor/pipeline plans and transport-independent collectives.")

(defn balanced-ranges [n parts]
  (when-not (and (nat-int? n) (pos-int? parts) (<= parts (max 1 n)))
    (throw (ex-info "invalid balanced partition" {:n n :parts parts})))
  (let [q (quot n parts) r (mod n parts)]
    (loop [i 0 start 0 out []]
      (if (= i parts) out
          (let [size (+ q (if (< i r) 1 0))]
            (recur (inc i) (+ start size) (conj out [start (+ start size)])))))))

(defn topology
  [{:keys [nodes tensor-parallel pipeline-parallel]
    :or {tensor-parallel 1 pipeline-parallel 1}}]
  (let [world-size (* tensor-parallel pipeline-parallel)
        nodes (vec nodes)]
    (when-not (= world-size (count nodes))
      (throw (ex-info "topology requires one node per rank"
                      {:world-size world-size :nodes (count nodes)})))
    {:world-size world-size :tensor-parallel tensor-parallel
     :pipeline-parallel pipeline-parallel :nodes nodes
     :ranks (mapv (fn [rank node]
                    {:rank rank :node node
                     :pipeline-rank (quot rank tensor-parallel)
                     :tensor-rank (mod rank tensor-parallel)})
                  (range world-size) nodes)}))

(defn rank-spec [topology rank]
  (or (get-in topology [:ranks rank])
      (throw (ex-info "rank outside topology" {:rank rank}))))

(defn pipeline-plan [layer-count topology]
  (let [ranges (balanced-ranges layer-count (:pipeline-parallel topology))]
    (mapv #(assoc % :layers (nth ranges (:pipeline-rank %))) (:ranks topology))))

(defn dot [a b] (reduce + 0.0 (map * a b)))
(defn linear [x weights] (mapv #(dot x %) weights))

(defn column-shards [weights parts]
  (mapv (fn [[start end]] (subvec (vec weights) start end))
        (balanced-ranges (count weights) parts)))

(defn row-shards [weights parts]
  (let [ranges (balanced-ranges (count (first weights)) parts)]
    (mapv (fn [[start end]]
            (mapv #(subvec (vec %) start end) weights)) ranges)))

(defn column-parallel [x shards] (vec (mapcat #(linear x %) shards)))

(defn row-parallel [x shards]
  (let [ranges (balanced-ranges (count x) (count shards))]
    (apply mapv +
           (map (fn [shard [start end]]
                  (linear (subvec (vec x) start end) shard))
                shards ranges))))

(defprotocol IRankTransport
  (send-rank! [transport destination envelope])
  (receive-rank! [transport source]))

(defn activation-envelope [source destination microbatch payload]
  {:protocol :kotoba.tensor/v1 :source source :destination destination
   :microbatch microbatch :payload payload})

(defn fn-transport [send-fn receive-fn]
  (reify IRankTransport
    (send-rank! [_ destination envelope] (send-fn destination envelope))
    (receive-rank! [_ source] (receive-fn source))))

(defn all-gather [rank-values] (vec (mapcat identity rank-values)))
(defn all-reduce-sum [rank-values] (apply mapv + rank-values))

(defn pipeline-events
  "Forward GPipe event order. Each microbatch visits each stage exactly once."
  [stages microbatches]
  (vec (for [clock (range (+ stages microbatches -1))
             stage (range stages)
             :let [microbatch (- clock stage)]
             :when (< -1 microbatch microbatches)]
         {:clock clock :stage stage :microbatch microbatch})))

(defn topology-summary [topology device-profiles]
  {:world-size (:world-size topology)
   :tensor-parallel (:tensor-parallel topology)
   :pipeline-parallel (:pipeline-parallel topology)
   :collectives (if (some #(= :host-staged (:collective %)) device-profiles)
                  :host-staged :device-local)})
