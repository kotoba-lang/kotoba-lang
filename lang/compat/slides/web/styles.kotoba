(ns slides.web.styles
  "Editor page-chrome CSS on the kotoba-lang design-system paved road
  (kotoba-ui/docs/agent-guide.md, ADR-2607122200).

  docs/main.css = this namespace's app rules only (emitted by slides.build);
  the layered HIG + glass + shell theme bundle is inlined into
  docs/index.html's <head> by kotoba-ui.core/->page (slides.site). App rules
  are UNLAYERED, so they always win over the library layers
  (`@layer kotoba.hig, kotoba.glass`) — no compound selectors against
  `liquid-glass__*`/`kotoba-shell__*` classes are needed or used.

  Color/typography discipline:
  - zero chrome hex — every chrome color is a `--hig-*` token, a
    `--liquid-glass-*` token, or a `color-mix()` over one;
  - the ONLY hex in this namespace are (a) the theme map accent (the one
    legitimate hex site in app code) and (b) `deck-paper`, which is user-deck
    domain, not chrome (see its docstring);
  - no raw px font-size: text sizes reference `--hig-text-*-font-size` vars
    (the `.hig-*` classes are used in markup where a whole text style fits);
  - no hand-written font stacks: the one monospace surface (`#deck-edn`, the
    EDN source pane) uses the stack's `--hig-font-mono` token (shitsuke.hig,
    same stack as its code/pre rule).

  `rules` stays css.core EDN data (a vector of [selector decls] pairs so rule
  order is preserved), same pipeline shape as before the migration."
  (:require [css.core :as css]))

