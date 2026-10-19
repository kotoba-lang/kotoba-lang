;; kotoba.bytes.sha256 — pure SHA-256 (FIPS 180-4) + HMAC-SHA256 (RFC 2104),
;; portable across JVM Clojure and ClojureScript. Same shape as
;; `kotoba.bytes.sha1`: no platform crypto API, bytes in and bytes out as
;; `kotoba.bytes` byte-vectors (vector<int 0..255>).
;;
;; Why this exists rather than a wrapper over MessageDigest / crypto.subtle:
;; those two disagree on more than spelling. `MessageDigest` returns a value;
;; `crypto.subtle.digest` returns a Promise. Every consumer that wanted one
;; digest function across both runtimes had to either fork its own code path or
;; make the whole call chain async — so instead of doing that once, this
;; workspace did it 112 times, each repo hand-rolling its own `sha256` and its
;; own `hmac`. A pure implementation is **synchronous on both runtimes**, which
;; removes the reason those forks existed.
;;
;; The trade is speed: this is Clojure arithmetic, not a native digest. It is
;; the right choice for signatures, identifiers, checksums and canonical hashes
;; — kilobytes at a time. For hashing large payloads, keep using the platform
;; primitive behind whatever seam you already have (e.g. `sigv4.protocols`).
(ns kotoba.bytes.sha256
  (:require [kotoba.bytes :as b]))

(def ^:private mask32 0xffffffff)

(defn- m32 [x] (bit-and x mask32))

(defn- rotr32 [x n]
  (m32 (bit-or (unsigned-bit-shift-right x n) (bit-shift-left x (- 32 n)))))

(defn- add32 [& xs] (m32 (reduce + 0 xs)))

;; FIPS 180-4 §5.3.3 — the first 32 bits of the fractional parts of the square
;; roots of the first eight primes.
(def ^:private h-init
  [0x6a09e667 0xbb67ae85 0x3c6ef372 0xa54ff53a
   0x510e527f 0x9b05688c 0x1f83d9ab 0x5be0cd19])

;; §4.2.2 — cube roots of the first sixty-four primes.
(def ^:private k
  [0x428a2f98 0x71374491 0xb5c0fbcf 0xe9b5dba5 0x3956c25b 0x59f111f1 0x923f82a4 0xab1c5ed5
   0xd807aa98 0x12835b01 0x243185be 0x550c7dc3 0x72be5d74 0x80deb1fe 0x9bdc06a7 0xc19bf174
   0xe49b69c1 0xefbe4786 0x0fc19dc6 0x240ca1cc 0x2de92c6f 0x4a7484aa 0x5cb0a9dc 0x76f988da
   0x983e5152 0xa831c66d 0xb00327c8 0xbf597fc7 0xc6e00bf3 0xd5a79147 0x06ca6351 0x14292967
   0x27b70a85 0x2e1b2138 0x4d2c6dfc 0x53380d13 0x650a7354 0x766a0abb 0x81c2c92e 0x92722c85
   0xa2bfe8a1 0xa81a664b 0xc24b8b70 0xc76c51a3 0xd192e819 0xd6990624 0xf40e3585 0x106aa070
   0x19a4c116 0x1e376c08 0x2748774c 0x34b0bcb5 0x391c0cb3 0x4ed8aa4a 0x5b9cca4f 0x682e6ff3
   0x748f82ee 0x78a5636f 0x84c87814 0x8cc70208 0x90befffa 0xa4506ceb 0xbef9a3f7 0xc67178f2])

(defn- pad-message
  "FIPS 180-4 §5.1.1: append 0x80, zero-pad to 56 mod 64, then the original
   bit-length as a big-endian 64-bit integer — written as two 32-bit words,
   since a single 64-bit shift is not cljs-safe."
  [msg]
  (let [byte-len (count msg)
        bit-len (* byte-len 8)
        with-marker (conj (vec msg) 0x80)
        rem64 (mod (count with-marker) 64)
        pad-len (mod (- 56 rem64) 64)
        padded (into with-marker (repeat pad-len 0))
        hi (m32 (quot bit-len 0x100000000))
        lo (m32 bit-len)]
    (-> padded
        (into [(bit-and (unsigned-bit-shift-right hi 24) 0xff)
               (bit-and (unsigned-bit-shift-right hi 16) 0xff)
               (bit-and (unsigned-bit-shift-right hi 8) 0xff)
               (bit-and hi 0xff)])
        (into [(bit-and (unsigned-bit-shift-right lo 24) 0xff)
               (bit-and (unsigned-bit-shift-right lo 16) 0xff)
               (bit-and (unsigned-bit-shift-right lo 8) 0xff)
               (bit-and lo 0xff)]))))

