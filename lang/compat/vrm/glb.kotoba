(ns vrm.glb
  "GLB binary container parse/write facade. Restored from `kami-vrm/src/glb.rs`
  (kotoba-lang/kami-engine, deleted PR #82 'Remove Rust workspace from
  kami-engine') as part of the clj-wgsl migration (ADR-2607010930,
  com-junkawasaki/root).

  As of ADR-0048 §5 (`com-junkawasaki/root`), the actual GLB container framing
  (12-byte header + JSON chunk + BIN chunk, magic numbers, padding) is no
  longer implemented here — it was independently hand-rolled both here
  (read-only) and in `kotoba-lang/gltf` (write-only), a real duplicated-
  implementation risk. This namespace is now a thin facade over the single
  canonical codec, **`kotoba-lang/glb`** (`:local/root \"../glb\"`), delegating
  to its raw (bytes-in/bytes-out, no JSON encode/decode) tier so every
  existing call site in this repo (`vrm.parse`, `vrm.export`, and the tests)
  needed zero changes: `parse-glb` still returns raw JSON chunk bytes (not a
  parsed map — this repo's own `vrm.json` parses them, e.g. `vrm.parse/parse-vrm`),
  and `write-glb` still takes positional `(json bin)` byte-seq args.

  Bytes are represented as a portable byte-int sequence (`(vec bytes)`, each
  element 0-255) so this namespace stays platform-neutral; callers on the JVM
  can `byte-array` the result, cljs callers can `js/Uint8Array.` it."
  (:require [glb :as glb-codec]))

;; Re-exported from `glb` (kotoba-lang/glb) for call-site backward
;; compatibility — nothing below reimplements these anymore.
(def glb-magic glb-codec/glb-magic)
(def glb-version glb-codec/glb-version)
(def chunk-json glb-codec/chunk-type-json)
(def chunk-bin glb-codec/chunk-type-bin)

(def u32->le-bytes glb-codec/u32->le-bytes)
(def string->byte-seq glb-codec/string->byte-seq)
(def byte-seq->string glb-codec/byte-seq->string)
(def pad-len glb-codec/pad-len)

;; ---------------------------------------------------------------------------
;; Parse
;; ---------------------------------------------------------------------------

(defn parse-glb
  "Parse raw GLB bytes (byte-int vector) into `{:json [byte-ints] :bin (nilable
  [byte-ints])}`. Throws `ex-info` on malformed input, mirroring `VrmError::InvalidGlb`.

  Delegates to `glb/parse-glb-raw` (kotoba-lang/glb) — the JSON chunk is
  returned as raw UTF-8 bytes, not decoded, matching this function's original
  contract; downstream callers in this repo (`vrm.parse`) JSON-decode via
  `vrm.json` themselves."
  [data]
  (glb-codec/parse-glb-raw data))

;; ---------------------------------------------------------------------------
;; Write
;; ---------------------------------------------------------------------------

(defn write-glb
  "Write GLB bytes (byte-int vector) from JSON bytes + binary buffer (both
  byte-int vectors). Delegates to `glb/write-glb-raw` (kotoba-lang/glb) — pure
  chunk framing, no JSON encoding (the caller, e.g. `vrm.export`, already has
  serialized JSON bytes)."
  [json bin]
  (glb-codec/write-glb-raw json bin))
