(ns ipni.sign
  "The bytes an IPNI advertisement signature is actually over.

  The spec says only that the advertisement is \"serialized, with all
  instances of `Signature` replaced with an empty array of bytes\", then
  \"hashed, and the hash is then signed\". It names neither the hash, nor
  the serialization, nor whether the result is raw. Every one of those
  gaps comes back as a bare HTTP 400 from an indexer, which is the same
  shape this library already measured for the announce message.

  So this namespace states what go-libipni does, and the test pins it to
  a real advertisement fetched from a live third-party publisher: the
  payload this code derives equals the one inside their envelope, and
  their signature verifies over the record this code frames. That is an
  external oracle -- checking our construction against our own is how a
  wrong construction stays green.

  Three layers, kept apart because only the middle one is IPNI's:

    signature-payload   the concatenation go-libipni hashes
    signed-record       libp2p's domain-separated framing of any payload
    envelope            libp2p's Envelope protobuf, which holds the result

  No crypto here, same rule as the rest of the library: `hash-fn`,
  `sign-fn` and `verify-fn` are injected. A missing one is an error, not
  an unsigned pass."
  (:require [clojure.string :as str]
            [ipni.octets :as oct]))

(def ^:const domain
  "The libp2p signing domain for every indexer record. Domain separation
  is what stops a signature made for one purpose being replayed as
  another, so it is part of the signed bytes, not of the envelope."
  "indexer")

(def ^:const ad-codec
  "Payload type for the advertisement's own Signature field."
  "/indexer/ingest/adSignature")

(def ^:const extended-provider-codec
  "Payload type for a provider inside ExtendedProvider. A different
  string, so an advertisement signature cannot be replayed as a
  provider signature."
  "/indexer/ingest/extendedProviderSignature")

(def ^:private octets oct/->octets)

(defn uvarint
  "Unsigned LEB128. Present here rather than borrowed from `ipni.metadata`
  because that one encodes multicodec identifiers and this one frames
  lengths -- the same arithmetic serving two contracts that are free to
  diverge."
  [n]
  (loop [n (long n) out []]
    (if (>= n 0x80)
      (recur (unsigned-bit-shift-right n 7) (conj out (bit-or (bit-and n 0x7F) 0x80)))
      (conj out (bit-and n 0xFF)))))

(defn signature-payload
  "The buffer go-libipni hashes, in its order:

    PreviousID bytes | Entries bytes | Provider | each Address | Metadata | IsRm

  `PreviousID` absent contributes NOTHING, not a zero byte: go passes
  `cid.Undef`, whose `Bytes()` is empty. Addresses are concatenated with
  no separator, so two orderings of the same set are two different
  signatures -- the order in the advertisement is part of what is signed.

  CIDs arrive as strings and leave as bytes through `cid-bytes-fn`,
  injected for the same reason `addr-encode-fn` is: decoding a CID needs
  a multibase table this library does not depend on."
  [{:keys [previous-id entries provider addresses metadata is-rm]}
   {:keys [cid-bytes-fn]}]
  (cond
    (not (ifn? cid-bytes-fn)) {:error :cid-bytes-fn-required}
    (not (string? entries)) {:error :entries-cid-required :value entries}
    (not (string? provider)) {:error :invalid-provider :value provider}
    (not (sequential? addresses)) {:error :invalid-addresses :value addresses}
    :else
    (let [prev (if (str/blank? (str previous-id)) [] (octets (cid-bytes-fn previous-id)))
          ent (octets (cid-bytes-fn entries))]
      (if (or (= :invalid prev) (= :invalid ent))
        {:error :invalid-cid-bytes}
        (vec (concat prev
                     ent
                     (octets provider)
                     (mapcat octets addresses)
                     (octets metadata)
                     [(if is-rm 1 0)]))))))

(defn signed-record
  "libp2p's `makeUnsigned`: each of domain, payload-type and payload
  length-prefixed with a uvarint and concatenated. This, not the payload,
  is what the private key signs."
  [payload-type payload]
  (let [fields [(octets domain) (octets payload-type) (octets payload)]]
    (vec (mapcat (fn [f] (concat (uvarint (count f)) f)) fields))))

;; ── libp2p Envelope protobuf ────────────────────────────────────────────────
;; public_key = 1, payload_type = 2, payload = 3, signature = **5**. Field 4
;; is skipped in the upstream .proto; writing the signature as 4 produces an
;; envelope that marshals cleanly and is rejected everywhere.

