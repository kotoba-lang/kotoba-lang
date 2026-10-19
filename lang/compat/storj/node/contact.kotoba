(ns storj.node.contact
  "Check-in: the first thing a node says, and the only thing it has to say to
  be on the network.

  A node introduces itself to a satellite with `/node.Node/CheckIn`, and the
  satellite answers by dialling it back. Everything else a node does — storing
  pieces, settling orders — happens only if this succeeded.

  ## Nothing here is signed

  `CheckInRequest` has no signature field, which looks like an omission and is
  not. The satellite already knows exactly who is calling, because the
  connection underneath is mutual TLS and the peer's chain named a node id
  (`storj.node.tls/verify-peer`). Adding a signature would authenticate the
  same thing twice. It is also why a node that cannot present its identity
  cannot check in at all — there is no unauthenticated path in.

  ## What the response actually says

  `ping_node_success` is the satellite reporting whether it could **dial the
  node back** at the address the node claimed. A response can arrive with
  `ok?` false and no error at all: the call worked, the introduction did not.
  Reading only the transport result would report that as success, which is the
  one mistake this namespace exists to make hard — hence `admitted?` rather
  than a bare truthy check.

  ## Address

  The address is what the node claims it is reachable at, and the satellite
  believes nothing about it until the dial-back succeeds."
  (:require [proto.wire :as w]
            [storj.node.pb :as pb]))

(def rpc
  "`/contact.Node/CheckIn`.

  Two services live in `contact.proto`: `Node`, which a storage node calls on
  a satellite, and `Contact`, which a satellite calls back on the node. Both
  are in the `contact` package — the service is `Node` but the package is not.

  This said `/node.Node/CheckIn` until a real satellite answered
  `protocol error: unknown rpc`. Nothing had caught it: the message bytes were
  verified against `storj.io/common`, but the path they travel under was only
  ever seen by this project's own Go harness, which serves whatever name it is
  given. A stub that accepts everything cannot report a name that is wrong."
  "/contact.Node/CheckIn")

(def ping-rpc
  "What a satellite calls on the node, in the other direction. Recorded so the
  pair is visible; nothing here serves it."
  "/contact.Contact/PingNode")

(defn node-version
  "`node.NodeVersion`.

  **The timestamp is always emitted**, even when the caller has none.
  `node.proto` declares it `(gogoproto.stdtime) = true` and
  `(gogoproto.nullable) = false`, so gogo marshals a `time.Time` value rather
  than a pointer — and a zero `time.Time` is still a value. It goes out as
  Go's zero time, year 1, not the epoch.

  This was got wrong first: omitting the field produced a request eleven bytes
  shorter than the one `pb.Marshal` produces, which is the same trap the
  order-limit timestamps set and the same one this repo's own generator
  comment warned about."
  [{:keys [version commit-hash timestamp release?]}]
  (cond-> []
    version     (conj (w/string-field 1 version))
    commit-hash (conj (w/string-field 2 commit-hash))
    :always     (conj (w/bytes-field 3 (or timestamp pb/go-zero-timestamp)))
    (some? release?) (conj (w/varint-field 4 (if release? 1 0)))))

(defn node-capacity
  "`node.NodeCapacity`. `free_bandwidth` is field 1 and deprecated; a node
  that fills it is telling a satellite something no satellite reads."
  [{:keys [free-disk]}]
  (cond-> []
    free-disk (conj (w/varint-field 2 free-disk))))

