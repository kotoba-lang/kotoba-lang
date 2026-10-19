(ns kekkai.node.udp
  "The one place this repo touches a socket. Everything above it is pure `.cljc`,
  so this file stays boring on purpose: bind, send, receive, close, and the
  Buffer↔byte-vector conversion.

  Byte-vectors (not Buffers) cross the boundary because that is what the protocol
  cores speak on both runtimes; the copy costs a few microseconds per datagram
  and buys one implementation of the protocol instead of two."
  (:require [clojure.string :as str]
            ["node:dgram" :as dgram]
            ["node:os" :as os]))

(defn ->vec [buf] (vec (js/Uint8Array.from buf)))
(defn ->buf [bytes] (js/Buffer.from (js/Uint8Array.from (clj->js (vec bytes)))))

(defn addr->str [rinfo] (str (.-address rinfo) ":" (.-port rinfo)))

(defn parse-addr
  "\"host:port\" -> [host port]; IPv6 in brackets."
  [s]
  (if (str/starts-with? s "[")
    (let [close (str/index-of s "]")]
      [(subs s 1 close) (js/parseInt (subs s (+ close 2)))])
    (let [idx (str/last-index-of s ":")]
      [(subs s 0 idx) (js/parseInt (subs s (inc idx)))])))

(defn socket
  "Create and bind a UDP socket. `on-message` gets [byte-vector from-addr-string].
   Returns a promise of the socket."
  [{:keys [port host on-message on-error]}]
  (js/Promise.
   (fn [resolve reject]
     (let [sock (.createSocket dgram "udp4")]
       (.on sock "message" (fn [msg rinfo]
                             (when on-message
                               (on-message (->vec msg) (addr->str rinfo)))))
       (.on sock "error" (fn [err]
                           (if on-error (on-error err) (js/console.error err))
                           (reject err)))
       (.bind sock (or port 0) (or host "0.0.0.0")
              (fn [] (resolve sock)))))))

(defn send!
  "Fire and forget — UDP. Failures surface on the socket's error handler, which is
   the same place the OS reports them; there is no per-datagram delivery promise
   to await and pretending otherwise would be a lie in the type."
  [sock bytes addr]
  (let [[host port] (parse-addr addr)]
    (.send sock (->buf bytes) port host)))

(defn local-port [sock] (.-port (.address sock)))

(defn close! [sock] (.close sock))

(defn local-candidates
  "Non-loopback IPv4 addresses of this host, as `host:port` strings for the given
   local port. These are the `:local` candidates — worthless across the internet,
   decisive on the same LAN."
  [port & {:keys [include-loopback?]}]
  (let [ifaces (js->clj (.networkInterfaces os) :keywordize-keys true)]
    (into []
          (for [[_ addrs] ifaces
                a addrs
                :when (and (= "IPv4" (:family a))
                           (or include-loopback? (not (:internal a))))]
            (str (:address a) ":" port)))))
