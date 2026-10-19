(ns vrm.firstperson
  "FirstPerson — resolve VRM first-person mesh visibility per camera view.
  Restored from `kami-vrm/src/firstperson.rs` (kotoba-lang/kami-engine,
  deleted PR #82) as part of the clj-wgsl migration (ADR-2607010930,
  com-junkawasaki/root).

  `kami-vrm` parses the VRM first-person mesh annotations (each mesh node
  tagged `:auto` / `:both` / `:third-person-only` / `:first-person-only`,
  see `vrm.parse`) but applies nothing. This is the runtime resolver (the
  `@pixiv/three-vrm` `VRMFirstPerson` analogue): given the active camera
  perspective, it answers which mesh nodes are visible.")

(defn node-visible?
  "Is a node with annotation `flag` visible in `view` (`:first-person` or
  `:third-person`)? `:auto` defaults to visible in both views."
  [flag view]
  (case flag
    :both true
    :auto true
    :third-person-only (= view :third-person)
    :first-person-only (= view :first-person)
    true))

(defn new-resolver
  "Build a resolver from a (nilable) parsed `VrmFirstPerson` map."
  [fp]
  {:fp fp})

(defn visible?
  "Visibility of a mesh `node` in `view`. Nodes without an annotation (and
  avatars without a first-person block) default to visible."
  [resolver node view]
  (if-let [fp (:fp resolver)]
    (if-let [a (some #(when (= (:node %) node) %) (:mesh-annotations fp))]
      (node-visible? (:annotation-type a) view)
      true)
    true))

(defn hidden-nodes
  "The set (vector) of node indices to cull (hidden) in `view`."
  [resolver view]
  (if-let [fp (:fp resolver)]
    (vec (keep (fn [a] (when-not (node-visible? (:annotation-type a) view) (:node a)))
               (:mesh-annotations fp)))
    []))
