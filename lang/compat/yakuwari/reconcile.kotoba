(ns yakuwari.reconcile
  "Turn a role's desired capacity into spawn/cancel decisions.

  This is the only place a yakuwari acts on its own judgement: it observes
  how many bounded executions are live and decides how many should be. It
  cannot change its own objective, scale bounds or policy while doing so —
  that would be self-evolution, which belongs to the AO layer.

  Pure: `runs` and `now-ms` come from the caller, nothing is dispatched
  here. Extracted from `kotoba.tamaki.actor`."
  (:require [yakuwari.spec :as spec]))

(def active-statuses
  "Statuses that still occupy a slot."
  #{:queued :leased :running :checkpointed :held})

(def default-lease-grace-ms 120000)

(defn runs-for [spec runs]
  (let [run-key (or (:yakuwari/run-key spec) :agent.run/yakuwari)]
    (filterv #(= (:yakuwari/id spec) (get % run-key)) runs)))

(defn stale-run?
  "A leased run whose lease expired without progress. Reaped rather than
  counted: a run nobody is executing still occupies a slot, and leaving it
  there starves the role it was meant to fill."
  ([run now-ms]
   (stale-run? {} run now-ms))
  ([spec run now-ms]
   (when (and now-ms
              (contains? active-statuses (:agent.run/status run)))
     (let [updated (or (:agent.run/updated-at run)
                       (:agent.run/created-at run) 0)
           grace (or (:agent.run/lease-grace-ms run)
                     (:yakuwari/lease-grace-ms spec)
                     default-lease-grace-ms)]
       (case (:yakuwari/stale-policy spec)
         :execution-deadline
         (let [deadline (or (get-in run [:agent.run/budget :deadline-ms])
                            (:yakuwari/default-deadline-ms spec)
                            1200000)]
           (>= (- now-ms updated) (+ deadline grace)))

         ;; The portable default retains the narrower original contract:
         ;; only an abandoned lease is a ghost. Hosts with durable running
         ;; states can opt into :execution-deadline explicitly.
         (and (= :leased (:agent.run/status run))
              (>= (- now-ms updated) grace)))))))

(defn- live-active [spec runs now-ms]
  (->> (runs-for spec runs)
       (filter #(contains? active-statuses (:agent.run/status %)))
       (remove #(stale-run? spec % now-ms))
       vec))

(defn- scale-up-extra
  "Extra capacity justified by live pressure.

  Held runs count toward queue depth on purpose: a role whose workers are
  all waiting on a human is a role making no progress, and refusing to add
  capacity there means the backlog and the HIL queue starve each other."
  [spec active]
  (let [scale (:yakuwari/scale spec)
        pressure (:yakuwari/control-pressure spec)
        thresholds (merge (:scale-up-on scale)
                          (when (map? pressure) pressure))
        {:keys [queue-depth blocker-count]} thresholds
        queued (count (filter #(contains? #{:queued :held} (:agent.run/status %)) active))
        queued-only (count (filter #(= :queued (:agent.run/status %)) active))
        blocked (count (filter #(= :held (:agent.run/status %)) active))
        max-extra (max 0 (- (:max scale) (:desired scale)))]
    (if (= :tamaki (:yakuwari/pressure-mode spec))
      (let [business-threshold (get-in scale [:scale-up-on
                                              :business-pressure])]
        (-> (cond-> 0
              (and queue-depth (integer? queue-depth) (pos? queue-depth)
                   (>= queued-only queue-depth))
              (+ (max 1 (inc (- queued-only queue-depth))))

              (and blocker-count (integer? blocker-count)
                   (pos? blocker-count) (>= blocked blocker-count))
              (+ blocked)

              (and (number? business-threshold) (number? pressure)
                   (>= (double pressure) (double business-threshold)))
              (+ (max 1 (long (Math/ceil (* 2.0 (double pressure)))))))
            (min max-extra)))
      (-> (cond-> 0
            (and queue-depth (>= queued queue-depth)) inc
            (and blocker-count (>= blocked blocker-count)) inc)
          (min max-extra)))))

(defn effective-desired
  "Baseline `:desired`, raised by live pressure, clamped to [min, max]."
  ([spec runs] (effective-desired spec runs nil))
  ([spec runs now-ms]
   (let [spec (spec/validate! spec)
         {:keys [min desired] mx :max} (:yakuwari/scale spec)
         active (live-active spec runs now-ms)
         raised (+ desired (scale-up-extra spec active))]
     (-> raised (clojure.core/min mx) (clojure.core/max min)))))

(defn- cancel-candidates
  "Newest first, and only runs that have done no work. Cancelling a
  :running execution to shed capacity would throw away work already paid
  for; only :queued and :held are eligible."
  [active now-ms scale-down-after-ms]
  (->> active
       reverse
       (filter #(contains? #{:queued :held} (:agent.run/status %)))
       (filter (fn [run]
                 (or (nil? scale-down-after-ms)
                     (nil? now-ms)
                     (let [updated (or (:agent.run/updated-at run)
                                       (:agent.run/created-at run) 0)]
                       (>= (- now-ms updated) scale-down-after-ms)))))
       vec))

(defn plan
  "Spawn/cancel/reap plan to reach effective desired capacity. Returns data;
  executing it is the caller's job."
  ([spec runs] (plan spec runs nil))
  ([spec runs now-ms]
   (let [spec (spec/validate! spec)
         all (runs-for spec runs)
         stale (filterv #(stale-run? spec % now-ms) all)
         active (live-active spec runs now-ms)
         desired (effective-desired spec runs now-ms)
         delta (- desired (count active))
         cancellable (cancel-candidates active now-ms
                                        (:scale-down-after-ms (:yakuwari/scale spec)))]
     {:yakuwari/id (:yakuwari/id spec)
      :desired desired
      :running (count (filter #(contains? #{:leased :running :checkpointed}
                                          (:agent.run/status %)) active))
      :queued (count (filter #(= :queued (:agent.run/status %)) active))
      :blocked (count (filter #(= :held (:agent.run/status %)) active))
      :spawn (max 0 delta)
      :cancel (if (neg? delta)
                (mapv :agent.run/id (take (- delta) cancellable))
                [])
      :reap (mapv :agent.run/id stale)
      :active active})))
