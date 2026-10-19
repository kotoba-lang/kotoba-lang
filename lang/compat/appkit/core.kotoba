(ns appkit.core
  "Desktop / dense-data platform binding on top of kotoba-ui.core (see
  90-docs/adr/2607022800-kotoba-lang-default-uiux-appkit-uikit-interface-fundamentals.md).

  appkit owns no tokens/CSS/component logic. Of kotoba-ui.core's 32
  components, only `panel` (`:surface`/`:elevation`) and `list-view`
  (`:surface`) actually take a surface/elevation opt in liquid-glass-ui v1 —
  every other component (`toolbar`/`nav-bar`/`sheet`/`alert`/`button`/…) has
  a fixed glass look with no per-screen-shape variance point, so appkit wraps
  only those two and re-exports the rest as-is; there is nothing to
  differentiate. Caller-supplied opts always win over these defaults, same
  merge contract as every other kotoba-ui wrap. Mirrors Apple AppKit's role
  against SwiftUI (kotoba-ui.core): screen-shape defaults, not a new
  component catalog. Target consumers: kotoba EDA flow workbench, slides
  editor, itonami.cloud cockpit, kotobase.net console — see the superproject
  ADR's provisional product mapping."
  (:require [kotoba-ui.core :as ui]))

(def default-panel-opts
  "Desktop panes read as flush/embedded, not floating — `:thick` surface for
  legibility over busy dense content, `:flat` elevation (no shadow) because
  panes sit side-by-side rather than stacked."
  {:surface :thick :elevation :flat})

(def default-list-view-opts
  {:surface :thick})

(defn panel
  ([body] (panel body {}))
  ([body opts] (ui/panel body (merge default-panel-opts opts))))

(defn list-view
  ([rows] (list-view rows {}))
  ([rows opts] (ui/list-view rows (merge default-list-view-opts opts))))
