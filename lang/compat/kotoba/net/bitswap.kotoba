(ns kotoba.net.bitswap
  "Bitswap-equivalent block-exchange semantics as pure, transport-independent
   functions: want-list / have-list bookkeeping, want/have intersection, and
   a WantSince delta-sync request applied against a local commit-log.

   Scope: dedup/intersection/commit-log math only. No block transfer over
   any wire, no session/ledger accounting for tit-for-tat — see
   docs/ADR-kotoba-net-p2p-semantics.md."
  (:require [clojure.set :as set]))

;; ---------------------------------------------------------------------------
;; want-list / have-list
;; ---------------------------------------------------------------------------

(defn empty-want-list [] #{})
(defn empty-have-list [] #{})

(defn add-want [want-list cid] (conj want-list cid))
(defn remove-want [want-list cid] (disj want-list cid))

(defn add-have [have-list cid] (conj have-list cid))
(defn remove-have [have-list cid] (disj have-list cid))

;; ---------------------------------------------------------------------------
;; respond-to-want
;; ---------------------------------------------------------------------------

(defn respond-to-want
  "Given the *peer's* want-list and *our* have-list, return the sorted
   vector of CIDs we should send: the set intersection, i.e. what they
   want that we actually have."
  [peer-want-list our-have-list]
  (->> (set/intersection (set peer-want-list) (set our-have-list))
       sort
       vec))

;; ---------------------------------------------------------------------------
;; WantSince: delta-sync request/response over a commit-log
;; ---------------------------------------------------------------------------
;; Wire schema (EDN):
;;   {:graph-cid "bafy..."   ; which graph/root this request is scoped to
;;    :since-seq 4           ; last seq the requester already has (exclusive)
;;    :head-cid  "bafy..."}  ; requester's belief about current head (advisory)
;;
;; Local commit-log is modeled as a vector of {:seq n :cid "..."} maps,
;; ordered by :seq ascending, seq starting at 1 (seq 0 means "nothing yet").

(defn commit-log-entry [seq cid] {:seq seq :cid cid})

(defn make-want-since
  [graph-cid since-seq head-cid]
  {:graph-cid graph-cid :since-seq since-seq :head-cid head-cid})

(defn commits-since
  "Applies a WantSince request against `commit-log` (a vector of
   {:seq :cid} sorted ascending by :seq) and returns the vector of entries
   with seq strictly greater than (:since-seq want-since).

   Boundary behavior:
   - since-seq 0            -> all commits (full sync from genesis)
   - since-seq = last seq   -> empty vector (already caught up)
   - since-seq > last seq   -> empty vector (requester claims to be ahead;
                                no commits exist to satisfy that, so we
                                return nothing rather than raise)"
  [commit-log want-since]
  (let [since (:since-seq want-since)]
    (vec (filter #(> (:seq %) since) commit-log))))
