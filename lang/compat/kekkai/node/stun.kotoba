(ns kekkai.node.stun
  "Reflexive endpoint discovery: ask a STUN server what address our packets
  appear to come from, i.e. our NAT's outside mapping. That answer is the
  candidate a hole punch actually uses.

  The STUN codec itself is [`org-ietf-turn`](https://github.com/kotoba-lang/org-ietf-turn)'s
  (`kotoba.turn.stun`, RFC 8489) — this namespace only puts a socket under it.

  One caveat that decides how much to trust the result: behind a **symmetric**
  NAT the mapping is per destination, so the address the STUN server saw is not
  the address our peer will see. The candidate is still worth publishing (it is
  correct for cone NATs, which are the majority) but it is why
  `kekkai.node.disco` treats every candidate as a hypothesis to probe. If two
  peers are both symmetric, no candidate exchange can work and the relay is the
  answer — see `disco`'s docstring."
  (:require [kekkai.node.udp :as udp]
            [kotoba.turn.stun :as stun]))

(defn- binding-request [tx-id]
  (stun/encode-header stun/binding-request 0 tx-id))

(defn- random-tx-id []
  ;; 96-bit transaction id (RFC 8489 §5). Random here is the right call — unlike
  ;; disco's tx-ids, this one is a defence against off-path response spoofing.
  (vec (js/Array.from (.getRandomValues js/crypto (js/Uint8Array. 12)))))

(defn reflexive
  "-> Promise of `\"host:port\"` (our mapped address) or nil on timeout.
   Uses `sock` if given, so the reflexive candidate is measured **on the same
   socket the data plane uses** — measuring on a fresh socket would report a
   different NAT mapping and publish a candidate that cannot receive anything."
  [{:keys [server sock timeout-ms] :or {timeout-ms 2000}}]
  (js/Promise.
   (fn [resolve _reject]
     (let [tx-id (random-tx-id)
           done (atom false)
           finish (fn [v] (when-not @done (reset! done true) (resolve v)))
           handler (fn [msg _from]
                     (try
                       (let [{:keys [type transaction-id]} (stun/decode-header msg)]
                         (when (and (= stun/binding-response type)
                                    (= (vec transaction-id) tx-id))
                           (let [attrs (stun/attributes msg)
                                 xma (some (fn [{:keys [type value]}]
                                             (when (= stun/attr-xor-mapped-address type) value))
                                           attrs)]
                             (when xma
                               (let [{:keys [ip port]} (stun/decode-xor-mapped-v4 xma)]
                                 (finish (str (clojure.string/join "." ip) ":" port)))))))
                       (catch :default _ nil)))]
       (.on sock "message" (fn [msg rinfo] (handler (udp/->vec msg) (udp/addr->str rinfo))))
       (udp/send! sock (binding-request tx-id) server)
       (js/setTimeout #(finish nil) timeout-ms)))))

(defn candidates
  "Ask several STUN servers and keep the distinct answers. More than one answer
   means the NAT is mapping per destination (symmetric) — worth knowing, and
   reported rather than hidden, because it predicts that hole punching will fail
   for this node."
  [{:keys [servers sock timeout-ms]}]
  (-> (js/Promise.all (clj->js (map #(reflexive {:server % :sock sock :timeout-ms timeout-ms})
                                    servers)))
      (.then (fn [results]
               (let [addrs (distinct (remove nil? (js->clj results)))]
                 {:candidates (vec addrs)
                  :symmetric? (> (count addrs) 1)})))))
