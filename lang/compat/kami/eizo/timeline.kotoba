(ns kami.eizo.timeline
  "Portable EDL/timeline data model — the video-domain analog of what
  Premiere Pro / DaVinci Resolve / Avid Media Composer store internally as
  an Edit Decision List. Pure data + validation; no render execution, no
  interchange-format (CMX3600/OTIO/FCPXML) export in v0 — see README.

  All positions and durations are integer frame counts against an explicit
  `kami.eizo.timeline.timecode` timebase, never float seconds."
  (:require [kami.eizo.timeline.timecode :as tc]))

;; ---------------------------------------------------------------------------
;; Constructors (plain EDN maps — these are convenience builders, not the
;; only valid way to build the data; any map with the same keys is fine)

(defn clip
  [{:keys [id source-id source-in source-out timeline-start effect-stack]
    :or {effect-stack []}}]
  {:clip/id id
   :clip/source-id source-id
   :clip/source-in source-in
   :clip/source-out source-out
   :clip/timeline-start timeline-start
   :clip/duration (- source-out source-in)
   :clip/effect-stack effect-stack})

(defn transition
  [{:keys [id type from-clip to-clip duration]}]
  {:transition/id id
   :transition/type type
   :transition/from-clip from-clip
   :transition/to-clip to-clip
   :transition/duration duration})

(defn marker
  [{:keys [id name position duration color] :or {duration 0}}]
  {:marker/id id
   :marker/name name
   :marker/position position
   :marker/duration duration
   :marker/color color})

(defn track
  [{:keys [id type clips transitions effect-stack enabled? muted? locked?]
    :or {clips [] transitions [] effect-stack [] enabled? true muted? false locked? false}}]
  {:track/id id
   :track/type type
   :track/clips (vec (sort-by :clip/timeline-start clips))
   :track/transitions transitions
   :track/effect-stack effect-stack
   :track/enabled? enabled?
   :track/muted? muted?
   :track/locked? locked?})

(defn timeline
  [{:keys [timebase tracks markers] :or {tracks [] markers []}}]
  {:timeline/timebase timebase
   :timeline/tracks tracks
   :timeline/markers markers})

;; ---------------------------------------------------------------------------
;; Derived queries

(defn clip-end
  "Exclusive end frame of a clip on the timeline."
  [c]
  (+ (:clip/timeline-start c) (:clip/duration c)))

(defn track-duration [t]
  (reduce max 0 (map clip-end (:track/clips t))))

(defn timeline-duration
  "Derived, not stored — the frame count one past the last clip on any track."
  [tl]
  (reduce max 0 (map track-duration (:timeline/tracks tl))))

(defn- transition-for
  "The transition (if any) bridging `from-clip-id` -> `to-clip-id`."
  [t from-clip-id to-clip-id]
  (first (filter #(and (= (:transition/from-clip %) from-clip-id)
                        (= (:transition/to-clip %) to-clip-id))
                  (:track/transitions t))))

;; ---------------------------------------------------------------------------
;; Validation — returns a (possibly empty) seq of problem maps rather than
;; throwing, so a caller can decide how to surface multiple issues at once.

(defn- problem [type detail]
  {:problem/type type :problem/detail detail})

(defn validate-clip [c]
  (cond-> []
    (not (<= (:clip/source-in c) (:clip/source-out c)))
    (conj (problem :clip/invalid-source-range c))
    (neg? (:clip/timeline-start c))
    (conj (problem :clip/negative-timeline-start c))
    (not= (:clip/duration c) (- (:clip/source-out c) (:clip/source-in c)))
    (conj (problem :clip/duration-mismatch c))))

(defn validate-transition [tr clips-by-id]
  (let [from (get clips-by-id (:transition/from-clip tr))
        to (get clips-by-id (:transition/to-clip tr))]
    (cond
      (or (nil? from) (nil? to))
      [(problem :transition/dangling-clip-ref tr)]

      (not= (clip-end from) (+ (:clip/timeline-start to) (:transition/duration tr)))
      [(problem :transition/misaligned tr)]

      (> (:transition/duration tr) (min (:clip/duration from) (:clip/duration to)))
      [(problem :transition/duration-exceeds-clip tr)]

      :else [])))

(defn validate-track [t]
  (let [clips (:track/clips t)
        clips-by-id (into {} (map (juxt :clip/id identity) clips))
        clip-problems (mapcat validate-clip clips)
        transition-problems (mapcat #(validate-transition % clips-by-id) (:track/transitions t))
        ordered (sort-by :clip/timeline-start clips)
        overlap-problems
        (mapcat
         (fn [[a b]]
           (let [overlap (- (clip-end a) (:clip/timeline-start b))]
             (cond
               (<= overlap 0) []
               (transition-for t (:clip/id a) (:clip/id b)) []
               :else [(problem :track/unbridged-overlap {:from (:clip/id a) :to (:clip/id b)})])))
         (partition 2 1 ordered))]
    (concat clip-problems transition-problems overlap-problems)))

(defn validate-timeline [tl]
  (let [tb (:timeline/timebase tl)
        dur (timeline-duration tl)
        track-problems (mapcat validate-track (:timeline/tracks tl))
        marker-problems
        (mapcat (fn [m]
                   (if (or (neg? (:marker/position m))
                           (> (+ (:marker/position m) (:marker/duration m)) dur))
                     [(problem :marker/out-of-bounds m)]
                     []))
                 (:timeline/markers tl))
        timebase-problems
        (if (and (:drop-frame? tb) (not (tc/drop-frame-eligible? tb)))
          [(problem :timebase/drop-frame-ineligible tb)]
          [])]
    {:valid? (empty? (concat track-problems marker-problems timebase-problems))
     :errors (vec (concat timebase-problems track-problems marker-problems))}))
