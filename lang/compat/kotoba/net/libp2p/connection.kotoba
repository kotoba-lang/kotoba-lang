(ns kotoba.net.libp2p.connection
  "The thing that runs the libp2p layers in order over one socket.

  Every layer already existed here as a pure state machine with no socket:
  `libp2p.multistream`, `kotoba-lang/noise`, `libp2p.yamux`,
  `multiformats.multiaddr`. What was missing was their sequencing, and the
  sequencing is where libp2p is unforgiving:

      TCP → multistream(/noise) → Noise XX → multistream(/yamux) → Yamux
                                                                     ↓
                                       multistream(<protocol>) per stream

  Three framings stack, and each is different. Multistream frames are
  varint-length-prefixed strings ending in a newline that is *inside* the
  length. Noise handshake and transport messages are prefixed with two
  big-endian bytes. Yamux has a twelve-octet header whose `length` field means
  a byte count for DATA and a credit delta for WINDOW_UPDATE. Reading one
  layer's frame with another layer's rule desynchronizes the connection
  permanently and silently, because the bytes stay structurally valid.

  The **second multistream runs inside the encrypted channel**, which is easy to
  miss: after the Noise handshake completes, `/yamux/1.0.0` is negotiated as
  Noise transport messages, not as plaintext on the socket.

  The socket is injected as `{:read! (fn [n] octets) :write! (fn [octets])}`
  for the same reason the layers below hold no socket: a driver that opened its
  own connection could not be tested without a network, and this one is tested
  against an in-memory pair."
  (:require [clojure.string :as str]
            [kotoba.net.libp2p.handshake :as identity]
            [libp2p.multistream :as ms]
            [libp2p.yamux :as yamux]
            [noise.cipher-state :as cs]
            [noise.handshake-state :as noise]))

(def multistream-protocol "/multistream/1.0.0")
(def noise-protocol "/noise")
(def yamux-protocol "/yamux/1.0.0")

(def max-noise-message 65535)
(def max-multistream-message 1024)

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

;; ---------------------------------------------------------------------------
;; Framing

(defn- u16-be [n] [(bit-and (bit-shift-right n 8) 0xFF) (bit-and n 0xFF)])

(defn- read-u16-frame
  "Noise framing: two big-endian length octets, then that many."
  [{:keys [read!]}]
  (let [header (read! 2)
        length (+ (* 256 (nth header 0)) (nth header 1))]
    (when (zero? length) (fail! :libp2p/empty-noise-frame {}))
    (read! length)))

(defn- write-u16-frame [{:keys [write!]} octets]
  (when (> (count octets) max-noise-message)
    (fail! :libp2p/noise-frame-too-large {:size (count octets)}))
  (write! (into (u16-be (count octets)) octets)))

(defn- read-varint
  "Multistream framing is varint-prefixed, and the length must be read one
  octet at a time: reading ahead would consume the payload of the frame the
  length describes."
  [{:keys [read!]}]
  (loop [shift 0 acc 0 seen 0]
    (when (> seen 9) (fail! :libp2p/varint-too-long {}))
    (let [b (nth (read! 1) 0)
          acc (bit-or acc (bit-shift-left (bit-and b 0x7F) shift))]
      (if (zero? (bit-and b 0x80))
        acc
        (recur (+ shift 7) acc (inc seen))))))

(defn- read-multistream-message
  "One multistream frame, as the string it carries (newline stripped)."
  [port]
  (let [length (read-varint port)]
    (when (> length max-multistream-message)
      (fail! :libp2p/multistream-frame-too-large {:size length}))
    (let [octets ((:read! port) length)
          text (apply str (map char octets))]
      (if (str/ends-with? text "\n")
        (subs text 0 (dec (count text)))
        (fail! :libp2p/multistream-frame-unterminated {:text text})))))

(defn- write-multistream-message [port text]
  ((:write! port) (ms/encode text)))

(defn accept-negotiation
  "The listener half of multistream: read a proposal and answer it.

  A responder that echoed whatever it was offered would speak protocols it does
  not implement; `na` is a real answer and the dialer is required to try its
  next choice or give up."
  [port supported]
  (loop [state (ms/listener supported)]
    (let [message (read-multistream-message port)
          {:keys [listener out done failed]} (ms/listener-recv state message)]
      (when out ((:write! port) out))
      (cond
        failed (fail! :libp2p/multistream-listener-failed {:reason failed})
        done done
        :else (recur listener)))))

