(ns ci.model
  "GitHub Actions workflows as EDN: a plain-data representation of a CI workflow
  and the job-DAG queries a planner or validator needs. No I/O, no third-party
  deps — portable .cljc (JVM, ClojureScript, SCI).

  A workflow is a map keyed by namespaced `:ci/*` keys. Jobs are kept in an
  id-keyed map for O(1) lookup; topology comes from each job's `:ci/needs`
  vector, never document order:

    {:ci/name \"CI\"
     :ci/on   {\"push\" {\"branches\" [\"main\"]}}
     :ci/jobs {\"build\" {:ci/id       \"build\"
                         :ci/runs-on  \"ubuntu-latest\"
                         :ci/needs    []
                         :ci/steps    [{:ci/uses \"actions/checkout@v4\"}
                                       {:ci/run  \"make\"}]}
               \"test\"  {:ci/id       \"test\"
                         :ci/runs-on  \"ubuntu-latest\"
                         :ci/needs    [\"build\"]
                         :ci/steps    [{:ci/run \"make test\"}]}}}")

;; --- builder (threadable) ---

(defn workflow
  "A fresh, empty workflow. opts: {:on}."
  ([name] (workflow name nil))
  ([name opts]
   (cond-> {:ci/name name
            :ci/on   {}
            :ci/jobs {}}
     (:on opts) (assoc :ci/on (:on opts)))))

(defn add-job
  "Attach `job-map` (a map with at minimum :ci/id) to `wf`."
  [wf job-map]
  (assoc-in wf [:ci/jobs (:ci/id job-map)] job-map))

(defn make-job
  "Build a bare job map. opts: {:runs-on :needs :steps}."
  ([id] (make-job id nil))
  ([id opts]
   {:ci/id      id
    :ci/runs-on (get opts :runs-on nil)
    :ci/needs   (vec (get opts :needs []))
    :ci/steps   (vec (get opts :steps []))}))

;; --- queries ---

(defn job
  "Return the job map for `id`, or nil."
  [model id]
  (get-in model [:ci/jobs id]))

(defn jobs
  "Return the id-keyed jobs map."
  [model]
  (:ci/jobs model))

(defn needs
  "Return the :ci/needs vector of job `id` (vector of job-id strings)."
  [model id]
  (get-in model [:ci/jobs id :ci/needs] []))

(defn dependents
  "Return a sorted vector of job ids whose :ci/needs list includes `id`."
  [model id]
  (->> (vals (jobs model))
       (filter #(some #{id} (:ci/needs %)))
       (map :ci/id)
       (sort)
       (vec)))

(defn topo-order
  "Return all job ids in topological order (dependencies before dependants).
  Deterministic: ids sorted at each Kahn step.
  Returns a {:ci/cycle [...]} sentinel map (never throws) when a cycle exists."
  [model]
  (let [job-ids (set (keys (jobs model)))
        in-deg  (reduce (fn [m id]
                          (assoc m id
                                 (count (filter job-ids (needs model id)))))
                        {} job-ids)
        result
        (loop [queue (sort (filter #(zero? (get in-deg %)) job-ids))
               deg   in-deg
               out   []]
          (if-let [cur (first queue)]
            (let [deps     (dependents model cur)
                  deg'     (reduce (fn [d dep] (update d dep dec)) deg deps)
                  new-zero (sort (filter #(zero? (get deg' %)) deps))]
              (recur (concat (rest queue) new-zero) deg' (conj out cur)))
            out))]
    (if (= (count result) (count job-ids))
      result
      {:ci/cycle (vec (sort (remove (set result) job-ids)))})))
