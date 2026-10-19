(ns liquid-glass.tokens
  "Design-token IR for the liquid-glass material, layered next to shitsuke.tokens
  (same IR shape: a map of groups, deep-merge overridable, emitted as `:root`
  CSS custom properties). liquid-glass owns glass-specific groups that
  shitsuke's web-CSS token set does not model:

    {:liquid-glass/surface     {<variant> {:blur ... :saturate ... :tint ... :border ...}}
     :liquid-glass/elevation   {<level>   {:shadow ...}}
     :liquid-glass/specular    {<part>    {...}}
     :liquid-glass/radius      {<size>    <css-length>}
     :liquid-glass/motion      {<phase>   {:duration ... :easing ...}}
     :liquid-glass/accent      {:tint ... :tint-strong ...}
     :liquid-glass/lens        {:frequency ... :scale ... :octaves ...}
     :liquid-glass/ink         {:default ... :shadow ...}}

  `default-tokens` is the light-scheme material. `dark-tokens` is a *partial*
  override map (surface tint/border, specular opacity, and ink — the values a
  glass material actually needs to change when the content behind it goes
  dark) applied under `@media (prefers-color-scheme: dark)` by
  `liquid-glass.style/root-css`, the same var-name-override technique used for
  dark mode in CSS (redeclare the same custom property inside the media query
  rather than emit a second `-dark` variable — components only ever reference
  one name).

  `deep-merge` is reused from `shitsuke.tokens` (a pure, host-independent fn) so
  overrides compose the same way across both token sets. Portable .cljc, zero
  deps beyond shitsuke, babashka-safe."
  (:require [shitsuke.tokens :as shitsuke]
            [clojure.string :as str]))

