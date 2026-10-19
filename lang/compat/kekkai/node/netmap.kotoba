(ns kekkai.node.netmap
  "The netmap as the node sees it: pure consumption of what the control plane
  published, with the deny-by-default reading preserved.

  `kekkai`'s charter is that the control plane never actuates the data plane —
  it publishes reachability and the node applies it. This namespace is the
  node's half of that contract, and it is deliberately paranoid in one specific
  way: **the node never derives authority from its own configuration.** Being
  listed in a local config file, or being able to reach a peer's socket, is
  never sufficient. A peer is dialable only if the published netmap says the
  peer is authorized, unexpired, and that an edge grants the capability being
  asked for. Anything not granted is denied — including anything this code
  cannot parse.

  That mirrors the mistake `murakumo`'s README documents about `fleet.edn`:
  'anyone who can edit fleet.edn and reach a node over Tailscale gets treated as
  fleet today'. A netmap consumer that trusts its local file reintroduces
  exactly that hole one layer down."
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(def ^:const authorized-status "authorized")

(defn- node-id [n] (or (:node/id n) (:id n)))

(defn validate
  "-> a vector of problems (empty when the netmap is usable). Cheap structural
   validation only; cryptographic verification of the netmap's signature is the
   caller's job (see `verified-netmap`)."
  [{:keys [netmap/version netmap/tailnet netmap/self netmap/peers] :as nm}]
  (cond-> []
    (nil? nm) (conj {:problem :missing-netmap})
    (not (int? version)) (conj {:problem :missing-version})
    (str/blank? (str tailnet)) (conj {:problem :missing-tailnet})
    (nil? (node-id self)) (conj {:problem :missing-self-id})
    (str/blank? (str (:node/key self))) (conj {:problem :missing-self-key})
    (not (sequential? peers)) (conj {:problem :peers-not-sequential})
    :always (into (keep (fn [p]
                          (cond
                            (nil? (node-id p)) {:problem :peer-missing-id :peer p}
                            (str/blank? (str (:node/key p)))
                            {:problem :peer-missing-key :peer (node-id p)}))
                        (when (sequential? peers) peers)))))

(defn usable?
  "A netmap with any structural problem is not partially applied — the node keeps
   the previous one. Half-applying a netmap means silently dropping edges, which
   presents as a mysterious connectivity loss instead of an error."
  [nm]
  (empty? (validate nm)))

(defn prologue-string
  "The Noise prologue both sides must agree on. Binding the tailnet and the
   netmap version into it means a handshake cannot be replayed against a
   different tailnet, and a peer running an older netmap fails the handshake
   loudly instead of operating under stale ACLs.

   The cost is real and worth stating: every node must move to the new version
   before sessions re-establish, so netmap rollout is a coordinated step, not a
   silent one."
  [{:keys [netmap/tailnet netmap/version]}]
  (str "kekkai-node/1 tailnet:" tailnet " netmap:" version))

(defn peer-table
  "-> {node-id peer}. Peers that fail their own admission checks are kept but
   marked, because 'this peer exists and is not authorized' is a different
   operational state from 'this peer is unknown', and the agent logs them
   differently."
  [{:keys [netmap/peers]}]
  (into {} (map (juxt node-id identity)) peers))

(defn authorized?
  "Does the control plane currently admit this peer? `now` in seconds."
  [peer now]
  (boolean (and peer
                (= authorized-status (:node/status peer))
                (or (nil? (:node/expires-at peer))
                    (> (:node/expires-at peer) now)))))

(defn- edge-index
  "-> {[from to] #{capability}} — only for edges the netmap actually lists."
  [{:keys [netmap/edges]}]
  (reduce (fn [acc {:keys [edge/from edge/to edge/capabilities]}]
            (update acc [from to] (fnil set/union #{}) (set capabilities)))
          {}
          edges))

(defn capabilities
  "The set of capabilities `from` may use toward `to`. Empty set = denied."
  [nm from to]
  (get (edge-index nm) [from to] #{}))

(defn reachable?
  "Deny-by-default: an edge must exist and grant `capability`."
  [nm from to capability]
  (contains? (capabilities nm from to) capability))

(defn permitted?
  "Deny-by-default application authorization including the destination port."
  [nm from to capability port]
  (some
   (fn [{edge-from :edge/from edge-to :edge/to
         edge-capabilities :edge/capabilities edge-ports :edge/ports}]
     (and (= from edge-from)
          (= to edge-to)
          (contains? (set edge-capabilities) capability)
          (or (contains? (set edge-ports) :any)
              (contains? (set edge-ports) port))))
   (:netmap/edges nm)))

(defn dialable
  "The peers this node may dial for `capability` at `now`, as
   `[{:peer … :capabilities …}]`. This is the only function the agent should use
   to decide who to hand to the handshake — it folds admission and the edge
   grant together so neither can be checked without the other."
  [nm capability now]
  (let [self (node-id (:netmap/self nm))]
    (into []
          (keep (fn [peer]
                  (let [caps (capabilities nm self (node-id peer))]
                    (when (and (authorized? peer now) (contains? caps capability))
                      {:peer peer :capabilities caps}))))
          (:netmap/peers nm))))

(defn sessionable
  "Peers allowed to establish a Noise session in either direction.

  A connector needs a session for an inbound-only edge, but that must not grant
  it the right to send application traffic in the reverse direction."
  [nm now]
  (let [self (node-id (:netmap/self nm))]
    (into []
          (keep
           (fn [peer]
             (let [peer-id (node-id peer)
                   outgoing (capabilities nm self peer-id)
                   incoming (capabilities nm peer-id self)]
               (when (and (authorized? peer now)
                          (or (contains? outgoing :overlay)
                              (contains? incoming :overlay)))
                 {:peer peer
                  :outgoing-capabilities outgoing
                  :incoming-capabilities incoming}))))
          (:netmap/peers nm))))

(defn denials
  "Why each non-dialable peer was denied — for the agent's log. Silent denial is
   how a deny-by-default system becomes unoperable: the node must be able to say
   'judah: not authorized (expired)' rather than just failing to connect."
  [nm capability now]
  (let [self (node-id (:netmap/self nm))]
    (into []
          (keep (fn [peer]
                  (let [id (node-id peer)
                        caps (capabilities nm self id)]
                    (cond
                      (not (authorized? peer now))
                      {:peer id :denied (if (= authorized-status (:node/status peer))
                                          :key-expired
                                          (keyword (str "status-" (or (:node/status peer) "unknown"))))}
                      (not (contains? caps capability))
                      {:peer id :denied :no-edge-grant :capability capability
                       :granted caps}))))
          (:netmap/peers nm))))

(defn peer-key
  "The peer's static X25519 public key (hex in the netmap)."
  [peer]
  (:node/key peer))

(defn relays
  "Relay records, ordered as published. Selection is `kekkai.node.relay/home`."
  [nm]
  (vec (:netmap/relays nm)))

(defn endpoints
  "A peer's published endpoint candidates. Direct candidates are hints only —
   they may be stale, wrong, or behind a NAT that will not accept them, which is
   the whole reason `kekkai.node.disco` probes instead of trusting them."
  [peer]
  (vec (:node/endpoints peer)))

(defn overlay-ip [peer] (:node/overlay-ip peer))
