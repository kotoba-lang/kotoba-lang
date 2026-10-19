(ns kotoba.compiler.module-lock
  "CID-pinned module resolution.

  `project-files` resolves `(:require [app.util :as u])` by turning the
  namespace into a PATH and looking for a file under the source roots. That is
  ambient: what a build compiles depends on what happens to be on disk, so the
  same source can compile to two different artifacts on two machines and
  neither build can say which inputs it actually used.

  Here a module is resolved through a lock instead. The lock names every module
  in the closed graph by CID; bytes are read from a content-addressed block
  directory and are rejected unless they hash to the CID that was asked for.
  There is no path search and no fallback: a `:require` for a namespace the
  lock does not pin is an error, not a reason to go looking.

  This is the Nix-shaped half of content addressing -- pinned, verified,
  reproducible INPUTS -- and it is deliberately separate from the semantic
  definition CIDs in `kotoba-lang/codebase`, which identify checked
  definitions. A source-tree CID says which bytes were compiled. A definition
  CID says what a definition MEANS. Conflating them would let a comment change
  invalidate a definition identity, or let two different sources claim one."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kotoba.compiler.bounded-edn :as bounded-edn]
            [kotoba.sema :as sema]
            [kotoba.compiler.project :as project]
            [multiformats.core :as mf])
  (:import [java.nio.file Files LinkOption Path]))

(def lock-schema :kotoba.module-lock/v1)

(defn- reject! [message data]
  (throw (ex-info message (assoc data :phase :module-lock))))

(defn- block-file ^Path [blocks-root cid]
  (when-not (re-matches #"[a-z0-9]{16,128}" (str cid))
    (reject! "module CID is not a plain base32 CIDv1" {:cid cid}))
  (.resolve ^Path blocks-root ^String cid))

(defn- read-verified-source
  "Read the module bytes for CID and prove they are the ones named.

  Verification is on the BYTES, before they are decoded as text: a store that
  serves the wrong file, a truncated read, and a tampered block are all the
  same failure here, and none of them can reach the frontend."
  [blocks-root cid]
  (let [path (block-file blocks-root cid)]
    (when-not (Files/isRegularFile path (make-array LinkOption 0))
      (reject! "locked module block is missing from the block store"
               {:cid cid :block-store (str blocks-root)}))
    (let [bytes (Files/readAllBytes path)]
      (when (> (alength bytes) project/max-linked-source-bytes)
        (reject! "locked module exceeds the source size limit"
                 {:cid cid :limit project/max-linked-source-bytes}))
      (let [actual (mf/cidv1-raw bytes)]
        (when-not (= cid actual)
          (reject! "locked module block does not hash to its CID"
                   {:expected cid :actual actual})))
      (String. ^bytes bytes "UTF-8"))))

(defn lock-cid
  "Identity of the pinned input set itself.

  A compile that records only the root module CID does not pin what that
  module was compiled AGAINST. This hashes the whole resolved graph -- root
  plus every namespace/CID pair, in canonical order -- so a receipt can bind
  one value that changes whenever any input does."
  [{:keys [root modules]}]
  (mf/cidv1-raw
   (.getBytes (pr-str {:schema lock-schema
                       :root (str root)
                       :modules (into (sorted-map)
                                      (map (fn [[k v]] [(str k) (str v)]))
                                      modules)})
              "UTF-8")))

(defn read-lock
  "Read and structurally validate a module lock file."
  [path]
  (let [lock (bounded-edn/read-file path)]
    (when-not (map? lock)
      (reject! "module lock must be a map" {:path path}))
    (when-not (= lock-schema (:schema lock))
      (reject! "unknown module lock schema"
               {:path path :schema (:schema lock) :expected lock-schema}))
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
  "Resolve the closed module graph named by LOCK-PATH out of BLOCKS-ROOT.

  Returns the same `{:sources :root}` shape `project-files/load-closed-graph`
  produces, so linking and compilation are unchanged downstream, plus the
  resolved `:modules` and the `:lock-cid` a receipt should bind.

  Only modules reachable from the root are loaded and verified. A lock that
  pins more than the root needs is not an error -- it is a lock shared by
  several roots -- but the unreachable entries do not silently become part of
  this compile."
  [lock-path blocks-path]
  (let [{:keys [root modules] :as lock} (read-lock lock-path)
        blocks-root (try
                      (.toRealPath (.toPath (io/file blocks-path))
                                   (make-array LinkOption 0))
                      (catch java.io.IOException _
                        (reject! "block store is not readable" {:path blocks-path})))]
    (when-not (Files/isDirectory blocks-root (make-array LinkOption 0))
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
  "Persist SOURCE into BLOCKS-ROOT under its own CID and return that CID.

  Used to build a lock from a working tree: the CID is derived from the bytes,
  so writing the same source twice is idempotent and two authors who wrote the
  same module produce the same block."
  [blocks-path source]
  (let [dir (io/file blocks-path)
        bytes (.getBytes ^String source "UTF-8")
        cid (mf/cidv1-raw bytes)]
    (.mkdirs dir)
    (let [target (io/file dir cid)]
      (if (.exists target)
        (when-not (= (seq bytes) (seq (Files/readAllBytes (.toPath target))))
          (reject! "existing block has different bytes for its CID" {:cid cid}))
        (Files/write (.toPath target) bytes (make-array java.nio.file.OpenOption 0))))
    cid))

(defn lock-from-source-paths
  "Derive a lock for the closed graph rooted at INPUT, writing each module into
  BLOCKS-PATH.

  This is the migration edge: a project that resolves by path today can be
  pinned once and compiled by CID from then on."
  [input source-paths blocks-path]
  (let [project-files (requiring-resolve 'kotoba.compiler.project-files/load-closed-graph)
        {:keys [sources root]} (project-files input source-paths)
        modules (into (sorted-map)
                      (map (fn [[namespace source]]
                             [namespace (write-block! blocks-path source)]))
                      sources)]
    {:schema lock-schema :root root :modules modules
     :lock-cid (lock-cid {:root root :modules modules})}))
