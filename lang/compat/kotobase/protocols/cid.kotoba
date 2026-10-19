(ns kotobase.protocols.cid
  "Real content addressing, pure half (ADR-2607176000, resolves the
  'fingerprints are not CIDs' follow-up of ADR-2607171700 for the raw
  codec).

  This namespace is DIGEST-AGNOSTIC on purpose: hashing is a host
  capability (WebCrypto in the Worker shell, node:crypto under nbb), so
  callers hand us the 32-byte sha2-256 digest and we do the pure part —
  multihash/CID framing and multibase base32. The result is a standard
  CIDv1 (base32, codec raw 0x55, sha2-256) — the `bafkrei…` form any
  IPFS gateway/tool can verify."
  (:require [clojure.string :as str]))

(def ^:private b32-alphabet "abcdefghijklmnopqrstuvwxyz234567")

(defn base32-lower
  "RFC 4648 base32 (lower-case, no padding) over a seq of byte ints.
  The accumulator is re-masked to its live bits after every byte so it
  never exceeds 12 bits — safe under cljs 32-bit bit ops."
  [bytes]
  (let [[out acc bits]
        (reduce
         (fn [[out acc bits] b]
           (let [acc (bit-or (bit-shift-left acc 8) (bit-and (long b) 0xff))
                 bits (+ bits 8)
                 [out bits'] (loop [out out bits bits]
                               (if (>= bits 5)
                                 (recur (str out (nth b32-alphabet
                                                      (bit-and (bit-shift-right acc (- bits 5))
                                                               0x1f)))
                                        (- bits 5))
                                 [out bits]))]
             [out (bit-and acc (dec (bit-shift-left 1 bits'))) bits']))
         ["" 0 0]
         bytes)]
    (if (pos? bits)
      (str out (nth b32-alphabet (bit-and (bit-shift-left acc (- 5 bits)) 0x1f)))
      out)))

(defn cidv1-raw-sha256
  "32-byte sha2-256 digest (seq of ints) → CIDv1 string
  (multibase b + version 1 + codec raw 0x55 + multihash 0x12 0x20)."
  [digest-bytes]
  {:pre [(= 32 (count digest-bytes))]}
  (str "b" (base32-lower (concat [0x01 0x55 0x12 0x20] digest-bytes))))

(defn cid?
  "Cheap shape check for the CIDs this library mints."
  [s]
  (boolean (and (string? s) (re-matches #"bafkrei[a-z2-7]{52}" s))))