(defn- schedule
  "§6.2.2 step 1: the 64-word message schedule for one 512-bit block."
  [block]
  (let [w0 (mapv (fn [i]
                   (let [o (* i 4)]
                     (m32 (bit-or (bit-shift-left (nth block o) 24)
                                  (bit-shift-left (nth block (+ o 1)) 16)
                                  (bit-shift-left (nth block (+ o 2)) 8)
                                  (nth block (+ o 3))))))
                 (range 16))]
    (reduce (fn [w t]
              (let [w15 (nth w (- t 15))
                    w2  (nth w (- t 2))
                    s0 (bit-xor (rotr32 w15 7) (rotr32 w15 18) (unsigned-bit-shift-right w15 3))
                    s1 (bit-xor (rotr32 w2 17) (rotr32 w2 19) (unsigned-bit-shift-right w2 10))]
                (conj w (add32 (nth w (- t 16)) s0 (nth w (- t 7)) s1))))
            w0
            (range 16 64))))

(defn- compress
  "§6.2.2 steps 2-4: one block's contribution to the running hash."
  [h block]
  (let [w (schedule block)
        [a b c d e f g hh]
        (reduce (fn [[a b c d e f g hh] t]
                  (let [s1 (bit-xor (rotr32 e 6) (rotr32 e 11) (rotr32 e 25))
                        ch (bit-xor (bit-and e f) (bit-and (bit-not e) g))
                        t1 (add32 hh s1 (m32 ch) (nth k t) (nth w t))
                        s0 (bit-xor (rotr32 a 2) (rotr32 a 13) (rotr32 a 22))
                        maj (bit-xor (bit-and a b) (bit-and a c) (bit-and b c))
                        t2 (add32 s0 (m32 maj))]
                    [(add32 t1 t2) a b c (add32 d t1) e f g]))
                h
                (range 64))]
    (mapv add32 h [a b c d e f g hh])))

(defn sha256-bytes
  "SHA-256 of a byte-vector → a 32-byte vector."
  [msg]
  (let [padded (pad-message msg)
        h (reduce (fn [h i] (compress h (subvec padded i (+ i 64))))
                  h-init
                  (range 0 (count padded) 64))]
    (into [] (mapcat (fn [x] [(bit-and (unsigned-bit-shift-right x 24) 0xff)
                              (bit-and (unsigned-bit-shift-right x 16) 0xff)
                              (bit-and (unsigned-bit-shift-right x 8) 0xff)
                              (bit-and x 0xff)]))
          h)))

(def ^:private block-size 64)

(defn hmac-sha256
  "HMAC-SHA256 (RFC 2104) of a byte-vector under a byte-vector key → 32 bytes.
   Keys longer than the 64-byte block are hashed first, shorter ones zero-padded."
  [key msg]
  (let [k (vec (if (> (count key) block-size) (sha256-bytes (vec key)) key))
        k (into k (repeat (- block-size (count k)) 0))
        ipad (mapv #(bit-xor % 0x36) k)
        opad (mapv #(bit-xor % 0x5c) k)]
    (sha256-bytes (into opad (sha256-bytes (into ipad (vec msg)))))))

;; ── string-in / hex-out conveniences ─────────────────────────────────────────
;;
;; The overwhelmingly common shape at the call sites this replaces is
;; "UTF-8 string in, lowercase hex out", so it is worth not making every caller
;; spell the conversion out.

(defn sha256-hex
  "Lowercase hex SHA-256 of a UTF-8 string or a byte-vector."
  [x]
  (b/hex (sha256-bytes (if (string? x) (b/utf8-encode x) x))))

(defn hmac-sha256-hex
  "Lowercase hex HMAC-SHA256. `key` and `msg` may each be a UTF-8 string or a
   byte-vector."
  [key msg]
  (b/hex (hmac-sha256 (if (string? key) (b/utf8-encode key) key)
                      (if (string? msg) (b/utf8-encode msg) msg))))
