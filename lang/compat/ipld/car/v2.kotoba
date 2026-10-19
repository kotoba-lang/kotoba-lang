(ns ipld.car.v2
  "CARv2 — a CARv1 payload wrapped in an 11-byte pragma and a 40-byte header,
  optionally followed by an index.

      pragma   11 bytes   0x0a a1 67 'version' 02
      header   40 bytes   characteristics(16) | data-offset | data-size | index-offset
      data     CARv1
      index    varint(codec) || payload      (absent when index-offset = 0)

  This is the layer the kotoba stack actually needs, and the reason is one
  number: a block store keyed one-object-per-CID pays one round trip per
  block. A pack pays one per *range*, and the header says exactly which range.

  `pack` therefore returns file-absolute offsets for every block it wrote, and
  `locate` turns an index plus a CID into a `Range:` header. Nothing here
  streams and nothing here fetches — the byte source is the caller's, so the
  same code runs in a Worker, a browser and a JVM test."
  (:require [ipld.car.bytes :as b]
            [ipld.car :as car]
            [ipld.car.index :as idx]))

(def ^:const version 2)
(def ^:const pragma-length 11)
(def ^:const header-length 40)
(def ^:const data-offset (+ pragma-length header-length)) ; 51

(def pragma
  "The fixed 11-byte CARv2 pragma: a length-prefixed DAG-CBOR `{version: 2}`."
  (b/->bytes [0x0a 0xa1 0x67 0x76 0x65 0x72 0x73 0x69 0x6f 0x6e 0x02]))

