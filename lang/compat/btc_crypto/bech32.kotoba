(ns btc-crypto.bech32
  "Bech32 (BIP-173, witness v0) and Bech32m (BIP-350, witness v1+/Taproot)
  encoding, plus the segwit address <-> (witver, program) mapping from
  BIP-173's reference pseudocode (segwit_addr.py)."
  (:require [clojure.string :as str]))

(def ^:private CHARSET "qpzry9x8gf2tvdw0s3jn54khce6mua7l")
(def ^:private CHARSET-IDX (into {} (map-indexed (fn [i c] [c i]) CHARSET)))

(def ^:private GEN [0x3b6a57b2 0x26508e6d 0x1ea119fa 0x3d4233dd 0x2a1462b3])

(defn- polymod ^long [values]
  (loop [chk 1 vs values]
    (if (empty? vs)
      chk
      (let [top (bit-shift-right chk 25)
            chk (bit-xor (bit-shift-left (bit-and chk 0x1ffffff) 5) (first vs))
            chk (reduce (fn [c i] (if (bit-test top i) (bit-xor c (nth GEN i)) c)) chk (range 5))]
        (recur chk (rest vs))))))

(defn- hrp-expand [^String hrp]
  (concat (map #(bit-shift-right (int %) 5) hrp) [0] (map #(bit-and (int %) 31) hrp)))

(def ^:private BECH32-CONST 1)
(def ^:private BECH32M-CONST 0x2bc830a3)

(defn- const-for [spec] (case spec :bech32 BECH32-CONST :bech32m BECH32M-CONST))

(defn- create-checksum [spec ^String hrp data]
  (let [values (concat (hrp-expand hrp) data [0 0 0 0 0 0])
        polymod-val (bit-xor (polymod values) (const-for spec))]
    (mapv #(bit-and (bit-shift-right polymod-val (* 5 (- 5 %))) 31) (range 6))))

(defn verify-checksum
  "Returns :bech32, :bech32m, or nil (invalid) for `hrp` + 5-bit `data` words
  (checksum included)."
  [^String hrp data]
  (let [pm (polymod (concat (hrp-expand hrp) data))]
    (cond (= pm BECH32-CONST) :bech32
          (= pm BECH32M-CONST) :bech32m
          :else nil)))

(defn encode
  "Encode `hrp` + 5-bit `data` words (spec :bech32 or :bech32m) to a bech32
  string (lowercase)."
  ^String [spec ^String hrp data]
  (let [combined (concat data (create-checksum spec hrp data))]
    (str hrp "1" (apply str (map #(.charAt CHARSET (int %)) combined)))))

(defn decode
  "Decode a bech32/bech32m string. Returns {:hrp .. :data [5-bit words, sans
  checksum] :spec :bech32|:bech32m} or throws."
  [^String s]
  (let [s (if (or (= s (str/lower-case s)) (= s (str/upper-case s)))
            (str/lower-case s)
            (throw (ex-info "bech32: mixed case" {:s s})))
        pos (str/last-index-of s "1")]
    (when (or (nil? pos) (< pos 1) (> (+ pos 7) (count s)))
      (throw (ex-info "bech32: no separator / too short" {:s s})))
    (let [hrp (subs s 0 pos)
          data-part (subs s (inc pos))
          data (mapv (fn [c]
                       (let [d (get CHARSET-IDX c)]
                         (when (nil? d) (throw (ex-info "bech32: invalid char" {:c c})))
                         d))
                     data-part)
          spec (verify-checksum hrp data)]
      (when (nil? spec) (throw (ex-info "bech32: invalid checksum" {:s s})))
      {:hrp hrp :data (vec (drop-last 6 data)) :spec spec})))

;; ─── 5-bit <-> 8-bit conversion (generic bit-regrouping, BIP-173 convertbits) ───

(defn convert-bits
  "Regroup a seq of ints from `frombits`-bit words to `tobits`-bit words
  (BIP-173 `convertbits`). `pad` true allows a short final group (encoding
  direction); false requires the input to represent whole `tobits` groups
  with no non-zero padding (decoding direction)."
  [data frombits tobits pad]
  (let [maxv (dec (bit-shift-left 1 tobits))]
    (loop [acc 0 bits 0 out (transient []) ds (seq data)]
      (if ds
        (let [value (long (first ds))]
          (when (or (neg? value) (pos? (bit-shift-right value frombits)))
            (throw (ex-info "bech32: invalid data value" {:value value})))
          (let [acc (bit-or (bit-shift-left acc frombits) value)
                bits (+ bits frombits)
                [acc bits out] (loop [acc acc bits bits out out]
                                 (if (>= bits tobits)
                                   (let [bits (- bits tobits)]
                                     (recur acc bits (conj! out (bit-and (bit-shift-right acc bits) maxv))))
                                   [acc bits out]))]
            (recur acc bits out (next ds))))
        (cond
          pad (persistent! (if (pos? bits)
                              (conj! out (bit-and (bit-shift-left acc (- tobits bits)) maxv))
                              out))
          (or (>= bits frombits) (not (zero? (bit-and (bit-shift-left acc (- tobits bits)) maxv))))
          (throw (ex-info "bech32: invalid padding" {}))
          :else (persistent! out))))))

;; ─── segwit address <-> (witver, program) (BIP-173 segwit_addr.py) ──────────

(defn encode-segwit-address
  "Encode a segwit `witver` (0-16) + `program` (byte seq, 2-40 bytes) under
  `hrp` (\"bc\"/\"tb\"). Uses bech32 for witver 0, bech32m for witver >= 1
  (BIP-350)."
  ^String [^String hrp witver program]
  (let [spec (if (zero? witver) :bech32 :bech32m)
        data (cons witver (convert-bits (map #(bit-and (long %) 0xff) program) 8 5 true))]
    (encode spec hrp data)))

(defn decode-segwit-address
  "Decode a segwit bech32/bech32m address. Returns {:hrp :witver :program
  (byte vector)} or throws on any structural/checksum-spec mismatch (BIP-350:
  witver 0 must use bech32, witver >= 1 must use bech32m)."
  [^String s]
  (let [{:keys [hrp data spec]} (decode s)
        witver (first data)
        program (convert-bits (rest data) 5 8 false)]
    (when (or (nil? witver) (> witver 16))
      (throw (ex-info "bech32: invalid witness version" {:witver witver})))
    (when (or (< (count program) 2) (> (count program) 40))
      (throw (ex-info "bech32: invalid program length" {:len (count program)})))
    (when (and (= witver 0) (not (contains? #{2 20 32} (count program))))
      (throw (ex-info "bech32: invalid program length for witness v0" {:len (count program)})))
    (when (not= spec (if (zero? witver) :bech32 :bech32m))
      (throw (ex-info "bech32: wrong checksum spec for witness version" {:witver witver :spec spec})))
    {:hrp hrp :witver witver :program (vec program)}))
