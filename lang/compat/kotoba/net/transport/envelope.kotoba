(ns kotoba.net.transport.envelope
  "Pure, transport-independent helpers factored out of
   kotoba.net.transport.tcp (the .cljs, Node-only real-socket adapter):
   wire-message envelope construction/shape, peer-id -> host:port
   resolution, and folding a node's configured :peers into a fresh
   kotoba.net.gossip peer-state. None of this needs a real socket, so it
   stays portable .cljc and testable under plain JVM `clojure -M:test`,
   same split kotoba-lang/dtn's transport uses between its pure `.cljc`
   data-model namespaces and its impure `.cljs` socket-I/O namespace.

   Wire envelope shapes (every one an EDN map with a :kind tag the .cljs
   dispatcher switches on):

     {:kind :gossip                :topic t :payload p :from peer-id}
     {:kind :bitswap-want          :from peer-id :want-set #{cid ...}}
     {:kind :bitswap-have          :from peer-id :have [cid ...]}
     {:kind :bitswap-commits-since :from peer-id :want-since {...}}
     {:kind :bitswap-commits       :from peer-id :entries [{:seq n :cid c} ...]}

   The :gossip envelope's shape matches kotoba.net.gossip/route-message's
   own documented input map exactly (:topic/:payload/:from), MINUS :self —
   :self is always the RECEIVING node's own node-id, computed locally at
   dispatch time, never carried on the wire (a message doesn't know in
   advance which node will receive it next)."
  (:require [kotoba.net.gossip :as gossip]))

;; ---------------------------------------------------------------------------
;; peer-id -> host:port resolution
;; ---------------------------------------------------------------------------

(defn resolve-peer
  "peers is a node's configured :peers map,
   {peer-id {:host \"...\" :port N :topics #{...}} ...}. Returns
   {:host \"...\" :port N} for peer-id, or throws ex-info (never nil — a
   fanout/want/commits-since target that isn't actually a configured peer
   is a real bug worth surfacing loudly, not a value a caller could
   silently ignore)."
  [peers peer-id]
  (if-let [entry (get peers peer-id)]
    (select-keys entry [:host :port])
    (throw (ex-info (str "kotoba.net.transport: unknown peer " peer-id
                          " (not present in this node's configured :peers)")
                     {:peer-id peer-id :known-peers (set (keys peers))}))))

;; ---------------------------------------------------------------------------
;; peer registration -> kotoba.net.gossip peer-state
;; ---------------------------------------------------------------------------

(defn register-peers
  "Fold peers (start-node!'s :peers option -- a
   {peer-id {:host ... :port ... :topics #{...}} ...} map) into a fresh
   kotoba.net.gossip peer-state: one gossip/add-peer call per configured
   peer, for whichever :topics that peer's own config entry declares
   (missing/nil :topics treated as #{}, matching gossip/add-peer's own
   default-arity behavior). Pure -- used once at start-node! time so this
   node's gossip fanout (kotoba.net.gossip/gossip-fanout, via
   route-message) knows which of its configured peers actually care about
   which topics."
  [peers]
  (reduce-kv (fn [state peer-id {:keys [topics]}]
               (gossip/add-peer state peer-id (or topics #{})))
             (gossip/empty-peer-state)
             peers))

;; ---------------------------------------------------------------------------
;; envelope construction
;; ---------------------------------------------------------------------------

(defn gossip-envelope
  "A :gossip wire envelope carrying payload on topic, :from this node
   (the immediate sender -- either the originating publisher for a
   locally-originated publish!, or a relaying node's own id when
   forwarding an inbound message onward). See namespace docstring for why
   :self is deliberately absent here."
  [from topic payload]
  {:kind :gossip :topic topic :payload payload :from from})

(defn bitswap-want-envelope
  "A :bitswap-want request: from is asking for whichever of want-set the
   receiving node has."
  [from want-set]
  {:kind :bitswap-want :from from :want-set (set want-set)})

(defn bitswap-have-envelope
  "A :bitswap-have response to a :bitswap-want request: have is the
   sorted vector kotoba.net.bitswap/respond-to-want produced (the
   requester's want-set intersected with the responder's have-set)."
  [from have]
  {:kind :bitswap-have :from from :have (vec have)})

(defn bitswap-commits-since-envelope
  "A :bitswap-commits-since request: from is asking for every commit-log
   entry newer than want-since (a kotoba.net.bitswap/make-want-since
   map)."
  [from want-since]
  {:kind :bitswap-commits-since :from from :want-since want-since})

(defn bitswap-commits-envelope
  "A :bitswap-commits response to a :bitswap-commits-since request:
   entries is the vector kotoba.net.bitswap/commits-since produced."
  [from entries]
  {:kind :bitswap-commits :from from :entries (vec entries)})
