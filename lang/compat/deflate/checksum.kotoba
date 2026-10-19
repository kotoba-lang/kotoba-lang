(ns deflate.checksum
  "Adler-32 (RFC 1950 §9) and CRC-32/ISO-HDLC (RFC 1952 §8) over sequences of
   unsigned bytes.

   Zero-dep and portable: no `java.util.zip.CRC32`, no Node `zlib.crc32`, no
   typed arrays. Both are needed by the container formats in this repo — zlib
   streams carry an Adler-32 trailer, gzip members carry a CRC-32 — and by
   `org-pkware-zip`, whose per-entry integrity check is a CRC-32.

   All values are kept in the unsigned 32-bit domain. ClojureScript's bitwise
   operators return *signed* int32, so every bitwise result is normalised
   through `u32`; on the JVM the same code is already unsigned because Clojure
   integers are 64-bit.")

(defn u32
  "Normalise a bitwise result into the unsigned 32-bit domain."
  [x]
  (if (neg? x) (+ x 4294967296) x))

;; ---------------------------------------------------------------------------
;; Adler-32 (RFC 1950 §9)
;; ---------------------------------------------------------------------------

(def ^:private adler-base 65521)

(defn adler32-update
  "Fold `bytes` into a running Adler-32 `state` (as produced by
   `adler32-init` / a previous `adler32-update`)."
  [state data]
  (loop [s (seq data)
         a (bit-and state 0xffff)
         b (bit-and (unsigned-bit-shift-right state 16) 0xffff)]
    (if-not s
      (+ (* b 65536) a)
      (let [a (rem (+ a (bit-and (first s) 0xff)) adler-base)
            b (rem (+ b a) adler-base)]
        (recur (next s) a b)))))

(defn adler32-init [] 1)

(defn adler32
  "Adler-32 of `bytes` → unsigned 32-bit integer."
  [data]
  (adler32-update (adler32-init) data))

;; ---------------------------------------------------------------------------
;; CRC-32/ISO-HDLC (RFC 1952 §8) — reflected, poly 0xEDB88320
;; ---------------------------------------------------------------------------

(def ^:private crc-table
  (vec (for [n (range 256)]
         (loop [c n k 0]
           (if (= k 8)
             c
             (recur (if (odd? c)
                      (u32 (bit-xor 0xedb88320 (unsigned-bit-shift-right c 1)))
                      (unsigned-bit-shift-right c 1))
                    (inc k)))))))

(defn crc32-init [] 0xffffffff)

(defn crc32-update
  "Fold `bytes` into a running (non-finalised) CRC-32 `state`."
  [state data]
  (loop [s (seq data) c state]
    (if-not s
      c
      (recur (next s)
             (u32 (bit-xor (nth crc-table (bit-and (bit-xor c (bit-and (first s) 0xff)) 0xff))
                           (unsigned-bit-shift-right c 8)))))))

(defn crc32-final
  "Finalise a running CRC-32 `state` into the transmitted value."
  [state]
  (u32 (bit-xor state 0xffffffff)))

(defn crc32
  "CRC-32/ISO-HDLC of `bytes` → unsigned 32-bit integer.
   Matches `java.util.zip.CRC32`, `cksum -o3`, gzip and ZIP."
  [data]
  (crc32-final (crc32-update (crc32-init) data)))
