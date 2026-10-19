(ns douga.eizo-timeline
  "kami.eizo.timeline (the canonical eizo/video-domain EDL, ADR-2607121400
  Wave 1) -> douga.ffmpeg's render-plan shape. This is douga's Wave-4
  rewire onto kami-eizo-timeline as its canonical input IR: the legacy
  loose scene/lines/assets shape consumed by `douga.ffmpeg/build-render-plan`
  remains available (the live yukkuri production pipeline, ADR-2607051600,
  depends on it and is not touched here) — this namespace is a second,
  parallel entry point for callers that already build a real
  `kami.eizo.timeline` value.

  Mapping (see this repo's README for the full contract):
    - one :video track, one clip per scene: :clip/source-id is the frame
      blob key, :douga/scene-index orders scenes.
    - one :audio track, one clip per voice line: :clip/source-id is the
      voice blob key, :douga/scene-index + :douga/line-index order lines
      within/across scenes, :douga/text + :douga/speaker carry caption data.
    - bgm is a single timeline marker named \"bgm\" carrying
      :douga/bgm-blob-key.
    - fps comes from the timeline's timebase (rounded, since ffmpeg -r takes
      an integer frame rate here — see kami.eizo.timeline.timecode/frame-rate-round).
    - resolution isn't part of kami-eizo-timeline's EDL model, so it's read
      from an extra :douga/resolution key on the timeline map itself
      (defaults to \"1280x720\", same default douga.ffmpeg/build-render-plan
      already used).

  v0 scope, updated (ADR-2607121400 Wave 5 -- dissolve, Wave 6 -- wipe):
  `:dissolve` and `:wipe` video-track transitions now have a real render
  path — a genuine `xfade` crossfade / spatial wipe, see
  `douga.ffmpeg/xfade-transition-cmd` and this repo's README. Other
  transition types (`:slide`/`:wipe-tl`/etc. -- anything not in
  supported-transition-types) are still unimplemented and a video track
  carrying one is rejected with ex-info rather than silently ignored or
  silently downgraded to a hard cut; wiring those is a further follow-up."
  (:require [clojure.string :as str]
            [kami.eizo.timeline :as tl]
            [kami.eizo.timeline.timecode :as tc]
            [douga.ffmpeg :as ffmpeg]))

