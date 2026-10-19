(ns noise.cipher-state
  "CipherState (Noise rev 34 §5.1) — a key plus a 64-bit nonce, pure.

  Every function returns `[cipher-state' result]`; nothing mutates. That matters
  more than it looks: a CipherState whose nonce silently failed to advance
  reuses a keystream, so making the advance part of the return value means a
  caller cannot forget it without also losing the ciphertext."
  (:refer-clojure :exclude [empty?]))

(def ^:const max-nonce
  "The largest nonce this implementation will use, 2^53-1.

   Noise reserves 2^64-1 and requires that reaching the maximum terminate the
   session rather than wrap. We stop lower, at the largest integer both target
   runtimes represent exactly (JS numbers are doubles; 2^64-1 is not even a JVM
   `long`). This is a *tightening* of the spec's bound, not a relaxation — the
   session dies earlier than Noise requires, never later, and `noise.session`'s
   rekey policy fires many orders of magnitude before this anyway (2^48
   messages ≈ 9 years at a sustained million packets per second).

   The one place the real 2^64-1 nonce is needed on the wire — REKEY's
   `ENCRYPT(k, 2^64-1, ...)` — uses `rekey-nonce-bytes` below, so the byte
   encoding stays spec-exact even though the counter type does not reach it."
  9007199254740991)

(def rekey-nonce-bytes
  "The 12-byte ChaCha20-Poly1305 encoding of nonce 2^64-1: four zero bytes then
   eight 0xff, i.e. what `(chachapoly-nonce (dec (pow 2 64)))` would produce if
   that number were representable."
  [0 0 0 0 255 255 255 255 255 255 255 255])

(defn initialize [suite k]
  {:suite suite :k (when k (vec k)) :n 0})

(defn has-key? [cs] (some? (:k cs)))

(defn set-nonce [cs n] (assoc cs :n n))

(defn- check-nonce! [{:keys [n]}]
  (when (>= n max-nonce)
    (throw (ex-info "noise nonce exhausted — session must be terminated" {:n n}))))

(defn encrypt-with-ad
  "-> [cs' ciphertext]. With no key this is the identity on the plaintext, which
   is what the pre-`:e`-token part of a handshake needs."
  [cs ad plaintext]
  (if-not (has-key? cs)
    [cs (vec plaintext)]
    (do (check-nonce! cs)
        (let [{:keys [suite k n]} cs
              ct ((:aead-encrypt suite) k ((:nonce-bytes suite) n) (vec ad) (vec plaintext))]
          [(update cs :n inc) (vec ct)]))))

(defn decrypt-with-ad
  "-> [cs' plaintext]. Throws on authentication failure — and critically, does
   NOT advance the nonce in that case (Noise rev 34 §5.1), so a forged packet
   cannot be used to desynchronize a peer's counter."
  [cs ad ciphertext]
  (if-not (has-key? cs)
    [cs (vec ciphertext)]
    (do (check-nonce! cs)
        (let [{:keys [suite k n]} cs
              pt ((:aead-decrypt suite) k ((:nonce-bytes suite) n) (vec ad) (vec ciphertext))]
          (when (nil? pt)
            (throw (ex-info "noise AEAD authentication failed" {:n n})))
          [(update cs :n inc) (vec pt)]))))

(defn rekey
  "REKEY(k) = ENCRYPT(k, 2^64-1, zerolen, zeros[32]) (Noise rev 34 §11.3).
   Forward-secures a long-lived transport key without a new handshake."
  [{:keys [suite k] :as cs}]
  (let [ct ((:aead-encrypt suite) k rekey-nonce-bytes [] (vec (repeat 32 0)))]
    (assoc cs :k (vec (take 32 ct)))))
