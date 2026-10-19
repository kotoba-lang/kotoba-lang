(ns murakumo.ci.runner
  "Host-injected CI job runner and content-addressable receipt builder."
  (:require [murakumo.canonical :as canonical]
            [murakumo.ci.sandbox :as sandbox]
            [murakumo.identity :as identity]))

(defn- step-context [context job-id index]
  (if-let [base (:output-dir context)]
    (assoc context :output-dir
           (str base "/step-" (identity/graph-cid (str job-id ":" index))))
    context))

(defn- run-step [exec-fn context job-id index step]
  (let [plan (sandbox/plan step (step-context context job-id index))
        result (exec-fn plan)
        exit (long (or (:exit result) 1))]
    {:ci.step/index index
     :ci.step/job job-id
     :ci.step/argv (:ci/argv step)
     :ci.step/image (:ci/image step)
     :ci.step/image-digest (:ci/image-digest step)
     :ci.step/exit exit
     :ci.step/status (cond (:timed-out? result) :timed-out
                           (zero? exit) :passed
                           :else :failed)
     :ci.step/stdout-digest (:stdout-digest result)
     :ci.step/stderr-digest (:stderr-digest result)
     :ci.step/artifacts (vec (:verified-artifacts result []))
     :ci.step/duration-ms (:duration-ms result)
     :ci.step/timed-out? (boolean (:timed-out? result))}))

(defn run-job [exec-fn context job]
  (loop [steps (:ci/steps job) index 0 results []]
    (if-let [step (first steps)]
      (let [result (run-step exec-fn context (:ci/id job) index step)
            results' (conj results result)]
        (if (#{:failed :timed-out} (:ci.step/status result))
          {:ci.job/id (:ci/id job) :ci.job/status (:ci.step/status result)
           :ci.job/steps results'}
          (recur (rest steps) (inc index) results')))
      {:ci.job/id (:ci/id job) :ci.job/status :passed :ci.job/steps results})))

(defn run
  "Execute planned waves. Jobs within a wave are independent; this reference
   host executes them deterministically in id order. A fleet host may execute
   the same wave concurrently while preserving the result shape."
  [exec-fn context run-request pipeline waves runner-id]
  (loop [remaining waves results []]
    (if-let [wave (first remaining)]
      (let [jobs (mapv #(run-job exec-fn context (get-in pipeline [:ci/jobs %])) wave)
            results' (into results jobs)]
        (if (some #(#{:failed :timed-out} (:ci.job/status %)) jobs)
          (recur nil results')
          (recur (rest remaining) results')))
      (let [status (cond
                     (some #(= :timed-out (:ci.job/status %)) results) :timed-out
                     (every? #(= :passed (:ci.job/status %)) results) :passed
                     :else :failed)
            receipt {:receipt/version 1
                     :receipt/run-id (:ci.run/id run-request)
                     :receipt/source (:ci.run/source run-request)
                     :receipt/pipeline-digest (:ci.run/pipeline-digest run-request)
                     :receipt/runner-id runner-id
                     :receipt/status status
                     :receipt/jobs results}
            cid (identity/graph-cid (canonical/string receipt))]
        {:result status :receipt-cid cid :receipt receipt}))))
