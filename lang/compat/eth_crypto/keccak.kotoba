(ns eth-crypto.keccak
  "Keccak-256 for ClojureScript, with 64-bit lanes on `js/BigInt`.

  WHY THIS FILE EXISTS: `eth-crypto.core`'s Keccak was :clj-only, and the reason
  recorded there is exact — JavaScript's bitwise operators are defined on 32-bit
  integers (ToInt32/ToUint32), so running Keccak-f[1600]'s 64-bit lane
  permutation with plain cljs `bit-xor`/`bit-shift-left` would NOT fail to
  compile. It would silently truncate the high 32 bits of every lane and return a
  wrong hash. A wrong keccak is a wrong address, a wrong selector, and a wrong
  signing digest, all without an error.

  `js/BigInt` gives real arbitrary-width integers, and JS's bitwise operators are
  defined on BigInt too (`&`, `|`, `^`, `~`, `<<`, `>>`), so the permutation can
  be written straightforwardly with lanes masked to 64 bits after each shift.

  Two BigInt hazards this file works around, both verified empirically under nbb
  rather than assumed:
    - `unsigned-bit-shift-right` (`>>>`) THROWS on BigInt (\"BigInts have no
      unsigned right shift\"). For a non-negative BigInt `>>` is already the
      logical shift, so `bit-shift-right` is used everywhere.
    - `zero?` returns FALSE for `(js/BigInt 0)` (it is not a JS number), as do
      `even?`/`odd?` (they throw). Comparisons here use `=` against a BigInt zero
      instead.

  This is a faithful port of the `:clj` implementation in `eth-crypto.core`,
  including the original-Keccak `pad10*1` padding (first pad byte `0x01`, last
  byte `|= 0x80`) that Ethereum uses — NOT NIST SHA3-256's `0x06` padding. The
  test suite asserts the same known-answer vectors on both platforms."
  (:refer-clojure :exclude [bytes]))

(def ^:private ZERO (js/BigInt 0))
(def ^:private MASK64 (js/BigInt "0xFFFFFFFFFFFFFFFF"))
(def ^:private B64 (js/BigInt 64))
(def ^:private BFF (js/BigInt 255))

(def ^:private RC
  "Keccak round constants — the same 24 values as the :clj table, written as
  unsigned hex (BigInt has no fixed width, and every lane is masked to 64 bits)."
  (mapv #(js/BigInt (str "0x" %))
        ["0000000000000001" "0000000000008082" "800000000000808A" "8000000080008000"
         "000000000000808B" "0000000080000001" "8000000080008081" "8000000000008009"
         "000000000000008A" "0000000000000088" "0000000080008009" "000000008000000A"
         "000000008000808B" "800000000000008B" "8000000000008089" "8000000000008003"
         "8000000000008002" "8000000000000080" "000000000000800A" "800000008000000A"
         "8000000080008081" "8000000000008080" "0000000080000001" "8000000080008008"]))

(def ^:private ROT
  "Rotation offsets r[x][y] at flat index x+5y, pre-converted to BigInt."
  (mapv js/BigInt
        [0 1 62 28 27
         36 44 6 55 20
         3 10 43 25 39
         41 45 15 21 8
         18 2 61 56 14]))

(defn- rotl64 [x n]
  (if (= n ZERO)
    x
    (bit-and MASK64
             (bit-or (bit-shift-left x n)
                     (bit-shift-right x (- B64 n))))))

(defn- keccak-f!
  "In-place Keccak-f[1600] on a 25-element JS array of BigInt lanes."
  [a]
  (let [bc (js/Array. 5)
        tmp (js/Array. 25)]
    (dotimes [round 24]
      ;; θ
      (dotimes [x 5]
        (aset bc x (-> (aget a x)
                       (bit-xor (aget a (+ x 5)))
                       (bit-xor (aget a (+ x 10)))
                       (bit-xor (aget a (+ x 15)))
                       (bit-xor (aget a (+ x 20))))))
      (dotimes [x 5]
        (let [d (bit-xor (aget bc (mod (+ x 4) 5))
                         (rotl64 (aget bc (mod (+ x 1) 5)) (js/BigInt 1)))]
          (dotimes [y 5]
            (let [i (+ x (* 5 y))]
              (aset a i (bit-xor (aget a i) d))))))
      ;; ρ + π : tmp[y, 2x+3y] = rot(a[x,y], r[x,y])
      (dotimes [x 5]
        (dotimes [y 5]
          (let [i (+ x (* 5 y))
                j (+ y (* 5 (mod (+ (* 2 x) (* 3 y)) 5)))]
            (aset tmp j (rotl64 (aget a i) (nth ROT i))))))
      ;; χ
      (dotimes [y 5]
        (dotimes [x 5]
          (let [i (+ x (* 5 y))]
            (aset a i (bit-xor (aget tmp i)
                               (bit-and (bit-not (aget tmp (+ (mod (+ x 1) 5) (* 5 y))))
                                        (aget tmp (+ (mod (+ x 2) 5) (* 5 y)))))))))
      ;; ι
      (aset a 0 (bit-xor (aget a 0) (nth RC round))))
    a))

(defn keccak256
  "Keccak-256 (Ethereum's, NOT SHA3-256) of `input` — any seqable of byte values
  0..255. Returns a vector of 32 ints, matching this library's cljs byte
  representation."
  [input]
  (let [rate 136                                    ; 1088 bits, for 256-bit output
        in (mapv #(bit-and % 0xff) (seq input))
        len (count in)
        padlen (- rate (mod len rate))
        total (+ len padlen)
        msg (js/Uint8Array. total)]
    (dotimes [i len] (aset msg i (nth in i)))
    ;; original-Keccak pad10*1
    (aset msg len 0x01)
    (aset msg (dec total) (bit-or (aget msg (dec total)) 0x80))
    (let [a (js/Array. 25)]
      (dotimes [i 25] (aset a i ZERO))
      (loop [off 0]
        (when (< off total)
          (dotimes [i (quot rate 8)]
            (let [base (+ off (* i 8))
                  lane (loop [k 0 acc ZERO]
                         (if (< k 8)
                           (recur (inc k)
                                  (bit-or acc (bit-shift-left (js/BigInt (aget msg (+ base k)))
                                                              (js/BigInt (* 8 k)))))
                           acc))]
              (aset a i (bit-xor (aget a i) lane))))
          (keccak-f! a)
          (recur (+ off rate))))
      (vec (for [i (range 4) k (range 8)]
             (js/Number (bit-and (bit-shift-right (aget a i) (js/BigInt (* 8 k))) BFF)))))))
