(ns shitsuke.hig
  "Apple-HIG semantic token layer + base CSS.

  THE single source of truth for Apple Human Interface Guidelines-grade
  typography (the 11 UIKit text styles + an opt-in fluid display scale
  :display1-3 above :large-title), semantic colors (light + dark),
  system palette, spacing (4pt grid), radius, and element-level base CSS.
  Everything is emitted as `--hig-*` CSS custom properties and element rules
  inside the CSS cascade layer `kotoba.hig`.

  Cascade-layer contract (see `layer-order-css`):

    @layer kotoba.hig, kotoba.glass;

  `kotoba.hig` (this namespace) is the base; `kotoba.glass` is filled by
  liquid-glass-ui's material layer; app CSS stays UNLAYERED so it always beats
  both layers. Forced appearance: a page sets `data-appearance=\"dark\"` (or
  `\"light\"`) on `:root` to override the `prefers-color-scheme` media query.

  Additive to `shitsuke.tokens` (v1 `--shitsuke-*` vars stay for existing
  consumers; only `deep-merge` is reused). Portable .cljc, zero deps,
  babashka-safe (string building via str/clojure.string, no `format`)."
  (:require [shitsuke.tokens :as t]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Font stacks

(def font-family-text
  "SF Pro Text stack (HIG text sizes, < 20px). Emitted as `--hig-font-text`."
  "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"Hiragino Sans\", \"Noto Sans JP\", system-ui, sans-serif")

(def font-family-display
  "SF Pro Display stack (HIG display sizes, >= 20px). Emitted as
  `--hig-font-display`."
  "-apple-system, BlinkMacSystemFont, \"SF Pro Display\", \"Hiragino Sans\", \"Noto Sans JP\", system-ui, sans-serif")

(def font-family-mono
  "Monospace stack (code/pre, `.hig-mono`). Emitted as `--hig-font-mono`."
  "ui-monospace, \"SF Mono\", SFMono-Regular, Menlo, Consolas, monospace")

;; ---------------------------------------------------------------------------
;; Typography — Apple's 11 text styles (published UIKit default point sizes)

(def text-style-order
  "The 11 Apple text styles, largest first (stable emission/class order)."
  [:large-title :title1 :title2 :title3 :headline :body :callout
   :subheadline :footnote :caption1 :caption2])

(def text-styles
  "Apple text styles: px font-size / line-height, HIG default font-weight."
  {:large-title {:font-size "34px" :line-height "41px" :font-weight 400}
   :title1      {:font-size "28px" :line-height "34px" :font-weight 400}
   :title2      {:font-size "22px" :line-height "28px" :font-weight 400}
   :title3      {:font-size "20px" :line-height "25px" :font-weight 400}
   :headline    {:font-size "17px" :line-height "22px" :font-weight 600}
   :body        {:font-size "17px" :line-height "22px" :font-weight 400}
   :callout     {:font-size "16px" :line-height "21px" :font-weight 400}
   :subheadline {:font-size "15px" :line-height "20px" :font-weight 400}
   :footnote    {:font-size "13px" :line-height "18px" :font-weight 400}
   :caption1    {:font-size "12px" :line-height "16px" :font-weight 400}
   :caption2    {:font-size "11px" :line-height "13px" :font-weight 400}})

(def ^:private display-styles
  "Styles at >= 20px use the SF Pro Display stack; the rest use SF Pro Text."
  #{:large-title :title1 :title2 :title3})

;; ---------------------------------------------------------------------------
;; Display scale — hero/marketing sizes above :large-title (web-only, opt-in)

(def display-style-order
  "The display-scale styles, largest first (stable emission/class order).
  These sit ABOVE the 11 Apple text styles: the web analogue of the larger
  display styles some Apple platforms add (e.g. visionOS/tvOS extra-large
  titles). Opt-in only — `base-css` keeps h1 at :large-title; use the
  `.hig-display*` classes (or the `--hig-text-display*-*` vars) explicitly."
  [:display3 :display2 :display1])

(def display-text-styles
  "Display-scale text styles for hero/marketing typography. All weight 700 on
  the SF Pro Display stack, with FLUID font sizes so one token works from
  phone to desktop without hand-written clamp()s in apps.

  Fluid formula (per style, max size M in px):

    font-size: clamp(5M/8, (5M/64)vw + M/8, M)

  i.e. min = 62.5% of max; the preferred value ramps linearly from min at a
  640px viewport to max at a 1120px viewport. Concretely:

    :display3  clamp(40px, 5vw + 8px, 64px)       ; 64/68
    :display2  clamp(30px, 3.75vw + 6px, 48px)    ; 48/52
    :display1  clamp(25px, 3.125vw + 5px, 40px)   ; 40/44

  Line-height is `calc(1em + 4px)` so it tracks the fluid font-size and
  equals the design pairs at max size (68px, 52px, 44px). Letter-spacing
  tightens as size grows (display type is tracked tighter):
  :display1 -0.01em, :display2 -0.015em, :display3 -0.02em.

  Pure data — values are plain CSS strings; merged into the :hig/text token
  group by `default-hig-tokens` (vars emit as `--hig-text-display3-*` etc.)."
  {:display3 {:font-size      "clamp(40px, 5vw + 8px, 64px)"
              :line-height    "calc(1em + 4px)"
              :font-weight    700
              :letter-spacing "-0.02em"}
   :display2 {:font-size      "clamp(30px, 3.75vw + 6px, 48px)"
              :line-height    "calc(1em + 4px)"
              :font-weight    700
              :letter-spacing "-0.015em"}
   :display1 {:font-size      "clamp(25px, 3.125vw + 5px, 40px)"
              :line-height    "calc(1em + 4px)"
              :font-weight    700
              :letter-spacing "-0.01em"}})

;; ---------------------------------------------------------------------------
;; Semantic colors + system palette (Apple's published UIKit values)

(def semantic-colors
  "UIKit semantic color tokens: token -> {:light <css-color> :dark <css-color>}.
  :tint is the default accent (#007AFF/#0A84FF = systemBlue) — the theme
  override point."
  {:label                               {:light "#000000" :dark "#FFFFFF"}
   :secondary-label                     {:light "rgba(60,60,67,0.6)" :dark "rgba(235,235,245,0.6)"}
   :tertiary-label                      {:light "rgba(60,60,67,0.3)" :dark "rgba(235,235,245,0.3)"}
   :quaternary-label                    {:light "rgba(60,60,67,0.18)" :dark "rgba(235,235,245,0.16)"}
   :placeholder-text                    {:light "rgba(60,60,67,0.3)" :dark "rgba(235,235,245,0.3)"}
   :system-background                   {:light "#FFFFFF" :dark "#000000"}
   :secondary-system-background         {:light "#F2F2F7" :dark "#1C1C1E"}
   :tertiary-system-background          {:light "#FFFFFF" :dark "#2C2C2E"}
   :system-grouped-background           {:light "#F2F2F7" :dark "#000000"}
   :secondary-system-grouped-background {:light "#FFFFFF" :dark "#1C1C1E"}
   :tertiary-system-grouped-background  {:light "#F2F2F7" :dark "#2C2C2E"}
   :separator                           {:light "rgba(60,60,67,0.36)" :dark "rgba(84,84,88,0.65)"}
   :opaque-separator                    {:light "#C6C6C8" :dark "#38383A"}
   :system-fill                         {:light "rgba(120,120,128,0.2)" :dark "rgba(120,120,128,0.36)"}
   :secondary-system-fill               {:light "rgba(120,120,128,0.16)" :dark "rgba(120,120,128,0.32)"}
   :tertiary-system-fill                {:light "rgba(118,118,128,0.12)" :dark "rgba(118,118,128,0.24)"}
   :quaternary-system-fill              {:light "rgba(116,116,128,0.08)" :dark "rgba(118,118,128,0.18)"}
   :tint                                {:light "#007AFF" :dark "#0A84FF"}})

(def system-palette
  "UIKit system colors: token -> {:light <css-color> :dark <css-color>}."
  {:red    {:light "#FF3B30" :dark "#FF453A"}
   :orange {:light "#FF9500" :dark "#FF9F0A"}
   :yellow {:light "#FFCC00" :dark "#FFD60A"}
   :green  {:light "#34C759" :dark "#30D158"}
   :mint   {:light "#00C7BE" :dark "#63E6E2"}
   :teal   {:light "#30B0C7" :dark "#40CBE0"}
   :cyan   {:light "#32ADE6" :dark "#64D2FF"}
   :blue   {:light "#007AFF" :dark "#0A84FF"}
   :indigo {:light "#5856D6" :dark "#5E5CE6"}
   :purple {:light "#AF52DE" :dark "#BF5AF2"}
   :pink   {:light "#FF2D55" :dark "#FF375F"}
   :brown  {:light "#A2845E" :dark "#AC8E68"}
   :gray   {:light "#8E8E93" :dark "#8E8E93"}
   :gray2  {:light "#AEAEB2" :dark "#636366"}
   :gray3  {:light "#C7C7CC" :dark "#48484A"}
   :gray4  {:light "#D1D1D6" :dark "#3A3A3C"}
   :gray5  {:light "#E5E5EA" :dark "#2C2C2E"}
   :gray6  {:light "#F2F2F7" :dark "#1C1C1E"}})

(defn- pick-appearance
  "{token {:light l :dark d}} -> {token <value for side>}."
  [m side]
  (into {} (map (fn [[k v]] [k (get v side)])) m))

;; ---------------------------------------------------------------------------
;; Token maps

(def default-hig-tokens
  "The HIG token set, light appearance. One map:
  {:hig/font {...} :hig/text {...} :hig/color {...} :hig/palette {...}
   :hig/spacing {...} :hig/radius {...} :hig/hairline \"0.5px\"}

  :hig/font is the three font stacks as first-class tokens
  (--hig-font-text / --hig-font-display / --hig-font-mono); the per-style
  :hig/text font-family values reference them via var(), so overriding a
  stack in one place re-fonts every style that uses it."
  {:hig/font
   {:text    font-family-text
    :display font-family-display
    :mono    font-family-mono}
   :hig/text
   (into (into {}
               (map (fn [[style props]]
                      [style (assoc props :font-family
                                    (if (contains? display-styles style)
                                      "var(--hig-font-display)"
                                      "var(--hig-font-text)"))]))
               text-styles)
         (map (fn [[style props]]
                [style (assoc props :font-family "var(--hig-font-display)")]))
         display-text-styles)
   :hig/color   (pick-appearance semantic-colors :light)
   :hig/palette (pick-appearance system-palette :light)
   :hig/spacing
   {:1 "4px" :2 "8px" :3 "12px" :4 "16px" :5 "20px"
    :6 "24px" :7 "32px" :8 "40px" :9 "48px" :10 "64px"
    :content-margin "16px"}
   :hig/radius
   {:xs "6px" :sm "10px" :md "14px" :lg "20px" :xl "28px" :capsule "999px"}
   :hig/hairline "0.5px"})

(def dark-hig-tokens
  "Partial override map: the dark-appearance color/palette values."
  {:hig/color   (pick-appearance semantic-colors :dark)
   :hig/palette (pick-appearance system-palette :dark)})

(defn resolve-hig-tokens
  "default-hig-tokens deep-merged with overrides (a partial map of the same
  shape)."
  ([] (resolve-hig-tokens nil))
  ([overrides]
   (t/deep-merge default-hig-tokens overrides)))

(defn resolve-dark-hig-tokens
  "default-hig-tokens deep-merged with dark-hig-tokens, then overrides."
  ([] (resolve-dark-hig-tokens nil))
  ([overrides]
   (t/deep-merge default-hig-tokens dark-hig-tokens overrides)))

;; ---------------------------------------------------------------------------
;; CSS custom-property emission

(defn- css-var-name [group k]
  (str "--hig-" (name group) "-" (name k)))

(defn- pair->css
  "One `--hig-...: value;` line for a scalar; nested maps (text styles) expand
  per-prop (`--hig-text-body-font-size`)."
  [group k v]
  (if (map? v)
    (str/join "\n" (for [[pk pv] v]
                     (str "  " (css-var-name group k) "-" (name pk) ": " pv ";")))
    (str "  " (css-var-name group k) ": " v ";")))

(defn- tokens->css-lines
  "All `--hig-*` declaration lines for a token map. A scalar group value
  (e.g. :hig/hairline) emits a single `--hig-<group>` var."
  [tokens]
  (str/join "\n"
            (for [[group m] tokens
                  :when (some? m)]
              (if (map? m)
                (str/join "\n" (for [[k v] m :when (some? v)]
                                 (pair->css group k v)))
                (str "  --hig-" (name group) ": " m ";")))))

(defn css-variables
  "Emit `:root { --hig-...: ...; }` (light appearance) from tokens
  (default-hig-tokens merged with overrides)."
  ([] (css-variables nil))
  ([overrides]
   (str ":root {\n" (tokens->css-lines (resolve-hig-tokens overrides)) "\n}")))

(def ^:private appearance-groups [:hig/color :hig/palette])

(defn dark-css-variables
  "Dark-appearance var blocks, three ways:
  1. `@media (prefers-color-scheme: dark) { :root {...} }` — OS preference.
  2. `:root[data-appearance=\"dark\"] {...}` — page forces dark via attribute.
  3. `:root[data-appearance=\"light\"] {...}` — resets to light values, so a
     forced light attribute wins over the dark media query (the attribute
     selector out-specifies the bare `:root` inside the media query).

  2-arity `(dark-css-variables overrides dark-overrides)`: `overrides` shapes
  the forced-light reset (same map as `css-variables`), `dark-overrides` the
  dark values. 1-arity treats the argument as dark-overrides."
  ([] (dark-css-variables nil nil))
  ([dark-overrides] (dark-css-variables nil dark-overrides))
  ([overrides dark-overrides]
   (let [dark  (select-keys (t/deep-merge dark-hig-tokens dark-overrides)
                            appearance-groups)
         light (select-keys (resolve-hig-tokens overrides)
                            appearance-groups)
         dark-body  (tokens->css-lines dark)
         light-body (tokens->css-lines light)]
     (str "@media (prefers-color-scheme: dark) {\n"
          ":root {\n" dark-body "\n}\n"
          "}\n"
          ":root[data-appearance=\"dark\"] {\n" dark-body "\n}\n"
          ":root[data-appearance=\"light\"] {\n" light-body "\n}"))))

;; ---------------------------------------------------------------------------
;; Cascade layer + element-level base CSS

(def layer-order-css
  "Cascade-layer order declaration. kotoba.hig is this base layer;
  kotoba.glass is filled by liquid-glass-ui; app CSS stays unlayered so it
  always beats both layers. Emit this FIRST (before any @layer block)."
  "@layer kotoba.hig, kotoba.glass;")

(defn- text-style-props
  "CSS declarations that apply one text style via its `--hig-text-*` vars.
  `weight` (optional) overrides the token font-weight (headings)."
  ([style] (text-style-props style nil))
  ([style weight]
   (let [n (name style)]
     (str "  font-family: var(--hig-text-" n "-font-family);\n"
          "  font-size: var(--hig-text-" n "-font-size);\n"
          "  line-height: var(--hig-text-" n "-line-height);\n"
          "  font-weight: " (or weight (str "var(--hig-text-" n "-font-weight)")) ";\n"
          "  letter-spacing: var(--hig-text-" n "-letter-spacing, normal);"))))

(defn base-css
  "Element-level defaults wrapped in `@layer kotoba.hig { ... }`. Everything
  references `--hig-*` vars, so appearance/overrides flow in via
  css-variables / dark-css-variables; margins sit on the 4pt grid."
  ([] (base-css nil))
  ([_overrides]
   (str
    "@layer kotoba.hig {\n"
    ":root { color-scheme: light dark; }\n"
    ":root[data-appearance=\"dark\"] { color-scheme: dark; }\n"
    ":root[data-appearance=\"light\"] { color-scheme: light; }\n"
    "body {\n"
    "  margin: 0;\n"
    "  background: var(--hig-color-system-background);\n"
    "  color: var(--hig-color-label);\n"
    (text-style-props :body) "\n"
    "  -webkit-font-smoothing: antialiased;\n"
    "  text-rendering: optimizeLegibility;\n"
    "}\n"
    "h1 {\n" (text-style-props :large-title 700) "\n  margin: 0 0 12px;\n}\n"
    "h2 {\n" (text-style-props :title1 700) "\n  margin: 0 0 12px;\n}\n"
    "h3 {\n" (text-style-props :title2 600) "\n  margin: 0 0 8px;\n}\n"
    "h4 {\n" (text-style-props :headline) "\n  margin: 0 0 8px;\n}\n"
    "p, ul, ol {\n  margin: 0 0 16px;\n}\n"
    "small {\n" (text-style-props :footnote) "\n}\n"
    "code, pre {\n"
    "  font-family: var(--hig-font-mono);\n"
    "  font-size: var(--hig-text-footnote-font-size);\n"
    "  background: var(--hig-color-secondary-system-background);\n"
    "  border-radius: var(--hig-radius-xs);\n"
    "  padding: 2px 5px;\n"
    "}\n"
    "a {\n"
    "  color: var(--hig-color-tint);\n"
    "  text-decoration: none;\n"
    "}\n"
    "a:hover {\n"
    "  text-decoration: underline;\n"
    "}\n"
    "hr {\n"
    "  border: none;\n"
    "  border-top: var(--hig-hairline) solid var(--hig-color-separator);\n"
    "}\n"
    "::selection {\n"
    "  background: color-mix(in srgb, var(--hig-color-tint) 25%, transparent);\n"
    "}\n"
    ":focus-visible {\n"
    "  outline: 2px solid var(--hig-color-tint);\n"
    "  outline-offset: 2px;\n"
    "}\n"
    "@media (prefers-reduced-motion: reduce) {\n"
    "  * { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }\n"
    "}\n"
    "}")))

(def text-style-classes
  "Utility class rules `.hig-display3` ... `.hig-display1` (fluid display
  scale, opt-in — base-css keeps h1 at :large-title) and `.hig-large-title`
  ... `.hig-caption2` (one per Apple text style) plus `.hig-mono` (mono stack
  + footnote size, the code/pre typography treatment), inside
  `@layer kotoba.hig`."
  (str "@layer kotoba.hig {\n"
       (str/join "\n"
                 (for [style (concat display-style-order text-style-order)]
                   (str ".hig-" (name style) " {\n"
                        (text-style-props style) "\n}")))
       "\n.hig-mono {\n"
       "  font-family: var(--hig-font-mono);\n"
       "  font-size: var(--hig-text-footnote-font-size);\n"
       "}"
       "\n}"))

;; ---------------------------------------------------------------------------
;; Bundle

(defn hig-css
  "The full HIG CSS bundle in correct order: layer-order declaration, then
  `--hig-*` vars (light + dark, layered), then element base CSS, then the
  text-style utility classes."
  ([] (hig-css nil nil))
  ([overrides] (hig-css overrides nil))
  ([overrides dark-overrides]
   (str layer-order-css "\n"
        "@layer kotoba.hig {\n"
        (css-variables overrides) "\n"
        (dark-css-variables overrides dark-overrides) "\n"
        "}\n"
        (base-css overrides) "\n"
        text-style-classes)))

(defn inline-style
  "Wrap a CSS string (default: the full hig-css bundle) in a <style> tag for
  inline SSR embedding (mirrors shitsuke.style/inline-style)."
  ([] (inline-style (hig-css)))
  ([css] (str "<style>\n" css "\n</style>")))

(defn inline-style-hiccup
  "Hiccup form of inline-style: [:style [:hiccup/raw css]] (raw so the CSS is
  not escaped by shitsuke.hiccup/->html)."
  ([] (inline-style-hiccup (hig-css)))
  ([css] [:style [:hiccup/raw css]]))
