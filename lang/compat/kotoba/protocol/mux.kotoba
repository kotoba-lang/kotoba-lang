(ns kotoba.protocol.mux
  "Session plane: authenticated stream and causal tip (ADR-2608145800 tick 4).

  Two coordinates, not one:

    mux  — Noise then Yamux. Bytes may be flowing.
    head — last signed action / graph CID this agent claims.

  A live mux does not make the head current. A current head does not
  open a socket. Identity is first; the stream is after. Who may write
  is govern, not this plane.

  Framing lives in io-libp2p-specs-transport. This ns does not speak
  octets and does not open sockets. `open-live` is fail-closed: this
  process is not a DHT node. Docs: docs/mux-and-head.md — not session.md."
  (:require [kotoba.lang.text :as str]
            [kotoba.protocol.vocab :as vocab]))

(defn stream
  "Yamux application stream. Stream 0 is session-control, not an app
  stream. Dialer ids are odd, listener ids are even."
  [{:keys [peer stream-id role alive?]}]
  (cond
    (and (some? peer) (or (not (string? peer)) (str/blank? peer)))
    {:error :invalid-peer :value peer}
    (not (#{:dialer :listener} role)) {:error :invalid-role :value role}
    (not (integer? stream-id)) {:error :invalid-stream-id :value stream-id}
    (zero? stream-id) {:error :reserved-stream}
    (and (= role :dialer) (even? stream-id)) {:error :stream-parity :role role :stream-id stream-id}
    (and (= role :listener) (odd? stream-id)) {:error :stream-parity :role role :stream-id stream-id}
    :else
    {:plane :session
     :kind :mux
     :identity? false
     :peer peer
     :stream-id stream-id
     :role role
     :alive? (boolean alive?)}))

(defn head
  "Causal tip this agent claims. Not a socket."
  [{:keys [agent cid]}]
  (cond
    (not (vocab/did-key? agent)) {:error :not-a-key :value agent}
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    :else
    {:plane :session
     :kind :head
     :agent agent
     :cid cid}))

(defn coords
  "Hold mux and head side by side. Do not collapse them."
  [mux head]
  (cond
    (and (map? mux) (:error mux)) mux
    (and (map? head) (:error head)) head
    (not (and (map? mux) (= :mux (:kind mux)))) {:error :not-a-mux :value mux}
    (not (and (map? head) (= :head (:kind head)))) {:error :not-a-head :value head}
    :else
    {:plane :session
     :identity? false
     :mux mux
     :head head}))

(defn at-head?
  "Asked graph CID vs claimed head. Mux liveness is ignored."
  [asked-cid session]
  (cond
    (and (map? session) (:error session)) false
    (not (vocab/cid? asked-cid)) false
    :else (= asked-cid (get-in session [:head :cid]))))

(defn attach-peer
  "A stream cannot rewrite the asked peer-id."
  [asked mux]
  (cond
    (and (map? mux) (:error mux)) mux
    (not (and (map? mux) (= :mux (:kind mux)))) {:error :not-a-mux :value mux}
    (and (some? (:peer mux)) (not= asked (:peer mux)))
    {:error :peer-mismatch :asked asked :got (:peer mux)}
    (or (not (string? asked)) (str/blank? asked)) {:error :invalid-peer :value asked}
    :else (assoc mux :peer asked)))

(defn place-call
  "Identity first. A stream without a CID is not a graph call."
  [{:keys [cid]} session]
  (cond
    (and (map? session) (:error session)) session
    (not (vocab/cid? cid)) {:error :identity-required :value cid}
    :else {:plane :session :cid cid :session session}))

(defn open-live
  "Refuse to open a socket. Session live waits on transport live,
  and this process is not a DHT node."
  [session]
  (cond
    (and (map? session) (:error session)) session
    :else {:error :not-a-dht-node
           :blocked-until :transport
           :session session}))
