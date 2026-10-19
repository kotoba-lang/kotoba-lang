(ns calendar.validate
  (:require [calendar.model :as model]))

(defn problem [severity code id msg]
  {:calendar/severity severity :calendar/code code :calendar/id id :calendar/msg msg})

(defn event-problems [ev]
  (cond-> []
    (or (nil? (:calendar/start ev)) (nil? (:calendar/end ev)))
    (conj (problem :error :event/missing-time (:calendar/id ev) "event requires start and end"))

    (and (:calendar/start ev) (:calendar/end ev)
         (not (neg? (compare (:calendar/start ev) (:calendar/end ev)))))
    (conj (problem :error :event/non-positive-duration (:calendar/id ev) "event start must be before end"))))

(defn problems [cal]
  (vec (mapcat event-problems (vals (:calendar/events cal)))))

(defn valid? [cal]
  (not-any? #(= :error (:calendar/severity %)) (problems cal)))
