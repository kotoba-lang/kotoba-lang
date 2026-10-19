(ns eth-crypto.js-core
  "The ClojureScript half of `eth-crypto.core`: EIP-712 value encoding, RLP, and
  transaction signing, built on `eth-crypto.keccak` and `eth-crypto.secp256k1`.

  WHY A SEPARATE NAMESPACE RATHER THAN A REFACTOR: `eth-crypto.core`'s `:clj`
  implementation is verified against spec vectors and already in use. Making its
  encoding layer platform-neutral would mean rewriting working crypto plumbing to
  add a runtime — a bad trade. So this mirrors it operation for operation and
  `core`'s `:cljs` branch delegates here.

  The cost of mirroring is drift, and the guard against drift is that BOTH halves
  are asserted against the SAME external vectors: the EIP-712 'Ether Mail' spec
  digest and signature, the EIP-155 canonical worked example's signing hash / r /
  s / raw transaction, and viem's EIP-1559 digest / raw tx / tx hash for both
  yParity values. If the two halves diverge anywhere that matters, one of the two
  suites goes red.

  BYTE-STRING REPRESENTATION, and why RLP forces the question: on the JVM a byte
  string is a `byte-array` (not `sequential?`) and an RLP list is a Clojure
  collection (`sequential?`), so `rlp-encode` can tell them apart by type. In
  ClojureScript both would be vectors, and `[1 2 3]` as a byte string encodes to
  `83 01 02 03` while the same value as a list encodes to `c3 01 02 03` — silently
  different, same input. So here a byte string is a **JS array** (`array?` true,
  `sequential?` false, and seqable), exactly mirroring the JVM's distinction, and
  a cljs vector/seq is a list. That also makes an empty RLP list (`[]`, needed for
  an empty access list -> `0xc0`) unambiguously different from an empty byte
  string (`#js []` -> `0x80`)."
  (:require [kotoba.lang.text :as str]
            [eth-crypto.keccak :as keccak]
            [eth-crypto.secp256k1 :as secp]))

(def ^:private ZERO (js/BigInt 0))
(def ^:private B8 (js/BigInt 8))
(def ^:private BFF (js/BigInt 255))

(defn- strip0x [s] (if (str/starts-with? s "0x") (subs s 2) s))

(defn- hex->jsbytes [s]
  (let [h (strip0x s)]
    (when (odd? (count h))
      (throw (ex-info "eth-crypto: odd-length hex string" {:hex s})))
    (to-array (for [i (range 0 (count h) 2)]
                (js/parseInt (subs h i (+ i 2)) 16)))))

(defn- utf8 [s] (js/Array.from (.encode (js/TextEncoder.) s)))

(defn- big? [v] (= (type v) js/BigInt))

(defn- ->big
  "Coerce a numeric field (int, decimal string, 0x-hex string, byte seq, BigInt)
  to BigInt."
  [v]
  (cond
    (nil? v) ZERO
    (big? v) v
    (number? v) (js/BigInt v)
    (string? v) (let [s (str/trim v)]
                  (cond (empty? s) ZERO
                        :else (js/BigInt s)))          ; BigInt parses 0x… too
    :else (secp/bytes->big (seq v))))

(defn- cat-bytes [arrays] (to-array (mapcat seq arrays)))

(defn- pad-left-32 [bs]
  (let [v (vec (seq bs))]
    (to-array (concat (repeat (- 32 (count v)) 0) v))))

(defn- uint->32
  "A non-negative integer as a 32-byte big-endian word."
  [v]
  (to-array (secp/big->bytes32 (->big v))))

(defn- big->minimal
  "Minimal big-endian byte-string of a non-negative BigInt (0 -> empty)."
  [x]
  (if (= x ZERO)
    (to-array [])
    (to-array
     (loop [x x acc ()]
       (if (= x ZERO)
         acc
         (recur (bit-shift-right x B8) (cons (js/Number (bit-and x BFF)) acc)))))))

;; ─── EIP-712 value encoding ──────────────────────────────────────────────

(declare encode-data)

(defn- type-hash [types primary encode-type-fn]
  (to-array (keccak/keccak256 (utf8 (encode-type-fn types primary)))))

(defn- encode-field [types type value encode-type-fn]
  (cond
    (contains? types type)
    (to-array (keccak/keccak256 (encode-data types type value encode-type-fn)))
    (= type "string")  (to-array (keccak/keccak256 (utf8 value)))
    (= type "bytes")   (to-array (keccak/keccak256
                                  (if (string? value) (hex->jsbytes value) (seq value))))
    (= type "bytes32") (pad-left-32 (if (string? value) (hex->jsbytes value) value))
    (= type "address") (pad-left-32 (if (string? value) (hex->jsbytes value) value))
    (= type "bool")    (uint->32 (if value 1 0))
    (str/starts-with? type "uint") (uint->32 value)
    (str/starts-with? type "int")  (uint->32 value)
    :else (throw (ex-info (str "unsupported EIP-712 type: " type) {:type type}))))

(defn encode-data
  "ABI-encoded struct bytes for `primary`, as a JS byte array. `encode-type-fn` is
  `core/encode-type`, passed in to avoid a circular require (it is already
  portable, so there is no reason to duplicate it here)."
  [types primary data encode-type-fn]
  (cat-bytes
   (cons (type-hash types primary encode-type-fn)
         (map (fn [{:keys [name type]}]
                (encode-field types type (get data name) encode-type-fn))
              (get types primary)))))

(defn hash-struct [types primary data encode-type-fn]
  (to-array (keccak/keccak256 (encode-data types primary data encode-type-fn))))