(def theme
  "The kotoba-ui one-map theme — the only styling entry for the editor chrome.
  :accent is the pre-migration brand steel blue (the old `--accent` #496b9a);
  :accent-dark is a lighter same-hue blue so accent-tinted chrome stays
  legible over dark surfaces; :auto follows the OS appearance."
  {:accent "#496B9A"
   :accent-dark "#7FA3CF"
   :appearance :auto})

(def deck-paper
  "The slide 'paper' color behind user deck content (.canvas / .thumb-preview).
  This is user-deck domain, not chrome: it mirrors the deck master's
  `:slides/background` default (FFFFFF) and deliberately does NOT follow the
  dark appearance — a document canvas stays paper-white so the user's own
  deck colors render as authored (same as slide editors' dark UIs)."
  "#fff")

(def rules
  [;; --- page ---------------------------------------------------------------
   ["body" {:background "var(--hig-color-system-grouped-background)"}]
   ;; raw-element controls the editor still renders directly (.thumb/.shape
   ;; buttons, property selects) inherit the page type instead of UA defaults.
   ["button,input,textarea,select" {:font "inherit"}]

   ;; --- top chrome (glass toolbar as a full-width bar) ----------------------
   [".editor-toolbar" {:border-radius "0" :width "100%"}]
   [".editor-toolbar > nav"
    {:display "grid"
     :grid-template-columns "minmax(180px,1fr) auto minmax(0,auto)"
     :align-items "center" :gap "var(--hig-spacing-4)" :width "100%"
     :min-width "0"}]
   [".brand h1" {:margin "0"}]
   [".brand p" {:margin "2px 0 0" :color "var(--hig-color-secondary-label)"
                :overflow "hidden" :text-overflow "ellipsis"
                :white-space "nowrap" :max-width "360px"}]
   [".deck-meta" {:display "flex" :align-items "center"
                  :gap "var(--hig-spacing-2)" :min-width "0"}]
   [".toolbar-actions" {:display "flex" :align-items "center"
                        :justify-content "flex-end"
                        :gap "var(--hig-spacing-2)" :flex-wrap "wrap"}]
   ;; accent-filled emphasis (primary action / active mode tab): accent from
   ;; the theme via the glass token; label flips with the appearance (the
   ;; dark accent is light, so system-background stays >= 4.5:1 on it).
   [".primary,.mode-tabs .active"
    {:background "var(--liquid-glass-accent-tint-strong)"
     :color "var(--hig-color-system-background)"
     :text-shadow "none" :font-weight "600"}]
   [".danger" {:color "var(--hig-palette-red)" :text-shadow "none"}]
   [".file-label"
    {:display "inline-flex" :align-items "center" :gap ".4em"
     :padding ".6em 1.1em" :border-radius "var(--liquid-glass-radius-pill)"
     :border "var(--hig-hairline) solid var(--hig-color-separator)"
     :background "var(--hig-color-quaternary-system-fill)"
     :cursor "pointer" :white-space "nowrap"}]
   [".file-label input" {:display "none"}]
   [".github" {:display "inline-flex" :align-items "center" :padding ".6em .2em"}]

   ;; --- slide rail (inside the shell sidebar) -------------------------------
   [".aside-title,.panel-title"
    {:font-size "var(--hig-text-caption1-font-size)" :font-weight "600"
     :text-transform "uppercase" :letter-spacing ".06em"
     :color "var(--hig-color-secondary-label)"
     :margin "0 0 var(--hig-spacing-3)"}]
   ;; the rail panel packs dense thumbs — tighter padding than the glass
   ;; panel default (own class hook on appkit/panel, unlayered so it wins)
   [".rail" {:padding "var(--hig-spacing-3)"}]
   [".rail-actions" {:display "grid" :grid-template-columns "1fr 1fr"
                     :gap "var(--hig-spacing-2)"
                     :margin-bottom "var(--hig-spacing-3)"}]
   [".thumb" {:display "grid"
              :grid-template-columns "24px 74px minmax(0,1fr) 22px"
              :align-items "center" :gap "var(--hig-spacing-2)" :width "100%"
              :text-align "left" :margin-bottom "var(--hig-spacing-2)"
              :padding "var(--hig-spacing-2)"
              :border "var(--hig-hairline) solid var(--hig-color-separator)"
              :border-radius "var(--hig-radius-sm)"
              :background "var(--hig-color-tertiary-system-background)"
              :color "inherit" :cursor "pointer"}]
   [".thumb.active" {:border-color "var(--hig-color-tint)"
                     :box-shadow "inset 3px 0 0 var(--hig-color-tint)"}]
   [".thumb-preview" {:position "relative" :width "74px" :aspect-ratio "16/9"
                      :border "var(--hig-hairline) solid var(--hig-color-separator)"
                      :border-radius "var(--hig-radius-xs)"
                      :background deck-paper :overflow "hidden"}]
   [".thumb-shape" {:position "absolute" :border-radius "2px" :display "block"
                    :min-width "2px" :min-height "2px" :opacity ".9"}]
   [".thumb-shape.text" {:height "3px!important"}]
   [".thumb span" {:overflow "hidden" :text-overflow "ellipsis"
                   :white-space "nowrap"}]
   [".thumb small,.thumb em"
    {:color "var(--hig-color-secondary-label)"
     :font-size "var(--hig-text-caption1-font-size)" :font-style "normal"}]
   [".thumb em" {:text-align "right"}]
   [".status" {:display "grid" :grid-template-columns "auto 1fr auto 1fr"
               :gap "var(--hig-spacing-2)" :align-items "baseline"
               :margin-top "var(--hig-spacing-4)"
               :padding "var(--hig-spacing-3)"
               :border "var(--hig-hairline) solid var(--hig-color-separator)"
               :border-radius "var(--hig-radius-sm)"
               :background "var(--hig-color-tertiary-system-background)"
               :font-size "var(--hig-text-caption1-font-size)"
               :color "var(--hig-color-secondary-label)"}]
   [".status strong" {:color "var(--hig-color-label)"
                      :font-size "var(--hig-text-callout-font-size)"}]

   ;; --- workspace + properties (main column) --------------------------------
   [".editor-main" {:display "grid"
                    :grid-template-columns "minmax(0,1fr) 328px"
                    :gap "var(--hig-spacing-4)" :align-items "start"
                    :padding "var(--hig-spacing-4)"}]
   [".workspace" {:min-width "0" :overflow "auto"}]
   [".workspace-head" {:display "flex" :align-items "flex-start"
                       :justify-content "space-between"
                       :gap "var(--hig-spacing-4)"
                       :margin "0 auto var(--hig-spacing-4)"
                       :max-width "1100px"}]
   [".workspace-head h2" {:margin "0"}]
   [".workspace-head p" {:color "var(--hig-color-secondary-label)"
                         :margin "3px 0 0"}]
   [".workspace-tools" {:display "flex" :gap "var(--hig-spacing-2)"
                        :align-items "center" :justify-content "flex-end"
                        :flex-wrap "wrap"}]
   [".mode-tabs,.insert-bar,.zoom-controls"
    {:display "flex" :gap "var(--hig-spacing-1)"}]
   [".zoom-controls #zoom-reset" {:min-width "58px"}]
   [".stage" {:min-height "620px" :display "grid" :place-items "center"
              :padding "var(--hig-spacing-7) var(--hig-spacing-5)"
              :overflow "auto"}]
   ;; the mode panes toggle via the `hidden` attribute; an author `display`
   ;; (like .stage's grid) beats the UA [hidden] rule, so restate it — the
   ;; visual pane must actually hide in EDN mode.
   [".stage[hidden],#edn-pane[hidden]" {:display "none"}]
   [".canvas-shell" {:width "min(100%,1040px)" :transform-origin "center top"
                     :transition "transform .12s ease"}]
   [".canvas" {:position "relative" :width "100%" :background deck-paper
               :border "var(--hig-hairline) solid var(--hig-color-separator)"
               :box-shadow "0 18px 46px color-mix(in srgb, var(--hig-color-label) 12%, transparent)"
               :overflow "hidden"}]
   [".shape" {:position "absolute" :display "block" :text-align "left"
              :white-space "pre-wrap" :border "0" :background "transparent"
              :padding "0" :margin "0" :overflow "hidden" :cursor "grab"
              :touch-action "none"}]
   [".shape:active" {:cursor "grabbing"}]
   [".shape.text" {:line-height "1.12"}]
   ;; rect border COLOR always comes from the shape's inline border-color
   ;; (user deck data — views.cljc/shape-node); only width/style live here.
   [".shape.rect" {:border-width "2px" :border-style "solid"}]
   [".shape.selected"
    {:z-index "2"
     :box-shadow "0 0 0 1px color-mix(in srgb, var(--hig-color-system-background) 90%, transparent),0 10px 22px color-mix(in srgb, var(--hig-color-label) 12%, transparent)"}]
   [".resize-handles" {:position "absolute" :inset "0" :pointer-events "none"}]
   [".resize-handle" {:position "absolute" :width "10px" :height "10px"
                      :border "1px solid var(--hig-color-system-background)"
                      :border-radius "var(--hig-radius-capsule)"
                      :background "var(--hig-color-tint)"
                      :box-shadow "0 2px 6px color-mix(in srgb, var(--hig-color-label) 20%, transparent)"
                      :pointer-events "auto"}]
   [".resize-handle.nw" {:left "2px" :top "2px" :cursor "nwse-resize"}]
   [".resize-handle.ne" {:right "2px" :top "2px" :cursor "nesw-resize"}]
   [".resize-handle.sw" {:left "2px" :bottom "2px" :cursor "nesw-resize"}]
   [".resize-handle.se" {:right "2px" :bottom "2px" :cursor "nwse-resize"}]

   ;; --- properties panel -----------------------------------------------------
   [".props" {:overflow "auto"}]
   [".props label" {:display "block" :margin "0 0 var(--hig-spacing-3)"}]
   [".props label > span" {:display "block"
                           :font-size "var(--hig-text-caption1-font-size)"
                           :color "var(--hig-color-secondary-label)"
                           :margin "0 0 4px"}]
   ;; property selects stay raw <select> (they carry the data-field enhancer
   ;; contract that shitsuke.components/select doesn't pass through) — give
   ;; them the same quiet fill as the glass fields around them.
   [".props select"
    {:width "100%" :padding ".55em .9em"
     :border "var(--hig-hairline) solid var(--hig-color-separator)"
     :border-radius "var(--liquid-glass-radius-pill)"
     :background "var(--hig-color-quaternary-system-fill)"
     :color "inherit"}]
   [".grid2" {:display "grid" :grid-template-columns "1fr 1fr"
              :gap "var(--hig-spacing-2)"}]
   [".inspector-actions" {:display "grid" :grid-template-columns "1fr 1fr"
                          :gap "var(--hig-spacing-2)"
                          :margin-top "var(--hig-spacing-1)"}]
   [".selection-count" {:display "flex" :align-items "baseline"
                        :gap "var(--hig-spacing-2)"
                        :margin "0 0 var(--hig-spacing-4)"
                        :padding "var(--hig-spacing-3)"
                        :border "var(--hig-hairline) solid var(--hig-color-separator)"
                        :border-radius "var(--hig-radius-sm)"
                        :background "var(--hig-color-quaternary-system-fill)"}]
   [".selection-count strong" {:font-size "var(--hig-text-title3-font-size)"}]
   [".selection-count span" {:margin "0"}]
   [".align-grid" {:display "grid" :grid-template-columns "1fr 1fr 1fr"
                   :gap "var(--hig-spacing-2)"}]

   ;; --- EDN pane ---------------------------------------------------------------
   ["#edn-pane" {:max-width "1100px" :margin "0 auto"}]
   ["#deck-edn"
    {:min-height "620px"
     :font-size "var(--hig-text-footnote-font-size)" :line-height "1.5"
     :font-family "var(--hig-font-mono)"}]
   [".edn-actions" {:display "flex" :gap "var(--hig-spacing-2)"
                    :margin-top "var(--hig-spacing-3)"}]
   ["#error" {:min-height "18px" :color "var(--hig-palette-red)"
              :font-size "var(--hig-text-footnote-font-size)"
              :margin "var(--hig-spacing-3) auto 0" :max-width "1100px"}]])

(def media-desktop
  "Desktop-only layout override on the app's own `.editor` class hook (the
  shell :class passthrough — see kotoba-ui.shell's root-opts contract; layout
  overrides belong in app CSS): the slide rail needs ~300px so thumb titles
  don't truncate. Scoped to min-width so the shell's own <=768px sidebar
  collapse (inside @layer kotoba.hig) still applies unbeaten."
  {".editor .kotoba-shell__app-body" {:grid-template-columns "320px minmax(0,1fr)"}
   ".editor .kotoba-shell__app-sidebar" {:padding "var(--hig-spacing-3)"}})

(def media-1180
  {".editor-toolbar > nav" {:grid-template-columns "1fr"
                            :align-items "flex-start"}
   ".toolbar-actions" {:justify-content "flex-start"}
   ".editor-main" {:grid-template-columns "minmax(0,1fr) 300px"}})

(def media-940
  [[".editor-main" {:grid-template-columns "1fr"}]
   [".workspace-head" {:display "block"}]
   [".workspace-tools" {:justify-content "flex-start"
                        :margin-top "var(--hig-spacing-3)"}]
   [".stage" {:min-height "360px" :padding "var(--hig-spacing-5) var(--hig-spacing-1)"}]
   [".thumb" {:grid-template-columns "24px 96px minmax(0,1fr) 22px"}]
   [".thumb-preview" {:width "96px"}]])

(def sheet
  {:rules rules
   :media [["(min-width:769px)" media-desktop]
           ["(max-width:1180px)" media-1180]
           ["(max-width:940px)" media-940]]})

(defn static-css
  "The editor's unlayered app-chrome rules, rendered via css.core/css.
  slides.build appends this after the kotoba-ui theme bundle."
  []
  (css/css sheet))