(defn node-operator
  "`node.NodeOperator` — who to pay and who to email."
  [{:keys [email wallet wallet-features]}]
  (cond-> []
    email  (conj (w/string-field 1 email))
    wallet (conj (w/string-field 2 wallet))
    :always (into (map #(w/string-field 3 %)) (or wallet-features []))))

(defn check-in-request
  "The bytes of a `CheckInRequest`.

  Only what a node has a reason to send. `noise_key_attestation` and
  `signed_tags` are absent rather than empty: an empty embedded message is a
  field that was set, and a satellite reading one is being told the node has
  a noise key when it does not."
  [{:keys [address version capacity operator debounce-limit features]}]
  (w/encode
   (cond-> []
     address        (conj (w/string-field 1 address))
     version        (conj (w/message-field 2 (node-version version)))
     capacity       (conj (w/message-field 3 (node-capacity capacity)))
     operator       (conj (w/message-field 4 (node-operator operator)))
     debounce-limit (conj (w/varint-field 6 debounce-limit))
     features       (conj (w/varint-field 7 features)))))

(defn read-check-in-request
  "A decoded `CheckInRequest` as a map — the satellite's half of check-in.

  Here so that a counterparty can be written without a second reading of
  `contact.proto`: this and `check-in-request` share `pb/check-in-request`, so
  a field number that is wrong is wrong for both and shows up as a round trip
  that does not close, rather than as two implementations that quietly agree
  with each other and disagree with Storj.

  `:address` is what the node *claims*; nothing here believes it. Deciding
  whether it is reachable means dialling it."
  [msg]
  {:address  (some-> (pb/get-bytes msg pb/check-in-request :address) w/bytes->utf8)
   :version  (some-> (pb/get-msg msg pb/check-in-request :version)
                     (as-> m {:version (some-> (pb/get-bytes m pb/node-version :version)
                                               w/bytes->utf8)
                              :release? (= 1 (pb/get-varint m pb/node-version :release))}))
   :capacity (some-> (pb/get-msg msg pb/check-in-request :capacity)
                     (as-> m {:free-disk (pb/get-varint m pb/node-capacity :free-disk)}))
   :operator (some-> (pb/get-msg msg pb/check-in-request :operator)
                     (as-> m {:email  (some-> (pb/get-bytes m pb/node-operator :email)
                                              w/bytes->utf8)
                              :wallet (some-> (pb/get-bytes m pb/node-operator :wallet)
                                              w/bytes->utf8)}))})

(defn check-in-response
  "The bytes of a `CheckInResponse`.

  `false` is written as an absent field, not as a zero: proto3 does not emit
  scalar defaults, so a satellite that answers \"no\" sends a response that is
  mostly empty — which is why `read-check-in-response` has to read absence as
  false rather than as missing information."
  [{:keys [ping-node-success ping-error-message ping-node-success-quic
           node-tag-success node-tag-error-message]}]
  (w/encode
   (cond-> []
     ping-node-success      (conj (w/varint-field 1 1))
     ping-error-message     (conj (w/string-field 2 ping-error-message))
     ping-node-success-quic (conj (w/varint-field 3 1))
     node-tag-success       (conj (w/varint-field 4 1))
     node-tag-error-message (conj (w/string-field 5 node-tag-error-message)))))

(defn read-check-in-response
  "A decoded `CheckInResponse` as a map.

  Booleans come back as booleans rather than 0/1 because every one of them is
  a decision the caller has to make, and `(if (get r :ping-node-success) ...)`
  is true for `0`."
  [msg]
  {:ping-node-success      (= 1 (pb/get-varint msg pb/check-in-response :ping-node-success))
   :ping-error-message     (some-> (pb/get-bytes msg pb/check-in-response :ping-error-message)
                                   w/bytes->utf8)
   :ping-node-success-quic (= 1 (pb/get-varint msg pb/check-in-response :ping-node-success-quic))
   :node-tag-success       (= 1 (pb/get-varint msg pb/check-in-response :node-tag-success))
   :node-tag-error-message (some-> (pb/get-bytes msg pb/check-in-response :node-tag-error-message)
                                   w/bytes->utf8)})

(defn admitted?
  "Whether the satellite could reach this node back.

  Named rather than left as `:ping-node-success` because the interesting case
  is the one that looks like success from the transport's point of view: the
  call returned, the response decoded, and the node is not on the network."
  [response]
  (boolean (:ping-node-success response)))

(defn refusal
  "Why the check-in did not take, or nil if it did.

  A satellite that could not dial back says so in `ping_error_message`, and a
  node that reports `unknown` when it was handed a reason is a node whose
  operator cannot fix it."
  [response]
  (when-not (admitted? response)
    {:reason :ping-failed
     :message (or (not-empty (:ping-error-message response))
                  "the satellite gave no reason")}))
