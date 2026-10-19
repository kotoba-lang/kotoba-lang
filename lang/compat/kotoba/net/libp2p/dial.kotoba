(ns kotoba.net.libp2p.dial
  "Dial a libp2p peer over TCP and speak a protocol to it.

  This is the assembly point: multiaddr in, an authenticated muxed connection
  out. Everything it uses was already here -- the addresses, the negotiation,
  the handshake pattern, the muxer -- and none of it could reach a peer, because
  nothing opened a socket and ran them in order.

  Crypto is injected rather than chosen here. The identity key is the caller's
  (it is the peer id), and the Noise primitives come from a provider, so this
  namespace holds no keys and no algorithms."
  (:require [kotoba.net.libp2p.connection :as connection]
            [kotoba.net.libp2p.handshake :as handshake]
            [kotoba.net.libp2p.identify :as identify]
            [kotoba.net.libp2p.socket :as socket]
            [multiformats.core :as mf]
            [noise.provider.jvm :as provider]
            [noise.suite :as suite]))

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn noise-suite
  "The suite libp2p uses: 25519 / ChaChaPoly / SHA256.

  SHA256, not BLAKE2s -- the default in `noise.suite` is WireGuard's choice and
  a handshake against a libp2p peer with the wrong hash fails as an
  authentication error, which is the least informative way to discover a
  configuration mismatch."
  []
  (suite/suite {:dh-generate provider/dh-generate
                :dh provider/dh
                :aead-encrypt provider/aead-encrypt
                :aead-decrypt provider/aead-decrypt}
               {:hash :sha256}))

(defn dial!
  "Connect to ADDRESS, complete the stack, and return a live connection.

  Returns `{:peer :session :secure :close!}`. `:peer` is what the handshake
  *proved*, not what the peer said."
  [address {:keys [identity-public-key sign-fn verify-fn connect-timeout-ms
                   read-timeout-ms]}]
  (let [target (or (socket/dial-address address)
                   (fail! :dial/unsupported-address
                          {:address address
                           :hint "only tcp with ip4/ip6/dns is dialable here"}))
        connected (socket/connect! (:host target) (:port target)
                                   (cond-> {}
                                     connect-timeout-ms (assoc :connect-timeout-ms connect-timeout-ms)
                                     read-timeout-ms (assoc :read-timeout-ms read-timeout-ms)))]
    (try
      (let [suite (noise-suite)
            static ((:dh-generate suite))
            {:keys [port peer]} (connection/handshake!
                                 (:port connected)
                                 {:suite suite
                                  :static static
                                  :identity-public-key identity-public-key
                                  :sign-fn sign-fn
                                  :verify-fn verify-fn})
            session (connection/open! port)
            actual-peer-id (handshake/peer-id mf/sha256 (:identity-key-protobuf peer))
            expected-peer-id (some->> (:peer-id target) mf/base58btc-decode
                                      (mapv #(bit-and % 0xff)))
            actual-peer-id (mapv #(bit-and % 0xff) actual-peer-id)]
        (when (and expected-peer-id (not= actual-peer-id expected-peer-id))
          (fail! :dial/peer-id-mismatch
                 {:expected (:peer-id target)
                  :actual (mf/base58btc actual-peer-id)}))
        {:peer peer
         :expected-peer-id (:peer-id target)
         :address address
         :secure port
         :session session
         :close! (:close! connected)})
      (catch Exception error
        ((:close! connected))
        (throw error)))))

(defn identify!
  "Run `/ipfs/id/1.0.0` on a live connection.

  The result is the peer's own claims; only `public-key` is checkable, and it is
  checked here against what the handshake proved."
  [{:keys [secure session peer]}]
  (let [stream (connection/stream! secure session identify/protocol)
        length (loop [shift 0 acc 0]
                 (let [b (nth ((:read! stream) 1) 0)
                       acc (bit-or acc (bit-shift-left (bit-and b 0x7F) shift))]
                   (if (zero? (bit-and b 0x80)) acc (recur (+ shift 7) acc))))
        identified (identify/parse ((:read! stream) length))]
    (assoc identified
           :consistent-with-handshake?
           (identify/consistent-with? identified (:identity-key-protobuf peer)))))
