(ns kami.musicxml
  "MusicXML as data — 'hiccup for scores'. MusicXML is the XML interchange for music notation, so a
   score maps onto EDN directly — a melody / lead sheet is composable data you fork and diff. A fresh
   music-notation domain in the kami.* family, built on kami.xml. `.cljc`.

   Builders return the MusicXML element tree (write extra elements as raw hiccup too):
     (note :C 4 4 :whole)   → <note><pitch><step>C</step><octave>4</octave></pitch>
                                <duration>4</duration><type>whole</type></note>
     (rest* 4 :whole)       → a <rest/> note of the given duration/type
     (attributes {:divisions 1 :fifths 0 :beats 4 :beat-type 4 :clef [:G 2]})
     (measure 1 attrs note…)
     (score-partwise {:part-name \"Music\"} measure…)  ⇒ a full <?xml?> <score-partwise> document"
  (:require [kami.xml :as xml]
            [clojure.string :as str]))

(defn- nm [x] (if (keyword? x) (name x) (str x)))

(defn note
  "A pitched note: step (C..B), octave, duration, optional notated type (:quarter/:whole/…)."
  [step octave duration & [type]]
  (cond-> [:note [:pitch [:step (nm step)] [:octave octave]] [:duration duration]]
    type (conj [:type (nm type)])))

(defn rest*
  "A rest of the given duration and optional notated type."
  [duration & [type]]
  (cond-> [:note [:rest] [:duration duration]]
    type (conj [:type (nm type)])))

(defn attributes
  "Measure attributes: {:divisions :fifths :beats :beat-type :clef [sign line]}."
  [{:keys [divisions fifths beats beat-type clef]}]
  (cond-> [:attributes]
    divisions (conj [:divisions divisions])
    fifths    (conj [:key [:fifths fifths]])
    beats     (conj [:time [:beats beats] [:beat-type beat-type]])
    clef      (conj [:clef [:sign (nm (first clef))] [:line (second clef)]])))

(defn measure
  "A measure: a number then its body (attributes / notes as hiccup)."
  [number & body] (into [:measure {:number number}] body))

(defn score-partwise
  "A single-part score document. opts {:part-name :part-id :version}; body is measures."
  [{:keys [part-name part-id version] :or {part-id "P1" version "4.0"}} & measures]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
       (xml/xml
         [:score-partwise {:version version}
          [:part-list [:score-part {:id part-id} [:part-name (or part-name "Music")]]]
          (into [:part {:id part-id}] measures)])))
