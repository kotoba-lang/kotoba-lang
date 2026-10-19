(ns window-session-state.compositor
  "Window compositor state — z-order, focus, and drag tracking. Restored
  from the legacy kami-engine/kami-os `compositor.rs` (deleted in
  kotoba-lang/kami-engine PR #82 'Remove Rust workspace from
  kami-engine') as part of the clj-wgsl migration (ADR-2607010930,
  com-junkawasaki/root).

  The original ran once per frame to sort windows by z-order and drive
  wgpu draw calls (desktop background, per-window shadow/rect/title-bar/
  content, and overlay rendering via kami-ui-gpu + kami-text). All of
  that GPU rendering is intentionally out of scope here (it lives in the
  native substrate, not portable CLJC) — this namespace owns only the
  portable z-stack / focus / drag *state machine* that the renderer
  would consume as its render-order and hit-test input.

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU.")

(def default-desktop-width 1920.0)
(def default-desktop-height 1080.0)
(def default-taskbar-height 48.0)

(defn compositor-state
  "Create compositor state with default 1920x1080 desktop."
  []
  {:z-stack []
   :focused nil
   :drag nil
   :desktop-width default-desktop-width
   :desktop-height default-desktop-height
   :taskbar-height default-taskbar-height})

(defn bring-to-front
  "Bring `window-id` to the front of the z-stack and focus it."
  [state window-id]
  (-> state
      (update :z-stack (fn [zs] (into [window-id] (remove #(= % window-id)) zs)))
      (assoc :focused window-id)))

(defn remove-window
  "Remove `window-id` from the z-stack. If it was focused, focus falls
  through to the new front-most window (or nil)."
  [state window-id]
  (let [state (update state :z-stack (fn [zs] (into [] (remove #(= % window-id)) zs)))]
    (if (= (:focused state) window-id)
      (assoc state :focused (first (:z-stack state)))
      state)))

(defn focused-window [state] (:focused state))

(defn z-stack
  "The z-stack (front-to-back order) for rendering."
  [state]
  (:z-stack state))

(defn start-drag
  "Start a drag operation on `window-id` with the pointer offset from
  the window's origin."
  [state window-id offset-x offset-y]
  (assoc state :drag {:window-id window-id :offset-x offset-x :offset-y offset-y}))

(defn update-drag
  "Given the current mouse position, return `[window-id new-x new-y]`
  if a drag is in progress, else nil."
  [state mouse-x mouse-y]
  (when-let [d (:drag state)]
    [(:window-id d) (- mouse-x (:offset-x d)) (- mouse-y (:offset-y d))]))

(defn end-drag [state] (assoc state :drag nil))

(defn usable-height
  "Usable desktop area height (excludes taskbar)."
  [state]
  (- (:desktop-height state) (:taskbar-height state)))
