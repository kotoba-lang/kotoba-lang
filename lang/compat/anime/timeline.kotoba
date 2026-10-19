(ns anime.timeline
  "Adapter: anime.production cut-hierarchy data → kami-eizo-timeline EDL.

  anime.production is a production-tracking vocabulary (work → episode →
  scene → cut → layers), not a time-based cut list — it declares no
  duration/timecode attribute for a cut (that stays with the consuming
  actor's own entity data, per the namespace docstring: 'persistence stays
  with the consumer'). This adapter bridges that gap: given an ordered
  sequence of cut entities (each optionally carrying a caller-supplied
  `:cut/duration-frames` and/or `:cut/stage-status`), it produces a
  `kami.eizo.timeline` value with cuts placed sequentially on one video
  track.

  Deliberately NOT modeled as timeline positions: the 12 production stages
  (`anime.production/stages`). The stage board is explicitly parallel, not
  serial (see that namespace's docstring — 'board columns run in parallel'),
  so a stage boundary has no meaningful playback-time position; forcing one
  onto the timeline would be fabricated, not derived. What IS a meaningful
  timeline position: a cut whose derived priority is \"retake\" — that's a
  real fact about a real point in the cut sequence, so retake cuts become
  timeline markers.

  Also deliberately NOT modeled as separate tracks: the 8 cut-child layers
  (`anime.production/layer-specs` — storyboard/layout/conceptKeyframe/
  keyframe/inbetween/background/composite/soundCue). Those are sequential
  *production states* of one cut's visual content (the cut isn't
  simultaneously showing its storyboard AND its finished composite at
  playback time — composite is what finish produces), not compositing
  layers stacked at playback. So one cut → one clip; a richer source
  reference (e.g. a real `composite_uri`) is a caller concern via
  `:clip/source-id` override, not this adapter's."
  (:require [anime.production :as production]
            [kami.eizo.timeline :as tl]))

(defn cut-sequence->timeline
  "cuts: ordered seq of cut entity maps (anime.production/schema shape).
  Recognized optional keys per cut:
    :cut/id               (required — used as both clip id and source-id)
    :cut/duration-frames   (falls back to opts :default-duration-frames)
    :cut/stage-status      (a stage-status map per anime.production/
                             default-stage-status; used only to detect
                             retake cuts for markers, not for timing)

  opts:
    :timebase                required — a kami.eizo.timeline.timecode
                              timebase (e.g. kami.eizo.timeline.timecode/
                              film-24, the conventional anime production
                              rate; this adapter does not assume one)
    :default-duration-frames required — duration applied to any cut without
                              its own :cut/duration-frames
    :track-id                optional, default :video-1
    :timeline-id-fn          optional, default identity — applied to a cut's
                              :cut/id to derive :clip/id / :clip/source-id
                              (e.g. to disambiguate ids across scenes)

  Returns a kami.eizo.timeline `timeline` value (via `tl/timeline`)."
  [cuts {:keys [timebase default-duration-frames track-id timeline-id-fn]
         :or {track-id :video-1 timeline-id-fn identity}}]
  {:pre [(some? timebase) (some? default-duration-frames)]}
  (loop [remaining cuts
         offset 0
         clips []
         markers []]
    (if-let [cut (first remaining)]
      (let [id       (timeline-id-fn (:cut/id cut))
            duration (or (:cut/duration-frames cut) default-duration-frames)
            clip     (tl/clip {:id id :source-id id
                                :source-in 0 :source-out duration
                                :timeline-start offset})
            retake?  (when-let [ss (:cut/stage-status cut)]
                       (= "retake" (production/derive-cut-priority ss)))
            markers' (cond-> markers
                       retake? (conj (tl/marker {:id (keyword (str (name id) "-retake"))
                                                  :name (str "retake: " (name id))
                                                  :position offset
                                                  :color :red})))]
        (recur (rest remaining) (+ offset duration) (conj clips clip) markers'))
      (tl/timeline {:timebase timebase
                    :tracks [(tl/track {:id track-id :type :video :clips clips})]
                    :markers markers}))))
