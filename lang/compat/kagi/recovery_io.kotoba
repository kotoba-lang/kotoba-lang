(ns kagi.recovery-io
  "Filesystem ceremony for recovery shares. Files are exclusive-create and
  owner-readable/writable where POSIX permissions are available."
  (:require [kagi.persist :as persist]
            [kagi.recovery :as recovery])
  (:import [java.nio.file Files LinkOption OpenOption Paths StandardOpenOption]
           [java.nio.file.attribute PosixFilePermissions]))

(def ^:private owner-only (PosixFilePermissions/fromString "rw-------"))
(defn- path [x] (Paths/get (str x) (make-array String 0)))

(defn write-shares!
  "Write shares without overwriting anything. Returns metadata, never share bytes."
  [directory shares]
  (let [dir (path directory)]
    (Files/createDirectories dir (make-array java.nio.file.attribute.FileAttribute 0))
    (mapv (fn [share]
            (let [p (.resolve dir (format "recovery-%02d.edn" (:recovery/index share)))
                  content (.getBytes (persist/->edn share) "UTF-8")]
              (Files/write p content
                           (into-array OpenOption [StandardOpenOption/CREATE_NEW
                                                   StandardOpenOption/WRITE]))
              (try (Files/setPosixFilePermissions p owner-only)
                   (catch UnsupportedOperationException _ nil))
              {:path (str p) :index (:recovery/index share)
               :set-id (:recovery/set-id share) :secret? true}))
          shares)))

(defn read-share [file] (persist/<-edn (Files/readString (path file))))
(defn combine-files [files] (recovery/combine (mapv read-share files)))

(defn owner-only? [file]
  (try
    (= owner-only (Files/getPosixFilePermissions (path file) (make-array LinkOption 0)))
    (catch UnsupportedOperationException _ true)))
