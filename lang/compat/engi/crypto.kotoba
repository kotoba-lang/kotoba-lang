(ns engi.crypto
  "Ed25519 signing/verification + CID computation for ENGI (ADR-2607101100).

  cljs-only (not `.cljc`) because the org's canonical crypto lib for this
  stack is npm `@noble/curves` (the SAME library `kotobase-client`'s
  `kotobase.cacao`/`kotobase.cid` already use for CACAO signing and did:key
  derivation — see that repo's README/ns docstrings; there is no JVM
  `kotoba-lang/ed25519` dependency anywhere in the kotobase.net write path
  today, so introducing one here would be a second, divergent crypto stack
  for no benefit). Reuses `kotobase.cid` (did:key <-> pubkey, graph/content
  CID) and `kotobase.cacao` (base64url encode/decode) directly rather than
  re-implementing either.

  ── deviation from the ADR's literal wording, noted here and in the README ──
  The ADR schema says `:engi/self-sig` / `:engi/counter-sig` are signatures
  \"over the TransferBody\". This ns signs the TRANSFER-ID (the CID of the
  canonical TransferBody) instead of the raw body bytes — cryptographically
  equivalent (sign-the-hash is standard practice; the CID already commits to
  every body field), and it makes post-hoc audit (`engi.protocol/audit-agent`)
  possible using ONLY the fields already stored in a ledger entry (the entry
  carries `:engi/transfer-id` but not the full original TransferBody —
  spender/receiver/spender-prev/nonce aren't ledger-entry fields). Signing
  the full body would require every auditor to also have the original
  TransferBody on hand, which the ADR's own schema doesn't persist."
  (:require ["@noble/curves/ed25519.js" :refer [ed25519]]
            ["@noble/hashes/sha2.js" :refer [sha256]]
            [kotobase.cid :as cid]
            [kotobase.cacao :as cacao]
            [engi.core :as core]))

;; ── identity ─────────────────────────────────────────────────────────────

(defn generate-identity
  "Mint a fresh Ed25519 keypair + its did:key. → {:secret-key :did}."
  []
  (let [seed (js/crypto.getRandomValues (js/Uint8Array. 32))]
    {:secret-key seed
     :did (cid/did-key-from-ed25519-pub (.getPublicKey ed25519 seed))}))

(defn identity-from-seed
  "Rehydrate {:secret-key :did} from an existing 32-byte seed."
  [^js seed]
  {:secret-key seed
   :did (cid/did-key-from-ed25519-pub (.getPublicKey ed25519 seed))})

;; ── CID / hashing (reuses kotobase.cid's CIDv1/dag-cbor/sha2-256 multibase) ──

(defn transfer-id
  "CID of a TransferBody's canonical serialization — the `transfer-id` /
  correlation key stored identically on both parties' ledger entries."
  [transfer-body]
  (cid/graph-cid-from-name (core/canonical-transfer-body transfer-body)))

(defn entry-hash
  "CID of an already-built (and signed) ledger entry's canonical form — feeds
  the NEXT entry's `:engi/prev-hash`."
  [entry]
  (cid/graph-cid-from-name (core/canonical-entry entry)))

;; ── sign / verify (over the transfer-id, see ns docstring) ────────────────

(defn sign-transfer
  "Sign a TransferBody: computes its transfer-id (CID) and an Ed25519
  signature over that CID's UTF-8 bytes. → {:transfer-id :sig}."
  [transfer-body secret-key]
  (let [tid (transfer-id transfer-body)
        sig (.sign ed25519 (cid/text->bytes tid) secret-key)]
    {:transfer-id tid
     :sig (cacao/bytes->base64url sig)}))

(defn sign-transfer-id
  "Sign an already-computed transfer-id directly (the counter-signing party
  never reconstructs the TransferBody from an entry — see ns docstring — so
  it signs the transfer-id it was handed in the propose/validate handshake)."
  [transfer-id-str secret-key]
  (cacao/bytes->base64url (.sign ed25519 (cid/text->bytes transfer-id-str) secret-key)))

(defn verify-transfer-id-sig
  "Verify an Ed25519 signature (base64url) over `transfer-id-str`, by the
  holder of `did` (a did:key). false (never throws) for a malformed sig, a
  non-Ed25519 did:key, or a genuine verification failure."
  [transfer-id-str sig-b64url did]
  (try
    (if-let [pubkey (cid/did-key->ed25519-pub did)]
      (boolean (.verify ed25519 (cacao/base64url->bytes sig-b64url) (cid/text->bytes transfer-id-str) pubkey))
      false)
    (catch :default _ false)))

(defn verify-self-sig
  "Verify `entry`'s `:engi/self-sig` was made by `owner-did` (the did:key
  whose OWN graph this entry lives in — the caller knows this from the graph
  it read the entry off of, it isn't a field of the entry itself)."
  [entry owner-did]
  (verify-transfer-id-sig (:engi/transfer-id entry) (:engi/self-sig entry) owner-did))

(defn verify-counter-sig
  "Verify `entry`'s `:engi/counter-sig` was made by the counterparty
  (`:engi/counterparty` on the entry IS that signer's did:key)."
  [entry]
  (when (:engi/counter-sig entry)
    (verify-transfer-id-sig (:engi/transfer-id entry) (:engi/counter-sig entry) (:engi/counterparty entry))))

;; ── warrant / detector signatures (same primitive, different payload) ─────

(defn sign-str [s secret-key] (cacao/bytes->base64url (.sign ed25519 (cid/text->bytes s) secret-key)))
(defn verify-str-sig [s sig-b64url did]
  (try
    (if-let [pubkey (cid/did-key->ed25519-pub did)]
      (boolean (.verify ed25519 (cacao/base64url->bytes sig-b64url) (cid/text->bytes s) pubkey))
      false)
    (catch :default _ false)))

(defn hex [^js bytes]
  (apply str (map #(-> % (.toString 16) (.padStart 2 "0")) (array-seq bytes))))

(defn sha256-hex [^string s] (hex (sha256 (cid/text->bytes s))))
