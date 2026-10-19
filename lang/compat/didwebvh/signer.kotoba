(ns didwebvh.signer
  "A signer for deployments that hold key material in this process.

   `didwebvh.entry/sign` asks only for `{:multikey :sign-fn}`, so an HSM, a
   KMS, a remote signing service, or a FROST threshold signer all substitute
   here without the rest of the library changing. This namespace is the
   simplest possible implementation of that shape -- a seed in memory -- and
   it is the WRONG one for an organization root key.

   Keep it for development, for tests, and for witnesses whose custody is
   already a separate machine. An organization's update key belongs somewhere
   this function cannot reach: that is the point of the seam."
  (:require [data-integrity.eddsa :as eddsa]
            [ed25519.sign :as ed]
            [multiformats.core :as mf]))

(defn multikey
  "Raw 32-byte Ed25519 public key -> the `z6Mk…` multikey string `updateKeys`
   and `did:key` both carry. Multicodec 0xed01, multibase base58btc, exactly
   as Controlled Identifiers v1.0 fixes it."
  [public-key]
  (str "z" (mf/base58btc (concat [0xed 0x01] (map #(bit-and (int %) 0xff) (seq public-key))))))

(defn did-key
  "The `did:key` DID for a raw Ed25519 public key -- what a witness is named
   by in the `witness` parameter."
  [public-key]
  (str "did:key:" (multikey public-key)))

(defn from-seed
  "`{:multikey :sign-fn :public-key :did-key}` for a raw 32-byte seed."
  [seed]
  (let [pub (ed/public-key seed)]
    {:multikey (multikey pub)
     :did-key (did-key pub)
     :public-key pub
     :sign-fn (fn [hash-bytes] (eddsa/sign-hash-data seed hash-bytes))}))
