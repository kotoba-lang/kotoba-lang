(ns storj.node.pb
  "The Storj messages a storage node has to understand, and — the part that is
  easy to get wrong and impossible to debug — the exact bytes their signatures
  cover.

  Field numbers here were read out of the authoritative `.proto` files in
  `storj/common` (`pb/orders.proto`, `pb/piecestore2.proto`), not recalled.
  That distinction earned its place: a first pass written from memory had
  `limit` at 8, `action` at 9 and `satellite_signature` at 6, and all three
  are wrong. Nothing about those mistakes would have surfaced as anything but
  a signature that refuses to verify.

  ## Signing is not `remove the signature field`

  Storj does not sign the message it sends. `orders.proto` defines a second
  message for each signed type — `OrderLimitSigning`, `OrderSigning`,
  `PieceHashSigning` — with the same field numbers but different nullability,
  and `signing.EncodeOrderLimit` copies the wire message into it before
  marshalling. The comment in that file says why: gogoproto cannot round-trip
  `nullable=false` fields, so the signed form is a deliberately different
  serialization.

  What that means concretely, for a verifier:

  - The signature field is **absent**, not empty.
  - A timestamp that is present-but-zero on the wire is **dropped** for
    signing. The wire form declares those fields `nullable=false`, so gogo
    always emits them, even when unset; the signing form declares them
    nullable, so `if !limit.PieceExpiration.IsZero()` omits them. Keeping a
    zero timestamp is the difference between verifying and not.
  - The same holds for `deprecated_uplink_id` and `uplink_public_key`.
  - **Unknown fields are kept.** `EncodeOrderLimit` copies
    `XXX_unrecognized` across explicitly. A satellite running a newer schema
    than this node signs over fields this node has never heard of, and a
    decoder that dropped them would fail every signature. That is why
    `proto.wire` preserves them, and why this namespace works on decoded
    fields rather than a parsed struct.

  Fields are emitted in ascending field-number order, with anything outside
  the known schema last, which is what gogo's generated marshaller does."
  (:require [proto.wire :as w]))

;; ── enums ────────────────────────────────────────────────────────────────────

(def piece-action
  "orders.proto `PieceAction`."
  {0 :invalid 1 :put 2 :get 3 :get-audit 4 :get-repair 5 :put-repair
   6 :delete 7 :put-graceful-exit})

(def piece-hash-algorithm
  "orders.proto `PieceHashAlgorithm`."
  {0 :sha256 1 :blake3})

(def format-version
  "piecestore2.proto `PieceHeader.FormatVersion`."
  {0 :v0 1 :v1})

;; ── schemas ──────────────────────────────────────────────────────────────────
;;
;; number → name, for the fields this node reads. A field absent from a table
;; is not an error: it is an unknown field, and it is preserved.

(def order-limit
  {1  :serial-number
   2  :satellite-id
   3  :deprecated-uplink-id
   4  :storage-node-id
   5  :piece-id
   6  :limit
   7  :action
   8  :piece-expiration
   9  :order-expiration
   10 :satellite-signature
   11 :deprecated-satellite-address
   12 :order-creation
   13 :uplink-public-key
   14 :encrypted-metadata-key-id
   15 :encrypted-metadata})

(def order
  {1 :serial-number
   2 :amount
   3 :uplink-signature})

(def piece-hash
  {1 :piece-id
   2 :hash
   3 :signature
   4 :piece-size
   5 :timestamp
   6 :hash-algorithm})

(def piece-header
  {1 :format-version
   2 :hash
   3 :creation-time
   4 :signature
   5 :order-limit
   6 :hash-algorithm})

(def check-in-request
  "contact.proto `CheckInRequest`. Built by `storj.node.contact`; here so the
  numbers live with every other schema rather than beside their one caller."
  {1 :address
   2 :version
   3 :capacity
   4 :operator
   5 :noise-key-attestation
   6 :debounce-limit
   7 :features
   8 :signed-tags})

(def check-in-response
  "contact.proto `CheckInResponse`.

  `ping_node_success` is the satellite saying whether it could dial the node
  *back*. A response with it false is a call that worked and an introduction
  that did not."
  {1 :ping-node-success
   2 :ping-error-message
   3 :ping-node-success-quic
   4 :node-tag-success
   5 :node-tag-error-message
   6 :hashstore-settings})

