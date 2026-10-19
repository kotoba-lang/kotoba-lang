(ns kami.pipelines
  "Render pipelines as data.

   native-pipelines is the shared EDN table for open-world pipeline fields, the
   same way the web side describes its frame in kami.webgpu/default-graph.
   Repository-local gates keep the committed EDN fixture in sync with this
   source. Native adapters can consume this table from their own repositories.

   Fields per pipeline: :shader (the scene_*.wgsl), :cull (:back/:front/:none), :depth-write (bool),
   :depth-compare (:less/:less-equal/…), :blend (:none/:alpha). Vertex layouts are the remaining
   field to fold in."
  (:require [clojure.string :as str]))

(def native-pipelines
  "The 8 open-world render pipelines as data."
  {:terrain    {:shader "scene_terrain"    :cull :back :depth-write true  :depth-compare :less       :blend :none}
   :sky        {:shader "scene_sky"        :cull :none :depth-write false :depth-compare :less-equal :blend :none}
   :vegetation {:shader "scene_vegetation" :cull :none :depth-write true  :depth-compare :less       :blend :alpha}
   :character  {:shader "scene_character"  :cull :back :depth-write true  :depth-compare :less       :blend :none}
   :water      {:shader "scene_water"      :cull :none :depth-write false :depth-compare :less       :blend :alpha}
   :voxel      {:shader "scene_voxel"      :cull :back :depth-write true  :depth-compare :less       :blend :none}
   :particle   {:shader "scene_particle"  :cull :none :depth-write false :depth-compare :less       :blend :alpha}
   :atlas      {:shader "scene_atlas"     :cull :none :depth-write false :depth-compare :less       :blend :alpha}})

;; ── parse the hand-written Rust into the same shape, to gate drift ────────────────────────────────
(defn- kw-cull [s] (case s "Back" :back "Front" :front :none))
(defn- kw-cmp  [s] (case s "Less" :less "LessEqual" :less-equal "Greater" :greater
                           "GreaterEqual" :greater-equal "Equal" :equal "Always" :always (keyword (str/lower-case s))))

(defn parse-rust
  "Extract each native pipeline's varying fields from scene_pipelines.rs, keyed by shader. Slices the
   source into per-pipeline blocks at each shader include_str!, then takes each block's first cull/
   depth fields — so missing/inline fields align to the right pipeline (a global zip mis-aligns)."
  [src]
  (let [blocks (rest (str/split src #"include_str!\(\"shaders/"))]
    (into {}
      (for [b blocks
            :let [sh (second (re-find #"^(scene_\w+)\.wgsl" b))]
            :when sh]
        [(keyword (str/replace sh "scene_" ""))
         {:shader sh
          :cull (if-let [m (re-find #"cull_mode:\s*Some\(wgpu::Face::(\w+)\)" b)] (kw-cull (second m)) :none)
          :depth-write (= "true" (second (re-find #"depth_write_enabled:\s*(true|false)" b)))
          :depth-compare (kw-cmp (second (re-find #"depth_compare:\s*wgpu::CompareFunction::(\w+)" b)))
          :blend (if (= "None" (second (re-find #"blend:\s*(None|Some)" b))) :none :alpha)}]))))
