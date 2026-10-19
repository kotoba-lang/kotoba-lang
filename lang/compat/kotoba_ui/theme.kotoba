(ns kotoba-ui.theme
  "One theme map = the ONLY styling entry an app needs.

  Consumers of this stack used to hand-write hundreds of lines of page CSS
  (invented hex colors, per-appearance palettes, specificity fights) because
  there was no single place to say \"this is my accent, this is my
  appearance\". This namespace is that place:

    {:accent      \"#RRGGBB\"       ;; optional; default = HIG tint (system blue)
     :accent-dark \"#RRGGBB\"       ;; optional; defaults to :accent
     :appearance  :auto|:light|:dark ;; default :auto (follow the OS)
     :hig        {...} :hig-dark  {...}   ;; raw shitsuke.hig token override
     :glass      {...} :glass-dark {...}} ;; raw liquid-glass token override

  `theme-css` threads the accent into shitsuke.hig's `:hig/color :tint`
  token and liquid-glass's `:liquid-glass/accent` token group and emits the
  complete page CSS in canonical order (layer-order declaration exactly once,
  first; then the HIG bundle, the glass bundle, and the shell structural
  rules). The raw `:hig`/`:glass` maps are escape hatches for anything
  beyond the accent — same shapes as each library's own override maps.

  Portable .cljc, zero third-party deps, babashka-safe (no `format`, no
  reader conditionals — hex parsing is done with a digit lookup string)."
  (:require [shitsuke.hig :as hig]
            [shitsuke.tokens :as tokens]
            [liquid-glass.tokens :as glass-tokens]
            [liquid-glass.style :as glass-style]
            [kotoba-ui.shell.style :as shell-style]
            [kotoba-ui.product :as product]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; hex -> rgba (portable: no Long/parseLong / js/parseInt, no reader conditionals)

(def ^:private hex-digits "0123456789abcdef")

(defn- hex-digit [c]
  (str/index-of hex-digits (str/lower-case (str c))))

(defn- hex-byte [s i]
  (+ (* 16 (hex-digit (nth s i))) (hex-digit (nth s (inc i)))))

(defn hex->rgba
  "\"#RRGGBB\" (or \"#RGB\", leading # optional) + alpha (a CSS number
  string, e.g. \"0.55\") -> \"rgba(r,g,b,alpha)\". Pure; used to derive the
  liquid-glass accent tokens from a theme's hex accent with the same alpha
  the library defaults carry."
  [hex alpha]
  (let [h (cond-> hex (str/starts-with? hex "#") (subs 1))
        h (if (= 3 (count h)) (apply str (mapcat (fn [c] [c c]) h)) h)]
    (str "rgba(" (hex-byte h 0) "," (hex-byte h 2) "," (hex-byte h 4) "," alpha ")")))

(defn- rgba-alpha
  "The alpha component of an \"rgba(r,g,b,a)\" string, as the literal
  substring (so \"0.55\" stays \"0.55\")."
  [rgba]
  (second (re-find #",\s*([0-9.]+)\s*\)\s*$" (str rgba))))

;; The accent alphas are read off liquid-glass's own defaults so a themed
;; accent keeps exactly the translucency the material was designed with —
;; if the library retunes its default accent alphas, themes follow.
(def ^:private accent-tint-alpha
  (or (rgba-alpha (get-in glass-tokens/default-tokens [:liquid-glass/accent :tint]))
      "0.55"))

(def ^:private accent-tint-strong-alpha
  (or (rgba-alpha (get-in glass-tokens/default-tokens [:liquid-glass/accent :tint-strong]))
      "0.85"))

;; ---------------------------------------------------------------------------
;; Theme -> per-library override maps

(defn- dark-accent [theme]
  (or (:accent-dark theme) (:accent theme)))

(defn hig-overrides
  "shitsuke.hig override map for the light appearance: the theme accent as
  `{:hig/color {:tint ...}}`, deep-merged with the raw `:hig` escape hatch
  (the raw map wins)."
  [theme]
  (tokens/deep-merge
   (when-let [a (:accent theme)] {:hig/color {:tint a}})
   (:hig theme)))

(defn hig-dark-overrides
  "shitsuke.hig override map for the dark appearance (`:accent-dark`,
  defaulting to `:accent`), deep-merged with the raw `:hig-dark` escape
  hatch."
  [theme]
  (tokens/deep-merge
   (when-let [a (dark-accent theme)] {:hig/color {:tint a}})
   (:hig-dark theme)))

(defn- accent-glass-tokens [accent]
  (when accent
    {:liquid-glass/accent
     {:tint        (hex->rgba accent accent-tint-alpha)
      :tint-strong (hex->rgba accent accent-tint-strong-alpha)}}))

(defn glass-overrides
  "liquid-glass token override map for the light scheme: the theme accent
  converted to the `:liquid-glass/accent` rgba pair (same alphas as the
  library defaults), deep-merged with the raw `:glass` escape hatch."
  [theme]
  (tokens/deep-merge
   (accent-glass-tokens (:accent theme))
   (:glass theme)))

(defn glass-dark-overrides
  "liquid-glass dark-scheme override map (`:accent-dark`, defaulting to
  `:accent`), deep-merged with the raw `:glass-dark` escape hatch. Applied
  inside the dark media query / attribute blocks by liquid-glass's own
  variable-redeclaration mechanism."
  [theme]
  (tokens/deep-merge
   (accent-glass-tokens (dark-accent theme))
   (:glass-dark theme)))

(defn theme-colors
  "The resolved page background per appearance — the color the browser
  should paint its own chrome with (`<meta name=theme-color>`; kotoba-ui.
  shell/page emits the metas from this). Follows the same override chain as
  `theme-css`: shitsuke.hig's `:system-background` semantic color with the
  theme's `:hig` / `:hig-dark` escape hatches threaded in.
  Returns {:light \"#RRGGBB\" :dark \"#RRGGBB\"}."
  [theme]
  {:light (get-in (hig/resolve-hig-tokens (hig-overrides theme))
                  [:hig/color :system-background])
   :dark  (get-in (hig/resolve-dark-hig-tokens (hig-dark-overrides theme))
                  [:hig/color :system-background])})

(defn appearance-attr
  "Value for the `data-appearance` attribute on `:root`/[:html]: nil for
  :auto (follow prefers-color-scheme — attribute omitted), \"light\"/\"dark\"
  for a forced appearance."
  [theme]
  (case (:appearance theme :auto)
    :light "light"
    :dark  "dark"
    nil))

;; ---------------------------------------------------------------------------
;; The full page bundle

(defn theme-css
  "The complete page CSS for a theme map, in canonical order:

    1. the cascade-layer order declaration (exactly once, first)
    2. shitsuke.hig's full bundle — `--hig-*` vars (light + dark + forced
       appearance), element base CSS, the 11 text-style utility classes —
       with the theme's HIG overrides threaded in
    3. liquid-glass's layered material bundle with the theme's accent/glass
       overrides threaded in
    4. kotoba-ui.shell's structural rules

  Apps embed this once per page (kotoba-ui.shell/page does it for you) and
  write only unlayered app CSS on top — which always wins over every rule
  in here, because everything below the order declaration lives inside
  `@layer kotoba.hig` / `@layer kotoba.glass`."
  ([] (theme-css nil))
  ([theme]
   (let [ho  (hig-overrides theme)
         hdo (hig-dark-overrides theme)
         go  (glass-overrides theme)
         gdo (glass-dark-overrides theme)
         ;; liquid-glass's layered bundle prepends its own copy of the
         ;; layer-order declaration; strip it (already emitted once above)
         ;; rather than restate the layer name here.
         glass (str/replace-first
                (glass-style/layered-css
                 (str (glass-style/root-css go gdo) "\n" (glass-style/component-css)))
                (str glass-style/layer-order "\n") "")]
     (str hig/layer-order-css "\n"
          ;; shitsuke.hig/hig-css also opens with layer-order-css; compose
          ;; from its public pieces instead so the declaration stays single.
          "@layer kotoba.hig {\n"
          (hig/css-variables ho) "\n"
          (hig/dark-css-variables ho hdo) "\n"
          "}\n"
          (hig/base-css ho) "\n"
          hig/text-style-classes "\n"
          glass "\n"
          ;; kotoba-ui.product's own rules. Without them the product
          ;; surfaces render as unstyled markup — `empty-state` becomes a
          ;; bare <h3>+<p>, `data-table` loses its scroll container — which
          ;; is what shipped while `product.cljc` was on main but nothing
          ;; pulled its CSS into the bundle.
          (shell-style/shell-css) "\n"
          product/product-css))))
