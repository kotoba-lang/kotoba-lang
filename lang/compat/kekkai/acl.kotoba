(ns kekkai.acl
  "Pure, deny-by-default ACL evaluation over the published policy — the zero-
  trust core (the kekkai analog of a Tailscale HuJSON policy). No I/O, no store:
  it takes plain node/policy maps so BOTH the independent TailnetGovernor and
  the mock coord-LLM can evaluate the same rules without sharing code that would
  couple the censor to the proposer.

  A node 'matches' a selector if the selector names the node's owner (user) or
  any tag the node carries. An edge src→dst is allowed iff some grant's :src
  matches src and :dst matches dst — there is NO implicit allow.

  ## The organization boundary is structural, not a policy rule

  A selector is a bare token (`\"alice\"`, `\"tag:server\"`), so two
  organizations that both name a tag `tag:server` would, under one flat policy,
  match each other's nodes. That is not a rule someone forgot to write — it is
  the shape of the policy itself, and no additional grant repairs it.

  So the org boundary lives OUTSIDE the grant language: every node belongs to
  exactly one **tailnet** (`:tailnet` — one organization's isolated overlay),
  each tailnet has its own policy, and `edge-decision` refuses a cross-tailnet
  edge BEFORE it ever looks at a grant. An ACL cannot grant its way across the
  boundary because the boundary is checked first, on a field the policy does
  not own.

  **An absent `:tailnet` is not a wildcard.** It resolves to the specific
  tailnet `default-tailnet` — so a node that predates the tenant plane keeps
  reaching exactly its historical peers, and an explicitly-placed node in
  `\"gftd\"` can never reach it. Reading unset as 'matches anything' is the
  precise failure this namespace exists to prevent.

  ## Crossing the boundary takes an object, not a rule

  Real organizations do share services, so the answer to cross-org
  reachability is not 'impossible' — it is a **peering**: a separate record
  naming both tailnets, carrying its own directional grants, and valid only
  when **both** organizations have approved it. One org cannot unilaterally
  open a path into another, and the grants that cross the boundary are not
  buried inside either org's internal policy where the other side cannot read
  them."
  (:require [clojure.set :as set]))

(def default-tailnet
  "The tailnet a node with no explicit `:tailnet` belongs to.

  A *specific* tailnet, deliberately — not a wildcard, not 'any'. Nodes
  registered before the tenant plane existed all resolve here together, so
  their reachability is preserved exactly as it was; a node explicitly placed
  in another tailnet is isolated from them."
  "default")

(defn tailnet-of
  "The tailnet a node belongs to. Never nil — see `default-tailnet`."
  [node]
  (or (:tailnet node) default-tailnet))

(defn same-tailnet? [a b]
  (= (tailnet-of a) (tailnet-of b)))

(defn principals
  "The set of selector tokens a node satisfies: its owner login-id plus tags."
  [node]
  (into #{(:user node)} (:tags node)))

(defn- selector-matches? [selectors principals]
  (boolean (seq (set/intersection (set selectors) principals))))

(def default-capabilities
  "What a grant that names no `:capabilities` conveys.

  `:overlay` alone — session establishment and nothing else. Deliberately the
  floor rather than a useful set: a policy written before a capability existed
  must not silently start conferring it the day the capability is added. Every
  richer capability (`:ssh`, `:private-http`, `:tun`) is opt-in per grant."
  [:overlay])

(defn grant-match
  "The first grant in `grants` that permits `src-node` → `dst-node`, or nil.

  `grant-allowed?` is the ports-only reading of this same match. Both exist
  because a netmap needs the whole grant (capabilities as well as ports) while
  the governor only ever asks the yes/no question — and re-deriving the match
  in a second place is exactly how the censor and the proposer drift apart."
  [grants src-node dst-node]
  (let [sp (principals src-node) dp (principals dst-node)]
    (some (fn [{:keys [src dst] :as grant}]
            (when (and (selector-matches? src sp) (selector-matches? dst dp))
              grant))
          grants)))

(defn grant-capabilities
  "The capabilities a matched grant conveys, never empty — see
  `default-capabilities`."
  [grant]
  (vec (or (seq (:capabilities grant)) default-capabilities)))

(defn grant-allowed?
  "Does `grants` (a plain vector) permit `src-node` to reach `dst-node`?
  Returns the matching grant's allowed ports (a vector, possibly [\"*\"]) or
  nil if no grant matches.

  Grant-level only: it knows nothing about tailnets. Anything computing real
  reachability must go through `edge-decision`, which applies the org boundary
  first."
  [grants src-node dst-node]
  (when-let [grant (grant-match grants src-node dst-node)]
    (vec (:ports grant))))

(defn edge-allowed?
  "Does the policy permit `src-node` to reach `dst-node`? Returns the matching
  grant's allowed ports (a vector, possibly [\"*\"]) or nil if denied.

  Intra-tailnet only, and kept at its original signature because a policy map
  is exactly what a single-tailnet deployment publishes. `edge-decision` is the
  tenant-aware entry point."
  [policy src-node dst-node]
  (grant-allowed? (:grants policy) src-node dst-node))

(defn tag-owned?
  "Is `tag` authorized for `user` by the policy's tag-owners? Untagged-but-owned
  selectors (a bare user) are always self-owned."
  [policy user tag]
  (boolean (some #{user} (get-in policy [:tag-owners tag]))))

(defn unowned-tags
  "Tags a node claims that its owner is NOT authorized to assume (privilege
  escalation via self-claimed tags)."
  [policy node]
  (vec (remove #(tag-owned? policy (:user node) %) (:tags node))))

;; ───────────────────────── peering (cross-org) ─────────────────────────

(defn peering-endpoints
  "The two tailnets a peering names, as a set."
  [peering]
  #{(:a peering) (:b peering)})

(defn peering-active?
  "A peering opens a path only when it is active AND **both** named tailnets
  have approved it.

  Both, deliberately: a peering approved by one side is a path that one
  organization opened into another without that other organization's consent.
  Status alone is not enough either — a revoked peering carrying two
  historical approvals must stop carrying traffic the moment it is revoked."
  [peering]
  (boolean
   (and peering
        (= "active" (:status peering))
        (let [ends (peering-endpoints peering)]
          (and (= 2 (count ends))
               (= ends (set/intersection ends (set (:approved-by peering)))))))))

(defn active-peering
  "The active, mutually-approved peering joining tailnets `a` and `b`, or nil.

  A tailnet is never peered with itself — an intra-tailnet edge is a policy
  question, and letting a peering also answer it would create a second,
  quieter path to the same decision."
  [peerings a b]
  (when (not= a b)
    (first (filter #(and (= #{a b} (peering-endpoints %)) (peering-active? %))
                   peerings))))

(defn peering-grants
  "The peering's grants that apply to traffic ORIGINATING in tailnet `from`.

  Grants are directional (`:from`): 'org A may reach org B's build cache' must
  not silently also mean 'org B may reach org A'. A grant with no `:from` is
  ignored rather than read as bidirectional — an under-specified grant inside a
  cross-org object is exactly where a default-open reading does its damage."
  [peering from]
  (filterv #(= from (:from %)) (:grants peering)))

;; ───────────────────────── the tenant-aware decision ─────────────────────────

(defn edge-decision
  "The reachability decision, org boundary first.

  `plane` is pure data: `{:policies {tailnet-id policy} :peerings [peering]}`.

  Returns `{:allowed? true :ports [...] :capabilities [...] :via
  :policy|:peering ...}`, or `{:allowed? false :reason kw ...}` where reason is
  one of
  `:deny-by-default` (same tailnet, no grant matched), `:cross-tailnet`
  (different tailnets, no mutually-approved peering) or
  `:peering-grant-missing` (peered, but this particular edge is not in the
  peering's grants for this direction).

  Those three stay distinct because they call for different actions: a missing
  grant is a policy edit, a missing peering is a negotiation between two
  organizations, and a missing directional grant is an edit to a document both
  organizations already signed. Collapsing them into one 'denied' sends an
  operator to edit a policy that cannot possibly fix it."
  [plane src-node dst-node]
  (let [ta (tailnet-of src-node)
        tb (tailnet-of dst-node)]
    (if (= ta tb)
      (if-let [grant (grant-match (:grants (get-in plane [:policies ta]))
                                  src-node dst-node)]
        {:allowed? true :ports (vec (:ports grant))
         :capabilities (grant-capabilities grant) :via :policy :tailnet ta}
        {:allowed? false :reason :deny-by-default :tailnet ta})
      (if-let [pr (active-peering (:peerings plane) ta tb)]
        (if-let [grant (grant-match (peering-grants pr ta) src-node dst-node)]
          {:allowed? true :ports (vec (:ports grant))
           :capabilities (grant-capabilities grant)
           :via :peering :peering (:id pr)
           :from ta :to tb}
          {:allowed? false :reason :peering-grant-missing :peering (:id pr)
           :from ta :to tb})
        {:allowed? false :reason :cross-tailnet :from ta :to tb}))))

(defn reachable-peers
  "Every authorized, key-valid peer `src-node` may reach under `plane`, as
  [{:peer id :ports [...] :via :policy|:peering}]. `now` gates expired keys.

  Candidates in other tailnets are deliberately NOT filtered out up front: an
  active peering legitimately puts them in reach, and pre-filtering by tailnet
  here would make peering invisible to the one function that computes a
  netmap. The boundary is enforced per-edge by `edge-decision` instead."
  [plane src-node candidate-nodes now]
  (->> candidate-nodes
       (remove #(= (:id %) (:id src-node)))
       (keep (fn [dst]
               (when (and (= "authorized" (:status dst))
                          (number? (:key-expiry dst)) (> (:key-expiry dst) now))
                 (let [d (edge-decision plane src-node dst)]
                   (when (:allowed? d)
                     {:peer (:id dst) :ports (:ports d) :via (:via d)})))))
       vec))
