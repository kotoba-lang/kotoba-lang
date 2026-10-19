(ns bonsai.refs
  "Mutable ref/branch pointers (e.g. \"refs/heads/main\" -> commit CID) as
   quads in an arrangement db — the mutable-pointer-over-immutable-content-
   addressed-DAG pattern, same shape Datomic uses for its own indexes.
   Shares its db with bonsai.object (see bonsai.repo) — a repo is
   one arrangement db holding both objects and refs."
  (:require [arrangement.core :as arr]
            [arrangement.query :as q]
            [kotoba.lang.text :as str]
            [ipld.core :as ipld]))

(defn- ref-pred [ref-name] (str "ref:" ref-name))

(defn set-ref
  "Point ref-name at commit-cid, replacing whatever it previously pointed at."
  [db repo-id ref-name commit-cid]
  (as-> db db
    (if-let [prev-link (some->> (q/query db [repo-id (ref-pred ref-name) nil] (constantly true))
                                 first :o)]
      (arr/retract-quad db {:s repo-id :p (ref-pred ref-name) :o prev-link})
      db)
    (arr/assert-quad db {:s repo-id :p (ref-pred ref-name) :o (ipld/link commit-cid)})))

(defn get-ref
  "The commit CID ref-name currently points at, or nil."
  [db repo-id ref-name]
  (some-> (q/query db [repo-id (ref-pred ref-name) nil] (constantly true))
          first :o ipld/link-cid))

(defn list-refs
  "{ref-name commit-cid} for every ref registered under repo-id."
  [db repo-id]
  (into {}
        (keep (fn [[p os]]
                (when (str/starts-with? p "ref:")
                  [(subs p 4) (ipld/link-cid (first os))])))
        (arr/entity-attrs db repo-id)))
