(ns window-session-state.file-explorer
  "File explorer — directory-listing view state for an R2/IPFS-backed
  file browser. Restored from the legacy kami-engine/kami-os
  `file_explorer.rs` (deleted in kotoba-lang/kami-engine PR #82 'Remove
  Rust workspace from kami-engine') as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  The original fetched directory entries via Magatama XRPC commands
  (`app.etzhayyim.window-session-state.sync.*`) against R2/IPFS storage; that network IO
  is out of scope here — this namespace owns only the portable view
  state: current path, navigation (into/up), multi-select, sort order,
  and view mode. Callers are expected to populate `:entries` from their
  own XRPC/network layer after a `navigate`/`navigate-up`.

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU."
  (:require [clojure.string :as str]))

(def entry-kinds #{:file :directory :symlink})
(def view-modes #{:grid :list})
(def sort-fields #{:name :size :modified :kind})

(defn file-entry
  [{:keys [name path kind size modified-at mime-type cid]}]
  {:name name :path path :kind kind :size (or size 0)
   :modified-at modified-at :mime-type (or mime-type "") :cid cid})

(defn file-explorer-state
  "Create a file explorer state rooted at \"/\"."
  []
  {:current-path "/" :entries [] :view-mode :list :selected [] :sort-by :name :sort-desc false})

(defn navigate
  "Navigate to `path`, clearing entries/selection (caller repopulates
  `:entries` from its own data source)."
  [state path]
  (assoc state :current-path path :entries [] :selected []))

(defn navigate-up
  "Navigate up one directory level. No-op at root."
  [state]
  (let [path (:current-path state)]
    (if (= path "/")
      state
      (let [pos (str/last-index-of path "/")]
        (if (nil? pos)
          state
          (let [parent (if (zero? pos) "/" (subs path 0 pos))]
            (assoc state :current-path parent :entries [] :selected [])))))))

(defn toggle-select
  "Toggle selection of the entry at `index`."
  [state index]
  (update state :selected
          (fn [sel]
            (if (some #(= % index) sel)
              (vec (remove #(= % index) sel))
              (conj sel index)))))

(defn selected-entries
  "The currently selected FileEntry maps."
  [state]
  (into [] (keep #(get (:entries state) %)) (:selected state)))
