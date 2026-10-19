(ns murakumo.cd.executor
  "Capability-gated canary/progressive deployment executor."
  (:require [murakumo.cd.capability :as capability]
            [murakumo.cd.rollout :as rollout]))

(defn- authorized? [policy issued clock-fn]
  (capability/verify (assoc policy :now (clock-fn)) issued))

(defn- deploy-batch [deploy-fn nodes artifact revision]
  (mapv (fn [node]
          [node (deploy-fn {:node node :artifact-cid artifact :revision revision})])
        nodes))

(defn- all-ok? [results] (every? (comp :ok? second) results))

(defn- health-batch [health-fn nodes revision]
  (mapv (fn [node] [node (health-fn {:node node :revision revision})]) nodes))

(defn- rollback
  [rollback-fn r reason]
  (let [targets (vec (reverse (:cd.rollout/deployed r)))
        results (mapv (fn [node]
                        [node (rollback-fn {:node node
                                            :artifact-cid (:cd.rollout/previous-artifact-cid r)
                                            :revision (:cd.rollout/previous-revision r)})])
                      targets)
        ok? (all-ok? results)]
    (-> r
        (rollout/state (if ok? :rolled-back :rollback-failed))
        (rollout/record :rollback/completed
                        {:reason reason :targets targets :results results}))))

(defn execute!
  "Execute canary then configured batches. Capability is revalidated before
   every deploy batch, so expiry during a rollout stops promotion and rolls
   back anything already deployed. Host functions receive plain maps and must
   return `{:ok? boolean ...}`."
  [{:keys [issued-capability verification-policy clock-fn deploy-fn health-fn
           rollback-fn rollout-plan]}]
  (let [artifact (:cd.rollout/artifact-cid rollout-plan)
        revision (:cd.rollout/revision rollout-plan)
        batches (into [[(:cd.rollout/canary rollout-plan)]]
                      (:cd.rollout/batches rollout-plan))]
    (loop [r (rollout/state rollout-plan :deploying) remaining batches]
      (if-let [batch (first remaining)]
        (if-not (authorized? verification-policy issued-capability clock-fn)
          (let [terminal (rollback rollback-fn r :capability-expired)]
            (assoc (rollout/receipt terminal) :rollout terminal))
          (let [deploy-results (deploy-batch deploy-fn batch artifact revision)
                r (rollout/record r :deploy/completed
                                  {:targets batch :results deploy-results})]
            (if-not (all-ok? deploy-results)
              (let [terminal (rollback rollback-fn (rollout/deployed r batch)
                                       :deploy-failed)]
                (assoc (rollout/receipt terminal) :rollout terminal))
              (let [r (-> r (rollout/deployed batch) (rollout/state :verifying))
                    health-results (health-batch health-fn batch revision)
                    r (rollout/record r :health/completed
                                      {:targets batch :results health-results})]
                (if-not (all-ok? health-results)
                  (let [terminal (rollback rollback-fn r :health-failed)]
                    (assoc (rollout/receipt terminal) :rollout terminal))
                  (recur (rollout/state r :deploying) (rest remaining)))))))
        (let [terminal (-> r (rollout/state :succeeded)
                           (rollout/record :rollout/succeeded
                                           {:targets (:cd.rollout/deployed r)}))]
          (assoc (rollout/receipt terminal) :rollout terminal))))))
