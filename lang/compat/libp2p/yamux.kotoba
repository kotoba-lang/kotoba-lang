(ns libp2p.yamux
  "Yamux — stream multiplexing over one connection
  (github.com/hashicorp/yamux/blob/master/spec.md), as libp2p uses it.

  One TCP connection, many independent streams. A DHT node runs a query, an
  identify exchange and a ping to the same peer at once, and without a muxer
  each needs its own connection — which is a new TCP handshake, a new Noise
  handshake, and a new entry in every NAT table between the two peers.

  ## The header is twelve octets and every field matters

      version(1) type(1) flags(2) streamID(4) length(4)

  `length` means different things per type, and that overloading is the trap:
  for `DATA` it is a **byte count** and that many octets follow; for
  `WINDOW_UPDATE` it is a **credit delta** and nothing follows. An
  implementation that reads `length` octets after a window update consumes the
  next frame's header as payload and desynchronizes the connection
  permanently — with no error, because the bytes are structurally valid.

  ## Stream IDs encode who opened the stream

  The dialer uses **odd** IDs, the listener **even**, and 0 is reserved for
  session-level frames (ping, go-away). That is the entire collision-avoidance
  mechanism: without it both ends eventually pick the same ID for different
  streams and each thinks the other's data belongs to its own.

  ## Flow control is not optional

  Each stream starts with a 256 KiB receive window. A sender may not send more
  than the window allows, and the receiver returns credit with
  `WINDOW_UPDATE` as it consumes. A sender that ignores the window will
  eventually be dropped by a conforming peer; a receiver that never returns
  credit stalls the stream forever and looks like a hung peer.

  Pure: frames in and out as octet vectors, and a session that is a value.
  Whoever owns the socket does the reading and writing."
  (:require [clojure.string :as str]))

(def ^:const version 0)
(def ^:const header-size 12)

(def types
  {:data 0x0 :window-update 0x1 :ping 0x2 :go-away 0x3})
(def type->kw (into {} (map (fn [[k v]] [v k])) types))

(def flags
  {:syn 0x1                                   ; open a stream
   :ack 0x2                                   ; acknowledge an open
   :fin 0x4                                   ; half-close: no more data from us
   :rst 0x8})                                 ; abort the stream

(def go-away-codes
  {0 :normal 1 :protocol-error 2 :internal-error})

(def ^:const initial-window
  "256 KiB, the spec's default. A receiver that never returns credit stalls the
  stream at exactly this many octets and looks like a hung peer rather than a
  flow-control bug."
  262144)

;; ── header codec ──────────────────────────────────────────────────────────

(defn- u16 [n] [(bit-and (bit-shift-right n 8) 0xFF) (bit-and n 0xFF)])
(defn- u32 [n] [(bit-and (bit-shift-right n 24) 0xFF) (bit-and (bit-shift-right n 16) 0xFF)
                (bit-and (bit-shift-right n 8) 0xFF) (bit-and n 0xFF)])
(defn- read-u16 [bs i] (+ (* 256 (bit-and (nth bs i) 0xFF)) (bit-and (nth bs (inc i)) 0xFF)))
(defn- read-u32 [bs i]
  (reduce (fn [a k] (+ (* 256 a) (bit-and (nth bs (+ i k)) 0xFF))) 0 (range 4)))

