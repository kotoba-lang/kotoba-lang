(ns eth-crypto.secp256k1
  "secp256k1 for ClojureScript on `js/BigInt`: point arithmetic, public-key
  derivation, RFC 6979 deterministic signing with EIP-2 low-s, and public-key
  recovery.

  WHY IT WAS :clj-ONLY: the `:clj` implementation leans on
  `java.math.BigInteger`'s `modInverse` and `modPow`. JavaScript's `BigInt` gives
  arbitrary-precision integers but NO modular inverse and NO modular exponentiation
  — so those two are implemented here (extended Euclid; square-and-multiply). That
  was the whole gap; everything else was already just integer arithmetic.

  This is a faithful port of `eth-crypto.core`'s `:clj` code, deliberately
  mirroring it operation for operation — same affine point formulas, same RFC 6979
  step order, same recovery-id derivation, same low-s flip. The verification bar
  is correspondingly concrete: the cljs suite asserts the SAME external vectors
  the JVM suite does (the EIP-712 'Ether Mail' spec signature, the EIP-155
  canonical worked example's r/s and raw transaction, and viem's EIP-1559 vectors).
  Matching r and s byte-for-byte on a deterministic-nonce scheme is a strong
  check: it means the field arithmetic, the point multiplication, the HMAC and the
  nonce derivation all agree with two independent implementations.

  BigInt hazards worked around (verified empirically under nbb, not assumed):
  `zero?` returns FALSE for `(js/BigInt 0)`, `even?`/`odd?`/`rem` throw, and
  `unsigned-bit-shift-right` throws. So parity is tested with `bit-and`, equality
  with `=` against a BigInt constant, and right shifts use `bit-shift-right`
  (already logical for non-negative values).

  SIDE-CHANNEL NOTE, stated rather than left implicit: `pt-mul` is a plain
  double-and-add whose work depends on the bits of the scalar, and BigInt
  operations are not constant-time. Nothing here is hardened against timing or
  cache side channels, and JavaScript gives no primitives to make it so. This is
  fine for signing with a key the user already controls in their own browser; it
  is NOT suitable for a shared/multi-tenant process signing on behalf of others.
  Same caveat applies to the `:clj` side (BigInteger is not constant-time either)."
  (:require [eth-crypto.keccak :as keccak]
            [eth-crypto.sha256 :as sha256]))

(def ^:private ZERO (js/BigInt 0))
(def ^:private ONE (js/BigInt 1))
(def ^:private TWO (js/BigInt 2))
(def ^:private THREE (js/BigInt 3))
(def ^:private FOUR (js/BigInt 4))
(def ^:private B8 (js/BigInt 8))
(def ^:private BFF (js/BigInt 255))

(def P (js/BigInt "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F"))
(def N (js/BigInt "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141"))
(def ^:private B (js/BigInt 7))
(def ^:private GX (js/BigInt "0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"))
(def ^:private GY (js/BigInt "0x483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8"))
(def ^:private G [GX GY])
(def ^:private N-HALF (bit-shift-right N ONE))

;; ─── bytes <-> BigInt ────────────────────────────────────────────────────

(defn bytes->big
  "Big-endian unsigned. `bs` is a seqable of ints 0..255."
  [bs]
  (reduce (fn [acc b] (bit-or (bit-shift-left acc B8) (js/BigInt (bit-and b 0xff))))
          ZERO (seq bs)))

(defn big->bytes32
  "Left-padded 32-byte big-endian vector of ints."
  [x]
  (vec (for [i (range 31 -1 -1)]
         (js/Number (bit-and (bit-shift-right x (js/BigInt (* 8 i))) BFF)))))

;; ─── modular arithmetic BigInt does not provide ──────────────────────────

(defn mod-inverse
  "a⁻¹ mod m by the extended Euclidean algorithm. BigInt has no modInverse."
  [a m]
  (loop [old-r (mod a m) r m old-s ONE s ZERO]
    (if (= r ZERO)
      (if (= old-r ONE)
        (mod old-s m)
        (throw (ex-info "secp256k1: value is not invertible mod m"
                        {:a (str a) :m (str m)})))
      (let [q (/ old-r r)]
        (recur r (- old-r (* q r)) s (- old-s (* q s)))))))

