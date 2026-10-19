(ns aiueos.topic
  "The topic bus — aiueos's in-process publish/subscribe substrate, the
  ROS-topic analogue (ADR-0002), ported from the retired `aiueos/src/topic.rs`
  Rust module to CLJC per ADR-2607022200.

  A producer `publish`es an i64-range sample to a numeric topic id, a
  consumer `latest`/`take-sample`s the value. Phase-0: latest-value semantics
  (last write wins) + a per-topic publish count, numeric topic ids, integer
  payloads.

  The Rust original mutated `&mut self`; here the bus is an ordinary
  immutable EDN map and every 'mutating' operation is a pure function
  `(f bus ...) -> bus'` (or, where the Rust method also returned a value,
  `(f bus ...) -> [bus' value]`).")

(def empty-bus
  "The initial empty bus value."
  {:aiueos.topic/latest {}
   :aiueos.topic/counts {}
   :aiueos.topic/queues {}
   :aiueos.topic/tick 0})

(defn publish
  "Publish `value` to `topic`: update latest, bump publish count, enqueue for
  FIFO `take-sample`. Returns the new bus.

  Queues are plain vectors used FIFO (append at the end via `conj`, drain
  from the front via `take-sample`) rather than `clojure.lang.PersistentQueue`
  — the latter is JVM-only and this namespace must stay portable to cljs/WASM."
  [bus topic value]
  (-> bus
      (assoc-in [:aiueos.topic/latest topic] value)
      (update-in [:aiueos.topic/counts topic] (fnil inc 0))
      (update-in [:aiueos.topic/queues topic] (fnil conj []) value)))

(defn latest
  "Most recent value on `topic` (peek, non-destructive), or nil."
  [bus topic]
  (get-in bus [:aiueos.topic/latest topic]))

(defn take-sample
  "Pop the oldest unread sample on `topic` (FIFO). Returns `[bus' value]`
  where `value` is nil if the topic's queue is drained (or never published
  to); `bus'` is unchanged from `bus` in that case."
  [bus topic]
  (let [q (get-in bus [:aiueos.topic/queues topic])]
    (if (empty? q)
      [bus nil]
      [(assoc-in bus [:aiueos.topic/queues topic] (subvec q 1)) (first q)])))

(defn pending
  "Unread (not-yet-taken) samples on `topic`."
  [bus topic]
  (count (get-in bus [:aiueos.topic/queues topic])))

(defn topic-count
  "How many times `topic` has been published to."
  [bus topic]
  (get-in bus [:aiueos.topic/counts topic] 0))

(defn tick
  "The current control-loop cycle (what a real clock would read)."
  [bus]
  (:aiueos.topic/tick bus))

(defn advance
  "Advance the cycle counter — the broker calls this once per round. Returns
  the new bus."
  [bus]
  (update bus :aiueos.topic/tick inc))

(defn topics
  "Topics that currently hold a value."
  [bus]
  (set (keys (:aiueos.topic/latest bus))))
