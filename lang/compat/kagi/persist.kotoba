(ns kagi.persist
  "vault スナップショットの edn 永続化。byte[] は base64 にして edn 安全にする。
  保存されるのは **暗号文 + wrap 済み鍵 + 台帳メタ** のみ(平文・素の VMK は出ない)。"
  (:require [clojure.walk :as walk]
            [clojure.edn :as edn])
  (:import [java.util Base64]
           [java.nio ByteBuffer]
           [java.nio.charset StandardCharsets]
           [java.nio.file Files Paths StandardCopyOption CopyOption StandardOpenOption]
           [java.nio.file.attribute PosixFilePermissions FileAttribute]
           [java.nio.channels FileChannel]))

(defonce ^:private path-locks (atom {}))

(defn- path-lock [path]
  (let [key (str path)]
    (or (get @path-locks key)
        (get (swap! path-locks #(if (contains? % key) % (assoc % key (Object.)))) key))))

(defn- enc [x]
  (if (bytes? x) {:kagi/b64 (.encodeToString (Base64/getEncoder) ^bytes x)} x))

(defn- dec* [x]
  (if (and (map? x) (contains? x :kagi/b64))
    (.decode (Base64/getDecoder) ^String (:kagi/b64 x))
    x))

(defn ->edn ^String [data] (pr-str (walk/postwalk enc data)))
(defn <-edn [^String s] (walk/postwalk dec* (edn/read-string s)))

(defn save! [path data]
  (let [target (.toAbsolutePath (Paths/get path (make-array String 0)))
        parent (.getParent target)
        lock-path (Paths/get (str target ".lock") (make-array String 0))]
    (Files/createDirectories parent (make-array FileAttribute 0))
    (locking (path-lock target)
      (with-open [lock-channel (FileChannel/open lock-path
                                               (into-array StandardOpenOption
                                                           [StandardOpenOption/CREATE
                                                            StandardOpenOption/WRITE]))
                _lock (.lock lock-channel)]
      (let [tmp (Files/createTempFile parent ".kagi-persist-" ".tmp"
                                      (make-array FileAttribute 0))
            payload (.getBytes (->edn data) StandardCharsets/UTF_8)]
        (try
          (try
            (Files/setPosixFilePermissions tmp (PosixFilePermissions/fromString "rw-------"))
            (catch UnsupportedOperationException _))
          (with-open [out (FileChannel/open tmp
                                           (into-array StandardOpenOption
                                                       [StandardOpenOption/WRITE
                                                        StandardOpenOption/TRUNCATE_EXISTING]))]
            (let [buffer (ByteBuffer/wrap payload)]
              (while (.hasRemaining buffer) (.write out buffer)))
            (.force out true))
          (try
            (Files/move tmp target
                        (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                                StandardCopyOption/REPLACE_EXISTING]))
            (catch java.nio.file.AtomicMoveNotSupportedException _
              (Files/move tmp target
                          (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))
          ;; Best-effort directory fsync makes the rename durable on filesystems
          ;; that permit opening directories. File fsync above is mandatory.
          (try
            (with-open [dir (FileChannel/open parent
                                              (into-array StandardOpenOption
                                                          [StandardOpenOption/READ]))]
              (.force dir true))
            (catch Exception _))
          (finally
            (Files/deleteIfExists tmp)))))))
  data)

(defn load* [path]
  (let [f (java.io.File. ^String path)]
    (when (.exists f) (<-edn (slurp f)))))
