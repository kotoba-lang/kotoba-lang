(ns deflate.core
  "Pure-cljc DEFLATE (RFC 1951), zlib (RFC 1950) and gzip (RFC 1952) — both
   directions.

   Zero-dep: no `java.util.zip`, no Node `zlib`, no external library. Everything
   is Huffman + LZ77 over a bit reader and writer, so the same code runs on the
   JVM, on ClojureScript/nbb, and anywhere else a Clojure dialect goes.

   Originally extracted from kotoba-lang/kasane (ADR-2606272100) as a
   decompressor only, backing PSD ZIP channels and PDF FlateDecode streams; also
   used by PNG IDAT, ZIP (raw deflate) and WOFF1 per-table compression.
   Compression, the gzip container, the checksums and the strict-decode paths
   were added in ADR-2607300400, which removed the last reason for anything in
   this workspace to reach for a host zlib.

   This namespace is the public surface. The implementation is split across
   `deflate.bits`, `deflate.huffman`, `deflate.tables`, `deflate.lz77`,
   `deflate.inflate`, `deflate.compress`, `deflate.zlib` and `deflate.gzip`;
   use those directly when you need the extra return values (stream end offsets,
   per-member gzip metadata, running checksums).

   All byte sequences — arguments and results — are sequences of *unsigned*
   bytes (0–255). Results are vectors."
  (:require [deflate.checksum :as checksum]
            [deflate.compress :as compress]
            [deflate.gzip :as gz]
            [deflate.inflate :as inf]
            [deflate.zlib :as zl]))

;; ---------------------------------------------------------------------------
;; Decompression
;; ---------------------------------------------------------------------------

(defn inflate-raw
  "Inflate a raw DEFLATE stream — no zlib header, no gzip header (RFC 1951).
   This is what ZIP members and WOFF tables contain.

   Options: `:max-output` (default 128 MiB, nil = unbounded), `:dictionary`."
  ([data] (inf/raw data))
  ([data opts] (inf/raw data opts)))

(defn inflate
  "Inflate a zlib stream (RFC 1950): validates the header and, by default,
   verifies the Adler-32 trailer.

   Options: `:verify-checksum` (default true), `:dictionary`, `:max-output`."
  ([data] (zl/unwrap data))
  ([data opts] (zl/unwrap data opts)))

(defn gunzip
  "Decompress a gzip file (RFC 1952), concatenating its members and verifying
   each CRC-32 and ISIZE.

   Options: `:verify-checksum` (default true), `:max-output`."
  ([data] (gz/unwrap data))
  ([data opts] (gz/unwrap data opts)))

(defn gzip-members
  "Per-member gzip metadata and payloads: filename, comment, extra field, mtime,
   os, crc32, isize."
  ([data] (gz/members data))
  ([data opts] (gz/members data opts)))

;; ---------------------------------------------------------------------------
;; Compression
;; ---------------------------------------------------------------------------

(defn deflate-raw
  "Compress into a raw DEFLATE stream (RFC 1951) — no header, no trailer.

   Options: `:level` 0–9 (default 6; 0 stores without compressing)."
  ([data] (compress/raw data))
  ([data opts] (compress/raw data opts)))

(defn deflate
  "Compress into a zlib stream (RFC 1950): header + DEFLATE + Adler-32.

   Options: `:level` 0–9 (default 6)."
  ([data] (zl/wrap data))
  ([data opts] (zl/wrap data opts)))

(defn gzip
  "Compress into a single-member gzip file (RFC 1952). MTIME defaults to 0 and
   OS to 255 so that output is deterministic and content-addressable.

   Options: `:level`, `:filename`, `:comment`, `:mtime`, `:os`."
  ([data] (gz/wrap data))
  ([data opts] (gz/wrap data opts)))

;; ---------------------------------------------------------------------------
;; Checksums (the wrappers' integrity fields, also used by ZIP)
;; ---------------------------------------------------------------------------

(defn adler32
  "Adler-32 (RFC 1950 §9) → unsigned 32-bit integer."
  [data]
  (checksum/adler32 data))

(defn crc32
  "CRC-32/ISO-HDLC (RFC 1952 §8) → unsigned 32-bit integer.
   Matches `java.util.zip.CRC32`, gzip and ZIP."
  [data]
  (checksum/crc32 data))
