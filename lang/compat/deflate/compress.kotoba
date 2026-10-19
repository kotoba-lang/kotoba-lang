(ns deflate.compress
  "DEFLATE compression (RFC 1951): LZ77 tokens from `deflate.lz77`, then a
   per-block choice between the three block types the format allows.

   For every block the encoder prices all three encodings in bits and emits the
   cheapest:

   - **stored** (type 0) — 3 bits, pad to a byte, LEN/NLEN, raw data. Wins on
     incompressible input, and is the reason the compressor can never expand
     input by more than ~5 bytes per 64 KiB.
   - **fixed Huffman** (type 1) — 3 bits and no table. Wins on short blocks
     where a transmitted table costs more than it saves.
   - **dynamic Huffman** (type 2) — length-limited Huffman over the block's own
     histogram, with the tables themselves Huffman-coded (§3.2.7). Wins on
     everything of any size.

   Pricing dynamic blocks means building the tables before deciding, so the cost
   comparison is exact rather than a heuristic. If a histogram cannot be coded
   within the format's 15-bit ceiling, `deflate.huffman` says so and the block
   silently falls back to fixed — an encoder must never emit a tree no decoder
   will accept.

   Not implemented: an optimal parse (this is a greedy matcher with one-step
   lazy evaluation, like zlib rather than zopfli) and preset dictionaries on the
   encoding side. Output is a valid DEFLATE stream but is typically a few
   percent larger than zlib's at the same level."
  (:require [deflate.bits :as bits]
            [deflate.huffman :as huffman]
            [deflate.lz77 :as lz77]
            [deflate.tables :as tables]))

(def ^:private fixed-lit-codes (huffman/codes-from-lengths tables/fixed-lit-lengths))
(def ^:private fixed-dist-codes (huffman/codes-from-lengths tables/fixed-dist-lengths))

(def ^:private max-stored-block 65535)

;; ---------------------------------------------------------------------------
;; Histograms and pricing
;; ---------------------------------------------------------------------------

(defn- token-freqs
  "Symbol histograms for one block's tokens. The end-of-block symbol (256) is
   counted here because it is always emitted."
  [tokens]
  (loop [ts (seq tokens) lit {256 1} dst {}]
    (if-not ts
      [lit dst]
      (let [t (first ts)]
        (if (vector? t)
          (let [[len dist] t]
            (recur (next ts)
                   (update lit (first (tables/len-code len)) (fnil inc 0))
                   (update dst (first (tables/dist-code dist)) (fnil inc 0))))
          (recur (next ts) (update lit t (fnil inc 0)) dst))))))

(defn- extra-bits
  "Bits spent on length/distance extra fields — identical under fixed and
   dynamic coding, but needed when pricing against a stored block."
  [tokens]
  (reduce (fn [acc t]
            (if (vector? t)
              (let [[len dist] t]
                (+ acc (second (tables/len-code len)) (second (tables/dist-code dist))))
              acc))
          0 tokens))

(defn- freqs->vec [freqs size]
  (persistent! (reduce (fn [v [s c]] (assoc! v s c))
                       (transient (vec (repeat size 0)))
                       freqs)))

(defn- symbol-bits [freqs lengths]
  (reduce (fn [acc [s c]] (+ acc (* c (nth lengths s)))) 0 freqs))

;; ---------------------------------------------------------------------------
;; Dynamic block tables (RFC 1951 §3.2.7)
;; ---------------------------------------------------------------------------

(defn- trim-length
  "Number of entries to transmit: trailing unused symbols are dropped, subject to
   the format's minimum."
  [lengths minimum]
  (loop [i (count lengths)]
    (cond
      (<= i minimum) minimum
      (pos? (nth lengths (dec i))) i
      :else (recur (dec i)))))

