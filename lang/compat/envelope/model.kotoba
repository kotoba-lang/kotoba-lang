(ns envelope.model
  "The envelope: what a sealed object's key material looks like, and every
  decision about it that can be made WITHOUT crypto.

  One object (a Drive file, a stored mail body) is encrypted once, under one
  random content key. That key is never stored — only copies of it wrapped to
  each recipient's X25519 public key. Sharing re-wraps; it never re-encrypts
  the object. This namespace owns the shape and the rules; `envelope.seal`
  (ClojureScript, async, Web Crypto + @noble/curves) owns the bytes.

  Split on purpose: everything here is pure and portable, so the rules that
  actually matter — nonce uniqueness, what a revoke does and does not
  accomplish, what the AEAD binds — are testable without a crypto runtime,
  on every runtime this workspace targets.

      {:envelope/id          \"drv:abc123\"      ; caller's stable object id
       :envelope/version     1
       :envelope/alg         :aes-256-gcm
       :envelope/kdf         :hkdf-sha256
       :envelope/kem         :x25519        ; or :x25519+ml-kem-768
       :envelope/chunk-bytes 4194304
       :envelope/chunks      3
       :envelope/nonce-epoch 0
       :envelope/recipients  [{:recipient/id   \"did:key:z6Mk…\"
                               :recipient/kind :did          ; or :link
                               :recipient/pub  \"base64url…\"  ; X25519 pub
                               :recipient/ephemeral-pub \"base64url…\"
                               :recipient/iv   \"base64url…\"
                               :recipient/wrapped \"base64url…\"
                               ;; hybrid recipients only (:x25519+ml-kem-768):
                               :recipient/kem    :x25519+ml-kem-768
                               :recipient/pq-pub \"base64url…\"  ; ML-KEM-768 pub
                               :recipient/pq-ct  \"base64url…\"}]} ; 1088 B

  ADR-2607263000 D3/D4/D5."
  (:require [kotoba.lang.text :as str]))

(def version 1)

(def defaults
  {:envelope/version version
   :envelope/alg :aes-256-gcm
   :envelope/kdf :hkdf-sha256
   :envelope/kem :x25519
   ;; 4 MiB: large enough that a big file is not thousands of round trips,
   ;; small enough to stay well inside a Worker's per-request body budget
   ;; and to make a range read cheap (ADR-2607263000 D5).
   :envelope/chunk-bytes (* 4 1024 1024)
   :envelope/nonce-epoch 0})

(def supported-algs #{:aes-256-gcm})

;; `:x25519` is the classical wrap this repo shipped with. `:x25519+ml-kem-768`
;; is the hybrid added 2026-08-08 per superproject ADR-2608070400 D5: a wrap
;; harvested today is decryptable by a future CRQC if X25519 is its only KEM,
;; and the wrap holds the content key, so that is the whole object. Hybrid
;; means an attacker must break BOTH — so it is never weaker than what it
;; extends, which is the only reason to combine rather than swap.
(def supported-kems #{:x25519 :x25519+ml-kem-768})

(def hybrid-kem :x25519+ml-kem-768)

(defn hybrid?
  "True when this envelope's KEM includes the post-quantum half."
  [env]
  (= hybrid-kem (:envelope/kem env)))

;; ------------------------------------------------------------- construction

(defn envelope
  "A sealed-object descriptor with no recipients yet. `id` is the caller's
  own stable identifier for the object; it is bound into the AEAD, so it
  cannot be changed later without re-encrypting."
  [id {:keys [chunk-bytes chunks] :as opts}]
  (merge defaults
         {:envelope/id id
          :envelope/chunks (or chunks 0)
          :envelope/recipients []}
         (when chunk-bytes {:envelope/chunk-bytes chunk-bytes})
         (dissoc opts :chunk-bytes :chunks)))

(defn chunk-count
  "How many chunks `byte-length` splits into. Zero bytes is ONE chunk, not
  zero: an empty file still has to be authenticated, and a zero-chunk
  envelope would let a truncation to empty pass as a valid read."
  [byte-length chunk-bytes]
  (if (zero? byte-length) 1 (long (Math/ceil (/ (double byte-length) chunk-bytes)))))

(defn valid?
  "Structural validity. Deliberately NOT a crypto check — it says this
  envelope is one this code knows how to open, not that it opens."
  [{:keys [:envelope/id :envelope/version :envelope/alg :envelope/kem
           :envelope/chunks :envelope/chunk-bytes] :as env}]
  (boolean
   (and (map? env)
        (string? id) (seq id)
        (= version (:envelope/version defaults))
        (contains? supported-algs alg)
        (contains? supported-kems kem)
        (integer? chunks) (pos? chunks)
        (integer? chunk-bytes) (pos? chunk-bytes)
        (vector? (:envelope/recipients env)))))

;; ----------------------------------------------------------------- nonces

(def nonce-bytes 12)

(defn- u32-bytes
  "An unsigned 32-bit integer as 4 big-endian bytes."
  [n]
  (mapv #(mod (quot n %) 256) [16777216 65536 256 1]))

(defn chunk-nonce
  "The AES-GCM nonce for chunk `i`: epoch (4 bytes, big-endian) || index
  (8 bytes, big-endian). Returned as a vector of unsigned bytes so this
  stays pure `.cljc`.

  Deterministic rather than random on purpose. GCM's one unforgivable
  failure is reusing a nonce under the same key, and a deterministic
  counter makes that a property you can check by reading the code instead
  of a probability you argue about. The content key is fresh per object, so
  (key, index) is unique — as long as a chunk is never re-encrypted in
  place, which is exactly what the epoch is for: rewriting any chunk bumps
  `:envelope/nonce-epoch`, so the rewritten chunk gets a nonce no previous
  chunk under this key has ever used.

  Reading a chunk uses the epoch recorded for THAT chunk, not the
  envelope's current one — see `chunk-epoch`.

  Built from `quot`/`rem`, not bit shifts, and that is not a style
  choice: JavaScript's bitwise operators truncate to 32 bits and take the
  shift count modulo 32, so `(unsigned-bit-shift-right i 32)` is
  `(unsigned-bit-shift-right i 0)` there. The first version of this
  function shifted, and under ClojureScript the top four bytes of the
  index came out as a copy of the bottom four — a nonce that disagrees
  with the same spec implemented on the JVM. Caught by
  `a-nonce-is-never-reused-under-one-key`, which is why it asserts exact
  bytes and not merely distinctness."
  [epoch i]
  {:pre [(nat-int? epoch) (nat-int? i)]}
  (into (u32-bytes epoch)
        (into (u32-bytes (quot i 4294967296))
              (u32-bytes (rem i 4294967296)))))

(defn bump-epoch
  "Rewriting chunk `i` in place. Returns the envelope with a fresh epoch and
  that chunk's epoch recorded, so old chunks keep opening under the epoch
  they were sealed with."
  [env i]
  (let [next (inc (:envelope/nonce-epoch env 0))]
    (-> env
        (assoc :envelope/nonce-epoch next)
        (assoc-in [:envelope/chunk-epochs i] next))))

(defn chunk-epoch
  "Which epoch chunk `i` was sealed under. Chunks written at creation carry
  no entry and use epoch 0."
  [env i]
  (get-in env [:envelope/chunk-epochs i] 0))

;; -------------------------------------------------------------------- AAD

(defn chunk-aad
  "The additional authenticated data for chunk `i` — authenticated, not
  encrypted. Binds the ciphertext to its object, its position, its epoch,
  and the total chunk count, so that swapping two chunks, replaying an old
  chunk, moving a chunk between objects, or truncating the object all fail
  to open rather than opening as something plausible.

  A string; `envelope.seal` UTF-8 encodes it."
  [{:keys [:envelope/id :envelope/version :envelope/chunks] :as env} i]
  (str/join "|" ["kotoba/envelope" version id (chunk-epoch env i) i chunks]))

(defn wrap-aad
  "AAD for a wrapped content key: binds the wrap to this object and this
  recipient, so a wrap harvested from one envelope cannot be pasted into
  another to make it look like that recipient was granted access.

  **It also binds the KEM, and that is a downgrade defence.** Without it a
  classical wrap and a hybrid wrap for the same (object, recipient) carry
  byte-identical AAD, so the two are interchangeable as far as the AEAD is
  concerned. An attacker who can make the hybrid path fail — a stripped
  ML-KEM ciphertext, a client that falls back on error — could then have a
  classical wrap accepted where a hybrid one was intended, and the
  post-quantum half would be gone without anything refusing.

  `:x25519` keeps the original string exactly. Adding the KEM to it would
  have changed the AAD of every wrap already written, and AAD must match
  byte-for-byte or the wrap stops opening. So the classical form is frozen
  and only the hybrid form carries the suffix — which is enough, because
  distinguishing the two is the whole requirement."
  ([env recipient-id] (wrap-aad env recipient-id (:envelope/kem env)))
  ([{:keys [:envelope/id :envelope/version]} recipient-id kem]
   (let [base (str/join "|" ["kotoba/envelope/wrap" version id recipient-id])]
     (if (= hybrid-kem kem)
       (str base "|" (name hybrid-kem))
       base))))

;; ------------------------------------------------------------- recipients

(defn recipient-ids [env]
  (mapv :recipient/id (:envelope/recipients env)))

(defn has-recipient? [env id]
  (boolean (some #(= id (:recipient/id %)) (:envelope/recipients env))))

(defn put-recipient
  "Add or replace a recipient's wrapped key. Replacing is how a rotated
  content key is redistributed."
  [env {:keys [:recipient/id] :as entry}]
  (update env :envelope/recipients
          (fn [rs]
            (let [rs (vec (remove #(= id (:recipient/id %)) rs))]
              (conj rs entry)))))

(defn revoke
  "Remove a recipient's wrapped key.

  Returns `{:envelope … :requires-rotation? true/false}`. The flag is the
  whole point: deleting a wrap stops the recipient from deriving the
  content key AGAIN, it does not make them forget one they already
  derived. Any revoke of a recipient that ever had access therefore
  requires rotating the content key and re-encrypting to be meaningful —
  and a caller that ignores the flag has built revocation theatre. A
  recipient that was added but demonstrably never fetched the object is
  the only case where it is false, and this namespace cannot know that, so
  it never claims it."
  [env id]
  (let [present? (has-recipient? env id)]
    {:envelope (update env :envelope/recipients
                       (fn [rs] (vec (remove #(= id (:recipient/id %)) rs))))
     :requires-rotation? present?}))

;; ------------------------------------------------------------ public links

(defn link-recipient-id
  "Public links are recipients too — a link is just a keypair nobody is.
  The id is derived from the link's own public key so two links to the
  same object never collide and neither is guessable from the other."
  [pub-b64url]
  (str "link:" pub-b64url))

(defn link?
  [{:keys [:recipient/kind]}] (= :link kind))

(defn link-grant
  "What the holder of a share URL needs, and what must never reach the
  server: the link keypair's PRIVATE key.

  Returned for the caller to place in the URL **fragment** (`#`), which
  browsers do not send to the origin. The content key itself is never put
  in a link — a link is revoked by deleting its wrap entry, which works
  precisely because the link holder only ever had the link key, and
  unwrapping is what the deleted entry made possible.

  This function does no crypto; it names the shape and states where the
  secret goes, so no caller has to invent that convention twice."
  [env {:keys [:recipient/id] :as _entry} link-priv-b64url]
  {:grant/kind :link
   :grant/envelope-id (:envelope/id env)
   :grant/recipient-id id
   :grant/secret link-priv-b64url
   :grant/placement :url-fragment
   :grant/never-send-to-origin true})
