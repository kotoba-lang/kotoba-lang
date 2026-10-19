(ns kotoba.protocol.transport
  "Transport plane: bytes to a neighbor. Not identity (ADR-2608145800 tick 3).

  A hop is a multiaddr plus protocol capability. It does not rewrite a
  CID or a peer-id. HTTPS gateway is a location projection of an
  existing identity, not a new identity.

  This ns does not open sockets. `dial-live` is fail-closed: this
  process is not a DHT node. Do not inject a dialer here to raise
  :live. Framing lives in io-libp2p-specs-transport; this plane only
  says what a hop is and what it must not become."
  (:require [kotoba.lang.text :as str]
            [kotoba.protocol.ref :as ref]
            [kotoba.protocol.vocab :as vocab]))

(defn multiaddr?
  "libp2p multiaddr form: starts with `/`, no URI scheme. Not identity."
  [s]
  (boolean (and (string? s)
                (str/starts-with? s "/")
                (not (str/includes? s "://"))
                (not (str/blank? (subs s 1))))))

(defn hop
  "A neighbor location. `:identity?` is always false."
  [{:keys [addr protocols]}]
  (cond
    (not (string? addr)) {:error :invalid-addr :value addr}
    (not (multiaddr? addr)) {:error :not-a-multiaddr :value addr}
    :else
    {:plane :transport
     :identity? false
     :addr addr
     :protocols (vec (or protocols []))}))

(defn attach
  "Place a hop next to an asked identity. The hop cannot become that
  identity and cannot rewrite it."
  [{:keys [cid peer]} hop]
  (cond
    (and (map? hop) (:error hop)) hop
    (not (and (map? hop) (= :transport (:plane hop))))
    {:error :not-a-hop :value hop}
    (true? (:identity? hop)) {:error :transport-is-not-identity}
    (and (some? cid) (some? (:cid hop)) (not= cid (:cid hop)))
    {:error :cid-mismatch :asked cid :got (:cid hop)}
    (and (some? peer) (some? (:peer hop)) (not= peer (:peer hop)))
    {:error :peer-mismatch :asked peer :got (:peer hop)}
    (and (some? cid) (not (vocab/cid? cid))) {:error :invalid-cid :value cid}
    (and (some? peer) (or (not (string? peer)) (str/blank? peer)))
    {:error :invalid-peer :value peer}
    :else
    (cond-> hop
      (some? cid) (assoc :cid cid)
      (some? peer) (assoc :peer peer))))

(defn gateway
  "HTTPS gateway is transport + location. The CID / k51 is unchanged.

  `host` is an origin (`https://ipfs.kotobase.net`), not identity."
  [{:keys [host cid name]}]
  (cond
    (not (string? host)) {:error :invalid-host :value host}
    (not (or (str/starts-with? host "https://")
             (str/starts-with? host "http://")))
    {:error :not-a-gateway-origin :value host}
    (and (some? cid) (some? name)) {:error :one-identity}
    (some? cid)
    (let [parsed (ref/parse (str "ipfs://" cid))]
      (if (:error parsed)
        parsed
        {:plane :transport
         :kind :gateway
         :identity? false
         :cid cid
         :host host
         :url (ref/gateway-url parsed host)}))
    (some? name)
    (let [parsed (ref/parse (str "ipns://" name))]
      (if (:error parsed)
        parsed
        {:plane :transport
         :kind :gateway
         :identity? false
         :name name
         :host host
         :url (ref/gateway-url parsed host)}))
    :else {:error :identity-required}))

(defn dial-live
  "Refuse to open a socket. This process is not a DHT node.

  A well-formed hop still returns `:not-a-dht-node`. Do not bind a
  dialer here to raise `:live`."
  [hop]
  (cond
    (and (map? hop) (:error hop)) hop
    :else {:error :not-a-dht-node
           :blocked-until :dht-node-transport
           :hop hop}))
