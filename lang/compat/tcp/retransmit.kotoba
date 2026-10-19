(ns tcp.retransmit
  "The retransmission queue: what has been sent, what has been acknowledged, and
  what is due to go again.

  Pure. `queue` holds segments in sequence order; `ack` removes what a
  cumulative ACK covers; `due` asks which segments have outlived their timeout
  at a given instant. Nothing here sends anything, and nothing reads a clock —
  every function that needs the time takes `:now-ms`.

  ## Cumulative ACK means a prefix, and only a prefix

  TCP's ACK number acknowledges *everything before it*. A segment is retired
  when its last byte is below the ACK, and a segment straddling the ACK is
  **not** retired — the peer has some of it, and resending the whole thing is
  correct while resending nothing loses the tail. `ack` therefore compares the
  segment's end, not its start, and does it in sequence space so a wrap does not
  retire the whole queue.

  ## Karn's flag lives on the entry

  Whether a segment has been retransmitted has to survive until its ACK arrives,
  because that is when the estimator needs to know whether the sample is
  ambiguous. Keeping the flag on the queue entry — rather than on the connection
  — is what makes it possible to have one segment retransmitted and another not
  in flight at the same time and still get one of the two samples.

  ## What is missing, said plainly

  No congestion window, no fast retransmit, no SACK, no Nagle. This is the
  timer-driven half only. A sender built on this will retransmit correctly and
  will not know when to slow down."
  (:require [tcp.seq :as s]))

(defn queue [] {:tcp.rtx/entries []})

(def ^:private entry-end s/segment-end)

(defn push
  "Record a segment as sent at `now-ms`. Entries stay in the order they were
  sent; the queue is small enough that order is kept by insertion rather than by
  sorting on every push."
  [q {:keys [seq len syn? fin? now-ms]}]
  (update q :tcp.rtx/entries conj
          {:seq seq :len (or len 0) :syn? (boolean syn?) :fin? (boolean fin?)
           :sent-ms now-ms :first-sent-ms now-ms :retransmitted? false
           :transmissions 1}))

(defn in-flight [q] (:tcp.rtx/entries q))
(defn empty-queue? [q] (empty? (:tcp.rtx/entries q)))

(defn ack
  "Apply a cumulative ACK.

  Returns `{:queue <remaining> :retired [entries] :sample-ms n :ambiguous? b}`.

  `:sample-ms` is the round trip for the **newest fully-acknowledged entry that
  was never retransmitted** — that is the one measurement this ACK can be said
  to have timed. When every retired entry was retransmitted, `:sample-ms` is nil
  and `:ambiguous?` is true, which is Karn's case and is reported rather than
  guessed."
  [q ack-num now-ms]
  (let [{:keys [retired kept]}
        (reduce (fn [acc e]
                  (if (s/leq? (entry-end e) ack-num)
                    (update acc :retired conj e)
                    (update acc :kept conj e)))
                {:retired [] :kept []}
                (:tcp.rtx/entries q))
        clean (remove :retransmitted? retired)
        newest (last clean)]
    {:queue (assoc q :tcp.rtx/entries (vec kept))
     :retired retired
     :sample-ms (when newest (- now-ms (:first-sent-ms newest)))
     :ambiguous? (and (seq retired) (empty? clean))}))

(defn due
  "Entries whose timeout has expired at `now-ms`.

  Only the oldest is returned even when several have expired: RFC 6298 §5.4
  retransmits the earliest unacknowledged segment, one per timeout, and a sender
  that flushed the whole queue on one timer would turn a single lost segment
  into a burst the network already told it not to send."
  [q rto-ms now-ms]
  (let [expired (filter #(>= (- now-ms (:sent-ms %)) rto-ms) (:tcp.rtx/entries q))]
    (when (seq expired) [(first expired)])))

(defn mark-retransmitted
  "Record that `seq-num` went again at `now-ms`. Sets Karn's flag, which stays
  set for the life of the entry — one ambiguous transmission makes every later
  ACK of that entry ambiguous too."
  [q seq-num now-ms]
  (update q :tcp.rtx/entries
          (fn [es]
            (mapv (fn [e]
                    (if (= (s/wrap (:seq e)) (s/wrap seq-num))
                      (assoc e :sent-ms now-ms :retransmitted? true
                             :transmissions (inc (:transmissions e)))
                      e))
                  es))))

(defn oldest-unacked [q] (first (:tcp.rtx/entries q)))

(defn give-up?
  "Has an entry been retransmitted past the limit? RFC 9293 §3.8.3 wants a
  connection abandoned rather than retried forever. The limit is the caller's
  policy; the default is the common one."
  ([e] (give-up? e 5))
  ([e limit] (> (:transmissions e 1) limit)))
