(ns kotobase.cid
  "Deterministic graph-CID + did:key derivation for kotobase.net.

  Byte-identical to the kotobase.net edge (net-kotobase worker/src/app.cljc:
  base32LowerNoPad / graphCidFromName) and the @gftd/kotobase-datomic SDK
  (net-kotobase/sdk/kotobase-datomic/src/index.cljc). A graph handle is the
  CIDv1/dag-cbor/sha2-256 of the name `kotobase/db/<did>/<db-name>`, so the
  edge can recompute exactly this from the caller DID + db_name and pin it
  into every write (a client-supplied graph can never override it).

  ClojureScript-only (not .cljc): SHA-256 is kotoba-lang/org-nist-sha2
  (`sha2.core`), which has no JVM analogue here. All ops are synchronous,
  unlike the SDK's async crypto.subtle path."
  (:require [kotoba.lang.text :as str]
            [sha2.core :as sha2]))

(def ^:private b58-alphabet
  "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(def ^:private b32-alphabet
  "abcdefghijklmnopqrstuvwxyz234567")

(def ^:private b36-alphabet
  "0123456789abcdefghijklmnopqrstuvwxyz")

(defn ^js text->bytes [^string s]
  (.encode (js/TextEncoder.) s))

(defn- bytes-from-u8 [^js u8]
  (vec (js/Array.from u8)))

(defn- u8-from-bytes [bytes]
  (let [n (count bytes)
        out (js/Uint8Array. n)]
    (dotimes [i n] (aset out i (nth bytes i)))
    out))

(defn base58btc
  "base58btc(multibase 'z' payload). Port of the edge's base58btcEncode."
  [^js bytes]
  (let [len (alength bytes)
        zeros (loop [i 0]
                (if (and (< i len) (zero? (aget bytes i))) (recur (inc i)) i))
        digits (array)]
    (loop [i zeros]
      (when (< i len)
        (let [carry (volatile! (aget bytes i))]
          (dotimes [j (alength digits)]
            (let [c (+ @carry (bit-shift-left (aget digits j) 8))]
              (aset digits j (mod c 58))
              (vreset! carry (quot c 58))))
          (while (pos? @carry)
            (.push digits (mod @carry 58))
            (vreset! carry (quot @carry 58))))
        (recur (inc i))))
    (str (apply str (repeat zeros "1"))
         (apply str (map #(nth b58-alphabet %)
                         (reverse (array-seq digits)))))))

(defn base32-lower-no-pad
  "CIDv1 base32-lower, no padding (multibase 'b' payload). Port of the edge's
  base32LowerNoPad — 32-bit accumulator, drains 5-bit groups MSB-first."
  [^js bytes]
  (let [st (reduce
            (fn [{:keys [bits value out]} b]
              (let [value (bit-or (bit-shift-left value 8) b)
                    bits (+ bits 8)]
                (loop [bits bits out out]
                  (if (>= bits 5)
                    (recur (- bits 5)
                           (str out (nth b32-alphabet
                                         (bit-and (unsigned-bit-shift-right value (- bits 5)) 31))))
                    {:bits bits :value value :out out}))))
            {:bits 0 :value 0 :out ""}
            (array-seq bytes))
        {:keys [bits value out]} st]
    (if (pos? bits)
      (str out (nth b32-alphabet (bit-and (bit-shift-left value (- 5 bits)) 31)))
      out)))

