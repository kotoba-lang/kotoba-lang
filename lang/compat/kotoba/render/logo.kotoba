(ns kotoba.render.logo
  "KAMI Engine boot logo + splash-screen timing state machine.
   Ported from `kami-render/src/logo.rs`. (The Rust doc comment says
   'rendered as GPU quad with fade-in animation' — the GPU quad/draw call
   is host-adapter; the SVG data, colors, and pure fade/progress timing
   math below are fully portable.)")

(def logo-svg
  "KAMI Engine logo as inline SVG string."
  "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 200\">
  <defs>
    <linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">
      <stop offset=\"0%\" stop-color=\"#f59e0b\"/>
      <stop offset=\"100%\" stop-color=\"#d97706\"/>
    </linearGradient>
  </defs>
  <!-- Torii gate -->
  <rect x=\"120\" y=\"40\" width=\"160\" height=\"12\" rx=\"6\" fill=\"url(#g)\"/>
  <rect x=\"130\" y=\"52\" width=\"140\" height=\"8\" rx=\"4\" fill=\"url(#g)\"/>
  <rect x=\"140\" y=\"60\" width=\"12\" height=\"80\" fill=\"url(#g)\"/>
  <rect x=\"248\" y=\"60\" width=\"12\" height=\"80\" fill=\"url(#g)\"/>
  <rect x=\"132\" y=\"90\" width=\"136\" height=\"8\" rx=\"4\" fill=\"url(#g)\"/>
  <!-- Text -->
  <text x=\"200\" y=\"170\" text-anchor=\"middle\" font-family=\"system-ui,sans-serif\" font-size=\"28\" font-weight=\"700\" fill=\"#f59e0b\" letter-spacing=\"8\">KAMI ENGINE</text>
  <text x=\"200\" y=\"190\" text-anchor=\"middle\" font-family=\"system-ui,sans-serif\" font-size=\"10\" fill=\"#888\" letter-spacing=\"4\">NEXT-GEN GAME PLATFORM</text>
</svg>")

(def brand-color
  "Logo brand color (amber, #f59e0b) as RGBA floats."
  [0.961 0.620 0.043 1.0])

(def splash-bg
  "Splash screen background color as RGBA floats."
  [0.05 0.05 0.07 1.0])

(defn splash-screen
  "New splash-screen timer state: 2.0s total, 0.5s fade-in, 0.3s fade-out."
  []
  {:elapsed 0.0 :duration 2.0 :fade-in 0.5 :fade-out 0.3})

(defn tick
  "Advance the splash timer by `dt` seconds. Returns updated state."
  [splash dt]
  (update splash :elapsed + dt))

(defn opacity
  "Current opacity (0.0 -> 1.0 -> 0.0)."
  [{:keys [elapsed duration fade-in fade-out]}]
  (cond
    (< elapsed fade-in) (/ elapsed fade-in)
    (> elapsed (- duration fade-out)) (/ (- duration elapsed) fade-out)
    :else 1.0))

(defn progress
  "Progress bar fraction, clamped [0.0, 1.0]."
  [{:keys [elapsed duration]}]
  (max 0.0 (min 1.0 (/ elapsed duration))))

(defn done?
  [{:keys [elapsed duration]}]
  (>= elapsed duration))
