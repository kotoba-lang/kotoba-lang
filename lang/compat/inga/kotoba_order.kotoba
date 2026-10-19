(ns inga.kotoba-order
  "Concrete Inga QC verifier for Kotoba's consensus-order envelope.

  The adapter does not ask Kotoba to trust a boolean named `valid`. It binds
  the dataspace, height, parent and ordered incidence CIDs into one ordinary
  Inga block, recomputes that block's commit id, and verifies the attached QC
  against an externally supplied admitted validator set and signature edge."
  (:require [inga.attest :as attest]
            [inga.consensus :as consensus]))

(def profile "kotoba-consensus-order/v1")

(def envelope-binding-fields
  [:consensus/profile :consensus/dataspace :consensus/height
   :consensus/parent-id :consensus/commit-id :consensus/entry-cids])

(def certificate-fields #{:inga/order-block :inga/qc})

(defn- bound-block?
  [envelope block]
  (and (map? block)
       (= (:consensus/height envelope) (:inga.block/height block))
       (= (or (:consensus/parent-id envelope) "genesis")
          (:inga.block/parent-hash block))
       (= (:consensus/entry-cids envelope) (:inga.block/proposals block))
       ;; A dataspace is the consensus domain. Binding it as proposer keeps it
       ;; inside canonical-block without inventing a parallel hash format.
       (= (:consensus/dataspace envelope) (:inga.block/proposer block))
       (int? (:inga.block/round block))
       (not (neg? (:inga.block/round block)))
       (int? (:inga.block/ts block))
       (not (neg? (:inga.block/ts block)))))

(defn verifier
  "Build a live verifier accepted by kotoba.lang.consensus-order/admit-commit!.

  HASH-FN receives `inga.consensus/canonical-block`. VERIFY-SIG-FN receives
  `[witness payload signature]`. QUORUM is an inga.quorum predicate or integer,
  and ADMITTED? is the validator set/predicate. Invalid evidence returns nil;
  it never produces a partially bound success map."
  [{:keys [chain-id quorum hash-fn verify-sig-fn admitted?] :as options}]
  (when-not (and (= #{:chain-id :quorum :hash-fn :verify-sig-fn :admitted?}
                    (set (keys options)))
                 (string? chain-id) (seq chain-id)
                 (some? quorum) (fn? hash-fn) (fn? verify-sig-fn)
                 (ifn? admitted?))
    (throw (ex-info "invalid Inga order verifier capabilities"
                    {:problem :inga.kotoba-order/verifier-options})))
  (fn [envelope]
    (let [certificate (:consensus/certificate envelope)
          block (:inga/order-block certificate)
          qc (:inga/qc certificate)
          commit-id (when (map? block)
                      (hash-fn (consensus/canonical-block block)))]
      (when (and (= profile (:consensus/profile envelope))
                 (= certificate-fields (set (keys certificate)))
                 (bound-block? envelope block)
                 (= (:consensus/commit-id envelope) commit-id)
                 (= commit-id (:inga.qc/block-hash qc))
                 (= (:consensus/height envelope) (:inga.qc/height qc))
                 (nil? (attest/verify-certificate
                        qc chain-id quorum verify-sig-fn admitted?)))
        (assoc (select-keys envelope envelope-binding-fields)
               :consensus/valid? true)))))
