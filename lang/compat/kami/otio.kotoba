(ns kami.otio
  "OpenTimelineIO (OTIO) as data — 'hiccup for cuts'. OTIO (Pixar's editorial-timeline interchange) is
   JSON tagged with OTIO_SCHEMA; this hides the RationalTime/TimeRange wrapping and schema boilerplate
   behind concise builders, so an edit / cut list / conform is composable data you fork and diff. The
   video axis of the kami.* family (sibling to kami.usd on the Pixar side). `.cljc`, serialized via
   kami.json.

   Builders return plain EDN maps (the OTIO object model); `otio` serializes a timeline to JSON.
     (rt 48 24)                         → RationalTime value 48 @ rate 24
     (trange 0 48 24)                   → TimeRange [start 0, duration 48] @ 24
     (external \"shot.mov\")              → ExternalReference target_url
     (clip \"s1\" {:from 0 :dur 48 :rate 24} (external \"s1.mov\"))
     (gap {:dur 12 :rate 24})
     (track \"V1\" :video child…)  ·  (timeline \"cut\" track…)  →  (otio (timeline …)) ⇒ JSON"
  (:require [kami.json :as json]))

(defn rt
  "A RationalTime: value @ rate."
  [value rate] {:OTIO_SCHEMA "RationalTime.1" :value value :rate rate})

(defn trange
  "A TimeRange: start_time + duration, both at rate."
  [start dur rate]
  {:OTIO_SCHEMA "TimeRange.1" :start_time (rt start rate) :duration (rt dur rate)})

(defn external
  "An ExternalReference media reference."
  [target-url] {:OTIO_SCHEMA "ExternalReference.1" :target_url target-url :metadata {}})

(defn clip
  "A Clip: name, {:from :dur :rate} source range, and a media reference."
  [name {:keys [from dur rate]} media]
  {:OTIO_SCHEMA "Clip.1" :name name
   :source_range (trange from dur rate) :media_reference media
   :effects [] :markers [] :metadata {}})

(defn gap
  "A Gap (blank) of {:dur :rate} (starting at 0)."
  [{:keys [dur rate]}]
  {:OTIO_SCHEMA "Gap.1" :name "" :source_range (trange 0 dur rate)
   :effects [] :markers [] :metadata {}})

(defn track
  "A Track of a :video/:audio kind holding child items in order."
  [name kind & children]
  {:OTIO_SCHEMA "Track.1" :name name :kind (case kind :video "Video" :audio "Audio" (clojure.core/name kind))
   :children (vec children) :effects [] :markers [] :metadata {}})

(defn timeline
  "A Timeline: name + tracks (wrapped in a Stack)."
  [name & tracks]
  {:OTIO_SCHEMA "Timeline.1" :name name
   :tracks {:OTIO_SCHEMA "Stack.1" :name "tracks" :children (vec tracks)
            :effects [] :markers [] :metadata {}}
   :metadata {}})

(defn otio
  "Serialize an OTIO object (typically a timeline) to a JSON string."
  [x] (json/json x))
