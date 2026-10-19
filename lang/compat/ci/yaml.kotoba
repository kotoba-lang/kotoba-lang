(ns ci.yaml
  "Conversion between an already-parsed GitHub Actions YAML map (string keys, as
  a YAML parser produces) and the ci-clj EDN model. Does NOT parse YAML text —
  the host is responsible for calling a YAML parser first and handing the
  resulting Clojure map here.

  `from-data` maps string-keyed GitHub Actions YAML structure:
    name / on / jobs / <job-id> / runs-on / needs / steps / <step> / uses / run / with

  to a namespaced EDN model (`:ci/*` keys, `needs` normalised to vector).
  `to-data` is the inverse.")

;; --- from-data helpers ---

(defn- normalize-needs
  "A YAML `needs:` field may be absent (nil), a bare string, or a list of
  strings. Always normalise to a vector of strings."
  [raw]
  (cond
    (nil? raw)    []
    (string? raw) [raw]
    :else         (vec raw)))

(defn- step-from-data
  "Convert a string-keyed step map to a :ci/* step map.
  Only :ci/uses, :ci/run, and :ci/with are mapped; extra keys are dropped."
  [step]
  (cond-> {}
    (contains? step "uses") (assoc :ci/uses (get step "uses"))
    (contains? step "run")  (assoc :ci/run  (get step "run"))
    (contains? step "with") (assoc :ci/with (get step "with"))))

(defn- job-from-data
  "Convert a string-keyed job map to a :ci/* job map."
  [id job-map]
  {:ci/id      id
   :ci/runs-on (get job-map "runs-on")
   :ci/needs   (normalize-needs (get job-map "needs"))
   :ci/steps   (mapv step-from-data (get job-map "steps" []))})

(defn from-data
  "Convert an already-parsed GitHub Actions YAML map (string keys) to a
  namespaced ci-clj model map. The `:ci/on` value is kept as-is (parsed map).
  An absent or nil `needs` is normalised to an empty vector."
  [data]
  {:ci/name (get data "name")
   :ci/on   (get data "on" {})
   :ci/jobs (into {}
                  (map (fn [[id job-map]]
                         [id (job-from-data id job-map)])
                       (get data "jobs" {})))})

;; --- to-data helpers ---

(defn- step-to-data
  "Convert a :ci/* step map back to a string-keyed YAML step map."
  [step]
  (cond-> {}
    (contains? step :ci/uses) (assoc "uses" (:ci/uses step))
    (contains? step :ci/run)  (assoc "run"  (:ci/run  step))
    (contains? step :ci/with) (assoc "with" (:ci/with step))))

(defn- job-to-data
  "Convert a :ci/* job map back to a string-keyed YAML job map."
  [job]
  (cond-> {"runs-on" (:ci/runs-on job)
           "steps"   (mapv step-to-data (:ci/steps job))}
    (seq (:ci/needs job)) (assoc "needs" (vec (:ci/needs job)))))

(defn to-data
  "Convert a ci-clj model map back to a string-keyed YAML-shaped map. The
  inverse of `from-data`; round-trips losslessly for the fields ci-clj models."
  [model]
  (cond-> {}
    (:ci/name model) (assoc "name" (:ci/name model))
    (:ci/on   model) (assoc "on"   (:ci/on   model))
    (seq (:ci/jobs model))
    (assoc "jobs"
           (into {}
                 (map (fn [[id job]]
                        [id (job-to-data job)])
                      (:ci/jobs model))))))
