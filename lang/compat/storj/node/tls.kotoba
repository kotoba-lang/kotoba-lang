(ns storj.node.tls
  "What a Storj TLS connection is, as data.

  `tlsopts.tlsConfig` is twenty lines and every one of them is a decision, so
  they live here rather than in whichever host builds the socket. A host that
  wrote `InsecureSkipVerify` for itself would be choosing, on its own, to stop
  checking certificates — and it would be *right* to, but only because of a
  reason that belongs in this library.

  ## Why a Storj peer turns off certificate verification

  Storage node certificates are self-signed by their operators. There is no
  certificate authority that could vouch for them and no hostname that means
  anything: a node is not `example.com`, it is 32 bytes of hash. So the whole
  X.509 trust apparatus is switched off and replaced by
  `storj.node.identity/admit-chain` — the chain must be internally consistent,
  the id derived from its CA must be the one that was dialled, and the proof
  of work must be there.

  Turning off verification and *not* replacing it is the failure this exists
  to prevent, which is why `verify-peer` is here and not left to callers.

  ## The whitelist runs one way

  `VerifyCAWhitelist` is registered with `ClientAdd`, so it applies when this
  peer is the one dialling: a node checks that a satellite's CA was signed by
  Storj's production CA. The reverse does not happen — a satellite cannot
  whitelist node CAs, since every operator self-signs their own.

  ## What is not here

  ALPN. `StorjApplicationProtocol` is set only on the QUIC path
  (`rpc/quic/connector.go`), not on TCP TLS, and advertising it on a TCP
  connection would be a difference from every other Storj peer for no reason."
  (:require [storj.node.identity :as ident]))

(def application-protocol
  "`tlsopts.StorjApplicationProtocol`. QUIC only — see the namespace docstring."
  "storj")

(def minimum-version
  "`tls.VersionTLS12`. Not a modern-practice choice on this library's part:
  it is what the network negotiates, and raising it here would refuse peers
  Storj itself accepts."
  :tls1.2)

(def default-peer-ca-whitelist
  "The production Storj network CA — `tlsopts.DefaultPeerCAWhitelist`.

  Public information, checked in rather than fetched: a whitelist that could
  change under the process it is protecting is not a whitelist."
  (str "-----BEGIN CERTIFICATE-----\n"
       "MIIBWzCCAQGgAwIBAgIRAK7f/E+PDEvB/TrUSaHxOEYwCgYIKoZIzj0EAwIwEDEO\n"
       "MAwGA1UEChMFU3RvcmowIhgPMDAwMTAxMDEwMDAwMDBaGA8wMDAxMDEwMTAwMDAw\n"
       "MFowEDEOMAwGA1UEChMFU3RvcmowWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr\n"
       "sDBAh7sr9eVZJUIFb79WK2qTcSKw/sP95JF5rCIJ5FvvwA/cx70VdW6IQjVhIaDY\n"
       "llQONAD90PeoOpqSyo+iozgwNjAOBgNVHQ8BAf8EBAMCAgQwEwYDVR0lBAwwCgYI\n"
       "KwYBBQUHAwEwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiEAzPdn\n"
       "5ZK9hIUm+0b7iBHfk1T/O7gpwGTmsSLps4cF6KgCIDhgQ4g2givMj5Khmuhnr/e7\n"
       "z6HlDVf3PJOQv1yZqg7W\n"
       "-----END CERTIFICATE-----\n"))

(defn whitelist-certificates
  "The DER certificates in a whitelist PEM. Defaults to Storj's own."
  ([] (whitelist-certificates default-peer-ca-whitelist))
  ([whitelist-pem] (ident/parse-chain-pem whitelist-pem)))

;; ── the config, as data ─────────────────────────────────────────────────────

(defn config
  "The `tls.Config` a Storj peer uses, as a map a host can read.

  `role` is `:client` or `:server`. Every key mirrors a field of
  `tlsopts.tlsConfig`, and the ones that look alarming are the point:

  - `:verify-certificates? false` is `InsecureSkipVerify: true`. There are no
    trusted roots to check against.
  - `:require-peer-certificate? true` on a server is `RequireAnyClientCert` —
    *any*, because the checking is done by `verify-peer` afterwards rather
    than by the TLS stack.
  - `:session-tickets? false` and `:dynamic-record-sizing? false` are
    `SessionTicketsDisabled` and `DynamicRecordSizingDisabled`. Neither is a
    security property; they are there because Storj sets them, and a
    connection that differs from every other Storj connection in observable
    ways is a connection that can be told apart from one."
  [{:keys [role]}]
  (when-not (#{:client :server} role)
    (throw (ex-info "storj.node.tls: role must be :client or :server" {:role role})))
  (cond-> {:minimum-version          minimum-version
           :verify-certificates?     false
           :session-tickets?         false
           :dynamic-record-sizing?   false}
    (= :server role) (assoc :require-peer-certificate? true)))

;; ── the verification that replaces it ───────────────────────────────────────

(defn- signed-by-any?
  [verifier whitelist peer-ca]
  (boolean (some (fn [ca-der]
                   (let [ca (ident/certificate ca-der)]
                     (ident/signed-by? verifier ca peer-ca)))
                 whitelist)))

(defn verify-peer
  "Decide whether a peer's certificate chain is acceptable on this connection.

  Returns `{:ok? bool :reasons [...] :node-id bytes :difficulty n}` — the
  shape `admit-chain` returns, with the whitelist reason folded in.

  `opts` takes `:verifier` (required), `:expected-node-id` when dialling a
  particular node, `:minimum-difficulty`, and `:whitelist` — a vector of CA
  DER certificates, or nil to skip the check. Pass
  `(whitelist-certificates)` when dialling a satellite; pass nil when
  accepting a connection, because there is nothing to whitelist a storage node
  against.

  The whitelist runs only after the chain itself is admitted. A chain that
  does not hang together has no CA worth comparing to anything, and the
  comparison costs a signature check per whitelisted authority."
  [chain {:keys [verifier whitelist] :as opts}]
  (let [admitted (ident/admit-chain chain opts)]
    (if (or (not (:ok? admitted)) (nil? whitelist))
      admitted
      (let [certs   (mapv #(if (map? %) % (ident/certificate %)) chain)
            peer-ca (get certs ident/ca-index)]
        (if (signed-by-any? verifier whitelist peer-ca)
          admitted
          (assoc admitted
                 :ok? false
                 :reasons [{:reason :ca-not-in-whitelist
                            :note "the peer CA was signed by no whitelisted authority"
                            :whitelist-size (count whitelist)}]))))))