(defn header
  "The 40-byte CARv2 header. `characteristics` is a 128-bit bitfield; the only
  bit the format defines is bit 63 of the first word (\"fully indexed\"), and
  this library leaves it clear because it does not promise that."
  [{:keys [data-offset data-size index-offset]}]
  (b/concat [(b/->bytes (repeat 16 0))
             (b/u64-le data-offset)
             (b/u64-le data-size)
             (b/u64-le (or index-offset 0))]))

(defn parse-header
  "Read pragma + header.

  Returns `{:characteristics :data-offset :data-size :index-offset}`.

  `:characteristics` is the raw 16-byte bitfield. It is returned rather than
  skipped because it is the archive telling a reader that it is not an
  ordinary one, and a reader that never looks cannot decline. `header` above
  writes it clear precisely because this library promises no characteristic;
  see `no-characteristics?` for the matching read side."
  [buf]
  (when (< (b/bcount buf) data-offset)
    (throw (ex-info "car: buffer shorter than a CARv2 pragma + header"
                    {:type :car/truncated :size (b/bcount buf)})))
  (when-not (b/equal? (b/slice buf 0 pragma-length) pragma)
    (throw (ex-info "car: not a CARv2 (pragma mismatch)"
                    {:type :car/not-carv2})))
  {:characteristics (b/slice buf pragma-length (+ pragma-length 16))
   :data-offset (b/read-u64-le buf (+ pragma-length 16))
   :data-size (b/read-u64-le buf (+ pragma-length 24))
   :index-offset (b/read-u64-le buf (+ pragma-length 32))})

(defn no-characteristics?
  "Is every characteristic bit clear?

  This library implements no characteristic, so this is the whole of what it
  can honestly say about the field: either the archive asks for nothing
  special, or it asks for something this reader does not implement. Naming
  which bit was set would be a claim about semantics this code does not
  have -- a caller that wants one reads `:characteristics` itself. The point
  is that ignoring a set bit and understanding it must not look the same."
  [{:keys [characteristics]}]
  (and (some? characteristics)
       (every? zero? (map #(b/bget characteristics %) (range (b/bcount characteristics))))))

;; ── writing ──────────────────────────────────────────────────────────────────

(defn pack
  "Pack `blocks` into one CARv2.

  Returns

      {:bytes         the whole archive
       :data-offset   51
       :data-size     length of the CARv1 payload
       :index-offset  0 when `:index? false`
       :entries       [{:cid :file-offset :frame-length :payload-offset
                        :block-offset :block-length} ...]}

  `:file-offset`/`:frame-length` are what a range request needs.
  `:payload-offset` is the same position expressed the way the CARv2 index
  stores it (relative to the CARv1 payload) — kept distinct on purpose,
  because conflating them is the one bug this format reliably produces."
  [{:keys [roots blocks index?] :or {index? true}}]
  (let [{v1 :bytes v1-entries :entries} (car/encode {:roots roots :blocks blocks})
        data-size (b/bcount v1)
        entries (mapv (fn [{:keys [cid frame-offset frame-length block-offset block-length]}]
                        {:cid cid
                         :file-offset (+ data-offset frame-offset)
                         :frame-length frame-length
                         :payload-offset frame-offset
                         :block-offset (+ data-offset block-offset)
                         :block-length block-length})
                      v1-entries)
        index-offset (if index? (+ data-offset data-size) 0)
        head (header {:data-offset data-offset
                      :data-size data-size
                      :index-offset index-offset})
        parts (cond-> [pragma head v1]
                index? (conj (idx/encode (mapv #(select-keys % [:cid :payload-offset]) entries))))]
    {:bytes (b/concat parts)
     :data-offset data-offset
     :data-size data-size
     :index-offset index-offset
     :entries entries}))

;; ── reading ──────────────────────────────────────────────────────────────────

(defn read-all
  "Decode a whole CARv2 in memory: `{:roots :entries :blocks}` with file-absolute
  offsets. For when the caller already holds the bytes."
  [buf]
  (let [{:keys [data-offset data-size]} (parse-header buf)]
    (car/decode buf data-offset (+ data-offset data-size))))

(defn read-index
  "The index records of a CARv2, or nil when it carries none."
  [buf]
  (let [{:keys [index-offset]} (parse-header buf)]
    (when (pos? index-offset)
      (idx/decode buf index-offset))))

(defn locate
  "Where `cid` lives in the file described by `hdr` and `index-records`.

  Returns `{:file-offset n}` or nil. The length is deliberately absent: the
  index stores where a frame starts and not how long it is, so a caller either
  reads to the next record or over-fetches. `pack` returns real lengths;
  a reader holding only the index must use `range-open`."
  [{:keys [data-offset]} index-records cid]
  (let [{:keys [digest]} (car/read-cid (car/cid->bytes cid) 0)]
    (some (fn [{:keys [payload-offset] :as r}]
            (when (b/equal? digest (:digest r))
              {:file-offset (+ data-offset payload-offset)}))
          index-records)))

(defn range-header
  "An HTTP `Range` value for a known frame."
  [{:keys [file-offset frame-length]}]
  (str "bytes=" file-offset "-" (+ file-offset frame-length -1)))

(defn range-open
  "An HTTP `Range` value from a start offset with a read-ahead ceiling, for the
  reader that knows where a frame begins but not where it ends. `limit` bytes
  are requested; `read-frame` then uses only the frame it finds."
  [{:keys [file-offset]} limit]
  (str "bytes=" file-offset "-" (+ file-offset limit -1)))

(defn read-frame
  "Parse one block frame at `off` in `buf` and verify its CID.

  `buf` is normally the body of a range response, in which case `off` is 0 —
  the point being that this function never assumes it holds the whole archive."
  [buf off]
  (let [{flen :value fvl :length} (b/read-varint buf off)
        body (+ off fvl)
        {:keys [cid length]} (car/read-cid buf body)
        end (+ body flen)]
    (when (> end (b/bcount buf))
      (throw (ex-info "car: range response shorter than the frame it starts"
                      {:type :car/frame-truncated
                       :need (- end (b/bcount buf))})))
    {:cid cid
     :bytes (car/verify-block cid (b/slice buf (+ body length) end))
     :frame-length (+ fvl flen)}))
