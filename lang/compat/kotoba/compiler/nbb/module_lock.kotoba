(ns kotoba.compiler.nbb.module-lock
  "Node port of `kotoba.compiler.module-lock`: resolve a closed module graph
  through a CID lock, without a JVM.

  The JVM twin's docstring states the design and it is unchanged here: a
  module is resolved through a lock that names every module in the closed
  graph by CID; bytes are read from a content-addressed block directory and
  are rejected unless they hash to the CID that was asked for; there is no
  path search and no fallback, so a `:require` for a namespace the lock does
  not pin is an error rather than a reason to go looking.

  Until this file existed `bin/amu` sent every `--module-lock` invocation to
  the JVM, and refused it outright under `--jvm-free`. That refusal was
  correct -- answering a lock-pinned build from `nbb.project-files` would have
  dropped the pinning silently, which is the ambience the lock exists to
  remove -- but it made reproducible and JDK-free mutually exclusive, and Q9
  forbids a JVM build dependency. The way to remove a refusal is to remove its
  reason.

  Two implementation notes, because they are the parts a reader will want to
  check rather than take on trust:

  - The CID is assembled here from `node:crypto`'s SHA-256 and
    `multiformats.base32` instead of calling `multiformats.core/cidv1-raw`.
    That namespace's ClojureScript branch hashes with `@noble/hashes`, an npm
    package Amu does not depend on and which would have to be installed on
    every runner for the compiler to start. `multiformats.base32` states in
    its own docstring that it carries no hashing or npm dependency, and the
    remaining bytes are two constants. `test/nbb/module-lock.cljs` pins the
    result against vectors produced by the JVM twin, so this shortcut is
    measured rather than assumed.

  - Errors carry `:phase :module-lock`, the same as the JVM twin, and every
    message below is byte-identical to its JVM counterpart. A caller that
    matches on the message gets one answer from both routes."
  (:require ["node:crypto" :as crypto]
            ["node:fs" :as fs]
            ["node:path" :as path]
            [multiformats.base32 :as base32]
            [kotoba.sema :as sema]
            [kotoba.compiler.nbb.cli-support :as support]
            [kotoba.compiler.nbb.io :as io]
            [kotoba.compiler.project :as project]))

(def lock-schema :kotoba.module-lock/v1)

(defn- reject! [message data]
  (throw (ex-info message (assoc data :phase :module-lock))))

;; CIDv1(raw, sha2-256) = multibase 'b' + base32(0x01 0x55 0x12 0x20 <digest>).
;; The four leading octets are the varints for CID version 1 and the raw codec
;; (0x55) followed by the sha2-256 multihash code (0x12) and its 32-byte
;; length -- all below 128, so each is a one-octet varint. Same value
;; `ipfs add --cid-version=1 --raw-leaves` produces for a single block.
(def ^:private cidv1-raw-prefix [0x01 0x55 0x12 0x20])

(defn- cidv1-raw [bytes]
  (let [digest (-> (.createHash crypto "sha256") (.update bytes) (.digest))]
    (str "b" (base32/encode (into cidv1-raw-prefix (js/Array.from digest))))))

(defn- utf8 [text] (.from js/Buffer text "utf8"))

