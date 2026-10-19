(ns kotoba.compiler.atomic-output
  (:import [java.nio ByteBuffer]
           [java.nio.channels FileChannel]
           [java.nio.charset StandardCharsets]
           [java.nio.file CopyOption Files OpenOption Path Paths StandardCopyOption StandardOpenOption]
           [java.nio.file.attribute AclEntry AclEntryPermission AclEntryType
            AclFileAttributeView FileAttribute PosixFilePermissions]
           [java.util Collections EnumSet]))

(defn temp-file!
  "Create a temporary file under java.io.tmpdir, registered for deletion on
  JVM exit, and return it as a java.io.File. This is the compiler's one
  temp-file constructor: call sites spell a prefix and a suffix, not
  java.io.File statics. Backed by java.nio.file.Files/createTempFile so the
  permissions model stays nio's."
  ([prefix] (temp-file! prefix ".tmp"))
  ([prefix suffix]
   (doto (.. (Files/createTempFile (str prefix) (str suffix)
                                   (make-array FileAttribute 0))
             toFile)
     (.deleteOnExit))))

(defn- reject! [message data cause]
  (throw (ex-info message (merge {:phase :output} data) cause)))

(defn- target-path [path]
  (when-not (and (string? path) (seq path))
    (reject! "output path is required" {} nil))
  (let [target (.normalize (.toAbsolutePath (Paths/get path (make-array String 0))))
        parent (.getParent target)]
    (when-not (and parent (Files/isDirectory parent (make-array java.nio.file.LinkOption 0)))
      (reject! "output parent must be a directory" {:path path} nil))
    target))

(defn- set-owner-acl! [^Path path executable?]
  (let [view (Files/getFileAttributeView path AclFileAttributeView
                                         (make-array java.nio.file.LinkOption 0))
        lookup (.getUserPrincipalLookupService (.getFileSystem path))
        owner (.lookupPrincipalByName lookup (System/getProperty "user.name"))
        ;; Windows checks additional generic-right mappings while a file is
        ;; written and atomically renamed.  Full control for exactly one SID is
        ;; the Windows equivalent of an owner-private file: it adds no reader
        ;; or writer, and the owner could change its ACL in either model.
        permissions (EnumSet/allOf AclEntryPermission)
        entry (-> (AclEntry/newBuilder)
                  (.setType AclEntryType/ALLOW)
                  (.setPrincipal owner)
                  (.setPermissions permissions)
                  (.build))]
    (when-not view
      (reject! "Windows ACL output permissions are unsupported" {:path (str path)} nil))
    ;; Windows runners can create a file owned by the Administrators group even
    ;; while the process uses a filtered, non-elevated user token.  Restricting
    ;; that inherited owner would lock the writer out of its own temporary file.
    ;; Make the actual process user the owner before replacing the inherited ACL.
    (.setOwner view owner)
    (.setAcl view (Collections/singletonList entry))
    (let [actual (.getAcl view)]
      (when-not (and (= 1 (count actual))
                     (= owner (Files/getOwner path (make-array java.nio.file.LinkOption 0)))
                     (= owner (.principal ^AclEntry (first actual)))
                     (= AclEntryType/ALLOW (.type ^AclEntry (first actual)))
                     (= permissions (.permissions ^AclEntry (first actual))))
        (reject! "owner-only Windows ACL could not be verified" {:path (str path)} nil)))))

(defn- set-private-permissions! [^Path path]
  (try
    (Files/setPosixFilePermissions path (PosixFilePermissions/fromString "rw-------"))
    (catch UnsupportedOperationException error
      (try (set-owner-acl! path false)
           (catch clojure.lang.ExceptionInfo nested (throw nested))
           (catch Exception nested
             (reject! "private output permissions are unsupported" {:path (str path)} nested))))))

(defn- set-executable-permissions! [^Path path]
  (try
    (Files/setPosixFilePermissions path (PosixFilePermissions/fromString "rwx------"))
    (catch UnsupportedOperationException error
      (try (set-owner-acl! path true)
           (catch clojure.lang.ExceptionInfo nested (throw nested))
           (catch Exception nested
             (reject! "executable output permissions are unsupported" {:path (str path)} nested))))))

(defn write-bytes!
  ([path bytes] (write-bytes! path bytes {}))
  ([path bytes {:keys [private? executable?] :or {private? false executable? false}}]
   (when-not (instance? (Class/forName "[B") bytes)
     (reject! "output must be a byte array" {:path path} nil))
   (let [target (target-path path)
         parent (.getParent target)
         temporary (Files/createTempFile parent ".kotoba-" ".tmp"
                                         (make-array FileAttribute 0))
         operation (volatile! :permissions)]
     (try
       (cond executable? (set-executable-permissions! temporary)
             private? (set-private-permissions! temporary))
       (vreset! operation :write)
       (with-open [channel (FileChannel/open
                            temporary
                            (into-array OpenOption
                                        [StandardOpenOption/WRITE
                                         StandardOpenOption/TRUNCATE_EXISTING]))]
         (let [buffer (ByteBuffer/wrap bytes)]
           (while (.hasRemaining buffer) (.write channel buffer)))
         (.force channel true))
       (vreset! operation :move)
       (Files/move temporary target
                   (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                           StandardCopyOption/REPLACE_EXISTING]))
       (str target)
       (catch clojure.lang.ExceptionInfo error (throw error))
       (catch Exception error
         (reject! "atomic output failed" {:path path :reason @operation} error))
       (finally (Files/deleteIfExists temporary))))))

(defn write-edn!
  ([path value] (write-edn! path value {}))
  ([path value options]
   (write-bytes! path (.getBytes (pr-str value) StandardCharsets/UTF_8) options)))

(defn write-text!
  "Like write-edn! but for a value that is ALREADY a string meant to be read
  verbatim (e.g. emitted ClojureScript source) -- write-edn! would pr-str it
  into a quoted/escaped EDN string literal, corrupting source text that must
  stay directly readable by a cljs host."
  ([path value] (write-text! path value {}))
  ([path value options]
   (when-not (string? value)
     (reject! "output must be a string" {:path path} nil))
   (write-bytes! path (.getBytes ^String value StandardCharsets/UTF_8) options)))
