;; murakumo.overlay.witness-treasury — the final composition ADR-2607110300
;; Phase 3 has been building toward across THREE independently-developed,
;; independently-tested repos, with nothing left calling all of them
;; together until this file:
;;
;;   1. `murakumo.overlay.witness-write/write-record-with-real-quorum!` --
;;      a REAL pre-commit witness quorum over murakumo's overlay QUIC
;;      transport, drawn from real fleet.edn nodes, reputation updated
;;      from the outcome (ADR-2607110300 Phase 2/4, verified working
;;      end-to-end across this repo's own overlay-witness-* test suite).
;;      A verdict does NOT move stake -- ADR-2608055000 G1 removed that,
;;      see witness-write.clj's header.
;;   2. `kekkai.governor`'s `:treasury/release` op -- deny-by-default value
;;      governance gated on a witness-quorum verdict, ALWAYS escalating to
;;      a human for real fund release (ADR-2607110300 Phase 3, verified
;;      end-to-end through the full CoordinationActor StateGraph in
;;      kekkai's own treasury_contract_test.clj: clean release requires
;;      human signoff then commits; missing/rejected verdicts hold).
;;
;; Both halves already existed, fully tested, in their own repos -- what
;; was missing was a caller sitting where BOTH dependencies are available
;; (kekkai doesn't vendor witness-quorum "by design" per governor.cljc's
;; own docstring; cloud-murakumo can't either, per its "single-checkout,
;; no-west-workspace" constraint documented in ledger/witness.cljc and
;; verify/compute.cljc). murakumo already depends on witness-quorum
;; (Phase 2) and already talks to kekkai (murakumo.kekkai, for tailnet
;; admission) -- this is the natural, already-established meeting point.
;;
;; Honesty note (same as witness-write.clj and kekkai.governor.cljc): a
;; :witnessed quorum here is crash-fault tolerance across physical
;; machines under ONE operator (fleet.edn), not Byzantine consensus across
;; independent third parties, and kekkai's governor NEVER auto-commits a
;; :treasury/release regardless of how clean the quorum verdict is -- real
;; money always waits on a human's :approval. This file does not change
;; either of those properties; it only lets a real witness-quorum verdict
;; reach kekkai's (already-built) human-signoff pipeline instead of that
;; pipeline only ever being exercised with hand-built test verdicts.

(ns murakumo.overlay.witness-treasury
  (:require [kekkai.operation :as op]
            [langgraph.graph :as g]
            [murakumo.overlay.witness-write :as witness-write]))

(defn treasury-release-proposal
  "Pure: shape a witness-quorum `:state` (as returned by
  write-record-with-real-quorum!'s :state, or any witness-quorum
  orchestrator result's :state -- {:kind :witnessed|:rejected|:escalated|
  :pending ...}) into the exact proposal map kekkai.governor/check's
  :treasury/release case expects. `confidence` defaults to 1.0 for a
  reached quorum and 0.0 otherwise -- kekkai's :treasury/release is
  high-stakes regardless (governor.cljc: :ok? can never be true for this
  op), so confidence here only affects WHY it escalates, never whether."
  [{:keys [quorum-state amount recipient confidence]
    :or {confidence nil}}]
  {:effect :treasury-release
   :witness-verdict quorum-state
   :amount amount
   :recipient recipient
   :confidence (or confidence (if (= :witnessed (:kind quorum-state)) 1.0 0.0))})

(defn run-treasury-release!
  "Run ONE :treasury/release request through a compiled kekkai
  CoordinationActor (`kekkai.operation/build kekkai-store`). Pure
  in-process StateGraph execution, no network -- this is the half of the
  pipeline testable without a real fleet (see witness_treasury_test.clj).
  Returns langgraph.graph/run*'s result map ({:status :state ...} -- see
  kekkai's treasury_contract_test.clj for the exact shape: :status
  :interrupted pending human sign-off for any non-hard, high-stakes
  proposal, or :ok with :state {:disposition :hold} for a violation)."
  [{:keys [kekkai-store node proposal thread-id phase]
    :or {phase 3}}]
  (let [actor (op/build kekkai-store)]
    (g/run* actor
            {:request {:op :treasury/release :node node}
             :proposal proposal
             :context {:phase phase}}
            {:thread-id thread-id})))

(defn release-with-real-quorum!
  "The full composition: get a REAL witness-quorum verdict over murakumo's
  overlay QUIC transport for the record described by `propose-fn`/
  `commit-fn`/`write-opts` (same contract as
  witness-write/write-record-with-real-quorum!), then run a
  :treasury/release proposal built from that verdict through kekkai's
  CoordinationActor. NOT independently unit-tested (needs a real fleet
  dialed over real QUIC) -- treasury-release-proposal and
  run-treasury-release! above are the tested halves; this fn is their
  straight-line composition, kept deliberately thin so there is nothing
  here to get wrong beyond 'call A, feed its :state into B'.

  `opts` = witness-write/write-record-with-real-quorum!'s opts, PLUS
  `:kekkai-store` (a kekkai.store/Store), `:node` (the requesting actor's
  kekkai-registered node id), `:amount`/`:recipient` (the release
  details), `:thread-id` (kekkai StateGraph checkpoint thread id).

  Returns {:quorum <write-record-with-real-quorum!'s result>
           :kekkai <run-treasury-release!'s result>}."
  [{:keys [kekkai-store node amount recipient thread-id phase] :as opts}]
  (let [quorum-result (witness-write/write-record-with-real-quorum!
                       (select-keys opts [:fleet-edn :session :propose-fn :commit-fn
                                          :write-opts :rule :quorum-options
                                          :reputation-db :stake-ledger :min-score
                                          :min-observations :timeout-ms]))
        proposal (treasury-release-proposal
                  {:quorum-state (:state quorum-result) :amount amount :recipient recipient})]
    {:quorum quorum-result
     :kekkai (run-treasury-release! {:kekkai-store kekkai-store :node node
                                     :proposal proposal :thread-id thread-id :phase phase})}))
