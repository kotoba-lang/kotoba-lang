(ns kotoba.shell.experience
  "Local-first UX/wellbeing projection. Collection and persistence remain
  application-owned.")

(defn summarize [events]
  (let [events (vec events)
        timed (keep :ui/duration-ms events)
        failures (count (remove :ui/ok? events))
        feelings (frequencies (keep :ui/feeling events))
        total (count events)
        avg (when (seq timed) (quot (reduce + timed) (count timed)))
        failure-penalty (if (pos? total) (* 45.0 (/ failures total)) 0)
        felt-total (+ (get feelings :calm 0) (get feelings :heavy 0))
        heavy-penalty (if (pos? felt-total) (* 35.0 (/ (get feelings :heavy 0) felt-total)) 0)
        latency-penalty (min 20.0 (/ (double (or avg 0)) 250.0))
        score (max 0 (min 100 (long (- 100 failure-penalty heavy-penalty latency-penalty))))]
    {:interaction-count total :failure-count failures
     :average-duration-ms avg :feelings feelings :comfort-score score
     :signal (cond (< score 55) :reduce-load
                   (< score 80) :simplify
                   :else :comfortable)}))