(defn mod-pow
  "bᵉ mod m by square-and-multiply. BigInt has no modPow."
  [b e m]
  (loop [b (mod b m) e e acc ONE]
    (if (= e ZERO)
      acc
      (recur (mod (* b b) m)
             (bit-shift-right e ONE)
             (if (= (bit-and e ONE) ONE) (mod (* acc b) m) acc)))))

(defn- odd-big? [x] (= (bit-and x ONE) ONE))

;; ─── affine point arithmetic: y² = x³ + 7 over Fp, nil = infinity ────────

(defn- pt-add [p1 p2]
  (cond
    (nil? p1) p2
    (nil? p2) p1
    :else
    (let [[x1 y1] p1 [x2 y2] p2]
      (if (and (= x1 x2) (= (mod (+ y1 y2) P) ZERO))
        nil                                                   ; P + (−P) = ∞
        (let [m (if (and (= x1 x2) (= y1 y2))
                  (mod (* THREE x1 x1 (mod-inverse (* TWO y1) P)) P)
                  (mod (* (- y2 y1) (mod-inverse (- x2 x1) P)) P))
              x3 (mod (- (- (* m m) x1) x2) P)
              y3 (mod (- (* m (- x1 x3)) y1) P)]
          [x3 y3])))))

(defn- pt-mul [k pt]
  (loop [k k acc nil base pt]
    (if (= k ZERO)
      acc
      (recur (bit-shift-right k ONE)
             (if (odd-big? k) (pt-add acc base) acc)
             (pt-add base base)))))

(defn- mod-sqrt
  "√a mod p, valid because secp256k1's p ≡ 3 (mod 4)."
  [a]
  (mod-pow a (/ (+ P ONE) FOUR) P))

(defn- decompress [x y-odd?]
  (let [y2 (mod (+ (mod-pow x THREE P) B) P)
        y (mod-sqrt y2)
        y (if (= (odd-big? y) (boolean y-odd?)) y (- P y))]
    [x y]))

;; ─── public key / address ────────────────────────────────────────────────

(defn private->public
  "32-byte private key (seqable of ints) -> 64-byte uncompressed public key
  X‖Y (no 0x04 prefix), as a vector of ints."
  [privkey]
  (let [d (bytes->big privkey)
        [qx qy] (pt-mul d G)]
    (into (big->bytes32 qx) (big->bytes32 qy))))

(defn- valid-point? [[x y]]
  (and x y (> x ZERO) (> y ZERO) (< x P) (< y P)
       (= (mod (* y y) P)
          (mod (+ (mod-pow x THREE P) B) P))))

