(ns tcp.seq
  "RFC 9293 §3.4 sequence arithmetic — the part of TCP that is wrong in most
  hand-rolled stacks.

  Sequence numbers are 32 bits and **wrap**. `<` on the raw integers is not the
  comparison TCP means: after a wrap, a segment with a small number can be newer
  than one with a large number. RFC 9293 defines the comparison modulo 2^32, and
  every acceptability test in the state machine depends on it.

  Getting this wrong does not fail loudly. A stack that compares raw integers
  works perfectly for the first four gigabytes of a connection and then starts
  discarding valid segments — which is why this is its own namespace with its
  own tests rather than a couple of helpers inside the state machine.")

(def modulus 4294967296)                       ; 2^32
(def half 2147483648)                          ; 2^31

(defn wrap
  "Bring any integer into the 32-bit sequence space."
  [n] (mod n modulus))

(defn +seq
  "Sequence addition, wrapping."
  [a b] (wrap (+ a b)))

(defn -seq
  "Distance from `a` to `b` going forward, wrapping. Always 0..2^32-1."
  [b a] (wrap (- b a)))

(defn lt?
  "Is `a` before `b` in sequence space?

  RFC 9293's comparison: `a` is before `b` when the forward distance from `a` to
  `b` is less than half the space. Equality is not \"before\", and two numbers
  exactly 2^31 apart are **not ordered** — the protocol cannot tell which came
  first, and this returns false for both directions rather than inventing an
  answer."
  [a b]
  (let [d (-seq b a)]
    (and (pos? d) (< d half))))

(defn leq? [a b] (or (= (wrap a) (wrap b)) (lt? a b)))
(defn gt? [a b] (lt? b a))
(defn geq? [a b] (or (= (wrap a) (wrap b)) (gt? a b)))

(defn between?
  "Is `x` in the half-open window [lo, hi)? Wrapping-aware."
  [x lo hi]
  (and (leq? lo x) (lt? x hi)))

(defn ordered?
  "Can these two be ordered at all? False for the 2^31 antipode, where TCP's
  comparison is undefined. Callers that must not act on an undefined ordering
  check this first."
  [a b]
  (let [d (-seq b a)] (not= d half)))

(defn segment-end
  "The sequence number one past a segment's last byte.

  SYN and FIN each occupy one sequence number of their own, which is why they
  are counted here rather than folded into `len`. A sender that omits them
  computes an end one short, and every range comparison built on it -- what a
  SACK block covers, what a cumulative ACK retires -- is wrong by exactly the
  handshake byte.

  `tcp.sack` and `tcp.retransmit` each carried a private copy of this. They
  were textually identical, which is the good case; the bad one is where they
  stop being."
  [{:keys [seq len syn? fin?] :or {len 0}}]
  (+seq seq (+ len (if syn? 1 0) (if fin? 1 0))))

;; ── acceptability (RFC 9293 §3.10.7.4) ─────────────────────────────────────

(defn acceptable?
  "Is a segment with `seg-seq` and `seg-len` acceptable against a receive window
  starting at `rcv-nxt` with size `rcv-wnd`?

  The four cases in the RFC's table, kept apart rather than collapsed, because
  the zero-length and zero-window cases are the ones hand-written stacks drop:

    len 0, wnd 0  -> only an exact match at rcv-nxt
    len 0, wnd >0 -> the sequence must fall in the window
    len >0, wnd 0 -> nothing is acceptable
    len >0, wnd >0-> either end of the segment must fall in the window"
  [seg-seq seg-len rcv-nxt rcv-wnd]
  (let [end (+seq seg-seq (max 0 (dec seg-len)))]
    (cond
      (and (zero? seg-len) (zero? rcv-wnd)) (= (wrap seg-seq) (wrap rcv-nxt))
      (and (zero? seg-len) (pos? rcv-wnd)) (between? seg-seq rcv-nxt (+seq rcv-nxt rcv-wnd))
      (and (pos? seg-len) (zero? rcv-wnd)) false
      :else (or (between? seg-seq rcv-nxt (+seq rcv-nxt rcv-wnd))
                (between? end rcv-nxt (+seq rcv-nxt rcv-wnd))))))