(defn negotiate
  "Propose PROTOCOL over PORT and require the peer to echo it.

  The echo IS the acceptance -- there is no `ok` -- so anything else is a
  failure rather than something to interpret. The header and the first proposal
  go out together: one round trip per layer, and there are three."
  [port protocol]
  (write-multistream-message port multistream-protocol)
  (write-multistream-message port protocol)
  (let [header (read-multistream-message port)]
    (when-not (= multistream-protocol header)
      (fail! :libp2p/multistream-header-mismatch {:offered multistream-protocol :got header}))
    (let [reply (read-multistream-message port)]
      (when-not (= protocol reply)
        (fail! :libp2p/protocol-not-accepted {:wanted protocol :got reply}))
      protocol)))

;; ---------------------------------------------------------------------------
;; Secure channel

(defn- secure-port
  "A port that encrypts on the way out and decrypts on the way in.

  Buffered because the layers above read by byte count while Noise delivers
  whole messages: a caller asking for 12 octets of a Yamux header must not
  consume -- and lose -- the rest of the Noise message it arrived in."
  [port send-cs recv-cs]
  (let [send (volatile! send-cs)
        recv (volatile! recv-cs)
        buffer (volatile! [])]
    {:read!
     (fn [n]
       (while (< (count @buffer) n)
         (let [frame (read-u16-frame port)
               [next plain] (cs/decrypt-with-ad @recv [] frame)]
           (vreset! recv next)
           (vswap! buffer into plain)))
       (let [taken (vec (take n @buffer))]
         (vswap! buffer #(vec (drop n %)))
         taken))
     :write!
     (fn [octets]
       ;; Chunked to the Noise message limit; a single write above this layer
       ;; is not a single frame below it.
       (doseq [chunk (partition-all (- max-noise-message 16) octets)]
         (let [[next ct] (cs/encrypt-with-ad @send [] (vec chunk))]
           (vreset! send next)
           (write-u16-frame port ct))))}))

(defn handshake!
  "Run multistream(/noise) and the XX handshake, returning an encrypted port.

  Returns `{:port :peer}` where `:peer` carries the verified identity key. The
  remote static key comes from the handshake state, never from the payload --
  a payload that named its own key would prove nothing."
  [port {:keys [suite static identity-public-key sign-fn verify-fn prologue]}]
  (negotiate port noise-protocol)
  (let [payload (identity/payload {:identity-public-key identity-public-key
                                   :noise-static-public-key (:pub static)
                                   :sign-fn sign-fn})
        hs (noise/initialize {:suite suite :pattern :XX :initiator? true
                              :s static :prologue (or prologue [])})
        ;; XX message 1: e. No payload -- nothing is encrypted yet.
        [hs msg1] (noise/write-message hs [])
        _ (write-u16-frame port msg1)
        ;; XX message 2: e, ee, s, es -- and the responder's payload.
        [hs their-payload] (noise/read-message hs (read-u16-frame port))
        ;; XX message 3: s, se -- ours.
        [hs msg3] (noise/write-message hs payload)
        _ (write-u16-frame port msg3)]
    (when-not (:done? hs)
      (fail! :libp2p/handshake-incomplete {}))
    (let [verified (identity/verify their-payload (:rs hs) verify-fn)]
      (when-not (:ok? verified)
        (fail! :libp2p/peer-identity-unverified (dissoc verified :ok?)))
      {:port (secure-port port (:send-cs hs) (:recv-cs hs))
       :peer (assoc verified :noise-static-key (:rs hs))
       :handshake-hash (:handshake-hash hs)})))

(defn accept-handshake!
  "The responder side of multistream(/noise) + XX.

  Same three messages in the same order, with the roles swapped: we read msg1,
  write msg2 carrying OUR payload, and read msg3 carrying theirs. The verified
  peer identity comes out the same way, and for the same reason -- the static
  key is the handshake's, not the payload's."
  [port {:keys [suite static identity-public-key sign-fn verify-fn prologue]}]
  (accept-negotiation port #{noise-protocol})
  (let [payload (identity/payload {:identity-public-key identity-public-key
                                   :noise-static-public-key (:pub static)
                                   :sign-fn sign-fn})
        hs (noise/initialize {:suite suite :pattern :XX :initiator? false
                              :s static :prologue (or prologue [])})
        [hs _] (noise/read-message hs (read-u16-frame port))
        [hs msg2] (noise/write-message hs payload)
        _ (write-u16-frame port msg2)
        [hs their-payload] (noise/read-message hs (read-u16-frame port))]
    (when-not (:done? hs)
      (fail! :libp2p/handshake-incomplete {}))
    (let [verified (identity/verify their-payload (:rs hs) verify-fn)]
      (when-not (:ok? verified)
        (fail! :libp2p/peer-identity-unverified (dissoc verified :ok?)))
      {:port (secure-port port (:send-cs hs) (:recv-cs hs))
       :peer (assoc verified :noise-static-key (:rs hs))
       :handshake-hash (:handshake-hash hs)})))

;; ---------------------------------------------------------------------------
;; Muxed streams

(defn- write-frame [port frame] ((:write! port) frame))

(defn open!
  "Negotiate Yamux over the encrypted port and return a session handle."
  [secure]
  (negotiate secure yamux-protocol)
  (volatile! (yamux/session :dialer)))

(defn accept!
  "The listener half: accept a muxer proposal and return a session handle.

  The session's role decides stream-id parity, and getting it wrong is not a
  handshake error -- both sides simply start choosing the same ids for
  different streams and each reads the other's data as its own."
  [secure]
  (accept-negotiation secure #{yamux-protocol})
  (volatile! (yamux/session :listener)))

(defn- header-length
  "The `length` field of a raw Yamux header, octets 8..11 big-endian."
  [header]
  (reduce (fn [acc i] (+ (* 256 acc) (bit-and (nth header i) 0xFF))) 0 (range 8 12)))

(defn read-yamux-frame
  "Read one frame: the twelve-octet header, then a DATA payload if there is one.

  `yamux/decode` returns `{:frame … :rest …}`, not the frame -- it is written
  for a buffer that may hold part of a frame or several. Reading its fields off
  the wrapper yields nil for every one of them, which is not a parse error and
  does not throw: `:type` is nil, so nothing looks like DATA, so no payload is
  ever consumed, and the next read takes that payload as a header. The
  connection desynchronizes on the first frame and the peer sees garbage. That
  was the whole of the bug this file spent a day on -- it presented as a peer
  resetting a connection it had just accepted."
  [port]
  (let [header (vec ((:read! port) 12))
        {:keys [frame error]} (yamux/decode header)]
    (cond
      error (fail! :libp2p/yamux-decode-failed {:error error})
      frame frame
      ;; No frame and no error means DATA whose payload has not arrived yet;
      ;; the header says how much to read.
      :else (let [length (header-length header)
                  payload (if (pos? length) (vec ((:read! port) length)) [])]
              (or (:frame (yamux/decode (into header payload)))
                  (fail! :libp2p/yamux-decode-failed {:length length}))))))

(defn pump!
  "Read one Yamux frame and dispatch it.

  A connection is not one stream. The moment a libp2p peer accepts us it opens
  streams of ITS own -- go-libp2p dials `/ipfs/id/1.0.0` at us immediately --
  and it pings, and it returns flow-control credit. A reader that waited only
  for frames on the stream it opened would silently drop all of that, and the
  peer would conclude we are broken: measured against a local Kubo node, its
  identify negotiation timed out after 5 s and it tore the connection down,
  which surfaced here as `Connection reset` after a handshake that had
  succeeded.

  Inbound streams are RESET rather than ignored. This side speaks no inbound
  protocol yet, and a reset says so in one frame; silence makes the peer wait
  out a timeout before concluding the same thing, and a timeout is
  indistinguishable from a hang."
  [secure session inboxes]
  (let [frame (read-yamux-frame secure)
        id (:stream-id frame)
        flags (:flags frame)]
    (cond
      (= :ping (:type frame))
      (when-not (contains? flags :ack)
        ((:write! secure) (yamux/ping (:length frame) :ack? true)))

      (= :go-away (:type frame))
      (vswap! inboxes assoc :closed true)

      ;; A stream this side did not open. Parity says so: a dialer opens odd
      ;; ids, so an even one is theirs.
      (and (contains? flags :syn) (not (contains? @inboxes id)))
      (let [reset (yamux/reset-stream @session id)]
        (vreset! session (:session reset))
        ((:write! secure) (:out reset)))

      (= :data (:type frame))
      (when (contains? @inboxes id)
        (vswap! inboxes update id into (:payload frame)))

      :else nil)
    frame))

(defn stream!
  "Open a Yamux stream and negotiate PROTOCOL on it.

  Returns a port for the stream, so a caller speaks its protocol without
  knowing anything about frames -- while every other frame on the connection is
  still answered."
  [secure session protocol]
  (let [{next-session :session stream-id :stream-id syn :out} (yamux/open-stream @session)
        _ (vreset! session next-session)
        ;; The SYN is a zero-length DATA frame; it opens the stream before any
        ;; payload rides it.
        _ (write-frame secure syn)
        inboxes (volatile! {stream-id []})
        port {:read! (fn [n]
                       (while (< (count (get @inboxes stream-id)) n)
                         (pump! secure session inboxes))
                       (let [taken (vec (take n (get @inboxes stream-id)))]
                         (vswap! inboxes update stream-id #(vec (drop n %)))
                         taken))
              :write! (fn [octets]
                        (write-frame secure (yamux/data-frame stream-id #{} (vec octets))))}]
    (negotiate port protocol)
    (assoc port :stream-id stream-id :inboxes inboxes)))
