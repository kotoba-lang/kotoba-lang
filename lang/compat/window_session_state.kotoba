(ns window-session-state
  "KAMI OS desktop — top-level ECS-world orchestration tying together
  window management, the compositor, input routing, taskbar, launcher,
  and notifications. Restored from the legacy kami-engine/kami-os
  `lib.rs` (deleted in kotoba-lang/kami-engine PR #82 'Remove Rust
  workspace from kami-engine') as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  ## Architecture

  - **ECS (hecs) → plain maps**: the original held Windows,
    Notifications, and TaskbarItems as `hecs::World` entities. There is
    no portable equivalent to a native ECS, so windows are represented
    here as plain integer window IDs used as map keys in a `:windows`
    map on the desktop (same entity-as-plain-ID adaptation used
    successfully in `kotoba-lang/scene-graph` and `kotoba-lang/core`).
  - **Compositor**: z-ordered window state — ported in `window-session-state.compositor`.
    Actual wgpu draw calls are GPU rendering and out of scope.
  - **Input router**: focus-aware dispatch state — ported in
    `window-session-state.input-router` (mirrors the `[:panel id]`/`[:modal id]`/
    `[:global-overlay]`/`[:none]` resolution pattern already
    established in the sibling `kotoba-lang/input` restoration).
  - **Bridge**: `kami-bridge` (native OS input capture) and `kami-knp`
    (device mesh) are native-substrate dependencies with no portable
    CLJC equivalent — entirely out of scope. Callers on the native
    substrate are expected to translate native input into the abstract
    events this namespace and `window-session-state.input-router` consume.

  The original's `GameClock` (fixed 30fps compositor tick) is
  reimplemented locally in `advance` as a simple portable accumulator,
  since `kami-core::time::GameClock` is a separate not-yet-restored
  crate; this preserves the original's fixed-timestep tick-counting
  semantics without pulling in unrelated native timing code.

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU."
  (:require [window-session-state.compositor :as compositor]
            [window-session-state.input-router :as input-router]
            [window-session-state.taskbar :as taskbar]
            [window-session-state.launcher :as launcher]
            [window-session-state.notification :as notification]
            [window-session-state.window :as window]))

(def default-tick-ns
  "Fixed-timestep interval for a 30fps compositor tick, in nanoseconds."
  (long (/ 1000000000 30)))

(defn window-session-state-desktop
  "Create a new OS desktop with default 30fps compositor clock, no open
  windows, and fresh compositor/input-router/taskbar/launcher/
  notification sub-states."
  []
  {:windows {}
   :next-window-id 1
   :clock {:accumulated-ns 0 :ticks 0}
   :compositor (compositor/compositor-state)
   :input-router (input-router/input-router-state)
   :taskbar (taskbar/taskbar-state)
   :launcher (launcher/launcher-state)
   :notifications (notification/notification-queue)})

(defn open-window
  "Open a new window from `config` (an `window-session-state.window/window-config` map).
  Returns `[desktop' window-id]`."
  [desktop config]
  (let [id (:next-window-id desktop)
        component (window/window-component-from-config config)
        rect (window/window-rect-from-config config)]
    [(-> desktop
         (assoc-in [:windows id] {:component component :rect rect})
         (update :next-window-id inc)
         (update :compositor compositor/bring-to-front id)
         (update :input-router input-router/set-focus id))
     id]))

(defn close-window
  "Close a window by ID. If the closed window held input focus, the input
  router is re-synced to whichever window the compositor falls focus
  through to (the new front-most window, or nil) rather than merely
  clearing focus -- keeping compositor and input-router focus in
  agreement, same as open-window/focus-window always setting both
  together."
  [desktop window-id]
  (let [desktop (-> desktop
                     (update :windows dissoc window-id)
                     (update :compositor compositor/remove-window window-id))]
    (if (= window-id (input-router/focused (:input-router desktop)))
      (update desktop :input-router input-router/set-focus
              (compositor/focused-window (:compositor desktop)))
      desktop)))

(defn focus-window
  "Focus a window by ID (bring to front + set input focus)."
  [desktop window-id]
  (-> desktop
      (update :compositor compositor/bring-to-front window-id)
      (update :input-router input-router/set-focus window-id)))

(defn show-notification
  "Push a notification toast (5000ms default duration, matching the
  original)."
  [desktop title body level]
  (update desktop :notifications notification/push-toast! title body level 5000))

(defn show-consent-modal
  "Push a consent modal. Returns `[desktop' request-id]`."
  [desktop request]
  (let [[queue' id] (notification/push-consent (:notifications desktop) request)]
    [(assoc desktop :notifications queue') id]))

(defn advance
  "Advance the compositor clock by `elapsed-ns`. Returns `[desktop'
  ticks-simulated]` at the fixed 30fps timestep."
  [desktop elapsed-ns]
  (let [acc (+ (:accumulated-ns (:clock desktop)) elapsed-ns)
        ticks (long (quot acc default-tick-ns))
        rem (- acc (* ticks default-tick-ns))]
    [(update desktop :clock
             (fn [c] (assoc c :accumulated-ns rem :ticks (+ (:ticks c) ticks))))
     ticks]))
