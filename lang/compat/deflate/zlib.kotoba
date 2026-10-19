(ns deflate.zlib
  "The zlib wrapper around a DEFLATE stream (RFC 1950): a two-byte header, the
   compressed data, and a four-byte big-endian Adler-32 of the *uncompressed*
   bytes.

   The header is validated rather than skipped. RFC 1950 §2.2 requires
   `CM = 8`, `CINFO ≤ 7` and `(CMF·256 + FLG) mod 31 = 0`; a stream failing
   those is not a zlib stream, and reading on regardless is how a mis-framed
   PNG chunk or PDF stream turns into a confusing Huffman error thirty bytes
   later instead of a clear one at byte zero.

   The Adler-32 trailer is checked by default. Pass `:verify-checksum false` for
   the recovery case where a truncated tail is expected and the payload is worth
   having anyway."
  (:require [deflate.bits :as bits]
            [deflate.checksum :as checksum]
            [deflate.inflate :as inflate]
            [deflate.compress :as compress]))

(defn- u32-be [v off]
  (+ (* 16777216 (nth v off))
     (* 65536 (nth v (+ off 1)))
     (* 256 (nth v (+ off 2)))
     (nth v (+ off 3))))

(defn- flevel-for [level]
  (cond (<= level 1) 0, (<= level 5) 1, (= level 6) 2, :else 3))

(defn wrap
  "zlib-compress `data` (RFC 1950). Options are those of `deflate.compress/raw`."
  ([data] (wrap data nil))
  ([data {:keys [level] :or {level 6} :as opts}]
   (let [cmf  0x78                                    ; CM=8 (deflate), CINFO=7 (32 KiB window)
         base (bit-shift-left (flevel-for level) 6)   ; FLEVEL, FDICT=0
         flg  (+ base (mod (- 31 (mod (+ (* cmf 256) base) 31)) 31))
         w    (bits/writer)]
     (bits/write-byte! w cmf)
     (bits/write-byte! w flg)
     (doseq [b (compress/raw data opts)] (bits/write-byte! w b))
     (bits/write-u32-be! w (checksum/adler32 data))
     (bits/finish! w))))

(defn unwrap*
  "Inflate a zlib stream, returning `{:bytes ... :end ... :adler32 ...}`.

   Options: `:dictionary`, `:verify-checksum` (default true), plus anything
   `deflate.inflate/raw*` accepts (`:max-output`)."
  ([data] (unwrap* data nil))
  ([data {:keys [dictionary verify-checksum] :or {verify-checksum true} :as opts}]
   (let [v (vec data)]
     (when (< (count v) 2)
       (throw (ex-info "zlib: stream shorter than its header" {:reason :truncated})))
     (let [cmf   (nth v 0)
           flg   (nth v 1)
           cm    (bit-and cmf 0x0f)
           cinfo (bit-and (unsigned-bit-shift-right cmf 4) 0x0f)
           fdict (bit-test flg 5)]
       (when-not (= cm 8)
         (throw (ex-info "zlib: unsupported compression method"
                         {:reason :bad-header :cm cm})))
       (when (> cinfo 7)
         (throw (ex-info "zlib: window size larger than the format allows"
                         {:reason :bad-header :cinfo cinfo})))
       (when-not (zero? (mod (+ (* cmf 256) flg) 31))
         (throw (ex-info "zlib: header check bits do not validate"
                         {:reason :bad-header :cmf cmf :flg flg})))
       (when (and fdict (nil? dictionary))
         (throw (ex-info "zlib: stream needs a preset dictionary"
                         {:reason :dictionary-required
                          :dictid (when (>= (count v) 6) (u32-be v 2))})))
       (when fdict
         (let [want (u32-be v 2)
               got  (checksum/adler32 dictionary)]
           (when-not (= want got)
             (throw (ex-info "zlib: preset dictionary does not match DICTID"
                             {:reason :dictionary-mismatch :expected want :actual got})))))
       (let [off  (if fdict 6 2)
             {:keys [bytes end]} (inflate/raw* (subvec v off) opts)
             end  (+ off end)
             have (- (count v) end)]
         (when (and verify-checksum (< have 4))
           (throw (ex-info "zlib: stream ends before its Adler-32 trailer"
                           {:reason :truncated :missing (- 4 have)})))
         (let [stored (when (>= have 4) (u32-be v end))]
           (when (and verify-checksum stored)
             (let [actual (checksum/adler32 bytes)]
               (when-not (= stored actual)
                 (throw (ex-info "zlib: Adler-32 mismatch"
                                 {:reason :checksum-mismatch :expected stored :actual actual})))))
           {:bytes bytes :end (+ end (if stored 4 0)) :adler32 stored}))))))

(defn unwrap
  "Inflate a zlib stream (RFC 1950) → vector of unsigned bytes."
  ([data] (:bytes (unwrap* data nil)))
  ([data opts] (:bytes (unwrap* data opts))))
