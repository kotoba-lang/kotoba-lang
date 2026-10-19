(ns ci.run
  "Portable, pure CI run state machine. Hosts persist the returned values and
   perform execution; this namespace only defines legal state transitions."
  (:require [ci.execute :as execute]
            [ci.validate :as validate]))

(def terminal-states #{:passed :failed :timed-out :cancelled})

(def transitions
  {:queued    #{:leased :cancelled}
   :leased    #{:running :queued :timed-out :cancelled}
   :running   #{:passed :failed :timed-out :cancelled}
   :passed    #{}
   :failed    #{}
   :timed-out #{}
   :cancelled #{}})

(defn new-run
  "Create a queued run from a valid workflow and immutable source descriptor.
   `id` is supplied by the host so it may use its preferred CID/hash scheme."
  [id source pipeline]
  (when-not (validate/valid? pipeline)
    (throw (ex-info "ci: invalid pipeline"
                    {:reason :invalid-pipeline
                     :problems (validate/problems pipeline)})))
  {:ci.run/id id
   :ci.run/source source
   :ci.run/pipeline pipeline
   :ci.run/plan (execute/plan pipeline)
   :ci.run/state :queued
   :ci.run/revision 0
   :ci.run/events []})

(defn terminal? [run]
  (contains? terminal-states (:ci.run/state run)))

(defn transition
  "Apply a legal transition and append its event. `event` is host-defined data
   (timestamps, runner id, receipt CID, etc.) and is never interpreted here."
  ([run next-state] (transition run next-state nil))
  ([run next-state event]
   (let [current (:ci.run/state run)]
     (when-not (contains? (get transitions current #{}) next-state)
       (throw (ex-info "ci: illegal run state transition"
                       {:reason :illegal-transition
                        :from current :to next-state
                        :run/id (:ci.run/id run)})))
     (-> run
         (assoc :ci.run/state next-state)
         (update :ci.run/revision inc)
         (update :ci.run/events conj
                 (cond-> {:ci.event/from current :ci.event/to next-state}
                   event (assoc :ci.event/data event)))))))
