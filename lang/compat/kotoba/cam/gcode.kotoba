(ns kotoba.cam.gcode
  "G-code generation from toolpath segments with post-processor
   configuration. Ported from kami-cam (Rust) `src/gcode.rs`.

   `generate-gcode` produces valid G-code with:
   - Program header (O-number, units, coordinate system, absolute mode)
   - Tool changes (T/M06) when tool-id changes between segments
   - Spindle start (M03) / stop (M05)
   - Coolant on (M08) / off (M09)
   - Motion commands: G00 (rapid), G01 (linear), G02 (arc CW), G03 (arc CCW)
   - Program end (M30)"
  (:require [clojure.string :as str]
            [kotoba.cam.util :as util]))

(def machine-types #{:mill-3axis :mill-4axis :mill-5axis :lathe :laser-cutter :printer-3d})
(def post-processors #{:fanuc :haas :siemens :heidenhain :linuxcnc :marlin :grbl})
(def gcode-units #{:millimeters :inches})
(def coordinate-systems #{:g54 :g55 :g56 :g57 :g58 :g59})

(defn coordinate-system->str [cs]
  (case cs
    :g54 "G54" :g55 "G55" :g56 "G56" :g57 "G57" :g58 "G58" :g59 "G59"))

;; Canonical source: resources/kotoba/cam/gcode-defaults.edn (kept in sync,
;; see test/kotoba/cam/materials_edn_test.clj). Embedded as a literal here so
;; this domain namespace has no file IO.
(def default-config
  {:machine-type :mill-3axis
   :post-processor :fanuc
   :units :millimeters
   :safe-height 5.0
   :coordinate-system :g54
   :program-number 1
   :coolant true})

(defn- header-lines [config]
  [ "%"
    (str "O" (util/pad-int (:program-number config) 4))
    "(KAMI CAM — generated G-code)"
    (case (:units config)
      :millimeters "G21 (metric)"
      :inches "G20 (imperial)")
    "G90 (absolute)"
    (coordinate-system->str (:coordinate-system config))
    "G40 (cancel cutter comp)"
    "G49 (cancel tool length offset)"
    (str "G00 Z" (util/fixed (:safe-height config) 4))])

(defn- motion-line [seg]
  (let [{:keys [segment-type start end feed-rate center]} seg
        x (util/fixed (:x end) 4)
        y (util/fixed (:y end) 4)
        z (util/fixed (:z end) 4)]
    (case segment-type
      :rapid (str "G00 X" x " Y" y " Z" z)
      :linear (str "G01 X" x " Y" y " Z" z " F" (util/fixed feed-rate 1))
      :arc-cw (when center
                (let [i (util/fixed (- (:x center) (:x start)) 4)
                      j (util/fixed (- (:y center) (:y start)) 4)]
                  (str "G02 X" x " Y" y " Z" z " I" i " J" j " F" (util/fixed feed-rate 1))))
      :arc-ccw (when center
                 (let [i (util/fixed (- (:x center) (:x start)) 4)
                       j (util/fixed (- (:y center) (:y start)) 4)]
                   (str "G03 X" x " Y" y " Z" z " I" i " J" j " F" (util/fixed feed-rate 1)))))))

(defn- body-reducer [config state seg]
  (let [{:keys [lines current-tool spindle-on]} state
        tool-id (:tool-id seg)
        coolant (:coolant config)
        safe-height (:safe-height config)]
    (if (not= current-tool tool-id)
      (let [lines (if spindle-on
                    (let [lines (conj lines "M05 (spindle stop)")]
                      (if coolant (conj lines "M09 (coolant off)") lines))
                    lines)
            lines (conj lines (str "G00 Z" (util/fixed safe-height 4)))
            lines (conj lines (str "T" (util/pad-int tool-id 2) " M06 (tool change)"))
            lines (conj lines "M03 S10000 (spindle CW)")
            lines (if coolant (conj lines "M08 (coolant on)") lines)]
        {:lines (conj lines (motion-line seg)) :current-tool tool-id :spindle-on true})
      {:lines (conj lines (motion-line seg)) :current-tool current-tool :spindle-on spindle-on})))

(defn generate-gcode
  "Generate a G-code string from toolpath `segments` and `config`
   (defaults to `default-config`)."
  ([segments] (generate-gcode segments default-config))
  ([segments config]
   (let [coolant (:coolant config)
         safe-height (:safe-height config)
         init {:lines (header-lines config) :current-tool nil :spindle-on false}
         {:keys [lines spindle-on]} (reduce (partial body-reducer config) init segments)
         lines (if spindle-on
                 (let [lines (conj lines "M05 (spindle stop)")]
                   (if coolant (conj lines "M09 (coolant off)") lines))
                 lines)
         lines (-> lines
                   (conj (str "G00 Z" (util/fixed safe-height 4) " (retract)"))
                   (conj "G00 X0.0000 Y0.0000 (return to origin)")
                   (conj "M30 (program end)")
                   (conj "%"))]
     (str (str/join "\n" lines) "\n"))))
