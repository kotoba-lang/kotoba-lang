(ns storj.node.host.blobs
  "An `IBlobStore` that keeps everything in memory.

  The first implementation of that protocol, and deliberately the least
  useful one: it exists so `Exists` and `DeletePieces` have something to
  answer from, and so a test can watch a node lose a piece without a disk
  being involved.

  A real node writes to disk, streams rather than moving whole blobs, and
  survives a restart. None of that is here — see the README's scope section,
  where this is still listed as missing, because a store that forgets
  everything when the process ends is not storage."
  (:require [storj.node.protocols :as p]))

(defn in-memory
  "A blob store backed by an atom of `{path bytes}`.

  `contents` seeds it, which is how a test says `this node holds these
  pieces` without uploading them through an rpc that does not exist yet."
  ([] (in-memory {}))
  ([contents]
   (let [state (atom contents)]
     (reify p/IBlobStore
       (-get [_ path] (get @state path))
       (-put [_ path bytes] (swap! state assoc path (vec bytes)) nil)
       (-delete [_ path]
         (when-not (clojure.core/contains? @state path)
           ;; a delete of something absent is not success. DeletePieces counts
           ;; what it could not handle, and silently succeeding here would
           ;; report a clean sweep over pieces this node never had.
           (throw (ex-info "storj.node.host.blobs: no such blob" {:path path})))
         (swap! state dissoc path)
         nil)
       (-exists? [_ path] (clojure.core/contains? @state path))))))

(defn snapshot
  "What a store holds, for a test or a log line. Not part of `IBlobStore` —
  a real store cannot answer this cheaply and should not be asked to."
  [store]
  (when-let [s (:state (meta store))] @s))