(defn flag-bits [fs] (reduce (fn [a f] (bit-or a (get flags f 0))) 0 fs))
(defn bits->flags [n] (into #{} (keep (fn [[k v]] (when (pos? (bit-and n v)) k))) flags))

(defn header
  [{:keys [type flags stream-id length] :or {flags #{} stream-id 0 length 0}}]
  (-> [version (get types type)]
      (into (u16 (flag-bits flags)))
      (into (u32 stream-id))
      (into (u32 length))))

(defn data-frame
  "A DATA frame: header with `length` = the payload size, then the payload."
  [stream-id fs payload]
  (into (header {:type :data :flags fs :stream-id stream-id :length (count payload)})
        (vec payload)))

(defn window-update
  "A WINDOW_UPDATE frame. `length` here is a **credit delta**, not a byte
  count, and **nothing follows the header** — reading `length` octets after one
  consumes the next frame's header as payload and desynchronizes the connection
  permanently, with no error, because the bytes are structurally valid."
  [stream-id fs delta]
  (header {:type :window-update :flags fs :stream-id stream-id :length delta}))

(defn ping
  "A session-level ping. Stream 0 always — a ping on a real stream ID is a
  frame the peer will route to that stream."
  [opaque & {:keys [ack?]}]
  (header {:type :ping :flags (if ack? #{:ack} #{:syn}) :stream-id 0 :length opaque}))

(defn go-away [code]
  (header {:type :go-away :stream-id 0
           :length (or (some (fn [[k v]] (when (= v code) k)) go-away-codes) 0)}))

(defn decode
  "Take one frame off the front of a buffer.

  Returns `{:frame f :rest bs}`, `{:rest bs}` when more octets are needed, or
  `{:error …}`. Only DATA consumes a payload — see `window-update`."
  ([bs] (decode bs (* 1024 1024)))
  ([bs max-payload]
   (let [bs (vec bs)]
     (cond
       (< (count bs) header-size) {:rest bs}

       (not= version (nth bs 0))
       {:error :bad-version :version (nth bs 0)}

       (nil? (type->kw (nth bs 1)))
       {:error :unknown-type :type (nth bs 1)}

       :else
       (let [t (type->kw (nth bs 1))
             fs (bits->flags (read-u16 bs 2))
             sid (read-u32 bs 4)
             len (read-u32 bs 8)
             base {:type t :flags fs :stream-id sid :length len}]
         (if (= :data t)
           (cond
             (> len max-payload)
             {:error :payload-too-large :length len}
             (< (count bs) (+ header-size len)) {:rest bs}
             :else {:frame (assoc base :payload (subvec bs header-size (+ header-size len)))
                    :rest (subvec bs (+ header-size len))})
           ;; Everything else is header-only. `length` is a credit delta, a
           ;; ping opaque, or a go-away code — never a byte count.
           {:frame base :rest (subvec bs header-size)}))))))

(defn decode-all
  ([bs] (decode-all bs (* 1024 1024)))
  ([bs max-payload]
   (loop [buf (vec bs) out []]
     (let [{:keys [frame rest error] :as r} (decode buf max-payload)]
       (cond
         error {:frames out :rest buf :error error :detail (dissoc r :frames)}
         frame (recur rest (conj out frame))
         :else {:frames out :rest rest})))))

;; ── the session ───────────────────────────────────────────────────────────

(defn session
  "`role` is `:dialer` or `:listener` — which decides the parity of the stream
  IDs this side may open."
  [role]
  {:yamux/role role
   :yamux/next-id (if (= role :dialer) 1 2)
   :yamux/streams {}})

(defn open-stream
  "Open a stream. Dialer IDs are odd, listener IDs even, and 0 is reserved for
  session frames — that parity split *is* the collision avoidance, and without
  it both ends eventually pick the same ID for different streams and each reads
  the other's data as its own."
  [sess]
  (let [id (:yamux/next-id sess)]
    {:session (-> sess
                  (assoc :yamux/next-id (+ id 2))
                  (assoc-in [:yamux/streams id]
                            {:stream/id id :stream/state :open
                             :stream/send-window initial-window
                             :stream/recv-window initial-window}))
     :stream-id id
     :out (header {:type :data :flags #{:syn} :stream-id id :length 0})}))

(defn accept-stream
  [sess id]
  {:session (assoc-in sess [:yamux/streams id]
                      {:stream/id id :stream/state :open
                       :stream/send-window initial-window
                       :stream/recv-window initial-window})
   :out (header {:type :data :flags #{:ack} :stream-id id :length 0})})

(defn valid-peer-id?
  "May the peer legitimately have opened this stream ID? A dialer must only see
  even IDs opened by the listener, and vice versa. A peer opening a stream with
  our own parity is either broken or trying to collide with a stream we are
  about to open."
  [sess id]
  (and (pos? id)
       (if (= :dialer (:yamux/role sess)) (even? id) (odd? id))))

(defn can-send?
  "Is there enough send window for `n` octets? A sender that ignores this will
  be dropped by a conforming peer."
  [sess id n]
  (<= n (get-in sess [:yamux/streams id :stream/send-window] 0)))

(defn record-sent
  [sess id n]
  (update-in sess [:yamux/streams id :stream/send-window] - n))

(defn record-received
  "Consume receive window. Returns the session and, when the window has fallen
  below half, the `WINDOW_UPDATE` to send — returning credit lazily rather than
  per frame is what keeps a bulk transfer from being one update per packet."
  [sess id n]
  (let [win (- (get-in sess [:yamux/streams id :stream/recv-window] initial-window) n)
        sess (assoc-in sess [:yamux/streams id :stream/recv-window] win)]
    (if (< win (quot initial-window 2))
      (let [delta (- initial-window win)]
        {:session (assoc-in sess [:yamux/streams id :stream/recv-window] initial-window)
         :out (window-update id #{} delta)})
      {:session sess})))

(defn apply-window-update
  [sess id delta]
  (update-in sess [:yamux/streams id :stream/send-window] (fnil + 0) delta))

(defn close-stream
  "Half-close: FIN says *we* will send no more, and the peer may still send to
  us. Treating FIN as a full close drops data the peer was entitled to send."
  [sess id]
  {:session (assoc-in sess [:yamux/streams id :stream/state] :half-closed)
   :out (header {:type :data :flags #{:fin} :stream-id id :length 0})})

(defn reset-stream
  [sess id]
  {:session (update sess :yamux/streams dissoc id)
   :out (header {:type :data :flags #{:rst} :stream-id id :length 0})})

(defn stream-state [sess id] (get-in sess [:yamux/streams id :stream/state]))
(defn open-stream-ids [sess] (vec (sort (keys (:yamux/streams sess)))))
