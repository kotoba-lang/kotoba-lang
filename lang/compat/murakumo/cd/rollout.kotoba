(ns murakumo.cd.rollout
  "Pure progressive rollout state and receipt model."
  (:require [murakumo.canonical :as canonical]
            [murakumo.identity :as identity]))

(def terminal-states #{:succeeded :rolled-back :rollback-failed})

(defn plan
  [{:keys [capability-cid artifact-cid previous-artifact-cid environment revision previous-revision
           targets batch-size]}]
  (when-not (and capability-cid artifact-cid previous-artifact-cid environment
                 revision previous-revision (seq targets))
    (throw (ex-info "murakumo-cd: incomplete rollout plan"
                    {:reason :invalid-rollout-plan})))
  {:cd.rollout/version 1 :cd.rollout/state :planned
   :cd.rollout/capability-cid capability-cid
   :cd.rollout/artifact-cid artifact-cid :cd.rollout/environment environment
   :cd.rollout/previous-artifact-cid previous-artifact-cid
   :cd.rollout/revision revision :cd.rollout/previous-revision previous-revision
   :cd.rollout/canary (first targets)
   :cd.rollout/batches (mapv vec (partition-all (or batch-size 1) (rest targets)))
   :cd.rollout/deployed [] :cd.rollout/events []})

(defn record [rollout type data]
  (update rollout :cd.rollout/events conj {:cd.event/type type :cd.event/data data}))

(defn state [rollout next-state]
  (assoc rollout :cd.rollout/state next-state))

(defn deployed [rollout nodes]
  (update rollout :cd.rollout/deployed into nodes))

(defn receipt [rollout]
  (when-not (contains? terminal-states (:cd.rollout/state rollout))
    (throw (ex-info "murakumo-cd: rollout is not terminal"
                    {:reason :rollout-not-terminal})))
  (let [document (-> rollout
                     (dissoc :cd.rollout/batches)
                     (assoc :cd.receipt/version 1))]
    {:receipt document
     :receipt-cid (identity/graph-cid (canonical/string document))
     :ref (str "refs/cd/deployments/" (:cd.rollout/capability-cid rollout))}))
