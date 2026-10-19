;; murakumo.infer.join — participation tiers for the fleet (pure cljc).
;;
;; W6 product-shell + T6.4: tier max-resident, needs-relay?, can?, clamp,
;; eligible-for-work? require the shipped `:infer-join` KIR on **every**
;; platform. Host pure mirrors are gone — cljs/nbb must preload shipped KIR
;; (resources/ via nbb cwd, register-kir!, or set-resource-loader!) before
;; requiring this ns (ADR-260731-w6-t64-infer-small-mirror-delete).
;; Tier maps and partition-work folds stay host.

(ns murakumo.infer.join
  (:require [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :infer-join)

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

(def ^:private can-schema
  [:record :join/can [[:tier :i64] [:kind :string]]])
(def ^:private relay-schema
  [:record :join/relay [[:tier :i64] [:inbound :bool]]])
(def ^:private clamp-schema
  [:record :join/clamp [[:mem [:option :i64]] [:tmax :i64]]])
(def ^:private work-schema
  [:record :join/work [[:can-kind :bool] [:max-res :i64] [:res :i64]]])

(defn- tier-code
  "0 browser | 1 wasm | 2 native (default)."
  [tier]
  (case tier
    :browser 0
    :wasm 1
    :native 2
    2))

(defn- max-res-for-tier [code]
  (oracle/i64->host (o-record 'max-resident-bytes {:code code} [[:code :i64]])))

(def tiers
  "Participation tiers, widest-reach first."
  {:browser
   {:tier :browser
    :install :none
    :runtime :webgpu-wasm
    :connect :webrtc
    :reach :widest
    :can [:media-postproc :small-shard :embarrassingly-parallel :prompt-eval]
    :cannot [:host-large-model :low-latency-pipeline]
    :max-resident-bytes (max-res-for-tier 0)}

   :wasm
   {:tier :wasm
    :install :embed
    :runtime :webgpu-wasm
    :connect :webtransport
    :reach :wide
    :can [:media-postproc :small-shard :embarrassingly-parallel :prompt-eval]
    :cannot [:host-large-model :low-latency-pipeline]
    :max-resident-bytes (max-res-for-tier 1)}

   :native
   {:tier :native
    :install :curl-sh
    :runtime :metal-cuda-cpu
    :connect :quic
    :reach :narrow
    :can [:host-large-model :low-latency-pipeline :media-generate :full-shard]
    :cannot []
    :max-resident-bytes (max-res-for-tier 2)}})

(defn tier-of [caps]
  (get tiers (or (:tier caps) :native)))

(defn can?
  "Can a joiner with `caps` take work of `kind`? Profile 5: guest :bool.
   T5.2: structural tier+kind → call-record."
  [caps kind]
  (oracle/bool->host
   (o-record 'can?
             {:c (oracle/record can-schema
                                {:tier (tier-code (:tier (tier-of caps)))
                                 :kind (name kind)})}
             [[:c :raw]])))

(defn needs-relay?
  "Browser/wasm ALWAYS need a relay (no inbound). Native only when un-reachable.
   Profile 5: guest :bool.
   T5.2: structural tier+reach → call-record."
  [caps]
  (oracle/bool->host
   (o-record 'needs-relay?
             {:r (oracle/record relay-schema
                                {:tier (tier-code (:tier (tier-of caps)))
                                 :inbound (true? (:inbound-reachable? caps))})}
             [[:r :raw]])))

;; ---------------------------------------------------------------------------
;; Capability provenance (ADR-2607319500 D3)
;;
;; What a node ADVERTISES and what a node HAS are different facts, and the
;; economy pays on the first. Measured 2026-07-31 against the live registry:
;; all 45 enrolled nodes were tier :browser and 44 of them advertised
;; :mem-bytes 34359738368 -- 32 GiB, byte-identical across every one. That is
;; a compiled-in constant, not forty-four independent probes agreeing to the
;; byte. Meanwhile the ten machines that actually run work
;; (scripts/fleet-ci/nodes.edn, probed) were not enrolled here at all.
;;
;; This matters economically and not just cosmetically: credits/settle splits
;; a run's pool by memory-time, so a registry where every node reports the
;; same number makes every share identical and silently defeats
;; credits.cljc's stated design ("the plan IS the cap table of the run").
;; ---------------------------------------------------------------------------

(def capability-sources
  "Declared provenance of an advertised capability.
     :measured  the joiner asserts it probed its own hardware
     :declared  everything else, INCLUDING absent -- the safe default"
  #{:measured :declared})

(defn- caps-of
  "Capability map from either an enrollment record (:node/caps), a planner
   node (:caps), or a bare capability map."
  [node]
  (or (:node/caps node) (:caps node) node))

(defn capability-provenance
  "How this node's advertised capability was obtained -- :measured or :declared.

   NEVER inferred from the value. ADR-2607259800's methodology rule (a property
   is DECLARED, never guessed) applies here: 32 GiB is not evidence of a
   fabrication and an odd-looking number is not evidence of a probe. Absent or
   unrecognised provenance reads as :declared, so a node that says nothing is
   treated as unmeasured rather than trusted by default.

   The check that does NOT depend on the node's honesty is
   `uniform-capability-cohorts`, which looks at the registry instead."
  [node]
  (let [s (:capability/source (caps-of node))]
    (if (contains? capability-sources s) s :declared)))

(defn measured-capability? [node]
  (= :measured (capability-provenance node)))

(defn uniform-capability-cohorts
  "Groups of `min-cohort` (default 2) or more nodes advertising a
   BYTE-IDENTICAL :mem-bytes. Returns data; never throws, never edits.

   A constant is not a measurement. Independently probed hardware does not
   agree to the byte -- the real fleet's own probe output shows :free-gb
   spread across 1..56 GB on ten same-model machines. So a large cohort
   sitting on one exact value is evidence the value was compiled in,
   whatever the nodes declare about themselves.

   `:declared-measured` counts how many of the cohort claim :measured. A
   NON-ZERO count is the interesting case: the declaration and the detector
   disagree, and ADR-2607300100's rule is to surface that divergence rather
   than pick a winner silently."
  ([nodes] (uniform-capability-cohorts nodes 2))
  ([nodes min-cohort]
   (->> nodes
        (keep (fn [n] (when-let [m (:mem-bytes (caps-of n))] [m n])))
        (group-by first)
        (keep (fn [[m pairs]]
                (when (>= (count pairs) min-cohort)
                  {:mem-bytes m
                   :count (count pairs)
                   :nodes (mapv (fn [[_ n]] (or (:node/name n) (:name n))) pairs)
                   :declared-measured (count (filter (fn [[_ n]] (measured-capability? n))
                                                     pairs))})))
        (sort-by (juxt (comp - :count) :mem-bytes))
        vec)))

(defn enrollment
  "The record a joiner posts to /infer/nodes.

   `:capability/source` rides inside :node/caps so it travels with the numbers
   it qualifies -- a capability separated from its provenance is how the live
   registry ended up with 44 identical 32 GiB claims and no way to tell."
  [{:keys [name did tier mem-bytes link-gbps engine gpu] :as caps}]
  (let [t (tier-of caps)
        tmax (:max-resident-bytes t)
        max-res (oracle/i64->host
                 (o-record 'clamp-resident
                           {:c (oracle/record clamp-schema
                                              {:mem mem-bytes :tmax tmax})}
                           [[:c :raw]]))]
    {:node/name name
     :node/did did
     :node/tier (:tier t)
     :node/connect (:connect t)
     :node/needs-relay? (needs-relay? caps)
     :node/caps {:engine (or engine (:runtime t))
                 :mem-bytes mem-bytes
                 :max-resident-bytes max-res
                 :link-gbps link-gbps
                 :gpu gpu
                 :capability/source (capability-provenance caps)}
     :node/can (:can t)}))

(defn eligible-for-work?
  "A node can take a job when its tier :can covers work-kind AND residency fits.
   Profile 5: can-kind arg and result are :bool.
   T5.2: structural eligibility map → call-record."
  [node {:keys [work-kind resident-bytes] :as _job}]
  (let [can-kind (boolean (some #{work-kind} (:node/can node)))
        max-res (or (get-in node [:node/caps :max-resident-bytes]) 0)
        res (or resident-bytes 0)]
    (oracle/bool->host
     (o-record 'eligible-for-work?
               {:w (oracle/record work-schema
                                  {:can-kind can-kind
                                   :max-res max-res
                                   :res res})}
               [[:w :raw]]))))

(defn partition-work
  "Route a batch of jobs across enrolled nodes by tier."
  [nodes jobs]
  (let [native (filter #(= :native (:node/tier %)) nodes)
        swarm (filter #(#{:browser :wasm} (:node/tier %)) nodes)]
    (reduce
     (fn [acc job]
       (cond
         (and (some #(eligible-for-work? % job) native)
              (#{:host-large-model :low-latency-pipeline :media-generate :full-shard}
               (:work-kind job)))
         (update acc :native conj job)

         (some #(eligible-for-work? % job) swarm)
         (update acc :swarm conj job)

         (some #(eligible-for-work? % job) native)
         (update acc :native conj job)

         :else (update acc :unschedulable conj job)))
     {:native [] :swarm [] :unschedulable []}
     jobs)))
