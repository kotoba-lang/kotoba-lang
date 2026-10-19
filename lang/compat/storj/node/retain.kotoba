(ns storj.node.retain
  "Which pieces to keep, and — the part that matters — which to keep anyway.

  A satellite periodically sends a bloom filter of every piece it still wants,
  and the node deletes what is not in it. That is the only operation a storage
  node performs that destroys customer data, so every rule here is written
  from the direction of *not* deleting.

  ## The filter alone is not the answer

  `RetainRequest` carries a `creation_date` beside the filter, and it is not
  metadata. The filter was built at that moment; a piece the node accepted
  afterwards **cannot** be in it, and deleting on absence alone would destroy
  exactly the pieces the satellite most recently entrusted to it. So the
  decision is:

      delete when   the piece is absent from the filter
                    AND it was created before the filter was

  Absence is necessary and not sufficient. `keep?` takes both.

  ## False positives are the safe direction

  A bloom filter can say `maybe` for something that is not in the set; it can
  never say `no` for something that is. So a false positive keeps a piece that
  could have been deleted — wasted disk — and there is no error in the other
  direction. Reading `contains?` as anything stronger than `maybe` is what
  turns that asymmetry into data loss.

  ## Sixty-four bit arithmetic without sixty-four bits

  Each probe reads a little-endian `uint64` out of the piece id and takes it
  modulo the table size. That number does not fit in a JavaScript double, so
  it is never formed: the modulus is folded byte by byte from the top down,
  which is exact as long as `table-size * 256` stays inside 2^53 — true for
  any filter that fits in memory, and asserted when a filter is parsed."
  (:refer-clojure :exclude [contains?])
  (:require [storj.node.bytes :as b]))

(def version-1
  "The only filter version `bloomfilter.NewFromBytes` accepts."
  1)

(def piece-id-length 32)

(def range-offsets
  "`bloomfilter.rangeOffsets` — offsets chosen to minimise overlap between the
  first hash functions."
  [9 13 19 23])

(def ^:private max-exact-table-size
  ;; the fold below computes `acc * 256 + byte`, so `table-size * 256 + 255`
  ;; has to stay exact. 2^53 / 256 is far larger than any filter that fits in
  ;; memory; the check exists so that if one ever did, it would say so.
  (quot 9007199254740992 256))

(defn- fail [msg data]
  (throw (ex-info (str "storj.node.retain: " msg) data)))

(defn initial-conditions
  "`bloomfilter.initialConditions`: where the first probe reads from, and how
  far each subsequent probe moves."
  [seed]
  {:offset       (mod seed 32)
   :range-offset (nth range-offsets (mod (quot seed 32) (count range-offsets)))})

(defn parse
  "Decode a filter, refusing everything `NewFromBytes` refuses and one more.

      {:seed n :hash-count n :table bytes :offset n :range-offset n}

  The extra refusal is an empty table. Go builds a `fastdiv.Uint64` from the
  table length and every probe divides by it; a zero-length table is a filter
  that cannot be consulted, and finding that out at the first probe rather
  than at parse time means finding it out with a piece in hand."
  [filter-bytes]
  (let [v (vec filter-bytes)]
    (when (< (count v) 3)
      (fail "a filter is at least a version, a seed and a hash count"
            {:length (count v)}))
    (let [version    (nth v 0)
          seed       (nth v 1)
          hash-count (nth v 2)
          table      (subvec v 3)]
      (when (not= version-1 version)
        (fail "unsupported filter version" {:version version}))
      (when (zero? hash-count)
        (fail "a filter with no hash functions matches everything" {}))
      (when (zero? (count table))
        (fail "a filter with an empty table cannot be consulted" {}))
      (when (> (count table) max-exact-table-size)
        (fail "table too large to take a modulus of exactly"
              {:size (count table) :limit max-exact-table-size}))
      (merge {:seed seed :hash-count hash-count :table table}
             (initial-conditions seed)))))

(defn- mod-little-endian-u64
  "`hash mod m`, where `hash` is the little-endian u64 at `bs[from]`.

  Folded from the most significant byte down rather than assembled first: the
  assembled value reaches 2^64 and a JavaScript number stops being exact at
  2^53, so the two runtimes would disagree about which bucket a piece lands
  in — and disagreeing about that means deleting different pieces."
  [bs from m]
  (loop [i 7, acc 0]
    (if (neg? i)
      acc
      (recur (dec i) (mod (+ (* acc 256) (nth bs (+ from i))) m)))))

(defn contains?
  "Whether the filter says the piece **may** be present.

  False is definitive — that piece is not in the set. True is not: a bloom
  filter has false positives by construction, and treating true as certainty
  is not the dangerous mistake here. Treating false as anything less than
  certain is."
  [{:keys [hash-count table offset range-offset]} piece-id]
  (let [id (vec piece-id)]
    (when (not= piece-id-length (count id))
      (fail "a piece id is 32 bytes" {:length (count id)}))
    ;; the id doubled, so a probe near the end can read eight bytes and a bit
    ;; without wrapping by hand — `Add` and `Contains` both do this
    (let [doubled (into id id)
          size    (count table)]
      (loop [k 0, offset offset]
        (if (>= k hash-count)
          true
          (let [bucket (mod-little-endian-u64 doubled offset size)
                bit    (nth doubled (+ offset 8))]
            (if (zero? (bit-and (nth table bucket)
                                (bit-shift-left 1 (mod bit 8))))
              false
              (recur (inc k) (mod (+ offset range-offset) piece-id-length)))))))))

(defn keep?
  "Whether a piece survives this retain request.

  `created-at` and `filter-created-at` are seconds. A piece the node accepted
  after the filter was built cannot be in it — the satellite had not seen it
  yet — so absence from the filter is not enough on its own. Getting this
  wrong deletes the pieces the satellite most recently entrusted to the node,
  which is the worst possible set to lose.

  A piece with no known creation time is kept. A node that cannot say when it
  received something is a node that must not delete it."
  [bloom piece-id {:keys [created-at filter-created-at]}]
  (boolean
   (or (contains? bloom piece-id)
       (nil? created-at)
       (nil? filter-created-at)
       (>= created-at filter-created-at))))

(defn partition-pieces
  "Split piece ids into those to keep and those to delete.

  `created-at-of` answers when the node received a piece, in seconds, or nil
  if it cannot say."
  [bloom piece-ids {:keys [filter-created-at created-at-of]}]
  (reduce (fn [acc id]
            (let [k (keep? bloom id {:created-at (when created-at-of (created-at-of id))
                                      :filter-created-at filter-created-at})]
              (update acc (if k :keep :delete) conj id)))
          {:keep [] :delete []}
          piece-ids))

(defn fill-rate
  "The proportion of bits set, as `Filter.FillRate` reports it.

  A filter that has filled up answers `maybe` to everything and stops
  reclaiming anything, which looks like a node with no garbage rather than a
  filter that has stopped working."
  [{:keys [table]}]
  (let [bits (reduce (fn [n byte]
                       (+ n (loop [c 0, v byte] (if (zero? v)
                                                  c
                                                  (recur (+ c (bit-and v 1))
                                                         (bit-shift-right v 1))))))
                     0 table)]
    (/ (double bits) (* 8.0 (count table)))))

(defn describe
  "What a filter is, for a log line that has to be read at 3am."
  [{:keys [seed hash-count table] :as bloom}]
  {:seed seed
   :hash-count hash-count
   :table-bytes (count table)
   :fill-rate (fill-rate bloom)
   :hex-prefix (b/hex (subvec table 0 (min 8 (count table))))})