(defn- rle-code-lengths
  "Run-length encode the transmitted code lengths onto the code-length alphabet:
   16 = repeat previous 3–6, 17 = zero run 3–10, 18 = zero run 11–138.
   Returns `[[symbol extra-bits extra-value] ...]`."
  [lens]
  (let [n (count lens)
        run-at (fn [i cur]
                 (loop [j i] (if (and (< j n) (= (nth lens j) cur)) (recur (inc j)) (- j i))))]
    (loop [i 0 prev -1 out []]
      (if (>= i n)
        out
        (let [cur (nth lens i)
              run (run-at i cur)]
          (cond
            (zero? cur)
            (cond
              (>= run 11) (let [r (min run 138)] (recur (+ i r) 0 (conj out [18 7 (- r 11)])))
              (>= run 3)  (let [r (min run 10)]  (recur (+ i r) 0 (conj out [17 3 (- r 3)])))
              :else       (recur (inc i) 0 (conj out [0 0 0])))

            ;; A 16 may only follow the length it repeats, so the first
            ;; occurrence is always transmitted literally.
            (and (= cur prev) (>= run 3))
            (let [r (min run 6)] (recur (+ i r) prev (conj out [16 2 (- r 3)])))

            :else (recur (inc i) cur (conj out [cur 0 0]))))))))

(defn- build-dynamic
  "Tables + transmitted header for a dynamic block, or nil when the histogram
   cannot be coded within the 15-bit ceiling."
  [lit-freqs dist-freqs]
  (let [lit-lengths  (huffman/lengths-from-freqs (freqs->vec lit-freqs 286) tables/max-code-length)
        ;; A block with no matches still needs a (never-read) distance tree.
        dist-freqs   (if (seq dist-freqs) dist-freqs {0 1})
        dist-lengths (huffman/lengths-from-freqs (freqs->vec dist-freqs 30) tables/max-code-length)]
    (when (and lit-lengths dist-lengths)
      (let [hlit        (trim-length lit-lengths 257)
            hdist       (trim-length dist-lengths 1)
            lens        (into (subvec lit-lengths 0 hlit) (subvec dist-lengths 0 hdist))
            items       (rle-code-lengths lens)
            clc-freqs   (reduce (fn [m [s _ _]] (update m s (fnil inc 0))) {} items)
            clc-lengths (huffman/lengths-from-freqs (freqs->vec clc-freqs 19) tables/max-clc-length)]
        (when clc-lengths
          (let [hclen (loop [i (count tables/clc-order)]
                        (cond
                          (<= i 4) 4
                          (pos? (nth clc-lengths (nth tables/clc-order (dec i)))) i
                          :else (recur (dec i))))
                header-bits (+ 14 (* 3 hclen)
                               (reduce (fn [acc [s eb _]] (+ acc (nth clc-lengths s) eb)) 0 items))]
            {:lit-lengths  lit-lengths
             :lit-codes    (huffman/codes-from-lengths lit-lengths)
             :dist-lengths dist-lengths
             :dist-codes   (huffman/codes-from-lengths dist-lengths)
             :clc-lengths  clc-lengths
             :clc-codes    (huffman/codes-from-lengths clc-lengths)
             :items        items
             :hlit         hlit
             :hdist        hdist
             :hclen        hclen
             :header-bits  header-bits}))))))

;; ---------------------------------------------------------------------------
;; Emission
;; ---------------------------------------------------------------------------

(defn- emit-stored! [w data final?]
  (bits/write-bits! w (if final? 1 0) 1)
  (bits/write-bits! w 0 2)
  (bits/align-writer! w)
  (let [len (count data)]
    (bits/write-u16-le! w len)
    (bits/write-u16-le! w (bit-and (bit-not len) 0xffff))
    (doseq [b data] (bits/write-byte! w b))))

(defn- emit-tokens! [w tokens lit-codes lit-lengths dist-codes dist-lengths]
  (doseq [t tokens]
    (if (vector? t)
      (let [[len dist]  t
            [ls le lv]  (tables/len-code len)
            [ds de dv]  (tables/dist-code dist)]
        (bits/write-code! w (nth lit-codes ls) (nth lit-lengths ls))
        (when (pos? le) (bits/write-bits! w lv le))
        (bits/write-code! w (nth dist-codes ds) (nth dist-lengths ds))
        (when (pos? de) (bits/write-bits! w dv de)))
      (bits/write-code! w (nth lit-codes t) (nth lit-lengths t))))
  (bits/write-code! w (nth lit-codes 256) (nth lit-lengths 256)))

