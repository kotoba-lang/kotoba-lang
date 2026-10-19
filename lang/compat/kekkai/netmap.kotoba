(ns kekkai.netmap
  "The wire netmap: the one artifact this control plane hands the data plane.

  The charter says the actor *publishes reachability and the node applies it*,
  and `kekkai-node`'s README states the handoff format as
  `:netmap/{version,tailnet,self,peers,edges,relays}` EDN. Until this namespace
  existed **nothing produced that shape** — the control plane emitted `:netmap`
  control-plane records and assessments, the node read a file, and the two
  repositories were joined only by prose. A node therefore trusted whoever
  could write its netmap file, which is the same hole `kekkai.node.netmap`'s
  own docstring calls out about `fleet.edn`.

  This is the projection, and it is pure: `{:nodes :plane :relays :heartbeats}`
  in, one node's netmap out. Signing it is `kekkai.envelope`; the two are
  separate because the projection must be testable without a key, and because
  the byte-exactness the signature depends on belongs to the encoder rather
  than to the thing being encoded.

  ## Three readings that are decisions, not details

  **A peer with no edge in either direction is not published at all.** Not
  published-and-denied: absent. Deny-by-default is about what may be reached,
  but a netmap listing every node in the tailnet would hand each node a roster
  of an organization it has no grant into, and the node cannot un-learn it.

  **A peer that IS edged but is expired or unadmitted is published, marked.**
  The node keeps it and denies it by status, which is what lets it log
  `judah: :key-expired` instead of silently failing to connect —
  `kekkai.node.netmap/peer-table` is explicit that 'exists and is not
  authorized' and 'unknown' are different operational states. Filtering here
  would collapse them and make revocation indistinguishable from a typo.

  **Cross-tailnet peers are excluded even when an active peering allows the
  edge.** This one is a limitation, not a preference, and it is reported rather
  than hidden (`excluded`, reason `:cross-tailnet-not-carriable`). The reason is
  structural: `kekkai.node.netmap/prologue-string` binds the *publisher's*
  tailnet into the Noise prologue, so two nodes in peered-but-different tailnets
  derive different prologues and the handshake fails — loudly, but with a
  message about authentication rather than about peering. Publishing a peer that
  provably cannot handshake is worse than omitting it and saying so. Carrying
  peered traffic needs the prologue to bind the peering for cross-tailnet
  sessions, which is a data-plane protocol change."
  (:require [clojure.string :as str]
            [kekkai.acl :as acl]))

(def wire-any
  "The port wildcard the node side understands.

  The control-plane policy language writes a wildcard as the string `\"*\"`
  (`kekkai.acl` grants) and `kekkai.node.netmap/permitted?` tests for the
  keyword `:any`. Neither is wrong; they were written apart. The translation
  lives here, in the one place that crosses the boundary, so that a policy is
  never silently narrowed to a literal port named `\"*\"` that nothing matches."
  :any)

(defn- port->wire [p]
  (if (= "*" p) wire-any p))

(defn- ports->wire [ports]
  (mapv port->wire ports))