(def node-version
  "node.proto `NodeVersion`."
  {1 :version 2 :commit-hash 3 :timestamp 4 :release})

(def node-capacity
  "node.proto `NodeCapacity`. Field 1 is `free_bandwidth`, deprecated."
  {1 :deprecated-free-bandwidth 2 :free-disk})

(def node-operator
  "node.proto `NodeOperator`."
  {1 :email 2 :wallet 3 :wallet-features})

(defn- number-of [schema k]
  (or (some (fn [[n name]] (when (= name k) n)) schema)
      (throw (ex-info "storj.node.pb: no such field" {:field k}))))

;; ── reading ──────────────────────────────────────────────────────────────────

(defn get-field
  "The last field named `k`, as a `proto.wire` field map, or nil."
  [msg schema k]
  (w/field msg (number-of schema k)))

(defn get-bytes  [msg schema k] (w/bytes-value  (get-field msg schema k)))
(defn get-varint [msg schema k] (w/varint-value (get-field msg schema k)))
(defn get-msg    [msg schema k] (w/message-value (get-field msg schema k)))

(defn get-enum
  "A varint field mapped through an enum table. Unknown values come back as
  `[:unknown n]` rather than nil, so a caller cannot confuse `an action this
  build does not know` with `no action given` — the first must be refused, the
  second is a malformed limit."
  [msg schema k table]
  (when-let [v (get-varint msg schema k)]
    (get table v [:unknown v])))

(defn enum-value
  "The number an enum keyword travels as — the inverse of `get-enum`.

  Here rather than beside a caller because the table is here: an inverse
  built next to whoever needs one is a second reading of the same enum, and
  the two only have to disagree once. Throws on a keyword the table does not
  name, because the alternative is writing a field that decodes to something
  else."
  [table k]
  (or (some (fn [[n kw]] (when (= kw k) n)) table)
      (throw (ex-info "storj.node.pb: not a value of this enum"
                      {:keyword k :table table}))))

;; ── zero-ness, as the Go encoder defines it ─────────────────────────────────

(def go-zero-timestamp
  "The payload of a `google.protobuf.Timestamp` holding Go's zero time.

  Not the epoch. `time.Time{}` is midnight, 1 January of year 1, which is
  -62135596800 seconds from 1970 — encoded, as protobuf encodes every negative
  int64, as a ten-byte two's-complement varint. The wire form of an
  `OrderLimit` declares its timestamps `nullable=false`, so gogo emits this
  even for a field the satellite never set; the signing form declares them
  nullable, so `IsZero()` drops them.

  Measured, not derived: this is the tail of `:order-limit-wire` in
  `testdata/gen_vectors.go`'s output, where `PieceExpiration` was deliberately
  left unset. Treating an unset timestamp as `seconds = 0` — the obvious
  reading of the .proto, and the first thing this library did — drops the
  wrong fields and every satellite signature fails."
  [0x08 0x80 0x92 0xb8 0xc3 0x98 0xfe 0xff 0xff 0xff 0x01])

(defn- zero-timestamp?
  "Whether a timestamp field is one Go would call zero: absent, empty, or
  carrying the year-1 instant above."
  [f]
  (let [payload (some-> f w/bytes-value vec)]
    (or (nil? payload)
        (empty? payload)
        (= payload go-zero-timestamp))))

(defn- zero-bytes? [f]
  (let [v (some-> f w/bytes-value)]
    (or (nil? v) (empty? v) (every? zero? v))))

;; ── the signed forms ─────────────────────────────────────────────────────────

