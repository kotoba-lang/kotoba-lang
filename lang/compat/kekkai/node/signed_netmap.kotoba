(ns kekkai.node.signed-netmap
  "Verification boundary for byte-exact, Ed25519-signed netmap envelopes."
  (:require ["node:crypto" :as crypto]
            [cljs.reader :as reader]))

(defn- buffer [value encoding]
  (.from js/Buffer value encoding))

(defn- sha256 [payload]
  (-> (.createHash crypto "sha256") (.update payload) (.digest "hex")))

(defn- same-text? [a b]
  (let [left (buffer (str a) "utf8")
        right (buffer (str b) "utf8")]
    (and (= (.-length left) (.-length right))
         (.timingSafeEqual crypto left right))))

(defn verify-envelope
  "Verify envelope EDN against the separately configured authority SPKI.
   The payload is parsed only after signature and digest verification."
  [text authority-spki-b64]
  (when-not (seq authority-spki-b64)
    (throw (ex-info "netmap authority public key is required"
                    {:type :kekkai/missing-netmap-authority})))
  (let [envelope (reader/read-string text)
        payload-b64 (:netmap/payload-b64 envelope)
        signature-b64 (:netmap/signature-b64 envelope)
        signer-spki-b64 (:netmap/signer-spki-b64 envelope)
        payload (when (string? payload-b64) (buffer payload-b64 "base64"))
        signature (when (string? signature-b64)
                    (buffer signature-b64 "base64"))]
    (when-not (and payload signature (string? signer-spki-b64)
                   (string? (:netmap/sha256 envelope)))
      (throw (ex-info "signed netmap envelope is incomplete"
                      {:type :kekkai/invalid-netmap-envelope})))
    (when-not (same-text? authority-spki-b64 signer-spki-b64)
      (throw (ex-info "netmap signer is not the configured authority"
                      {:type :kekkai/untrusted-netmap-signer})))
    (when-not (same-text? (:netmap/sha256 envelope) (sha256 payload))
      (throw (ex-info "netmap payload digest mismatch"
                      {:type :kekkai/netmap-digest-mismatch})))
    (let [public-key (.createPublicKey
                      crypto
                      #js {:key (buffer authority-spki-b64 "base64")
                           :format "der" :type "spki"})]
      (when-not (.verify crypto nil payload public-key signature)
        (throw (ex-info "netmap signature verification failed"
                        {:type :kekkai/netmap-signature-invalid}))))
    (reader/read-string (.toString payload "utf8"))))
