(ns tcp.rto
  "RFC 6298 — computing the retransmission timeout from round-trip samples.

  A pure estimator: `sample` folds one RTT measurement in, `backoff` doubles the
  timeout after a loss, and neither reads a clock. The caller measures the round
  trip and passes the number.

  ## Why the estimator is not just an average

  RTO has to cover the round trip *and* its variance. An estimator that tracked
  only the mean would retransmit early whenever the network jittered, and every
  early retransmission is a segment the receiver already has plus a congestion
  signal the sender invented. RFC 6298 tracks the smoothed mean (SRTT) and the
  smoothed deviation (RTTVAR) separately and adds four deviations of headroom:

      RTO = SRTT + max(G, K·RTTVAR)     K = 4

  The first sample seeds differently from the rest — `SRTT = R`, `RTTVAR = R/2`
  — because there is no history to smooth against, and pretending there is one
  biases the first several minutes of a connection.

  ## Karn's algorithm is not optional

  When a segment is retransmitted, an arriving ACK is ambiguous: it may
  acknowledge the original or the copy, and the two have very different round
  trips. Measuring it either way corrupts the estimator in a direction that
  makes the next timeout worse, which produces more retransmissions, which
  produces more bad samples. `sample` therefore refuses a measurement taken from
  a retransmitted segment — refuses, not weights.

  The counterpart is that a connection which is retransmitting stops getting
  samples entirely, so the timeout can only grow. That is what `backoff` is for,
  and it is why the RFC pairs them."
  )

(def alpha 0.125)      ; 1/8, the SRTT gain
(def beta 0.25)        ; 1/4, the RTTVAR gain
(def k 4)              ; deviations of headroom
(def clock-granularity-ms 1)

(def initial-rto-ms
  "RFC 6298 §2.1: before any sample, RTO is 1 second."
  1000)

(def min-rto-ms
  "§2.4: the RFC's floor. Kept as a named value because lowering it is the first
  thing anyone reaches for when a connection feels slow, and it is the change
  that turns jitter into a retransmission storm."
  1000)

(def max-rto-ms
  "§2.5: the ceiling must be at least 60 s."
  60000)

(defn estimator
  "A fresh estimator: no samples yet, so `:rto-ms` is the initial 1 s and both
  smoothed values are nil rather than zero. Zero would be a measurement that was
  never taken, and it would make the first real sample look like a huge jump."
  []
  {:tcp.rto/srtt-ms nil :tcp.rto/rttvar-ms nil :tcp.rto/rto-ms initial-rto-ms
   :tcp.rto/samples 0})

(defn- clamp [ms]
  (-> ms (max min-rto-ms) (min max-rto-ms) double Math/round long))

(defn sample
  "Fold one round-trip measurement in.

  `r-ms` is the measured round trip. `retransmitted?` is Karn's flag: when true
  the sample is **discarded**, and the estimator is returned unchanged with
  `:tcp.rto/last-ignored :karn` so a caller can see that a measurement was taken
  and refused rather than never taken."
  ([est r-ms] (sample est r-ms false))
  ([est r-ms retransmitted?]
   (cond
     retransmitted?
     (assoc est :tcp.rto/last-ignored :karn)

     (or (nil? r-ms) (neg? r-ms))
     (assoc est :tcp.rto/last-ignored :not-a-measurement)

     (nil? (:tcp.rto/srtt-ms est))
     ;; §2.2 -- the first sample seeds, it does not smooth.
     (let [srtt (double r-ms)
           rttvar (/ (double r-ms) 2.0)]
       {:tcp.rto/srtt-ms srtt :tcp.rto/rttvar-ms rttvar
        :tcp.rto/rto-ms (clamp (+ srtt (max clock-granularity-ms (* k rttvar))))
        :tcp.rto/samples 1})

     :else
     ;; §2.3 -- RTTVAR is updated FIRST, using the OLD SRTT. Updating SRTT
     ;; first makes the deviation term collapse toward zero on a jump, which
     ;; is precisely when the headroom is needed.
     (let [srtt (:tcp.rto/srtt-ms est)
           rttvar (+ (* (- 1 beta) (:tcp.rto/rttvar-ms est))
                     (* beta (Math/abs (- srtt (double r-ms)))))
           srtt' (+ (* (- 1 alpha) srtt) (* alpha (double r-ms)))]
       {:tcp.rto/srtt-ms srtt' :tcp.rto/rttvar-ms rttvar
        :tcp.rto/rto-ms (clamp (+ srtt' (max clock-granularity-ms (* k rttvar))))
        :tcp.rto/samples (inc (:tcp.rto/samples est))}))))

(defn backoff
  "§5.5: double the timeout after a retransmission, up to the ceiling.

  The smoothed values are left alone. A timeout is not a measurement — nothing
  was observed except that something did not arrive — and folding it into SRTT
  would let one loss move the estimate the network never justified."
  [est]
  (assoc est :tcp.rto/rto-ms (clamp (* 2 (:tcp.rto/rto-ms est)))
         :tcp.rto/backoffs (inc (:tcp.rto/backoffs est 0))))

(defn on-ack-after-loss
  "§5: when a new ACK arrives after backoff, the RTO is recomputed from the
  smoothed values rather than left doubled — but only once a fresh, unambiguous
  sample has been taken. Returns the estimator with the backoff cleared."
  [est]
  (if (:tcp.rto/srtt-ms est)
    (-> est
        (assoc :tcp.rto/rto-ms (clamp (+ (:tcp.rto/srtt-ms est)
                                         (max clock-granularity-ms
                                              (* k (:tcp.rto/rttvar-ms est))))))
        (dissoc :tcp.rto/backoffs))
    (assoc est :tcp.rto/rto-ms initial-rto-ms)))
