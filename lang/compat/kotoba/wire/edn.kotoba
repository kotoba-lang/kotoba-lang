;; kotoba.wire.edn — pure, portable EDN-over-length-prefixed-frame codec.
;;
;; Layered strictly on top of `kotoba.wire.framing` (length-prefix framing,
;; knows nothing about EDN) and `kotoba.bytes` (UTF-8 encode, knows nothing
;; about framing or EDN): this namespace is the middle layer that knows how
;; to turn an EDN map into wire bytes and back, but nothing about sockets —
;; see `kotoba.wire.tcp` for the actual Node socket I/O built on top of
;; this. Portable `.cljc`: no platform-specific string/crypto/socket API,
;; runs identically under JVM `clojure -M:test` and Node-hosted
;; ClojureScript (nbb).
(ns kotoba.wire.edn
  (:require [clojure.edn :as edn]
            [kotoba.bytes :as bytes]
            [kotoba.wire.framing :as framing]))

(defn utf8-decode
  "Decode a UTF-8 byte-vector back to a string. The inverse of
  `kotoba.bytes/utf8-encode` — `kotoba.bytes` itself only provides the
  encode direction, so this namespace (the first consumer that actually
  needs to decode bytes back to a string) provides the matching decoder.
  Pure/portable: handles the BMP plus surrogate-pair codepoints (>0xFFFF)
  the same way `utf8-encode` produces them, without relying on any
  platform string API, so `:clj` and `:cljs` decode byte-identical input
  to identical strings."
  [byte-vec]
  (let [n (count byte-vec)]
    (loop [i 0 out (transient [])]
      (if (>= i n)
        (apply str (persistent! out))
        (let [b0 (bit-and (nth byte-vec i) 0xff)]
          (cond
            (< b0 0x80)
            (recur (inc i) (conj! out (char b0)))

            (= 0xC0 (bit-and b0 0xE0))
            (let [b1 (bit-and (nth byte-vec (+ i 1)) 0xff)
                  cp (bit-or (bit-shift-left (bit-and b0 0x1F) 6)
                             (bit-and b1 0x3F))]
              (recur (+ i 2) (conj! out (char cp))))

            (= 0xE0 (bit-and b0 0xF0))
            (let [b1 (bit-and (nth byte-vec (+ i 1)) 0xff)
                  b2 (bit-and (nth byte-vec (+ i 2)) 0xff)
                  cp (bit-or (bit-shift-left (bit-and b0 0x0F) 12)
                              (bit-shift-left (bit-and b1 0x3F) 6)
                              (bit-and b2 0x3F))]
              (recur (+ i 3) (conj! out (char cp))))

            (= 0xF0 (bit-and b0 0xF8))
            (let [b1 (bit-and (nth byte-vec (+ i 1)) 0xff)
                  b2 (bit-and (nth byte-vec (+ i 2)) 0xff)
                  b3 (bit-and (nth byte-vec (+ i 3)) 0xff)
                  cp (bit-or (bit-shift-left (bit-and b0 0x07) 18)
                              (bit-shift-left (bit-and b1 0x3F) 12)
                              (bit-shift-left (bit-and b2 0x3F) 6)
                              (bit-and b3 0x3F))
                  cp' (- cp 0x10000)
                  hi (+ 0xD800 (bit-shift-right cp' 10))
                  lo (+ 0xDC00 (bit-and cp' 0x3FF))]
              (recur (+ i 4) (-> out (conj! (char hi)) (conj! (char lo)))))

            ;; malformed/continuation byte encountered where a lead byte
            ;; was expected: skip it rather than throwing, mirroring
            ;; kotoba.bytes/utf8-encode's own "skip malformed input" stance
            ;; for a lone high surrogate.
            :else
            (recur (inc i) out)))))))

(defn encode
  "Encode EDN map `m` to a single length-prefixed wire-frame byte-vector:
  `pr-str` -> UTF-8-encode (`kotoba.bytes/utf8-encode`) -> length-prefix
  (`kotoba.wire.framing/frame-length-prefix`)."
  [m]
  (-> (pr-str m)
      bytes/utf8-encode
      framing/frame-length-prefix))

(defn decode-frames
  "Decode a seq of already-defragmented frame PAYLOAD byte-vectors (i.e.
  each element is one `kotoba.wire.framing/defragment` `:frames` entry —
  the length prefix has already been stripped by the caller; this
  namespace never sees length prefixes) back to EDN maps: UTF-8-decode
  (`utf8-decode`, above) then `clojure.edn/read-string` each one. Returns a
  vector of decoded maps, in the same order as `byte-vecs`."
  [byte-vecs]
  (mapv (fn [payload] (edn/read-string (utf8-decode payload))) byte-vecs))
