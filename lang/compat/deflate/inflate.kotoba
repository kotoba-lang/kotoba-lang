(ns deflate.inflate
  "DEFLATE decompression (RFC 1951 §3.2): stored, fixed-Huffman and
   dynamic-Huffman blocks over Huffman + LZ77.

   Correctness-first: output is accumulated in a persistent vector and
   back-references read it via `nth`, so there is no 32 KiB ring buffer to get
   wrong and no mutable output array to leak between calls. Large rasters
   should be inflated on demand rather than all at once.

   Every failure mode is an `ex-info` carrying a `:reason` key — `:truncated`,
   `:bad-code`, `:bad-block-type`, `:bad-stored-length`, `:bad-distance`,
   `:bad-length-symbol`, `:output-limit` — so containers on top (zlib, gzip,
   ZIP) can distinguish a corrupt member from a mis-framed one."
  (:require [deflate.bits :as bits]
            [deflate.huffman :as huffman]
            [deflate.tables :as tables]))

(def default-max-output
  "Default ceiling on inflated size (128 MiB), so a hostile 1 KiB stream cannot
   expand until the process dies. Pass `:max-output nil` for no ceiling."
  (* 128 1024 1024))

(def ^:private fixed-lit-table
  (huffman/decode-table tables/fixed-lit-lengths))

(def ^:private fixed-dist-table
  (huffman/decode-table tables/fixed-dist-lengths))

(defn- read-dynamic-tables [r]
  (let [hlit    (+ 257 (bits/read-bits r 5))
        hdist   (+ 1   (bits/read-bits r 5))
        hclen   (+ 4   (bits/read-bits r 4))
        cl-lens (loop [i 0 m {}]
                  (if (= i hclen)
                    m
                    (recur (inc i) (assoc m (nth tables/clc-order i) (bits/read-bits r 3)))))
        cl-huff (huffman/decode-table (mapv #(get cl-lens % 0) (range 19)))
        total   (+ hlit hdist)
        all     (loop [out []]
                  (if (>= (count out) total)
                    out
                    (let [sym (huffman/read-sym r cl-huff)]
                      (cond
                        (< sym 16) (recur (conj out sym))
                        (= sym 16) (let [rep  (+ 3 (bits/read-bits r 2))
                                         prev (peek out)]
                                     (when (nil? prev)
                                       (throw (ex-info "deflate: code-length repeat with no previous length"
                                                       {:reason :bad-code-lengths})))
                                     (recur (into out (repeat rep prev))))
                        (= sym 17) (recur (into out (repeat (+ 3 (bits/read-bits r 3)) 0)))
                        :else      (recur (into out (repeat (+ 11 (bits/read-bits r 7)) 0)))))))]
    (when (> (count all) total)
      ;; A repeat may not run past the end of the combined length list.
      (throw (ex-info "deflate: code lengths overrun the declared table sizes"
                      {:reason :bad-code-lengths :declared total :got (count all)})))
    [(huffman/decode-table (subvec all 0 hlit))
     (huffman/decode-table (subvec all hlit total))]))

(defn- inflate-block! [r lit-table dist-table out base limit]
  (loop []
    (let [sym (huffman/read-sym r lit-table)]
      (cond
        (= sym 256) nil                                     ; end of block

        (< sym 256)
        (do (vswap! out conj sym)
            (when (and limit (> (- (count @out) base) limit))
              (throw (ex-info "deflate: inflated output exceeds limit"
                              {:reason :output-limit :limit limit})))
            (recur))

        (> sym 285)
        (throw (ex-info "deflate: invalid length symbol" {:reason :bad-length-symbol :symbol sym}))

        :else
        (let [li       (- sym 257)
              length   (+ (nth tables/len-base li) (bits/read-bits r (nth tables/len-extra li)))
              dsym     (huffman/read-sym r dist-table)
              _        (when (> dsym 29)
                         (throw (ex-info "deflate: invalid distance symbol"
                                         {:reason :bad-distance :symbol dsym})))
              distance (+ (nth tables/dist-base dsym) (bits/read-bits r (nth tables/dist-extra dsym)))]
          (when (> distance (count @out))
            (throw (ex-info "deflate: back-reference before the start of the stream"
                            {:reason :bad-distance :distance distance :available (count @out)})))
          (when (and limit (> (+ (- (count @out) base) length) limit))
            (throw (ex-info "deflate: inflated output exceeds limit"
                            {:reason :output-limit :limit limit})))
          (dotimes [_ length]
            (let [o @out]
              (vswap! out conj (nth o (- (count o) distance)))))
          (recur))))))

(defn- copy-stored! [r out base limit]
  (bits/align-reader! r)
  (let [len  (+ (bits/read-byte! r) (* 256 (bits/read-byte! r)))
        nlen (+ (bits/read-byte! r) (* 256 (bits/read-byte! r)))]
    (when-not (= nlen (bit-and (bit-not len) 0xffff))
      (throw (ex-info "deflate: stored block LEN/NLEN mismatch"
                      {:reason :bad-stored-length :len len :nlen nlen})))
    (when (and limit (> (+ (- (count @out) base) len) limit))
      (throw (ex-info "deflate: inflated output exceeds limit"
                      {:reason :output-limit :limit limit})))
    (dotimes [_ len]
      (vswap! out conj (bits/read-byte! r)))))

(defn raw*
  "Inflate a raw DEFLATE stream and report where it ended.

   Options:
     :max-output  ceiling on inflated bytes (default `default-max-output`,
                  nil = unbounded)
     :dictionary  preset dictionary bytes (RFC 1950 FDICT); pre-loaded into the
                  window and excluded from the result

   Returns `{:bytes <vector of unsigned bytes> :end <byte offset past the
   stream>}`. `:end` is what containers need in order to find a trailer or a
   following member."
  ([data] (raw* data nil))
  ([data {:keys [max-output dictionary]
          :or   {max-output default-max-output}}]
   (let [dict (vec dictionary)
         base (count dict)
         r    (bits/reader data)
         out  (volatile! dict)]
     (loop []
       (let [final (bits/read-bit r)
             btype (bits/read-bits r 2)]
         (case btype
           0 (copy-stored! r out base max-output)
           1 (inflate-block! r fixed-lit-table fixed-dist-table out base max-output)
           2 (let [[lit dist] (read-dynamic-tables r)]
               (inflate-block! r lit dist out base max-output))
           (throw (ex-info "deflate: reserved block type" {:reason :bad-block-type :btype btype})))
         (if (= final 1)
           (do (bits/align-reader! r)
               {:bytes (if (pos? base) (subvec @out base) @out)
                :end   (bits/reader-pos r)})
           (recur)))))))

(defn raw
  "Inflate a raw DEFLATE stream (no zlib/gzip header) → vector of unsigned bytes."
  ([data] (:bytes (raw* data nil)))
  ([data opts] (:bytes (raw* data opts))))
