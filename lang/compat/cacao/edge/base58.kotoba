;; EXTRACTED (2026-07-15) verbatim from cloud-itonami.edge.{base58,cbor,cacao}
;; — the 2nd consumer (cloud-manimani per-user CACAO auth, ADR-2607141753 M2)
;; appeared, triggering the extraction rule (ADR-2607141654: extract on 2nd
;; consumer, not before). cloud-itonami's own copies remain canonical-in-use
;; until it migrates here (follow-up noted in ADR-2607141753 addendum 6).
(ns cacao.edge.base58
  "base58btc (Bitcoin alphabet) decode+encode for edge did:key parsing and
  minting.

  CLJS-only (like cloud-itonami.local-app: js/ interop throughout, no JVM
  branch — this runs in the Cloudflare Workers isolate, not the JVM).
  Decodes/encodes via the standard multiply-by-58-with-carry byte algorithm
  rather than porting ed25519.core/b58-decode's BigInteger approach, since
  ClojureScript has no arbitrary-precision integer in cljs.core and the
  byte algorithm needs none — every intermediate value stays under 256*58.

  `encode` was originally a test-only helper (cloud-itonami.edge.wire-
  fixtures, built to construct signed CACAO fixtures for cacao_test.cljc)
  and is promoted here verbatim for cacao.edge.verify-mint's did:key
  minting path (ADR pending — WebAuthn passkey -> server-held Ed25519
  did:key -> CACAO bridge). cacao_test.cljc's round-trip only ever calls
  wire-fixtures' own separate hand-written copy of this algorithm, never
  this production `encode` — the two happened to be textually identical at
  the time of the port, but nothing keeps them in sync, so that round-trip
  does NOT cover this function. base58_test.cljc's own
  `round-trips-through-production-encode` is what actually exercises this
  `encode` directly (decode(encode(bytes)) = bytes, same shapes wire-
  fixtures' copy is tested against)."
  (:require [kotoba.lang.text :as str]))

(def ^:private alphabet "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")
(def ^:private char->index (into {} (map-indexed (fn [i c] [c i]) alphabet)))

(defn decode
  "base58btc string -> js/Uint8Array."
  [s]
  (let [out (array)]
    (doseq [c s]
      (let [v (get char->index c)]
        (when (nil? v)
          (throw (js/Error. (str "bad base58 char: " c))))
        (let [carry (atom v)
              n (aget out "length")]
          (loop [i (dec n)]
            (when (>= i 0)
              (let [total (+ @carry (* 58 (aget out i)))]
                (aset out i (bit-and total 0xff))
                (reset! carry (bit-shift-right total 8)))
              (recur (dec i))))
          (loop []
            (when (pos? @carry)
              (.unshift out (bit-and @carry 0xff))
              (reset! carry (bit-shift-right @carry 8))
              (recur))))))
    (let [leading (count (take-while #(= % \1) s))]
      (js/Uint8Array.from (clj->js (concat (repeat leading 0) (array-seq out)))))))

(defn encode
  "byte sequence (js/Uint8Array or any seqable of 0-255 ints) -> base58btc
  string. The exact inverse of `decode` above (verified by round-trip in
  base58_test.cljc's `round-trips-through-production-encode`)."
  [bytes]
  (let [bytes (vec (array-seq (js/Array.from bytes)))
        digits (atom [0])]
    (doseq [b bytes]
      (let [carry (atom b)]
        (dotimes [i (count @digits)]
          (let [v (+ @carry (* (nth @digits i) 256))]
            (swap! digits assoc i (mod v 58))
            (reset! carry (quot v 58))))
        (while (pos? @carry)
          (swap! digits conj (mod @carry 58))
          (reset! carry (quot @carry 58)))))
    (let [leading (count (take-while zero? bytes))
          all-zero? (every? zero? bytes)
          body (if all-zero? "" (apply str (map #(nth alphabet %) (reverse @digits))))]
      (str (apply str (repeat leading (first alphabet))) body))))
