(ns kotoba.net.libp2p.validate
  "Concrete record validators, one namespace at a time.

  `store` takes a validator and ships none but `/pk/`, because the DHT has no
  universal notion of a valid record and a store that guessed would be guessing
  about correctness. This is where a node says which namespaces it can actually
  check — and it is a separate namespace so that a node validating nothing
  never loads the IPNS machinery at all.

  The `/ipns/` rule is worth stating precisely, because two of its three parts
  are the ones that are easy to skip:

  1. the record's signature must verify — under the key the KEY names, not one
     the record supplies. A record carrying its own key would verify perfectly
     and prove nothing;
  2. the record must not be expired. An expired record that still validates is
     how a resolver gets confidently stale answers;
  3. a newer record must beat an older one. Without the sequence comparison a
     node re-serves whichever it happened to store first, and an attacker who
     cannot forge a signature can still pin the network to an old value by
     racing to publish it."
  (:require [ed25519.core :as ed]
            [ipns.core :as ipns-core]
            [ipns.record :as ipns]
            [kotoba.net.libp2p.store :as store]))

(def ipns-prefix (mapv int "/ipns/"))

(defn- name-of
  "The `k51…` name a `/ipns/<multihash>` DHT key denotes.

  The key carries the binary multihash; the validator needs the text name that
  `ipns.record/validate` checks against, and the raw public key to verify with.
  Both come out of the same octets, which is what makes the check self-contained
  -- nothing about the identity is taken from the record."
  [key]
  (let [multihash (vec (drop (count ipns-prefix) (vec key)))]
    ;; identity multihash (0x00) wrapping the libp2p PublicKey protobuf, whose
    ;; last 32 octets are the raw Ed25519 key.
    (when (and (= 0x00 (first multihash)) (> (count multihash) 34))
      (let [protobuf (vec (drop 2 multihash))
            raw (vec (take-last 32 protobuf))]
        {:name (ipns-core/pubkey->name (byte-array (map unchecked-byte raw)))
         :pubkey (byte-array (map unchecked-byte raw))}))))

(defn ipns-validator
  "Validate `/ipns/…` records. `now-ms` is injected so a caller owns the clock."
  ([] (ipns-validator #(System/currentTimeMillis)))
  ([now-fn]
   (fn [key value]
     (boolean
      (when (= ipns-prefix (vec (take (count ipns-prefix) (vec key))))
        (when-let [{:keys [name pubkey]} (name-of key)]
          (let [parsed (try (ipns/parse (vec value)) (catch Exception _ nil))]
            (when parsed
              (:valid?
               (ipns/validate parsed name
                              {:verify-fn (fn [_ message signature]
                                            (ed/verify pubkey
                                                       (byte-array (map unchecked-byte message))
                                                       (byte-array (map unchecked-byte signature))))
                                :now-ms (now-fn)}))))))))))

(defn newer?
  "Whether a candidate IPNS record supersedes one already held.

  `ipns.record/better?` is highest sequence, then later expiry. A store that
  overwrote unconditionally would let a replayed older record win by arriving
  second -- an attacker who cannot forge a signature pinning the network to an
  old value simply by racing."
  [held candidate]
  (let [parse #(try (ipns/parse (vec %)) (catch Exception _ nil))]
    (if-let [old (parse held)]
      (boolean (when-let [new (parse candidate)] (ipns/better? new old)))
      true)))

(defn validators
  "The namespace map a node hands to `store/by-namespace`.

  `sha256-fn` is what `/pk/` hashes with; passing it keeps this namespace free
  of a crypto dependency it does not otherwise need."
  [sha256-fn]
  {"ipns" (ipns-validator)
   "pk" (store/public-key-validator sha256-fn)})
