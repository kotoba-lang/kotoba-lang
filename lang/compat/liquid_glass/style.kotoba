(ns liquid-glass.style
  "Two-tier styling layer, mirroring shitsuke.style:

  Tier A (portable, `root-css`): token -> CSS custom properties on `:root`,
  light values plus a `prefers-color-scheme: dark` override block. Babashka-safe,
  zero deps beyond liquid-glass.tokens.

  Tier B (`component-css`): the actual glass material rules — backdrop-filter
  blur+saturate+brightness, tint, specular-highlight overlay, a two-tone edge
  rim light (the `:liquid-glass/specular :rim` token, top brighter than
  bottom — the detail that reads as an actual lit glass edge rather than a
  flat translucent panel), a default readable ink color + text-shadow (the
  `:liquid-glass/ink` token — adapts with the same light/dark mechanism as
  the surface tint, so text is legible without every consumer re-deriving
  it), elevation shadow, corner radius, press/hover motion.

  Every rule is built from EDN declaration maps via `kotoba-lang/css`
  (`css.core`) rather than hand-typed CSS strings — `css.core/rule` /
  `css.core/css` own bracket/semicolon bookkeeping, so a rule can't end up
  malformed, and (more importantly) the rule set is *data*: tests can assert
  against the declaration maps directly instead of regex-scraping rendered
  CSS text. This is exactly the shape of two real bugs found while building
  this repo (`panel--flat`'s shadow rule and the `:specular :rim` tokens were
  each defined but silently never referenced by any hand-typed rule) — harder
  to reintroduce once selectors and declarations are values, not string
  fragments assembled by hand. liquid-glass-ui is `kotoba-lang/css`'s first
  real consumer.

  Unlike shitsuke (whose component visuals are deferred to a shadow-css
  `:pages` build that does not yet exist), liquid-glass-ui ships Tier B as a
  plain CSS string: no build step is required to see the material, and the
  same string works for SSR (inlined `<style>`) and browser builds
  (concatenated into main.css).

  Class names read `liquid-glass__<component>` (base) and
  `liquid-glass__<component>--<variant>` (modifier), the same two-part
  convention as shitsuke.style/class-name."
  (:require [liquid-glass.tokens :as t]
            [css.core :as css]
            [clojure.string :as str]))

(defn class-name
  "Stable class for a component or component--modifier, e.g. (class-name :button)
  => \"liquid-glass__button\", (class-name \"panel--thick\") => \"liquid-glass__panel--thick\"."
  [component]
  (str "liquid-glass__" (name component)))

(defn root-css
  "`:root{...}` light vars + `@media (prefers-color-scheme: dark){:root{...}}`
  override block, from tokens (default merged with overrides / dark-overrides)."
  ([] (root-css nil nil))
  ([overrides] (root-css overrides nil))
  ([overrides dark-overrides]
   (str (t/css-variables overrides) "\n" (t/dark-css-variables dark-overrides))))

;; --- small declaration-map builders (kept DRY so every surface gets the same
;; blur+saturate+brightness backdrop and the same elevation+rim shadow — the
;; panel--flat-shaped bug (a class rendered, but its rule forgotten) is much
;; harder to reintroduce when there's one place that emits these declaration
;; maps instead of ~15 hand-typed copies) -----------------------------------

(defn- backdrop-decls
  "backdrop-filter (+ -webkit- prefix): blur+saturate from a surface tier's
  tokens, plus a small brightness lift — real glass looks slightly brighter
  than the flat tint alone, not just blurred."
  [surface]
  (let [s (name surface)
        v (str "blur(var(--liquid-glass-surface-" s "-blur)) "
               "saturate(var(--liquid-glass-surface-" s "-saturate)) brightness(1.05)")]
    {:backdrop-filter v :-webkit-backdrop-filter v}))

(defn- glass-bg-decls
  "background + backdrop-filter + border declarations for a surface tier
  (:clear/:regular/:thick)."
  [surface]
  (let [s (name surface)]
    (merge {:background (str "var(--liquid-glass-surface-" s "-tint)")
            :border (str "1px solid var(--liquid-glass-surface-" s "-border)")}
           (backdrop-decls surface))))

(defn- glass-shadow-decls
  "Elevation drop-shadow plus the :liquid-glass/specular :rim edge light — a
  bright 1px inset line along the top edge, a much dimmer one along the
  bottom (the token values are asymmetric on purpose: light source from
  above). This is what turns a flat translucent rectangle into something
  that reads as a lit glass edge."
  [level]
  {:box-shadow (str "var(--liquid-glass-elevation-" (name level) "-shadow),"
                     "inset 0 1px 0 rgba(255,255,255,var(--liquid-glass-specular-rim-top-opacity)),"
                     "inset 0 -1px 0 rgba(255,255,255,var(--liquid-glass-specular-rim-bottom-opacity))")})

(def ^:private ink-decls
  "Default readable text color + a soft counter-shadow, from the
  :liquid-glass/ink token (dark ink in light scheme, light ink in dark scheme
  — the same media-query redeclaration technique as the surface tint). A
  consumer's own `color` on a more specific selector still wins normally;
  this only replaces the browser default of inheriting whatever ancestor
  color happened to be in scope, which is what silently produced illegible
  text before this token existed."
  {:color "var(--liquid-glass-ink-default)" :text-shadow "var(--liquid-glass-ink-shadow)"})

