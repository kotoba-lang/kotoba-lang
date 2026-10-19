(ns ipld.car.index
  "`MultihashIndexSorted` (multicodec 0x0401) — the index a CARv2 carries so a
  reader can find one block without scanning the archive.

  Layout, little-endian throughout except the leading multicodec varint:

      varint(0x0401)
      int32   number of distinct multihash codes
        uint64  multihash code                      (ascending)
        int32   number of distinct digest widths
          uint32  width          = digest length + 8
          int64   byte length of the record run     = count * width
          records                                    (ascending by digest)
            digest bytes || uint64 offset

  The offset is relative to the **start of the CARv1 payload**, not to the
  start of the file, and it points at the frame's length varint rather than at
  the block bytes. Both choices are the format's, not this library's; a reader
  that assumes otherwise reads a valid file wrongly, which is why
  `ipld.car.v2/locate` returns absolute file offsets and never exposes these."
  (:require [ipld.car.bytes :as b]
            [ipld.car :as car]))

(def ^:const codec 0x0401)

;; ── resource limits ──────────────────────────────────────────────────────────
;; The counts below are read FROM the archive, so an index can declare more
;; work than it carries bytes for. Declared work is not evidence of work: a
;; group header costs 12 bytes and a record costs `width`, so the buffer's own
;; length is an upper bound on both, and checking against it costs nothing and
;; needs no configuration. The explicit record ceiling exists on top of that
;; because a large, WELL-FORMED index is still an unbounded allocation for a
;; reader that fetched it over the network and cannot see its size in advance.

(def ^:const group-header-bytes
  "`uint64 code || int32 nwidths`, and `uint32 width || int64 run` — both 12."
  12)

(def ^:const default-max-records
  "Records one `decode` will materialise before refusing. 2^20 records is a
   ~40 MB index; a reader wanting more must say so, because the caller is the
   only party that knows what it is willing to hold."
  1048576)

(defn- limit!
  "Refuse a declared count that the remaining bytes cannot support."
  [what declared per-item buf off]
  (let [remaining (- (b/bcount buf) off)
        possible (quot remaining per-item)]
    (when (or (neg? declared) (> declared possible))
      (throw (ex-info (str "car: index declares more " (name what)
                           " than its bytes can hold")
                      {:type :car/index-overruns-buffer
                       :what what :declared declared
                       :possible possible :remaining remaining}))))
  declared)

(defn- digest-of [cid]
  (let [{:keys [digest mh-code]} (car/read-cid (car/cid->bytes cid) 0)]
    {:digest digest :mh-code mh-code}))

(defn- cmp-bytes [x y]
  (let [n (min (b/bcount x) (b/bcount y))]
    (loop [i 0]
      (cond
        (= i n) (compare (b/bcount x) (b/bcount y))
        (not= (b/bget x i) (b/bget y i)) (compare (b/bget x i) (b/bget y i))
        :else (recur (inc i))))))

(defn encode
  "Index bytes for `entries` — `[{:cid s :payload-offset n} ...]`, where
  `:payload-offset` is the frame offset **within the CARv1 payload**."
  [entries]
  (let [recs (map (fn [{:keys [cid payload-offset]}]
                    (let [{:keys [digest mh-code]} (digest-of cid)]
                      {:digest digest :mh-code mh-code :offset payload-offset}))
                  entries)
        by-code (into (sorted-map) (group-by :mh-code recs))]
    (b/concat
     (into [(b/varint codec) (b/u32-le (count by-code))]
           (mapcat
            (fn [[code rs]]
              (let [by-width (into (sorted-map)
                                   (group-by #(+ 8 (b/bcount (:digest %))) rs))]
                (into [(b/u64-le code) (b/u32-le (count by-width))]
                      (mapcat
                       (fn [[width ws]]
                         (let [sorted (sort-by :digest cmp-bytes ws)]
                           (into [(b/u32-le width)
                                  (b/u64-le (* width (count sorted)))]
                                 (mapcat (fn [{:keys [digest offset]}]
                                           [digest (b/u64-le offset)])
                                         sorted))))
                       by-width))))
            by-code)))))

(defn- read-width-run
  "One `uint32 width || int64 run || records` group at `q`."
  [buf q code budget]
  (let [width (b/read-u32-le buf q)
        run (b/read-u64-le buf (+ q 4))
        start (+ q 12)
        dlen (- width 8)]
    (when (or (<= width 8) (pos? (mod run width)))
      (throw (ex-info "car: index width/run disagree"
                      {:type :car/index-malformed :width width :run run})))
    ;; The run length is declared by the file. Check it against the bytes that
    ;; actually follow before allocating one record, rather than discovering
    ;; the shortfall part-way through a `mapv`.
    (when (> run (- (b/bcount buf) start))
      (throw (ex-info "car: index record run overruns the buffer"
                      {:type :car/index-overruns-buffer
                       :what :records :declared run
                       :remaining (- (b/bcount buf) start)})))
    (let [n (quot run width)]
      (when (> n @budget)
        (throw (ex-info "car: index exceeds the record limit"
                        {:type :car/index-too-many-records
                         :records n :remaining-budget @budget})))
      (swap! budget - n)
      {:next (+ start run)
       :records (mapv (fn [k]
                        (let [r (+ start (* k width))]
                          {:mh-code code
                           :digest (b/slice buf r (+ r dlen))
                           :payload-offset (b/read-u64-le buf (+ r dlen))}))
                      (range n))})))

(defn- read-code-group
  "One `uint64 code || int32 nwidths || width-runs` group at `p`."
  [buf p budget]
  (let [code (b/read-u64-le buf p)
        n-widths (limit! :width-runs (b/read-u32-le buf (+ p 8))
                         group-header-bytes buf (+ p 12))]
    (loop [j 0 q (+ p 12) acc []]
      (if (= j n-widths)
        {:next q :records acc}
        (let [{:keys [next records]} (read-width-run buf q code budget)]
          (recur (inc j) next (into acc records)))))))

(defn decode
  "Parse index bytes at `off` → `[{:mh-code n :digest bytes :payload-offset n} ...]`.

  Every count in the layout is read from the archive, so this refuses a
  declared count the remaining bytes cannot support, and refuses more than
  `:max-records` records (`default-max-records`) even when they are all
  present. A reader that fetched an index over the network learns its size
  only by holding it; the ceiling is what lets it stop first.

  The failures are typed rather than shared: `:car/index-overruns-buffer` for
  a count the bytes cannot support, `:car/index-too-many-records` for one the
  caller will not hold, `:car/read-out-of-range` for a truncated field. A
  short buffer must not decode as an empty index -- nothing downstream can
  tell that apart from a pack that genuinely indexes no blocks."
  ([buf] (decode buf 0 nil))
  ([buf off] (decode buf off nil))
  ([buf off {:keys [max-records] :or {max-records default-max-records}}]
   (let [{c :value cl :length} (b/read-varint buf off)]
     (when-not (= codec c)
       (throw (ex-info "car: unsupported index codec — only MultihashIndexSorted (0x0401)"
                       {:type :car/index-codec :codec c})))
     (let [pos (+ off cl)
           n-codes (limit! :code-groups (b/read-u32-le buf pos)
                           group-header-bytes buf (+ pos 4))
           budget (atom max-records)]
       (loop [i 0 p (+ pos 4) acc []]
         (if (= i n-codes)
           acc
           (let [{:keys [next records]} (read-code-group buf p budget)]
             (recur (inc i) next (into acc records)))))))))