(defn- field [n bytes]
  (vec (concat [(bit-or (bit-shift-left n 3) 2)] (uvarint (count bytes)) bytes)))

(defn ed25519-public-key-proto
  "libp2p PublicKey protobuf for a raw Ed25519 key: type Ed25519(1), then
  the 32 raw bytes. The same bytes a peer ID is the identity multihash of."
  [pubkey]
  (let [p (octets pubkey)]
    (if (or (= :invalid p) (not= 32 (count p)))
      {:error :invalid-ed25519-public-key :length (when (vector? p) (count p))}
      (vec (concat [0x08 0x01 0x12 0x20] p)))))

(defn envelope
  "Marshal a libp2p Envelope. `sign-fn` sees `signed-record`, never the
  bare payload."
  [{:keys [payload-type payload pubkey sign-fn]}]
  (let [pk (ed25519-public-key-proto pubkey)]
    (cond
      (:error pk) pk
      (not (ifn? sign-fn)) {:error :sign-fn-required}
      (not (string? payload-type)) {:error :invalid-payload-type :value payload-type}
      :else
      (let [pl (octets payload)
            sig (octets (sign-fn (signed-record payload-type pl)))]
        (if (or (= :invalid pl) (= :invalid sig) (empty? sig))
          {:error :invalid-signature}
          (vec (concat (field 1 pk)
                       (field 2 (octets payload-type))
                       (field 3 pl)
                       (field 5 sig))))))))

(defn- read-uvarint
  "Returns `[value next-index]`, or nil when the buffer ends mid-varint."
  [b i]
  (loop [j i value 0 shift 0]
    (when (< j (count b))
      (let [byte (nth b j)
            value (bit-or value (bit-shift-left (bit-and byte 0x7F) shift))]
        (if (>= byte 0x80)
          (recur (inc j) value (+ shift 7))
          [value (inc j)])))))

(defn parse-envelope
  "Read an Envelope back. Returns `{:pubkey :payload-type :payload
  :signature}` or `{:error …}`. Length-delimited fields only: any other
  wire type in an envelope is a malformed envelope, not a field to skip."
  [bytes]
  (let [b (octets bytes)]
    (if (= :invalid b)
      {:error :invalid-envelope}
      (loop [i 0 acc {}]
        (cond
          (> i (count b)) {:error :envelope-truncated}

          (= i (count b))
          (let [pk (get acc 1)]
            (cond
              (nil? pk) {:error :envelope-missing-public-key}
              (or (< (count pk) 4) (not= [0x08 0x01 0x12 0x20] (vec (take 4 pk))))
              {:error :envelope-public-key-not-ed25519}
              :else {:pubkey (vec (drop 4 pk))
                     :payload-type (apply str (map char (get acc 2)))
                     :payload (vec (get acc 3))
                     :signature (vec (get acc 5))}))

          :else
          (if-let [[key after-key] (read-uvarint b i)]
            (let [fnum (bit-shift-right key 3)
                  wire (bit-and key 7)]
              (if (not= 2 wire)
                {:error :envelope-unexpected-wire-type :field fnum :wire wire}
                (if-let [[len after-len] (read-uvarint b after-key)]
                  (let [end (+ after-len len)]
                    (if (> end (count b))
                      {:error :envelope-truncated}
                      (recur end (assoc acc fnum (vec (subvec b after-len end))))))
                  {:error :envelope-truncated})))
            {:error :envelope-truncated}))))))

(defn verify
  "Does this envelope carry a valid signature over `payload`, made by the
  key it names?

  Fail-closed in both directions: a payload that is not the one the
  signature covers is `:payload-mismatch`, and a `verify-fn` that is
  missing is `:verify-fn-required` -- never `true`. An advertisement whose
  signature could not be checked has not been checked."
  [envelope-bytes payload {:keys [verify-fn]}]
  (let [parsed (parse-envelope envelope-bytes)]
    (cond
      (:error parsed) parsed
      (not (ifn? verify-fn)) {:error :verify-fn-required}
      (not= (vec (octets payload)) (:payload parsed)) {:error :payload-mismatch}
      :else
      {:valid? (boolean (verify-fn (:pubkey parsed)
                                   (signed-record (:payload-type parsed) (:payload parsed))
                                   (:signature parsed)))
       :payload-type (:payload-type parsed)
       :pubkey (:pubkey parsed)})))