(defn base58btc-decode
  "Inverse of base58btc: base58 string → Uint8Array. Throws on bad chars."
  [^string s]
  (let [idx (into {} (map-indexed (fn [i c] [c i]) b58-alphabet))
        zeros (count (take-while #(= % \1) s))
        b256 (array)]
    (doseq [c (drop zeros s)]
      (let [carry (volatile! (or (get idx c)
                                 (throw (js/Error. (str "base58: bad char " c)))))]
        (dotimes [j (alength b256)]
          (let [x (+ (* (aget b256 j) 58) @carry)]
            (aset b256 j (bit-and x 0xff))
            (vreset! carry (bit-shift-right x 8))))
        (while (pos? @carry)
          (.push b256 (bit-and @carry 0xff))
          (vreset! carry (bit-shift-right @carry 8)))))
    (let [n (alength b256)
          out (js/Uint8Array. (+ zeros n))]
      (dotimes [j n] (aset out (+ zeros j) (aget b256 (- n 1 j))))  ; b256 is little-endian
      out)))

(defn base36-decode
  "Inverse of the multibase base36 `ipns.core/base36` uses: lowercase base36
  string → Uint8Array. Throws on bad chars.

  A deliberate second implementation of `kotoba-lang/tech-ipfs-specs-ipns`'s
  portable `.cljc` codec, not a shared dependency — this repo is consumed by
  its workers as a bare shadow-cljs `:source-path` (see the aozora workers'
  `shadow-cljs.edn`), so a `deps.edn` git dep here would not resolve in their
  builds and a new `(:require [ipns.core])` would break them. Same reason and
  same shape as this namespace's existing did:key/base58 duplication of the
  JVM stack (ADR-2607050100): byte compatibility is the contract, held by
  golden vectors in `cid_test`, not by sharing code.

  Two copies of a codec is a drift risk and the vectors are the mitigation:
  they are the literals `ipns.core-test` asserts, including the one taken from
  a real Kubo node."
  [^string s]
  (let [idx (into {} (map-indexed (fn [i c] [c i]) b36-alphabet))
        zeros (count (take-while #(= % \0) s))
        b256 (array)]
    (doseq [c (drop zeros s)]
      (let [carry (volatile! (or (get idx c)
                                 (throw (js/Error. (str "base36: bad char " c)))))]
        (dotimes [j (alength b256)]
          (let [x (+ (* (aget b256 j) 36) @carry)]
            (aset b256 j (bit-and x 0xff))
            (vreset! carry (bit-shift-right x 8))))
        (while (pos? @carry)
          (.push b256 (bit-and @carry 0xff))
          (vreset! carry (bit-shift-right @carry 8)))))
    (let [n (alength b256)
          out (js/Uint8Array. (+ zeros n))]
      (dotimes [j n] (aset out (+ zeros j) (aget b256 (- n 1 j))))  ; b256 is little-endian
      out)))

(defn ipns-name->ed25519-pub
  "`k51…` libp2p-key IPNS name → the 32-byte Ed25519 public key it names, or
  nil. The cljs counterpart of `ipns.core/name->pubkey*`.

  Structurally exact, not merely prefix-shaped: CIDv1 (0x01) + libp2p-key
  codec (0x72) + identity multihash (0x00) + a libp2p PublicKey protobuf of
  EXACTLY 36 bytes with header `08 01 12 20`. A length check that only
  enforced a floor would fold trailing garbage into the returned key, which
  is a forged name that verifies.

  Fail-closed: nil for anything malformed, never a throw."
  [^string name]
  (try
    (when (and (string? name) (str/starts-with? name "k"))
      (let [cid (base36-decode (subs name 1))]
        (when (and (>= (alength cid) 4)
                   (= 0x01 (aget cid 0)) (= 0x72 (aget cid 1))
                   (= 0x00 (aget cid 2)))
          (let [len (aget cid 3)
                pb (.subarray cid 4 (+ 4 len))]
            (when (and (= 36 len) (= 36 (alength pb))
                       (= 0x08 (aget pb 0)) (= 0x01 (aget pb 1))
                       (= 0x12 (aget pb 2)) (= 0x20 (aget pb 3)))
              (.slice pb 4 36))))))
    (catch js/Error _ nil)))

(defn ipns-name-matches-pub?
  "Does `name` name exactly `pub`? The cljs counterpart of
  `ipns.core/name-matches-pubkey?`, and the check that makes \"the name is the
  key\" true rather than decorative — see that function's docstring and
  `kotobase.ipns/verify-head`. Fail-closed."
  [^string name ^js pub]
  (boolean
   (when-let [expected (ipns-name->ed25519-pub name)]
     (and pub
          (= (alength expected) (alength pub))
          (every? true? (map = (array-seq expected) (array-seq pub)))))))

(defn did-key->ed25519-pub
  "did:key:z<base58btc(0xed 0x01 || pub32)> → the 32-byte Ed25519 public key,
  or nil when the DID isn't an Ed25519 did:key -- including when the
  base58 payload itself is malformed (base58btc-decode throws on a
  character outside its alphabet), not just when the decoded multicodec
  header is wrong."
  [^string did]
  (when (str/starts-with? did "did:key:z")
    (let [mc (try (base58btc-decode (subs did (count "did:key:z")))
                  (catch js/Error _ nil))]
      (when (and mc (>= (alength mc) 34) (= 0xed (aget mc 0)) (= 0x01 (aget mc 1)))
        (.slice mc 2 34)))))

(defn did-key-from-ed25519-pub
  "did:key:z<base58btc(0xed 0x01 || pub32)> for an Ed25519 public key."
  [^js pub]
  (when (not= 32 (alength pub))
    (throw (js/Error. "Ed25519 public key must be 32 bytes")))
  (let [mc (js/Uint8Array. 34)]
    (aset mc 0 0xed)
    (aset mc 1 0x01)
    (.set mc pub 2)
    (str "did:key:z" (base58btc mc))))

(defn graph-cid-from-name
  "KotobaCid::from_bytes(name).to_multibase(): SHA-256(name) behind a
  CIDv1/dag-cbor/sha2-256 header (0x01 0x71 0x12 0x20), base32-lower 'b'."
  [^string name]
  (let [hash (u8-from-bytes (sha2/sha256 (bytes-from-u8 (text->bytes name))))
        cid (js/Uint8Array. 36)]
    (aset cid 0 0x01)
    (aset cid 1 0x71)
    (aset cid 2 0x12)
    (aset cid 3 0x20)
    (.set cid hash 4)
    (str "b" (base32-lower-no-pad cid))))

(defn canonical-graph
  "The deterministic graph CID for one of your databases. The edge recomputes
  exactly this from your DID + db-name and pins it into every write."
  [^string did ^string db-name]
  (graph-cid-from-name (str "kotobase/db/" did "/" db-name)))
