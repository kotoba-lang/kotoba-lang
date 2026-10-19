(ns nekko.key-journal
  "A signed, hash-chained history of one mailbox's key events, so that a
  server which swaps the published mailbox public key is *detected* rather
  than trusted (cloud-itonami ADR-0038's sharpest residual risk).

  ## Why one root secret

  Everything is derived from a single 32-byte root secret that is generated on
  the client and only ever leaves it inside `nekko.keyslot` ciphertext:

    root ──HKDF \"…/v1/x25519\"──► X25519 private key   (the mailbox key; pub published)
         └─HKDF \"…/v1/journal\"─► Ed25519 seed         (signs every journal entry)

  That is the whole trick. Any device that can open ONE keyslot recovers the
  root, and can therefore derive the journal public key *itself* and verify the
  entire chain from genesis — it does not have to be told which signer to
  trust, which is what a malicious server would want to tell it. The server
  holds no keyslot, so it cannot produce an entry that verifies. Substitution
  stops being a matter of trust and becomes a signature check.

  Pinning still matters, for a different attack: a server cannot forge an
  entry, but it can serve a *truncated* chain (rolling back to before a keyslot
  was revoked). `extends-pinned?` is that check — the head a client saw before
  must still be in the chain it is served now.

  ## Host

  Client-only, like `nekko.keyslot`, and for the same reason: a server able to
  sign key events is the capability being removed. `ed25519.core`'s cljs branch
  targets node:crypto and so cannot run in a browser or a Worker, so Ed25519
  here is the `noble/curves` ed25519 module -- pure JS, synchronous, and already this
  workspace's browser-side curve (org-signal uses the same import). Chaining
  and CIDs come from `nekko.journal`/`chain.core`, whose SHA-256 reaches
  `@noble/hashes` on cljs and is likewise browser-safe: this namespace adds a
  signature layer and reinvents neither.

  Storage is injected exactly as `chain.core` wants it — `put!` (cid, bytes)
  and `get-fn` (cid -> bytes) — so the caller decides whether entries live in
  KV, D1 or memory. This namespace never assumes the store is honest; every
  read is CID-verified by `chain.core` and signature-verified here."
  ;; `derive` shadows cljs.core/derive, which every consumer's build warns
  ;; about. Excluding it says the shadowing is deliberate and silences a
  ;; warning that would otherwise train readers to ignore warnings.
  (:refer-clojure :exclude [derive])
  (:require [ipld.core :as ipld]
            [nekko.journal :as journal]
            [nekko.recipient-grant-async :as rga]
            ["@noble/curves/ed25519.js" :refer [ed25519 x25519]]))

(def ^:private hkdf-x25519 "nekko/mailbox-key/v1/x25519")
(def ^:private hkdf-journal "nekko/mailbox-key/v1/journal")

(defn- subtle [] (aget (aget js/globalThis "crypto") "subtle"))
(defn- utf8 [s] (.encode (js/TextEncoder.) s))

(defn- hkdf
  "-> Promise<Uint8Array 32B>. Salt is empty on purpose: the root secret is
  already uniformly random (it comes from getRandomValues), so extract needs
  no additional entropy, and a per-derivation salt would have to be stored and
  could then be tampered with. The info string is the only thing separating
  the two derived keys, so those strings are load-bearing and versioned."
  [root info]
  (-> (.importKey (subtle) "raw" (js/Uint8Array. root) #js {"name" "HKDF"}
                  false #js ["deriveBits"])
      (.then (fn [prk]
               (.deriveBits (subtle)
                            #js {"name" "HKDF" "hash" "SHA-256"
                                 "salt" (js/Uint8Array. 0) "info" (utf8 info)}
                            prk 256)))
      (.then (fn [bits] (js/Uint8Array. bits)))))

(defn new-root-secret
  "A fresh 32-byte mailbox root secret. Generate this on the CLIENT; the server
  must never see it in the clear."
  []
  (rga/random-bytes 32))

(defn derive
  "root (Uint8Array 32B) ->
  Promise<{:x25519-priv :x25519-pub :journal-seed :journal-pub}>, all hex.

  `:x25519-priv` is what `nekko.recipient-grant-async/open` takes and
  `:x25519-pub` is what an inbound sealer seals to. Note the two are produced by
  DIFFERENT implementations -- the public half by `noble/curves` here, the
  private half consumed by WebCrypto over there — so a disagreement between
  them would silently produce mail nobody can open. `key_journal_test` seals a
  real grant to `:x25519-pub` and opens it with `:x25519-priv` to pin that
  down; do not delete that test.

  `:journal-pub` is what `verify` takes. Derive it, never read it out of the
  chain: the chain is what an attacker controls."
  [root]
  (-> (js/Promise.all #js [(hkdf root hkdf-x25519) (hkdf root hkdf-journal)])
      (.then (fn [pair]
               (let [x (aget pair 0) seed (aget pair 1)]
                 {:x25519-priv (rga/hexify x)
                  :x25519-pub (rga/hexify (.getPublicKey x25519 x))
                  :journal-seed (rga/hexify seed)
                  :journal-pub (rga/hexify (.getPublicKey ed25519 seed))})))))

;; ── entries ─────────────────────────────────────────────────────────────────

(defn- payload-bytes
  "Canonical bytes signed by an entry: the entry WITHOUT its signature,
  DAG-CBOR encoded (map keys sorted, so the same entry always encodes the same
  way on every host)."
  [entry]
  (ipld/encode (dissoc entry "sig")))

(defn entry
  "Build a signed entry. `event` is a keyword-ish string
  (\"mailbox-key/created\", \"keyslot/added\", \"keyslot/removed\",
  \"mailbox-key/rotated\"); `mailbox-pub` is the X25519 pubkey hex this event
  asserts; `detail` is an opaque DAG-CBOR-encodable map or nil.

  `signerPub` is carried in every entry and checked to be constant across the
  chain, so a chain cannot quietly change signer halfway."
  [{:keys [journal-seed journal-pub]} event mailbox-pub detail]
  (let [body {"v" 1 "event" event "mailboxPub" mailbox-pub
              "detail" (or detail {}) "signerPub" journal-pub}
        sig (.sign ed25519 (payload-bytes body) (rga/unhex journal-seed))]
    (assoc body "sig" (rga/hexify sig))))

(defn append!
  "Append a signed entry, returning the new head CID. `head-cid` is nil for
  genesis."
  [put! get-fn head-cid keys event mailbox-pub detail]
  (journal/append! put! get-fn head-cid (entry keys event mailbox-pub detail)))

;; ── verification ────────────────────────────────────────────────────────────

(defn- entry-signature-ok? [journal-pub e]
  (let [stored (get e "sig")
        ;; :cid/:seq are attached by journal/entries for the reader's benefit
        ;; and were never part of what was signed.
        body (dissoc e "sig" :cid :seq)]
    (boolean
     (and stored
          (= journal-pub (get e "signerPub"))
          (.verify ed25519 (rga/unhex stored) (payload-bytes body)
                   (rga/unhex journal-pub))))))

(defn verify
  "True when the chain at `head-cid` is hash-chain-valid AND every entry is
  signed by `journal-pub` (which the caller DERIVED from the root secret — do
  not read it out of the chain, that is the thing an attacker controls).

  Returns false, never throws, for every failure mode -- bad signature, wrong
  signer, broken hash chain, and a store handing back bytes that do not hash to
  the CID it was asked for. `chain.core/verify-chain` catches its own CID
  mismatch and returns false; an earlier version of this docstring claimed it
  threw, which the test caught."
  [get-fn head-cid journal-pub]
  (and (journal/verify get-fn head-cid)
       (every? (partial entry-signature-ok? journal-pub)
               (journal/entries get-fn head-cid))))

(defn current-mailbox-pub
  "The mailbox pubkey asserted by the newest entry, or nil for an empty
  journal. Only meaningful together with `verify` — read it after verifying,
  never instead."
  [get-fn head-cid]
  (some-> (last (journal/entries get-fn head-cid)) (get "mailboxPub")))

(defn extends-pinned?
  "True when the chain at `head-cid` still contains `pinned-cid` — i.e. the
  server is serving something that EXTENDS what this client already saw, not a
  truncated or forked history. nil `pinned-cid` means first contact (nothing to
  compare), which is the one moment a client has to trust what it is given."
  [get-fn head-cid pinned-cid]
  (or (nil? pinned-cid)
      (boolean (some #(= pinned-cid (:cid %)) (journal/entries get-fn head-cid)))))

(defn accept?
  "The whole client-side check in one call: the chain verifies under a
  self-derived signer, it extends what we pinned, and the mailbox pubkey we are
  about to encrypt to is the one the newest entry asserts. Returns
  {:ok? :mailbox-pub :head} — on a false :ok?, refuse to use the key."
  [get-fn head-cid journal-pub pinned-cid]
  (let [ok? (and (verify get-fn head-cid journal-pub)
                 (extends-pinned? get-fn head-cid pinned-cid))]
    {:ok? ok?
     :mailbox-pub (when ok? (current-mailbox-pub get-fn head-cid))
     :head head-cid}))
