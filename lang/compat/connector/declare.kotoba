(ns connector.declare
  "`connector.edn` — a connector repository's declaration, as data.

  The same role `capability.edn` plays for the `capability-` family: a file at
  the repository root stating the contract, readable without loading any
  Clojure. That is what makes a catalog over four thousand repositories
  possible — a generator that had to start a JVM per repository to find out
  whether it is a connector would not be run.

  It is GENERATED from the descriptor rather than written by hand, and each
  repository's tests check that the committed file still matches. A declaration
  maintained separately from the code it describes is a second source of truth
  for the same contract, and the workspace has had that go wrong before."
  (:require [connector.model :as m]
            [connector.provider :as p]))

(def schema "kotoba.connector.repository.v1")

(defn declaration
  "The `connector.edn` contents for one provider.

  `opts` names where the provider lives, because the declaration's job is to
  let a reader get from the file to the code:
  {:namespace \"google-calendar.connector\" :var \"provider\"}"
  [provider {:keys [namespace var authority]}]
  (let [d (p/descriptor provider)]
    (cond-> {:schema schema
             :connector/id (:connector/id d)
             :connector/name (:connector/name d)
             :connector/origin-domain (:connector/origin-domain d)
             :connector/namespace namespace
             :connector/var (or var "provider")
             :connector/auth-kind (get-in d [:connector/auth :connector.auth/kind])
             :connector/auth-pkce? (get-in d [:connector/auth :connector.auth/pkce?])
             :connector/client-id-env (get-in d [:connector/auth :connector.auth/client-id-env])
             :connector/base-scopes (vec (get-in d [:connector/auth :connector.auth/base-scopes]))
             ;; Per tool, not just the union: the union is what a full grant
             ;; costs, and the point of this plane is that a deployment does
             ;; not have to pay it.
             :connector/tools (mapv (fn [t]
                                      {:connector/name (:connector/name t)
                                       :connector/effect (:connector/effect t)
                                       :connector/scopes (vec (:connector/scopes t))
                                       :connector/description (:connector/description t)})
                                    (m/tools d))
             :connector/scopes (m/scopes d)}
      (:connector/summary d) (assoc :connector/summary (:connector/summary d))
      (:connector/docs-url d) (assoc :connector/docs-url (:connector/docs-url d))
      authority (assoc :authority authority))))
