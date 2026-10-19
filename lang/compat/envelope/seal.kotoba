;; envelope.seal — the bytes half of the envelope: AES-256-GCM over the
;; object, X25519 + HKDF-SHA256 over the content key.
;;
;; ClojureScript, not .cljc, and every function returns a Promise — Web
;; Crypto has no synchronous API, and this runs where the object actually
;; is: a Cloudflare Worker and a browser. Same reason kotoba-lang/org-signal
;; keeps its CLJS and JVM ratchets as sibling files rather than one .cljc.
;;
;; X25519 and HKDF come from org-signal (audited @noble/curves; Web Crypto
;; HMAC) rather than being written again here — ADR-2607263000 D3 says to
;; consume that stack, and a second X25519 in this workspace would be a
;; second one to get wrong.
;;
;;   (-> (seal/seal-object env plaintext-chunks [{:id did :pub pub}])
;;       (.then (fn [{:keys [envelope chunks]}] …)))
(ns envelope.seal
  (:require [envelope.model :as m]
            [envelope.kem :as kem]
            [kotoba.signal.hkdf :as hkdf]
            [kotoba.signal.x25519 :as x25519]))

;; ------------------------------------------------------------------ bytes

(def ^:private encoder (js/TextEncoder.))

(defn utf8 [s] (.encode encoder s))

(defn b64url
  "Uint8Array -> unpadded base64url. Envelope fields are transported in
  JSON/EDN, so they are text; base64url avoids the `+` and `/` that break
  in a URL fragment, which is where a link secret has to survive."
  [^js u8]
  (-> (js/btoa (.apply js/String.fromCharCode nil u8))
      (.replace (js/RegExp. "\\+" "g") "-")
      (.replace (js/RegExp. "/" "g") "_")
      (.replace (js/RegExp. "=+$") "")))

(defn unb64url [s]
  (let [s (-> s (.replace (js/RegExp. "-" "g") "+") (.replace (js/RegExp. "_" "g") "/"))
        pad (mod (- 4 (mod (.-length s) 4)) 4)
        bin (js/atob (str s (.repeat "=" pad)))]
    (js/Uint8Array.from bin (fn [c] (.charCodeAt c 0)))))

(defn- bytes->u8 [v] (js/Uint8Array.from (clj->js v)))

(defn- random-bytes [n]
  (js/crypto.getRandomValues (js/Uint8Array. n)))

;; -------------------------------------------------------------- AES-GCM

