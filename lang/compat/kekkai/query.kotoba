(ns kekkai.query
  "Pure zero-trust status lookups for a kekkai Store.

  No LLM/governor involved — `kekkai.operation`'s CoordinationActor is how a
  node GETS to `authorized` (coord-LLM proposes admission, TailnetGovernor
  censors it, high-stakes admission always routes to a human). This ns only
  READS that already-committed ground fact, for callers that need to gate on
  current admission state without running the actor (e.g. murakumo's fleet
  reachability — see kotoba-lang/murakumo's `murakumo.kekkai`).

  ## Admission is not authorization

  `authorized?` answers 'is this machine in the mesh at all'. It does NOT
  answer 'may it reach that machine', and a caller that treats the two as the
  same has quietly restored the flat, single-organization model the tenant
  plane exists to replace — every admitted node of every organization would
  pass. `reachable?` is the question that has an organization boundary in it,
  and it is the one a caller gating access wants."
  (:require [kekkai.acl :as acl]
            [kekkai.store :as store]))

(defn status-of
  "The node's kekkai admission status (\"authorized\"/\"pending\"/\"expired\"/
  \"revoked\"), or \"unknown\" if the node has never been registered in this
  store."
  [st node-id]
  (or (:status (store/node st node-id)) "unknown"))

(defn authorized?
  "True only when the node has been admitted (status = \"authorized\").
  Deny-by-default: unknown/pending/expired/revoked all fall through to false."
  [st node-id]
  (= "authorized" (status-of st node-id)))

(defn tailnet-of
  "The tailnet (organization) a registered node belongs to.

  An unregistered node reports the default tailnet, matching
  `acl/tailnet-of` — so callers must gate on `authorized?` first rather than
  reading an organization off a machine that was never admitted."
  [st node-id]
  (acl/tailnet-of (store/node st node-id)))

(defn same-tailnet?
  "Are these two nodes in the same organization's tailnet?"
  [st a b]
  (= (tailnet-of st a) (tailnet-of st b)))

(defn reachability
  "The full decision for src → dst: `{:allowed? true :via ... :ports ...}`, or
  `{:allowed? false :reason :not-authorized|:deny-by-default|:cross-tailnet|
  :peering-grant-missing}`.

  Admission is checked here rather than left to the ACL: a pending or revoked
  machine that happens to match a grant is still not in the mesh, and
  reporting that as `:deny-by-default` would send an operator to edit an ACL
  that is already correct."
  [st src-id dst-id]
  (cond
    (not (authorized? st src-id))
    {:allowed? false :reason :not-authorized :node src-id}
    (not (authorized? st dst-id))
    {:allowed? false :reason :not-authorized :node dst-id}
    :else (acl/edge-decision (store/plane st)
                             (store/node st src-id)
                             (store/node st dst-id))))

(defn reachable?
  "Deny-by-default: true only when both nodes are admitted AND the edge is
  backed by their own tailnet's policy or by a mutually-approved peering."
  [st src-id dst-id]
  (boolean (:allowed? (reachability st src-id dst-id))))

(defn netmap
  "Every peer `node-id` may currently reach, as
  [{:peer id :ports [...] :via :policy|:peering}].

  This is the value a node PULLS; the actor never pushes it (charter G1)."
  [st node-id now]
  (acl/reachable-peers (store/plane st) (store/node st node-id)
                       (store/all-nodes st) now))

(defn nodes-of
  "Every node in one organization's tailnet."
  [st tailnet-id]
  (filterv #(= tailnet-id (acl/tailnet-of %)) (store/all-nodes st)))

(defn peerings-of
  "Every peering one tailnet is party to, approved or not.

  Callers deciding reachability must not read this list as 'these
  organizations are connected': a peering carrying one signature appears here
  and opens nothing (`acl/peering-active?` requires both)."
  [st tailnet-id]
  (filterv #(contains? (acl/peering-endpoints %) tailnet-id)
           (store/all-peerings st)))