(defn- emit-fixed! [w tokens final?]
  (bits/write-bits! w (if final? 1 0) 1)
  (bits/write-bits! w 1 2)
  (emit-tokens! w tokens fixed-lit-codes tables/fixed-lit-lengths
                fixed-dist-codes tables/fixed-dist-lengths))

(defn- emit-dynamic! [w tokens dyn final?]
  (bits/write-bits! w (if final? 1 0) 1)
  (bits/write-bits! w 2 2)
  (bits/write-bits! w (- (:hlit dyn) 257) 5)
  (bits/write-bits! w (- (:hdist dyn) 1) 5)
  (bits/write-bits! w (- (:hclen dyn) 4) 4)
  (dotimes [i (:hclen dyn)]
    (bits/write-bits! w (nth (:clc-lengths dyn) (nth tables/clc-order i)) 3))
  (doseq [[s eb v] (:items dyn)]
    (bits/write-code! w (nth (:clc-codes dyn) s) (nth (:clc-lengths dyn) s))
    (when (pos? eb) (bits/write-bits! w v eb)))
  (emit-tokens! w tokens (:lit-codes dyn) (:lit-lengths dyn)
                (:dist-codes dyn) (:dist-lengths dyn)))

(defn- emit-block!
  "Price the three block types and emit the cheapest."
  [w tokens raw-bytes final?]
  (let [[lit-freqs dist-freqs] (token-freqs tokens)
        extra       (extra-bits tokens)
        fixed-bits  (+ 3 extra
                       (symbol-bits lit-freqs tables/fixed-lit-lengths)
                       (symbol-bits dist-freqs tables/fixed-dist-lengths))
        dyn         (build-dynamic lit-freqs dist-freqs)
        dyn-bits    (when dyn
                      (+ 3 extra (:header-bits dyn)
                         (symbol-bits lit-freqs (:lit-lengths dyn))
                         (symbol-bits dist-freqs (:dist-lengths dyn))))
        stored-len  (count raw-bytes)
        ;; A stored block pads to a byte boundary after its 3 header bits.
        pad         (mod (- 8 (mod (+ (bits/bits-written w) 3) 8)) 8)
        stored-bits (+ 3 pad 32 (* 8 stored-len))
        best        (min fixed-bits (or dyn-bits fixed-bits)
                         (if (<= stored-len max-stored-block) stored-bits fixed-bits))]
    (cond
      (and (<= stored-len max-stored-block) (= best stored-bits)) (emit-stored! w raw-bytes final?)
      (and dyn-bits (= best dyn-bits))                            (emit-dynamic! w tokens dyn final?)
      :else                                                       (emit-fixed! w tokens final?))))

;; ---------------------------------------------------------------------------
;; Entry point
;; ---------------------------------------------------------------------------

(defn raw
  "Compress `data` (a sequence of unsigned bytes) into a raw DEFLATE stream.

   Options:
     :level  0–9 (default 6). 0 stores without compressing; 1 is a shallow
             match search, 9 the deepest.

   Returns a vector of unsigned bytes."
  ([data] (raw data nil))
  ([data {:keys [level] :or {level lz77/default-level}}]
   (let [v (vec data)
         n (count v)
         w (bits/writer)]
     (cond
       (zero? n)
       (emit-stored! w [] true)

       (zero? level)
       (loop [pos 0]
         (let [end (min n (+ pos max-stored-block))]
           (emit-stored! w (subvec v pos end) (>= end n))
           (when (< end n) (recur end))))

       :else
       (let [m (lz77/matcher v {:level level})]
         (loop [pos 0]
           (let [{:keys [tokens start end]} (lz77/next-block! m pos {})
                 final? (>= end n)]
             (emit-block! w tokens (subvec v start end) final?)
             (when-not final? (recur end))))))
     (bits/finish! w))))
