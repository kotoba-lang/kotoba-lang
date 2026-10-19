(ns window-session-state.input-router
  "Focus-aware input routing. Restored from the legacy kami-engine/kami-os
  `input_router.rs` (deleted in kotoba-lang/kami-engine PR #82 'Remove
  Rust workspace from kami-engine') as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  The original was a thin OS-specific wrapper over `kami-input`'s
  generic `FocusManager` (panel focus / modal stack / global overlay).
  That generic focus manager was already restored to CLJC in the
  sibling `kotoba-lang/input` repo as `input/focus-manager` et al
  (`[:panel id]` / `[:modal id]` / `[:global-overlay]` / `[:none]`
  resolution). Rather than duplicate it, this namespace reimplements
  the same tiny state machine locally (kept dependency-free per this
  repo's zero-dep-portable-CLJC scope) using the identical shape and
  semantics, plus the OS-specific sentinel (`consent-modal-id`) and
  method names from `input_router.rs`.

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU.")

;; Sentinel modal ID used for the OS consent modal (matches the
;; original's `push_modal(0)`).
(def consent-modal-id 0)

(defn input-router-state
  "Create input-router state with no focus, empty modal stack, no
  global overlay."
  []
  {:focused nil :modal-stack [] :global-overlay false})

(defn set-focus
  "Set the focused window."
  [state window-id]
  (assoc state :focused window-id))

(defn clear-focus-if
  "Clear focus if `window-id` is currently focused."
  [state window-id]
  (if (= (:focused state) window-id)
    (assoc state :focused nil)
    state))

(defn set-consent-modal
  "Set consent-modal blocking state: push the consent sentinel modal
  when `active?` is true, pop it when false."
  [state active?]
  (if active?
    (update state :modal-stack conj consent-modal-id)
    (update state :modal-stack (fn [ms] (if (seq ms) (pop ms) ms)))))

(defn set-launcher
  "Set launcher overlay (global-overlay) state."
  [state active?]
  (assoc state :global-overlay active?))

(defn resolve-target
  "Resolve where input should go: global overlay > modal stack top >
  focused window > `[:none]`."
  [state]
  (cond
    (:global-overlay state) [:global-overlay]
    (seq (:modal-stack state)) [:modal (peek (:modal-stack state))]
    (some? (:focused state)) [:panel (:focused state)]
    :else [:none]))

(defn focused
  "The currently focused window (ignoring modals/overlays)."
  [state]
  (:focused state))
