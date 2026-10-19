(ns kotoba.net.libp2p.identify
  "`/ipfs/id/1.0.0` — what a peer says it is, once the connection already knows
  who it is.

  Identify runs *after* the security handshake and adds nothing to
  authentication: the peer id was proven by the Noise payload signature, and
  everything here is self-reported. What it carries is operational rather than
  evidentiary -- which addresses the peer believes it listens on, which
  protocols it speaks, which address it observed us at -- and treating any of it
  as a fact about the peer rather than a claim by the peer is the classic
  mistake.

  The one genuinely useful check is cross-checking: the `public-key` field must
  hash to the peer id the handshake already established. A peer that sends a
  different key is either broken or trying something, and either way its other
  claims are worthless."
  (:require [protobuf.wire :as pb]))

(def protocol "/ipfs/id/1.0.0")

(def schema
  "The field numbers are the spec's, and they are not in a memorable order:
  `publicKey` is 1, `protocols` is 3, `protocolVersion` is 5 and `agentVersion`
  is 6. Getting them wrong does not fail -- protobuf skips fields it has no
  entry for -- so a mis-numbered schema silently returns nil for everything it
  misnamed and a short list for what it did not."
  {1 {:name :public-key :type :bytes}
   2 {:name :listen-addrs :type :bytes :repeated true}
   3 {:name :protocols :type :string :repeated true}
   4 {:name :observed-addr :type :bytes}
   5 {:name :protocol-version :type :string}
   6 {:name :agent-version :type :string}})

(defn parse
  "Decode an identify message. Returns a map with the claims as claims."
  [octets]
  (let [message (pb/decode schema octets)]
    {:public-key (:public-key message)
     :protocol-version (:protocol-version message)
     :agent-version (:agent-version message)
     :protocols (vec (or (:protocols message) []))
     :listen-addrs (vec (or (:listen-addrs message) []))
     :observed-addr (:observed-addr message)}))

(defn consistent-with?
  "Whether identify's `public-key` is the one the handshake authenticated.

  The only claim in an identify message that can be checked at all."
  [identified handshake-identity-key-protobuf]
  (and (some? (:public-key identified))
       (= (vec (:public-key identified)) (vec handshake-identity-key-protobuf))))

(defn speaks?
  "Whether the peer listed PROTOCOL. A claim, and useful only as a hint: the
  authoritative answer is what multistream says when you propose it."
  [identified protocol-id]
  (boolean (some #{protocol-id} (:protocols identified))))
