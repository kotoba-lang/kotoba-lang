(ns aero.bridge
  "The design-loop closure: a computed Cd replaces vehicle-design-actor's FIXED
  Cd prior, and the range / battery size move accordingly. The aero force and
  range-sensitivity math now live in the shared `vphysics-clj`; datafication in
  the shared `datom-clj`. This namespace just composes solve → effect → datoms."
  (:require [aero.solver :as solver]
            [vphysics.core :as phys]
            [datom.core :as d]))

(defn range-effect
  "Range multiplier from a computed solve vs the assumed `prior-cd`
  (delegates to vphysics)."
  [{:keys [Cd]} prior-cd & [opts]]
  (phys/range-effect Cd prior-cd opts))

(defn run
  "Solve a case, compute its range effect vs `prior-cd`, and datafy both.
  Returns {:solve .. :effect .. :tx .. :datoms .. :datom-count ..}."
  [case prior-cd]
  (let [s   (solver/solve case)
        eff (range-effect s prior-cd)
        cid (:case/id case)
        ent (d/entity "aero" :AeroCase cid
                      {:geometry (:geometry/ref case)
                       :solver   (name (:solver s))
                       :Cd       (Math/round (* 1.0e4 (:Cd s)))
                       :CdA      (Math/round (* 1.0e4 (:CdA s)))
                       :Re       (Math/round (double (:Re s)))
                       :priorCd  (Math/round (* 1.0e4 prior-cd))
                       :rangeMultPct (Math/round (* 100.0 (:range-mult eff)))})
        bks (map (fn [[k v]] (d/entity "aero" :AeroComponent (str cid "/" (name k))
                                       {:case cid :component (name k)
                                        :cdCount (Math/round (* 1.0e4 v))}))
                 (:breakdown s))
        led (d/log (cons ent bks))]
    {:solve s :effect eff
     :tx (:tx led) :datoms (:datoms led) :datom-count (:count led)}))
