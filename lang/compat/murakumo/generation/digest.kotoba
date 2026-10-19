(ns murakumo.generation.digest
  "sha2-256 hex → raw CIDv1, the one conversion that makes a generated
  artifact have a SINGLE name.

  murakumo states an artifact's integrity as `contentHash: \"sha256:<hex>\"`.
  kotobase names archive objects by exactly that digest (`PUT /ipfs/:cid`,
  raw CIDv1). So the fleet's content hash IS the storage name — no re-hash,
  no second naming scheme, and a re-store of identical bytes is a no-op.

  Pure and dependency-free so it loads on both hosts (JVM consumers and nbb
  operator scripts) without pulling an IPLD stack in for twenty lines of
  base32."
  (:require [clojure.string :as str]))

(def ^:private b32-alphabet "abcdefghijklmnopqrstuvwxyz234567")

(defn- b32-char [i] (nth b32-alphabet i))

(defn base32-encode
  "bytes (seq of unsigned ints) -> RFC 4648 lowercase base32, unpadded.

  Carries at most 12 bits at a time (8 in, 5 out), so one reduce over the
  bytes emits one or two characters per step and the tail flushes the rest —
  no bignum, no padding, and no host interop, so this is the same code on the
  JVM and in nbb."
  [bs]
  (let [[acc bits out]
        (reduce (fn [[acc bits out] b]
                  (let [acc (bit-or (bit-shift-left acc 8) (bit-and (long b) 0xff))
                        bits (+ bits 8)
                        [acc bits out]
                        (loop [acc acc bits bits out out]
                          (if (>= bits 5)
                            (recur acc (- bits 5)
                                   (conj out (b32-char
                                              (bit-and (unsigned-bit-shift-right acc (- bits 5)) 31))))
                            [acc bits out]))]
                    [(bit-and acc (dec (bit-shift-left 1 (max bits 1)))) bits out]))
                [0 0 []]
                bs)
        out (if (pos? bits)
              (conj out (b32-char (bit-and (bit-shift-left acc (- 5 bits)) 31)))
              out)]
    (apply str out)))

(defn raw-cid
  "32-byte sha2-256 digest (seq of ints) -> bafkrei… raw CIDv1.
  Prefix 0x01 0x55 0x12 0x20 = CIDv1 / raw / sha2-256 / 32 bytes."
  [digest]
  (let [ds (vec digest)]
    (when-not (= 32 (count ds))
      (throw (ex-info "sha2-256 digest must be 32 bytes" {:length (count ds)})))
    (str "b" (base32-encode (into [0x01 0x55 0x12 0x20] ds)))))

(def ^:private hex-index
  (into {} (map-indexed (fn [i c] [c i]) "0123456789abcdef")))

(defn hex->bytes
  "Hex string -> byte int vector. nil on any non-hex character or an odd
  length, so a malformed digest can never be half-decoded into a
  plausible-looking CID."
  [s]
  (let [cs (vec (str/lower-case (str (or s ""))))]
    (when (and (seq cs) (even? (count cs)))
      (reduce (fn [acc [hi lo]]
                (if (and (hex-index hi) (hex-index lo))
                  (conj acc (+ (* 16 (hex-index hi)) (hex-index lo)))
                  (reduced nil)))
              []
              (partition 2 cs)))))

(defn sha256-hex->cid
  "sha2-256 hex -> bafkrei… raw CIDv1, or nil when it is not a 32-byte digest."
  [hex]
  (let [bs (hex->bytes hex)]
    (when (= 32 (count bs)) (raw-cid bs))))
