(ns kekkai.node.stream
  "A reliable, ordered byte stream over the kekkai peer session — the layer that
  was missing between `kekkai.node.peer` and anything that speaks TCP.

  ## Why this has to exist

  `peer` carries authenticated datagrams and nothing more: `peer.cljc` has a
  retry for the *handshake* and no sequence numbers, no acknowledgement and no
  retransmission for data. That is the right shape for a packet overlay — a
  WireGuard-equivalent hands datagrams to an IP stack and lets the inner TCP
  provide reliability. But `kekkai-node` has no L3/TUN plane (its README is
  explicit: `:node/overlay-ip` names peers for MagicDNS, it does not route
  packets), and the macOS full-tunnel path is a signed Network Extension.

  So carrying `ssh` over this overlay means forwarding a TCP stream in
  userspace, and there is no inner TCP to borrow reliability from. Everything
  TCP does for a forwarder — ordering, loss recovery, flow control, clean
  half-close — has to be done here or not at all. `kekkai.node.application`
  does not do it: it chunks a message into frames and reassembles them, and a
  lost chunk simply means the message never completes.

  This namespace is the state machine only. It touches no socket, decides
  nothing about authorisation (that is `netmap/permitted?`, applied by the edge
  before a stream is ever opened), and returns frames for the caller to send —
  the same shape `peer` and `relay` use.

  ## The shape

  A **stream** is one bidirectional byte channel between two peers. Many
  streams multiplex over one peer session, keyed by a 32-bit id chosen by the
  initiator. Sequence numbers are **byte offsets**, so a cumulative ack means
  'every byte below this arrived' regardless of how the sender happened to
  segment it.

      OPEN(id, port) ──▶            the initiator asks for a service
                 ◀── OPEN-OK        the responder accepted and connected
      DATA(seq) ◀────▶ DATA(seq)    both directions, independently sequenced
      ACK(ack, window) ◀──▶         cumulative, with the receiver's free space
      FIN(seq) ──▶                  'I have sent my last byte' — one direction
                 ◀── FIN(seq)       the other direction closes separately
      RST(reason) ──▶               give up, and say why

  ## Choices worth stating

  **Out-of-order segments are buffered, not dropped.** Go-back-N would be
  simpler, and over a relayed path where a single datagram is lost it would
  discard everything already in flight behind it — an interactive session would
  stall for a full RTO on every loss. The receive buffer is bounded
  (`max-reorder-bytes`), and anything past the bound is dropped rather than
  growing without limit.

  **Retransmission is timeout-driven with a fast path on duplicate acks.**
  Three acks for the same offset mean a later segment arrived and an earlier one
  did not, which is information available immediately rather than an RTO later.
  Interactive traffic is exactly where that matters: `ssh` sends a keystroke and
  waits.

  **The retransmit timer is not adaptive.** `disco` states its fixed burst
  schedule is fixed *because both sides must agree*; that reasoning does not
  apply here (only the sender consults its own timer), but an RTT estimator is
  a second control loop to get wrong, and the honest measurement in this repo's
  README is 6–9 ms per datagram of marshalling overhead — the timer is not what
  limits this transport. A fixed base with exponential backoff, and a hard
  ceiling after which the stream resets rather than retrying forever.

  **Offsets do not wrap.** At 2^32 bytes a stream resets with
  `:stream-exhausted` instead of wrapping to zero. Wrapping silently would
  corrupt the delivered byte order, and there is no bound on how long that
  corruption stays invisible. A caller that needs more opens another stream.
  This is ~4 GB, and this transport is documented as unsuitable for bulk
  transfer, so it should never be reached in the traffic it is for."
  (:require [clojure.string :as str]
            [kotoba.bytes :as b]))