(defn- parse-endpoint
  "`\"203.0.113.7:41641\"` -> `{:kind :reflexive :host … :port …}`, or nil.

  A heartbeat endpoint is what the node last observed about itself, so it is
  published as `:reflexive`: a hint for `kekkai.node.disco` to probe, never a
  fact. Anything unparseable yields nil rather than a partial candidate."
  [endpoint]
  (when (string? endpoint)
    (let [idx (str/last-index-of endpoint ":")
          host (when idx (subs endpoint 0 idx))
          port (when idx (subs endpoint (inc idx)))]
      (when (and (seq host) (re-matches #"\d{1,5}" (str port)))
        {:kind :reflexive :host host :port (parse-long port)}))))

(defn- endpoints-of [heartbeats node-id]
  (into []
        (comp (keep :endpoint) (keep parse-endpoint) (distinct))
        (get heartbeats node-id)))

(defn- node->wire [node heartbeats]
  (cond-> {:node/id (:id node)
           :node/key (:static-pub node)
           :node/overlay-ip (:overlay-ip node)
           :node/status (:status node)
           :node/expires-at (:key-expiry node)}
    (seq (endpoints-of heartbeats (:id node)))
    (assoc :node/endpoints (endpoints-of heartbeats (:id node)))))

(defn- relay->wire [relay]
  {:relay/name (:name relay)
   :relay/region (:region relay)
   :relay/host (:host relay)
   :relay/port (:port relay)
   :relay/key (:key relay)})

(defn data-plane-problems
  "Fields a node needs before it can appear in any netmap, as a vector of
  problems.

  The control plane admits a *device*; the data plane dials a *key*. Those are
  different facts and the store historically carried only the first, so a node
  can be perfectly admitted and still be unpublishable. Reported as a named
  problem at publish time rather than emitted as a netmap with a blank
  `:node/key`, which the node would reject with `:peer-missing-key` — true, but
  attributed to the wrong side of the boundary."
  [node]
  (cond-> []
    (str/blank? (str (:static-pub node)))
    (conj {:problem :missing-static-pub :node (:id node)})
    (str/blank? (str (:overlay-ip node)))
    (conj {:problem :missing-overlay-ip :node (:id node)})))

(defn- edge-entry [from-id to-id decision]
  {:edge/from from-id
   :edge/to to-id
   :edge/capabilities (:capabilities decision)
   :edge/ports (ports->wire (:ports decision))})

(defn- candidate-decisions
  "For one candidate: `{:node … :out decision :in decision}`."
  [plane self node]
  {:node node
   :out (acl/edge-decision plane self node)
   :in (acl/edge-decision plane node self)})

(defn excluded
  "Why each node other than `self-id` is absent from the netmap.

  Exists because `kekkai.node.netmap/denials` has a counterpart obligation on
  this side: a node that cannot see why it was left out of a netmap cannot tell
  a revoked grant from a publisher bug."
  [{:keys [nodes plane]} self-id]
  (let [self (first (filter #(= self-id (:id %)) nodes))
        self-tailnet (acl/tailnet-of self)]
    (into []
          (comp
           (keep (fn [node]
                  (when-not (= self-id (:id node))
                    (let [{:keys [out in]} (candidate-decisions plane self node)]
                      (cond
                        (not= self-tailnet (acl/tailnet-of node))
                        {:node (:id node)
                         :excluded :cross-tailnet-not-carriable
                         :peering-would-allow? (boolean (or (:allowed? out)
                                                            (:allowed? in)))
                         :tailnet (acl/tailnet-of node)}

                        (and (not (:allowed? out)) (not (:allowed? in)))
                        {:node (:id node)
                         :excluded :no-edge-either-direction
                         :outbound-reason (:reason out)
                         :inbound-reason (:reason in)}))))))
          (sort-by :id nodes))))

(defn publish
  "One node's netmap, in the shape `kekkai-node` consumes.

  `version` is the caller's: it is the value both ends bind into the Noise
  prologue, so it has to be monotonic across a rollout and only the thing
  driving the rollout knows that. Deriving it here from content would make two
  publishers disagree about ordering the moment they were run out of step.

  **Peers and edges are sorted by id, not left in input order.** The netmap is
  signed as a byte string (`kekkai.envelope`), so ordering is part of what gets
  signed: a projector that preserved input order would give the same logical
  netmap two different signatures depending on whether the nodes arrived from
  `all-nodes` (sorted) or from a map's seq (not). Found by a test comparing a
  store-fed publish against a hand-assembled one — they agreed on every fact
  and disagreed on the bytes."
  [{:keys [nodes plane relays heartbeats version]} self-id]
  (let [self (first (filter #(= self-id (:id %)) nodes))]
    (when-not self
      (throw (ex-info "netmap requested for an unknown node"
                      {:type :kekkai/unknown-node :node self-id})))
    (when-not (int? version)
      (throw (ex-info "netmap version must be an integer"
                      {:type :kekkai/invalid-netmap-version :version version})))
    (let [self-tailnet (acl/tailnet-of self)
          published (->> nodes
                         (remove #(= self-id (:id %)))
                         (filter #(= self-tailnet (acl/tailnet-of %)))
                         (map #(candidate-decisions plane self %))
                         (filter #(or (:allowed? (:out %)) (:allowed? (:in %))))
                         (sort-by #(:id (:node %)))
                         vec)
          problems (into (data-plane-problems self)
                         (mapcat #(data-plane-problems (:node %)))
                         published)]
      (when (seq problems)
        (throw (ex-info "netmap cannot be published: nodes lack data-plane facts"
                        {:type :kekkai/unpublishable-nodes :problems problems})))
      {:netmap/version version
       :netmap/tailnet self-tailnet
       :netmap/self {:node/id (:id self)
                     :node/key (:static-pub self)
                     :node/overlay-ip (:overlay-ip self)}
       :netmap/peers (mapv #(node->wire (:node %) heartbeats) published)
       :netmap/edges (->> published
                          (mapcat (fn [{:keys [node out in]}]
                                    (cond-> []
                                      (:allowed? out)
                                      (conj (edge-entry self-id (:id node) out))
                                      (:allowed? in)
                                      (conj (edge-entry (:id node) self-id in)))))
                          (sort-by (juxt :edge/from :edge/to))
                          vec)
       :netmap/relays (mapv relay->wire relays)})))