;; Components whose root element is a plain container (div/span/label/section/
;; header/nav/details) — i.e. NOT a replaced element like <input>/<select>, so
;; ::before generated content is reliable — get the shared base rule
;; (position/isolation/transition) and the shared specular ::before overlay.
;; Native form controls (slider, and the sr-only <input> inside toggle/
;; checkbox/radio) are styled directly instead; see their sections below.
;; `gauge` also opts out — its ::before is a dedicated inner-disc mask, not
;; the specular sheen (see gauge-rules).
(def ^:private glass-surface-components
  ["panel" "button" "icon-button" "toolbar" "sheet" "badge"
   "text-field" "text-area" "search-field" "menu-select"
   "toggle-track" "checkbox-box" "radio-box" "stepper"
   "nav-bar" "alert" "menu" "list" "chip" "disclosure"])

;; Every top-level component root that can carry user-visible text — a
;; superset of glass-surface-components (adds the outer wrapper for
;; toggle/checkbox/radio, whose glass surface is a small *nested* box, plus
;; components with no ::before treatment at all) — gets the ink color/shadow.
;; `color`/`text-shadow` are inherited CSS properties, so setting them once
;; here reaches every nested span (checkbox-text, list-row-content, ...)
;; without a rule per sub-element.
(def ^:private ink-components
  (into glass-surface-components
        ["tab-bar" "toggle" "checkbox" "radio" "slider" "progress-bar"
         "progress-circle" "gauge" "divider" "label" "avatar" "tooltip"]))

;; Components that actually append the `liquid-glass__specular` marker span
;; as a direct child (see liquid-glass.components — the compound controls
;; whose glass surface is a nested track/box skip it, and badge/scrim/tooltip/
;; gauge never carry it).
(def ^:private specular-host-components
  ["panel" "button" "icon-button" "toolbar" "tab-bar" "sheet" "text-field"
   "text-area" "search-field" "menu-select" "stepper" "nav-bar" "alert"
   "menu" "list" "chip" "disclosure"])

;; Overlay/presence components that get enter animations on insertion and the
;; [data-state="closing"] exit contract (see overlay-motion-rules).
(def ^:private overlay-components
  ["scrim" "sheet" "alert" "menu" "tooltip"])

