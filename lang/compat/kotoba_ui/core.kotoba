(ns kotoba-ui.core
  "kotoba-lang default UI/UX design — single require point.

  Product repos that want kotoba-lang's default design (see
  90-docs/adr/2607022800-kotoba-lang-default-uiux-appkit-uikit-interface-fundamentals.md)
  require only `kotoba-ui.core` and get, in one namespace:
  shitsuke's dual-render hiccup contract + liquid-glass-ui's material tokens/
  style/29 components + kotoba-ui's own shell (HIG layout scaffolds) and
  theme (one-map styling entry) layers. Every var below is a direct alias of
  a shitsuke, liquid-glass, or kotoba-ui.shell/theme var — see the source
  namespace's docstring for behavior — except `->page`, a one-line
  composition (doctype + ->html over shell/page). Adding a component/token
  here means adding the alias, nothing else — see docs/adr/0001-kotoba-ui.md.
  Before building any frontend on this, read docs/agent-guide.md.

  Not to be confused with the pre-existing `orgs/kotoba-lang/ui` repo
  (package `kotoba.ui`), which is kami-engine's unrelated WebGPU HUD overlay
  renderer and has no connection to this design system."
  (:require [shitsuke.hiccup :as hiccup]
            [liquid-glass.tokens :as tokens]
            [liquid-glass.style :as style]
            [liquid-glass.components :as glass]
            [kotoba-ui.theme :as theme]
            [kotoba-ui.shell :as shell]
            [kotoba-ui.shell.style :as shell-style]
            [kotoba-ui.product :as product]))

;; -- hiccup (shitsuke.hiccup) -------------------------------------------------

(def ->html
  "SSR: render kotoba-ui hiccup to an HTML string. Same fn browser reagent
  renders (shitsuke.reagent.core) consumes, per shitsuke's dual-render contract."
  hiccup/->html)

;; -- tokens (liquid-glass.tokens) ---------------------------------------------

(def resolve-tokens tokens/resolve-tokens)
(def resolve-dark-tokens tokens/resolve-dark-tokens)
(def css-variables tokens/css-variables)
(def dark-css-variables tokens/dark-css-variables)
(def spring-linear-easing tokens/spring-linear-easing)

;; -- style (liquid-glass.style) ------------------------------------------------

(def class-name style/class-name)
(def root-css style/root-css)
(def component-rules style/component-rules)
(def component-css style/component-css)
(def inline-style style/inline-style)
(def inline-style-hiccup style/inline-style-hiccup)

;; -- components (liquid-glass.components) -------------------------------------
;; Structural / navigation

(def panel glass/panel)
(def toolbar glass/toolbar)
(def nav-bar glass/nav-bar)
(def tab-bar glass/tab-bar)
(def sheet glass/sheet)
(def alert glass/alert)
(def menu glass/menu)
(def scrim glass/scrim)
(def list-view glass/list-view)
(def list-row glass/list-row)
(def disclosure glass/disclosure)

;; Controls

(def button glass/button)
(def icon-button glass/icon-button)
(def text-field glass/text-field)
(def text-area glass/text-area)
(def search-field glass/search-field)
(def menu-select glass/menu-select)
(def toggle glass/toggle)
(def checkbox glass/checkbox)
(def radio glass/radio)
(def slider glass/slider)
(def stepper glass/stepper)

;; Feedback / content

(def progress-bar glass/progress-bar)
(def progress-circle glass/progress-circle)
(def gauge glass/gauge)
(def badge glass/badge)
(def chip glass/chip)
(def label glass/label)
(def avatar glass/avatar)
(def divider glass/divider)
(def tooltip glass/tooltip)
(def lens-filter-defs glass/lens-filter-defs)

;; -- theme (kotoba-ui.theme) ----------------------------------------------------
;; One theme map — {:accent "#RRGGBB" :appearance :auto|:light|:dark ...} —
;; is the only styling entry an app needs; see kotoba-ui.theme's docstring.

(def theme-css theme/theme-css)
(def appearance-attr theme/appearance-attr)
(def hig-overrides theme/hig-overrides)
(def hig-dark-overrides theme/hig-dark-overrides)
(def glass-overrides theme/glass-overrides)
(def glass-dark-overrides theme/glass-dark-overrides)
(def hex->rgba theme/hex->rgba)

;; -- shell (kotoba-ui.shell) ----------------------------------------------------
;; HIG layout scaffolds — layout starts here, not with hand-written .layout CSS.

(def stack shell/stack)
(def spacer shell/spacer)
(def section shell/section)
(def hero shell/hero)
(def grid shell/grid)
(def app-shell shell/app-shell)
(def page shell/page)
(def shell-css shell/shell-css)

;; -- product surfaces (kotoba-ui.product) -------------------------------------
;; `kotoba_ui/product.cljc` shipped WITHOUT these aliases, so the ns existed on
;; main but nothing could reach it under rule 1 (apps require kotoba-ui.core
;; only). Consumers either reached around core to require kotoba-ui.product
;; directly — the opt-out rule 1 exists to prevent — or, as actually happened,
;; built green against a west checkout carrying an uncommitted local alias and
;; would have failed from a clean clone at the pinned SHA.

(def metric product/metric)
(def empty-state product/empty-state)
(def data-table product/data-table)

;; -- responsive constants -----------------------------------------------------
;; An app whose own CSS reacts to the app-shell's layout needs the SAME
;; numbers the shell switches on, or it invents a second breakpoint beside the
;; design system's and the two disagree in the band between them. That shipped:
;; a console un-stuck its sticky sidebar at 900px while the shell keeps the
;; two-column layout down to 768px, so between 769px and 900px the sidebar was
;; a 260px column that scrolled away instead of staying pinned. Exported here
;; rather than left for apps to hardcode.

(def sidebar-breakpoint
  "Viewport width at (and under) which `app-shell`'s sidebar collapses to a
  single column. Use this in an app's own media queries so they switch in
  lockstep with the shell (`(str \"(max-width: \" ui/sidebar-breakpoint \")\")`)."
  shell-style/sidebar-breakpoint)

(def sidebar-width
  "Fixed sidebar column width in the two-column app-shell layout."
  shell-style/sidebar-width)

(defn ->page
  "The one-call SSR entry: a complete refined HTML document string —
  doctype + shell/page (charset/viewport/title/theme CSS/data-appearance)
  rendered via ->html. opts as kotoba-ui.shell/page (:title :description
  :lang :theme :head)."
  [opts & body]
  (str "<!doctype html>" (->html (apply shell/page opts body))))
