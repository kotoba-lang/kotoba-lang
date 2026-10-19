(ns kaisha.validate
  (:require [kaisha.model :as model]))

(defn problem [severity code id msg]
  {:kaisha/severity severity :kaisha/code code :kaisha/id id :kaisha/msg msg})

(defn message-problems [sp ch msg]
  (let [msgs (:kaisha/messages ch)]
    (cond-> []
      (nil? (:kaisha/author msg))
      (conj (problem :error :message/missing-author (:kaisha/id msg)
                     "message requires an author"))

      (not (contains? (:kaisha/members sp) (:kaisha/author msg)))
      (conj (problem :error :message/unknown-author (:kaisha/id msg)
                     "message author must be a member of the space"))

      (and (= :private (:kaisha/visibility ch))
           (:kaisha/author msg)
           (not (contains? (:kaisha/members ch) (:kaisha/author msg))))
      (conj (problem :error :message/author-not-channel-member (:kaisha/id msg)
                     "message author must be a member of the private channel"))

      (nil? (:kaisha/at msg))
      (conj (problem :error :message/missing-at (:kaisha/id msg)
                     "message requires a timestamp"))

      (and (:kaisha/thread msg)
           (not (contains? msgs (:kaisha/thread msg))))
      (conj (problem :error :message/orphan-reply (:kaisha/id msg)
                     "reply thread parent does not exist in the channel"))

      (and (:kaisha/thread msg)
           (some-> (get msgs (:kaisha/thread msg)) :kaisha/thread))
      (conj (problem :error :message/nested-thread (:kaisha/id msg)
                     "thread parent must be a top-level message")))))

(defn channel-problems [sp ch]
  (let [own (cond-> []
              (and (= :private (:kaisha/visibility ch))
                   (empty? (:kaisha/members ch)))
              (conj (problem :error :channel/private-without-members (:kaisha/id ch)
                             "private channel requires at least one member"))

              (seq (remove #(contains? (:kaisha/members sp) %) (:kaisha/members ch)))
              (conj (problem :error :channel/unknown-member (:kaisha/id ch)
                             "channel members must be members of the space")))]
    (into own (mapcat #(message-problems sp ch %) (vals (:kaisha/messages ch))))))

(defn problems [sp]
  (vec (mapcat #(channel-problems sp %) (vals (:kaisha/channels sp)))))

(defn valid? [sp]
  (not-any? #(= :error (:kaisha/severity %)) (problems sp)))
