(ns drive.store.memory
  "An `IObjectStore` in a map.

  Portable, no reader conditionals, and the default a test should reach for.
  It is also the honest baseline for a local-first workspace that has not
  chosen a backend yet: everything works and nothing survives the process."
  (:require [drive.object :as object]))

(defn store
  ([] (store {}))
  ([initial]
   (let [state (atom initial)]
     (reify object/IObjectStore
       (-get-object [_ ref] (get @state ref))
       (-put-object [_ ref bytes] (swap! state assoc ref (vec bytes)) nil)
       (-delete-object [_ ref] (swap! state dissoc ref) nil)
       (-object-exists? [_ ref] (contains? @state ref))))))

(defn contents
  "What a store holds. Not part of `IObjectStore` — a real backend cannot
  answer this cheaply and should not be asked to."
  [state-atom]
  @state-atom)
