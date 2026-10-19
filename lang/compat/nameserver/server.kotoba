(ns nameserver.server
  "JVM UDP/TCP authoritative DNS listener — the one non-portable namespace in
  this library. Raw socket binding has no portable equivalent across this
  stack's other runtimes (kotoba wasm's actor:host ABI has no raw-socket
  capability in its closed host-import table per ADR-2607062330; browsers
  can't bind UDP:53 either), so per CLAUDE.md's kotoba-wasm > clojurewasm >
  cljs > nbb > JVM runtime priority, JVM is the deliberate last resort here,
  not a default.

  Resolution is injected (`IResolver` — nameserver.resolver /
  nameserver.store / nameserver.custom-tld, the same seam shape as
  godaddy-dns-clj's `IDns`), so this namespace is a thin, swappable wire-up:
  decode -> resolve -> encode -> reply, with the classic 512-byte UDP
  truncation (TC bit) fallback to TCP (RFC 1035 §4.2).

  Scope: no rate limiting or connection throttling beyond the wire-layer
  compression-pointer-loop guard — a public-facing deployment should sit
  behind its own DoS protection. This socket layer does not itself wire up
  EDNS0 or zone transfer — `nameserver.edns` and `nameserver.transfer` are
  pure and a server embedding them chooses its own payload limit and transfer
  ACL. No
  DNSSEC — see nameserver.wire's docstring for the wire-format-level scope."
  (:require [nameserver.wire :as wire]
            [nameserver.resolver :as resolver])
  (:import [java.net DatagramSocket DatagramPacket InetAddress ServerSocket SocketException]
           [java.io DataInputStream DataOutputStream]))

(def ^:private udp-max
  "Classic non-EDNS0 UDP response ceiling; see nameserver.wire's docstring."
  512)

(defn- bytes->unsigned-ints [^bytes bs len]
  (vec (map #(bit-and (int %) 0xFF) (take len bs))))

(defn- ints->byte-array [ints]
  (byte-array (map unchecked-byte ints)))

(def ^:private formerr-response
  {:dns/id 0 :dns/qr :response :dns/opcode 0 :dns/aa? false
   :dns/tc? false :dns/rd? false :dns/ra? false :dns/rcode :formerr
   :dns/questions [] :dns/answers [] :dns/authority [] :dns/additional []})

(defn- build-response [resolver {:dns/keys [id questions]}]
  (let [{:dns/keys [qname qtype qclass]} (first questions)
        {:keys [status aa? answers authority additional]}
        (resolver/-resolve resolver qname qtype qclass)
        rcode (case status (:ok :nodata) :noerror :nxdomain :nxdomain :refused :refused :noerror)]
    {:dns/id id :dns/qr :response :dns/opcode 0
     :dns/aa? (boolean aa?) :dns/tc? false :dns/rd? true :dns/ra? false
     :dns/rcode rcode
     :dns/questions questions
     :dns/answers (or answers []) :dns/authority (or authority []) :dns/additional (or additional [])}))

(defn- response-for-bytes
  "Decode `in-bytes`, resolve against `resolver`, return the response EDN
  map — or a FORMERR response map if decoding failed (malformed/truncated
  input; never throws)."
  [resolver in-bytes]
  (try
    (build-response resolver (wire/decode-message in-bytes))
    (catch Exception _ formerr-response)))

(defn- udp-encode
  "Encode `response`; if it doesn't fit the classic 512-byte UDP ceiling,
  re-encode with the answer/authority/additional sections dropped and TC
  set (RFC 1035 §4.2.1) so the client retries over TCP."
  [response]
  (let [full (wire/encode-message response)]
    (if (<= (count full) udp-max)
      full
      (wire/encode-message (assoc response :dns/tc? true
                                   :dns/answers [] :dns/authority [] :dns/additional [])))))

;; ── UDP ──────────────────────────────────────────────────────────────────

(defn- udp-loop [^DatagramSocket socket resolver running?]
  (let [buf (byte-array 4096)]
    (while @running?
      (try
        (let [packet (DatagramPacket. buf (alength buf))]
          (.receive socket packet)
          (let [in-bytes (bytes->unsigned-ints buf (.getLength packet))
                response (response-for-bytes resolver in-bytes)
                out-arr (ints->byte-array (udp-encode response))
                reply (DatagramPacket. out-arr (alength out-arr) (.getAddress packet) (.getPort packet))]
            (.send socket reply)))
        (catch SocketException _ nil)   ; socket closed under us by stop-server! -> loop re-checks running?
        (catch Exception _ nil)))))     ; never let one bad packet kill the listener

;; ── TCP (RFC 1035 §4.2.2: 2-byte length prefix, no truncation needed) ────

(defn- handle-tcp-conn [^java.net.Socket conn resolver]
  (with-open [conn conn
              in (DataInputStream. (.getInputStream conn))
              out (DataOutputStream. (.getOutputStream conn))]
    (let [len (.readUnsignedShort in)
          buf (byte-array len)]
      (.readFully in buf)
      (let [response (response-for-bytes resolver (bytes->unsigned-ints buf len))
            out-arr (ints->byte-array (wire/encode-message response))]
        (.writeShort out (alength out-arr))
        (.write out out-arr)
        (.flush out)))))

(defn- tcp-loop [^ServerSocket socket resolver running?]
  (while @running?
    (try
      (let [conn (.accept socket)]
        (future (try (handle-tcp-conn conn resolver) (catch Exception _ nil))))
      (catch SocketException _ nil))))

;; ── lifecycle ─────────────────────────────────────────────────────────────

(defn start-server!
  "Boot a UDP + TCP authoritative DNS listener on daemon threads.

  `:resolver` (required) — an IResolver (nameserver.resolver /
  nameserver.store / nameserver.custom-tld).
  `:port` — UDP+TCP bind port (default 53 — typically needs root /
  CAP_NET_BIND_SERVICE; use e.g. 1053 for unprivileged local testing, or 0
  to let the OS assign a free port — read it back from the returned map).
  `:host` — bind address (default \"0.0.0.0\").
  `:tcp-port` — override the TCP port independently of `:port`.
  `:backlog` — TCP accept backlog (default 50).

  Returns a handle map for `stop-server!`, including the actual bound
  `:udp-port` / `:tcp-port`."
  [{:keys [resolver port host backlog tcp-port] :or {port 53 host "0.0.0.0" backlog 50}}]
  (let [addr (InetAddress/getByName host)
        udp-socket (DatagramSocket. (int port) addr)
        tcp-socket (ServerSocket. (int (or tcp-port port)) (int backlog) addr)
        running? (atom true)
        udp-thread (doto (Thread. ^Runnable (fn [] (udp-loop udp-socket resolver running?)))
                     (.setDaemon true) (.setName "nameserver-udp") (.start))
        tcp-thread (doto (Thread. ^Runnable (fn [] (tcp-loop tcp-socket resolver running?)))
                     (.setDaemon true) (.setName "nameserver-tcp") (.start))]
    {:udp-socket udp-socket :tcp-socket tcp-socket :running? running?
     :udp-thread udp-thread :tcp-thread tcp-thread
     :udp-port (.getLocalPort udp-socket) :tcp-port (.getLocalPort tcp-socket)}))

(defn stop-server!
  "Stop a server handle returned by `start-server!`. Idempotent-ish: safe to
  call once; closing already-closed sockets would throw, so don't call
  twice."
  [{:keys [running? udp-socket tcp-socket udp-thread tcp-thread]}]
  (reset! running? false)
  (.close ^DatagramSocket udp-socket)
  (.close ^ServerSocket tcp-socket)
  (.join ^Thread udp-thread 1000)
  (.join ^Thread tcp-thread 1000))
