(ns kotobase.ipns
  "Signed IPNS head records (ADR-2607061800) for the browser/tenant-plane
  client -- the `:cljs` counterpart to `kotoba-lang/tech-ipfs-specs-ipns`'s
  `ipns.head`, which is `:clj`-only. Uses this repo's own `@noble/curves`/
  `@ipld/dag-cbor` stack (same npm libs `kotobase.cacao` already uses),
  matching ADR-2607050100's precedent that this client's crypto seam
  stays separate from the JVM `ed25519`/`cacao` git deps -- byte
  compatibility with the JVM `ipns.head` is the contract, not a shared
  dependency (both sides encode the head record via dag-cbor's canonical
  key-sort, and a keyword/string map key serializes to the same CBOR
  text bytes either way -- verified against a real JVM-signed fixture,
  see test).

  `ClojureScript`-only (not portable `.cljc`), same documented exception
  as `kotobase.cacao`/`kotobase.cid`."
  (:require ["@noble/curves/ed25519.js" :refer [ed25519]]
            ["@ipld/dag-cbor" :as dag-cbor]
            [kotobase.cid :as cid]))

(defn- payload-bytes [record]
  (.encode dag-cbor (clj->js (dissoc record :public_key_multibase :signature_multibase))))

(defn sign-head
  "Sign an IPNS head record `{:name :value :sequence :valid_until ...}`
   with the actor's own raw 32-byte Ed25519 `secret-key` (ADR-2607032500's
   self-mint pattern -- the actor signs its own head, no delegation).
   Returns `record` with `:public_key_multibase`/`:signature_multibase`
   added, the signature covering a canonical dag-cbor payload of every
   OTHER field."
  [^js secret-key record]
  (let [pub (.getPublicKey ed25519 secret-key)
        sig (.sign ed25519 (payload-bytes record) secret-key)]
    (assoc record
           :public_key_multibase (cid/did-key-from-ed25519-pub pub)
           :signature_multibase (str "z" (cid/base58btc sig)))))

(defn verify-head
  "Verify a signed IPNS head record (as `sign-head` -- or the JVM
   `ipns.head/sign` -- produces). Returns `{:valid? bool :name ...}` --
   recomputes the canonical dag-cbor payload over every field except the
   two signature fields, and checks `:signature_multibase` against the
   `did:key` in `:public_key_multibase`, AND that that key is the one
   `:name` names. Sequence-rollback (CAS) is NOT this function's job --
   that is the storage layer's optimistic-concurrency write.

   **The signature alone is not authority over the name.** Until 2026-08-04
   this function (and its JVM twin `ipns.head/verify`) checked only the
   signature, so any keypair could sign a record carrying somebody else's
   `k51...` and have it verify -- and `kotobase.server.ipns/verify-and-
   decide-publish`, whose only other gate is sequence monotonicity, accepted
   it. A `k51...` name IS `pubkey->name` of its Ed25519 key, so the
   authoritative key is resolved FROM THE NAME and
   `:public_key_multibase` is only ever a restatement of it. See
   `kotobase.cid/ipns-name-matches-pub?` and superproject ADR-2608047000."
  [record]
  (let [{:keys [public_key_multibase signature_multibase]} record
        pub (and public_key_multibase (cid/did-key->ed25519-pub public_key_multibase))
        sig (and signature_multibase (cid/base58btc-decode (subs signature_multibase 1)))]
    {:valid? (boolean (and pub sig
                           ;; the name IS the key -- checked before the
                           ;; signature, so a forged name can never reach a
                           ;; verify that would say yes
                           (cid/ipns-name-matches-pub? (:name record) pub)
                           (.verify ed25519 sig (payload-bytes record) pub)))
     :name (:name record)}))
