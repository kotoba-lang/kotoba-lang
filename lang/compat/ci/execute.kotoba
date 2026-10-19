(ns ci.execute
  "Pure job-DAG planner for a CI workflow model. Takes a model and returns a
  vector of *waves* — each wave is a sorted vector of job ids that may run in
  parallel because every job they depend on appeared in an earlier wave.

  When the job DAG contains a cycle, `plan` returns the `{:ci/cycle [...]}` sentinel
  from `ci.model/topo-order` rather than a vector of waves. No exceptions are
  thrown. No ports or I/O — pure data in, pure data out."
  (:require [ci.model :as m]))

(defn plan
  "Return a vector of waves. Each wave is an id-sorted vector of job ids whose
  :ci/needs are all satisfied by jobs in earlier waves.

  When the workflow DAG has a cycle, returns the {:ci/cycle [...]} sentinel map
  that `ci.model/topo-order` produces — the caller may inspect :ci/cycle to
  identify the involved job ids."
  [model]
  (let [order (m/topo-order model)]
    (if (map? order)
      order  ; propagate the cycle sentinel
      (loop [remaining (set order)
             completed #{}
             waves     []]
        (if (empty? remaining)
          waves
          (let [wave (sort (filter (fn [id]
                                     (every? completed (m/needs model id)))
                                   remaining))]
            (recur (reduce disj remaining wave)
                   (into completed wave)
                   (conj waves (vec wave)))))))))
