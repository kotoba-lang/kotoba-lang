;; kotoba.wire.framing — pure, portable length-prefixed stream framing.
;;
;; This is the generalized form of the inline framing logic
;; kotoba-lang/dtn's `kotoba.dtn.transport.tcp` used to hand-roll directly
;; on top of Node `Buffer`s (`encode-frame` / `make-frame-reader`, before
;; this extraction): a 4-byte big-endian length prefix followed by that
;; many bytes of payload. Extracted here so the exact same stream-
;; reassembly logic is:
;;   - testable under plain JVM `clojure -M:test` (not only under a
;;     Node-hosted ClojureScript runtime like nbb), because it operates on
;;     plain `kotoba.bytes` byte-vectors (`vector<int 0..255>`), never a
;;     platform `Buffer`/typed array, and
;;   - reusable by any future kotoba-lang protocol library that needs to
;;     frame messages over a byte stream (kotoba-lang/turn's relay I/O,
;;     kotoba-lang/net's gossip I/O), not just kotoba-lang/dtn.
;;
;; This namespace knows nothing about EDN, sockets, or any particular
;; protocol's message shape — see `kotoba.wire.edn` for the EDN-payload
;; layer built on top of this, and `kotoba.wire.tcp` for the actual socket
;; I/O built on top of that.
(ns kotoba.wire.framing
  (:require [kotoba.bytes :as bytes]))

(defn frame-length-prefix
  "Prepend a 4-byte big-endian length prefix (`kotoba.bytes/u32->bytes` of
  `(count payload-byte-vec)`) onto `payload-byte-vec`. Returns a single
  byte-vector: `[len-b0 len-b1 len-b2 len-b3 ...payload-bytes]`."
  [payload-byte-vec]
  (into (bytes/u32->bytes (count payload-byte-vec)) payload-byte-vec))

(defn defragment
  "Extract every complete length-prefixed frame available from
  `buffer-byte-vec` — an accumulating byte-vector of everything received so
  far on a stream, which may hold zero, one, or many complete frames, plus
  (at most) one trailing incomplete frame.

  Returns `{:frames [byte-vector ...] :remainder byte-vector}`: `:frames`
  is every complete frame's PAYLOAD (length prefix already stripped), in
  the order they appear in the stream; `:remainder` is whatever bytes are
  left over that do not yet form a complete frame (either fewer than 4
  bytes — not even a full length prefix yet — or a length prefix whose
  claimed payload hasn't fully arrived yet). Callers own accumulating
  `:remainder` together with the next chunk of bytes and calling
  `defragment` again — this function itself is stateless, a single map in,
  a single map out.

  Boundary cases (see `test/kotoba/wire/framing_test.cljc` for executable
  coverage of every one of these):
    - empty input -> `{:frames [] :remainder []}`
    - fewer than 4 bytes (not even a full length prefix) -> the whole
      input comes back as `:remainder`, `:frames` empty
    - exactly 4 bytes (a complete length prefix, zero payload bytes yet)
      -> still `:remainder` (unless the claimed length is itself 0, a
      valid zero-length payload frame, in which case it IS a complete
      frame)
    - exactly one complete frame with nothing left over -> `:remainder []`
    - a frame split across two `defragment` calls -> the first call
      returns it (whole or in part) as `:remainder`; concatenating that
      `:remainder` with the next chunk and calling `defragment` again
      yields the completed frame
    - multiple complete frames arriving in a single call -> all of them
      come back in `:frames`, in order, in one call"
  [buffer-byte-vec]
  (loop [buf buffer-byte-vec
         frames []]
    (if (< (count buf) 4)
      {:frames frames :remainder (vec buf)}
      (let [frame-len (bytes/bytes->u32 (subvec buf 0 4))
            total (+ 4 frame-len)]
        (if (< (count buf) total)
          {:frames frames :remainder (vec buf)}
          (let [payload (vec (subvec buf 4 total))
                rest-buf (subvec buf total)]
            (recur rest-buf (conj frames payload))))))))
