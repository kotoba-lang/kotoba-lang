;; murakumo.infer.schedule — job-parallel media scheduling (pure cljc).
;;
;; W6 product-shell + T6.4: eligible? / score / queue-inc require the shipped
;; `:infer-schedule` KIR on **every** platform. Host pure mirrors are gone —
;; cljs/nbb must preload shipped KIR before requiring this ns
;; (ADR-260731-w6-t64-infer-sched-rebal-engine-mirror-delete).
;; Host remains: set membership projection (engines/checkpoints), pick sort-by
;; (stable order on ties — differs from tournament later-index), assign atom fold.

(ns murakumo.infer.schedule
  (:require [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :infer-schedule)

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

;; Profile 5: eligibility fields are real host/guest booleans (not 0/1 i64).


(def ^:private queue-step-schema
  "Guest :schedule/queue-step — T5.2 residual record for queue-inc-if."
  [:record :schedule/queue-step
   [[:queue :i64] [:picked :i64]]])

(def ^:private eligibility-schema
  "Guest descriptor for infer_schedule_core's eligibility record (T5.3 + profile 5).
   Four flags used to be packed into one i64; then T5.3 made them :i64 fields;
   language profile 5 makes them :bool."
  [:record :schedule/eligibility
   [[:has-engine :bool] [:has-checkpoint :bool]
    [:holds-checkpoint :bool] [:can-fetch :bool]
    [:free-bytes :i64] [:min-free :i64]]])

(defn- eligibility-fields [node model]
  (let [engine (:model/engine model)
        ckpt (:model/checkpoint model)
        engines (or (:engines node) #{})
        checkpoints (or (:checkpoints node) #{})]
    {:has-engine (contains? engines engine)
     :has-checkpoint (some? ckpt)
     :holds-checkpoint (boolean (and ckpt (contains? checkpoints ckpt)))
     :can-fetch (not (false? (:node/can-fetch? node)))
     :free-bytes (or (:free-bytes node) 0)
     :min-free (:model/min-free-bytes model 0)}))

(defn eligible?
  "Can `node` run `model`? Kotoba eligible? with a single eligibility record
  (T5.3 + profile 5 + T5.2 native guest record wire: free/min on the record)."
  [node model]
  (oracle/bool->host
   (o-record 'eligible?
             {:eligibility (oracle/record eligibility-schema
                                           (eligibility-fields node model))}
             [[:eligibility :raw]])))

(defn score
  "Lower is better: queue then -free-bytes. Kotoba score-queue + score-free.
   T5.2: structural map → call-record."
  [{:keys [queue free-bytes]}]
  [(oracle/i64->host
    (o-record 'score-queue
              {:queue (or queue 0)}
              [[:queue :i64]]))
   (oracle/i64->host
    (o-record 'score-free
              {:free-bytes (or free-bytes 0)}
              [[:free-bytes :i64]]))])

(defn pick
  "Choose the node to run `model`, or nil if none eligible.
   Prefers warm (holds checkpoint) then least-loaded. Host stable sort-by."
  [nodes model]
  (let [ok (filter #(eligible? % model) nodes)
        holds? (fn [n] (contains? (or (:checkpoints n) #{}) (:model/checkpoint model)))
        warm (filter holds? ok)]
    (first (sort-by score (if (seq warm) warm ok)))))

(defn assign
  "Assign a batch of jobs to nodes, updating each picked node's queue."
  [nodes jobs]
  (let [by-name (atom (into {} (map (juxt :name identity) nodes)))]
    (vec
     (for [job jobs
           :let [n (pick (vals @by-name) (:model job))]]
       (do (when n
             (swap! by-name update-in [(:name n) :queue]
                    (fn [q]
                      (oracle/i64->host
                       (o-record 'queue-inc-if
                                 {:step (oracle/record
                                         queue-step-schema
                                         {:queue (or q 0) :picked 1})}
                                 [[:step :raw]])))))
           {:job job :node (when n (:name n))})))))
