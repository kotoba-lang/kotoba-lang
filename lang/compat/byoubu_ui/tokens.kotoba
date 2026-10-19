(ns byoubu-ui.tokens
  "Tokens for the plate layer — the small set of decisions that are about
  *mounting* a backdrop rather than about the backdrop itself.

  The colors are not here. They come from `byoubu`, per backdrop, and are
  applied per instance; baking them into tokens would mean a page could
  only ever have one backdrop, and would put the same color in two places.
  What lives here is everything that is the same whichever backdrop is
  mounted: how the plate scales, how long a media upgrade takes to fade in,
  how strong the legibility scrim is.

  Token map -> CSS custom properties, same two-tier shape as
  liquid-glass.tokens / shitsuke.tokens."
  (:require [clojure.string :as str]))

(def default-tokens
  {:byoubu/plate
   {;; The plate is behind everything and must never intercept a click.
    :z-index      "0"
    :content-z    "1"
    ;; Media (poster / loop) covers the plate; `cover` is the only fit that
    ;; keeps a horizon line horizontal across aspect ratios.
    :fit          "cover"
    :position     "center"
    ;; A rendered tier fades in over the gradient tier rather than cutting,
    ;; so a slow network reads as the image arriving, not as a flash.
    :media-fade   "480ms"
    :media-easing "cubic-bezier(0.2, 0, 0, 1)"}

   :byoubu/scrim
   {;; A flat veil between plate and content. The default is 0: a backdrop
    ;; in this catalog already clears AA on its own content band, so the
    ;; scrim is a knob for consumers who put unusually small text on an
    ;; unusually busy backdrop — not a crutch every page pays for.
    :opacity      "0"
    :color        "var(--byoubu-scrim-color, #000)"}})

(defn deep-merge [a b]
  (cond
    (and (map? a) (map? b)) (merge-with deep-merge a b)
    (some? b) b
    :else a))

(defn resolve-tokens
  ([] (resolve-tokens nil))
  ([overrides] (deep-merge default-tokens overrides)))

(defn- var-name [group k]
  (str "--byoubu-" (name group) "-" (name k)))

(defn css-variables
  "`:root{...}` custom properties from the resolved token map."
  ([] (css-variables nil))
  ([overrides]
   (let [tokens (resolve-tokens overrides)]
     (str ":root { "
          (str/join " "
                    (for [[group ks] (sort-by (comp name key) tokens)
                          [k v] (sort-by (comp name key) ks)]
                      (str (var-name (name group) k) ": " v ";")))
          " }"))))

(defn token
  "`var(--byoubu-<group>-<k>)` reference, so style rules never inline a
  literal token value."
  [group k]
  (str "var(" (var-name (name group) k) ")"))
