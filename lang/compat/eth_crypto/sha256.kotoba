(ns eth-crypto.sha256
  "SHA-256 and HMAC-SHA256 for ClojureScript, in pure cljs.

  Needed because RFC 6979's deterministic-nonce derivation is built on
  HMAC-SHA256, and the `:clj` side gets that from `javax.crypto`. There is no
  portable equivalent, and the browser's `SubtleCrypto` is asynchronous — which
  would make `secp256k1-sign` return a Promise and infect every caller. So this
  is implemented directly.

  Unlike Keccak, SHA-256 is a NATURAL fit for ClojureScript: its whole
  compression function is defined on 32-bit words, which is exactly what JS
  bitwise operators give. No BigInt is needed anywhere here, and no precision is
  at risk. Intermediate sums are coerced back to int32 with `(bit-or 0 …)`
  (JS `|0`, i.e. mod 2^32) and only made unsigned at byte extraction with `>>>`.

  Bytes in and out are seqables of ints 0..255, matching this library's cljs byte
  representation.

  The 64 round constants and 8 initial hash values below are transcribed, which
  is the error-prone part — so `test/eth_crypto/test_cljs_crypto.cljs` asserts
  the two canonical digests (of \"\" and \"abc\"). A single wrong constant changes
  the output completely, so those two vectors are a sufficient check."
  (:refer-clojure :exclude [bytes]))

(def ^:private K
  #js [0x428a2f98 0x71374491 0xb5c0fbcf 0xe9b5dba5 0x3956c25b 0x59f111f1 0x923f82a4 0xab1c5ed5
       0xd807aa98 0x12835b01 0x243185be 0x550c7dc3 0x72be5d74 0x80deb1fe 0x9bdc06a7 0xc19bf174
       0xe49b69c1 0xefbe4786 0x0fc19dc6 0x240ca1cc 0x2de92c6f 0x4a7484aa 0x5cb0a9dc 0x76f988da
       0x983e5152 0xa831c66d 0xb00327c8 0xbf597fc7 0xc6e00bf3 0xd5a79147 0x06ca6351 0x14292967
       0x27b70a85 0x2e1b2138 0x4d2c6dfc 0x53380d13 0x650a7354 0x766a0abb 0x81c2c92e 0x92722c85
       0xa2bfe8a1 0xa81a664b 0xc24b8b70 0xc76c51a3 0xd192e819 0xd6990624 0xf40e3585 0x106aa070
       0x19a4c116 0x1e376c08 0x2748774c 0x34b0bcb5 0x391c0cb3 0x4ed8aa4a 0x5b9cca4f 0x682e6ff3
       0x748f82ee 0x78a5636f 0x84c87814 0x8cc70208 0x90befffa 0xa4506ceb 0xbef9a3f7 0xc67178f2])

(def ^:private H0
  #js [0x6a09e667 0xbb67ae85 0x3c6ef372 0xa54ff53a 0x510e527f 0x9b05688c 0x1f83d9ab 0x5be0cd19])

(defn- i32 [x] (bit-or 0 x))

(defn- rotr [x n]
  (i32 (bit-or (unsigned-bit-shift-right x n) (bit-shift-left x (- 32 n)))))

(def block-size
  "SHA-256's compression block, in bytes. Also HMAC's block size."
  64)

(defn digest
  "SHA-256 of `input` (seqable of ints 0..255) -> vector of 32 ints."
  [input]
  (let [in (mapv #(bit-and % 0xff) (seq input))
        len (count in)
        bitlen (* 8 len)
        ;; pad: 0x80, zeros, then the 64-bit big-endian bit length
        padlen (let [r (mod (+ len 1 8) block-size)]
                 (if (zero? r) 0 (- block-size r)))
        total (+ len 1 padlen 8)
        msg (js/Uint8Array. total)]
    (dotimes [i len] (aset msg i (nth in i)))
    (aset msg len 0x80)
    ;; bit length as 64-bit big-endian. Lengths here are message sizes in a
    ;; wallet (tens of bytes), so the high word is written as 0 explicitly rather
    ;; than pretending to support 2^32-byte inputs.
    (dotimes [i 4] (aset msg (+ total -8 i) 0))
    (dotimes [i 4]
      (aset msg (+ total -4 i)
            (bit-and (unsigned-bit-shift-right bitlen (* 8 (- 3 i))) 0xff)))
    (let [h (.slice H0)
          w (js/Int32Array. 64)]
      (loop [off 0]
        (when (< off total)
          (dotimes [i 16]
            (let [b (+ off (* 4 i))]
              (aset w i (i32 (bit-or (bit-shift-left (aget msg b) 24)
                                     (bit-shift-left (aget msg (+ b 1)) 16)
                                     (bit-shift-left (aget msg (+ b 2)) 8)
                                     (aget msg (+ b 3)))))))
          (loop [i 16]
            (when (< i 64)
              (let [w15 (aget w (- i 15))
                    w2 (aget w (- i 2))
                    s0 (bit-xor (rotr w15 7) (rotr w15 18) (unsigned-bit-shift-right w15 3))
                    s1 (bit-xor (rotr w2 17) (rotr w2 19) (unsigned-bit-shift-right w2 10))]
                (aset w i (i32 (+ (aget w (- i 16)) s0 (aget w (- i 7)) s1))))
              (recur (inc i))))
          (loop [i 0
                 a (aget h 0) b (aget h 1) c (aget h 2) d (aget h 3)
                 e (aget h 4) f (aget h 5) g (aget h 6) hh (aget h 7)]
            (if (< i 64)
              (let [S1 (bit-xor (rotr e 6) (rotr e 11) (rotr e 25))
                    ch (bit-xor (bit-and e f) (bit-and (bit-not e) g))
                    t1 (i32 (+ hh S1 ch (aget K i) (aget w i)))
                    S0 (bit-xor (rotr a 2) (rotr a 13) (rotr a 22))
                    maj (bit-xor (bit-and a b) (bit-and a c) (bit-and b c))
                    t2 (i32 (+ S0 maj))]
                (recur (inc i) (i32 (+ t1 t2)) a b c (i32 (+ d t1)) e f g))
              (do (aset h 0 (i32 (+ (aget h 0) a)))
                  (aset h 1 (i32 (+ (aget h 1) b)))
                  (aset h 2 (i32 (+ (aget h 2) c)))
                  (aset h 3 (i32 (+ (aget h 3) d)))
                  (aset h 4 (i32 (+ (aget h 4) e)))
                  (aset h 5 (i32 (+ (aget h 5) f)))
                  (aset h 6 (i32 (+ (aget h 6) g)))
                  (aset h 7 (i32 (+ (aget h 7) hh))))))
          (recur (+ off block-size))))
      (vec (for [i (range 8) k (range 4)]
             (bit-and (unsigned-bit-shift-right (aget h i) (* 8 (- 3 k))) 0xff))))))

(defn hmac
  "HMAC-SHA256 of `data` under `key` (both seqables of ints 0..255) -> vector of
  32 ints. H(K^opad || H(K^ipad || data))."
  [key data]
  (let [k (mapv #(bit-and % 0xff) (seq key))
        k (if (> (count k) block-size) (digest k) k)
        k (into (vec k) (repeat (- block-size (count k)) 0))
        ipad (mapv #(bit-xor % 0x36) k)
        opad (mapv #(bit-xor % 0x5c) k)]
    (digest (into opad (digest (into ipad (vec (seq data))))))))
