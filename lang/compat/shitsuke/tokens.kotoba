(ns shitsuke.tokens
  "Design-token IR + resolver + CSS custom-property emitter.

  Token IR is forward-compatible with office-style's :style-ir
  (:office-style/colors, :office-style/fonts) and slides.design's :slides/theme,
  but generalised for web CSS:

    {:shitsuke/colors     {<token> <css-color>}
     :shitsuke/type       {<token> {:font-size ... :font-family ... :weight ... :line-height ...}}
     :shitsuke/spacing    {<token> <css-length>}
     :shitsuke/motion     {<token> {:duration ... :easing ...}}
     :shitsuke/breakpoints {<token> <px>}}

  All values are CSS strings (colors with leading #, lengths with unit). The
  emitter turns tokens into `--shitsuke-<group>-<name>` custom properties on
  :root, so any component can consume `var(--shitsuke-...)`. Portable .cljc,
  zero deps, babashka-safe."
  (:require [clojure.string :as str]))

(def default-tokens
  "Initial web-CSS token set. Deliberately small; dark mode / i18n typography
  are extension points (see docs/design.md), not v1."
  {:shitsuke/colors
   {:ink       "#17202A"
    :muted     "#526170"
    :line      "#D8DEE8"
    :panel     "#F7F8FB"
    :wash       "#FFFFFF"
    :accent    "#496B9A"
    :accent2   "#7C9A4B"
    :danger    "#B46A55"}
   :shitsuke/type
   {:eyebrow   {:font-family "Aptos Display, system-ui, sans-serif" :font-size "10px" :font-weight 700 :color "var(--shitsuke-colors-accent2)"}
    :title     {:font-family "Aptos Display, system-ui, sans-serif" :font-size "38px" :font-weight 700 :color "var(--shitsuke-colors-ink)"}
    :subtitle  {:font-family "Aptos, system-ui, sans-serif"        :font-size "20px" :font-weight 400 :color "var(--shitsuke-colors-muted)"}
    :body      {:font-family "Aptos, system-ui, sans-serif"        :font-size "16px" :font-weight 400 :color "var(--shitsuke-colors-ink)"}
    :caption   {:font-family "Aptos, system-ui, sans-serif"        :font-size "9px"  :font-weight 400 :color "var(--shitsuke-colors-muted)"}}
   :shitsuke/spacing
   {:0 "0px" :1 "4px" :2 "8px" :3 "12px" :4 "16px" :5 "24px" :6 "32px"}
   :shitsuke/motion
   {:fast   {:duration "120ms" :easing "ease-out"}
    :normal {:duration "200ms" :easing "ease-out"}}
   :shitsuke/breakpoints
   {:sm "640px" :md "940px" :lg "1200px"}})

(defn deep-merge
  "Right-biased recursive merge for token maps. Ported from slides.design so
  decks/repos can layer overrides on default-tokens."
  [& maps]
  (letfn [(mrg [a b]
            (cond
              (nil? b) a
              (and (map? a) (map? b)) (merge-with mrg a b)
              :else b))]
    (reduce mrg {} (filter some? maps))))

(defn resolve-tokens
  "default-tokens deep-merged with overrides (a partial token map of the same
  shape). Deck/repo may supply any subset."
  [overrides]
  (deep-merge default-tokens overrides))

(defn- css-var-name [group k]
  (str "--shitsuke-" (name group) "-" (name k)))

(defn- pair->css
  "Emit one `--name: value;` line for a scalar, or recurse into a nested map
  (used by :shitsuke/type entries that are themselves maps of CSS props)."
  [group k v]
  (cond
    (map? v)
    (str/join "\n" (for [[pk pv] v] (str "  " (css-var-name group k) "-" (name pk) ": " pv ";")))
    :else
    (str "  " (css-var-name group k) ": " v ";")))

(defn css-variables
  "Emit a `:root { ... }` CSS string from tokens (default merged with overrides).
  Suitable for an inline <style> in SSR or a generated CSS preamble."
  ([]
   (css-variables nil))
  ([overrides]
   (let [tokens (resolve-tokens overrides)
         body (str/join "\n"
                        (for [[group m] tokens
                              [k v] m
                              :when (some? v)]
                          (pair->css group k v)))]
     (str ":root {\n" body "\n}"))))

(defn normalize-hex
  "slides.design stores colors as hex WITHOUT leading # (office-style.color/*
  convention). CSS needs the #. Pass through if already #ff..., else prepend #."
  [s]
  (let [s (str s)]
    (if (str/starts-with? s "#") s (str "#" s))))

(defn from-slides-design
  "Adapter: build a shitsuke token overrides map from a slides.design deck design
  (:slides/theme colors/fonts + :slides/text-styles). Keeps slides' existing EDN
  design system reusable under the new CSS-var layer."
  [deck-design]
  (let [{:slides/keys [theme text-styles]} deck-design
        colors (:slides/colors theme)
        fonts  (:slides/fonts theme)
        color-tokens (into {}
                           (map (fn [[k v]] [(keyword (name k)) (normalize-hex v)]))
                           colors)
        type-tokens (into {}
                          (map (fn [[id ts]]
                                 [id (cond-> {}
                                        (:slides/font-size ts) (assoc :font-size (str (:slides/font-size ts) "px"))
                                        (:slides/color ts)     (assoc :color (normalize-hex (:slides/color ts)))
                                        (:slides/bold ts)      (assoc :font-weight 700))]))
                          text-styles)
        font-family (or (some-> (:office-style.font/majorFont fonts) (str ", system-ui, sans-serif"))
                        "system-ui, sans-serif")]
    (cond-> {:shitsuke/colors color-tokens}
      (seq type-tokens) (assoc :shitsuke/type
                               (into {} (map (fn [[id ts]] [id (assoc ts :font-family font-family)])) type-tokens)))))
