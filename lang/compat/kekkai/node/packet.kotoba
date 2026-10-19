(ns kekkai.node.packet
  "Pure raw-IP framing and signed-netmap route selection."
  (:require [kekkai.node.netmap :as netmap]))

(def magic [0x4b 0x54 0x50 0x01])
(def max-packet-bytes 65575)

(defn parse
  [bytes]
  (let [bytes (vec bytes)
        first-byte (first bytes)
        family (when first-byte (quot first-byte 16))]
    (case family
      4 (when (>= (count bytes) 20)
          (let [header-length (* 4 (mod first-byte 16))
                total (+ (* 256 (nth bytes 2)) (nth bytes 3))]
            (when (and (>= header-length 20)
                       (>= total header-length)
                       (<= total (count bytes))
                       (<= total max-packet-bytes))
              {:packet/family 4
               :packet/destination (subvec bytes 16 20)
               :packet/bytes (subvec bytes 0 total)})))
      6 (when (>= (count bytes) 40)
          (let [payload (+ (* 256 (nth bytes 4)) (nth bytes 5))
                total (+ 40 payload)]
            (when (and (<= total (count bytes))
                       (<= total max-packet-bytes))
              {:packet/family 6
               :packet/destination (subvec bytes 24 40)
               :packet/bytes (subvec bytes 0 total)})))
      nil)))

(defn- prefix-match?
  [address prefix bits]
  (and (int? bits)
       (<= 0 bits (* 8 (count address)))
       (= (count address) (count prefix))
       (let [whole (quot bits 8)
             remainder (mod bits 8)
             mask (when (pos? remainder)
                    (bit-and 0xff (bit-shift-left 0xff (- 8 remainder))))]
         (and (= (subvec address 0 whole) (subvec prefix 0 whole))
              (or (zero? remainder)
                  (= (bit-and (nth address whole) mask)
                     (bit-and (nth prefix whole) mask)))))))

(defn route-match?
  [packet route]
  (and (= (:packet/family packet) (:route/family route))
       (prefix-match? (:packet/destination packet)
                      (vec (:route/prefix route))
                      (:route/bits route))))

(defn route-peer
  "Longest-prefix match among authorized :tun peers. Ambiguous equal-length
  routes fail closed instead of choosing by collection order."
  [nm packet now]
  (let [self (get-in nm [:netmap/self :node/id])
        candidates
        (for [peer (:netmap/peers nm)
              :when (and (netmap/authorized? peer now)
                         (netmap/reachable? nm self (:node/id peer) :tun))
              route (:node/routes peer)
              :when (route-match? packet route)]
          {:peer (:node/id peer) :bits (:route/bits route)})
        longest (when (seq candidates) (apply max (map :bits candidates)))
        winners (filter #(= longest (:bits %)) candidates)]
    (when (= 1 (count winners))
      (:peer (first winners)))))

(defn inbound-authorized?
  [nm peer-id]
  (let [self (get-in nm [:netmap/self :node/id])]
    (or (netmap/reachable? nm peer-id self :tun)
        ;; Response traffic on an outbound edge is allowed, but this does not
        ;; grant the peer authority to originate an unrelated session.
        (netmap/reachable? nm self peer-id :tun))))

(defn frame [packet-bytes]
  (into magic packet-bytes))

(defn unframe [bytes]
  (let [bytes (vec bytes)]
    (when (and (<= 5 (count bytes) (+ 4 max-packet-bytes))
               (= magic (subvec bytes 0 4)))
      (parse (subvec bytes 4)))))
