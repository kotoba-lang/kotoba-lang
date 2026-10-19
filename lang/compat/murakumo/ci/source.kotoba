(ns murakumo.ci.source
  "Normalize forge-specific events into one immutable Murakumo RunRequest."
  (:require [murakumo.canonical :as canonical]
            [murakumo.identity :as identity]))

(defn run-id [source pipeline-digest]
  (identity/graph-cid
   (canonical/string {:source source :pipeline/digest pipeline-digest})))

(defn github
  "Normalize the stable subset of a GitHub webhook. The caller verifies the
   webhook signature before calling this pure function."
  [event pipeline-digest]
  (let [repo (or (get-in event [:repository :full_name])
                 (get-in event ["repository" "full_name"]))
        sha (or (:after event) (get event "after")
                (get-in event [:pull_request :head :sha])
                (get-in event ["pull_request" "head" "sha"]))
        ref (or (:ref event) (get event "ref")
                (get-in event [:pull_request :head :ref])
                (get-in event ["pull_request" "head" "ref"]))
        source {:source/type :github :source/repo repo
                :source/revision sha :source/ref ref}]
    (when-not (and repo sha)
      (throw (ex-info "murakumo-ci: incomplete GitHub event"
                      {:reason :invalid-source-event})))
    {:ci.run/id (run-id source pipeline-digest)
     :ci.run/source source
     :ci.run/pipeline-digest pipeline-digest}))

(defn radicle
  "Normalize a verified Radicle repository-change event. `rid`, commit and
   signer/node identity remain explicit provenance inputs."
  [event pipeline-digest]
  (let [rid (or (:rid event) (get event "rid"))
        commit (or (:commit event) (get event "commit"))
        ref (or (:ref event) (get event "ref"))
        signer (or (:signer event) (get event "signer"))
        source {:source/type :radicle :source/repo rid
                :source/revision commit :source/ref ref
                :source/signer signer}]
    (when-not (and rid commit signer)
      (throw (ex-info "murakumo-ci: incomplete Radicle event"
                      {:reason :invalid-source-event})))
    {:ci.run/id (run-id source pipeline-digest)
     :ci.run/source source
     :ci.run/pipeline-digest pipeline-digest}))
