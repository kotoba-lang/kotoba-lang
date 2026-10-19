(ns storj.node.piece
  "Where a piece lives on disk, and how its header is framed.

  A storage node keeps opaque blobs. It never sees plaintext and never
  performs erasure coding — the uplink encrypts and erasure-codes before any
  node is contacted, and a node holds one share of a segment with no idea what
  the others contain. That is the whole reason the node side is tractable in a
  library like this while the uplink side is not (see the README).

  Two encodings that look interchangeable and are not:

  - A **piece ID printed for humans** is uppercase base32 — `PieceID.String()`
    uses the standard RFC 4648 table.
  - A **path on disk** is lowercase base32. `filestore.PathEncoding` is
    declared as `base32.NewEncoding(\"abcdefghijklmnopqrstuvwxyz234567\")`.

  Both were checked against Storj's own output rather than assumed: the
  vectors in `testdata/gen_vectors.go` print `PieceID.String()` in uppercase,
  and `storagenode/blobstore/filestore/dir.go` is where the lowercase table
  comes from. Getting them the wrong way round puts every piece in a
  directory the node will never look in again."
  (:require [clojure.string :as str]
            [storj.node.bytes :as b]
            [storj.node.pb :as pb]
            [proto.wire :as w]))

(def header-reserved-area
  "Bytes reserved at the front of a V1 piece file for its header.

  `V1PieceHeaderReservedArea` in `storagenode/pieces/readwrite.go`. The header
  is written at the start rather than appended so that a truncated file is
  still identifiable, which is a decision worth keeping when reimplementing:
  it is what makes a partially-written piece recoverable instead of garbage."
  512)

(def header-framing-size
  "The header's length prefix — two bytes, big-endian. Protobuf messages are
  not self-delimiting, and the reserved area is zero-padded, so without this
  a reader cannot tell padding from message."
  2)

(defn- fail [msg data]
  (throw (ex-info (str "storj.node.piece: " msg) data)))

;; ── identifiers ──────────────────────────────────────────────────────────────

(defn id->string
  "A piece or satellite ID as Storj prints it: uppercase base32, no padding."
  [id]
  (str/upper-case (b/base32-encode id)))

(defn- path-encode
  "Lowercase base32, no padding — `filestore.PathEncoding`."
  [id]
  (b/base32-encode id))

;; ── blob paths ───────────────────────────────────────────────────────────────

(def format-suffix
  "Piece files are named by storage format version. V0 has no suffix at all,
  which is why a V0 file cannot be told from a stray file by name alone —
  `v0PieceFileSuffix` really is the empty string."
  {:v0 "" :v1 ".sj1"})

(defn blob-path
  "The path of a piece under the blobs directory.

      <blobs>/<base32 satellite-id>/<key[0:2]>/<key[2:]><suffix>

  The two-character directory is a fan-out so a satellite's blobs do not land
  in one directory with millions of siblings. Storj pads a key shorter than
  three characters with `11` so the split is always possible; that cannot
  happen for a real 32-byte piece ID, but it is reproduced here because the
  path function is also used for shorter keys."
  ([satellite-id piece-id] (blob-path satellite-id piece-id :v1))
  ([satellite-id piece-id version]
   (let [suffix (or (format-suffix version)
                    (fail "unknown storage format version" {:version version}))
         ns-dir (path-encode satellite-id)
         key    (path-encode piece-id)
         key    (if (< (count key) 3) (str "11" key) key)]
     (str ns-dir "/" (subs key 0 2) "/" (subs key 2) suffix))))

;; ── the V1 header ────────────────────────────────────────────────────────────

(defn encode-header
  "Frame an encoded `PieceHeader` into the 512-byte reserved area.

  Returns exactly `header-reserved-area` bytes: a big-endian length, the
  message, then zero padding."
  [header-msg]
  (let [body (w/encode header-msg)
        n    (count body)]
    (when (> n (- header-reserved-area header-framing-size))
      (fail "piece header does not fit in the reserved area"
            {:size n :available (- header-reserved-area header-framing-size)}))
    (into (into [(bit-and (bit-shift-right n 8) 0xff) (bit-and n 0xff)] body)
          (repeat (- header-reserved-area header-framing-size n) 0))))

(defn decode-header
  "Read a `PieceHeader` out of the first 512 bytes of a V1 piece file.

  Rejects a length that does not fit the reserved area rather than reading
  past it — a damaged or hostile file should not be able to steer a read into
  the piece body and have the result parsed as a header."
  [bs]
  (let [v (b/->ints bs)]
    (when (< (count v) header-reserved-area)
      (fail "short read: a V1 piece file starts with a 512-byte header area"
            {:got (count v)}))
    (let [n (+ (* 256 (nth v 0)) (nth v 1))]
      (when (> n (- header-reserved-area header-framing-size))
        (fail "piece header framing claims an impossible size" {:claimed n}))
      (w/decode (subvec v header-framing-size (+ header-framing-size n))))))

(defn body-offset
  "Where the piece data starts, for a given storage format version. V0 files
  have no header area at all — the header lives in a database instead."
  [version]
  (case version
    :v0 0
    :v1 header-reserved-area
    (fail "unknown storage format version" {:version version})))

(defn header-for
  "The `PieceHeader` fields for a piece this node has just accepted.

  What makes a V1 file self-describing: the uplink's hash, the uplink's
  *signature* over that hash, and the order limit that authorised it. A node
  that restarts has no memory of the upload, and this is how it can still
  answer an audit — the header carries the uplink's own attestation, not the
  node's word for it.

  Which is why an unverified hash must not be written here. The signature in
  this header is the one thing a later reader cannot check for itself without
  the limit's key, so writing one that was never checked turns `finish-upload`
  reporting `:hash-verified? false` into a file that looks proven. The caller
  decides; `storj.node.transfer` refuses.

  `creation-time` is seconds, and is the caller's — this namespace reads no
  clock, for the same reason `storj.node.orders` does not."
  [{:keys [hash hash-algorithm signature order-limit created-at]}]
  (cond-> [(w/varint-field 1 (pb/enum-value pb/format-version :v1))]
    hash        (conj (w/bytes-field 2 (vec hash)))
    created-at  (conj (w/message-field 3 [(w/varint-field 1 created-at)]))
    signature   (conj (w/bytes-field 4 (vec signature)))
    order-limit (conj (w/message-field 5 order-limit))
    hash-algorithm
    (conj (w/varint-field 6 (pb/enum-value pb/piece-hash-algorithm hash-algorithm)))))

(defn v1-file
  "A complete V1 piece file: the 512-byte header area, then the body."
  [header-fields body]
  (into (encode-header header-fields) (vec body)))

(defn header-fields
  "The interesting fields of a decoded `PieceHeader`, as data."
  [header-msg]
  {:format-version (pb/get-enum header-msg pb/piece-header :format-version pb/format-version)
   :hash           (pb/get-bytes header-msg pb/piece-header :hash)
   :hash-algorithm (pb/get-enum header-msg pb/piece-header :hash-algorithm pb/piece-hash-algorithm)
   :creation-time  (pb/timestamp-seconds (pb/get-field header-msg pb/piece-header :creation-time))
   :signature      (pb/get-bytes header-msg pb/piece-header :signature)
   :order-limit    (pb/get-msg header-msg pb/piece-header :order-limit)})
