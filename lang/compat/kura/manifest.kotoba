(ns kura.manifest
  "How an object becomes shards, and how a byte range becomes reads.

  An object is cut into fixed-size **stripes**; each stripe is encoded
  independently into `n` shards of `stripe-bytes / k` each. Independent
  because a stripe is the unit of repair and of reconstruction: making the
  whole object one stripe would mean a single lost shard forces a read
  proportional to the object, and reading one byte forces a decode of
  everything.

  **Why the range path is the interesting one.** ADR-2607299200 section 7
  claims kura reads a small range at ~1.0x amplification where a
  whole-segment erasure scheme pays ~64x, and attributes that to the code
  being *systematic* with directly readable data shards rather than to it
  being an LRC. `range-reads` is that claim as code: it returns only data
  shards, only the ones the range actually intersects, and `amplification`
  reports what it cost. If someone later makes the data shards
  non-systematic or routes reads through a reconstruct-always path, the
  amplification test fails, which is the point of writing it down.

  The cost of a direct shard read is a single node in the path, so tail
  latency is one node's tail rather than the best of many. `range-reads`
  therefore also returns the local group that could rebuild each shard, so a
  caller can hedge on a slow holder instead of waiting. Section 7 says this
  out loud rather than claiming Storj-equal TTFB for free."
  (:require [erasure.lrc :as lrc]))

(defn plan
  "Fix an object's geometry. `stripe-bytes` must divide evenly by `k` — a
  ragged shard is representable but every downstream offset calculation then
  needs a special case for the last one, and the cost of padding is bounded
  by one shard per object."
  [{:keys [object-id size stripe-bytes] :as _obj} layout]
  (let [k (:k layout)]
    (assert (pos? stripe-bytes) "stripe-bytes must be positive")
    (assert (zero? (mod stripe-bytes k)) "stripe-bytes must divide by k")
    (assert (not (neg? size)) "size must be non-negative")
    {:object-id object-id
     :size size
     :stripe-bytes stripe-bytes
     :shard-bytes (quot stripe-bytes k)
     :stripes (max 1 (quot (+ size stripe-bytes -1) stripe-bytes))
     :layout layout}))

(defn shard-id
  "Stable identifier of one shard. Hierarchical so that a node's local
  ordering groups an object's shards together, and ASCII so it can be a
  placement key."
  [object-id stripe index]
  (str object-id "/" stripe "/" index))

(defn stripe-shards
  "Every shard id of one stripe, in shard-index order."
  [{:keys [object-id layout]} stripe]
  (mapv #(shard-id object-id stripe %) (range (:n layout))))

(defn total-shards
  [{:keys [stripes layout]}]
  (* stripes (:n layout)))

(defn stored-bytes
  "Physical bytes the object occupies across the network — the storage
  multiplier made concrete."
  [{:keys [stripes shard-bytes layout]}]
  (* stripes shard-bytes (:n layout)))

(defn multiplier
  [{:keys [size] :as p}]
  (if (zero? size) 0.0 (/ (double (stored-bytes p)) size)))

;; --- reads -----------------------------------------------------------------

(defn- clamp [lo hi x] (max lo (min hi x)))

(defn range-reads
  "The shard reads that cover `[offset, offset+length)`.

  Returns a vector of `{:stripe :index :shard-id :shard-offset :length
  :hedge-group}` over DATA shards only. `:hedge-group` is the local group
  whose other members can rebuild that shard if its holder is slow — the
  mitigation for the single-node tail latency a direct read implies."
  [{:keys [size stripe-bytes shard-bytes layout object-id]} offset length]
  (let [k (:k layout)
        start (clamp 0 size offset)
        end (clamp 0 size (+ offset length))]
    (if (>= start end)
      []
      (vec
       (for [stripe (range (quot start stripe-bytes)
                           (inc (quot (dec end) stripe-bytes)))
             index (range k)
             :let [shard-start (+ (* stripe stripe-bytes) (* index shard-bytes))
                   shard-end (+ shard-start shard-bytes)
                   from (max start shard-start)
                   to (min end shard-end)]
             :when (< from to)]
         {:stripe stripe
          :index index
          :shard-id (shard-id object-id stripe index)
          :shard-offset (- from shard-start)
          :length (- to from)
          :hedge-group (lrc/group-of layout index)})))))

(defn amplification
  "Bytes fetched divided by bytes requested, for a range.

  The number ADR-2607299200 section 7 puts at ~1.0-1.2. It is above 1.0 only
  where a range starts or ends mid-shard and the caller cannot ask for a
  partial one; `range-reads` does emit partial lengths, so with a
  byte-range-capable transport this is exactly 1.0 and the figure to watch is
  what a real node protocol rounds it up to."
  [p offset length]
  (let [reads (range-reads p offset length)
        got (reduce + 0 (map :length reads))
        want (max 1 (- (min (:size p) (+ offset length)) (max 0 offset)))]
    (/ (double got) want)))

(defn whole-object-reads
  "Every data-shard read for the whole object — the sequential path."
  [p]
  (range-reads p 0 (:size p)))
