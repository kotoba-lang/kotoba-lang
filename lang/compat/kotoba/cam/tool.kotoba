(ns kotoba.cam.tool
  "Cutting tool definitions and tool library management. Ported from
   kami-cam (Rust) `src/tool.rs`.

   A `Tool` is a plain map:
     {:id ... :name \"...\" :tool-type :end-mill :diameter ... :flute-length ...
      :overall-length ... :flute-count ... :corner-radius ... :material :carbide
      :coating \"TiAlN\" | nil}

   `ToolLibrary` is represented as a plain persistent map of `id -> tool`
   (not a mutable struct like the Rust original) — `add`/`remove` are pure
   functions returning `[new-library previous-tool-or-nil]`, matching the
   Rust API's `Option<Tool>` return value without introducing mutable state
   into the domain layer.")

(def tool-types
  #{:end-mill :ball-nose :bull-nose :drill :tap :face-mill :chamfer-mill :lathe})

(def tool-materials
  ;; HSS = High-Speed Steel, CBN = Cubic Boron Nitride, PCD = Polycrystalline
  ;; Diamond.
  #{:hss :carbide :ceramic :cbn :pcd})

(defn empty-library
  "A tool library with no tools."
  []
  {})

(defn add
  "Insert or replace `tool` (keyed by `:id`). Returns `[library' previous]`
   where `previous` is the tool that was replaced, or nil."
  [library tool]
  [(assoc library (:id tool) tool) (get library (:id tool))])

(defn get-tool
  "Look up a tool by numeric id."
  [library id]
  (get library id))

(defn remove-tool
  "Remove a tool by id. Returns `[library' removed-or-nil]`."
  [library id]
  [(dissoc library id) (get library id)])

(defn list-tools
  "All tools, sorted by id."
  [library]
  (sort-by :id (vals library)))

(defn lib-count [library] (count library))

(defn empty-lib? [library] (zero? (count library)))
