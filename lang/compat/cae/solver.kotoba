(ns cae.solver
  "The CAE solver CONTRACT — a single `solve` multimethod every physics domain
  (aero, crash, motor, echem) and every fidelity backend (reduced-order vs a
  high-fidelity kami-* solver) registers a method on. Dispatch is on
  `[:solver :kind]` in the case, so the SAME case is answered by `:rom-buildup`
  today and `:lbm` (kami-cfd lattice-Boltzmann) tomorrow — caller unchanged.

  Purpose: SOLVER DISPATCH only. A solver method returns a domain result map;
  representation (datoms) and physics math live in datom-clj / vphysics-clj.

  A domain registers like:
    (defmethod cae.solver/solve :rom-buildup [case] {...})

  and is invoked uniformly:
    (cae.solver/solve case)"
  (:require [clojure.string :as str]))

(defmulti solve
  "Solve a CAE `case`; dispatch on (get-in case [:solver :kind])."
  (fn [case] (get-in case [:solver :kind])))

(defmethod solve :default [case]
  (throw (ex-info "no CAE solver registered for this kind"
                  {:kind      (get-in case [:solver :kind])
                   :registered (vec (remove #{:default} (keys (methods solve))))})))

(defn backends
  "The solver kinds currently registered (e.g. #{:rom-buildup :lbm})."
  []
  (set (remove #{:default} (keys (methods solve)))))

(defn registered?
  "Is a backend available for `kind`?"
  [kind]
  (contains? (backends) kind))
