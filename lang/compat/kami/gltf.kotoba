(ns kami.gltf
  "glTF 2.0 as data — 'hiccup for runtime scenes'. glTF is the runtime 3D-scene standard and is itself
   JSON, so it maps onto EDN directly; this injects the mandatory `asset` block and offers small node/
   material constructors so a scene graph is composable data you fork and diff. Sits next to
   kami.webgpu on the web/GPU axis (EDN scene → glTF → browser). `.cljc`, serialized via kami.json.

   glTF's references are integer indices into top-level arrays (a node's :mesh is an index into
   :meshes, a primitive's :material indexes :materials) — this is a STRUCTURAL emitter; the index
   bookkeeping is the author's. Helpers fill common defaults:
     (material \"red\" [1 0 0 1])         → a pbrMetallicRoughness material
     (node {:name \"root\" :mesh 0 :translation [0 1 0]})
     (gltf {:generator \"kami\"} {:scene 0 :scenes [{:nodes [0]}] :nodes […] :meshes […]})  ⇒ JSON"
  (:require [kami.json :as json]))

(defn material
  "A PBR metallic-roughness material: name + RGBA base-colour factor."
  [name base-color] {:name name :pbrMetallicRoughness {:baseColorFactor base-color}})

(defn node
  "A scene node — a map passed through (drops nil values so optional fields stay absent)."
  [m] (into {} (remove (comp nil? val) m)))

(defn gltf
  "Serialize a glTF document to JSON, injecting the required asset block.
   opts: {:generator :copyright}; doc: the remaining top-level glTF object (scenes/nodes/meshes/…)."
  [{:keys [generator copyright]} doc]
  (json/json (merge {:asset (cond-> {:version "2.0"}
                              generator (assoc :generator generator)
                              copyright (assoc :copyright copyright))}
                    doc)))
