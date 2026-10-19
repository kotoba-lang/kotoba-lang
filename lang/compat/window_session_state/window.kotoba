(ns window-session-state.window
  "Window ECS component + management. Restored from the legacy
  kami-engine/kami-os `window.rs` (deleted in kotoba-lang/kami-engine
  PR #82 'Remove Rust workspace from kami-engine') as part of the
  clj-wgsl migration (ADR-2607010930, com-junkawasaki/root).

  Each window was an `hecs` entity with `WindowComponent` + `WindowRect`
  components; here the entity is a plain integer/keyword ID and the two
  components are plain CLJC maps keyed by that ID (same adaptation used
  in kotoba-lang/scene-graph and kotoba-lang/core). The compositor query
  over these (z-ordered rounded-rect render + per-`WindowContent`-variant
  content draw via kami-ui-gpu) is GPU rendering and is intentionally out
  of scope here — this namespace owns only the portable window state:
  config, lifecycle state, content-variant tag, and rect hit-testing.

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU.")

;; ── Window lifecycle state ──────────────────────

(def window-states #{:normal :minimized :maximized})

;; ── Window content variant ──────────────────────
;; Rust `WindowContent` enum -> a `[:tag data]` tuple (or bare keyword
;; for the no-data `:terminal`/`:file-explorer` variants).
;;   [:iframe {:url ...}]
;;   [:kami {:scene-json ...}]
;;   :terminal
;;   :file-explorer
;;   [:agent-chat {:convo-id ...}]

(defn iframe-content [url] [:iframe {:url url}])
(defn kami-content [scene-json] [:kami {:scene-json scene-json}])
(def terminal-content :terminal)
(def file-explorer-content :file-explorer)
(defn agent-chat-content [convo-id] [:agent-chat {:convo-id convo-id}])

;; ── WindowConfig / WindowComponent / WindowRect ─

(defn window-config
  "Configuration for opening a new window."
  [{:keys [app-id title x y w h content]}]
  {:app-id app-id :title title :x x :y y :w w :h h :content content})

(defn window-component-from-config
  "Create a WindowComponent map from a window-config map."
  [config]
  {:app-id (:app-id config)
   :title (:title config)
   :state :normal
   :content (:content config)
   :z-order 0})

(defn window-rect
  [{:keys [x y w h]}]
  {:x x :y y :w w :h h})

(defn window-rect-from-config [config]
  (window-rect config))

(def title-bar-height 32.0)

(defn rect-contains?
  "Check if point (px, py) is inside `rect`."
  [rect px py]
  (and (>= px (:x rect))
       (<= px (+ (:x rect) (:w rect)))
       (>= py (:y rect))
       (<= py (+ (:y rect) (:h rect)))))

(defn title-bar-contains?
  "Title bar hit area (top `title-bar-height` px of window)."
  [rect px py]
  (and (>= px (:x rect))
       (<= px (+ (:x rect) (:w rect)))
       (>= py (:y rect))
       (<= py (+ (:y rect) title-bar-height))))
