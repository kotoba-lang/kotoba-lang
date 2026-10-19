(ns kami.ecs
  "L3 — in-memory ECS store (the tick-fast projection of Datomic).

  A scene-snapshot (`kami.scene/build-snapshot`) is loaded into an entity index +
  per-component-key sets so a system iterating \"all (transform mesh)\" filters by
  set intersection rather than scanning every entity (ARCHITECTURE.md §6).

  This core keeps components as plain maps/vectors (correctness-first, fully
  portable .cljc). The browser backend may, as an optimization, additionally pack
  hot transform columns into a Float32Array for zero-copy upload (§9) — that is a
  representation detail layered on top, not required for correctness.

  Datomic is consulted only at load/save boundaries; a frame never queries it."
  (:require [clojure.set :as set]))

(defn world
  "Create an empty ECS world."
  []
  {:entities {}        ; eid → component-map
   :by-key   {}        ; component-key → #{eid}
   :basis-t  nil       ; snapshot's Datalog t, for as-of round-trip undo
   :origin   {}        ; eid → component-map at load time (for ->tx diffing)
   :dirty    #{}       ; eids changed/added since load
   :removed  #{}})     ; eids removed since load

(defn- index-keys
  "Add `eid`'s component keys into the :by-key index."
  [by-key eid comp-map]
  (reduce (fn [m k] (update m k (fnil conj #{}) eid))
          by-key
          (remove #{:kami/eid} (keys comp-map))))

(defn- deindex-keys
  [by-key eid comp-map]
  (reduce (fn [m k] (let [s (disj (get m k #{}) eid)]
                      (if (seq s) (assoc m k s) (dissoc m k))))
          by-key
          (remove #{:kami/eid} (keys comp-map))))

(defn load-snapshot
  "Project a `kami.scene` snapshot into a fresh ECS world. Pure: snapshot in,
  world out. Records :basis-t and a copy of every entity as :origin for later
  diffing in `->tx`. Runs in the browser."
  [snapshot]
  (let [ents (into {} (map (juxt :kami/eid identity)) (:snapshot/entities snapshot))]
    {:entities ents
     :by-key   (reduce-kv index-keys {} ents)
     :basis-t  (:snapshot/t snapshot)
     :origin   ents
     :dirty    #{}
     :removed  #{}}))

(defn query
  "Return a seq of [eid component-map] for entities possessing ALL `component-keys`.
  Intersects the per-key eid sets, so cost scales with the smallest set, not the
  world size. The ECS read primitive systems use each tick."
  [world component-keys]
  (let [ks (seq component-keys)]
    (if-not ks
      (seq (:entities world))
      (let [sets (map #(get-in world [:by-key %] #{}) ks)
            eids (reduce set/intersection (first sets) (rest sets))]
        (map (fn [e] [e (get-in world [:entities e])]) eids)))))

(defn get-entity
  "Return the component-map for `eid` (or nil)."
  [world eid]
  (get-in world [:entities eid]))

(defn add
  "Add/replace an entity (component map, must carry :kami/eid) at runtime.
  Returns updated world; marks the entity :dirty."
  [world components]
  (let [eid (or (:kami/eid components)
                (throw (ex-info "ecs/add: missing :kami/eid" {:components components})))
        prev (get-in world [:entities eid])]
    (-> world
        (assoc-in [:entities eid] components)
        (update :by-key #(-> % (deindex-keys eid (or prev {})) (index-keys eid components)))
        (update :dirty conj eid)
        (update :removed disj eid))))

(defn set-component
  "Set one component on `eid`; returns updated world and marks it :dirty."
  [world eid k v]
  (when-not (get-in world [:entities eid])
    (throw (ex-info "ecs/set-component: no such entity" {:eid eid})))
  (-> world
      (assoc-in [:entities eid k] v)
      (update :by-key index-keys eid {k v})
      (update :dirty conj eid)))

(defn remove-entity
  "Remove `eid` from the world; returns updated world. Tracked for retraction in
  `->tx`."
  [world eid]
  (let [prev (get-in world [:entities eid])]
    (cond-> world
      prev (-> (update :entities dissoc eid)
               (update :by-key deindex-keys eid prev)
               (update :dirty disj eid)
               (update :removed conj eid)))))

(defn ->tx
  "Diff the world against its loaded :origin and emit Datalog tx-data for the
  **save path** (§6): changed/added entities as upserts (keyed by :kami/eid),
  removed entities as [:db/retractEntity [:kami/eid …]]. Only dirty/removed
  entities are emitted. Hand the result to `kami.db/transact!`."
  [world]
  (let [upserts (for [eid (:dirty world)
                      :let [cur (get-in world [:entities eid])]
                      :when (and cur (not= cur (get-in world [:origin eid])))]
                  cur)
        retracts (for [eid (:removed world)] [:db/retractEntity [:kami/eid eid]])]
    (vec (concat upserts retracts))))

(defn mark-saved
  "After a successful transact, fold the dirty set into :origin and clear it.
  `new-t` is the post-transact basis-t (becomes the new undo anchor)."
  [world new-t]
  (assoc world
         :origin  (:entities world)
         :basis-t new-t
         :dirty   #{}
         :removed #{}))
