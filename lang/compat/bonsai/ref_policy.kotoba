(ns bonsai.ref-policy
  "Ref-update policy: deciding whether a proposed ref move is allowed given
   its *shape* (fast-forward vs not), as distinct from kotoba-rad's job of
   deciding *who* is allowed to move a ref. A repo can compose both: check
   authorize-push? (identity) and fast-forward? (shape) before calling
   bonsai.refs/set-ref, which itself enforces neither on its own."
  (:require [bonsai.log :as log]
            [bonsai.refs :as refs]))

(defn fast-forward?
  "Is moving a ref from old-commit-cid to new-commit-cid a fast-forward?
   True if old-commit-cid is nil (the ref doesn't exist yet -- creating a
   ref is always a fast-forward), if it equals new-commit-cid (a no-op
   update), or if it's one of new-commit-cid's ancestors in the commit DAG."
  [db old-commit-cid new-commit-cid]
  (or (nil? old-commit-cid)
      (= old-commit-cid new-commit-cid)
      (contains? (log/ancestors db new-commit-cid) old-commit-cid)))

(defn set-ref-ff-only!
  "Like bonsai.refs/set-ref, but throws ex-info (rather than silently
   moving the ref) if the update is not a fast-forward. Use this instead
   of set-ref directly when a repo/branch policy requires ff-only pushes
   (e.g. any branch other than a force-pushable feature branch)."
  [db repo-id ref-name commit-cid]
  (let [current (refs/get-ref db repo-id ref-name)]
    (when-not (fast-forward? db current commit-cid)
      (throw (ex-info "ref update is not a fast-forward"
                       {:repo-id repo-id :ref ref-name
                        :current current :proposed commit-cid})))
    (refs/set-ref db repo-id ref-name commit-cid)))

(defn set-ref-guarded!
  "Compose *identity* policy (who's allowed) with *shape* policy (is it a
   fast-forward) into the single call kotoba-git's own README used to name
   as a gap: neither bonsai.refs/set-ref nor set-ref-ff-only! checks
   who's authorized, and kotoba-rad's authorize-push?/authorize-push-
   cacao? don't know what a fast-forward is. This namespace still has no
   dependency on kotoba-rad (or anything else) -- `authorized?` is a
   caller-supplied 0-arg predicate, typically a partial application of
   whichever authorization scheme the caller already chose, e.g.:

     (set-ref-guarded! db repo-id ref-name commit-cid
                        #(kotoba-rad.push-gate/authorize-push?
                           get-fn journal-head owner-did rid ref-name commit-cid sr))

   Throws ex-info (ref untouched) on either failure, with :reason
   distinguishing :unauthorized from :not-fast-forward so a caller can
   report the two differently (e.g. HTTP 403 vs 409)."
  [db repo-id ref-name commit-cid authorized?]
  (cond
    (not (authorized?))
    (throw (ex-info "ref update not authorized"
                     {:reason :unauthorized :repo-id repo-id :ref ref-name
                      :proposed commit-cid}))

    (not (fast-forward? db (refs/get-ref db repo-id ref-name) commit-cid))
    (throw (ex-info "ref update is not a fast-forward"
                     {:reason :not-fast-forward :repo-id repo-id :ref ref-name
                      :current (refs/get-ref db repo-id ref-name) :proposed commit-cid}))

    :else
    (refs/set-ref db repo-id ref-name commit-cid)))
