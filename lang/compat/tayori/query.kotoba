(ns tayori.query
  "Pure status lookups for a tayori Store.

  No LLM/governor involved — `tayori.operation`'s CorrespondenceActor is how a
  draft GETS to `:sent`/a revision GETS to `:published` (reply-LLM proposes,
  ComplianceGovernor censors, send/publish always route to a human). This ns
  only READS already-committed ground facts, for callers that need to gate on
  current status without running the actor (e.g. local-manimani's triage UI
  checking whether a thread already has a pending/sent draft)."
  (:require [tayori.store :as store]))

(defn draft-status
  "\"proposed\"/\"sent\", or \"none\" if no draft has ever been proposed."
  [st thread-id]
  (name (or (:status (store/draft-of st thread-id)) "none")))

(defn sent? [st thread-id]
  (= :sent (:status (store/draft-of st thread-id))))

(defn revision-status
  "\"proposed\"/\"published\", or \"none\" if no revision has ever been proposed."
  [st document-id]
  (name (or (:status (store/revision-of st document-id)) "none")))

(defn published? [st document-id]
  (= :published (:status (store/revision-of st document-id))))
