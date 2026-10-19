(ns vdesign.motionplan
  "Extends `vdesign.process`'s real BOM + 4D assembly-order sequence
  (`vdesign.process/plan`'s `:assembly`, the giemon-factory
  `construction.order.json :seq` pattern) into an actual ordered list of
  Cartesian waypoints, one per assembly station, walking the SAME
  station order the real process plan already produces (ADR-2607151600).

  Honest scope: this is a WAYPOINT LIST — a plausible, honestly
  simplified station layout (stations placed at a fixed pitch along a
  straight assembly line, working height derived from the design's own
  real packaging-envelope dims) — NOT an inverse-kinematics solver, NOT
  a trajectory optimizer, and it does not drive any real robot
  controller. `:tool-orientation` is a fixed 'straight down' approach
  vector, not a solved end-effector pose."
  (:require [vdesign.process :as process]
            [vdesign.cad :as cad]))

(def ^:const station-pitch-m
  "Nominal spacing between adjacent assembly stations along the line
  (m) — a plausible, round figure for an automotive final-assembly
  line, honestly NOT derived from any real facility's actual layout."
  5.0)

(def ^:const default-tool-orientation
  "Fixed straight-down tool-approach vector — NOT a solved end-effector
  orientation (this namespace is not an IK solver)."
  [0.0 0.0 -1.0])

(def ^:const default-working-height-m
  "Fallback working height (m) when `design` carries no `:geometry`
  (older/hand-rolled callers — mirrors `vdesign.process`'s own
  geometry-optional handling)."
  0.75)

(defn- working-height-m
  "Half the design's own real tessellated packaging-envelope height
  (`vdesign.cad/envelope-dims-mm`) — a plausible fixed working height
  for every station, not a per-part/per-operation solved height."
  [geometry]
  (if geometry
    (/ (:height-mm (cad/envelope-dims-mm geometry)) 2000.0)
    default-working-height-m))

(defn motion-plan-for
  "Ordered Cartesian waypoint list, one per `vdesign.process/plan`'s real
  assembly-order station (same order, same station/step names, same
  `:seq` numbers), laid out along a straight line at `station-pitch-m`
  spacing:

    [{:seq :step :station :waypoint [x y z] :tool-orientation [dx dy dz]} ...]

  x = (station-index) * `station-pitch-m`; y = 0 (line centerline); z =
  `working-height-m`. Deterministic: the same `design` (same
  `:powertrain`, same `:geometry`, same everything `vdesign.process/plan`
  reads) always produces the same plan — `vdesign.process/plan` is
  itself pure, and no randomness is introduced here."
  [{:keys [geometry] :as design}]
  (let [stations (:assembly (process/plan design))
        z (working-height-m geometry)]
    (mapv (fn [i {:keys [seq step station]}]
            {:seq seq :step step :station station
             :waypoint [(* i station-pitch-m) 0.0 z]
             :tool-orientation default-tool-orientation})
          (range (count stations))
          stations)))
