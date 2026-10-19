;; EXTRACTED (2026-07-15) verbatim from cloud-itonami.edge.{base58,cbor,cacao}
;; — the 2nd consumer (cloud-manimani per-user CACAO auth, ADR-2607141753 M2)
;; appeared, triggering the extraction rule (ADR-2607141654: extract on 2nd
;; consumer, not before). cloud-itonami's own copies remain canonical-in-use
;; until it migrates here (follow-up noted in ADR-2607141753 addendum 6).
(ns cacao.edge.cbor
  "Definite-length CBOR (RFC 8949) decode+encode for the edge — CLJS port of
  cbor.core/decode (orgs/kotoba-lang/dag-cbor), same restricted profile:
  uint/negint/byte-string/text/array/map/bool/null, no indefinite lengths,
  no floats, no tags. CLJS-only (js/Uint8Array/DataView interop).

  `encode` covers only the text-string/array/map major types (3/4/5) — the
  only shapes a CACAO `p`/`s` payload ever needs (cacao.edge.verify-
  mint) — same restricted profile cloud-itonami.edge.wire-fixtures' test-
  only encoder used, promoted verbatim. cacao_test.cljc's fixtures only
  ever call wire-fixtures' own separate, textually independent copy of
  this algorithm, never this production `encode` — cbor_test.cljc's own
  direct round-trip tests are what actually exercise `encode-map`/
  `encode-cacao-envelope` against the production `decode` below (same
  gap and same fix as base58.cljc's ns docstring once had)."
  (:refer-clojure :exclude [decode]))

;; A mutable cursor {bytes i} where i is a plain js number in an atom is
;; simplest here — this is inherently imperative byte-cursor work, same as
;; cbor.core's ByteArrayInputStream on the JVM side.
(defn- make-cursor [bytes] {:bytes bytes :i (atom 0)})

(defn- next-byte [{:keys [bytes i]}]
  (when (>= @i (aget bytes "length"))
    (throw (js/Error. "cbor: unexpected end of input")))
  (let [b (aget bytes @i)]
    (swap! i inc)
    b))

(defn- read-arg [cursor info]
  (cond
    (< info 24) info
    (= info 24) (next-byte cursor)
    (= info 25) (bit-or (bit-shift-left (next-byte cursor) 8) (next-byte cursor))
    (= info 26) (loop [k 0 n 0]
                  (if (< k 4) (recur (inc k) (+ (* n 256) (next-byte cursor))) n))
    (= info 27) (loop [k 0 n 0]
                  (if (< k 8) (recur (inc k) (+ (* n 256) (next-byte cursor))) n))
    :else (throw (js/Error. (str "cbor: indefinite/reserved length unsupported (info=" info ")")))))

(defn- read-bytes [{:keys [bytes i]} n]
  (when (> (+ @i n) (aget bytes "length"))
    (throw (js/Error. "cbor: unexpected end of input")))
  (let [out (.slice bytes @i (+ @i n))]
    (swap! i + n)
    out))

(defn- decode-value [cursor]
  (let [ib (next-byte cursor)
        major (bit-shift-right ib 5)
        info (bit-and ib 0x1f)]
    (case major
      0 (read-arg cursor info)
      1 (- (- (read-arg cursor info)) 1)
      2 (read-bytes cursor (read-arg cursor info))
      3 (let [raw (read-bytes cursor (read-arg cursor info))]
          (.decode (js/TextDecoder. "utf-8") raw))
      4 (let [n (read-arg cursor info)
              arr (array)]
          (dotimes [_ n] (.push arr (decode-value cursor)))
          arr)
      5 (let [n (read-arg cursor info)
              obj (js-obj)]
          (dotimes [_ n]
            (let [k (decode-value cursor)
                  v (decode-value cursor)]
              (aset obj k v)))
          obj)
      7 (case info
          20 false
          21 true
          22 nil
          (throw (js/Error. (str "cbor: unsupported simple/float (info=" info ")"))))
      (throw (js/Error. (str "cbor: unsupported major type " major))))))

(defn decode
  "Decode CBOR bytes (js/Uint8Array) -> a JS value. Maps decode to plain JS
  objects and arrays decode to real JS arrays (`js-obj`/`array`/`aset`/
  `aget`, string keys — this profile never uses non-string map keys), so
  callers use aget/.-length/array-seq, matching kotobase.cacao.cljc's
  convention for decoded-CBOR access. NOT `(into [] ...)` — that produces a
  CLJS PersistentVector, which has no .-length and breaks array-seq; this
  bit us once already (siwe-message silently dropped the Resources: section
  because `(.-length resources)` on a PersistentVector isn't a valid JS
  array length, producing a message that didn't match what was signed)."
  [bytes]
  (decode-value (make-cursor bytes)))

;; ---- encode (text/array/map only — see ns docstring) ----------------------

(defn- header [major n]
  (cond
    (< n 24)      [(bit-or (bit-shift-left major 5) n)]
    (<= n 0xff)   [(bit-or (bit-shift-left major 5) 24) n]
    (<= n 0xffff) [(bit-or (bit-shift-left major 5) 25)
                   (bit-and (bit-shift-right n 8) 0xff)
                   (bit-and n 0xff)]
    :else (throw (js/Error. "cbor encode: value too large for this restricted profile"))))

(defn- utf8-bytes [s]
  ;; js/TextEncoder.encode coerces non-string input via USVString rules --
  ;; notably nil (JS null) silently becomes the 4-character string "null"
  ;; instead of raising. A CACAO p-pair minted from an incomplete `fields`
  ;; map (e.g. a missing :aud) would then encode as the literal text "null"
  ;; -- a plausible-looking but wrong value -- instead of failing loudly at
  ;; the point the required field went missing.
  (when-not (string? s)
    (throw (js/Error. (str "cbor encode: expected a string, got " (pr-str s)))))
  (vec (array-seq (js/Array.from (.encode (js/TextEncoder.) s)))))

(defn- encode-text [s]
  (into (header 3 (count (utf8-bytes s))) (utf8-bytes s)))

(defn- encode-str-array [strs]
  (into (header 4 (count strs)) (mapcat encode-text strs)))

(defn- encode-p-value [v]
  (if (sequential? v) (encode-str-array v) (encode-text v)))

(defn encode-map
  "Major type 5 (map), definite length. `pairs`: a seq of [string-key value]
  where value is a string or a vector-of-strings — the only two shapes a
  CACAO `p`/`s` payload ever needs. Returns a plain Clojure vector of byte
  ints (not a Uint8Array) — callers `into`/`concat` these before a final
  js/Uint8Array.from, same as cloud-itonami.edge.wire-fixtures did."
  [pairs]
  (into (header 5 (count pairs))
        (mapcat (fn [[k v]] (into (encode-text k) (encode-p-value v))) pairs)))

(defn encode-cacao-envelope
  "Assemble the outer `{\"p\": <p-pairs as encode-map>, \"s\": {\"s\":
  <sig-base64-string>}}` CBOR map cacao/verify expects, as a plain Clojure
  vector of byte ints. Kept as one function (rather than composing
  encode-map/encode-text calls at each mint call site) so the exact outer
  shape lives in one place, matching how `decode`'s `p`/`s` destructuring in
  cacao.cljc is itself the single source of truth for the wire shape."
  [p-pairs sig-b64]
  (into (header 5 2)
        (concat (encode-text "p") (encode-map p-pairs)
                (encode-text "s") (encode-map [["s" sig-b64]]))))
