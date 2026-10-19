(ns window-session-state.taskbar
  "Taskbar state — agent status list, budget display, consent badge,
  launcher toggle. Restored from the legacy kami-engine/kami-os
  `taskbar.rs` (deleted in kotoba-lang/kami-engine PR #82 'Remove Rust
  workspace from kami-engine') as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  The original rendered a bottom bar (launcher button | window list |
  agent status | budget | clock) via kami-ui-gpu; the GPU layout/draw is
  out of scope here — this namespace owns only the portable taskbar
  state that such a renderer would read.

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU.")

(def agent-statuses #{:active :busy :error :paused :dead})

(defn agent-status-entry
  [{:keys [did name status]}]
  {:did did :name name :status status})

(defn taskbar-state
  "Create an empty taskbar."
  []
  {:agents [] :budget-display "0 GCC" :pending-consent-count 0 :launcher-open false})

(defn update-agents
  "Replace the agent status list."
  [state agents]
  (assoc state :agents agents))

(defn update-budget
  "Update the formatted budget display string."
  [state formatted]
  (assoc state :budget-display formatted))

(defn update-consent-count
  "Update the pending-consent badge count."
  [state count]
  (assoc state :pending-consent-count count))

(defn toggle-launcher
  "Toggle launcher-open state."
  [state]
  (update state :launcher-open not))