(defn- track-of-type [timeline type]
  (first (filter #(= type (:track/type %)) (:timeline/tracks timeline))))

(defn- bgm-marker [timeline]
  (first (filter #(= "bgm" (:marker/name %)) (:timeline/markers timeline))))

(def ^:private supported-transition-types
  "Transition types douga.ffmpeg has a real render path for: :dissolve (a
  real xfade `fade` filter_complex) and :wipe (a real xfade `wipeleft`
  filter_complex), see douga.ffmpeg/xfade-transition-cmd. Other
  kami.eizo.timeline transition types have no ffmpeg builder yet and are
  still rejected below, same as v0's blanket rejection was."
  #{:dissolve :wipe})

(defn- video-segments
  "scene-index -> frame-blob-key / duration-frames, from the :video track's
  clips, plus any supported transitions on the track translated from
  :clip/id refs to scene-index pairs (`:transitions`, consumed by
  render-plan to populate :video-transitions). Unsupported transition
  types (anything not in supported-transition-types) have no ffmpeg render
  path yet and are rejected with ex-info — strip them before calling this
  fn, same as v0."
  [video-track]
  (when video-track
    (let [unsupported (remove #(contains? supported-transition-types (:transition/type %))
                               (:track/transitions video-track))]
      (when (seq unsupported)
        (throw (ex-info (str "douga.eizo-timeline: video-track transition type(s) "
                              (pr-str (vec (distinct (map :transition/type unsupported))))
                              " have no ffmpeg render path yet (supported: "
                              (pr-str supported-transition-types) "); "
                              "strip them before calling this fn")
                         {:track video-track :unsupported-transitions unsupported}))))
    (let [clips (:track/clips video-track)
          clip-id->scene-index (into {} (map (juxt :clip/id :douga/scene-index) clips))]
      {:frame-by-scene
       (into {} (map (fn [c] [(:douga/scene-index c) (:clip/source-id c)])) clips)
       :duration-by-scene
       (into {} (map (fn [c] [(:douga/scene-index c) (:clip/duration c)])) clips)
       :transitions
       (mapv (fn [tr]
               {:from-scene-index (clip-id->scene-index (:transition/from-clip tr))
                :to-scene-index (clip-id->scene-index (:transition/to-clip tr))
                :duration-frames (:transition/duration tr)
                :transition-type (:transition/type tr)})
             (:track/transitions video-track))})))

(defn- voice-lines-by-scene
  "scene-index -> ordered [line-index voice-blob-key text speaker] tuples,
  from the :audio track's clips."
  [audio-track]
  (when audio-track
    (reduce
     (fn [acc c]
       (let [si (:douga/scene-index c)
             li (or (:douga/line-index c) 0)
             text (or (:douga/text c) "")
             speaker (-> (or (:douga/speaker c) "left") str str/trim str/lower-case)]
         (update acc si (fnil conj []) [li (:clip/source-id c) text speaker])))
     {}
     (:track/clips audio-track))))

(defn render-plan
  "kami.eizo.timeline `timeline` -> the same render-plan shape
  `douga.ffmpeg/build-render-plan` produces (`{:segments [...]
  :bgm-blob-key ... :width :height :fps}`), so the existing
  scene-segment-cmd / concat-segments-cmd / bgm-mix-cmd / concat-list-text
  command builders keep working unchanged downstream. Each segment now also
  carries :duration-frames (the clip's own full duration, from
  :clip/duration — needed to compute a transition's ffmpeg xfade offset/duration
  in seconds). If the :video track has any supported transitions
  (:dissolve / :wipe), the plan additionally carries :video-transitions — a
  vector of `{:from-scene-index :to-scene-index :duration-frames
  :transition-type}` maps, one per transition, for a caller to feed to
  `douga.ffmpeg/xfade-transition-cmd` alongside the two bridged segments'
  :frame-blob-key/:duration-frames (`:transition-type` selects the xfade
  mode, see `douga.ffmpeg/xfade-mode-by-transition-type`)."
  [timeline]
  (let [video-track (track-of-type timeline :video)
        audio-track (track-of-type timeline :audio)
        {:keys [frame-by-scene duration-by-scene transitions]} (video-segments video-track)
        voices (voice-lines-by-scene audio-track)
        order (sort (into (set (keys frame-by-scene)) (keys voices)))
        segments
        (vec
         (keep
          (fn [si]
            (when-let [frame (get frame-by-scene si)]
              (let [ordered (sort-by first (get voices si []))]
                {:scene-index si
                 :frame-blob-key frame
                 :duration-frames (get duration-by-scene si)
                 :voice-blob-keys (mapv second ordered)
                 :texts (mapv #(nth % 2) ordered)
                 :speakers (mapv #(nth % 3) ordered)})))
          order))
        bgm (:douga/bgm-blob-key (bgm-marker timeline))
        [w h] (ffmpeg/parse-resolution (:douga/resolution timeline) [1280 720])
        fps (tc/frame-rate-round (:timeline/timebase timeline))]
    (cond-> {:segments segments
             :bgm-blob-key bgm
             :width w
             :height h
             :fps fps}
      (seq transitions) (assoc :video-transitions transitions))))

;; ---------------------------------------------------------------------------
;; Builders — convenience constructors that mirror kami.eizo.timeline's own
;; constructor style, pre-populated with the extra :douga/* keys this
;; namespace reads. Any map with the same keys works just as well; these
;; just save callers from repeating boilerplate.

(defn scene-clip
  "A :video-track clip for one scene. duration-frames must be known up
  front (e.g. from a pre-rendered-audio duration pass) — unlike the legacy
  build-render-plan path, a real EDL clip cannot leave duration implicit
  for ffmpeg to discover at render time via -shortest."
  [{:keys [scene-index frame-blob-key timeline-start duration-frames]}]
  (assoc (tl/clip {:id (keyword (str "scene-" scene-index))
                    :source-id frame-blob-key
                    :source-in 0
                    :source-out duration-frames
                    :timeline-start timeline-start})
         :douga/scene-index scene-index))

(defn voice-clip
  [{:keys [scene-index line-index voice-blob-key text speaker
           timeline-start duration-frames]}]
  (assoc (tl/clip {:id (keyword (str "voice-" scene-index "-" line-index))
                    :source-id voice-blob-key
                    :source-in 0
                    :source-out duration-frames
                    :timeline-start timeline-start})
         :douga/scene-index scene-index
         :douga/line-index line-index
         :douga/text text
         :douga/speaker speaker))

(defn bgm-marker*
  [{:keys [bgm-blob-key duration-frames]}]
  (assoc (tl/marker {:id :bgm :name "bgm" :position 0 :duration duration-frames})
         :douga/bgm-blob-key bgm-blob-key))
