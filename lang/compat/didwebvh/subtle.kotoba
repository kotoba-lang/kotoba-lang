(ns didwebvh.subtle
  "The WebCrypto binding for `didwebvh.log/verify-async`.

   It is a separate namespace on purpose: the rest of this library reaches
   for no platform at all, and requiring the verifier is the caller saying
   which platform it is on. In a Cloudflare Worker or in Node, Ed25519
   verification through `crypto.subtle` costs a few milliseconds where the
   pure RFC 8032 path costs a few hundred, and neither one holds a key --
   this verifies with a public key, which is public.

   Nothing here maps an error to `false`. A key that cannot be imported and a
   runtime without Ed25519 both mean the check did not run, and a check that
   did not run must not answer the way a check that ran and refused answers.
   The one boolean this returns is `crypto.subtle.verify`'s own."
  (:require [data-integrity.bytes :as b]))

(defn- u8 [x]
  (js/Uint8Array.from (into-array (b/->ints x))))

(defn verify-signature
  "`(fn [public-key hash-data signature] -> Promise<boolean>)`, the shape
   `didwebvh.log/verify-async` wants. `public-key` is 32 raw Ed25519 bytes --
   `didwebvh.proof` has already decoded and length-checked the multikey by the
   time this is called, so an import failure here is a broken runtime, not a
   broken proof, and it propagates."
  [public-key hash-data signature]
  (-> (js/crypto.subtle.importKey "raw" (u8 public-key) #js {:name "Ed25519"} false #js ["verify"])
      (.then (fn [key]
               (js/crypto.subtle.verify #js {:name "Ed25519"} key (u8 signature) (u8 hash-data))))
      (.then boolean)))
