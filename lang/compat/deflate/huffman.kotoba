(ns deflate.huffman
  "Canonical Huffman codes (RFC 1951 §3.2.2) in both directions.

   A DEFLATE Huffman code is fully determined by its per-symbol code *lengths*;
   the codes themselves are then assigned canonically (shorter codes numerically
   first, ties broken by symbol order). So:

   - decoding needs `decode-table` (lengths → lookup) + `read-sym`,
   - encoding needs `codes-from-lengths` (lengths → codes) and, for dynamic
     blocks, `lengths-from-freqs` (symbol frequencies → length-limited lengths).

   `lengths-from-freqs` is where an encoder can quietly produce an
   unrepresentable tree: RFC 1951 caps code lengths at 15 bits, and plain
   Huffman on a skewed histogram can exceed that. Rather than emit a stream no
   decoder will accept, it flattens the histogram and retries, and reports
   failure so the caller can fall back to the fixed tables."
  (:require [deflate.bits :as bits]))

;; ---------------------------------------------------------------------------
;; Decoding
;; ---------------------------------------------------------------------------

(defn decode-table
  "Build a decode table from per-symbol code `lengths` (index = symbol,
   value = bit length, 0 = symbol unused)."
  [lengths]
  (let [maxlen    (reduce max 0 lengths)
        bl-count  (reduce (fn [m l] (if (pos? l) (update m l (fnil inc 0)) m)) {} lengths)
        next-code (loop [len 1 code 0 acc {}]
                    (if (> len maxlen)
                      acc
                      (let [code (bit-shift-left (+ code (get bl-count (dec len) 0)) 1)]
                        (recur (inc len) code (assoc acc len code)))))
        table     (loop [sym 0 nc next-code t (transient {})]
                    (if (>= sym (count lengths))
                      (persistent! t)
                      (let [l (nth lengths sym)]
                        (if (pos? l)
                          (recur (inc sym) (update nc l inc) (assoc! t [l (get nc l)] sym))
                          (recur (inc sym) nc t)))))]
    {:table table :maxlen maxlen :count (count (filter pos? lengths))}))

(defn read-sym
  "Decode one symbol from bit reader `r`. Codes are read MSB-first."
  [r {:keys [table maxlen]}]
  (when (zero? maxlen)
    (throw (ex-info "deflate: decode from an empty Huffman table" {:reason :empty-table})))
  (loop [len 1 code 0]
    (let [code (bit-or (bit-shift-left code 1) (bits/read-bit r))]
      (if-let [s (get table [len code])]
        s
        (do (when (>= len maxlen)
              (throw (ex-info "deflate: invalid Huffman code" {:reason :bad-code :len len})))
            (recur (inc len) code))))))

;; ---------------------------------------------------------------------------
;; Encoding
;; ---------------------------------------------------------------------------

(defn codes-from-lengths
  "Assign canonical codes for per-symbol code `lengths`.
   Returns a vector of codes (0 where the length is 0)."
  [lengths]
  (let [maxlen    (reduce max 0 lengths)
        bl-count  (reduce (fn [m l] (if (pos? l) (update m l (fnil inc 0)) m)) {} lengths)
        next-code (loop [len 1 code 0 acc {}]
                    (if (> len maxlen)
                      acc
                      (let [code (bit-shift-left (+ code (get bl-count (dec len) 0)) 1)]
                        (recur (inc len) code (assoc acc len code)))))]
    (loop [sym 0 nc next-code out (transient (vec (repeat (count lengths) 0)))]
      (if (>= sym (count lengths))
        (persistent! out)
        (let [l (nth lengths sym)]
          (if (pos? l)
            (recur (inc sym) (update nc l inc) (assoc! out sym (get nc l)))
            (recur (inc sym) nc out)))))))

(defn- huffman-depths
  "Exact Huffman code lengths for `freq-map` (symbol → positive frequency,
   at least two entries). Merges the two lowest-frequency nodes, deepening
   every symbol underneath, which is the textbook construction stated in terms
   of depths instead of an explicit tree.

   The initial order is `[frequency symbol]`, and the second component is
   load-bearing rather than tidy. `sort-by` is stable, so sorting on frequency
   alone leaves equal-frequency symbols in `freq-map`'s own seq order — and
   `freq-map` is a hash map, whose order differs between Clojure and
   ClojureScript. Different tie-breaks build a different (equally optimal)
   tree, so the two runtimes emitted different, equally valid DEFLATE streams
   for identical input.

   That was invisible to every consumer here — PNG, ZIP, WOFF, PDF and bonsai
   all care only that a stream inflates, and it does — until a caller put the
   compressed bytes inside a content address, where a different encoding is a
   different identity. Sorting on the symbol too makes the order total, so the
   construction is a function of the histogram and nothing else."
  [freq-map]
  (let [start (vec (sort-by (juxt first (comp first second))
                            (map (fn [[s f]] [f [s]]) freq-map)))]
    (loop [nodes start
           depths (transient (zipmap (keys freq-map) (repeat 0)))]
      (if (<= (count nodes) 1)
        (persistent! depths)
        (let [[f1 s1]    (nth nodes 0)
              [f2 s2]    (nth nodes 1)
              others     (subvec nodes 2)
              merged-f   (+ f1 f2)
              merged-s   (into s1 s2)
              depths     (reduce (fn [d s] (assoc! d s (inc (get d s)))) depths merged-s)
              ;; keep the node list sorted ascending by frequency
              idx        (count (take-while #(<= (first %) merged-f) others))]
          (recur (into (conj (subvec others 0 idx) [merged-f merged-s])
                       (subvec others idx))
                 depths))))))

(defn- flatten-freqs
  "Halve every frequency (never below 1). Iterating this converges on a uniform
   histogram, whose Huffman depths are ⌈log2 n⌉ — so the length limit is always
   reachable."
  [freq-map]
  (into {} (map (fn [[s f]] [s (max 1 (quot (inc f) 2))]) freq-map)))

(defn lengths-from-freqs
  "Code lengths for the histogram `freqs` (vector indexed by symbol), with no
   length exceeding `limit`.

   Returns a vector of lengths the same size as `freqs`, or nil when fewer than
   one symbol is used. A single used symbol is padded with a second one, since a
   one-code Huffman tree cannot be expressed with a nonzero length."
  [freqs limit]
  (let [used (into {} (keep-indexed (fn [i f] (when (pos? f) [i f])) freqs))
        used (cond
               (empty? used) nil
               (= 1 (count used))
               (let [taken (key (first used))]
                 (assoc used (if (zero? taken) (min 1 (dec (count freqs))) 0) 1))
               :else used)]
    (when (and used (>= (count used) 2))
      (loop [m used attempt 0]
        (let [depths (huffman-depths m)
              deepest (reduce max 0 (vals depths))]
          (cond
            (<= deepest limit)
            (persistent!
             (reduce (fn [v [s d]] (assoc! v s d))
                     (transient (vec (repeat (count freqs) 0)))
                     depths))

            ;; ⌈log2 286⌉ = 9 ≤ 15, so this terminates well before the cap.
            (> attempt 40)
            nil

            :else (recur (flatten-freqs m) (inc attempt))))))))
