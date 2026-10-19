(ns kotoba.net.libp2p.keys
  "Verifying a libp2p identity signature, whatever key type the peer has.

  Mechanism, not policy: this says whether a signature is valid, and says
  nothing about whether a peer with that key type should be talked to. The
  split matters because most peers on the network today still have RSA
  identities -- a verifier that only knew Ed25519 would make a compatibility
  limit look like a security decision.

  Each key type carries its public key in the encoding libp2p specifies for it,
  and they are not the same shape: Ed25519 is the raw 32 octets, RSA and ECDSA
  are DER SubjectPublicKeyInfo. Decoding one as the other fails as an invalid
  signature, which is the least informative way to find out."
  (:import [java.security KeyFactory Signature]
           [java.security.spec X509EncodedKeySpec]))

(defn- ->bytes [octets] (byte-array (map unchecked-byte octets)))

(defn- verify-with [algorithm key-spec-fn key message signature]
  (let [public (key-spec-fn key)
        verifier (doto (Signature/getInstance algorithm) (.initVerify public))]
    (.update verifier (->bytes message))
    (.verify verifier (->bytes signature))))

(defn- spki [algorithm key]
  (.generatePublic (KeyFactory/getInstance algorithm)
                   (X509EncodedKeySpec. (->bytes key))))

(defn verifier
  "A `verify-fn` for `handshake/verify` covering the types the JVM can check.

  `accept` bounds which types are admitted at all -- a caller that wants
  Ed25519 only says so here, and gets a refusal that names the reason instead
  of a signature failure that does not."
  ([] (verifier #{:ed25519 :rsa :ecdsa}))
  ([accept]
   (fn [{:keys [key-type key]} message signature]
     (when (contains? accept key-type)
       (case key-type
         :ed25519 (verify-with "Ed25519"
                               #(spki "Ed25519"
                                      ;; Raw 32 octets on the wire; the JCA wants
                                      ;; SPKI, so the fixed Ed25519 prefix is put
                                      ;; back rather than a parser written.
                                      (concat [0x30 0x2a 0x30 0x05 0x06 0x03 0x2b 0x65 0x70
                                               0x03 0x21 0x00]
                                              %))
                               key message signature)
         :rsa (verify-with "SHA256withRSA" #(spki "RSA" %) key message signature)
         :ecdsa (verify-with "SHA256withECDSA" #(spki "EC" %) key message signature)
         false)))))
