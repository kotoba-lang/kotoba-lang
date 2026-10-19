(ns nfs.tcp
  "A TCP listener that carries the NFS and MOUNT programs.

  `:clj` only, and that is a boundary rather than a gap — the same one
  `multiformats.core/cid-of-file` draws. Everything that decides anything
  is portable `.cljc`; this namespace owns a socket, a thread per
  connection, and nothing else. A different host can replace it without
  touching a byte of protocol.

  ## One listener, both programs

  `mount_nfs` takes `port=` and `mountport=` because MOUNT was historically
  a separate daemon. Pointing both at the same port is legal and is one
  fewer thing to configure; ONC RPC already multiplexes by program number.

  ## No portmapper

  Program 100000 is how a client would *discover* the ports. A client told
  the ports explicitly does not ask, so this does not implement it — and
  binding 111 needs root, which is the thing this whole design exists to
  avoid."
  (:require [oncrpc.core :as rpc]
            [oncrpc.record :as record]
            [nfs.mount :as mount]
            [nfs.v3 :as nfs])
  (:import [java.io InputStream OutputStream ByteArrayOutputStream]
           [java.net ServerSocket Socket InetAddress]))

(defn- read-available!
  "Append whatever has arrived to `buf`, returning false at end of stream."
  [^InputStream in ^ByteArrayOutputStream buf ^bytes chunk]
  (let [n (.read in chunk)]
    (if (neg? n)
      false
      (do (.write buf chunk 0 n) true))))

(defn- serve-connection!
  [^Socket socket programs {:keys [max-message] :or {max-message (* 4 1024 1024)}}]
  (with-open [socket socket
              in (.getInputStream socket)
              out (.getOutputStream socket)]
    (let [chunk (byte-array 65536)]
      (loop [buf (ByteArrayOutputStream.)]
        (let [bs (.toByteArray buf)]
          (if-let [{:keys [message end]} (record/parse bs {:max-message max-message})]
            (do
              (when-let [reply (rpc/dispatch programs message)]
                (let [framed (record/frame reply)]
                  (.write ^OutputStream out ^bytes framed)
                  (.flush ^OutputStream out)))
              ;; Keep the tail: a client pipelines, and the bytes after this
              ;; message are the next call, not noise to discard.
              (let [rest (ByteArrayOutputStream.)]
                (.write rest bs (int end) (int (- (alength bs) end)))
                (recur rest)))
            (if (read-available! in buf chunk)
              (recur buf)
              nil)))))))

(defn- peer-of [^Socket socket]
  {:remote-address (.getHostAddress (.getInetAddress socket))
   :remote-port (.getPort socket)
   :local-port (.getLocalPort socket)})

(defn start!
  "Serve a filesystem at `dir` until the returned map's `:stop!` is called.

  Returns `{:port n :dir d :stop! f}`. Port 0 asks the OS for a free one and
  the answer is in `:port` — what a test wants and what a desktop app wants
  too, since a fixed port is a collision waiting for the second install.

  ## Who is allowed, and as whom

  NFSv3 has no authentication. `AUTH_SYS` carries a uid and a gid that the
  *client* chose, which makes it a hint and never a boundary — RFC 1813 is
  candid about this and every deployment has to answer it somewhere else.

  Two optional seams answer it here, and neither knows what supplies them:

  - `:authorize` — `(fn [peer] principal | nil)`, called once per accepted
    connection. `nil` closes it before a single byte is read. `peer` is
    `{:remote-address :remote-port :local-port}`.
  - `:filesystem-for` — `(fn [principal] fs)`, so each authorized principal
    is served a filesystem of its own. That is the natural shape when every
    user has their own tree, and it means the identity decision happens
    once at accept rather than being re-derived per call.

  Give `:fs` for a single shared filesystem or `:filesystem-for` for one per
  principal. With neither seam supplied the server is open to anything that
  can reach the socket — which is why the default bind is loopback, and why
  that default is stated rather than assumed.

  A `kekkai` netmap is the intended supplier: `kekkai.acl/edge-allowed?` is
  pure, deny-by-default and port-granular, and returns the allowed ports for
  one node reaching another. This namespace deliberately does not depend on
  it; whoever depends on both is the application."
  [{:keys [fs filesystem-for authorize dir port bind boot-id backlog]
    :or {dir "/" port 0 bind "127.0.0.1" backlog 32}}]
  (when-not (or fs filesystem-for)
    (throw (ex-info "nfs.tcp/start! needs :fs or :filesystem-for" {})))
  (let [boot-id (or boot-id (System/currentTimeMillis))
        programs-for (fn [fs]
                       (merge (nfs/program-map fs {:boot-id boot-id})
                              (mount/program-map
                               [{:dir dir :handle (nfs/-root fs)}])))
        shared (when fs (programs-for fs))
        server (ServerSocket. port backlog (InetAddress/getByName bind))
        running (atom true)
        handle (fn [^Socket s]
                 (let [principal (if authorize (authorize (peer-of s)) ::open)]
                   (if (nil? principal)
                     ;; Refused before a byte is read. Closing rather than
                     ;; replying is right here and only here: there is no RPC
                     ;; call yet to answer, so there is nothing to answer.
                     (try (.close s) (catch Exception _ nil))
                     (serve-connection!
                      s
                      (or shared (programs-for (filesystem-for principal)))
                      {}))))
        accept (fn []
                 (while @running
                   (try
                     (let [s (.accept server)]
                       (doto (Thread. #(try (handle s)
                                            (catch Exception _ nil)))
                         (.setDaemon true)
                         (.start)))
                     (catch Exception _ nil))))
        thread (doto (Thread. accept) (.setDaemon true) (.start))]
    {:port (.getLocalPort server)
     :dir dir
     :stop! (fn []
              (reset! running false)
              (try (.close server) (catch Exception _ nil))
              (.interrupt ^Thread thread)
              nil)}))
