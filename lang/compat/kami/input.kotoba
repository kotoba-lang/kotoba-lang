(ns kami.input
  "L3 — data-first input action maps: a pure layer that turns a set of *held
  keys* into *action axes / events* via a declarative binding table.

  The native `kami-input` crate owns OS event capture (keyboard/mouse/touch/
  gamepad) + the FocusManager. This namespace owns only the *binding* layer:
    (action-axes held-keys binding-table) -> {axis-name float}
  It runs on tiny data (a held-key set + a small binding table), never
  per-element, so it is wasmi-safe to compile via kotoba-clj and run as
  CLJ→WASM on iOS/console (ADR-0040 / clj-wgsl-ledger Phase 1.2).

  Binding table shape:
    {:axes   [{:axis :move-x :positive :d :negative :a :scale 1.0}
              {:axis :move-y :positive :w :negative :s}]
     :actions #{:jump :fire}   ; any held key in this set → action active
     :triggers {:jump :space}} ; edge-triggered (handled via prev-held diff)

  Pure — no IO. A `defsystem` reads the host-held key set (a host import) and
  calls `action-axes` / `active-actions` / `triggered-actions` once per tick."
  (:require [clojure.set :as set]))

(defn- axis-value
  "Compute one axis' float in [-1,1] from held keys. :positive adds +scale,
  :negative adds -scale; both held → 0 (mutual cancellation, standard gamepad
  semantics). Supports :positive-set / :negative-set (multiple keys) too."
  [held {:keys [positive negative positive-set negative-set scale] :or {scale 1.0}}]
  (let [pos? (or (and positive (contains? held positive))
                 (and positive-set (seq (set/intersection held positive-set))))
        neg? (or (and negative (contains? held negative))
                 (and negative-set (seq (set/intersection held negative-set))))]
    (cond
      (and pos? neg?) 0.0
      pos?            (float scale)
      neg?            (float (- scale))
      :else           0.0)))

(defn action-axes
  "Reduce `held` (a set of keys) through `table`'s :axes into a {axis-name float}
  map. Axes not declared in the table are absent from the result."
  [held table]
  (into {} (for [a (:axes table)] [(:axis a) (axis-value held a)])))

(defn active-actions
  "The set of :actions currently held (any key in the action's :keys contributes).
  Returns #{} if none."
  [held table]
  (reduce
   (fn [acc {:keys [action keys]}]
     (let [keys (if (set? keys) keys #{keys})]
       (if (seq (set/intersection held keys))
         (conj acc action) acc)))
   #{} (:actions table)))

(defn triggered-actions
  "Edge-triggered actions: those whose trigger key is in `held` but was NOT in
  `prev-held` (the previous tick's held set). Returns a set of trigger keys.
  A `defsystem` keeps the prev-held set in world state and calls this each tick."
  [held prev-held table]
  (let [trig (:triggers table)]
    (->> trig
         (filter (fn [[_ key]] (and (contains? held key)
                                    (not (contains? prev-held key)))))
         (map val)
         set)))

(defn merge-held
  "Merge a new event's key set into the running held set. `event` is one of:
    {:press #{:a :d}}  → union into held
    {:release #{:a}}   → difference from held
  Pure — returns the new held set; the host owns the mutable accumulator."
  [held event]
  (cond
    (:press event)   (set/union held (:press event))
    (:release event) (set/difference held (:release event))
    :else            held))
