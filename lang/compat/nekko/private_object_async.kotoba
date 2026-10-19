(ns nekko.private-object-async
  "`nekko.private-object` for hosts whose only crypto is asynchronous
  SubtleCrypto — which for ADR-0038 means the Cloudflare Email Worker, the one
  place that ever holds a message in the clear.

  Same envelope, byte for byte: `{:epoch :plaintext-cid :iv :ct}`, AES-256-GCM
  with a fresh 96-bit iv per object and `ct` = ciphertext||tag, and
  `plaintext-cid` = `\"r2sha256:\" + sha256(plaintext)`. An object sealed by the
  Worker therefore opens on the JVM and on nbb, and vice versa;
  `private_object_async_test` proves both directions rather than assuming them.

  Every fn returns a `js/Promise`, including `ciphertext-cid`, because
  SubtleCrypto's digest is async — the sync namespace can return that value
  directly and this one cannot, which is the only shape difference between the
  two.

  `plaintext-cid` earns its place here more than it does in the sync version.
  The GCM tag already proves the ciphertext was not edited, but it proves
  nothing about which plaintext the sealer *meant*: a server that re-seals a
  message it fabricated produces a perfectly valid envelope. Checking the
  recovered bytes back against a hash the sealer committed to is what makes
  substitution visible, and it is the reason `open` refuses rather than
  returning bytes that merely decrypted.

  Deliberately does not require `nekko.bytes` — same reason as
  `recipient-grant-async`: that namespace's cljs branch reaches for js/Buffer
  and node:crypto, neither of which exists in a Worker."
  (:require [nekko.recipient-grant-async :as rga]))

(defn- subtle [] (aget (aget js/globalThis "crypto") "subtle"))

(defn- sha256-hex [bytes]
  (-> (.digest (subtle) "SHA-256" (js/Uint8Array. bytes))
      (.then (fn [d] (rga/hexify d)))))

(defn- import-key [key-hex]
  (.importKey (subtle) "raw" (rga/unhex key-hex) #js {"name" "AES-GCM"} false
              #js ["encrypt" "decrypt"]))

(defn seal
  "Encrypt `bytes` under `epoch-key-hex` at `epoch`.
  -> Promise<{:epoch :plaintext-cid :iv :ct}>."
  [epoch epoch-key-hex bytes]
  (let [iv (rga/random-bytes 12)]
    (-> (js/Promise.all #js [(sha256-hex bytes) (import-key epoch-key-hex)])
        (.then (fn [pair]
                 (let [cid (aget pair 0) aes (aget pair 1)]
                   (-> (.encrypt (subtle)
                                 #js {"name" "AES-GCM" "iv" iv "tagLength" 128}
                                 aes (js/Uint8Array. bytes))
                       (.then (fn [ct]
                                {:epoch epoch
                                 :plaintext-cid (str "r2sha256:" cid)
                                 :iv (rga/hexify iv)
                                 :ct (rga/hexify ct)})))))))))

(defn open
  "Decrypt and then check the recovered bytes against `:plaintext-cid`.
  -> Promise<Uint8Array>. REJECTS on a bad tag (wrong key or edited
  ciphertext) and, separately, on a plaintext-cid mismatch — the second is what
  catches a valid envelope around a plaintext the sealer never committed to."
  [sealed epoch-key-hex]
  (-> (import-key epoch-key-hex)
      (.then (fn [aes]
               (.decrypt (subtle)
                         #js {"name" "AES-GCM" "iv" (rga/unhex (:iv sealed))
                              "tagLength" 128}
                         aes (rga/unhex (:ct sealed)))))
      (.then (fn [plain]
               (let [u (js/Uint8Array. plain)]
                 (.then (sha256-hex u)
                        (fn [cid]
                          (if (= (str "r2sha256:" cid) (:plaintext-cid sealed))
                            u
                            (throw (js/Error.
                                    "private-object: recovered bytes do not match plaintext-cid"))))))))))

(defn ciphertext-cid
  "The replication id: the hash of the ciphertext, which a store can address
  and route on while blind to the plaintext. -> Promise<string>."
  [sealed]
  (.then (sha256-hex (rga/unhex (:ct sealed)))
         (fn [h] (str "r2ct:" h))))
