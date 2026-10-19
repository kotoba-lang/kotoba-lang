(ns erasure.gf
  "GF(2^8) arithmetic — the field every erasure code in this library is built
  over.

  **Field choice.** Reduction polynomial `0x11D`
  (x^8 + x^4 + x^3 + x^2 + 1) with alpha = 2 as the generator. This is the
  classic Reed-Solomon field (Jerasure, klauspost/reedsolomon, and the
  CD/DVD codes all use it), and it is NOT the AES field `0x11B` — in `0x11B`
  the element 2 has order 51 and is therefore not primitive, which is why AES
  tables are generated from 3. Picking `0x11D` means the log/exp tables below
  can be walked by repeated doubling, and it means shards produced here
  interoperate with every other RS implementation over the same polynomial.

  **Two multiplies, on purpose.** `mul-def` is the definition — a table-free
  Russian-peasant loop. `mul` is the same function via log/exp tables and is
  what the rest of the library calls. `mul-def` exists because it is what the
  `.kotoba` port (`kotoba/erasure_core.kotoba`) is checked against: the port
  computes rather than tabulates, so a byte-exact parity test needs a
  computing reference on this side too. `erasure.gf-test` asserts the two
  agree on all 65,536 pairs.

  Elements are plain integers in [0,255]. Addition in a characteristic-2
  field IS xor, so `add` and `sub` are the same function; both names exist
  because the algebra reads better with the right one."
  (:refer-clojure :exclude [mod]))

(def ^:const field-poly
  "x^8 + x^4 + x^3 + x^2 + 1. See the namespace docstring for why not 0x11B."
  0x11d)

(def ^:const order 256)

;; --- addition -------------------------------------------------------------

(defn add
  "Field addition. Characteristic 2, so this is xor and is its own inverse."
  [a b]
  (bit-xor a b))

(def sub
  "Field subtraction. Identical to `add` in characteristic 2 — named
  separately so that solving code can say what it means."
  add)

;; --- multiplication -------------------------------------------------------

(defn mul-def
  "Multiply by shift-and-add (Russian peasant), no tables.

  This is the specification of the field's multiplication. Each round folds
  bit 0 of `b` with the current `a`, then doubles `a` and reduces modulo
  `field-poly` when it overflows 8 bits."
  [a b]
  (loop [a (bit-and a 0xff)
         b (bit-and b 0xff)
         acc 0]
    (if (zero? b)
      acc
      (recur (let [doubled (bit-shift-left a 1)]
               (if (>= doubled 0x100)
                 (bit-xor doubled field-poly)
                 doubled))
             (bit-shift-right b 1)
             (if (odd? b) (bit-xor acc a) acc)))))

(def exp-table
  "alpha^i for i in [0,511]. Carried past the period (alpha^255 = 1) so that
  `mul` can index by a raw sum of logs without a modulo."
  (vec (take 512 (iterate #(mul-def % 2) 1))))

(def log-table
  "Discrete log base alpha. Index 0 is unused — zero has no logarithm — and
  is left at 0 so the vector stays dense; every caller guards on zero first."
  (reduce (fn [t i] (assoc t (nth exp-table i) i))
          (vec (repeat order 0))
          (range 255)))

(defn mul
  "Field multiplication via logs. Agrees with `mul-def` everywhere; this is
  the one the library uses."
  [a b]
  (if (or (zero? a) (zero? b))
    0
    (nth exp-table (+ (nth log-table a) (nth log-table b)))))

(defn inv
  "Multiplicative inverse.

  Zero has none. This returns 0 rather than throwing, so the namespace stays
  free of host-specific exception types and usable from ClojureScript and
  nbb unchanged; the matrix code guards before calling and treats a zero
  pivot as a singular system."
  [a]
  (if (zero? a)
    0
    (nth exp-table (- 255 (nth log-table a)))))

(defn div
  "Field division. `b` must be non-zero."
  [a b]
  (mul a (inv b)))

(defn pow
  "Field exponentiation by a non-negative integer exponent."
  [a e]
  (cond
    (zero? e) 1
    (zero? a) 0
    :else (nth exp-table (clojure.core/mod (* (nth log-table a) e) 255))))

;; --- shard-level helpers --------------------------------------------------

(defn zero-shard
  "A shard of `len` zero bytes — what every parity accumulator starts from."
  [len]
  (vec (repeat len 0)))

(defn scale-add
  "`acc + c * xs`, elementwise over a whole shard — the inner loop of every
  encode and every decode.

  This is the one function the design (ADR-2607299200 section 5) designates
  as *mechanism* rather than decision: a host provider is expected to replace
  it with a SIMD or table-driven implementation and prove byte-equality
  against this definition. Nothing above it may know which one it got."
  [acc c xs]
  (if (zero? c)
    acc
    (mapv (fn [a x] (bit-xor a (mul c x))) acc xs)))

(defn xor-shards
  "Xor a sequence of equal-length shards together. Local parity is exactly
  this, which is why local repair costs no multiplies."
  [shards len]
  (reduce (fn [acc s] (mapv bit-xor acc s)) (zero-shard len) shards))
