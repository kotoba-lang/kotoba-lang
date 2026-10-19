;; murakumo.infer.topology — the interconnect measurement plane (pure cljc).
;;
;; `murakumo.infer.plan/choose-strategy` decides tensor vs expert vs pipeline
;; from `:link-gbps`. This namespace is where that number comes from, and --
;; more importantly -- where the refusal to invent it lives.
;;
;; Measured 2026-08-15: before this namespace existed, `:link-gbps` had no
;; producer anywhere in the repository. `choose-strategy` was exercised only by
;; tests passing literals (1 / 40 / 100), and the sole production shape was
;; `(or link-gbps 0)`. An unmeasured fleet and a genuinely slow fleet were the
;; same input. See kotoba/infer_topology_core.kotoba for why that is the exact
;; failure shape ADR-2608136000 is about, and why it has to be fixed before the
;; first Thunderbolt cable arrives rather than after.
;;
;; Division of labour, as everywhere else here: this file folds sequences of
;; per-boundary observations; `:infer-topology` KIR judges the totals. The
;; probe that produces the observations is murakumo.infer.topology-probe
;; (nbb) -- ssh, sockets and clocks stay out of both.

(ns murakumo.infer.topology
  "Interconnect facts → the link number choose-strategy is entitled to see."
  (:require [murakumo.infer.plan :as plan]
            [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :infer-topology)

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

(def ^:private fabric-schema
  [:record :topology/fabric
   [[:observed :i64] [:expected :i64] [:min-mbps :i64] [:unverified :bool]]])

(def ^:private link-schema
  [:record :topology/link [[:mbps :i64] [:observed :bool]]])

(def ^:private ring-schema
  [:record :topology/ring [[:ranks :i64] [:closed :bool]]])

;; ── methods, and which of them count as evidence ──────────────────────

(def observation-methods
  "How a boundary's number was obtained, and whether that counts as evidence.

  `:nominal` is what the interface says it negotiated (`ifconfig en0 media:
  1000baseT`). It is a claim about the NIC, not an observation of two nodes
  talking, and the core refuses to let it reach `choose-strategy`. This fleet
  has already paid for trusting an advertised capability once: 44 of 45
  enrolled nodes reported a byte-identical memory figure that was a
  compiled-in constant (murakumo.infer.join, 2026-07-31).

  `:tcp-stream` is a real transfer whose byte count was checked at the
  RECEIVER. The check is not ceremony: measured 2026-08-15, an `nc`-based
  probe reported 9,587-14,913 Mbps on this 1 GbE fleet because macOS `nc`
  gives up when the send buffer fills — 130 KB of a 16 MiB transfer landed,
  the sender exited 0, and the elapsed time was the time to fail.

  `:tcp-stream-unverified` is the same transfer with the count missing or
  short. It is kept, because knowing a boundary was attempted and came back
  wrong is worth more than silence, but it is not evidence.

  `:method-limited` is a verified transfer that the calibration says was
  bounded by the instrument rather than the wire. It is a true lower bound on
  the link and not a measurement of it, so it may not lift the gate."
  {:tcp-stream {:method :tcp-stream :evidence? true
                :doc "bytes moved between the pair, counted at the receiver"}
   :tcp-stream-unverified {:method :tcp-stream-unverified :evidence? false
                           :doc "transfer attempted; byte count short or missing"}
   :method-limited {:method :method-limited :evidence? false
                    :doc "verified, but the prober was the bottleneck — a floor, not a speed"}
   :nominal {:method :nominal :evidence? false
             :doc "interface media speed; a claim, not an observation"}})

(defn evidence?
  "Did this number come from real traffic, rather than from a claim?

  Kept separate from `:observed?`. A nominal reading IS an observation — we
  really did learn that the interface says 1000baseT — it is just not evidence
  about what two nodes achieve. Collapsing the two is how a fleet ends up
  planning against advertised capability."
  [{:keys [method]}]
  (boolean (get-in observation-methods [(or method :nominal) :evidence?])))

;; ── one boundary ──────────────────────────────────────────────────────

(defn link
  "Normalize one observation into a boundary fact.

  `:mbps` nil (never observed) is NOT zero (observed as dead): the first
  cannot be measured, the second was. Both are unusable, and coverage keeps
  them apart — only one of them is a reason to go and look again."
  [{:keys [from to mbps method at]}]
  {:from from
   :to to
   :mbps (or mbps 0)
   :method (or method :nominal)
   :observed? (some? mbps)
   :evidence? (and (some? mbps) (evidence? {:method method}))
   :at at})

(defn- link-record [l]
  (oracle/record link-schema
                 {:mbps (long (:mbps l 0))
                  :observed (true? (:observed? l))}))

(defn usable-link?
  "Measured, and carrying traffic."
  [l]
  (oracle/bool->host
   (o-record 'usable-link? {:l (link-record l)} [[:l :raw]])))

(defn link-class
  "`:wan` | `:gbe` | `:fast` | `:unknown` — a label for the report table.

  Deliberately NOT the tensor threshold. That number (>= 20 Gbps) lives in
  infer-plan-core/choose-strategy-name and is not restated here."
  [l]
  (keyword
   (o 'link-class-name
      [(oracle/as-i64
        (o-record 'link-class-code {:l (link-record l)} [[:l :raw]]))])))

;; ── the ring a plan implies ───────────────────────────────────────────

(defn ring-of
  "Boundaries a shard plan needs measured.

  Ranks are the assignments that actually serve (`:span` > 0) -- a node the
  memory-weighted partition cut to zero layers is not on the wire. Pipeline
  parallel over N serving ranks is an open chain: N-1 boundaries."
  [{:keys [assignments]} & {:keys [closed?] :or {closed? false}}]
  (let [ranks (count (filter (comp pos? :span) assignments))]
    {:ranks ranks
     :closed? (boolean closed?)
     :expected (oracle/i64->host
                (o-record 'expected-links
                          {:r (oracle/record ring-schema
                                             {:ranks (long ranks)
                                              :closed (boolean closed?)})}
                          [[:r :raw]]))}))

;; ── folding observations into one fabric ──────────────────────────────

(defn fabric
  "Fold per-boundary observations against the boundaries a ring needs.

  The minimum is the fabric's speed: every token crosses every boundary, so
  the slowest one sets the pace. Boundaries with no number at all are excluded
  from the minimum rather than folded in as zero — a missing observation must
  not masquerade as an observed dead link. Coverage is what reports absence.

  `:unverified?` is set when ANY usable boundary failed to earn the name, not
  only when all of them did. A fabric is worth its weakest boundary in
  provenance exactly as it is in speed, and a mostly-measured ring with one
  asserted hop is a ring nobody measured end to end."
  [ring links]
  (let [usable (filter usable-link? links)
        mins (map :mbps usable)]
    {:ring ring
     :links (vec links)
     :observed (count usable)
     :expected (:expected ring)
     :min-mbps (if (seq mins) (long (apply min mins)) 0)
     :unverified? (boolean (and (seq usable) (not-every? :evidence? usable)))}))

(defn- fabric-record [f]
  (oracle/record fabric-schema
                 {:observed (long (:observed f 0))
                  :expected (long (:expected f 0))
                  :min-mbps (long (:min-mbps f 0))
                  :unverified (true? (:unverified? f))}))

(defn evidence
  "`:measured` | `:partial` | `:none` | `:unverified` — which of the four we are in.

  This is the answer to 'is a pass distinguishable from a skip'. Every one of
  these produces the same conservative `:pipeline` today; they are not the
  same fact and the operator has to be able to tell which one they got."
  [f]
  (keyword
   (o 'evidence-name
      [(oracle/as-i64 (o-record 'evidence-code {:f (fabric-record f)} [[:f :raw]]))])))

(defn coverage-complete? [f]
  (oracle/bool->host
   (o-record 'coverage-complete? {:f (fabric-record f)} [[:f :raw]])))

(defn strategy-link-gbps
  "The link number `choose-strategy` is entitled to see: 0 unless every
  boundary carries a real transfer."
  [f]
  (oracle/i64->host
   (o-record 'strategy-link-gbps {:f (fabric-record f)} [[:f :raw]])))

;; ── the decision, with its provenance attached ────────────────────────

(defn decide
  "Choose a parallelism strategy from MEASURED interconnect facts.

  Returns `choose-strategy`'s result plus the evidence that produced the link
  number, so a caller printing 'pipeline' can also print whether that was a
  finding or an absence.

  `:link-gbps` is gated, never raw: this call cannot return `:tensor` on a
  fleet nobody measured, however fast the nominal media claims to be."
  [{:keys [fabric model]}]
  (let [ranks (get-in fabric [:ring :ranks] 0)
        gbps (strategy-link-gbps fabric)
        ev (evidence fabric)]
    (merge (plan/choose-strategy {:link-gbps gbps :ranks ranks :model model})
           {:link-gbps gbps
            :evidence ev
            :observed (:observed fabric)
            :expected (:expected fabric)
            :min-mbps (:min-mbps fabric)
            :gated? (not= ev :measured)})))

(defn report
  "Rows for the topology table (pure; printing is the caller's job)."
  [{:keys [links] :as f}]
  {:evidence (evidence f)
   :coverage [(:observed f) (:expected f)]
   :min-mbps (:min-mbps f)
   :rows (for [l links]
           {:from (:from l)
            :to (:to l)
            ;; nil, not 0, when nothing was observed. The whole namespace rests
            ;; on that distinction; printing a zero here would undo it in the
            ;; one place a human actually looks.
            :mbps (when (:observed? l) (:mbps l))
            :method (:method l)
            :class (link-class l)
            :ok (if (usable-link? l) "✓" "·")})})
