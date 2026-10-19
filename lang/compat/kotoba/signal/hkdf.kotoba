;; kotoba.signal.hkdf — HKDF-SHA256 (RFC 5869) for ClojureScript, via
;; js/crypto.subtle ("HMAC"/"SHA-256" — Web Crypto, available in browsers,
;; Cloudflare Workers, and Node 19+). CLJS sibling of kotoba.signal.hkdf (JVM,
;; javax.crypto.Mac) — same algorithm, same public API SHAPE, but every
;; function here returns a Promise: Web Crypto's primitives are inherently
;; async (no synchronous subtle-crypto API exists), unlike JCA's Mac. Pinned
;; against the same RFC 5869 §A.1–A.3 test vectors as the JVM version, see
;; hkdf_test.cljs.
;;
;;   (require '[kotoba.signal.hkdf :as hkdf])
;;   (hkdf/hkdf-extract salt ikm)              ; -> Promise<Uint8Array(32)> (PRK)
;;   (hkdf/hkdf-expand prk info length)        ; -> Promise<Uint8Array(length)> (OKM)
;;   (hkdf/hkdf salt ikm info length)          ; -> Promise<Uint8Array(length)> (extract-then-expand)
(ns kotoba.signal.hkdf)

(def hash-len 32) ; SHA-256 output size

(defn- concat-bytes [& arrs]
  (let [total (reduce + (map #(.-length ^js %) arrs))
        out (js/Uint8Array. total)]
    (reduce (fn [off ^js a] (.set out a off) (+ off (.-length a))) 0 arrs)
    out))

(defn hmac-sha256
  "Raw HMAC-SHA256(key, data). Per RFC 2104 / FIPS 198-1, a zero-length key is
   valid (HMAC pads it out to the block size internally), so callers may pass
   an all-zero 32-byte salt for the RFC 5869 \"no salt provided\" case."
  [^js key ^js data]
  (-> (js/crypto.subtle.importKey "raw" key #js {:name "HMAC" :hash "SHA-256"} false #js ["sign"])
      (.then (fn [k] (js/crypto.subtle.sign "HMAC" k data)))
      (.then #(js/Uint8Array. %))))

(defn hkdf-extract
  "RFC 5869 §2.2: PRK = HMAC-Hash(salt, IKM). A nil/empty salt is replaced with
   HashLen zero bytes, per spec."
  [salt ikm]
  (let [salt (if (or (nil? salt) (zero? (.-length ^js salt))) (js/Uint8Array. hash-len) salt)]
    (hmac-sha256 salt ikm)))

(defn- expand-step [prk info i n prev acc]
  (if (> i n)
    (js/Promise.resolve (apply concat-bytes acc))
    (-> (hmac-sha256 prk (concat-bytes prev info (js/Uint8Array. #js [i])))
        (.then (fn [t] (expand-step prk info (inc i) n t (conj acc t)))))))

(defn hkdf-expand
  "RFC 5869 §2.3: OKM = T(1) || T(2) || ... truncated to `length` bytes, where
   T(0) = \"\", T(i) = HMAC-Hash(PRK, T(i-1) || info || i). `length` must be
   <= 255 * HashLen (RFC 5869 constraint; SHA-256 => <= 8160 bytes)."
  [prk info length]
  (let [info (or info (js/Uint8Array. 0))
        n (js/Math.ceil (/ length hash-len))]
    (when (> n 255)
      (throw (js/Error. (str "hkdf-expand: requested length too large: " length))))
    (-> (expand-step prk info 1 n (js/Uint8Array. 0) [])
        (.then (fn [^js okm] (.slice okm 0 length))))))

(defn hkdf
  "RFC 5869 §2.1: HKDF(salt, ikm, info, length) = expand(extract(salt, ikm), info, length)."
  [salt ikm info length]
  (-> (hkdf-extract salt ikm)
      (.then (fn [prk] (hkdf-expand prk info length)))))
