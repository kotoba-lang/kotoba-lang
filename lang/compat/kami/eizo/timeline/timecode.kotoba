(ns kami.eizo.timeline.timecode
  "Frame-accurate integer timecode over an explicit rational timebase.

  Clip/marker/transition positions in this library are always integer frame
  counts from a timeline's zero point — never float seconds and never native
  Clojure ratios (ClojureScript has no ratio type, so `(/ 30000 1001)`
  silently becomes a float there; this namespace instead carries frame rates
  as an explicit {:num :den} pair and does all interval math in integers).")

;; ---------------------------------------------------------------------------
;; Exact rational helpers (portable — no reliance on clj-only ratio type)

(defn- gcd [a b]
  (if (zero? b) (max a 1) (recur b (mod a b))))

(defn rational
  "Reduce a positive num/den pair to lowest terms."
  [num den]
  (let [g (gcd (abs num) (abs den))]
    {:num (quot num g) :den (quot den g)}))

(defn round-half-up
  "round(num/den) for positive num/den, computed in exact integer arithmetic
  (no float division, no native ratio)."
  [num den]
  (quot (+ (* 2 num) den) (* 2 den)))

;; ---------------------------------------------------------------------------
;; Timebase

(defn make-timebase
  "fps as an exact rational {:num :den}. drop-frame? must only be true for
  rates whose rounded fps is a multiple of the NTSC drop pattern (30/60);
  see `drop-frame-eligible?`."
  [num den drop-frame?]
  (let [fps (rational num den)]
    (merge fps {:drop-frame? (boolean drop-frame?)})))

(def film-24 (make-timebase 24 1 false))
(def ntsc-23976 (make-timebase 24000 1001 false))
(def pal-25 (make-timebase 25 1 false))
(def ntsc-2997-df (make-timebase 30000 1001 true))
(def ntsc-2997-ndf (make-timebase 30000 1001 false))
(def ntsc-30 (make-timebase 30 1 false))
(def pal-50 (make-timebase 50 1 false))
(def ntsc-5994-df (make-timebase 60000 1001 true))
(def ntsc-60 (make-timebase 60 1 false))

(defn frame-rate-round
  "Nearest integer fps (30 for 29.97, 60 for 59.94, exact for integral rates)."
  [{:keys [num den]}]
  (round-half-up num den))

(defn drop-frame-eligible?
  "Drop-frame timecode is only a defined concept for the two NTSC rates
  whose rounded fps (30 or 60) is evenly divisible into the standard
  2-frames-per-30fps-minute drop pattern."
  [timebase]
  (let [fr (frame-rate-round timebase)]
    (zero? (mod fr 30))))

;; ---------------------------------------------------------------------------
;; Drop-frame constants (SMPTE 12M), all exact integer arithmetic

(defn- drop-frame-constants [fr-round]
  (let [drop-frames (round-half-up fr-round 15) ; ~6.6666% of fr-round: 2 @30fps, 4 @60fps
        frames-per-minute (- (* fr-round 60) drop-frames)
        frames-per-10-minutes (- (* fr-round 600) (* drop-frames 9))
        frames-per-hour (* frames-per-10-minutes 6)
        frames-per-24-hours (* frames-per-hour 24)]
    {:drop-frames drop-frames
     :frames-per-minute frames-per-minute
     :frames-per-10-minutes frames-per-10-minutes
     :frames-per-hour frames-per-hour
     :frames-per-24-hours frames-per-24-hours}))

;; ---------------------------------------------------------------------------
;; frame-count <-> {:hh :mm :ss :ff}

(defn frames->smpte
  "Convert a non-negative integer frame count (from timeline zero) to a
  display timecode map, applying SMPTE drop-frame correction when
  `(:drop-frame? timebase)` is true."
  [frame-count timebase]
  (when (neg? frame-count)
    (throw (ex-info "frame-count must be non-negative" {:frame-count frame-count})))
  (let [fr-round (frame-rate-round timebase)]
    (if (:drop-frame? timebase)
      (let [{:keys [drop-frames frames-per-minute frames-per-10-minutes frames-per-24-hours]}
            (drop-frame-constants fr-round)
            fc (mod frame-count frames-per-24-hours)
            d (quot fc frames-per-10-minutes)
            m (mod fc frames-per-10-minutes)
            fc2 (if (> m drop-frames)
                  (+ fc (* drop-frames 9 d)
                     (* drop-frames (quot (- m drop-frames) frames-per-minute)))
                  (+ fc (* drop-frames 9 d)))
            frames (mod fc2 fr-round)
            total-seconds (quot fc2 fr-round)
            seconds (mod total-seconds 60)
            total-minutes (quot total-seconds 60)
            minutes (mod total-minutes 60)
            hours (quot total-minutes 60)]
        {:hh hours :mm minutes :ss seconds :ff frames :drop-frame? true})
      (let [frames-per-24-hours (* fr-round 60 60 24)
            fc (mod frame-count frames-per-24-hours)
            frames (mod fc fr-round)
            total-seconds (quot fc fr-round)
            seconds (mod total-seconds 60)
            total-minutes (quot total-seconds 60)
            minutes (mod total-minutes 60)
            hours (quot total-minutes 60)]
        {:hh hours :mm minutes :ss seconds :ff frames :drop-frame? false}))))

(defn smpte->frames
  "Inverse of `frames->smpte`."
  [{:keys [hh mm ss ff]} timebase]
  (let [fr-round (frame-rate-round timebase)
        naive (+ (* (+ (* hh 3600) (* mm 60) ss) fr-round) ff)]
    (if (:drop-frame? timebase)
      (let [{:keys [drop-frames]} (drop-frame-constants fr-round)
            total-minutes (+ (* hh 60) mm)
            drop-adjustment (* drop-frames (- total-minutes (quot total-minutes 10)))]
        (- naive drop-adjustment))
      naive)))

(defn format-timecode
  "SMPTE display string: `hh:mm:ss:ff` (non-drop) or `hh:mm:ss;ff` (drop —
  the semicolon before frames is the standard drop-frame marker)."
  [frame-count timebase]
  (let [{:keys [hh mm ss ff drop-frame?]} (frames->smpte frame-count timebase)
        pad #(if (< % 10) (str "0" %) (str %))]
    (str (pad hh) ":" (pad mm) ":" (pad ss) (if drop-frame? ";" ":") (pad ff))))
