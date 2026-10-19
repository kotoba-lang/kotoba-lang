(ns kekkai.node.endpoint
  "Endpoint candidates — the addresses at which a peer might be reachable.

  Three kinds, and the distinction matters because they fail differently:

  - `:local`     an address the peer sees on its own interfaces. Works on the
                 same LAN, useless across the internet, and is the candidate
                 that makes two nodes in the same office stop paying for a relay.
  - `:reflexive` the address a STUN server saw the peer's packets come *from*,
                 i.e. its NAT's outside mapping. This is the candidate a hole
                 punch actually uses.
  - `:relay`     a relay region. Always works if the relay is up; costs a round
                 trip through it.

  A candidate is a hint, never a fact: NAT mappings expire, addresses are stale
  the moment they are published, and a symmetric NAT allocates a different port
  per destination so the peer's reflexive candidate is wrong *for us*
  specifically. `kekkai.node.disco` therefore probes; nothing here trusts."
  (:require [clojure.string :as str]))

(def kinds #{:local :reflexive :relay})

(defn endpoint
  ([kind host port] (endpoint kind host port nil))
  ([kind host port region]
   (cond-> {:kind kind :host host :port port}
     region (assoc :region region))))

(defn relay-endpoint [region] {:kind :relay :region region})

(defn parse
  "\"192.168.1.10:41641\" -> {:kind :local :host … :port …}. IPv6 in brackets."
  ([s] (parse s :local))
  ([s kind]
   (when-not (str/blank? (str s))
     (let [s (str s)]
       (if (str/starts-with? s "[")
         (let [close (str/index-of s "]")
               host (subs s 1 close)
               port (subs s (+ close 2))]
           (endpoint kind host (parse-long port)))
         (let [idx (str/last-index-of s ":")]
           (when idx
             (endpoint kind (subs s 0 idx) (parse-long (subs s (inc idx)))))))))))

(defn ->str [{:keys [kind host port region]}]
  (if (= :relay kind)
    (str "relay:" region)
    (if (str/includes? (str host) ":")
      (str "[" host "]:" port)
      (str host ":" port))))

(defn ekey
  "A stable identity for a candidate, used as the map key for its probe state."
  [ep]
  (->str ep))

(defn direct? [{:keys [kind]}] (contains? #{:local :reflexive} kind))

(defn normalize
  "Coerce whatever the netmap published into candidate maps, dropping anything
   unparseable rather than guessing (a wrong candidate wastes probe bursts)."
  [candidates]
  (into []
        (comp (keep (fn [c]
                      (cond
                        (string? c) (parse c)
                        (and (map? c) (= :relay (:kind c)) (:region c)) c
                        (and (map? c) (:host c) (:port c))
                        (endpoint (if (contains? kinds (:kind c)) (:kind c) :local)
                                  (:host c) (:port c) (:region c))
                        :else nil)))
              (distinct))
        candidates))
