(ns kami.audio
  "hiccup for sound — a cue bank described as EDN, synthesized via the Web Audio API.
   No asset files: each cue is a tiny data recipe (waveform + pitch sweep + envelope), so
   the whole soundscape is data you can store as datoms, fork, and rebind to events.

     {:fire {:wave \"square\" :freq 320 :to 120 :dur 0.09 :gain 0.18}
      :jump {:wave \"sine\"   :freq 300 :to 620 :dur 0.14 :gain 0.18}}

   Browsers suspend audio until a user gesture — call (resume!) from a click/keydown.
   The same EDN bank drives any executor (Web Audio here; a native mixer reads the data).")

(def default-bank
  {:fire {:wave "square"   :freq 320 :to 120 :dur 0.09 :gain 0.16}
   :jump {:wave "sine"     :freq 300 :to 640 :dur 0.14 :gain 0.16}
   :hit  {:wave "triangle" :freq 180 :to 60  :dur 0.18 :gain 0.20}
   :pick {:wave "sine"     :freq 520 :to 880 :dur 0.10 :gain 0.14}})

(defonce ^:private ctx* (atom nil))
(defonce played (atom 0))   ;; for verification: how many cues have been scheduled
(defn- ac [] (or @ctx* (reset! ctx* (js/AudioContext.))))

(defn resume!
  "Resume the AudioContext (call from a user gesture so sound is allowed to play)."
  [] (let [a (ac)] (when (= (.-state a) "suspended") (.resume a)) a))

(defn play!
  "Play a cue — a keyword into `bank`, or an inline cue map — via Web Audio synthesis.
   Returns true if a cue was scheduled."
  [bank cue]
  (let [c (if (map? cue) cue (get bank cue))]
    (boolean
      (when c
        (let [a (ac) t (.-currentTime a)
              osc (.createOscillator a) g (.createGain a)
              dur (or (:dur c) 0.1) gain (or (:gain c) 0.2)]
          (set! (.-type osc) (or (:wave c) "sine"))
          (.setValueAtTime (.-frequency osc) (or (:freq c) 440) t)
          (when (:to c) (.exponentialRampToValueAtTime (.-frequency osc) (max 1 (:to c)) (+ t dur)))
          (.setValueAtTime (.-gain g) gain t)
          (.exponentialRampToValueAtTime (.-gain g) 0.0001 (+ t dur))
          (.connect osc g) (.connect g (.-destination a))
          (.start osc t) (.stop osc (+ t dur))
          (swap! played inc)
          true)))))
