(ns kotoba-ui.shell.style
  "Structural CSS for the kotoba-ui.shell layout scaffolds.

  Lives in its own namespace (rather than inside kotoba-ui.shell) so that
  kotoba-ui.theme can include `shell-css` in its full-page bundle while
  kotoba-ui.shell requires kotoba-ui.theme for `page` — the same
  components/style split as liquid-glass-ui, and no require cycle.

  Everything here is emitted inside the `kotoba.hig` cascade layer: shell
  rules are structural/base CSS (layout, spacing, readable measure), not
  material, so they belong in shitsuke.hig's layer — and, like the rest of
  the stack, they lose to unlayered app CSS by design (see
  liquid-glass.style's cascade-layer note; the layer order declaration
  `@layer kotoba.hig, kotoba.glass;` is emitted once by kotoba-ui.theme).

  Every color/size value references a `--hig-*` custom property (or a
  `color-mix()` of one) — zero literal colors, so appearance/theme overrides
  flow in via shitsuke.hig's variable emission. The only literals are
  structural layout constants (readable-column max-width, grid card minimum,
  sidebar width/breakpoint), which are lengths, not design tokens.

  Rules are built as EDN `[selector decls]` pairs via `kotoba-lang/css`
  (`css.core`), same as liquid-glass.style — `shell-rules` stays data so
  tests can assert declarations directly instead of regex-scraping.
  Portable .cljc, babashka-safe (no `format`)."
  (:require [css.core :as css]))

(defn class-name
  "Stable class for a shell component or component--modifier, e.g.
  (class-name :hero) => \"kotoba-shell__hero\", (class-name \"app--with-sidebar\")
  => \"kotoba-shell__app--with-sidebar\". Mirrors liquid-glass.style/class-name."
  [component]
  (str "kotoba-shell__" (name component)))

(def readable-max-width
  "Readable single-column measure for section content (~65–75ch of body
  text) — sections center on this by default; `:wide true` opts out."
  "680px")

(def grid-min-width
  "Default minimum card width for the responsive grid (`grid`'s :min opt
  overrides per instance)."
  "260px")

(def sidebar-width
  "Fixed sidebar column width in the two-column app-shell layout."
  "260px")

(def sidebar-breakpoint
  "Viewport width at (and under) which the app-shell sidebar collapses to a
  single column."
  "768px")

