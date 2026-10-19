;; murakumo.overlay.witness-ledger — the missing :quorum-fn wiring
;; ADR-2607995000 §7 (third gate bullet) calls for: cloud-murakumo's
;; economic ledger (`cloud-murakumo.ledger.witness/witness-run`) is
;; fail-closed on an INJECTED `:quorum-fn` and ships no implementation,
;; because that repo is deployed from a single checkout and cannot
;; depend on witness-quorum or this repo (its own ns docstring). This
;; file is the counterpart of witness_treasury.clj for the ECONOMIC
;; ledger: murakumo already depends on witness-quorum (Phase 2) and owns
;; the overlay QUIC transport, so the composition lives here and is
;; handed across the boundary as a plain function — the exact contract
;; ledger/witness.cljc documents:
;;
;;   (fn [{:keys [record-cid record fleet]}] -> {:kind ... :verdict ...})
;;
;; Honesty note (same as witness-write.clj / witness-treasury.clj): the
;; quorum reached through this transport is crash-fault tolerance across
;; physical machines under ONE operator (fleet.edn), not Byzantine
;; consensus across independent third parties.

(ns murakumo.overlay.witness-ledger
  (:require [murakumo.overlay.witness-write :as witness-write]))

(defn ledger-quorum-fn
  "Factory: build the `:quorum-fn` that `cloud-murakumo.ledger.witness/
  witness-run` requires, backed by a REAL pre-commit witness quorum over
  murakumo's overlay QUIC transport (witness-write/write-record-with-
  real-quorum!, reputation updated from the outcome -- stake is not
  touched by a quorum verdict, ADR-2608055000 G1).

  `opts` — threaded through to write-record-with-real-quorum!:
    :fleet-edn / :session / :rule / :reputation-db / :stake-ledger /
    :min-score / :min-observations / :timeout-ms /
    :quorum-options   same contract as witness-write.
  plus two of its own:
    :write-fn     the quorum-write entry point. Default
                  witness-write/write-record-with-real-quorum!; tests
                  inject a stub here (same isolation the overlay test
                  suite uses for quic-driver/request!).
    :on-outcome!  optional `(fn [result])` called with the FULL
                  write-record-with-real-quorum! result — the caller's
                  hook to persist :reputation-db' / :stake-ledger' /
                  :outcomes, which a bare quorum-state return cannot carry.

  The returned fn takes ledger.witness's `{:record-cid :record :fleet}`
  request and returns the quorum STATE ({:kind :witnessed|:rejected|
  :escalated|:pending ...}) — exactly what witness-run stores under
  [:witness :state]. `:fleet`, when the ledger caller passes one, is
  treated as a fleet-edn override; otherwise this factory's :fleet-edn
  is used.

  The orchestrator's :commit-fn is deliberately a no-op marker here
  (:ledger/commit-deferred-to-caller): witness-run only wants the
  verdict — appending the witnessed run to the economic feed remains
  the ledger caller's job, so a :witnessed verdict must not double-write."
  [{:keys [write-fn on-outcome!] :as opts}]
  (let [write-fn (or write-fn witness-write/write-record-with-real-quorum!)]
    (fn [{:keys [record-cid record fleet]}]
      (let [result (write-fn
                    (-> opts
                        (dissoc :write-fn :on-outcome!)
                        (assoc :propose-fn (fn [_write-opts]
                                             {:uri (str "murakumo://ledger/run/"
                                                        (Integer/toHexString (hash record-cid)))
                                              :cid record-cid})
                               :commit-fn (fn [_write-opts _receipt]
                                            :ledger/commit-deferred-to-caller)
                               :write-opts {:record record})
                        (cond-> fleet (assoc :fleet-edn fleet))))]
        (when on-outcome! (on-outcome! result))
        (:state result)))))
