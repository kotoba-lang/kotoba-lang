(ns shitsuke.style
  "Two-tier styling layer.

  Tier A (portable, this namespace): token → CSS custom properties on :root.
  `root-css` returns the `:root{...}` string; `inline-style` wraps it in a
  <style> tag for SSR. babashka-safe, zero dep.

  Tier B (build-time, shadow-css): scoped class CSS. Components in
  shitsuke.components carry stable class names `shitsuke__<component>`; a
  consumer's :pages build adds `shitsuke.components` to shadow.css.build's
  :include (see slides.build) so the `(css ...)` markers materialise scoped
  rules. This namespace owns the class-name convention + a tiny registry so
  tests can assert stable names without shadow-css at runtime."
  (:require [shitsuke.tokens :as t]
            [clojure.string :as str]))

(defn class-name
  "Stable scoped class for a component: `shitsuke__button`. Used both as the
  hiccup :class and as the shadow-css extraction anchor."
  [component]
  (str "shitsuke__" (name component)))

(defn root-css
  ":root{...} CSS string from tokens (default merged with overrides)."
  ([]
   (t/css-variables))
  ([overrides]
   (t/css-variables overrides)))

(defn inline-style
  "Wrap a CSS string in a <style> tag for inline SSR embedding."
  ([]
   (inline-style (root-css)))
  ([css]
   (str "<style>\n" css "\n</style>")))

(defn inline-style-hiccup
  "Hiccup form of inline-style: [:style [:hiccup/raw css]] (the raw form is
  understood by shitsuke.hiccup/->html so the CSS is not escaped)."
  ([]
   (inline-style-hiccup (root-css)))
  ([css]
   [:style [:hiccup/raw css]]))
