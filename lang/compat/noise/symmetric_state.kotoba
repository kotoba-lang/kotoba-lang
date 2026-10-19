(ns noise.symmetric-state
  "SymmetricState (Noise rev 34 §5.2) — the chaining key `ck`, the running
  handshake hash `h`, and the current CipherState, all pure.

  `h` is the transcript: every public key and every ciphertext that crosses the
  wire is mixed into it, and it is the AEAD's associated data. That is what makes
  a Noise handshake resistant to a man in the middle rewriting an earlier
  message — the final `h` (exposed as the session's handshake hash, usable as a
  channel binding) only matches if both sides saw byte-identical transcripts."
  (:require [kotoba.bytes :as b]
            [noise.cipher-state :as cs]
            [noise.kdf :as kdf]))

(defn initialize-symmetric [suite protocol-name]
  (let [{:keys [hashlen hash]} suite
        pn (b/utf8-encode protocol-name)
        h (if (<= (count pn) hashlen)
            (into pn (repeat (- hashlen (count pn)) 0))
            (vec (hash pn)))]
    {:suite suite :ck h :h h :cs (cs/initialize suite nil)}))

(defn mix-key [{:keys [suite] :as ss} ikm]
  (let [[ck temp-k] (kdf/hkdf suite (:ck ss) (vec ikm) 2)]
    (assoc ss :ck ck :cs (cs/initialize suite (vec (take 32 temp-k))))))

(defn mix-hash [{:keys [suite] :as ss} data]
  (update ss :h (fn [h] (vec ((:hash suite) (into (vec h) (vec data)))))))

(defn mix-key-and-hash
  "For PSK patterns: derives a new ck, mixes an intermediate value into h, and
   re-keys, all from one input (Noise rev 34 §5.2)."
  [{:keys [suite] :as ss} ikm]
  (let [[ck temp-h temp-k] (kdf/hkdf suite (:ck ss) (vec ikm) 3)]
    (-> (assoc ss :ck ck)
        (mix-hash temp-h)
        (assoc :cs (cs/initialize suite (vec (take 32 temp-k)))))))

(defn encrypt-and-hash [ss plaintext]
  (let [[cs' ct] (cs/encrypt-with-ad (:cs ss) (:h ss) plaintext)]
    [(-> ss (assoc :cs cs') (mix-hash ct)) ct]))

(defn decrypt-and-hash
  "Note the ordering: `h` must be mixed with the *ciphertext*, and only after it
   was used as the associated data for the decrypt."
  [ss ciphertext]
  (let [[cs' pt] (cs/decrypt-with-ad (:cs ss) (:h ss) ciphertext)]
    [(-> ss (assoc :cs cs') (mix-hash ciphertext)) pt]))

(defn split
  "Split into the two transport CipherStates (initiator→responder,
   responder→initiator)."
  [{:keys [suite ck]}]
  (let [[k1 k2] (kdf/hkdf suite ck [] 2)]
    [(cs/initialize suite (vec (take 32 k1)))
     (cs/initialize suite (vec (take 32 k2)))]))