;; ── wire format ─────────────────────────────────────────────────────────────
;;
;; A 16-byte fixed header, then payload. Binary rather than the JSON+base64 that
;; `kekkai.node.application` uses: base64 adds a third again to every byte, and
;; this carries the bulk of what a forwarded connection sends. The header is
;; fixed-width so decoding cannot depend on parsing a length that is itself
;; being decoded.

(def ^:const protocol-version 1)
(def ^:const header-bytes 16)

(def kinds
  "kind byte ↔ keyword. Closed set: an unknown kind is a protocol error, never
   a frame to guess at."
  {1 :open 2 :open-ok 3 :data 4 :ack 5 :fin 6 :rst})

(def kind-codes (into {} (map (fn [[k v]] [v k])) kinds))

(def ^:const max-offset
  "2^32-1. See the namespace docstring: offsets refuse to wrap."
  4294967295)

(def ^:const max-segment-bytes
  "Payload per DATA frame.

  `kekkai.node.application` chunks at 700 bytes and rides the same Noise frame,
  so this stays at the same budget rather than discovering a different MTU the
  hard way on the first path that fragments."
  700)

(def ^:const max-reorder-bytes
  "How far ahead of the contiguous prefix the receiver will buffer.

  A bound, not a tuning knob: without one, a peer that never sends the missing
  segment can make the receiver hold everything after it forever."
  (* 64 1024))

(def ^:const default-window-bytes
  "What a receiver advertises when its buffer is empty."
  (* 32 1024))

(def ^:const rto-base-ms 250)
(def ^:const rto-max-ms 4000)
(def ^:const rto-give-up-ms 30000)
(def ^:const dup-acks-before-fast-retransmit 3)

(defn- u32-put [out off v]
  (-> out
      (assoc! off (bit-and (bit-shift-right v 24) 0xff))
      (assoc! (+ off 1) (bit-and (bit-shift-right v 16) 0xff))
      (assoc! (+ off 2) (bit-and (bit-shift-right v 8) 0xff))
      (assoc! (+ off 3) (bit-and v 0xff))))

(defn- u32-get [bs off]
  (+ (* (nth bs off) 16777216)
     (* (nth bs (+ off 1)) 65536)
     (* (nth bs (+ off 2)) 256)
     (nth bs (+ off 3))))

(defn encode
  "-> a byte vector. `payload` is a byte vector (possibly empty)."
  [{:keys [kind stream-id seq ack window payload]}]
  (let [payload (vec (or payload []))
        code (or (kind-codes kind)
                 (throw (ex-info "unknown stream frame kind" {:kind kind})))
        head (-> (transient (vec (repeat header-bytes 0)))
                 (assoc! 0 protocol-version)
                 (assoc! 1 code)
                 (u32-put 2 (or stream-id 0))
                 (u32-put 6 (or seq 0))
                 (u32-put 10 (or ack 0))
                 (assoc! 14 (bit-and (bit-shift-right (or window 0) 8) 0xff))
                 (assoc! 15 (bit-and (or window 0) 0xff))
                 persistent!)]
    (into head payload)))

(defn decode
  "-> a frame map, or nil.

  nil for anything this cannot fully account for — a short buffer, the wrong
  version, an unknown kind. A stream frame that cannot be decoded is not a
  stream frame; guessing is how a decoder becomes an attack surface."
  [bs]
  (let [bs (vec bs)]
    (when (and (>= (count bs) header-bytes)
               (= protocol-version (nth bs 0))
               (contains? kinds (nth bs 1)))
      {:kind (get kinds (nth bs 1))
       :stream-id (u32-get bs 2)
       :seq (u32-get bs 6)
       :ack (u32-get bs 10)
       :window (+ (* (nth bs 14) 256) (nth bs 15))
       :payload (subvec bs header-bytes)})))

;; ── state ───────────────────────────────────────────────────────────────────