(defn shell-rules
  "The shell rule set as EDN data — a vector of `[selector decls-map]` pairs
  (the shape `css.core/css`'s `:rules` consumes), exposed so tests can assert
  against declarations directly."
  []
  [;; --- stack: flex column (or row with --horizontal), 4pt-grid gap -------
   [".kotoba-shell__stack"
    {:display "flex" :flex-direction "column" :gap "var(--hig-spacing-4)"
     :min-width "0"}]
   [".kotoba-shell__stack--horizontal"
    {:flex-direction "row" :align-items "center"}]
   ;; flex children default to min-width:auto and refuse to shrink below
   ;; their content — the same overflow bug the app-shell main guards against.
   [".kotoba-shell__stack > *" {:min-width "0"}]
   [".kotoba-shell__spacer" {:flex "1 1 auto"}]

   ;; --- section: hairline-separated readable column ------------------------
   [".kotoba-shell__section"
    {:box-sizing "border-box" :width "100%"
     :max-width readable-max-width :margin "0 auto"
     :padding "var(--hig-spacing-7) var(--hig-spacing-content-margin)"
     :border-top "var(--hig-hairline) solid var(--hig-color-separator)"}]
   [".kotoba-shell__section--wide" {:max-width "none"}]
   [".kotoba-shell__section-title"
    {:margin "0 0 var(--hig-spacing-4)" :font-weight "600"}]

   ;; --- hero: page-entrance block with a subtle accent wash ----------------
   ;; The wash is color-mix() over the tint token — NOT a literal gradient
   ;; color — so it follows the theme accent and both appearances for free.
   [".kotoba-shell__hero"
    {:box-sizing "border-box" :text-align "center"
     :padding "var(--hig-spacing-10) var(--hig-spacing-content-margin) var(--hig-spacing-8)"
     :background (str "radial-gradient(80% 70% at 50% 0%,"
                      "color-mix(in srgb, var(--hig-color-tint) 12%, transparent) 0%,"
                      "transparent 70%)")}]
   [".kotoba-shell__hero-title"
    {:margin "0 0 var(--hig-spacing-3)" :font-weight "700"}]
   [".kotoba-shell__hero-tagline"
    {:margin "0 auto var(--hig-spacing-6)" :max-width readable-max-width
     :color "var(--hig-color-secondary-label)"}]
   [".kotoba-shell__hero-actions"
    {:display "flex" :justify-content "center" :flex-wrap "wrap"
     :gap "var(--hig-spacing-3)"}]

   ;; --- grid: responsive auto-fill card grid -------------------------------
   [".kotoba-shell__grid"
    {:display "grid"
     :grid-template-columns (str "repeat(auto-fill, minmax(" grid-min-width ", 1fr))")
     :gap "var(--hig-spacing-4)"}]
   ;; grid items also default to min-width:auto; without this a long
   ;; unbroken string in one card blows the whole track out.
   [".kotoba-shell__grid > *" {:min-width "0"}]

   ;; --- app-shell: sticky nav + optional sidebar + main --------------------
   ;; Safe-area insets (HIG: content never sits under the notch / home
   ;; indicator): left/right cover landscape notches, bottom covers the home
   ;; indicator; all resolve to 0px on devices without insets. The top edge
   ;; belongs to the glass nav material (liquid-glass nav-bar), not the shell.
   [".kotoba-shell__app"
    {:min-height "100vh" :display "flex" :flex-direction "column"
     :padding-left "env(safe-area-inset-left, 0px)"
     :padding-right "env(safe-area-inset-right, 0px)"
     :padding-bottom "env(safe-area-inset-bottom, 0px)"}]
   ;; Dynamic-viewport progressive enhancement: bare 100vh overshoots under
   ;; mobile browser chrome (the URL bar); browsers that know dvh use it,
   ;; older ones keep the 100vh fallback above.
   [".kotoba-shell__app" {:min-height "100dvh"}]
   [".kotoba-shell__app-nav" {:position "sticky" :top "0" :z-index "10"}]
   [".kotoba-shell__app-body"
    {:flex "1" :display "grid" :grid-template-columns "minmax(0, 1fr)"
     :align-items "start"}]
   [".kotoba-shell__app--with-sidebar .kotoba-shell__app-body"
    {:grid-template-columns (str sidebar-width " minmax(0, 1fr)")}]
   [".kotoba-shell__app-sidebar"
    {:box-sizing "border-box"
     :padding "var(--hig-spacing-4) var(--hig-spacing-content-margin)"
     :border-right "var(--hig-hairline) solid var(--hig-color-separator)"}]
   ;; min-width:0 + overflow-wrap: the exact grid-item overflow bug that
   ;; shipped in production (a wide <pre>/long URL in main stretched the
   ;; grid track past the viewport) — guarded here so apps never re-learn it.
   [".kotoba-shell__app-main" {:min-width "0" :overflow-wrap "break-word"}]])

(defn- responsive-css
  "The sidebar-collapse media query: under `sidebar-breakpoint` the
  two-column app-shell becomes a single column and the sidebar's separator
  rotates from right edge to bottom edge."
  []
  (css/media (str "(max-width: " sidebar-breakpoint ")")
             [[".kotoba-shell__app--with-sidebar .kotoba-shell__app-body"
               {:grid-template-columns "minmax(0, 1fr)"}]
              [".kotoba-shell__app-sidebar"
               {:border-right "none"
                :border-bottom "var(--hig-hairline) solid var(--hig-color-separator)"}]]))

(defn shell-css
  "All kotoba-shell__* structural rules (plus the sidebar-collapse media
  query) wrapped in `@layer kotoba.hig { ... }`. Emitted as part of
  kotoba-ui.theme/theme-css; a page that composes its own CSS appends this
  after shitsuke.hig's bundle (the layer-order declaration must already have
  been emitted once, first — see shitsuke.hig/layer-order-css)."
  []
  (str "@layer kotoba.hig {\n"
       (css/css {:rules (shell-rules)})
       "\n" (responsive-css)
       "\n}"))
