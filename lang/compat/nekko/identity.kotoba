(ns nekko.identity
  "Sovereign repo identity: an RID is the CID of a repo's genesis block,
   anchoring identity to the same content-addressing scheme (io-ipld,
   DAG-CBOR) kotoba-git uses for its objects, rather than a separate
   ad-hoc CID implementation."
  (:require [ipld.core :as ipld]))

(defn genesis!
  "Create a new sovereign repo identity anchored to owner-did (a did:key
   string). Returns the RID (a CID string) -- the repo's identity IS the
   content address of its own genesis block."
  [put! owner-did ts]
  (ipld/put-node! put! {"kind" "rad-identity" "did" owner-did "created" ts}))

(defn read-identity
  "The genesis block a RID points at, or nil."
  [get-fn rid]
  (when-let [node (ipld/get-node get-fn rid)]
    {:did (get node "did") :created (get node "created")}))

(defn owner-did
  [get-fn rid]
  (:did (read-identity get-fn rid)))
