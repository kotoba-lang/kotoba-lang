(ns tls.transcript
  "`Transcript-Hash` -- RFC 8446 section 4.4.1.

  The transcript is the concatenation of every handshake message, each
  *including* its four-byte header, in the order they appear on the wire. Not
  the records that carried them: a handshake message split across two records
  hashes once, and two messages in one record hash separately. Keeping the
  transcript at the message layer rather than the record layer is what makes
  that true by construction.

  It is accumulated rather than re-hashed, but the *bytes* are retained, not
  a running hash state: TLS 1.3 needs the hash at four different points
  (ClientHello..ServerHello, ..server Finished, ..client Finished, and for
  CertificateVerify ..Certificate) and a streaming SHA-256 cannot be forked.
  A handshake transcript is a few kilobytes; this is not the place to be
  clever.

  One deliberate exception: `truncate` exists for the HelloRetryRequest
  rewrite of section 4.4.1, where the original ClientHello is replaced by a
  synthetic `message_hash` message. It is not used by the 1-RTT path."
  (:require [tls.codec :as c]))

(defn transcript [] {:tls/messages []})

(defn add
  "Append one complete handshake message (header included)."
  [t message]
  (update t :tls/messages conj (vec message)))

(defn add-all [t messages] (reduce add t messages))

(defn bytes-of [t] (vec (apply concat (:tls/messages t))))

(defn digest
  "`Transcript-Hash(...)` with the provider's hash for this suite."
  [t hash-fn]
  (vec (hash-fn (bytes-of t))))

(defn empty-digest
  "`Transcript-Hash(\"\")` -- the hash of nothing, which the key schedule needs
   for every `Derive-Secret(., \"derived\", \"\")` step."
  [hash-fn]
  (vec (hash-fn [])))

(defn message-hash
  "Section 4.4.1's HelloRetryRequest rewrite: the client's first ClientHello is
   replaced in the transcript by

       message_hash(254) || 0 || 0 || Hash.length || Hash(ClientHello1)

   Present because leaving it out is how an implementation that later adds
   HRR gets a transcript mismatch it cannot see."
  [hash-fn hash-length client-hello-1]
  (vec (concat [254 0 0 hash-length] (vec (hash-fn (vec client-hello-1))))))
