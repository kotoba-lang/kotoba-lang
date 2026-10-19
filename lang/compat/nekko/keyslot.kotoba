(ns nekko.keyslot
  "Wrap one long-lived secret under N independent *unlock factors*, so any one
  factor opens it and losing one loses nothing (ADR-0038 keyslots).

  The secret is a mailbox's X25519 private key from
  `nekko.recipient-grant-async`; the factors are things only the user has — a
  WebAuthn PRF output (one per credential, so one slot per enrolled passkey)
  and a recovery code. The server stores slots and can open none of them.

  A slot is self-describing, all hex except :factor:

    {:v 1 :factor \"prf:<credential-id>\" :kdf \"HKDF-SHA-256\"
     :salt <32B> :iv <12B> :ct <ciphertext||tag>}

  ## No JVM implementation, deliberately

  There is no `.cljc` sibling of this namespace and there should not be one.
  A server-side unwrap path is precisely the capability zero-access removes;
  adding one \"for tests\" would hand the server the mailbox key back. The
  tests here run under nbb, which is a client host like any other.

  ## Why the IV is random here, when recipient-grant's is zero

  `nekko.recipient-grant` uses a 12-byte ZERO iv, and that is correct THERE:
  its AES key comes from a fresh ephemeral ECDH output used exactly once, so
  (key, iv) can never repeat. A keyslot is the opposite case — the factor
  secret is long-lived (a PRF output for a given credential and salt never
  changes), so the slot key recurs every time the slot is rewritten. With a
  zero iv, two wraps under the same factor would reuse (key, iv), which breaks
  AES-GCM outright: the keystreams are identical, so XORing the two
  ciphertexts reveals the XOR of the plaintexts, and the authentication key is
  recoverable. **Do not copy the zero iv from recipient-grant into anything
  whose key is reused.** Both the salt and the iv are freshly random per wrap.

  ## Factor binding

  The factor id is passed to AES-GCM as additionalData, so a slot cannot be
  relabelled or transplanted to another factor by whoever stores it: changing
  the :factor string makes the tag check fail. The id is not secret."
  (:require [nekko.recipient-grant-async :as rga]))

(def ^:private kdf-name "HKDF-SHA-256")
(def ^:private hkdf-info-prefix "nekko/keyslot/v1/")

(defn- subtle [] (aget (aget js/globalThis "crypto") "subtle"))

(defn- utf8 [s] (.encode (js/TextEncoder.) s))

(defn- slot-key
  "HKDF-SHA-256(factor-secret, salt, info=\"nekko/keyslot/v1/<factor>\")
  -> Promise<CryptoKey> for AES-256-GCM.

  Real HKDF, not the one-shot SHA-256 `recipient-grant` uses: there the input
  was already a uniformly random ECDH output, whereas a factor secret may be
  low-entropy (a recovery code a human can type), which is exactly the case
  HKDF's extract step exists for."
  [factor-secret salt factor-id]
  (-> (.importKey (subtle) "raw" (js/Uint8Array. factor-secret) #js {"name" "HKDF"}
                  false #js ["deriveBits"])
      (.then (fn [prk]
               (.deriveBits (subtle)
                            #js {"name" "HKDF" "hash" "SHA-256" "salt" salt
                                 "info" (utf8 (str hkdf-info-prefix factor-id))}
                            prk 256)))
      (.then (fn [okm]
               (.importKey (subtle) "raw" okm #js {"name" "AES-GCM"} false
                           #js ["encrypt" "decrypt"])))))

(defn wrap
  "Wrap `secret` (Uint8Array — a mailbox X25519 private key) under
  `factor-secret` (Uint8Array — a PRF output, a derived recovery secret).
  `factor-id` is a non-secret label, bound into the slot as AAD.
  -> Promise<slot map>."
  [secret factor-id factor-secret]
  (let [salt (rga/random-bytes 32)
        iv (rga/random-bytes 12)]
    (-> (slot-key factor-secret salt factor-id)
        (.then (fn [aes]
                 (.encrypt (subtle)
                           #js {"name" "AES-GCM" "iv" iv "tagLength" 128
                                "additionalData" (utf8 factor-id)}
                           aes (js/Uint8Array. secret))))
        (.then (fn [ct]
                 {:v 1 :factor factor-id :kdf kdf-name
                  :salt (rga/hexify salt) :iv (rga/hexify iv)
                  :ct (rga/hexify ct)})))))

(defn unwrap
  "Open `slot` with `factor-secret`. -> Promise<Uint8Array secret>. REJECTS on
  the wrong factor secret, a tampered ciphertext, or a relabelled :factor —
  all three surface as the same GCM tag failure, which is the point: a caller
  cannot learn *which* of those went wrong."
  [slot factor-secret]
  (let [{:keys [factor salt iv ct]} slot]
    (-> (slot-key factor-secret (rga/unhex salt) factor)
        (.then (fn [aes]
                 (.decrypt (subtle)
                           #js {"name" "AES-GCM" "iv" (rga/unhex iv) "tagLength" 128
                                "additionalData" (utf8 factor)}
                           aes (rga/unhex ct))))
        (.then (fn [pt] (js/Uint8Array. pt))))))

(defn wrap-many
  "Wrap the same `secret` once per factor. `factors` is a seq of
  `[factor-id factor-secret]`. -> Promise<{factor-id slot}>.

  This is how a mailbox gets its initial slots (one enrolled passkey + one
  recovery code) and how a second device is added later: an already-unlocked
  client wraps the SAME secret for the new credential's factor. The server
  never participates -- it only stores what it is handed."
  [secret factors]
  (let [fs (vec factors)]
    (-> (js/Promise.all (clj->js (mapv (fn [[id sec]] (wrap secret id sec)) fs)))
        (.then (fn [slots]
                 (zipmap (mapv first fs) (js->clj slots :keywordize-keys true)))))))