(defn- sel
  ([names] (sel names ""))
  ([names suffix] (str/join "," (map #(str "." (class-name %) suffix) names))))

(defn specular-selector
  "CSS selector list matching every component root that carries a
  `liquid-glass__specular` marker span — the hosts the optional
  pointer-tracking enhancer (the reference script inlined by
  liquid-glass.demo/specular-script; see ADR-0003) targets.
  Public so an embedder can hand it to the script via its `data-lg-selector`
  attribute (the script also derives the same list from the marker spans in
  the document when the attribute is absent) — one source of truth, no
  hand-copied class list in JS."
  []
  (sel specular-host-components))

(defn- base-rules []
  [[(sel glass-surface-components)
    {:position "relative" :isolation "isolate" :box-sizing "border-box" :font "inherit"
     :transition (str "transform var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing),"
                       "box-shadow var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing),"
                       "filter var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing)")}]
   [(sel glass-surface-components "::before")
    {:content "\"\"" :position "absolute" :inset "0" :pointer-events "none" :border-radius "inherit"
     :background (str "radial-gradient(120% 60% at 18% -10%,"
                       "rgba(255,255,255,var(--liquid-glass-specular-highlight-opacity)) 0%,rgba(255,255,255,0) 65%)")
     :mix-blend-mode "overlay"}]
   [(sel ink-components) ink-decls]])

(defn- panel-rules []
  [[".liquid-glass__panel"
    (merge {:overflow "hidden" :padding "1em" :border-radius "var(--liquid-glass-radius-md)"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__panel--clear" (glass-bg-decls :clear)]
   [".liquid-glass__panel--thick" (glass-bg-decls :thick)]
   [".liquid-glass__panel--flat" (glass-shadow-decls :flat)]
   [".liquid-glass__panel--overlay" (glass-shadow-decls :overlay)]
   [".liquid-glass__panel--floating" (glass-shadow-decls :floating)]])

(defn- button-rules []
  [[".liquid-glass__button,.liquid-glass__icon-button"
    ;; min-height 44px: Apple HIG minimum tap-target — padding alone leaves the
    ;; computed height under 44px at default type sizes (design-quality
    ;; :tap-targets axis, kotoba-lang/kotoba-ui#3 follow-up).
    (merge {:overflow "hidden" :display "inline-flex" :align-items "center" :justify-content "center"
            :gap ".4em" :padding ".6em 1.1em" :min-height "44px"
            :border-radius "var(--liquid-glass-radius-pill)" :cursor "pointer"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__icon-button" {:padding ".55em" :aspect-ratio "1"}]
   [".liquid-glass__button:hover,.liquid-glass__icon-button:hover"
    (merge {:transform "translateY(-1px)" :filter "brightness(1.08)"} (glass-shadow-decls :overlay))]
   ;; Press morph: a subtle squash (wider + shorter) rather than a flat
   ;; uniform scale — reads as glass giving under a fingertip. Values are the
   ;; :liquid-glass/motion :press :scale-x/:scale-y tokens.
   [".liquid-glass__button:active,.liquid-glass__icon-button:active"
    {:transform (str "translateY(0) scaleX(var(--liquid-glass-motion-press-scale-x))"
                     " scaleY(var(--liquid-glass-motion-press-scale-y))")
     :filter "brightness(.97)"}]
   [".liquid-glass__button:disabled,.liquid-glass__icon-button:disabled"
    {:opacity ".45" :cursor "not-allowed" :transform "none" :filter "none"}]])

(defn- toolbar-tabbar-rules []
  [[".liquid-glass__toolbar"
    (merge {:display "flex" :align-items "center" :gap ".5em" :padding ".5em .75em"
            :border-radius "var(--liquid-glass-radius-lg)"}
           (glass-bg-decls :thick) (glass-shadow-decls :overlay))]
   [".liquid-glass__tab-bar" {:display "flex" :gap ".15em"}]
   [".liquid-glass__tab"
    {:padding ".4em .9em" :border-radius "var(--liquid-glass-radius-pill)" :opacity ".7"
     :transition (str "opacity var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing),"
                       "background var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing)")}]
   [".liquid-glass__tab--active" {:opacity "1" :background "rgba(255,255,255,.18)"}]])

(defn- sheet-scrim-badge-rules []
  [[".liquid-glass__sheet"
    (merge {:overflow "hidden"
            :border-radius "var(--liquid-glass-radius-lg) var(--liquid-glass-radius-lg) 0 0"
            :border-bottom "none"
            :padding "1.25em 1.25em calc(1.25em + env(safe-area-inset-bottom,0px))"}
           (glass-bg-decls :thick) (glass-shadow-decls :floating))]
   [".liquid-glass__scrim"
    {:position "fixed" :inset "0" :background "rgba(0,0,0,.28)"
     :backdrop-filter "blur(2px)" :-webkit-backdrop-filter "blur(2px)"}]
   [".liquid-glass__badge"
    (merge {:overflow "hidden" :display "inline-flex" :align-items "center" :justify-content "center"
            :min-width "1.4em" :padding ".1em .45em" :border-radius "var(--liquid-glass-radius-pill)"
            :font-size ".75em"}
           (glass-bg-decls :regular))]])

(defn- form-field-rules []
  [[".liquid-glass__text-field,.liquid-glass__search-field"
    (merge {:display "flex" :align-items "center" :gap ".5em" :padding ".55em .9em"
            :border-radius "var(--liquid-glass-radius-pill)"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__text-area"
    (merge {:display "flex" :align-items "flex-start" :padding ".7em .9em"
            :border-radius "var(--liquid-glass-radius-md)"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__text-field input,.liquid-glass__search-field input,.liquid-glass__text-area textarea"
    {:flex "1" :min-width "0" :width "100%" :border "none" :outline "none"
     :background "transparent" :color "inherit" :font "inherit"}]
   [".liquid-glass__text-area textarea" {:resize "vertical"}]
   [".liquid-glass__search-icon" {:opacity ".6" :font-size ".9em" :line-height "1"}]
   [".liquid-glass__text-field:focus-within,.liquid-glass__search-field:focus-within,.liquid-glass__text-area:focus-within"
    {:box-shadow "0 0 0 2px var(--liquid-glass-accent-tint)"}]
   [".liquid-glass__menu-select"
    (merge {:display "inline-flex" :align-items "center" :padding ".55em 2.1em .55em .9em"
            :border-radius "var(--liquid-glass-radius-pill)"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__menu-select select"
    {:appearance "none" :-webkit-appearance "none" :background "transparent" :border "none"
     :outline "none" :color "inherit" :font "inherit" :width "100%"}]
   [".liquid-glass__menu-select::after"
    {:content "\"⌄\"" :position "absolute" :right ".9em" :top "50%"
     :transform "translateY(-50%)" :pointer-events "none" :opacity ".6"}]])

(defn- toggle-rules []
  [[".liquid-glass__toggle" {:display "inline-flex" :align-items "center" :cursor "pointer"}]
   [".liquid-glass__toggle-input"
    {:position "absolute" :width "1px" :height "1px" :padding "0" :margin "-1px" :overflow "hidden"
     :clip "rect(0,0,0,0)" :white-space "nowrap" :border "0"}]
   [".liquid-glass__toggle-track"
    (merge {:width "48px" :height "28px" :border-radius "var(--liquid-glass-radius-pill)"
            :transition "background var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing)"}
           (glass-bg-decls :regular))]
   [".liquid-glass__toggle-thumb"
    (merge {:position "absolute" :top "2px" :left "2px" :width "22px" :height "22px" :border-radius "50%"
            :background "#fff"
            :transition "transform var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing)"}
           (glass-shadow-decls :raised))]
   [".liquid-glass__toggle-input:checked ~ .liquid-glass__toggle-track"
    {:background "var(--liquid-glass-accent-tint-strong)" :border-color "var(--liquid-glass-accent-tint-strong)"}]
   [".liquid-glass__toggle-input:checked ~ .liquid-glass__toggle-track .liquid-glass__toggle-thumb"
    {:transform "translateX(20px)"}]
   [".liquid-glass__toggle-input:focus-visible ~ .liquid-glass__toggle-track"
    {:box-shadow "0 0 0 2px var(--liquid-glass-accent-tint)"}]
   [".liquid-glass__toggle-input:disabled ~ .liquid-glass__toggle-track"
    {:opacity ".45" :cursor "not-allowed"}]])

(defn- checkbox-radio-rules []
  [[".liquid-glass__checkbox,.liquid-glass__radio"
    {:display "inline-flex" :align-items "center" :gap ".5em" :cursor "pointer"}]
   [".liquid-glass__checkbox-input,.liquid-glass__radio-input"
    {:position "absolute" :width "1px" :height "1px" :padding "0" :margin "-1px" :overflow "hidden"
     :clip "rect(0,0,0,0)" :white-space "nowrap" :border "0"}]
   [".liquid-glass__checkbox-box"
    (merge {:width "20px" :height "20px" :border-radius "6px"} (glass-bg-decls :regular))]
   [".liquid-glass__radio-box"
    (merge {:width "20px" :height "20px" :border-radius "50%"} (glass-bg-decls :regular))]
   [".liquid-glass__checkbox-input:checked ~ .liquid-glass__checkbox-box"
    {:background "var(--liquid-glass-accent-tint-strong)" :border-color "var(--liquid-glass-accent-tint-strong)"}]
   [".liquid-glass__checkbox-input:checked ~ .liquid-glass__checkbox-box::after"
    ;; glyph sized in em (0.8125em = 13px at 16px root) so a sub-16px px
    ;; font-size never sits on an input pseudo-element (design-quality
    ;; :input-zoom heuristic, kotoba-lang/kotoba-ui#3 follow-up); same visual size.
    {:content "\"✓\"" :position "absolute" :inset "0" :display "flex" :align-items "center"
     :justify-content "center" :font-size "0.8125em" :color "#fff"}]
   [".liquid-glass__radio-input:checked ~ .liquid-glass__radio-box"
    {:border-color "var(--liquid-glass-accent-tint-strong)"}]
   [".liquid-glass__radio-input:checked ~ .liquid-glass__radio-box::after"
    {:content "\"\"" :position "absolute" :top "50%" :left "50%" :width "10px" :height "10px"
     :border-radius "50%" :background "var(--liquid-glass-accent-tint-strong)" :transform "translate(-50%,-50%)"}]
   [".liquid-glass__checkbox-input:disabled ~ .liquid-glass__checkbox-box,.liquid-glass__radio-input:disabled ~ .liquid-glass__radio-box"
    {:opacity ".45"}]
   [".liquid-glass__checkbox-text,.liquid-glass__radio-text" {:font-size "14px" :line-height "1.3"}]])

(defn- slider-rules []
  [[".liquid-glass__slider" {:-webkit-appearance "none" :appearance "none" :width "100%" :height "28px"
                             :background "transparent"}]
   [".liquid-glass__slider::-webkit-slider-runnable-track"
    {:height "6px" :border-radius "var(--liquid-glass-radius-pill)"
     :background "var(--liquid-glass-surface-regular-tint)"
     :border "1px solid var(--liquid-glass-surface-regular-border)"}]
   [".liquid-glass__slider::-webkit-slider-thumb"
    (merge {:-webkit-appearance "none" :width "22px" :height "22px" :margin-top "-8px" :border-radius "50%"
            :background "#fff" :border "1px solid var(--liquid-glass-surface-regular-border)"}
           (glass-shadow-decls :raised))]
   [".liquid-glass__slider::-moz-range-track"
    {:height "6px" :border-radius "var(--liquid-glass-radius-pill)"
     :background "var(--liquid-glass-surface-regular-tint)"
     :border "1px solid var(--liquid-glass-surface-regular-border)"}]
   [".liquid-glass__slider::-moz-range-thumb"
    (merge {:width "22px" :height "22px" :border-radius "50%" :background "#fff"
            :border "1px solid var(--liquid-glass-surface-regular-border)"}
           (glass-shadow-decls :raised))]])

(defn- stepper-rules []
  [[".liquid-glass__stepper"
    (merge {:display "inline-flex" :align-items "center" :gap ".4em" :padding ".25em .3em"
            :border-radius "var(--liquid-glass-radius-pill)"}
           (glass-bg-decls :regular))]
   [".liquid-glass__stepper .liquid-glass__icon-button"
    {:padding ".35em" :width "26px" :height "26px" :background "transparent"
     :backdrop-filter "none" :-webkit-backdrop-filter "none" :border "none" :box-shadow "none"}]
   [".liquid-glass__stepper .liquid-glass__icon-button::before" {:display "none"}]
   [".liquid-glass__stepper-value"
    {:min-width "1.6em" :text-align "center" :font-variant-numeric "tabular-nums" :font-size ".9em"}]])

(defn- progress-rules []
  [[".liquid-glass__progress-bar"
    {:width "100%" :height "8px" :border-radius "var(--liquid-glass-radius-pill)"
     :background "var(--liquid-glass-surface-regular-tint)"
     :backdrop-filter "blur(var(--liquid-glass-surface-regular-blur))"
     :-webkit-backdrop-filter "blur(var(--liquid-glass-surface-regular-blur))"
     :border "1px solid var(--liquid-glass-surface-regular-border)" :overflow "hidden"}]
   [".liquid-glass__progress-bar-fill"
    {:height "100%" :border-radius "inherit" :background "var(--liquid-glass-accent-tint-strong)"
     :transition "width var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing)"}]
   [".liquid-glass__progress-circle"
    {:display "inline-block" :width "20px" :height "20px" :border-radius "50%"
     :border "2.5px solid var(--liquid-glass-surface-regular-border)" :border-top-color "#fff"
     :animation "liquid-glass-spin .8s linear infinite"}]])

(defn- gauge-rules []
  ;; The ring is a plain conic-gradient set inline per-instance (see
  ;; liquid-glass.components/gauge) — no CSS custom-property trick needed,
  ;; so it works identically across every reagent/SSR target.
  [[".liquid-glass__gauge"
    ;; Not part of glass-surface-components (its ::before is the inner disc,
    ;; not the shared specular overlay), so it must establish its own
    ;; containing block — without position:relative the absolutely-positioned
    ;; ::before resolves against the viewport and renders as a page-sized
    ;; blurred circle.
    (merge {:display "inline-flex" :align-items "center" :justify-content "center"
            :position "relative" :isolation "isolate"
            :width "56px" :height "56px" :border-radius "50%"}
           (glass-shadow-decls :raised))]
   [".liquid-glass__gauge::before"
    {:content "\"\"" :position "absolute" :inset "5px" :border-radius "50%"
     :background "var(--liquid-glass-surface-thick-tint)"
     :backdrop-filter "blur(var(--liquid-glass-surface-thick-blur)) brightness(1.05)"
     :-webkit-backdrop-filter "blur(var(--liquid-glass-surface-thick-blur)) brightness(1.05)"}]
   [".liquid-glass__gauge-label" {:position "relative" :z-index "1" :font-size "12px" :font-weight "700"}]])

(defn- misc-rules []
  [[".liquid-glass__divider" {:border "none" :height "1px" :margin "1em 0"
                              :background "var(--liquid-glass-surface-regular-border)"}]
   [".liquid-glass__label" {:display "inline-flex" :align-items "center" :gap ".5em"}]
   [".liquid-glass__label-icon" {:display "inline-flex" :font-size "1.1em" :line-height "1"}]
   [".liquid-glass__label-text" {:line-height "1.3"}]
   [".liquid-glass__avatar"
    (merge {:display "inline-flex" :align-items "center" :justify-content "center"
            :width "36px" :height "36px" :border-radius "50%" :overflow "hidden"
            :font-size ".85em" :font-weight "600"}
           (glass-bg-decls :regular))]
   [".liquid-glass__avatar img" {:width "100%" :height "100%" :object-fit "cover"}]
   [".liquid-glass__tooltip"
    (merge {:position "absolute" :padding ".35em .6em" :border-radius "var(--liquid-glass-radius-sm)"
            :font-size "12px" :white-space "nowrap"}
           (glass-bg-decls :clear))]])

(defn- nav-bar-rules []
  [[".liquid-glass__nav-bar"
    (merge {:display "flex" :align-items "center" :justify-content "space-between" :gap ".75em"
            :padding ".75em 1em" :border-left "none" :border-right "none" :border-top "none"}
           (glass-bg-decls :thick))]
   [".liquid-glass__nav-bar-title" {:font-weight "700" :font-size "15px" :flex "1" :text-align "center"}]
   [".liquid-glass__nav-bar-leading,.liquid-glass__nav-bar-trailing"
    {:display "flex" :align-items "center" :gap ".4em" :min-width "2em"}]])

(defn- alert-rules []
  [[".liquid-glass__alert"
    (merge {:position "fixed" :top "50%" :left "50%" :transform "translate(-50%,-50%)" :z-index "1"
            :width "min(90vw,360px)" :padding "1.5em" :text-align "center"
            :border-radius "var(--liquid-glass-radius-lg)"}
           (glass-bg-decls :thick) (glass-shadow-decls :floating))]])

(defn- menu-rules []
  [[".liquid-glass__menu"
    (merge {:position "absolute" :display "flex" :flex-direction "column" :padding ".4em"
            :min-width "160px" :border-radius "var(--liquid-glass-radius-md)"}
           (glass-bg-decls :thick) (glass-shadow-decls :overlay))]
   [".liquid-glass__menu-item"
    {:all "unset" :box-sizing "border-box" :padding ".5em .7em" :border-radius "var(--liquid-glass-radius-sm)"
     :cursor "pointer" :font "inherit" :color "inherit"}]
   [".liquid-glass__menu-item:hover" {:background "rgba(255,255,255,.18)"}]
   [".liquid-glass__menu-item:disabled" {:opacity ".45" :cursor "not-allowed"}]])

(defn- list-rules []
  [[".liquid-glass__list"
    (merge {:display "flex" :flex-direction "column" :border-radius "var(--liquid-glass-radius-md)"
            :overflow "hidden"}
           (glass-bg-decls :regular))]
   [".liquid-glass__list--thick" (glass-bg-decls :thick)]
   [".liquid-glass__list-row" {:display "flex" :align-items "center" :justify-content "space-between"
                               :gap ".75em" :padding ".7em 1em"}]
   [".liquid-glass__list-row + .liquid-glass__list-row"
    {:border-top "1px solid var(--liquid-glass-surface-regular-border)"}]
   [".liquid-glass__list-row[data-act]" {:cursor "pointer"}]
   [".liquid-glass__list-row-content" {:flex "1" :min-width "0"}]
   [".liquid-glass__list-row-trailing" {:opacity ".55" :font-size ".9em"}]])

(defn- chip-rules []
  [[".liquid-glass__chip"
    (merge {:display "inline-flex" :align-items "center" :gap ".35em"
            :padding ".35em .7em .35em .85em" :border-radius "var(--liquid-glass-radius-pill)"
            :font-size "13px"}
           (glass-bg-decls :regular))]
   [".liquid-glass__chip-remove"
    {:all "unset" :box-sizing "border-box" :display "inline-flex" :align-items "center"
     :justify-content "center" :width "16px" :height "16px" :border-radius "50%"
     :cursor "pointer" :opacity ".7" :font-size "11px"}]
   [".liquid-glass__chip-remove:hover" {:opacity "1" :background "rgba(255,255,255,.18)"}]])

(defn- disclosure-rules []
  [[".liquid-glass__disclosure"
    (merge {:border-radius "var(--liquid-glass-radius-md)" :overflow "hidden"} (glass-bg-decls :regular))]
   [".liquid-glass__disclosure-summary"
    {:list-style "none" :cursor "pointer" :display "flex" :align-items "center"
     :justify-content "space-between" :padding ".75em 1em"}]
   [".liquid-glass__disclosure-summary::-webkit-details-marker" {:display "none"}]
   [".liquid-glass__disclosure-chevron"
    {:display "inline-flex" :opacity ".6"
     :transition "transform var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing)"}]
   [".liquid-glass__disclosure[open] .liquid-glass__disclosure-chevron" {:transform "rotate(180deg)"}]
   [".liquid-glass__disclosure-body" {:padding "0 1em 1em"}]])

(defn- overlay-motion-rules []
  ;; Overlay presence transitions, pure CSS (SSR-friendly): the *enter*
  ;; animation runs when the element first paints (insertion into the DOM, or
  ;; e.g. a <details> opening), so no JS is needed to show it. *Exit* is the
  ;; [data-state=\"closing\"] attribute contract (docs/design.md "Motion &
  ;; dynamic effects"): the caller — who owns open/close state, this library
  ;; owns none — sets data-state=\"closing\", waits for `animationend`, then
  ;; removes the element. `both` fill keeps the exit end-state (opacity 0)
  ;; until removal. Durations/easings/offsets are the
  ;; :liquid-glass/motion :overlay-enter/:overlay-exit tokens.
  (let [enter (fn [kf] (str kf " var(--liquid-glass-motion-overlay-enter-duration)"
                            " var(--liquid-glass-motion-overlay-enter-easing) both"))
        exit  (fn [kf] (str kf " var(--liquid-glass-motion-overlay-exit-duration)"
                            " var(--liquid-glass-motion-overlay-exit-easing) both"))]
    [[".liquid-glass__scrim" {:animation (enter "liquid-glass-scrim-enter")}]
     [".liquid-glass__scrim[data-state=\"closing\"]" {:animation (exit "liquid-glass-scrim-exit")}]
     [".liquid-glass__sheet" {:animation (enter "liquid-glass-sheet-enter")}]
     [".liquid-glass__sheet[data-state=\"closing\"]" {:animation (exit "liquid-glass-sheet-exit")}]
     ;; alert's keyframes fold the base centering translate(-50%,-50%) into
     ;; every frame — an animation's transform *replaces* the rule transform,
     ;; so plain translateY keyframes would un-center it mid-flight.
     [".liquid-glass__alert" {:animation (enter "liquid-glass-alert-enter")}]
     [".liquid-glass__alert[data-state=\"closing\"]" {:animation (exit "liquid-glass-alert-exit")}]
     [".liquid-glass__menu" {:transform-origin "top center"
                             :animation (enter "liquid-glass-menu-enter")}]
     [".liquid-glass__menu[data-state=\"closing\"]" {:animation (exit "liquid-glass-menu-exit")}]
     [".liquid-glass__tooltip" {:animation (enter "liquid-glass-tooltip-enter")}]
     [".liquid-glass__tooltip[data-state=\"closing\"]" {:animation (exit "liquid-glass-tooltip-exit")}]]))

(defn- specular-pointer-rules []
  ;; Pointer-tracking specular highlight on the `liquid-glass__specular`
  ;; marker span — the seam docs/design.md reserved for "a pointer/device-
  ;; motion-driven highlight position". Everything is gated behind the
  ;; `.liquid-glass-js` class that the pointer-tracking enhancer script (the
  ;; reference implementation is inlined in liquid-glass.demo/specular-js,
  ;; ADR-0003) adds to
  ;; <html>: without the script the span keeps its display:none default and
  ;; nothing changes. The script writes --liquid-glass-pointer-x/-y (0..1,
  ;; relative to the host rect) and toggles [data-lg-pointer] on the hovered
  ;; host; only opacity transitions (cheap — the gradient position updates
  ;; per pointer frame without animation).
  [[".liquid-glass-js .liquid-glass__specular"
    {:display "block" :position "absolute" :inset "0" :border-radius "inherit"
     :pointer-events "none" :opacity "0" :z-index "1"
     :background (str "radial-gradient(var(--liquid-glass-specular-pointer-size) circle at "
                      "calc(var(--liquid-glass-pointer-x,.5)*100%) "
                      "calc(var(--liquid-glass-pointer-y,.5)*100%),"
                      "rgba(255,255,255,var(--liquid-glass-specular-pointer-opacity)) 0%,"
                      "rgba(255,255,255,0) 70%)")
     :mix-blend-mode "overlay"
     :transition "opacity var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing)"}]
   [".liquid-glass-js [data-lg-pointer] > .liquid-glass__specular" {:opacity "1"}]])

(defn- lens-rules []
  ;; Baseline for the .liquid-glass--lens modifier: the plain regular-surface
  ;; backdrop (blur+saturate+brightness), so the modifier is harmless on its
  ;; own and on non-glass hosts. The actual displacement upgrade lives in
  ;; lens-supports-css (@supports (backdrop-filter: url(#...))) — see
  ;; docs/design.md for honest engine support (Chromium partial; Safari no →
  ;; this fallback).
  [[".liquid-glass--lens" (backdrop-decls :regular)]])

(defn component-rules
  "The Tier B rule set as EDN data — a vector of `[selector decls-map]` pairs,
  the same shape `css.core/css`'s `:rules` consumes. Exposed (not just the
  rendered `component-css` string) so tests — and any future consumer, e.g. a
  shadow-css `:pages` extraction — can assert against declarations directly
  instead of regex-scraping rendered CSS text. This is the whole point of the
  css.core migration: a selector with no matching entry here, or a `:box-shadow`
  that's missing the rim vars, is a data assertion instead of a string search."
  []
  (vec (mapcat (fn [f] (f))
               [base-rules panel-rules button-rules toolbar-tabbar-rules sheet-scrim-badge-rules
                form-field-rules toggle-rules checkbox-radio-rules slider-rules stepper-rules
                progress-rules gauge-rules misc-rules nav-bar-rules alert-rules menu-rules
                list-rules chip-rules disclosure-rules
                overlay-motion-rules specular-pointer-rules lens-rules])))

(def ^:private motion-keyframes
  "CSS @keyframes for the overlay presence transitions (plus the spinner). Frame
  offsets reference --liquid-glass-motion-overlay-enter-* custom properties —
  custom properties are legal inside keyframes and resolve per element, so the
  distances/scales stay tokens, not literals. Exit frames mirror enter (the
  [data-state=\"closing\"] contract, see overlay-motion-rules)."
  (let [sheet-out  (str "translateY(var(--liquid-glass-motion-overlay-enter-distance))"
                        " scale(var(--liquid-glass-motion-overlay-enter-scale))")
        sheet-in   "translateY(0) scale(1)"
        alert-out  (str "translate(-50%,calc(-50% + var(--liquid-glass-motion-overlay-enter-distance)))"
                        " scale(var(--liquid-glass-motion-overlay-enter-scale))")
        alert-in   "translate(-50%,-50%) scale(1)"
        menu-out   "scaleY(var(--liquid-glass-motion-overlay-enter-scale-y))"
        menu-in    "scaleY(1)"]
    {"liquid-glass-spin"          {100 {:transform "rotate(360deg)"}}
     "liquid-glass-scrim-enter"   {0 {:opacity "0"} 100 {:opacity "1"}}
     "liquid-glass-scrim-exit"    {0 {:opacity "1"} 100 {:opacity "0"}}
     "liquid-glass-sheet-enter"   {0 {:opacity "0" :transform sheet-out} 100 {:opacity "1" :transform sheet-in}}
     "liquid-glass-sheet-exit"    {0 {:opacity "1" :transform sheet-in} 100 {:opacity "0" :transform sheet-out}}
     "liquid-glass-alert-enter"   {0 {:opacity "0" :transform alert-out} 100 {:opacity "1" :transform alert-in}}
     "liquid-glass-alert-exit"    {0 {:opacity "1" :transform alert-in} 100 {:opacity "0" :transform alert-out}}
     "liquid-glass-menu-enter"    {0 {:opacity "0" :transform menu-out} 100 {:opacity "1" :transform menu-in}}
     "liquid-glass-menu-exit"     {0 {:opacity "1" :transform menu-in} 100 {:opacity "0" :transform menu-out}}
     "liquid-glass-tooltip-enter" {0 {:opacity "0"} 100 {:opacity "1"}}
     "liquid-glass-tooltip-exit"  {0 {:opacity "1"} 100 {:opacity "0"}}}))

(defn- supports-fallback-css
  "CSS @supports isn't a CSS *rule* in css.core's sense (it's an at-rule wrapping
  other rules, like CSS @media, but css.core/media hardcodes the `@media` prefix)
  — built directly with css.core/rule for the inner block so the declaration
  side stays data-driven even though the at-rule wrapper is a literal string."
  []
  (str "@supports not (backdrop-filter: blur(1px)) { "
       (css/rule (str (sel glass-surface-components)
                       ",.liquid-glass__slider::-webkit-slider-runnable-track,"
                       ".liquid-glass__slider::-moz-range-track,.liquid-glass__toggle-track,"
                       ".liquid-glass__checkbox-box,.liquid-glass__radio-box,.liquid-glass__avatar,"
                       ".liquid-glass__gauge::before")
                  {:background "rgba(255,255,255,.85)"})
       " }"))

(defn- spring-supports-css
  "Spring-flavored settle: engines that understand CSS linear() get the
  generated damped-spring curve (`--liquid-glass-motion-spring-easing`, see
  liquid-glass.tokens/spring-linear-easing) on the properties where an
  overshoot reads as physical — button/icon-button transform settle (release
  from the :active squash bounces once past rest), the toggle thumb slide and
  the disclosure chevron flip. Default rules keep the cubic-bezier :settle /
  :press easings; this block only *upgrades* — no linear(), no change."
  []
  (let [spring "transform var(--liquid-glass-motion-spring-duration) var(--liquid-glass-motion-spring-easing)"]
    (str "@supports (transition-timing-function: linear(0, 1)) { "
         (css/rule ".liquid-glass__button,.liquid-glass__icon-button"
                   {:transition (str spring ","
                                     "box-shadow var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing),"
                                     "filter var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing)")})
         " "
         (css/rule ".liquid-glass__toggle-thumb" {:transition spring})
         " "
         (css/rule ".liquid-glass__disclosure-chevron" {:transition spring})
         " }")))

(defn- lens-supports-css
  "SVG displacement-lens upgrade for .liquid-glass--lens: appends
  url(#liquid-glass-lens) (the feTurbulence+feDisplacementMap filter emitted
  by liquid-glass.components/lens-filter-defs) to the regular backdrop chain.
  Feature-tested honestly: only engines that *parse* backdrop-filter url()
  enter the block, and the blur()/saturate() functions stay in the upgraded
  value, so an engine that parses url() but ignores it at paint time
  (@supports can't detect that) still renders the plain-blur material rather
  than losing the backdrop entirely. Engine support (2026): Chromium applies
  backdrop-filter url() — with compositing caveats; Safari/Firefox fall
  through to the base .liquid-glass--lens rule. See docs/design.md."
  []
  (let [v (str "blur(var(--liquid-glass-surface-regular-blur)) "
               "saturate(var(--liquid-glass-surface-regular-saturate)) brightness(1.05) "
               "url(#liquid-glass-lens)")]
    (str "@supports (backdrop-filter: url(#liquid-glass-lens)) { "
         (css/rule ".liquid-glass--lens" {:backdrop-filter v :-webkit-backdrop-filter v})
         " }")))

(defn- reduced-motion-css
  "The prefers-reduced-motion guard, emitted as the *last* block of
  component-css so it out-cascades the equal-specificity @supports upgrades
  (spring transitions) and the base animation rules: every transition and
  presence animation this stylesheet defines is disabled, including the
  [data-state=\"closing\"] exit variants (whose attribute selector would
  otherwise beat a bare class rule on specificity) and the JS pointer
  highlight (also never attached — the reference enhancer script,
  liquid-glass.demo/specular-js, checks the same media query).

  The first rule's selector is `glass-surface-components` itself (every root
  that base-rules gives the universal transform/box-shadow/filter transition
  to) plus the handful of nested sub-elements that carry their OWN separate
  transition (tab, toggle-thumb, progress-bar-fill, disclosure-chevron —
  none of those four are glass-surface roots themselves). Previously this
  was a hand-maintained subset (panel/button/icon-button/tab/toggle-track/
  toggle-thumb/progress-bar-fill/disclosure-chevron) that had drifted out of
  sync with glass-surface-components as new components were added — toolbar
  (and sheet/badge/text-field/text-area/search-field/menu-select/checkbox-
  box/radio-box/stepper/nav-bar/alert/menu/list/chip/disclosure) still
  carried a live transition under reduced-motion. Found auditing
  net-babiniku's nav bar (kotoba-lang/liquid-glass-ui#1)."
  []
  (css/media "(prefers-reduced-motion: reduce)"
             [[(sel (concat glass-surface-components
                            ["tab" "toggle-thumb" "progress-bar-fill" "disclosure-chevron"]))
               {:transition "none"}]
              [(sel overlay-components) {:animation "none"}]
              [(sel overlay-components "[data-state=\"closing\"]") {:animation "none"}]
              [".liquid-glass-js .liquid-glass__specular" {:display "none" :transition "none"}]]))

(defn component-css
  "The Tier B CSS: glass material rules for every liquid-glass__* class,
  generated from EDN declaration maps via kotoba-lang/css (`css.core`).
  Every declaration references `var(--liquid-glass-...)` custom properties
  only (never a literal color/blur value) so `root-css` overrides propagate.
  Ordered: rules + keyframes, the display:none specular default, then the
  feature-tested upgrade blocks (`@supports not (backdrop-filter)` opaque
  fallback, `@supports (transition-timing-function: linear())` spring settle,
  `@supports (backdrop-filter: url())` displacement lens), and *last* the
  `prefers-reduced-motion: reduce` guard so it out-cascades every
  equal-specificity animation/transition above it."
  []
  (str
   (css/css
    {:rules (component-rules)
     :keyframes motion-keyframes})
   "\n.liquid-glass__specular{display:none;}\n" ;; static visual is the ::before overlay; the span upgrades to the pointer highlight only under .liquid-glass-js (see specular-pointer-rules / docs/design.md)
   "\n" (supports-fallback-css)
   "\n" (spring-supports-css)
   "\n" (lens-supports-css)
   "\n" (reduced-motion-css)))

;; --- cascade layer -----------------------------------------------------
;;
;; All library CSS is delivered inside the `kotoba.glass` cascade layer.
;; Per the CSS cascade spec, *unlayered* author CSS beats *layered* author
;; CSS regardless of source order or selector specificity — so a consumer's
;; own stylesheet always wins over this library's rules, even when the
;; library `<style>` is injected at runtime AFTER the app's. This kills the
;; specificity wars consumers previously fought (net-babiniku carried ~100
;; lines of compound-selector overrides like
;; `.liquid-glass__toolbar.app-toolbar` purely to out-specify this library).
;; Apps must NOT write
;; compound selectors against `liquid-glass__*` classes to win specificity
;; anymore — a plain class selector in unlayered app CSS is enough.

(def layer-order
  "The cascade-layer order declaration emitted before the layered bundle:
  `kotoba.hig` (shitsuke's base layer — declaring it here is harmless if
  unused, and pins the relative order when both skins are on a page) below
  `kotoba.glass` (this library). Unlayered app CSS outranks both."
  "@layer kotoba.hig, kotoba.glass;")

(defn layered-css
  "The full material bundle — `root-css` + `component-css` — wrapped in
  `@layer kotoba.glass { ... }`, preceded by the `layer-order` declaration.
  This is the string consumers should inject (or embed via `inline-style` /
  `inline-style-hiccup`, which default to it). Nested at-rules (`@media`,
  `@supports`, `@keyframes`) are valid inside `@layer`, so the whole bundle
  wraps as-is. The raw unwrapped `root-css` / `component-css` stay public for
  tests and advanced pipelines that do their own layering."
  ([] (layered-css (str (root-css) "\n" (component-css))))
  ([css] (str layer-order "\n@layer kotoba.glass {\n" css "\n}")))

(defn inline-style
  "Wrap CSS in a <style> tag for inline SSR embedding. The 0-arity embeds the
  layered bundle (`layered-css`) — use it (or pass `(layered-css ...)`
  yourself) so app CSS stays authoritative; pass raw CSS only when you are
  doing your own layering."
  ([] (inline-style (layered-css)))
  ([css] (str "<style>\n" css "\n</style>")))

(defn inline-style-hiccup
  "Hiccup form of inline-style: [:style [:hiccup/raw css]] — the raw form is
  understood by shitsuke.hiccup/->html so the CSS is not escaped. The 0-arity
  embeds the layered bundle (`layered-css`), same contract as inline-style."
  ([] (inline-style-hiccup (layered-css)))
  ([css] [:style [:hiccup/raw css]]))
