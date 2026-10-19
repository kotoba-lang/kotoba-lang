(ns noise.patterns
  "Handshake patterns as data (Noise rev 34 §7).

  `IK` is the one that matters for an overlay data plane, and it is the pattern
  WireGuard uses: the initiator already knows the responder's static public key
  (for us: from the netmap the control plane published), so the initiator can
  authenticate the responder and transmit its own static key **encrypted** in the
  very first message. One round trip, mutual authentication, initiator identity
  hidden from a passive observer.

  `XX` is included for the bootstrap case where the responder's static key is not
  yet known (three messages, both statics transmitted), and `NN` for anonymous
  probe traffic that only needs confidentiality — the disco/ping path, where
  authenticating a NAT-probe reply is the relay's job, not the packet's."
  (:refer-clojure :exclude [name]))

(def patterns
  {:IK {:pre-messages {:responder [:s]}
        :messages [[:e :es :s :ss]
                   [:e :ee :se]]}
   :XX {:pre-messages {}
        :messages [[:e]
                   [:e :ee :s :es]
                   [:s :se]]}
   :NN {:pre-messages {}
        :messages [[:e]
                   [:e :ee]]}})

(defn pattern [pattern-name]
  (or (get patterns pattern-name)
      (throw (ex-info "unknown noise handshake pattern"
                      {:pattern pattern-name :known (vec (keys patterns))}))))

(defn requires-remote-static?
  "Does the initiator need `rs` up front? (IK does — that is the whole point.)"
  [pattern-name]
  (boolean (some #{:s} (get-in (pattern pattern-name) [:pre-messages :responder]))))

(defn message-count [pattern-name]
  (count (:messages (pattern pattern-name))))
