(ns bonsai.pack
  "Git PACK v2 writer/reader and idx v2 writer. The writer emits full objects;
   the reader also resolves REF_DELTA and OFS_DELTA through bonsai.delta."
  (:require [bonsai.delta :as delta]
            [bonsai.git-object :as object]
            [bonsai.loose-object :as loose]
            [multiformats.core :as mf])
  (:import (java.io ByteArrayOutputStream)
           (java.security MessageDigest)
           (java.util.zip CRC32 Inflater DataFormatException)))

(def type->code {"commit" 1 "tree" 2 "blob" 3 "tag" 4})
(def code->type {1 "commit" 2 "tree" 3 "blob" 4 "tag"})
(defn- ba [xs] (if (bytes? xs) xs (byte-array (map unchecked-byte xs))))
(defn- u8 [b] (bit-and (int b) 0xff))
(defn- sha1 [b] (.digest (MessageDigest/getInstance "SHA-1") ^bytes (ba b)))
(defn- write! [^ByteArrayOutputStream out xs] (.write out ^bytes (ba xs)))
(defn- u32 [n] (byte-array (map unchecked-byte [(bit-shift-right n 24) (bit-shift-right n 16)
                                                 (bit-shift-right n 8) n])))
(defn- read-u32 [v p] (reduce (fn [n i] (bit-or (bit-shift-left n 8) (nth v i))) 0 (range p (+ p 4))))

(defn- entry-header [code size]
  (loop [n (unsigned-bit-shift-right size 4)
         out [(bit-or (bit-shift-left code 4) (bit-and size 0x0f))]]
    (if (zero? n)
      (byte-array (map unchecked-byte out))
      (recur (unsigned-bit-shift-right n 7)
             (conj (update out (dec (count out)) bit-or 0x80) (bit-and n 0x7f))))))

(defn encode
  "Encode full objects [{:type :body}] as PACK v2 bytes with trailer SHA-1."
  [objects]
  (let [out (ByteArrayOutputStream.)]
    (write! out (.getBytes "PACK" "US-ASCII"))
    (write! out (u32 2))
    (write! out (u32 (count objects)))
    (doseq [{:keys [type body]} objects]
      (let [code (type->code type)]
        (when-not code (throw (ex-info "unsupported pack object type" {:type type})))
        (write! out (entry-header code (alength ^bytes (ba body))))
        (write! out (loose/deflate body))))
    (let [without-trailer (.toByteArray out)]
      (write! out (sha1 without-trailer))
      (.toByteArray out))))

(defn- inflate-at [bytes offset expected-size]
  (let [z (Inflater.) out (ByteArrayOutputStream.) buf (byte-array 8192)]
    (try
      (.setInput z ^bytes bytes offset (- (alength ^bytes bytes) offset 20))
      (while (not (.finished z))
        (let [n (.inflate z buf)]
          (when (and (zero? n) (or (.needsInput z) (.needsDictionary z)))
            (throw (ex-info "truncated pack zlib stream" {:reason :truncated-pack})))
          (.write out buf 0 n)))
      (let [body (.toByteArray out) consumed (- (- (alength ^bytes bytes) offset 20) (.getRemaining z))]
        (when-not (= expected-size (alength body))
          (throw (ex-info "pack object size mismatch" {:reason :pack-size})))
        [body consumed])
      (catch DataFormatException e (throw (ex-info "invalid pack zlib" {:reason :invalid-pack} e)))
      (finally (.end z)))))

(defn- parse-header [v p]
  (let [b0 (nth v p) code (bit-and (bit-shift-right b0 4) 7)]
    (loop [b b0 q (inc p) shift 4 size (bit-and b0 0x0f)]
      (if (zero? (bit-and b 0x80)) [code size q]
          (let [b' (nth v q)]
            (recur b' (inc q) (+ shift 7)
                   (bit-or size (bit-shift-left (bit-and b' 0x7f) shift))))))))

(defn decode
  "Decode PACK v2, verify trailer, and resolve deltas. Returns objects in pack
   order with :offset/:type/:body/:oid."
  [pack]
  (let [pack (ba pack) v (vec (map u8 pack)) n (alength pack)]
    (when-not (= "PACK" (String. pack 0 4 "US-ASCII")) (throw (ex-info "bad pack magic" {:reason :pack-magic})))
    (when-not (= 2 (read-u32 v 4)) (throw (ex-info "unsupported pack version" {:reason :pack-version})))
    (when-not (java.util.Arrays/equals ^bytes (sha1 (java.util.Arrays/copyOfRange pack 0 (- n 20)))
                                      ^bytes (java.util.Arrays/copyOfRange pack (- n 20) n))
      (throw (ex-info "pack checksum mismatch" {:reason :pack-checksum})))
    (let [count (read-u32 v 8)]
      (loop [i 0 p 12 out []]
        (if (= i count)
          out
          (let [offset p [code size q0] (parse-header v p)
                [base-ref q] (cond
                               (= code 7) [(mf/hexify (subvec v q0 (+ q0 20))) (+ q0 20)]
                               (= code 6) (loop [q q0 b (nth v q0) d (bit-and (nth v q0) 0x7f)]
                                            (if (zero? (bit-and b 0x80)) [(- offset d) (inc q)]
                                                (let [b' (nth v (inc q))]
                                                  (recur (inc q) b' (+ (bit-shift-left (inc d) 7)
                                                                       (bit-and b' 0x7f))))))
                               :else [nil q0])
                [raw consumed] (inflate-at pack q size)
                base (when base-ref
                       (if (= code 7)
                         (some #(when (= base-ref (:oid %)) %) out)
                         (some #(when (= base-ref (:offset %)) %) out)))
                body (if (#{6 7} code)
                       (do (when-not base (throw (ex-info "delta base missing" {:reason :delta-base})))
                           (delta/apply-delta (:body base) raw))
                       raw)
                type (if (#{6 7} code) (:type base) (code->type code))
                oid (object/object-oid type body)]
            (recur (inc i) (+ q consumed)
                   (conj out {:offset offset :type type :body body :oid oid
                              :packed-end (+ q consumed)}))))))))

(defn encode-index-v2
  "Build Git pack index v2 for decoded objects and the complete pack bytes."
  [objects pack]
  (let [sorted (sort-by :oid objects) out (ByteArrayOutputStream.)
        pack-sum (java.util.Arrays/copyOfRange ^bytes pack (- (alength ^bytes pack) 20) (alength ^bytes pack))]
    (write! out (byte-array (map unchecked-byte [0xff 0x74 0x4f 0x63])))
    (write! out (u32 2))
    (let [freq (frequencies (map #(Integer/parseInt (subs (:oid %) 0 2) 16) sorted))]
      (loop [i 0 cumulative 0]
        (when (< i 256)
          (let [next (+ cumulative (get freq i 0))]
            (write! out (u32 next)) (recur (inc i) next)))))
    (doseq [o sorted] (write! out (mf/unhex (:oid o))))
    (doseq [{:keys [offset packed-end]} sorted]
      (let [crc (CRC32.)]
        (.update crc ^bytes pack offset (- packed-end offset))
        (write! out (u32 (.getValue crc)))))
    (doseq [{:keys [offset]} sorted]
      (when (>= offset 0x80000000) (throw (ex-info "large pack offsets not supported" {:offset offset})))
      (write! out (u32 offset)))
    (write! out pack-sum)
    (let [prefix (.toByteArray out)] (write! out (sha1 prefix)) (.toByteArray out))))