(defn- emit
  "Fields in ascending field-number order, unknown fields last — gogo's
  marshalling order."
  [schema fields]
  (let [known? #(contains? schema (:field-number %))]
    (vec (concat (sort-by :field-number (filterv known? fields))
                 (filterv (complement known?) fields)))))

(defn- drop-when
  "Remove field `k` from `fields` when `empty-pred` says the value it holds is
  one Go would have left unset."
  [fields schema k empty-pred]
  (let [n (number-of schema k)]
    (if (empty-pred (w/field fields n))
      (w/remove-field fields n)
      fields)))

(defn encode-order-limit-for-signing
  "The bytes a satellite signed when it issued this order limit.

  Mirrors `signing.EncodeOrderLimit` field for field: the signature is
  removed, `deprecated_uplink_id`, `uplink_public_key` and each of the three
  timestamps are removed when zero, and everything else — including fields
  this build has never heard of — is kept."
  [msg]
  (-> msg
      (w/remove-field (number-of order-limit :satellite-signature))
      (drop-when order-limit :deprecated-uplink-id zero-bytes?)
      (drop-when order-limit :uplink-public-key    zero-bytes?)
      (drop-when order-limit :piece-expiration     zero-timestamp?)
      (drop-when order-limit :order-expiration     zero-timestamp?)
      (drop-when order-limit :order-creation       zero-timestamp?)
      (->> (emit order-limit))
      w/encode))

(defn encode-order-for-signing
  "The bytes an uplink signed for this order. `signing.EncodeOrder` keeps only
  the serial number and the amount — plus unknown fields."
  [msg]
  (-> msg
      (w/remove-field (number-of order :uplink-signature))
      (->> (emit order))
      w/encode))

(defn encode-piece-hash-for-signing
  "The bytes signed over a piece hash. `signing.EncodePieceHash` drops the
  signature, and drops the timestamp when it is zero."
  [msg]
  (-> msg
      (w/remove-field (number-of piece-hash :signature))
      (drop-when piece-hash :timestamp zero-timestamp?)
      (->> (emit piece-hash))
      w/encode))

;; ── timestamps ───────────────────────────────────────────────────────────────

(defn timestamp-seconds
  "Seconds since the epoch from a `google.protobuf.Timestamp` field.

  Returns nil when the field is absent or holds Go's zero time — the caller
  should read that as `not set`, which is what the satellite meant.

  Nanoseconds (field 2) are ignored deliberately: every decision this library
  makes with a timestamp is an expiry comparison, and sub-second precision
  there would be false precision about clock skew across a network.

  Negative timestamps other than the zero time throw. They cannot occur in a
  live order limit — every one is a deadline in the near future — and decoding
  a ten-byte two's-complement varint into a JavaScript double would silently
  lose the low bits. Failing closed is the correct behaviour for a value that
  decides whether an order has expired."
  [f]
  (when-not (zero-timestamp? f)
    (let [inner (w/message-value f)]
      (or (w/varint-value (w/field inner 1)) 0))))

;; ── the unary piecestore surface ────────────────────────────────────────────
;;
;; Upload, Download and RetainBig are streams and are not here. What is left
;; is what a satellite asks a node between transfers.

(def exists-request
  "piecestore2.proto `ExistsRequest`. `piece_ids` repeats."
  {1 :piece-ids})

(def exists-response
  "piecestore2.proto `ExistsResponse`.

  `missing` is a list of **indices into the request**, not piece ids — a node
  that answered with ids would be answering a different question, and the
  satellite would read the first varint of an id as an index."
  {1 :missing})

(def delete-pieces-request
  "piecestore2.proto `DeletePiecesRequest`."
  {1 :piece-ids})

(def delete-pieces-response
  "piecestore2.proto `DeletePiecesResponse`. `unhandled_count` is how many the
  node did not get to, not how many it deleted."
  {1 :unhandled-count})

(def retain-request
  "piecestore2.proto `RetainRequest`."
  {1 :creation-date 2 :filter 3 :hash-algorithm 4 :hash})

;; ── the streaming piecestore surface ────────────────────────────────────────

(def piece-upload-request
  "piecestore2.proto `PieceUploadRequest`.

  One message type for four different messages. The first carries `limit`, the
  middle ones carry `order` and `chunk`, the last carries `done` — and nothing
  in the encoding says which is which. A node that reads a `limit` out of the
  fifth message it receives is being told to forget the one it admitted."
  {1 :limit
   2 :order
   3 :chunk
   4 :done
   5 :hash-algorithm})

(def piece-upload-response
  {1 :done 2 :node-certchain})

(def upload-chunk
  "`PieceUploadRequest.Chunk` — where the bytes go and what they are."
  {1 :offset 2 :data})

(def piece-download-request
  "piecestore2.proto `PieceDownloadRequest`."
  {1 :limit 2 :order 3 :chunk 4 :maximum-chunk-size})

(def download-chunk
  "`PieceDownloadRequest.Chunk` — how much of the piece is wanted."
  {1 :offset 2 :chunk-size})

(def piece-download-response
  {1 :chunk 2 :hash 3 :limit 4 :restored-from-trash})

(def download-response-chunk
  "`PieceDownloadResponse.Chunk` — the bytes going back."
  {1 :offset 2 :data})