(defn- import-aes [^js raw usages]
  (js/crypto.subtle.importKey "raw" raw #js {:name "AES-GCM"} false (clj->js usages)))

(defn- gcm-encrypt [^js raw-key ^js iv ^js plaintext ^js aad]
  (-> (import-aes raw-key ["encrypt"])
      (.then (fn [k] (js/crypto.subtle.encrypt
                      #js {:name "AES-GCM" :iv iv :additionalData aad}
                      k plaintext)))
      (.then #(js/Uint8Array. %))))

(defn- gcm-decrypt [^js raw-key ^js iv ^js ciphertext ^js aad]
  (-> (import-aes raw-key ["decrypt"])
      (.then (fn [k] (js/crypto.subtle.decrypt
                      #js {:name "AES-GCM" :iv iv :additionalData aad}
                      k ciphertext)))
      (.then #(js/Uint8Array. %))))

;; --------------------------------------------------------- content keys

(def content-key-bytes 32)

(defn generate-content-key
  "A fresh 256-bit content key. One per object, never stored — only ever
  wrapped. Not a Promise: `getRandomValues` is synchronous everywhere Web
  Crypto exists."
  []
  (random-bytes content-key-bytes))

(defn seal-chunk
  "Encrypt chunk `i` of an object under its content key.
  -> Promise<Uint8Array> (ciphertext||tag). The nonce is derived, not
  stored: `m/chunk-nonce` of the chunk's epoch and index."
  [env ^js content-key i ^js plaintext]
  (gcm-encrypt content-key
               (bytes->u8 (m/chunk-nonce (m/chunk-epoch env i) i))
               plaintext
               (utf8 (m/chunk-aad env i))))

(defn open-chunk
  "Inverse of `seal-chunk`. Rejects (OperationError) if the ciphertext, the
  index, the epoch, the object id or the chunk count disagree with what was
  sealed — a swapped, replayed, relocated or truncated object does not
  open."
  [env ^js content-key i ^js ciphertext]
  (gcm-decrypt content-key
               (bytes->u8 (m/chunk-nonce (m/chunk-epoch env i) i))
               ciphertext
               (utf8 (m/chunk-aad env i))))

;; ------------------------------------------------------------- key wrap

(def ^:private wrap-info "kotoba/envelope/v1 wrap")

(defn- wrap-key-from-dh [^js shared ^js eph-pub ^js recipient-pub]
  ;; The ephemeral and recipient public keys go into HKDF's info, not just
  ;; the DH output: it binds the derived key to the exact pair it came
  ;; from, which is what stops a wrap from being replayed against a
  ;; different recipient's entry.
  (let [info (js/Uint8Array. (+ (.-length (utf8 wrap-info)) 64))]
    (.set info (utf8 wrap-info) 0)
    (.set info eph-pub (.-length (utf8 wrap-info)))
    (.set info recipient-pub (+ (.-length (utf8 wrap-info)) 32))
    (hkdf/hkdf nil shared info content-key-bytes)))

(defn wrap-bytes
  "Wrap arbitrary `plaintext` bytes to one X25519 public key under an
  explicit `aad` string. -> Promise<wrap map>.

  Fresh ephemeral keypair per wrap (ECIES shape): the sender needs no
  long-term key of their own, and two wraps of the same plaintext to the
  same public key share no key material.

  AAD is a parameter rather than derived here because this is the wrap
  primitive for the whole workspace, not just for envelopes. A content key
  binds to `m/wrap-aad`; `kotoba-lang/custody` binds a Shamir share to its
  deal, epoch and custodian instead. Both need the SAME ECIES construction
  and neither should write a second X25519 to get it — which is the reason
  this is one function taking an AAD and not two implementations."
  [^js plaintext pub aad]
  (let [recipient-pub (if (string? pub) (unb64url pub) pub)
        {eph-priv :priv eph-pub :pub} (x25519/generate-keypair)
        shared (x25519/dh eph-priv recipient-pub)
        iv (random-bytes m/nonce-bytes)]
    (-> (wrap-key-from-dh shared eph-pub recipient-pub)
        (.then (fn [wk] (gcm-encrypt wk iv plaintext (utf8 aad))))
        (.then (fn [wrapped]
                 {:wrap/pub (b64url recipient-pub)
                  :wrap/ephemeral-pub (b64url eph-pub)
                  :wrap/iv (b64url iv)
                  :wrap/wrapped (b64url wrapped)})))))

(defn unwrap-bytes
  "Inverse of `wrap-bytes`. -> Promise<Uint8Array>. Rejects if the wrap was
  tampered with, or if `aad` is not byte-identical to the one it was sealed
  under — which is what makes a wrap non-transplantable."
  [{:keys [:wrap/kem :wrap/pub :wrap/ephemeral-pub :wrap/iv :wrap/wrapped]} priv aad]
  (if (some? kem)
    ;; Rejected, not thrown. This file's contract is that every function
    ;; returns a Promise, and a host that only attaches `.catch` would miss
    ;; a synchronous throw entirely -- the refusal would crash the caller
    ;; instead of failing closed, which is the opposite of what it is for.
    (js/Promise.reject
     (ex-info "wrap declares a KEM; refusing to open it classically"
              {:wrap/kem kem}))
    (let [priv (if (string? priv) (unb64url priv) priv)
        eph-pub (unb64url ephemeral-pub)
          shared (x25519/dh priv eph-pub)]
      (-> (wrap-key-from-dh shared eph-pub (unb64url pub))
          (.then (fn [wk] (gcm-decrypt wk (unb64url iv) (unb64url wrapped)
                                       (utf8 aad))))))))

(defn wrap-bytes-hybrid
  "Wrap arbitrary `plaintext` bytes to a HYBRID recipient — one that
  publishes both an X25519 public key and an ML-KEM-768 public key — under
  an explicit `aad` string. -> Promise<wrap map>.

  The post-quantum sibling of `wrap-bytes`, and a parameter-taking AAD for
  the same reason that one takes it: this is the wrap primitive for the
  whole workspace, not just for envelopes. `wrap-for-hybrid` derives the AAD
  from the envelope and recipient id; kotobase's recipient-bound disclosure
  binds `binding(grant)` instead, which is a CID of the grant and not
  something this repo can compute. Both need the SAME hybrid construction,
  and a second one written to get a different AAD in is exactly the drift
  `wrap-bytes` was factored out to avoid.

  The returned map carries `:wrap/kem`. `unwrap-bytes` refuses a map that
  carries it and `unwrap-bytes-hybrid` refuses one that does not: which
  construction opens a wrap is read off the wrap, never negotiated."
  [^js plaintext {:keys [pub pq-pub]} aad]
  (let [recipient-pub (if (string? pub) (unb64url pub) pub)
        recipient-pq (if (string? pq-pub) (unb64url pq-pub) pq-pub)
        iv (random-bytes m/nonce-bytes)]
    (-> (kem/encapsulate {:pub recipient-pub :pq-pub recipient-pq})
        (.then (fn [{:keys [wrap-key ephemeral-pub pq-ct]}]
                 (-> (gcm-encrypt wrap-key iv plaintext (utf8 aad))
                     (.then (fn [wrapped]
                              {:wrap/kem m/hybrid-kem
                               :wrap/pub (b64url recipient-pub)
                               :wrap/pq-pub (b64url recipient-pq)
                               :wrap/ephemeral-pub (b64url ephemeral-pub)
                               :wrap/pq-ct (b64url pq-ct)
                               :wrap/iv (b64url iv)
                               :wrap/wrapped (b64url wrapped)}))))))))

(defn unwrap-bytes-hybrid
  "Inverse of `wrap-bytes-hybrid`. -> Promise<Uint8Array>.

  Rejects if either KEM half is wrong, if the encapsulation was substituted,
  if the wrap was tampered with, or if `aad` is not byte-identical to the
  one it was sealed under. There is no classical fallback: a wrap that does
  not declare the hybrid KEM is refused rather than opened the other way,
  because a fallback is the downgrade the hybrid exists to prevent."
  [{:keys [:wrap/kem :wrap/pub :wrap/ephemeral-pub :wrap/pq-ct
           :wrap/iv :wrap/wrapped]}
   {:keys [priv pq-priv]} aad]
  (cond
    (not= m/hybrid-kem kem)
    (js/Promise.reject (ex-info "not a hybrid wrap; refusing to open it as one"
                                {:wrap/kem kem :expected m/hybrid-kem}))

    (nil? pq-priv)
    (js/Promise.reject (ex-info "hybrid wrap needs the ML-KEM private key"
                                {:wrap/kem kem}))

    :else
    (-> (kem/decapsulate {:priv (if (string? priv) (unb64url priv) priv)
                          :pq-priv pq-priv}
                         {:ephemeral-pub (unb64url ephemeral-pub)
                          :pq-ct (unb64url pq-ct)
                          :recipient-pub (unb64url pub)})
        (.then (fn [wk] (gcm-decrypt wk (unb64url iv) (unb64url wrapped)
                                     (utf8 aad)))))))

(def ^:private symmetric-wrap-info "kotoba/envelope/v1 symmetric-wrap")

(defn wrap-under-key
  "Wrap `plaintext` under a raw secret that both sides already hold, with an
  explicit `aad` string. -> Promise<wrap map>.

  The asymmetric sibling is `wrap-bytes`; this exists because not every
  secret arrives as a public key. A passkey PRF output is the case that
  forced it: the authenticator hands back the same 32 bytes for the same
  (credential, salt) and nothing else, so there is no public key to encrypt
  to — only a secret to derive a key-encryption key from.

  The secret is passed through HKDF rather than used as an AES key
  directly. A PRF output is uniform, but the next caller's secret may not
  be, and `salt` binds this wrap to a context so the same secret used for
  two purposes does not produce the same key."
  [^js plaintext ^js secret ^js salt aad]
  (let [iv (random-bytes m/nonce-bytes)]
    (-> (hkdf/hkdf salt secret (utf8 symmetric-wrap-info) content-key-bytes)
        (.then (fn [wk] (gcm-encrypt wk iv plaintext (utf8 aad))))
        (.then (fn [wrapped]
                 {:wrap/kind :symmetric
                  :wrap/iv (b64url iv)
                  :wrap/salt (when salt (b64url salt))
                  :wrap/wrapped (b64url wrapped)})))))

(defn unwrap-under-key
  "Inverse of `wrap-under-key`. -> Promise<Uint8Array>. Rejects if the wrap
  was tampered with, if the secret is not the one it was sealed under, or
  if `aad` differs by a byte."
  [{:keys [:wrap/iv :wrap/salt :wrap/wrapped]} ^js secret aad]
  (-> (hkdf/hkdf (when salt (unb64url salt)) secret
                 (utf8 symmetric-wrap-info) content-key-bytes)
      (.then (fn [wk] (gcm-decrypt wk (unb64url iv) (unb64url wrapped)
                                   (utf8 aad))))))

(defn wrap-for
  "Wrap `content-key` to one recipient's X25519 public key.
  -> Promise<recipient entry>."
  [env {:keys [id pub kind]} ^js content-key]
  (-> (wrap-bytes content-key pub (m/wrap-aad env id))
      (.then (fn [w]
               {:recipient/id id
                :recipient/kind (or kind :did)
                :recipient/pub (:wrap/pub w)
                :recipient/ephemeral-pub (:wrap/ephemeral-pub w)
                :recipient/iv (:wrap/iv w)
                :recipient/wrapped (:wrap/wrapped w)}))))

;; --------------------------------------------------- hybrid (post-quantum)
;;
;; Same envelope, same AEAD, same recipient entry shape — only the KEM that
;; derives the wrap key changes, plus the ML-KEM ciphertext the recipient
;; needs to decapsulate. Keeping the surface identical is deliberate: a
;; caller upgrades by handing `wrap-for` a recipient that has `:pq-pub`, not
;; by learning a second API.

(defn wrap-for-hybrid
  "Wrap `content-key` to a recipient that publishes BOTH an X25519 public key
  and an ML-KEM-768 public key. -> Promise<recipient entry>.

  The entry carries `:recipient/pq-ct` (1088 B) in addition to the classical
  fields, and its AAD is the hybrid form — so it cannot be swapped for a
  classical wrap of the same object and recipient."
  [env {:keys [id pub pq-pub kind]} ^js content-key]
  (let [recipient-pub (if (string? pub) (unb64url pub) pub)
        recipient-pq (if (string? pq-pub) (unb64url pq-pub) pq-pub)
        iv (random-bytes m/nonce-bytes)
        aad (m/wrap-aad env id m/hybrid-kem)]
    (-> (kem/encapsulate {:pub recipient-pub :pq-pub recipient-pq})
        (.then (fn [{:keys [wrap-key ephemeral-pub pq-ct]}]
                 (-> (gcm-encrypt wrap-key iv content-key (utf8 aad))
                     (.then (fn [wrapped]
                              {:recipient/id id
                               :recipient/kind (or kind :did)
                               :recipient/kem m/hybrid-kem
                               :recipient/pub (b64url recipient-pub)
                               :recipient/pq-pub (b64url recipient-pq)
                               :recipient/ephemeral-pub (b64url ephemeral-pub)
                               :recipient/pq-ct (b64url pq-ct)
                               :recipient/iv (b64url iv)
                               :recipient/wrapped (b64url wrapped)}))))))))

(defn unwrap-with
  "Recover the content key from a recipient entry, given that recipient's
  private key(s). -> Promise<Uint8Array(32)>. Rejects if the entry was
  tampered with or belongs to a different object or recipient.

  `priv` is the X25519 private key. A hybrid entry additionally needs the
  ML-KEM private key, passed as `pq-priv`.

  **Which construction is used is read off the entry, not negotiated.** An
  entry that declares the hybrid KEM is opened as hybrid or not at all —
  there is no fallback to the classical path, because a fallback is exactly
  the downgrade the hybrid AAD exists to prevent."
  ([env entry priv] (unwrap-with env entry priv nil))
  ([env {:keys [:recipient/id :recipient/pub :recipient/ephemeral-pub
                :recipient/iv :recipient/wrapped :recipient/kem
                :recipient/pq-ct]}
    priv pq-priv]
   (if (= m/hybrid-kem kem)
     (do
       (when (nil? pq-priv)
         (throw (ex-info "hybrid recipient entry needs the ML-KEM private key"
                         {:recipient/id id :recipient/kem kem})))
       (-> (kem/decapsulate {:priv (if (string? priv) (unb64url priv) priv)
                             :pq-priv pq-priv}
                            {:ephemeral-pub (unb64url ephemeral-pub)
                             :pq-ct (unb64url pq-ct)
                             :recipient-pub (unb64url pub)})
           (.then (fn [wk] (gcm-decrypt wk (unb64url iv) (unb64url wrapped)
                                        (utf8 (m/wrap-aad env id m/hybrid-kem)))))))
     (unwrap-bytes {:wrap/pub pub
                    :wrap/ephemeral-pub ephemeral-pub
                    :wrap/iv iv
                    :wrap/wrapped wrapped}
                   priv
                   (m/wrap-aad env id :x25519)))))

;; ------------------------------------------------------------- sharing

(defn share-with
  "Grant `recipient` access to an already-sealed object, given a private
  key that already has access. Re-wraps the content key; never touches the
  ciphertext. -> Promise<envelope>."
  [env from-entry from-priv recipient]
  (-> (unwrap-with env from-entry from-priv)
      (.then (fn [ck] (wrap-for env recipient ck)))
      (.then (fn [entry] (m/put-recipient env entry)))))

(defn mint-link
  "Create a public share link for an object the caller can already open.
  -> Promise<{:envelope … :grant …}>. The grant carries the link's PRIVATE
  key for the URL fragment; the envelope carries only the public half, so
  the server storing it cannot open the object."
  [env from-entry from-priv]
  (let [{:keys [priv pub]} (x25519/generate-keypair)
        id (m/link-recipient-id (b64url pub))]
    (-> (share-with env from-entry from-priv {:id id :pub pub :kind :link})
        (.then (fn [env']
                 {:envelope env'
                  :grant (m/link-grant env'
                                       (first (filter #(= id (:recipient/id %))
                                                      (:envelope/recipients env')))
                                       (b64url priv))})))))

;; ------------------------------------------------------------- objects

(defn seal-object
  "Seal a whole object: fresh content key, every chunk encrypted, the key
  wrapped to every recipient.
  -> Promise<{:envelope … :chunks [Uint8Array …]}>.

  The content key exists only inside this call; it is not returned. A
  caller that wants it back opens the object with a recipient key, like
  everyone else."
  [env-id plaintext-chunks recipients & [opts]]
  (let [chunks (vec plaintext-chunks)
        env (m/envelope env-id (merge {:chunks (count chunks)} opts))
        ck (generate-content-key)]
    (-> (js/Promise.all
         (clj->js (map-indexed (fn [i pt] (seal-chunk env ck i pt)) chunks)))
        (.then (fn [sealed]
                 (-> (js/Promise.all (clj->js (map #(wrap-for env % ck) recipients)))
                     (.then (fn [entries]
                              {:envelope (reduce m/put-recipient env entries)
                               :chunks (vec sealed)}))))))))

(defn open-object
  "Open every chunk with one recipient's private key.
  -> Promise<[Uint8Array …]>."
  [env entry priv ciphertext-chunks]
  (-> (unwrap-with env entry priv)
      (.then (fn [ck]
               (js/Promise.all
                (clj->js (map-indexed (fn [i ct] (open-chunk env ck i ct))
                                      ciphertext-chunks)))))
      (.then vec)))

(defn entry-for [env id]
  (first (filter #(= id (:recipient/id %)) (:envelope/recipients env))))
