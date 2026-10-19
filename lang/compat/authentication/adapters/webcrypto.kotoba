(ns authentication.adapters.webcrypto
  "WebCrypto primitives for Cloudflare Workers, browsers and Node. All
  functions return Promises where the platform API is asynchronous.")

(def ^:private encoder (js/TextEncoder.))

(defn- bytes->base64url [bytes]
  (let [chunk 0x8000
        parts (loop [offset 0 out []]
                (if (>= offset (.-length bytes)) out
                  (recur (+ offset chunk)
                         (conj out (.apply js/String.fromCharCode nil
                                          (.subarray bytes offset
                                                     (min (.-length bytes) (+ offset chunk))))))))]
    ;; The `g` flags are load-bearing. JS `String.replace` with a NON-global
    ;; regex replaces only the FIRST match, so without them a token keeps every
    ;; `+` and `/` after the first -- i.e. it is not base64url at all, despite
    ;; the name and the docstring. Measured: roughly two 32-byte tokens in
    ;; three came out containing at least one. Found 2026-07-25 when an OAuth
    ;; `state` came back percent-encoded from a URL because it still held a `+`.
    (-> (js/btoa (.join (clj->js parts) ""))
        (.replace (js/RegExp. "\\+" "g") "-")
        (.replace (js/RegExp. "/" "g") "_")
        (.replace (js/RegExp. "=+$") ""))))

(defn random-token
  "Cryptographically random base64url token. Defaults to 32 bytes (256 bits)."
  ([] (random-token 32))
  ([byte-count]
   (when-not (<= 16 byte-count 128)
     (throw (js/Error. "random token byte count must be 16..128")))
   (let [bytes (js/Uint8Array. byte-count)]
     (js/crypto.getRandomValues bytes)
     (bytes->base64url bytes))))

(defn sha256-bytes [value]
  (js/crypto.subtle.digest "SHA-256" (.encode encoder value)))

(defn sha256-base64url [value]
  (-> (sha256-bytes value)
      (.then #(bytes->base64url (js/Uint8Array. %)))))

(defn pkce-pair []
  (let [verifier (random-token 48)]
    (-> (sha256-base64url verifier)
        (.then (fn [challenge]
                 #js {:verifier verifier :challenge challenge :method "S256"})))))

(defn password-digest
  "PBKDF2-SHA256. The host persists algorithm, iterations, salt and digest so
  parameters can be upgraded."
  [password salt iterations]
  (when-not (and (string? password) (<= 12 (count password) 1024))
    (throw (js/Error. "password must contain at least 12 characters")))
  (when-not (<= 210000 iterations 2000000)
    (throw (js/Error. "PBKDF2 iterations below policy")))
  (-> (js/crypto.subtle.importKey "raw" (.encode encoder password)
                                  "PBKDF2" false #js ["deriveBits"])
      (.then (fn [key]
               (js/crypto.subtle.deriveBits
                #js {:name "PBKDF2" :hash "SHA-256"
                     :salt (.encode encoder salt) :iterations iterations}
                key 256)))
      (.then #(bytes->base64url (js/Uint8Array. %)))))

(defn credential-envelope [password]
  (let [salt (random-token 24)
        iterations 310000]
    (-> (password-digest password salt iterations)
        (.then (fn [digest]
                 #js {:algorithm "PBKDF2-SHA256" :iterations iterations
                      :salt salt :digest digest})))))

(defn verify-password [password envelope]
  (if-not (= "PBKDF2-SHA256" (aget envelope "algorithm"))
    (js/Promise.resolve false)
    (-> (password-digest password (aget envelope "salt") (aget envelope "iterations"))
        (.then (fn [actual]
                 ;; Both values are fixed-length SHA-256 base64url strings. The
                 ;; XOR accumulator avoids content-dependent early return.
                 (let [expected (aget envelope "digest")]
                   (and (= (count actual) (count expected))
                        (zero? (reduce bit-or 0
                                       (map bit-xor
                                            (map #(.charCodeAt actual %) (range (count actual)))
                                            (map #(.charCodeAt expected %) (range (count expected)))))))))))))