(defn- decode-public-key [pubkey]
  (try
    (let [pubkey (vec (map #(bit-and % 0xff) pubkey))
          prefix (first pubkey)
          point
          (cond
            (and (= 33 (count pubkey)) (contains? #{2 3} prefix))
            (let [x (bytes->big (subvec pubkey 1 33))]
              (when (< x P) (decompress x (= prefix 3))))

            (and (= 65 (count pubkey)) (= 4 prefix))
            [(bytes->big (subvec pubkey 1 33))
             (bytes->big (subvec pubkey 33 65))]

            :else nil)]
      (when (and point (valid-point? point)) point))
    (catch :default _ nil)))

(defn low-s?
  "Whether an ECDSA s scalar is in the lower half curve order."
  [s]
  (and (> s ZERO) (<= s N-HALF)))

(defn verify
  "ECDSA verification over a 32-byte digest and SEC compressed/uncompressed
  public key. Strict DER and low-S are calling-protocol policy."
  [digest {:keys [r s]} pubkey]
  (try
    (boolean
     (let [q (decode-public-key pubkey)]
       (and (= 32 (count digest))
            q (> r ZERO) (> s ZERO) (< r N) (< s N)
            (let [z (bytes->big digest)
                  w (mod-inverse s N)
                  u1 (mod (* z w) N)
                  u2 (mod (* r w) N)
                  result (pt-add (pt-mul u1 G) (pt-mul u2 q))]
              (and result (= r (mod (first result) N)))))))
    (catch :default _ false)))

(defn address-bytes
  "Last 20 bytes of keccak256(uncompressed public key) — the raw address."
  [privkey]
  (vec (drop 12 (keccak/keccak256 (private->public privkey)))))

;; ─── recovery ────────────────────────────────────────────────────────────

(defn ecrecover-pubkey
  "Recover the signer's 64-byte uncompressed public key X‖Y (no 0x04 prefix)
  from a 32-byte `digest` and a 65-byte `sig` (r‖s‖v).

  Ethereum keccaks this and keeps 20 bytes; a Filecoin f1 address is
  BLAKE2b-160 of 0x04 ‖ this. Recovery and address derivation are separate."
  [digest sig]
  (let [sig (vec (map #(bit-and % 0xff) (seq sig)))
        r (bytes->big (subvec sig 0 32))
        s (bytes->big (subvec sig 32 64))
        v (nth sig 64)
        rec-id (if (>= v 27) (- v 27) v)
        e (bytes->big digest)
        x (+ r (* N (js/BigInt (bit-shift-right rec-id 1))))
        R (decompress x (odd? (bit-and rec-id 1)))
        r-inv (mod-inverse r N)
        sR (pt-mul s R)
        eG (pt-mul e G)
        neg-eG [(first eG) (mod (- (second eG)) P)]
        [qx qy] (pt-mul r-inv (pt-add sR neg-eG))]
    (into (big->bytes32 qx) (big->bytes32 qy))))

(defn ecrecover
  "Recover the signer's 20 address bytes from a 32-byte `digest` and a 65-byte
  `sig` (r‖s‖v), v ∈ {27,28} or {0,1}."
  [digest sig]
  (vec (drop 12 (keccak/keccak256 (ecrecover-pubkey digest sig)))))

;; ─── RFC 6979 deterministic signing + EIP-2 low-s ────────────────────────

(defn sign
  "Deterministic ECDSA over secp256k1 of a 32-byte `digest` with a 32-byte
  `privkey`. RFC 6979 (HMAC-SHA256) nonce, EIP-2 low-s normalization.
  Returns {:r BigInt :s BigInt :recovery-id 0|1}.

  Deterministic is not a convenience here — a repeated or biased nonce leaks the
  private key outright, and there is no way to audit a random one after the fact."
  [privkey digest]
  (let [z (bytes->big digest)
        d (bytes->big privkey)
        x-oct (big->bytes32 d)                       ; int2octets(x)
        h-oct (big->bytes32 (mod z N))               ; bits2octets(h1)
        b00 [0x00]
        b01 [0x01]
        V0 (vec (repeat 32 0x01))
        K0 (vec (repeat 32 0x00))
        K1 (sha256/hmac K0 (concat V0 b00 x-oct h-oct))
        V1 (sha256/hmac K1 V0)
        K2 (sha256/hmac K1 (concat V1 b01 x-oct h-oct))
        V2 (sha256/hmac K2 V1)]
    ;; HMAC-SHA256's output is 32 bytes = qlen/8, so one block is the candidate.
    (loop [K K2 V V2]
      (let [T (sha256/hmac K V)
            k (bytes->big T)
            result
            (when (and (not= k ZERO) (< k N))
              (let [[rx ry] (pt-mul k G)
                    r (mod rx N)]
                (when-not (= r ZERO)
                  (let [s (mod (* (mod-inverse k N) (+ z (* r d))) N)]
                    (when-not (= s ZERO)
                      (let [rec (bit-or (if (odd-big? ry) 1 0)
                                        (if (>= rx N) 2 0))]
                        (if (> s N-HALF)
                          {:r r :s (- N s) :recovery-id (bit-xor rec 1)}
                          {:r r :s s :recovery-id rec})))))))]
        (or result
            (let [K' (sha256/hmac K (concat T b00))
                  V' (sha256/hmac K' T)]
              (recur K' V')))))))
