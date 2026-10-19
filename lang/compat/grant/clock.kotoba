(ns grant.clock
  "Trusted wall time on a machine that may not have a clock.

  The bare-metal profile has a monotonic APIC timer and no wall clock, and
  `grant.publisher`'s freshness check needs one — that gap is named in
  `resources/aiueos/publisher_contract.edn` and in ADR-2608153500. This
  namespace closes it, and the shape of the answer is the interesting part.

  ## You can always reject. You cannot always accept.

  Freshness is two different questions wearing one name:

  - *is this timestamp too old?* — answerable from a **lower bound** on the
    current time, which every machine has even with no clock at all: **the
    image it is running was built at some instant, and time does not run
    backwards, so now is at least the build stamp.** A signed timestamp older
    than the running image is stale no matter what the machine believes.
  - *is this timestamp recent enough?* — needs an actual reading, because
    accepting is a claim about how much time has passed, and a lower bound says
    nothing about that.

  So a clockless machine keeps the *anti-freeze* half of the check and loses the
  *acceptance* half. `decide-freshness` returns `:stale`, `:fresh`, or
  `:undecidable`, and `:undecidable` is never quietly folded into either — a
  measurement that could not be taken must not read like a measurement that came
  back clean.

  ## Sources, ranked, and why an RTC is not the top

  | source | trust | why |
  |---|---|---|
  | `:signed` | highest | a signed timestamp document from the publisher, verified before it reaches here |
  | `:rtc` | usable | a real reading, but **an attacker with the box can set it**, so it can be moved forward as well as back |
  | `:build-stamp` | lower bound only | cannot be forged downward by anyone who cannot rebuild the image |
  | `:none` | nothing | monotonic uptime alone dates nothing |

  A reading is never accepted below its own lower bound: an RTC that says it is
  earlier than the image's own build is wrong, and this namespace says so rather
  than believing the hardware.")

(def sources
  "Ranked highest-trust first. `:build-stamp` is a bound, not a reading, and is
  kept out of `readings` for that reason."
  [:signed :rtc :build-stamp :none])

(def readings
  "Sources that can answer \"what time is it\" rather than only \"it is at
  least\"."
  #{:signed :rtc})

(def confidences
  "What a caller may do with the resolved time.

  `:signed` — accept or reject freshness.
  `:rtc`    — accept or reject, knowing the reading is attacker-settable.
  `:lower-bound` — reject only. Never accept.
  `:none`   — neither."
  #{:signed :rtc :lower-bound :none})

(defn- reading
  [wall-ms source]
  (when (and (number? wall-ms) (pos? wall-ms)) {:wall-ms wall-ms :source source}))

(defn resolve-time
  "Fuse what the machine knows into one answer.

  `input` — `{:signed-ms :rtc-ms :build-stamp-ms :monotonic-ms :anchor}` where
  `:anchor` is `{:wall-ms :monotonic-ms :source}` captured when a reading was
  last taken, so a machine that saw a signed time at boot can carry it forward
  across a long uptime without asking again.

  Returns `{:aiueos.clock/wall-ms :aiueos.clock/source :aiueos.clock/confidence
  :aiueos.clock/lower-bound-ms}`. `:wall-ms` is nil when nothing can date the
  machine — the map still carries a lower bound, because a bound is useful on
  its own and callers that treat nil as \"no information\" would throw the
  anti-freeze half away."
  [{:keys [signed-ms rtc-ms build-stamp-ms monotonic-ms anchor]}]
  (let [carried (when (and anchor (:wall-ms anchor) (number? monotonic-ms)
                           (number? (:monotonic-ms anchor))
                           (>= monotonic-ms (:monotonic-ms anchor)))
                  {:wall-ms (+ (:wall-ms anchor)
                               (- monotonic-ms (:monotonic-ms anchor)))
                   :source (or (:source anchor) :signed)})
        bound (or build-stamp-ms 0)
        best (or (reading signed-ms :signed)
                 (when (and carried (= :signed (:source carried))) carried)
                 (reading rtc-ms :rtc)
                 (when carried carried))
        ;; a reading below the lower bound is wrong; the bound wins and the
        ;; confidence drops, rather than the machine believing its hardware.
        below? (and best (< (:wall-ms best) bound))
        resolved (when (and best (not below?)) best)]
    {:aiueos.clock/wall-ms (:wall-ms resolved)
     :aiueos.clock/source (cond resolved (:source resolved)
                                (pos? bound) :build-stamp
                                :else :none)
     :aiueos.clock/confidence (cond resolved (if (= :signed (:source resolved)) :signed :rtc)
                                    (pos? bound) :lower-bound
                                    :else :none)
     :aiueos.clock/lower-bound-ms (if (and resolved (> (:wall-ms resolved) bound))
                                    (:wall-ms resolved)
                                    (when (pos? bound) bound))
     :aiueos.clock/reading-below-bound? (boolean below?)}))

(defn decide-freshness
  "Is `timestamp-ms` fresh, stale, or undecidable, given `ttl-ms` and what the
  machine knows about the time?

  Three answers, never two. `:undecidable` is what a machine with no clock
  returns for a timestamp that is not old enough to reject on the bound alone —
  and a caller must not read it as `:fresh`. That is the whole reason this
  function does not return a boolean."
  [clock timestamp-ms ttl-ms]
  (let [{:aiueos.clock/keys [wall-ms confidence lower-bound-ms]} clock]
    (cond
      (nil? timestamp-ms) {:aiueos.clock/freshness :stale
                           :aiueos.clock/reason :timestamp-missing}

      ;; A reading answers both halves, so it is consulted first. Putting the
      ;; bound first instead makes :ttl-exceeded unreachable whenever a reading
      ;; exists -- when there is one, lower-bound-ms *is* the reading, and the
      ;; two conditions are the same inequality. A test caught that; the branch
      ;; order is the fix, not a second reason label.
      (contains? #{:signed :rtc} confidence)
      (if (> (- wall-ms timestamp-ms) ttl-ms)
        {:aiueos.clock/freshness :stale :aiueos.clock/reason :ttl-exceeded
         :aiueos.clock/age-ms (- wall-ms timestamp-ms)
         :aiueos.clock/confidence confidence}
        {:aiueos.clock/freshness :fresh
         :aiueos.clock/age-ms (- wall-ms timestamp-ms)
         :aiueos.clock/confidence confidence})

      ;; No reading: the bound can still reject. This is the whole anti-freeze
      ;; half, and it survives on a machine with no clock at all.
      (and lower-bound-ms (< (+ timestamp-ms ttl-ms) lower-bound-ms))
      {:aiueos.clock/freshness :stale
       :aiueos.clock/reason :older-than-lower-bound
       :aiueos.clock/lower-bound-ms lower-bound-ms}

      :else {:aiueos.clock/freshness :undecidable
             :aiueos.clock/reason (if (= :lower-bound confidence)
                                    :no-reading-only-a-bound
                                    :no-time-source)})))

(defn advance-anchor
  "Record a reading so a later resolve can carry it forward on the monotonic
  timer. Refuses to anchor on anything that is not a reading — a bound is not a
  clock, and anchoring on one would turn \"at least\" into \"exactly\"."
  [{:keys [wall-ms monotonic-ms source]}]
  (when (and (number? wall-ms) (number? monotonic-ms) (contains? readings source))
    {:wall-ms wall-ms :monotonic-ms monotonic-ms :source source}))

(defn usable-for-tls?
  "Certificate expiry needs an actual reading. A lower bound can tell you a
  certificate has expired; it cannot tell you one is still valid."
  [clock]
  (contains? #{:signed :rtc} (:aiueos.clock/confidence clock)))
