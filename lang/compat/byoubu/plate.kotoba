(ns byoubu.plate
  "Tier-0 plate: the backdrop as pure gradient data, no assets.

  A backdrop ships in three tiers (docs/adr/0001-byoubu.md):

    T0  this namespace — layered gradients derived from the palette. About
        a kilobyte, paints on first frame, works with no network, no GPU,
        no WebGPU support, and under `prefers-reduced-motion`.
    T1  a rendered poster (still) — the literal scene.
    T2  the live WebGPU scene through the kami stack.

  T0 is deliberately *not* a picture of dunes: CSS gradients cannot draw a
  ridge line, and pretending otherwise produces the muddy blobs that make
  gradient backgrounds look cheap. What it reproduces is the vertical light
  structure of the scene (zenith, horizon glow, ground fall-off, vignette),
  with the skyline placed from the same `:camera :pitch-deg` the renderer
  uses.

  It does NOT reproduce the scene's luminance. Measured on 2026-08-02, the
  T0 content band runs about half as bright as the rendered T1 poster on
  every dark backdrop (purple-desert 0.060 vs 0.132) and slightly brighter on
  the light one. An earlier draft of this docstring claimed the two tiers sit
  at the same luminance; sampling both said otherwise. What IS guaranteed,
  and tested, is that every tier resolves to the same ink and the same
  appearance, and that each one independently clears AA body contrast with
  that ink — see `byoubu.facts/derive-facts`, which picks ink by the worst
  tier rather than a representative one.

  Layers are returned as data, not CSS strings. byoubu-ui turns them into a
  `background-image` stack via css.core; tests assert against the layer maps
  directly. This is the same reason liquid-glass-ui keeps its rules as EDN:
  a string fragment can be silently malformed, a value cannot."
  (:require [byoubu.color :as color]))

(defn- stop [color at] {:plate/color color :plate/at at})

(defn- p [backdrop k] (get-in backdrop [:byoubu/palette k]))

(defn- horizon-pct
  "Where the horizon sits, as a percentage of plate height. Derived from the
  camera pitch so T0 and the rendered scene put the skyline in the same
  place: a level camera puts it at 62%, and each degree of downward pitch
  pushes it down."
  [backdrop]
  (let [pitch (or (get-in backdrop [:byoubu/scene :camera :pitch-deg]) 0.0)]
    (max 40.0 (min 80.0 (- 62.0 (* 2.6 pitch))))))

(defn layers
  "Ordered background layers, topmost first (CSS `background-image` order).

  Each layer is one of:
    {:plate/kind :linear :plate/direction \"to bottom\" :plate/stops [...]}
    {:plate/kind :radial :plate/shape \"70% 45% at 50% 62%\" :plate/stops [...]}

  and each stop is {:plate/color <css color> :plate/at <css length|percent>}."
  [backdrop]
  (let [h      (horizon-pct backdrop)
        vign   (or (get-in backdrop [:byoubu/scene :grade :vignette]) 0.25)
        ;; Integer percent on purpose: `(/ (Math/round ..) 10.0)` prints
        ;; "62.0" on the JVM and "62" in ClojureScript, and a portable
        ;; library whose output differs per runtime cannot be tested once.
        pct    (fn [x] (str (int (Math/round (double x))) "%"))]
    [;; 1. vignette — postfx's grade, as the outermost darkening
     {:plate/kind  :radial
      :plate/shape "120% 100% at 50% 45%"
      :plate/stops [(stop "rgba(0,0,0,0)" "45%")
                    (stop (color/rgba (p backdrop :dune-shadow) (str vign)) "100%")]}

     ;; 2. horizon afterglow — the chromatic note; the accent lives here
     {:plate/kind  :radial
      :plate/shape (str "80% 34% at 50% " (pct h))
      :plate/stops [(stop (color/rgba (p backdrop :sky-horizon) "0.85") "0%")
                    (stop (color/rgba (p backdrop :haze) "0.35") "45%")
                    (stop (color/rgba (p backdrop :haze) "0") "100%")]}

     ;; 3. ground — from the horizon down; this is the band content sits on
     {:plate/kind      :linear
      :plate/direction "to bottom"
      :plate/stops     [(stop (color/rgba (p backdrop :ridge-near) "0") (pct (- h 1.0)))
                        (stop (p backdrop :ridge-near) (pct (+ h 0.5)))
                        (stop (p backdrop :dune-lit) (pct (+ h (* 0.35 (- 100.0 h)))))
                        (stop (p backdrop :dune-shadow) "100%")]}

     ;; 4. sky — the base gradient, bottom of the stack
     {:plate/kind      :linear
      :plate/direction "to bottom"
      :plate/stops     [(stop (p backdrop :sky-zenith) "0%")
                        (stop (p backdrop :sky-mid) (pct (* 0.62 h)))
                        (stop (p backdrop :sky-horizon) (pct h))
                        (stop (p backdrop :dune-shadow) "100%")]}]))

(defn base-color
  "The single color to paint under the layers (`background-color`), so a
  plate never flashes white before its gradients resolve."
  [backdrop]
  (p backdrop :dune-shadow))
