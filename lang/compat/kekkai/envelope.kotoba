(ns kekkai.envelope
  "Ed25519-signed netmap envelopes — the publishing half of a boundary whose
  verifying half already existed.

  `kekkai.node.signed-netmap/verify-envelope` has been able to authenticate a
  netmap for some time; nothing produced one, so `kekkai-node` ran with
  `allow-unsigned-netmap?` and trusted its own filesystem. That is the gap the
  node's README named as 'the single most important remaining hole', and it was
  open on this side, not that one.

  The envelope is deliberately dumb:

      {:netmap/payload-b64     base64(UTF-8 pr-str netmap)
       :netmap/signature-b64   base64(Ed25519 over the payload BYTES)
       :netmap/signer-spki-b64 base64(DER X.509 SubjectPublicKeyInfo)
       :netmap/sha256          hex sha256 of the payload BYTES}

  ## Why the payload is carried as bytes rather than as EDN

  The signature covers a byte string, and the verifier parses only *after* it
  verifies. If the envelope carried the netmap as nested EDN, both sides would
  have to agree on a canonical re-serialization to recover the signed bytes, and
  every such agreement is a place where a reader that normalizes differently —
  key order, map printing, integer width — turns a valid netmap into a
  signature failure, or worse, lets two different netmaps share one signature.
  Base64 removes the question: there is exactly one byte string and it is the
  one that was signed.

  ## `*print-namespace-maps*` is load-bearing

  Every key in the netmap is namespace-qualified, so a printer with
  `*print-namespace-maps*` true emits `#:netmap{:version 1 …}`. That is valid
  EDN and Clojure reads it back, which is precisely why it is dangerous: the
  round-trip test on this side passes while the ClojureScript verifier on the
  other side is handed a form it may not read. `encode-payload` binds the var
  rather than assuming a default, because the default differs between a plain
  run and a REPL — so the bug would appear only when a human published by hand."
  (:require [clojure.string :as str])
  (:import [java.security KeyFactory MessageDigest Signature]
           [java.security.spec X509EncodedKeySpec]
           [java.util Base64]))

(defn- b64-encode ^String [^bytes bs]
  (.encodeToString (Base64/getEncoder) bs))

(defn- b64-decode ^bytes [^String s]
  (.decode (Base64/getDecoder) s))

(defn- sha256-hex ^String [^bytes bs]
  (->> (.digest (MessageDigest/getInstance "SHA-256") bs)
       (map #(format "%02x" %))
       (str/join)))

(defn encode-payload
  "The exact bytes that get signed: UTF-8 of `pr-str`, with namespace-map
  printing pinned off. See the namespace docstring."
  ^bytes [netmap]
  (binding [*print-namespace-maps* false
            *print-length* nil
            *print-level* nil]
    (.getBytes (pr-str netmap) "UTF-8")))

(defn seal
  "Sign `netmap` with `identity` (a `kekkai.cacao` identity map) and return the
  envelope map. `envelope-string` is what gets written to a file."
  [netmap {:keys [private-key public-key]}]
  (when-not (and private-key public-key)
    (throw (ex-info "signing a netmap needs an Ed25519 identity"
                    {:type :kekkai/missing-signing-identity})))
  (let [payload (encode-payload netmap)
        signature (let [s (doto (Signature/getInstance "Ed25519")
                            (.initSign private-key))]
                    (.update s payload)
                    (.sign s))]
    {:netmap/payload-b64 (b64-encode payload)
     :netmap/signature-b64 (b64-encode signature)
     :netmap/signer-spki-b64 (b64-encode (.getEncoded public-key))
     :netmap/sha256 (sha256-hex payload)}))

(defn envelope-string
  "The envelope as EDN text, for `:netmap-file`.

  Printed with the same binding as the payload: an envelope the node cannot
  read is not better than one it cannot verify."
  ^String [envelope]
  (binding [*print-namespace-maps* false
            *print-length* nil
            *print-level* nil]
    (pr-str envelope)))

(defn authority-spki-b64
  "The value a node configures as `:netmap-authority-spki-b64`.

  Published separately from the envelope on purpose — an envelope that carried
  the only copy of its signer's key would authenticate itself, which is not
  authentication."
  ^String [{:keys [public-key]}]
  (b64-encode (.getEncoded public-key)))

(defn verify
  "Verify an envelope map against `authority-spki-b64`, returning the netmap.

  A publisher-side mirror of `kekkai.node.signed-netmap/verify-envelope`, in
  the same order (signer, then digest, then signature, then parse). It exists so
  this repository can prove what it emits without a Node process — and its
  agreement with the node's verifier is the thing the parity fixture pins, since
  two verifiers written from one prose description is exactly the arrangement
  that drifts."
  [envelope authority-spki-b64*]
  (let [{:netmap/keys [payload-b64 signature-b64 signer-spki-b64 sha256]} envelope]
    (when-not (and (string? payload-b64) (string? signature-b64)
                   (string? signer-spki-b64) (string? sha256))
      (throw (ex-info "signed netmap envelope is incomplete"
                      {:type :kekkai/invalid-netmap-envelope})))
    (when-not (= authority-spki-b64* signer-spki-b64)
      (throw (ex-info "netmap signer is not the configured authority"
                      {:type :kekkai/untrusted-netmap-signer})))
    (let [payload (b64-decode payload-b64)]
      (when-not (= sha256 (sha256-hex payload))
        (throw (ex-info "netmap payload digest mismatch"
                        {:type :kekkai/netmap-digest-mismatch})))
      (let [pub (.generatePublic (KeyFactory/getInstance "Ed25519")
                                 (X509EncodedKeySpec. (b64-decode signer-spki-b64)))
            ok? (let [v (doto (Signature/getInstance "Ed25519") (.initVerify pub))]
                  (.update v payload)
                  (.verify v (b64-decode signature-b64)))]
        (when-not ok?
          (throw (ex-info "netmap signature verification failed"
                          {:type :kekkai/netmap-signature-invalid})))
        (read-string (String. payload "UTF-8"))))))
