(ns bonsai.object
  "Content-addressed blob/tree/commit object model — the git-equivalent
   layer — represented as native arrangement (Datomic-shaped) datoms
   rather than a separate content-addressed block store. A blob/tree/
   commit's subject IS its own content hash; io-multiformats/io-ipld
   contribute only their pure canonical-encoding/hashing functions
   (cidv1-raw, encode, cid) to derive that identity — nothing is
   persisted through them. Persisting a repo's whole datom set (objects
   and refs together) to durable storage is bonsai.repo/persist!'s
   job, the same arrangement.core/commit! path every other domain in this
   ecosystem uses."
  (:require [multiformats.core :as mf]
            [ipld.core :as ipld]
            [arrangement.core :as arr]))

(defn write-blob
  "Content-address raw bytes (git blob semantics: the raw, 0x55 codec) and
   assert them as a single blob/bytes datom. Returns [db' cid]."
  [db bytes]
  (let [cid (mf/cidv1-raw bytes)]
    [(arr/assert-quad db {:s cid :p "blob/bytes" :o bytes}) cid]))

(defn read-blob
  "This blob's raw bytes, or nil if absent."
  [db cid]
  (first (get (arr/entity-attrs db cid) "blob/bytes")))

(defn- tree-node
  "entries: seq of {:name :cid :kind}. Canonical (name-sorted) shape whose
   DAG-CBOR hash is the tree's content-addressed identity — the same
   identity a persisted io-ipld node would have had, even though nothing
   is persisted through io-ipld here."
  [entries]
  {"kind" "tree"
   "entries" (vec (for [{:keys [name cid kind]} (sort-by :name entries)]
                     [name (ipld/link cid) (clojure.core/name kind)]))})

(defn write-tree
  "Assert a tree's (name-sorted) entries as a single tree/entries datom.
   Returns [db' cid]."
  [db entries]
  (let [node (tree-node entries)
        cid (ipld/cid (ipld/encode node))]
    [(arr/assert-quad db {:s cid :p "tree/entries" :o (get node "entries")}) cid]))

(defn read-tree
  "Decode a tree back into {:kind :tree :entries [{:name :cid :kind} ...]}."
  [db cid]
  (when-let [raw-entries (first (get (arr/entity-attrs db cid) "tree/entries"))]
    {:kind :tree
     :entries (vec (for [[name link kind] raw-entries]
                      {:name name :cid (ipld/link-cid link) :kind (keyword kind)}))}))

(defn- commit-node
  [{:keys [tree parents author message ts]}]
  {"kind" "commit" "tree" (ipld/link tree) "parents" (mapv ipld/link parents)
   "author" author "message" message "ts" ts})

(defn write-commit
  "opts: {:tree cid :parents [cid ...] :author :message :ts}. The commit's
   identity is the DAG-CBOR hash of the whole node (parents is a vector —
   0, 1, or N — so merge commits are representable); persisted as one
   Datomic-style attribute per datom (commit/tree, commit/parents,
   commit/author, commit/message, commit/ts) on that content-hash subject.
   commit/tree is a single ipld/link, so arrangement.core/refs-to can find
   \"which commits reference this tree\" for free — call it as
   (arr/refs-to db (ipld/link tree-cid)), NOT with the bare CID string;
   the reverse-reference index behind refs-to is keyed by the Link value
   itself. (That index is named :vaet as of arrangement a5d68dc8, and :ocp
   in the older arrangement this repo still pins -- which is why the name
   is not written here. Go through refs-to and it does not matter.)
   commit/parents
   holds its whole ordered vector as one literal so parent order
   (first-parent history) survives, at the cost of not being
   reverse-indexed per-parent. Returns [db' cid]."
  [db opts]
  (let [node (commit-node opts)
        cid (ipld/cid (ipld/encode node))
        {:keys [tree parents author message ts]} opts]
    [(-> db
         (arr/assert-quad {:s cid :p "commit/tree" :o (ipld/link tree)})
         (arr/assert-quad {:s cid :p "commit/parents" :o (mapv ipld/link parents)})
         (arr/assert-quad {:s cid :p "commit/author" :o author})
         (arr/assert-quad {:s cid :p "commit/message" :o message})
         (arr/assert-quad {:s cid :p "commit/ts" :o ts}))
     cid]))

(defn read-commit
  "Decode a commit back into {:kind :commit :tree :parents :author :message :ts}."
  [db cid]
  (let [attrs (arr/entity-attrs db cid)]
    (when (seq attrs)
      {:kind :commit
       :tree (some-> (get attrs "commit/tree") first ipld/link-cid)
       :parents (mapv ipld/link-cid (first (get attrs "commit/parents")))
       :author (first (get attrs "commit/author"))
       :message (first (get attrs "commit/message"))
       :ts (first (get attrs "commit/ts"))})))