(def ^:private eip712-domain-type
  {"EIP712Domain" [{:name "name" :type "string"}
                   {:name "version" :type "string"}
                   {:name "chainId" :type "uint256"}
                   {:name "verifyingContract" :type "address"}]})

(defn domain-separator [domain encode-type-fn]
  (hash-struct eip712-domain-type "EIP712Domain" domain encode-type-fn))

(defn eip712-digest [domain types primary message encode-type-fn]
  (to-array
   (keccak/keccak256
    (cat-bytes [(to-array [0x19 0x01])
                (domain-separator domain encode-type-fn)
                (hash-struct types primary message encode-type-fn)]))))

;; ─── RLP ─────────────────────────────────────────────────────────────────

(defn- rlp-prefix [offset len]
  (if (< len 56)
    (to-array [(+ offset len)])
    (let [len-bytes (big->minimal (js/BigInt len))]
      (cat-bytes [(to-array [(+ offset 55 (count len-bytes))]) len-bytes]))))

(defn rlp-encode
  "Canonical recursive RLP. A **JS array** is a byte string; a cljs
  vector/seq is a list (see the ns docstring for why the distinction has to be
  by type). Returns a JS byte array."
  [item]
  (if (sequential? item)
    (let [payload (cat-bytes (map rlp-encode item))]
      (cat-bytes [(rlp-prefix 0xc0 (count payload)) payload]))
    (let [n (count item)]
      (if (and (= n 1) (< (aget item 0) 0x80))
        (to-array (seq item))
        (cat-bytes [(rlp-prefix 0x80 n) item])))))

;; ─── transaction signing ─────────────────────────────────────────────────

(defn- ->num-bytes [v] (big->minimal (->big v)))

(defn- ->byte-str [v]
  (cond (nil? v) (to-array [])
        (string? v) (hex->jsbytes v)
        (array? v) v
        :else (to-array (seq v))))

(defn- bytes->hex [bs]
  (apply str (map #(.padStart (.toString (bit-and % 0xff) 16) 2 "0") (seq bs))))

(defn- legacy-fields [tx]
  (let [{:keys [nonce gas-price gas to value data]} tx]
    [(->num-bytes nonce) (->num-bytes gas-price) (->num-bytes gas)
     (->byte-str to) (->num-bytes value) (->byte-str data)]))

(defn legacy-digest
  "keccak256(rlp([nonce, gasPrice, gas, to, value, data, chainId, 0, 0]))."
  [tx]
  (let [empty-b (to-array [])]
    (to-array
     (keccak/keccak256
      (rlp-encode (into (legacy-fields tx)
                        [(->num-bytes (:chain-id tx)) empty-b empty-b]))))))

(defn sign-tx-legacy
  "EIP-155 legacy transaction -> 0x… raw signed transaction hex."
  [tx privkey]
  (let [{:keys [chain-id]} tx
        fields (legacy-fields tx)
        {:keys [r s recovery-id]} (secp/sign privkey (legacy-digest tx))
        v (+ (js/BigInt recovery-id) (* (js/BigInt 2) (->big chain-id)) (js/BigInt 35))]
    (str "0x" (bytes->hex
               (rlp-encode (into fields [(big->minimal v)
                                         (big->minimal r)
                                         (big->minimal s)]))))))

(def eip1559-tx-type 0x02)

(defn- access-list-items [access-list]
  (mapv (fn [entry]
          (let [address (or (:address entry) (get entry "address"))
                slots (or (:storage-keys entry) (:storageKeys entry)
                          (get entry "storageKeys") (get entry "storage-keys") [])]
            [(->byte-str address) (mapv ->byte-str slots)]))
        (or access-list [])))

(defn- eip1559-payload-items [tx]
  (let [{:keys [chain-id nonce max-priority-fee-per-gas max-fee-per-gas
                gas to value data access-list]} tx]
    [(->num-bytes chain-id)
     (->num-bytes nonce)
     (->num-bytes max-priority-fee-per-gas)
     (->num-bytes max-fee-per-gas)
     (->num-bytes gas)
     (->byte-str to)
     (->num-bytes value)
     (->byte-str data)
     (access-list-items access-list)]))

(defn eip1559-digest
  "keccak256(0x02 || rlp(payload)) — the digest an EIP-1559 tx is signed over."
  [tx]
  (to-array
   (keccak/keccak256
    (cat-bytes [(to-array [eip1559-tx-type])
                (rlp-encode (eip1559-payload-items tx))]))))

(defn eip1559-raw
  "Assemble a raw signed type-2 tx from an already-computed {:r :s :recovery-id}."
  [tx {:keys [r s recovery-id]}]
  (str "0x" (bytes->hex
             (cat-bytes
              [(to-array [eip1559-tx-type])
               (rlp-encode (conj (eip1559-payload-items tx)
                                 (->num-bytes recovery-id)
                                 (big->minimal r)
                                 (big->minimal s)))]))))

(defn sign-tx-eip1559 [tx privkey]
  (eip1559-raw tx (secp/sign privkey (eip1559-digest tx))))

(defn signature->bytes
  "{:r :s :recovery-id} -> the 65-byte r‖s‖v signature (v = recovery-id + 27),
  as a vector of ints."
  [{:keys [r s recovery-id]}]
  (-> (vec (secp/big->bytes32 r))
      (into (secp/big->bytes32 s))
      (conj (+ recovery-id 27))))

(defn raw-tx-hash
  "keccak256 of a raw signed transaction — the tx hash, pre-broadcast."
  [raw]
  (str "0x" (bytes->hex (keccak/keccak256 (->byte-str raw)))))
