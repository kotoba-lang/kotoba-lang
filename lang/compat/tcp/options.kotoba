(ns tcp.options
  "RFC 7323 — window scaling and timestamps, and the negotiation that decides
  whether either is in use.

  Pure: options in and out as data, plus the small amount of state negotiation
  and PAWS need. No clock — `:now-ms` arrives with the event, as everywhere else
  in this stack.

  ## Window scaling exists because the window field is 16 bits

  The header can advertise 65535 bytes and no more. On a path with a large
  bandwidth-delay product that caps throughput regardless of what the congestion
  controller has decided, which makes `tcp.congestion` an exercise: it can grow
  cwnd all it likes and the receiver's advertised window is the binding limit.

  ## Both sides must offer it, and it never applies to the SYN

  The scale is negotiated **in the SYN exchange only**. If either side leaves the
  option out, neither side scales — there is no partial adoption, because a
  scaled window read as unscaled is a window 2^N times too small and a window
  read the other way is 2^N too large. `negotiate` returns the pair or nothing.

  And the SYN's own window field is **never** scaled: the option that establishes
  scaling is in the same segment, so a receiver applying the shift to the segment
  carrying it would be using a factor its peer had not agreed to yet. Applying
  the scale to a SYN is the classic implementation bug in this option, so
  `apply-scale` refuses it by name rather than trusting the caller.

  ## Timestamps buy two different things

  **RTT on retransmitted segments.** `tcp.rto` refuses samples from retransmitted
  segments because an ACK is ambiguous about which copy it acknowledges — that is
  Karn's algorithm, and it is correct without timestamps. With them the echo
  names the transmission being acknowledged, the ambiguity disappears, and the
  sample is usable. `rtt-sample` is the function that lifts that restriction, and
  it exists so the lifting is deliberate rather than an accident of some caller
  forgetting to pass Karn's flag.

  **PAWS.** A 32-bit sequence space wraps, and on a fast path it wraps while old
  duplicates could still be alive. Sequence numbers alone cannot tell a wrapped
  new segment from an old one; a monotonically increasing timestamp can."
  (:require [tcp.seq :as s]))

;; ── option kinds (RFC 9293 §3.1, RFC 7323) ────────────────────────────────

(def kind-eol 0)
(def kind-nop 1)
(def kind-mss 2)
(def kind-window-scale 3)
(def kind-timestamps 8)

(def max-shift
  "RFC 7323 §2.3: a shift above 14 is invalid. A receiver that accepts a larger
  one can be pushed past the 2^30 limit the sequence space allows a window to
  reach, at which point acceptability tests start disagreeing with the peer."
  14)

(defn- u32 [b i]
  (+ (* (nth b i) 16777216) (* (nth b (inc i)) 65536)
     (* (nth b (+ i 2)) 256) (nth b (+ i 3))))

(defn- ->u32 [n]
  [(bit-and (bit-shift-right n 24) 0xff) (bit-and (bit-shift-right n 16) 0xff)
   (bit-and (bit-shift-right n 8) 0xff) (bit-and n 0xff)])