(defn- text-payload
  "UTF-8 bytes, through `kotoba.bytes` rather than a per-platform accessor.

  This file has to run on the JVM for `clojure -M:test` and in nbb for the
  agent, and the two disagree about what `(int \\a)` means once a string has
  been `seq`ed — `b/utf8-encode` is the accessor this repository already uses
  to get byte-identical output from both."
  [s]
  (vec (b/utf8-encode (str s))))

(defn- payload-text [payload]
  (apply str (map char payload)))

(defn- open-payload
  "OPEN carries the requested service as `\"<capability> <port>\"`.

  Text rather than two more header fields: it is sent once per stream and never
  on the hot path, and a reader can see what a stream asked for in a packet
  capture without a decoder."
  [capability port]
  (text-payload (str (name capability) " " port)))

(defn- parse-open-payload [payload]
  (let [text (payload-text payload)
        [cap port] (str/split (str/trim text) #"\s+")]
    (when (and cap port (re-matches #"\d{1,5}" port))
      {:capability (keyword cap) :port (parse-long port)})))

(defn initiator
  "A stream this side is opening. Returns `{:state … :frames [frame]}`.

  `stream-id` is the caller's: the edge owns id allocation because it is the
  thing that knows which ids are already in use on this peer session."
  [{:keys [stream-id capability port now-ms]}]
  {:state {:role :initiator
           :stream-id stream-id
           :phase :opening
           :capability capability
           :port port
           :send-next 0
           :send-una 0
           :unacked []              ; [{:seq :payload :sent-ms :retries}]
           :pending []              ; bytes accepted from the app, not yet segmented
           :peer-window default-window-bytes
           :recv-next 0
           :reorder {}              ; seq -> payload
           :delivered []
           :sent-fin? false
           :recv-fin nil
           :dup-acks 0
           :opened-ms now-ms
           ;; The OPEN is retransmitted like any other unacknowledged thing —
           ;; see `tick`. Without this a single lost OPEN leaves the initiator
           ;; waiting forever for an OPEN-OK that was never provoked, which
           ;; presents to a user as a connection that hangs instead of failing.
           :open-frame {:kind :open :stream-id stream-id :seq 0 :ack 0
                        :window default-window-bytes
                        :payload (open-payload capability port)}
           :open-sent-ms now-ms
           :rto-ms rto-base-ms}
   :frames [{:kind :open :stream-id stream-id :seq 0 :ack 0
             :window default-window-bytes
             :payload (open-payload capability port)}]})

(defn responder
  "Accept an inbound OPEN. Returns `{:state … :frames […] :request {…}}`, or
  `{:frames [rst] :refused reason}` when the OPEN itself is unusable.

  Authorisation is NOT decided here — the edge asks `netmap/permitted?` with
  the `:request` this returns, and calls `refuse` if the answer is no. Keeping
  the policy question outside the state machine is the same separation
  `kekkai.node.netmap` already draws: this code cannot accidentally become a
  second, weaker admission rule."
  [{:keys [frame now-ms]}]
  (let [{:keys [stream-id payload]} frame
        request (parse-open-payload payload)]
    (if-not request
      {:frames [{:kind :rst :stream-id stream-id :seq 0 :ack 0 :window 0
                 :payload (text-payload "malformed-open")}]
       :refused :malformed-open}
      {:request request
       :state {:role :responder
               :stream-id stream-id
               :phase :open
               :capability (:capability request)
               :port (:port request)
               :send-next 0
               :send-una 0
               :unacked []
               :pending []
               :peer-window (:window frame)
               :recv-next 0
               :reorder {}
               :delivered []
               :sent-fin? false
               :recv-fin nil
               :dup-acks 0
               :opened-ms now-ms
               ;; Kept so a retransmitted OPEN can be answered with the same
               ;; OPEN-OK. A duplicate OPEN means the initiator did not see the
               ;; first answer, not that it wants a second stream.
               :open-ok-frame {:kind :open-ok :stream-id stream-id :seq 0 :ack 0
                               :window default-window-bytes :payload []}
               :rto-ms rto-base-ms}
       :frames [{:kind :open-ok :stream-id stream-id :seq 0 :ack 0
                 :window default-window-bytes :payload []}]})))

(defn refuse
  "The frame that answers an OPEN the edge will not serve. `reason` is a
   keyword and travels on the wire, because 'the connection closed' is not an
   operable message — `kekkai.node.netmap/denials` exists for the same reason."
  [stream-id reason]
  {:kind :rst :stream-id stream-id :seq 0 :ack 0 :window 0
   :payload (text-payload (name reason))})

(defn- free-window [st]
  (max 0 (- max-reorder-bytes
            (reduce + 0 (map count (vals (:reorder st))))
            (count (:delivered st)))))

(defn- ack-frame [st]
  {:kind :ack :stream-id (:stream-id st) :seq 0 :ack (:recv-next st)
   :window (min 65535 (free-window st)) :payload []})

(defn- segment!
  "Turn as much of `:pending` into DATA frames as the peer's window allows.

  Nothing is sent while `:opening`: the responder has no stream yet, so a DATA
  frame that overtook its own OPEN would arrive for an id that does not exist
  and be answered with a reset."
  [st now-ms]
  (loop [st st frames []]
    (let [in-flight (- (:send-next st) (:send-una st))
          room (- (:peer-window st) in-flight)
          pending (:pending st)]
      (if (or (empty? pending) (<= room 0) (:sent-fin? st)
              (= :opening (:phase st)))
        [st frames]
        (let [n (min (count pending) max-segment-bytes room)
              payload (vec (take n pending))
              seq* (:send-next st)]
          (if (> (+ seq* n) max-offset)
            ;; Refuse rather than wrap. See the namespace docstring.
            [(assoc st :phase :reset :reset-reason :stream-exhausted)
             (conj frames (refuse (:stream-id st) :stream-exhausted))]
            (recur (-> st
                       (assoc :pending (vec (drop n pending)))
                       (assoc :send-next (+ seq* n))
                       (update :unacked conj {:seq seq* :payload payload
                                              :sent-ms now-ms :retries 0}))
                   (conj frames {:kind :data :stream-id (:stream-id st)
                                 :seq seq* :ack (:recv-next st)
                                 :window (min 65535 (free-window st))
                                 :payload payload}))))))))

(defn send-bytes
  "Accept application bytes. -> `{:state … :frames […]}`.

  Bytes are queued and segmented against the peer's advertised window, so a
  caller can hand over more than the window and the excess simply waits — which
  is what a TCP forwarder needs, since it is reading from a socket that does
  not care about this transport's window."
  [st bytes now-ms]
  (if (#{:reset :closed} (:phase st))
    {:state st :frames []}
    (let [st (update st :pending into (vec bytes))
          [st frames] (segment! st now-ms)]
      {:state st :frames frames})))

(defn- deliver-contiguous
  "Move reordered segments into `:delivered` while they are contiguous, and
  consume the peer's FIN when the prefix reaches it.

  The FIN occupies one sequence number and carries no byte. That is what makes
  it acknowledgeable: a cumulative ack past `:fin-at` is proof the peer's
  close was seen, so a lost FIN is recovered by the same retransmission every
  other segment gets. A zero-width FIN would be indistinguishable from an ack
  of the last data byte, and a lost one would leave the sender waiting for a
  confirmation that could never arrive."
  [st]
  (loop [st st]
    (cond
      (get (:reorder st) (:recv-next st))
      (let [payload (get (:reorder st) (:recv-next st))]
        (recur (-> st
                   (update :reorder dissoc (:recv-next st))
                   (update :recv-next + (count payload))
                   (update :delivered into payload))))

      (and (:fin-at st) (= (:recv-next st) (:fin-at st)))
      (-> st
          (update :recv-next inc)
          (assoc :recv-fin (:fin-at st)))

      :else st)))

(defn- accept-data [st {:keys [seq payload]}]
  (let [end (+ seq (count payload))]
    (cond
      ;; Entirely below the contiguous prefix: a retransmission of something
      ;; already delivered. Not an error — ack it again so the sender advances.
      (<= end (:recv-next st)) st

      ;; Past what the buffer can hold. Dropping is correct and the sender will
      ;; retransmit; buffering it would let a peer that withholds one segment
      ;; pin memory indefinitely.
      (> end (+ (:recv-next st) max-reorder-bytes)) st

      ;; Partially below the prefix (an overlapping retransmit): keep the new
      ;; part only, so `:recv-next` never moves backwards.
      (< seq (:recv-next st))
      (let [skip (- (:recv-next st) seq)]
        (-> st
            (assoc-in [:reorder (:recv-next st)] (vec (drop skip payload)))
            deliver-contiguous))

      :else
      (-> st
          (assoc-in [:reorder seq] (vec payload))
          deliver-contiguous))))

(defn- segment-end
  "The offset one past the last sequence number a segment occupies.

  A FIN carries no byte and still occupies one number — that is what makes it
  acknowledgeable. Getting this wrong is not a rounding error: retiring the FIN
  from the retransmit queue at `ack == fin-seq` means the ack for the *last data
  byte* retires it, so a FIN that was lost in flight is never sent again and the
  peer never learns the stream ended. Found by running the same test suite on
  the JVM, whose loss pattern happened to drop a FIN that the ClojureScript run
  had delivered first time."
  [{:keys [seq payload fin?]}]
  (+ seq (if fin? 1 (count payload))))

(defn- apply-ack [st ack window]
  (let [st (assoc st :peer-window window)]
    (cond
      (> ack (:send-una st))
      (-> st
          (assoc :send-una ack)
          (assoc :dup-acks 0)
          (assoc :rto-ms rto-base-ms)
          (update :unacked (fn [segs]
                             (vec (remove #(<= (segment-end %) ack) segs)))))

      (= ack (:send-una st)) (update st :dup-acks inc)
      :else st)))

(defn- fast-retransmit [st]
  (if (and (>= (:dup-acks st) dup-acks-before-fast-retransmit)
           (seq (:unacked st)))
    (let [seg (first (sort-by :seq (:unacked st)))]
      [(assoc st :dup-acks 0)
       [{:kind :data :stream-id (:stream-id st) :seq (:seq seg)
         :ack (:recv-next st) :window (min 65535 (free-window st))
         :payload (:payload seg)}]])
    [st []]))

(defn on-frame
  "One inbound frame. -> `{:state … :frames […] :delivered bytes :events […]}`.

  `:delivered` is the newly readable bytes, in order, and is drained from the
  state — the caller writes them to its socket and they are gone. Holding them
  would make this namespace responsible for a buffer whose consumer it cannot
  see."
  [st frame now-ms]
  (let [{:keys [kind seq ack window payload]} frame]
    (case kind
      :open-ok
      ;; Segmenting here is load-bearing, not tidiness. `send-bytes` queues
      ;; while `:opening` and `segment!` refuses to run, so anything the
      ;; application wrote before the OPEN-OK arrived is sitting in `:pending`
      ;; with nothing scheduled to move it. A forwarder hits this on its very
      ;; first connection — the local client writes immediately — and the
      ;; symptom is a stream that opens cleanly and then transfers nothing.
      (let [[st frames] (segment! (assoc st :phase :open :peer-window window)
                                  now-ms)]
        {:state st :frames frames :delivered []
         :events [{:event :stream-open :stream (:stream-id st)}]})

      :ack
      (let [st (apply-ack st ack window)
            [st frames] (fast-retransmit st)
            [st more] (segment! st now-ms)]
        {:state st :frames (into frames more) :delivered [] :events []})

      :data
      (let [had-fin? (some? (:recv-fin st))
            st (apply-ack st ack window)
            st (accept-data st {:seq seq :payload payload})
            delivered (:delivered st)
            st (assoc st :delivered [])
            ;; The FIN may have arrived FIRST and be sitting in `:fin-at`
            ;; waiting for this segment — the `:fin` branch says so itself
            ;; ("a FIN can overtake data it was sent after") and records the
            ;; state correctly. What it cannot do is emit the event, because
            ;; at that moment the prefix had not reached the FIN yet. Emitting
            ;; it only there left half-close silently unreported on every
            ;; reordered close: the state said closed and nobody was told, so
            ;; a forwarder never called `end` on its local socket and a
            ;; response body delimited by EOF never completed. Measured over
            ;; the real overlay at 2 failures in 6 runs before this line
            ;; existed — flaky precisely because it depends on arrival order.
            newly-fin? (and (not had-fin?) (some? (:recv-fin st)))
            st (if (and newly-fin? (:sent-fin? st) (empty? (:unacked st)))
                 (assoc st :phase :closed)
                 st)
            [st more] (segment! st now-ms)]
        {:state st
         ;; Always ack a DATA frame, including a duplicate: a duplicate means
         ;; the sender did not see the previous ack, and staying silent leaves
         ;; it retransmitting until it gives up.
         :frames (into [(ack-frame st)] more)
         :delivered delivered
         :events (if newly-fin?
                   [{:event :stream-peer-fin :stream (:stream-id st)}]
                   [])})

      :open
      ;; A duplicate OPEN: the initiator did not see the OPEN-OK. Answering with
      ;; the same one is idempotent; treating it as a new stream would let a
      ;; peer reset an established stream by replaying its own OPEN.
      {:state st :frames (if-let [f (:open-ok-frame st)] [f] []) :delivered []
       :events []}

      :fin
      (let [st (apply-ack st ack window)
            ;; Recorded, then consumed only when the contiguous prefix reaches
            ;; it — a FIN can overtake data it was sent after. Half-close is the
            ;; point: `ssh` and every request/response protocol send EOF one way
            ;; and keep reading the other.
            st (-> st (assoc :fin-at seq) deliver-contiguous)
            delivered (:delivered st)
            st (assoc st :delivered [])
            st (if (and (:sent-fin? st) (:recv-fin st) (empty? (:unacked st)))
                 (assoc st :phase :closed)
                 st)]
        {:state st :frames [(ack-frame st)] :delivered delivered
         :events (if (:recv-fin st)
                   [{:event :stream-peer-fin :stream (:stream-id st)}]
                   [])})

      :rst
      {:state (assoc st :phase :reset
                     :reset-reason (keyword (payload-text payload)))
       :frames [] :delivered []
       :events [{:event :stream-reset :stream (:stream-id st)
                 :reason (keyword (payload-text payload))}]}

      ;; An OPEN on an established stream is not a state this side can be in;
      ;; answering it as a fresh OPEN would let a peer reset another peer's
      ;; stream by guessing an id.
      {:state st :frames [] :delivered []
       :events [{:event :stream-unexpected-frame :stream (:stream-id st)
                 :kind kind}]})))

(defn close
  "Send FIN: this side has no more bytes. -> `{:state … :frames […]}`.

  Queued-but-unsent bytes are flushed first — a FIN that overtook them would
  truncate the stream, and the receiver has no way to tell a truncation from a
  clean close."
  [st now-ms]
  (if (or (:sent-fin? st) (#{:reset :closed} (:phase st)))
    {:state st :frames []}
    (let [[st frames] (segment! st now-ms)
          fin-seq (:send-next st)
          st (-> st
                 (assoc :sent-fin? true :fin-seq fin-seq)
                 (assoc :send-next (inc fin-seq))
                 ;; In `:unacked` like everything else, so `tick` retransmits it
                 ;; and the peer's cumulative ack retires it. `:fin?` is what
                 ;; tells the retransmitter to re-send a FIN rather than a DATA.
                 (update :unacked conj {:seq fin-seq :payload [] :fin? true
                                        :sent-ms now-ms :retries 0}))]
      {:state st
       :frames (conj (vec frames)
                     {:kind :fin :stream-id (:stream-id st)
                      :seq fin-seq :ack (:recv-next st)
                      :window (min 65535 (free-window st)) :payload []})})))

(defn reset
  "Tear the stream down and say why."
  [st reason]
  {:state (assoc st :phase :reset :reset-reason reason)
   :frames [(refuse (:stream-id st) reason)]})

(defn tick
  "Retransmit what is overdue. -> `{:state … :frames […] :events […]}`."
  [st now-ms]
  (cond
    (#{:reset :closed} (:phase st))
    {:state st :frames [] :events []}

    ;; The OPEN is not in `:unacked` — it has no sequence number and the whole
    ;; stream is waiting on it — so it gets its own timer with the same
    ;; give-up ceiling.
    (= :opening (:phase st))
    (cond
      (>= (- now-ms (:opened-ms st)) rto-give-up-ms)
      {:state (assoc st :phase :reset :reset-reason :open-timeout)
       :frames [(refuse (:stream-id st) :open-timeout)]
       :events [{:event :stream-reset :stream (:stream-id st)
                 :reason :open-timeout}]}

      (>= (- now-ms (:open-sent-ms st)) (:rto-ms st))
      {:state (-> st
                  (assoc :open-sent-ms now-ms)
                  (assoc :rto-ms (min rto-max-ms (* 2 (:rto-ms st)))))
       :frames [(:open-frame st)] :events []}

      :else {:state st :frames [] :events []})

    :else
    (let [overdue (filterv #(>= (- now-ms (:sent-ms %)) (:rto-ms st))
                           (:unacked st))]
      (cond
        (empty? overdue)
        {:state st :frames [] :events []}

        ;; Retried past the ceiling: the peer is gone, or the path is. Resetting
        ;; is better than retrying forever, because a forwarder holding a socket
        ;; open against a dead stream looks to its user like a hang.
        (some #(>= (- now-ms (:sent-ms %)) rto-give-up-ms) overdue)
        (let [{:keys [state frames]} (reset st :retransmit-timeout)]
          {:state state :frames frames
           :events [{:event :stream-reset :stream (:stream-id st)
                     :reason :retransmit-timeout}]})

        :else
        ;; Retransmit from the lowest unacked only. Sending the whole window
        ;; again on every timeout multiplies the traffic on a path that is
        ;; already losing packets.
        (let [seg (first (sort-by :seq overdue))
              st (-> st
                     (assoc :rto-ms (min rto-max-ms (* 2 (:rto-ms st))))
                     (update :unacked
                             (fn [segs]
                               (mapv #(if (= (:seq %) (:seq seg))
                                        (assoc % :sent-ms now-ms
                                               :retries (inc (:retries %)))
                                        %)
                                     segs))))]
          {:state st
           :frames [{:kind (if (:fin? seg) :fin :data)
                     :stream-id (:stream-id st) :seq (:seq seg)
                     :ack (:recv-next st) :window (min 65535 (free-window st))
                     :payload (:payload seg)}]
           :events []})))))

(defn closed?
  "Both directions finished, or the stream was reset.

  `:unacked` must be empty as well as both FINs seen: a stream whose own FIN
  has not been acknowledged has not finished sending, and tearing the socket
  down there would truncate the last write."
  [st]
  (or (= :reset (:phase st))
      (= :closed (:phase st))
      (and (:sent-fin? st) (some? (:recv-fin st)) (empty? (:unacked st)))))

(defn idle?
  "Nothing queued, nothing in flight — the caller may stop ticking it."
  [st]
  (and (empty? (:unacked st)) (empty? (:pending st))))
