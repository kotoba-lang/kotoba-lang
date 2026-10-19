(ns tcp.sack
  "RFC 2018 — telling the sender which segments actually arrived.

  Without it a sender that loses one segment out of a window knows only that the
  cumulative ACK stopped moving. It cannot tell one loss from ten, so it resends
  from the hole onward and the receiver discards most of what it gets — data the
  network carried twice for nothing, on a path the sender has just decided is
  congested.

  With it the receiver names the ranges it holds, and the sender resends exactly
  the holes.

  ## SACK is advisory, and that is the whole subtlety

  RFC 2018 §8 permits a receiver to **renege**: to report a range as received
  and later discard it, because the data is held in a buffer the receiver may
  need to reclaim. So a sender may not treat a SACK as delivery.

  Concretely, `apply-blocks` marks queue entries as SACKed and **does not remove
  them**. Only the cumulative ACK retires an entry, because only the cumulative
  ACK is a promise. A sender that frees SACKed data and then meets a reneging
  receiver has lost bytes it can never resend, and the connection stalls with no
  error anywhere. That is why `sacked?` exists and why nothing here calls
  `retransmit/ack`.

  ## The option is small, and how small depends on what else is on

  A TCP header has 40 bytes of option space. Each SACK block costs 8 and the
  option header costs 2, so four blocks fit — unless timestamps are also in use,
  which cost 10 more and leave room for **three**. `max-blocks` takes that as an
  argument rather than assuming, because a stack that always emits four silently
  overruns the option space on every timestamped connection.

  ## Order is not arbitrary

  §4: the first block must be the one covering the **most recently received**
  segment, and the blocks after it should repeat the previous report where they
  can. A receiver that emits its blocks in sequence order instead makes the
  sender's newest information the easiest one to lose when the option is
  truncated."
  (:require [tcp.seq :as s]))

(def block-bytes 8)
(def option-header-bytes 2)
(def option-space-bytes 40)
(def timestamp-option-bytes 10)

(defn max-blocks
  "How many blocks fit alongside whatever else the header carries."
  ([] (max-blocks false))
  ([timestamps?]
   (quot (- option-space-bytes option-header-bytes
            (if timestamps? timestamp-option-bytes 0))
         block-bytes)))

(defn permitted?
  "SACK is used only when both SYNs carried SACK-permitted, like every other
  negotiated option in this stack."
  [ours theirs]
  (boolean (and (:sack-permitted ours) (:sack-permitted theirs))))

;; ── receiver side ─────────────────────────────────────────────────────────

(defn blocks-from-held
  "Turn the reassembler's held ranges into SACK blocks.

  `held` is `[{:seq n :data [...]} ...]` as `tcp.reassemble` keeps it — the
  ranges the receiver **has**, which is the inverse of what `gaps` reports.
  Adjacent ranges are coalesced, because two blocks that touch describe one
  range and spending an option slot on the seam wastes a report the sender
  needed."
  [held]
  (->> held
       (map (fn [h] [(:seq h) (s/+seq (:seq h) (count (:data h)))]))
       (reduce (fn [acc [lo hi]]
                 (if-let [[plo phi] (peek acc)]
                   (if (= (s/wrap phi) (s/wrap lo))
                     (conj (pop acc) [plo hi])
                     (conj acc [lo hi]))
                   (conj acc [lo hi])))
               [])))

(defn report
  "The blocks to put in an ACK, newest first and bounded by the option space.

  `newest-seq` is the sequence number of the segment that just arrived; the
  block containing it goes first (§4). `previous` is what was reported last
  time, and its blocks fill the remaining slots in order — repeating them is
  what lets a sender survive a lost ACK without losing its picture."
  ([held newest-seq] (report held newest-seq [] false))
  ([held newest-seq previous timestamps?]
   (let [all (blocks-from-held held)
         newest (first (filter (fn [[lo hi]] (and newest-seq (s/leq? lo newest-seq)
                                                  (s/lt? newest-seq hi)))
                               all))
         rest* (remove #(= % newest) all)
         ;; previous blocks that are still real, in the order they were reported
         repeats (filter (fn [b] (some #(= % b) rest*)) previous)
         others (concat repeats (remove (fn [b] (some #(= % b) repeats)) rest*))]
     (vec (take (max-blocks timestamps?)
                (remove nil? (cons newest others)))))))

;; ── sender side ───────────────────────────────────────────────────────────

(def ^:private entry-end s/segment-end)

(defn- covered? [e [lo hi]]
  (and (s/geq? (:seq e) lo) (s/leq? (entry-end e) hi)))

(defn apply-blocks
  "Mark retransmission-queue entries the blocks cover.

  **Marks. Does not remove.** A receiver may renege on a SACK (§8), so only the
  cumulative ACK may retire an entry. Removing here would drop data that a
  reneging receiver will never see again, and the connection stalls with nothing
  reporting why."
  [q blocks]
  (update q :tcp.rtx/entries
          (fn [es]
            (mapv (fn [e]
                    (if (some #(covered? e %) blocks)
                      (assoc e :sacked? true)
                      e))
                  es))))

(defn sacked? [e] (boolean (:sacked? e)))

(defn holes
  "Entries the SACK blocks did **not** cover — what actually needs resending.

  This is the whole point of the option: without it a sender resends from the
  first unacknowledged byte onward, and most of what it sends is data the
  receiver already has."
  [q]
  (vec (remove sacked? (:tcp.rtx/entries q))))

(defn next-to-retransmit
  "The oldest un-SACKed entry, or nil. One at a time, for the same reason
  `tcp.retransmit/due` returns one: a burst is what the congestion signal just
  asked the sender not to send."
  [q]
  (first (holes q)))

(defn pipe
  "RFC 6675's estimate of how much is actually in the network: bytes sent minus
  bytes SACKed. A sender that counts SACKed data as still in flight under-sends
  for the rest of the recovery, which is the throughput cost of not having this
  number."
  [q]
  (reduce (fn [n e] (if (sacked? e) n (+ n (:len e 0))))
          0 (:tcp.rtx/entries q)))