(defn- block-file [blocks-root cid]
  (when-not (re-matches #"[a-z0-9]{16,128}" (str cid))
    (reject! "module CID is not a plain base32 CIDv1" {:cid cid}))
  ;; `path/join` on a name that passed the pattern above cannot escape the
  ;; block root: the pattern admits no separator, no dot and no colon, so
  ;; there is no `..` segment and no drive-relative form to normalize away.
  (.join path blocks-root cid))

(defn- regular-file? [p]
  (try (.isFile (.statSync fs p)) (catch :default _ false)))

(defn- read-verified-source
  "Read the module bytes for CID and prove they are the ones named.

  Verification is on the BYTES, before they are decoded as text: a store that
  serves the wrong file, a truncated read, and a tampered block are all the
  same failure here, and none of them can reach the frontend."
  [blocks-root cid]
  (let [p (block-file blocks-root cid)]
    (when-not (regular-file? p)
      (reject! "locked module block is missing from the block store"
               {:cid cid :block-store (str blocks-root)}))
    (let [bytes (.readFileSync fs p)]
      (when (> (.-length bytes) project/max-linked-source-bytes)
        (reject! "locked module exceeds the source size limit"
                 {:cid cid :limit project/max-linked-source-bytes}))
      (let [actual (cidv1-raw bytes)]
        (when-not (= cid actual)
          (reject! "locked module block does not hash to its CID"
                   {:expected cid :actual actual})))
      ;; Decoded only after the hash matched. The decoder is strict so a block
      ;; whose bytes verify but are not UTF-8 is rejected here rather than
      ;; reaching the reader as replacement characters.
      (let [decoder (js/TextDecoder. "utf-8" #js {:fatal true})]
        (try
          (.decode decoder bytes)
          (catch :default error
            (throw (ex-info "input is not valid UTF-8"
                            {:phase :decode :cid cid} error))))))))

(defn lock-cid
  "Identity of the pinned input set itself.

  A compile that records only the root module CID does not pin what that
  module was compiled AGAINST. This hashes the whole resolved graph -- root
  plus every namespace/CID pair, in canonical order -- so a receipt can bind
  one value that changes whenever any input does."
  [{:keys [root modules]}]
  (cidv1-raw
   (utf8 (pr-str {:schema lock-schema
                  :root (str root)
                  :modules (into (sorted-map)
                                 (map (fn [[k v]] [(str k) (str v)]))
                                 modules)}))))

(defn read-lock
  "Read and structurally validate a module lock file."
  [p]
  (let [lock (support/read-edn-file! p)]
    (when-not (map? lock)
      (reject! "module lock must be a map" {:path p}))
    (when-not (= lock-schema (:schema lock))
      (reject! "unknown module lock schema"
               {:path p :schema (:schema lock) :expected lock-schema}))
    (let [root (:root lock)
          modules (:modules lock)]
      (when-not (and (or (symbol? root) (string? root)) (seq (str root)))
        (reject! "module lock must name a root namespace" {:root root}))
      (when-not (and (map? modules) (seq modules))
        (reject! "module lock must pin at least one module" {}))
      (when (> (count modules) project/max-project-modules)
        (reject! "module lock exceeds the project module limit"
                 {:limit project/max-project-modules :count (count modules)}))
      (doseq [[namespace cid] modules]
        (when-not (and (or (symbol? namespace) (string? namespace))
                       (seq (str namespace)))
          (reject! "module lock namespace must be a symbol" {:namespace namespace}))
        (when-not (and (string? cid) (seq cid))
          (reject! "module lock entry must pin a CID" {:namespace namespace})))
      (let [modules (into {} (map (fn [[k v]] [(symbol (str k)) v])) modules)
            root (symbol (str root))]
        (when-not (contains? modules root)
          (reject! "module lock does not pin its own root" {:root root}))
        {:schema lock-schema :root root :modules modules}))))

(defn load-locked-graph
  "Resolve the closed module graph named by LOCK-PATH out of BLOCKS-PATH.

  Returns the same `{:sources :root}` shape `nbb.project-files/load-closed-graph`
  produces, so linking and compilation are unchanged downstream, plus the
  resolved `:modules` and the `:lock-cid` a receipt should bind.

  Only modules reachable from the root are loaded and verified. A lock that
  pins more than the root needs is not an error -- it is a lock shared by
  several roots -- but the unreachable entries do not silently become part of
  this compile."
  [lock-path blocks-path]
  (let [{:keys [root modules]} (read-lock lock-path)
        blocks-root (try
                      (.realpathSync fs blocks-path)
                      (catch :default _
                        (reject! "block store is not readable" {:path blocks-path})))]
    (when-not (try (.isDirectory (.statSync fs blocks-root))
                   (catch :default _ false))
      (reject! "block store must be a readable directory" {:path blocks-path}))
    (loop [pending [root] sources {} resolved {}]
      (if-let [namespace (first pending)]
        (if (contains? sources namespace)
          (recur (subvec pending 1) sources resolved)
          (let [cid (or (get modules namespace)
                        ;; The whole point: an unpinned dependency stops the
                        ;; build. Falling back to a path here would restore
                        ;; exactly the ambient resolution the lock removes.
                        (reject! "required module is not pinned by the lock"
                                 {:module namespace}))
                source (read-verified-source blocks-root cid)
                info (project/module-info (sema/read-forms source))
                declared (:namespace info)]
            (when-not (= namespace declared)
              (reject! "locked module declares a different namespace"
                       {:module namespace :declared declared :cid cid}))
            (recur (into (subvec pending 1) (map :namespace (:requires info)))
                   (assoc sources namespace source)
                   (assoc resolved namespace cid))))
        {:sources sources
         :root root
         :modules (into (sorted-map) resolved)
         :lock-cid (lock-cid {:root root :modules resolved})}))))

(defn write-block!
  "Persist SOURCE into BLOCKS-PATH under its own CID and return that CID.

  Used to build a lock from a working tree: the CID is derived from the bytes,
  so writing the same source twice is idempotent and two authors who wrote the
  same module produce the same block."
  [blocks-path source]
  (let [bytes (utf8 source)
        cid (cidv1-raw bytes)
        target (.join path blocks-path cid)]
    (.mkdirSync fs blocks-path #js {:recursive true})
    (if (regular-file? target)
      ;; A CID collision is not the expectation here; a block store shared
      ;; with something that writes by a different rule is. Compare rather
      ;; than overwrite, so the store can only ever hold bytes that match
      ;; their own name.
      (when-not (.equals bytes (.readFileSync fs target))
        (reject! "existing block has different bytes for its CID" {:cid cid}))
      (io/write-text! target source))
    cid))

(defn lock-from-source-paths
  "Derive a lock for the closed graph rooted at INPUT, writing each module into
  BLOCKS-PATH.

  This is the migration edge: a project that resolves by path today can be
  pinned once and compiled by CID from then on. `load-closed-graph` is passed
  in rather than required, so this namespace does not pull the path resolver
  into the dependency closure of a build that is already pinned."
  [load-closed-graph input source-paths blocks-path]
  (let [{:keys [sources root]} (load-closed-graph input source-paths)
        modules (into (sorted-map)
                      (map (fn [[namespace source]]
                             [namespace (write-block! blocks-path source)]))
                      sources)]
    {:schema lock-schema :root root :modules modules
     :lock-cid (lock-cid {:root root :modules modules})}))