(def parse-errors
  #{:option-overruns :bad-length :shift-too-large})

(defn parse
  "Option bytes -> `{:mss n :window-shift n :timestamps {:tsval :tsecr}}`, or
  `{:tcp.opt/error reason}`.

  A length that would read past the end is refused rather than clamped, for the
  same reason it is in `dhcp.wire`: a clamped option decodes into something
  shorter but plausible from bytes that were never fully received."
  [bs]
  (let [b (vec bs) n (count b)]
    (loop [i 0 acc {}]
      (cond
        (>= i n) acc
        (= kind-eol (nth b i)) acc
        (= kind-nop (nth b i)) (recur (inc i) acc)
        (>= (inc i) n) {:tcp.opt/error :option-overruns}
        :else
        (let [kind (nth b i) len (nth b (inc i))]
          (cond
            (< len 2) {:tcp.opt/error :bad-length}
            (> (+ i len) n) {:tcp.opt/error :option-overruns}
            (= kind kind-mss)
            (if (= len 4)
              (recur (+ i len) (assoc acc :mss (+ (* 256 (nth b (+ i 2))) (nth b (+ i 3)))))
              {:tcp.opt/error :bad-length})
            (= kind kind-window-scale)
            (cond
              (not= len 3) {:tcp.opt/error :bad-length}
              (> (nth b (+ i 2)) max-shift) {:tcp.opt/error :shift-too-large}
              :else (recur (+ i len) (assoc acc :window-shift (nth b (+ i 2)))))
            (= kind kind-timestamps)
            (if (= len 10)
              (recur (+ i len) (assoc acc :timestamps {:tsval (u32 b (+ i 2))
                                                       :tsecr (u32 b (+ i 6))}))
              {:tcp.opt/error :bad-length})
            :else (recur (+ i len) acc)))))))

(defn render
  "Options map -> bytes, padded to a 4-byte boundary with NOPs the way stacks
  conventionally order them (NOP,NOP,TS keeps the timestamp aligned)."
  [{:keys [mss window-shift timestamps]}]
  (let [bs (cond-> []
             mss (into [kind-mss 4 (bit-and (bit-shift-right mss 8) 0xff) (bit-and mss 0xff)])
             window-shift (into [kind-nop kind-window-scale 3 window-shift])
             timestamps (into (concat [kind-nop kind-nop kind-timestamps 10]
                                      (->u32 (:tsval timestamps))
                                      (->u32 (:tsecr timestamps 0)))))
        pad (mod (- 4 (mod (count bs) 4)) 4)]
    (into bs (repeat pad kind-eol))))

;; ── negotiation ───────────────────────────────────────────────────────────

(defn negotiate
  "Decide what is in use, from what we offered in our SYN and what theirs
  carried.

  Returns `{:window-scaling? b :send-shift n :recv-shift n :timestamps? b}`.
  Absent from either side means absent from both — there is no partial adoption
  of either option, because half-applied scaling is a window wrong by a factor
  of 2^N in whichever direction hurts."
  [ours theirs]
  (let [ws (and (:window-shift ours) (:window-shift theirs))
        ts (and (:timestamps ours) (:timestamps theirs))]
    {:tcp.opt/window-scaling? (boolean ws)
     ;; what WE shift outgoing advertisements by is what WE offered; what we
     ;; shift THEIR advertisements by is what THEY offered. Swapping these is
     ;; the other classic bug, and it only shows on asymmetric shifts.
     :tcp.opt/send-shift (if ws (:window-shift ours) 0)
     :tcp.opt/recv-shift (if ws (:window-shift theirs) 0)
     :tcp.opt/timestamps? (boolean ts)}))

(defn apply-scale
  "Turn a 16-bit advertised window into the real one.

  `syn?` must be passed. A SYN's window is never scaled — the option agreeing
  the scale travels in that very segment — and this refuses rather than trusting
  the caller to remember."
  [window shift syn?]
  (if syn? window (bit-shift-left window shift)))

(defn advertise
  "The 16-bit field to put in a segment for a real receive window of
  `window-bytes`.

  Returns nil when the window cannot be expressed at this shift — a caller that
  silently truncated would advertise a window smaller than it means and stall
  its own throughput without any error anywhere."
  [window-bytes shift syn?]
  (let [v (if syn? window-bytes (bit-shift-right window-bytes shift))]
    (when (<= 0 v 65535) v)))

;; ── PAWS (RFC 7323 §5) ────────────────────────────────────────────────────

(def paws-idle-ms
  "§5.5: a connection idle longer than 24 days may legitimately present a
  timestamp that looks older, so the check is suspended past this."
  (* 24 24 60 60 1000))

(defn paws-acceptable?
  "Is a segment's timestamp acceptable against the last one we recorded?

  `nil` recent timestamp means we have none yet and everything is acceptable.
  A timestamp older than the recorded one means an old duplicate that the
  sequence space could have wrapped past — discard it.

  The comparison is modular for the same reason sequence comparison is: the
  timestamp clock is 32 bits and wraps too, and a stack that compares raw
  integers rejects every segment for a while after the wrap."
  ([recent tsval] (paws-acceptable? recent tsval nil nil))
  ([recent tsval last-seen-ms now-ms]
   (cond
     (nil? recent) true
     (and last-seen-ms now-ms (> (- now-ms last-seen-ms) paws-idle-ms)) true
     :else (not (s/lt? tsval recent)))))

(defn record-timestamp
  "Update the recorded timestamp, but only from a segment that is in sequence:
  §4.3 says the echo must come from the segment that advances the window, and
  recording from an out-of-order arrival lets a reordered old segment raise the
  bar against the segments that follow it."
  [state {:keys [tsval seg-seq rcv-nxt now-ms]}]
  (if (and tsval (= (s/wrap seg-seq) (s/wrap rcv-nxt)))
    (assoc state :tcp.opt/recent-tsval tsval :tcp.opt/recent-ms now-ms)
    state))

;; ── RTT from the echo ─────────────────────────────────────────────────────

(defn rtt-sample
  "The round trip a timestamp echo names, or nil.

  This is what lifts Karn's restriction: `tcp.rto/sample` refuses a measurement
  from a retransmitted segment because the ACK is ambiguous about which copy it
  acknowledges. The echo is not ambiguous — it names the transmission — so a
  sample derived here is passed with `retransmitted?` false deliberately, and
  the deliberateness is why this is a named function rather than an inline
  subtraction at a call site."
  [{:keys [tsecr]} now-ms]
  (when (and tsecr (pos? tsecr) now-ms (>= now-ms tsecr))
    (- now-ms tsecr)))
