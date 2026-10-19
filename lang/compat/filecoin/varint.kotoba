(ns filecoin.varint
  "Unsigned LEB128 varints, in arithmetic rather than bit operations.

  `multiformats.core/varint` already encodes these, but it shifts with
  `unsigned-bit-shift-right`, which JavaScript evaluates on 32 bits — correct
  for the CID codec numbers that library was written for, silently wrong for
  anything past 2^32. Filecoin puts varints in two places where the value is
  not bounded by a codec table: the ID inside an `f0` address, and the
  namespace inside an `f4`. So this does the same job with `quot`/`rem`,
  which are exact on both runtimes.

  **Ceiling: 2^53-1**, not Go's 2^63-1. That is JavaScript's exact-integer
  limit, and a value above it would be a different number on each runtime
  rather than an error on both. Filecoin actor IDs are in the millions and
  the only allocated `f4` namespace is `10`, so the gap is theoretical — but
  it is a refusal, not a silent truncation.")

(def ^:const max-value 9007199254740991) ; 2^53 - 1

(defn encode
  "n → a vector of 0..255 ints."
  [n]
  (when (or (neg? n) (> n max-value))
    (throw (ex-info "varint: out of range (0 .. 2^53-1)" {:value n})))
  (loop [v n out []]
    (if (< v 0x80)
      (conj out v)
      (recur (quot v 128) (conj out (+ 128 (rem v 128)))))))

(defn decode
  "Read a varint from `bs` (a vector of ints) at `off`.
  → [value bytes-consumed]. Throws on a truncated or over-long encoding."
  ([bs] (decode bs 0))
  ([bs off]
   (loop [i off acc 0 shift 1 n 0]
     (when (>= i (count bs))
       (throw (ex-info "varint: truncated" {:offset off})))
     (when (> n 7)
       (throw (ex-info "varint: too long for 2^53" {:offset off})))
     (let [b (nth bs i)
           acc (+ acc (* (bit-and b 0x7f) shift))]
       (when (> acc max-value)
         (throw (ex-info "varint: out of range (0 .. 2^53-1)" {:offset off})))
       (if (zero? (bit-and b 0x80))
         [acc (inc (- i off))]
         (recur (inc i) acc (* shift 128) (inc n)))))))
