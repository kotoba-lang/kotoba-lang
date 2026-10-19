(ns byoubu-ui.style
  "Structural CSS for the plate, and the per-instance gradient stack.

  Two things are separated here on purpose:

  * `component-rules` / `component-css` — the rules that are the same for
    every backdrop (stacking, cover-fit, reduced-motion, print). One
    stylesheet, emitted once.
  * `plate-style` — the part that differs per backdrop (its gradients),
    emitted as an inline style on the instance. A page with three backdrops
    gets one stylesheet and three small style attributes, not three
    stylesheets.

  Rules are EDN declaration maps rendered by kotoba-lang/css, never
  hand-typed CSS text — same reasoning as liquid-glass.style: a selector and
  its declarations are values, so a test can assert on them, and a rule
  cannot end up syntactically malformed."
  (:require [byoubu.core :as byoubu]
            [byoubu-ui.tokens :as t]
            [css.core :as css]
            [clojure.string :as str]))

(defn class-name
  "Stable class for a part or part--modifier, e.g. (class-name :plate)
  => \"byoubu__plate\". Two-part convention shared with shitsuke.style and
  liquid-glass.style."
  [part]
  (str "byoubu__" (name part)))

(defn root-css
  ([] (root-css nil))
  ([overrides] (t/css-variables overrides)))

;; ---------------------------------------------------------------------------
;; per-instance gradients

(defn- stops-str [stops]
  (str/join ", " (for [{:plate/keys [color at]} stops] (str color " " at))))

(defn- layer-str
  "One byoubu.plate layer -> one CSS gradient function."
  [{:plate/keys [kind direction shape stops]}]
  (case kind
    :linear (str "linear-gradient(" direction ", " (stops-str stops) ")")
    :radial (str "radial-gradient(" shape ", " (stops-str stops) ")")
    (throw (ex-info (str "byoubu-ui: unknown plate layer kind " (pr-str kind))
                    {:kind kind}))))

(defn plate-style
  "Inline style map for one plate: the backdrop's tier-0 gradient stack plus
  the base color underneath it.

  `background-color` is not decoration — without it the plate paints white
  for one frame on a cold load, which is the single most visible way a dark
  backdrop goes wrong."
  [id-or-backdrop]
  {:background-color (byoubu/plate-base-color id-or-backdrop)
   :background-image (str/join ", " (map layer-str (byoubu/plate-layers id-or-backdrop)))})

;; ---------------------------------------------------------------------------
;; shared rules

(defn component-rules
  "[[selector declarations] ...] for the non-media rules."
  []
  [[(str "." (class-name :stage))
    {;; The stage owns a stacking context so the plate can sit at z-index 0
     ;; without competing with whatever the host page already stacks.
     :position   "relative"
     :isolation  "isolate"
     :min-height "100%"}]

   [(str "." (class-name :plate))
    {:position       "absolute"
     :inset          "0"
     :z-index        (t/token :byoubu/plate :z-index)
     ;; Decorative: it must never eat a click, and a screen reader must
     ;; never announce it. `aria-hidden` is set on the element too — both,
     ;; because a consumer may render the plate without this component.
     :pointer-events "none"
     :overflow       "hidden"
     ;; The gradient stack is painted per instance by `plate-style`.
     :background-repeat "no-repeat"
     :background-size   "cover"}]

   [(str "." (class-name :plate-media))
    {:position   "absolute"
     :inset      "0"
     :width      "100%"
     :height     "100%"
     :object-fit (t/token :byoubu/plate :fit)
     :object-position (t/token :byoubu/plate :position)
     ;; Starts transparent; `byoubu__plate-media--ready` fades it in once the
     ;; asset has decoded, so the gradient tier is what the reader sees until
     ;; the real thing is actually there.
     :opacity    "0"
     :transition (str "opacity " (t/token :byoubu/plate :media-fade) " "
                      (t/token :byoubu/plate :media-easing))}]

   [(str "." (class-name "plate-media--ready")) {:opacity "1"}]

   [(str "." (class-name :scrim))
    {:position "absolute"
     :inset    "0"
     :background-color (t/token :byoubu/scrim :color)
     :opacity  (t/token :byoubu/scrim :opacity)
     :pointer-events "none"}]

   [(str "." (class-name :content))
    {:position "relative"
     :z-index  (t/token :byoubu/plate :content-z)}]])

(defn media-rules
  "[[query [[selector declarations] ...]] ...]"
  []
  [["(prefers-reduced-motion: reduce)"
    ;; A looping backdrop is motion the reader did not ask for. The still
    ;; tiers stay: the page keeps its backdrop, it just stops moving.
    [[(str "." (class-name "plate-media--motion")) {:display "none"}]]]

   ["print"
    ;; A full-bleed dark plate is the fastest way to waste a print cartridge.
    [[(str "." (class-name :plate)) {:display "none"}]
     [(str "." (class-name :content)) {:z-index "auto"}]]]])

(defn component-css
  "The complete plate stylesheet as a string, ready to inline in SSR or
  concatenate into main.css. No build step required — same choice
  liquid-glass-ui made for its material."
  []
  (str/join "\n"
            (concat (for [[sel decls] (component-rules)] (css/rule sel decls))
                    (for [[q rules] (media-rules)] (css/media q rules)))))
