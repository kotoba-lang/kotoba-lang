(ns ipni.metadata
  "IPNI advertisement metadata — a multicodec protocol identifier, then
  protocol-specific bytes.

  kotobase retrieval is `transport-ipfs-gateway-http` (`0x0920`). That is
  what kotoba `codebase-routing` already filters for. Bitswap (`0x0900`)
  is here because IPNI talks it; kotobase does not.

  Encoding is unsigned LEB128. This namespace does not import
  io-multiformats: the identifier is one varint, not a CID.")

(def ^:const bitswap 0x0900)
(def ^:const graphsync-filecoin 0x0909)
(def ^:const graphsync 0x0910)
(def ^:const http 0x0911)
(def ^:const gateway-http 0x0920)

(def protocol-name
  {bitswap "transport-bitswap"
   graphsync-filecoin "transport-graphsync-filecoinv1"
   graphsync "transport-graphsync"
   http "transport-http"
   gateway-http "transport-ipfs-gateway-http"})

(defn uvarint-encode
  "Unsigned LEB128. Refuses negatives rather than wrapping."
  [n]
  (cond
    (not (integer? n)) {:error :not-integer :value n}
    (neg? n) {:error :negative :value n}
    :else
    (loop [n n out []]
      (if (< n 128)
        (conj out n)
        (recur (quot n 128)
               (conj out (bit-or (bit-and n 0x7F) 0x80)))))))

(defn uvarint-decode
  "First unsigned LEB128 in `octets`. Returns `{:value :rest}` or an error.
  An empty input is not 0 — silence is not a protocol identifier."
  [octets]
  (let [xs (vec octets)]
    (if (empty? xs)
      {:error :empty}
      (loop [i 0 acc 0 shift 0]
        (if (>= i (count xs))
          {:error :truncated}
          (let [b (bit-and (nth xs i) 0xFF)
                acc (bit-or acc (bit-shift-left (bit-and b 0x7F) shift))]
            (if (zero? (bit-and b 0x80))
              {:value acc :rest (subvec xs (inc i))}
              (if (> shift 63)
                {:error :overflow}
                (recur (inc i) acc (+ shift 7))))))))))

(defn encode
  "Protocol identifier as metadata bytes. Extra protocol bytes may follow."
  ([protocol] (encode protocol nil))
  ([protocol extra]
   (let [head (uvarint-encode protocol)]
     (if (:error head)
       head
       (into head (mapv #(bit-and % 0xFF) (or extra [])))))))

(defn decode
  [octets]
  (let [r (uvarint-decode octets)]
    (if (:error r)
      r
      {:protocol (:value r)
       :name (get protocol-name (:value r))
       :extra (:rest r)})))

(defn gateway-http-bytes
  "Metadata kotobase advertisements carry. Retrieval is HTTPS gateway,
  not Bitswap."
  []
  (encode gateway-http))

(defn gateway-http?
  [octets]
  (= gateway-http (:protocol (decode octets))))
