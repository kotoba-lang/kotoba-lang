(ns deflate.tables
  "The constant tables of RFC 1951, shared by the decoder and the encoder so the
   two can never drift apart.

   §3.2.5 gives the length and distance alphabets as base value + extra bits;
   decoding reads them left to right, encoding needs the inverse mapping
   (`len-code` / `dist-code`). §3.2.6 gives the fixed Huffman code lengths and
   §3.2.7 the order in which code-length-code lengths are transmitted.")

(def len-base   [3 4 5 6 7 8 9 10 11 13 15 17 19 23 27 31 35 43 51 59 67 83 99 115 131 163 195 227 258])
(def len-extra  [0 0 0 0 0 0 0 0 1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4 5 5 5 5 0])
(def dist-base  [1 2 3 4 5 7 9 13 17 25 33 49 65 97 129 193 257 385 513 769 1025 1537 2049 3073 4097 6145 8193 12289 16385 24577])
(def dist-extra [0 0 0 0 1 1 2 2 3 3 4 4 5 5 6 6 7 7 8 8 9 9 10 10 11 11 12 12 13 13])
(def clc-order  [16 17 18 0 8 7 9 6 10 5 11 4 12 3 13 2 14 1 15])

(def fixed-lit-lengths
  (vec (concat (repeat 144 8) (repeat 112 9) (repeat 24 7) (repeat 8 8))))

(def fixed-dist-lengths (vec (repeat 30 5)))

(def max-code-length
  "RFC 1951 §3.2.7 — no Huffman code in a DEFLATE stream may exceed 15 bits."
  15)

(def max-clc-length
  "The code-length alphabet itself is limited to 7 bits."
  7)

(defn len-code
  "Inverse of §3.2.5 for a match length 3–258 → [symbol extra-bits extra-value].
   Length 258 has its own symbol (285) with no extra bits, which is why the scan
   runs downward from the top of the table."
  [len]
  (loop [i 28]
    (if (<= (nth len-base i) len)
      [(+ 257 i) (nth len-extra i) (- len (nth len-base i))]
      (recur (dec i)))))

(defn dist-code
  "Inverse of §3.2.5 for a distance 1–32768 → [symbol extra-bits extra-value]."
  [dist]
  (loop [i 29]
    (if (<= (nth dist-base i) dist)
      [i (nth dist-extra i) (- dist (nth dist-base i))]
      (recur (dec i)))))
