(ns tcp.reassemble
  "Holding segments that arrived out of order, and handing up the bytes that
  have become contiguous.

  Pure. `accept` takes one in-window segment and returns the reassembler plus
  whatever prefix is now deliverable. No buffers are allocated outside the map,
  nothing is timed, and the caller decides what to do with delivered bytes.

  ## Why a receiver has to hold them at all

  A receiver that discards everything after a gap turns one lost segment into a
  whole window of retransmissions — and the congestion controller reads those as
  a much worse path than the one it is on. Holding the out-of-order data is what
  makes a single loss cost a single retransmission.

  ## Overlap has a policy, and the policy is first-data-wins

  Segments can overlap, and the overlapping bytes can *disagree*. Two receivers
  resolving that differently see two different streams from the same packets,
  which is the whole family of overlap-based intrusion-detection evasions.

  This reassembler keeps **the bytes it already has** and trims the new segment
  down to what is genuinely new. Choosing the newer bytes instead would let
  anyone who can inject a later packet rewrite data the peer already sent. The
  policy is stated here because the failure mode is not a crash — it is two
  parties who believe different things about what was said.

  ## The queue is bounded, and overrun is refused rather than trimmed

  An unbounded out-of-order queue is a memory-exhaustion primitive: an attacker
  sends the whole window except its first segment and the receiver holds it all
  forever. `:max-bytes` bounds it, and a segment that would exceed the bound is
  refused whole (`:queue-full`). Trimming to fit would store a fragment that can
  never be completed, which is a leak with extra steps."
  (:require [tcp.seq :as s]))

(def default-max-bytes
  "How much out-of-order data one connection may hold. A window's worth is the
  natural size; more than that is holding data the sender was never allowed to
  have in flight."
  65535)

(defn reassembler
  [{:keys [rcv-nxt max-bytes] :or {max-bytes default-max-bytes}}]
  {:tcp.ra/rcv-nxt rcv-nxt
   :tcp.ra/held []                     ; sorted, non-overlapping, all above rcv-nxt
   :tcp.ra/held-bytes 0
   :tcp.ra/max-bytes max-bytes})

(def drop-reasons
  #{:duplicate :queue-full :empty})

(defn- seg-end [{:keys [seq data]}] (s/+seq seq (count data)))

(defn- trim-front
  "Drop the leading bytes of `sg` that fall below `at`. Returns nil when nothing
  is left — a segment entirely below rcv-nxt is a duplicate, not an empty
  segment, and the caller distinguishes those."
  [sg at]
  ;; `-seq` is a FORWARD distance, so asking it how far `at` is from a segment
  ;; that starts after `at` returns nearly 2^32 rather than a negative number.
  ;; Reading that as "skip everything" discards every out-of-order segment,
  ;; which is the normal case this namespace exists to handle. Check the
  ;; ordering before computing the distance.
  (if (s/leq? at (:seq sg))
    sg
    (let [skip (s/-seq at (:seq sg))]
      (cond
        (>= skip (count (:data sg))) nil
        :else {:seq at :data (subvec (vec (:data sg)) skip)}))))

(defn- overlaps? [a b]
  (and (s/lt? (:seq a) (seg-end b)) (s/lt? (:seq b) (seg-end a))))

(defn- subtract-held
  "Remove from `sg` every byte range already held. First-data-wins: the held
  bytes stay, and what comes back is the genuinely new pieces, in order."
  [sg held]
  (reduce (fn [pieces h]
            (mapcat (fn [p]
                      (cond
                        (not (overlaps? p h)) [p]
                        :else
                        (let [pre-len (s/-seq (:seq h) (:seq p))
                              pre (when (and (pos? pre-len) (< pre-len (count (:data p))))
                                    {:seq (:seq p) :data (subvec (vec (:data p)) 0 pre-len)})
                              post (trim-front p (seg-end h))]
                          (remove nil? [pre post]))))
                    pieces))
          [sg] held))

(defn- deliverable
  "Split `held` into the contiguous run starting at `rcv-nxt` and the rest."
  [held rcv-nxt]
  (loop [at rcv-nxt out [] remaining (vec held)]
    (if-let [h (first remaining)]
      (if (= (s/wrap (:seq h)) (s/wrap at))
        (recur (seg-end h) (into out (:data h)) (vec (rest remaining)))
        {:data out :at at :held (vec remaining)})
      {:data out :at at :held []})))

(defn accept
  "Take one segment. `sg` is `{:seq n :data [bytes]}`.

  Returns `{:reassembler r :delivered [bytes] :dropped reason}`. `:delivered` is
  the bytes that became contiguous *because of this segment* — it is empty when
  the segment filled no gap, and the caller advances the application stream by
  exactly what it gets."
  [r sg]
  (let [rcv-nxt (:tcp.ra/rcv-nxt r)
        held (:tcp.ra/held r)]
    (cond
      (empty? (:data sg))
      {:reassembler r :delivered [] :dropped :empty}

      ;; entirely at or below what we have already handed up
      (s/leq? (seg-end sg) rcv-nxt)
      {:reassembler r :delivered [] :dropped :duplicate}

      :else
      (let [clipped (trim-front sg rcv-nxt)
            pieces (if clipped (subtract-held clipped held) [])
            new-bytes (reduce + 0 (map #(count (:data %)) pieces))]
        (if (zero? new-bytes)
          {:reassembler r :delivered [] :dropped :duplicate}
          (let [held' (vec (sort-by #(s/-seq (:seq %) rcv-nxt) (concat held pieces)))
                {:keys [data at held]} (deliverable held' rcv-nxt)
                remaining (reduce + 0 (map #(count (:data %)) held))]
            ;; The bound is on what stays HELD, not on what arrives. In-order
            ;; data is delivered and never occupies the queue, so charging it
            ;; would refuse a large but perfectly ordered segment -- measured by
            ;; a test before this was written the other way round.
            (if (> remaining (:tcp.ra/max-bytes r))
              ;; refused whole: a trimmed fragment can never be completed.
              {:reassembler r :delivered [] :dropped :queue-full}
              {:reassembler (assoc r :tcp.ra/rcv-nxt at
                                   :tcp.ra/held held
                                   :tcp.ra/held-bytes remaining)
               :delivered data})))))))

(defn gaps
  "The holes, as `[start end)` pairs — what a SACK option would report if this
  stack had one. Useful on its own for seeing why a stream has stalled."
  [r]
  (let [rcv (:tcp.ra/rcv-nxt r)]
    (first
     (reduce (fn [[acc at] h]
               (if (= (s/wrap at) (s/wrap (:seq h)))
                 [acc (seg-end h)]
                 [(conj acc [at (:seq h)]) (seg-end h)]))
             [[] rcv]
             (:tcp.ra/held r)))))

(defn held-bytes [r] (:tcp.ra/held-bytes r))
(defn rcv-nxt [r] (:tcp.ra/rcv-nxt r))