(defn spring-linear-easing
  "Generate a CSS `linear(...)` easing string approximating a damped spring.

  CSS has no native spring timing function; `linear()` (a piecewise-linear
  easing, Chrome 113+/Safari 17.2+/Firefox 112+) can approximate one from
  sampled points. This is a pure fn so the curve is *generated*, not a magic
  string: it samples the classic under-damped spring step response

      x(t) = 1 - e^(-ζωt) (cos(ω_d t) + (ζω/ω_d) sin(ω_d t)),  ω_d = ω√(1-ζ²)

  at `samples` evenly-spaced points over the normalized duration t ∈ [0,1]
  (evenly-spaced linear() stops need no percentage suffixes). Defaults
  (ζ=0.55, ω=13, 16 points) give one visible overshoot to ~1.12 that settles
  back to 1 — the \"press releases and the glass bounces once\" feel. The
  emitted literal lands in `default-tokens` under `[:liquid-glass/motion
  :spring :easing]` (→ `--liquid-glass-motion-spring-easing`); engines without
  linear() keep the cubic-bezier fallback via the @supports structure in
  liquid-glass.style. Portable .cljc (Math/* works on both hosts); rounding is
  done without `format` so it stays cljs/babashka-safe."
  ([] (spring-linear-easing nil))
  ([{:keys [zeta omega samples] :or {zeta 0.55 omega 13.0 samples 16}}]
   (let [wd  (* omega (Math/sqrt (- 1.0 (* zeta zeta))))
         pos (fn [t] (- 1.0 (* (Math/exp (- (* zeta omega t)))
                               (+ (Math/cos (* wd t))
                                  (* (/ (* zeta omega) wd) (Math/sin (* wd t)))))))
         fmt (fn [v] (str (/ (Math/round (* v 1000.0)) 1000.0)))]
     (str "linear("
          (str/join ","
                    (for [i (range samples)]
                      (fmt (if (= i (dec samples))
                             1.0 ;; clamp the terminal stop so the settle ends exactly at rest
                             (pos (/ i (double (dec samples))))))))
          ")"))))

(def default-tokens
  "v1 light-scheme material. `:clear` (barely-there — sheet scrims, tooltips),
  `:regular` (the default control surface), `:thick` (toolbars/sheets that sit
  over busy content and need more optical separation)."
  {:liquid-glass/surface
   {:clear   {:blur "12px" :saturate "160%" :tint "rgba(255,255,255,0.06)" :border "rgba(255,255,255,0.22)"}
    :regular {:blur "20px" :saturate "180%" :tint "rgba(255,255,255,0.14)" :border "rgba(255,255,255,0.35)"}
    :thick   {:blur "32px" :saturate "200%" :tint "rgba(255,255,255,0.22)" :border "rgba(255,255,255,0.45)"}}
   :liquid-glass/elevation
   {:flat     {:shadow "none"}
    :raised   {:shadow "0 1px 2px rgba(0,0,0,.12), 0 4px 10px rgba(0,0,0,.10)"}
    :overlay  {:shadow "0 8px 24px rgba(0,0,0,.18), 0 2px 6px rgba(0,0,0,.12)"}
    :floating {:shadow "0 20px 48px rgba(0,0,0,.28), 0 6px 16px rgba(0,0,0,.16)"}}
   :liquid-glass/specular
   {:highlight {:opacity "0.55"}
    :rim       {:top-opacity "0.9" :bottom-opacity "0.05"}
    ;; pointer-tracking highlight (progressive-enhancement JS; see
    ;; liquid-glass.style/specular-selector + the reference script inlined
    ;; in liquid-glass.demo/specular-js, ADR-0003)
    :pointer   {:opacity "0.5" :size "160px"}}
   :liquid-glass/radius
   {:sm "10px" :md "16px" :lg "24px" :pill "999px"}
   :liquid-glass/motion
   {:press  {:duration "120ms" :easing "cubic-bezier(.32,.72,0,1)"
             ;; press morph: :active squashes (wider + shorter) instead of a
             ;; flat uniform scale — glass under a fingertip, not a shrink
             :scale-x "1.02" :scale-y ".95"}
    :settle {:duration "420ms" :easing "cubic-bezier(.22,1,.36,1)"}
    ;; overlay presence transitions (scrim/sheet/alert/menu/tooltip):
    ;; enter runs on insertion/initial paint; exit is the
    ;; [data-state="closing"] attribute contract (see docs/design.md)
    :overlay-enter {:duration "300ms" :easing "cubic-bezier(.05,.7,.1,1)" ;; decelerate
                    :distance "12px"  ;; sheet/alert translateY offset
                    :scale ".98"      ;; sheet/alert initial scale
                    :scale-y ".9"}    ;; menu initial scaleY (top origin)
    :overlay-exit  {:duration "200ms" :easing "cubic-bezier(.3,0,.8,.15)"} ;; accelerate
    ;; spring settle (linear() curve, generated — see spring-linear-easing).
    ;; Only applied inside @supports (transition-timing-function: linear(0,1));
    ;; :settle's cubic-bezier stays the baseline elsewhere.
    :spring {:duration "500ms" :easing (spring-linear-easing)}}
   :liquid-glass/accent
   {:tint        "rgba(10,132,255,0.55)"
    :tint-strong "rgba(10,132,255,0.85)"}
   ;; SVG displacement "lens" refraction (components/lens-filter-defs +
   ;; .liquid-glass--lens). NOTE: SVG filter-primitive attributes cannot read
   ;; CSS custom properties, so lens-filter-defs resolves these at hiccup-emit
   ;; time; they are still emitted as --liquid-glass-lens-* vars so the values
   ;; are documented/overridable through the same token pipeline.
   :liquid-glass/lens
   {:frequency "0.008" ;; feTurbulence baseFrequency — lower = broader ripples
    :scale     "8"     ;; feDisplacementMap scale (px) — keep small: it's a lens, not water
    :octaves   "2"}
   :liquid-glass/ink
   {:default "#1c1c1e"
    :shadow  "0 1px 2px rgba(255,255,255,.4)"}})

(def dark-tokens
  "Partial override applied inside `@media (prefers-color-scheme: dark)`. Only
  the entries a dark background actually changes — blur/saturate/radius/motion
  are scheme-independent so they are omitted here (default-tokens values carry
  through unchanged)."
  {:liquid-glass/surface
   {:clear   {:tint "rgba(18,18,22,0.30)"  :border "rgba(255,255,255,0.08)"}
    :regular {:tint "rgba(20,20,24,0.42)"  :border "rgba(255,255,255,0.12)"}
    :thick   {:tint "rgba(16,16,20,0.58)" :border "rgba(255,255,255,0.16)"}}
   :liquid-glass/specular
   {:highlight {:opacity "0.30"}
    :rim       {:top-opacity "0.5" :bottom-opacity "0.02"}
    :pointer   {:opacity "0.28"}}
   :liquid-glass/ink
   {:default "#f5f5f7"
    :shadow  "0 1px 3px rgba(0,0,0,.45)"}})

(def deep-merge
  "Re-exported from shitsuke.tokens: right-biased recursive merge for token maps."
  shitsuke/deep-merge)

(defn resolve-tokens
  "default-tokens deep-merged with overrides (a partial token map of the same shape)."
  [overrides]
  (deep-merge default-tokens overrides))

(defn resolve-dark-tokens
  "dark-tokens deep-merged with dark-overrides (a partial token map, same shape
  as dark-tokens — i.e. only the scheme-sensitive groups)."
  [dark-overrides]
  (deep-merge dark-tokens dark-overrides))

(defn- css-var-name [group k]
  (str "--liquid-glass-" (name group) "-" (name k)))

(defn- pair->css
  [group k v]
  (cond
    (map? v)
    (str/join "\n" (for [[pk pv] v] (str "  " (css-var-name group k) "-" (name pk) ": " pv ";")))
    :else
    (str "  " (css-var-name group k) ": " v ";")))

(defn- tokens->body [tokens]
  (str/join "\n"
            (for [[group m] tokens
                  [k v] m
                  :when (some? v)]
              (pair->css group k v))))

(defn css-variables
  "Emit a `:root { ... }` CSS string from the light material (default merged
  with overrides)."
  ([] (css-variables nil))
  ([overrides] (str ":root {\n" (tokens->body (resolve-tokens overrides)) "\n}")))

(defn dark-css-variables
  "Dark-appearance var blocks, three ways (mirrors shitsuke.hig's
  dark-css-variables — the two token layers must agree on how dark is
  selected, or a page that FORCES dark via `data-appearance=\"dark\"` gets
  hig's dark labels but light-mode glass ink; net-babiniku shipped exactly
  that: #1c1c1e ink on forced-dark glass, an invisible chat panel for every
  visitor whose OS was in light mode):
  1. `@media (prefers-color-scheme: dark) { :root {...} }` — OS preference.
  2. `:root[data-appearance=\"dark\"] {...}` — page forces dark.
  3. `:root[data-appearance=\"light\"] {...}` — forced light beats the dark
     media query (the attribute selector out-specifies bare `:root`)."
  ([] (dark-css-variables nil))
  ([dark-overrides] (dark-css-variables nil dark-overrides))
  ([overrides dark-overrides]
   (let [dark-body (tokens->body (resolve-dark-tokens dark-overrides))
         ;; forced-light resets only the scheme-sensitive groups, shaped by
         ;; the same overrides the light :root block was built from
         light-body (tokens->body (select-keys (resolve-tokens overrides)
                                               (keys (resolve-dark-tokens nil))))]
     (str "@media (prefers-color-scheme: dark) {\n:root {\n" dark-body "\n}\n}\n"
          ":root[data-appearance=\"dark\"] {\n" dark-body "\n}\n"
          ":root[data-appearance=\"light\"] {\n" light-body "\n}"))))
