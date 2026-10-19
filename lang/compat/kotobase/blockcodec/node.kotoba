(ns kotobase.blockcodec.node
  "Compressing a whole DAG-CBOR node, for the blocks that are NOT encrypted.

  `kotobase.blockcodec.core` frames a payload that is about to be encrypted —
  the tx block, where compression rides inside an opaque byte string that was
  already there. The Merkle-LSM run blocks have no such string: they are
  `ipld/encode`d and stored as-is, and the CID is over the node itself. So
  compressing them needs the frame plus two things the raw frame does not
  provide.

  ## 1. The block must stay valid DAG-CBOR, because its CID says so

  `ipld/cid` is `cidv1-dag-cbor`. Storing a zlib stream under it would be a
  CID that lies about its own codec, and any generic tool that trusts the
  multicodec — `ipfs dag get`, go-ipld-prime — would choke on a block this
  workspace told it was DAG-CBOR.

  So the compressed bytes go INSIDE a DAG-CBOR node:

      {\"kbc\" 1 \"z\" <byte string: kotobase.blockcodec.core/frame of the
                                    inner node's encoding>}

  Two keys rather than one, and a version int rather than a bare payload,
  because `decode-node` has to tell an envelope from an ordinary node by
  looking at it, and a one-key `{\"z\" bytes}` map is something a caller could
  plausibly write on purpose.

  ## 2. Links must stay visible to a walker that does not decompress

  `ipld/links` is described by its own namespace as \"the one generic walk
  hydrate loops and GC need\". A link inside a compressed payload is invisible
  to it, and an invisible link is a child that garbage collection is entitled
  to delete while its parent still points at it.

  There is no clever fix — a compressed byte string cannot also be a
  traversable link. So the rule is a precondition instead: **only a node with
  no links is ever compressed.** `encode-node` checks, and falls back to plain
  encoding otherwise. That is why Merkle-LSM manifests and range directories
  (which are almost entirely links) keep their current bytes while the run
  blocks (which are keys and values) do not.

  `links-preserved?` states the invariant as something runnable, and the suite
  asserts it over both shapes.

  ## Determinism

  `encode-node` is a pure function of the node, like `frame` is of its bytes,
  and for the same reason: the result is the block's address. The envelope is
  used only when it is genuinely smaller than the plain encoding, so a node
  that will not compress keeps byte-for-byte the encoding it has today and
  keeps its CID."
  (:require [ipld.core :as ipld]
            [kotobase.blockcodec.core :as bc]))

(def envelope-version 1)
(def ^:private version-key "kbc")
(def ^:private payload-key "z")

(defn envelope?
  "Is this decoded node a compression envelope rather than application data?"
  [node]
  (and (map? node)
       (= 2 (count node))
       (= envelope-version (get node version-key))
       (some? (get node payload-key))))

(defn encode-node
  "DAG-CBOR bytes for `node`, compressed when that is both safe and smaller.

  Safe means link-free (see the namespace docstring). Smaller is measured
  against the plain encoding including the envelope's own overhead, so this
  never grows a block and never changes the bytes of one it cannot shrink."
  [node]
  (let [plain (ipld/encode node)]
    (if (seq (ipld/links node))
      plain
      (let [framed (bc/frame plain)]
        (if (= :legacy (bc/frame-codec framed))
          plain                                   ; frame declined; nothing to wrap
          (let [env (ipld/encode {version-key envelope-version
                                  payload-key (bc/platform-bytes framed)})]
            (if (< (bc/byte-length env) (bc/byte-length plain)) env plain)))))))

(defn decode-node
  "Bytes → node, transparently unwrapping a compression envelope.

  The identity on every block written before this namespace existed, which is
  what allows readers to be deployed before any writer starts producing
  envelopes — and that ordering is not optional here, because the Merkle-LSM
  producer and its readers are separate artifacts on separate deploy cycles."
  [bytes]
  (let [node (ipld/decode bytes)]
    (if (envelope? node)
      (ipld/decode (bc/platform-bytes (bc/unframe (get node payload-key))))
      node)))

(defn links-preserved?
  "Does the stored block expose the same links as the node it encodes?

  The property GC and hydrate depend on, as a predicate rather than a comment:
  a walker that reads the block without decompressing must see every link the
  node has."
  [node]
  (= (vec (ipld/links node))
     (vec (ipld/links (ipld/decode (encode-node node))))))

(defn measure
  "`kotobase.blockcodec.core/measure` for whole nodes — reports what this
  encoding actually costs on a real sample, including how many nodes were left
  plain because they carry links."
  [nodes]
  (let [rows (mapv (fn [n]
                     (let [plain (bc/byte-length (ipld/encode n))
                           out (bc/byte-length (encode-node n))]
                       [plain out (< out plain) (boolean (seq (ipld/links n)))]))
                   nodes)
        plain (reduce + 0 (map first rows))
        out (reduce + 0 (map second rows))]
    {:nodes (count rows)
     :plain-bytes plain
     :encoded-bytes out
     :ratio (if (pos? plain) (/ (double out) plain) 1.0)
     :compressed (count (filter #(nth % 2) rows))
     :linked-so-left-plain (count (filter #(nth % 3) rows))}))
