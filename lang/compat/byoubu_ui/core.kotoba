(ns byoubu-ui.core
  "Mount a `byoubu` backdrop behind page content, and hand the design system
  the theme that keeps content legible on it.

  Two functions carry the whole library:

    (backdrop {:backdrop :purple-desert} content...)   ;; the plate
    (theme-for :purple-desert)                         ;; the theme map

  `theme-for` is the half that is easy to skip and expensive to skip. The
  plate alone gives a page a picture; the theme is what makes the accent,
  the ink, and the browser chrome agree with that picture — derived from the
  backdrop's palette by `byoubu.facts`, not chosen by hand per page.

  The returned theme is exactly the map `kotoba-ui.core/theme-css` already
  takes, so integration is `(kotoba-ui/theme-css (byoubu-ui/theme-for :id))`
  and this library does not depend on kotoba-ui, shitsuke, or liquid-glass —
  it emits data they already understand. That is what keeps the dependency
  arrow pointing one way and lets an app that does not use the design system
  still mount a plate.

  Hiccup only; no reagent/re-frame seam, because a plate has no state."
  (:require [byoubu.core :as byoubu]
            [byoubu-ui.style :as style]))

(def class-name style/class-name)
(def root-css style/root-css)
(def component-rules style/component-rules)
(def component-css style/component-css)
(def plate-style style/plate-style)

;; ---------------------------------------------------------------------------
;; theme

(defn theme-for
  "The `kotoba-ui` theme map for a backdrop: accent, appearance, ink and
  page background all derived from the backdrop's palette.

  `overrides` (optional) is merged last, so an app can keep its own accent
  while still taking the derived appearance and ink.

  The light and dark HIG overrides are set to the *same* values on purpose.
  The backdrop does not change when the OS switches scheme — the picture
  behind the text is the same picture — so content over it must resolve the
  same way in both. Letting the OS flip the ink here is how you get white
  text on a salt flat."
  ([id-or-backdrop] (theme-for id-or-backdrop nil))
  ([id-or-backdrop overrides]
   (let [f    (byoubu/facts id-or-backdrop)
         ink  (:byoubu.facts/ink f)
         bg   (:byoubu.facts/content-color f)
         acc  (:byoubu.facts/accent f)
         hig  {:hig/color {:label ink :system-background bg}}]
     (merge {:accent      acc
             :accent-dark acc
             :appearance  (:byoubu.facts/appearance f)
             :hig         hig
             :hig-dark    hig}
            overrides))))

(defn glass-surface
  "The liquid-glass surface tier this backdrop wants under content
  (`:thin` / `:regular` / `:thick`). A busier backdrop needs more material
  between itself and the text."
  [id-or-backdrop]
  (:byoubu.facts/glass-surface (byoubu/facts id-or-backdrop)))

;; ---------------------------------------------------------------------------
;; component

(defn- media-element
  "Poster `<img>` or looping `<video>` for the rendered tiers.

  Both start at opacity 0 and are faded in by the `--ready` class once the
  asset has decoded (`onload` / `oncanplay`); until then the reader sees the
  tier-0 gradients. A consumer rendering server-side without JS simply never
  adds the class, and the gradients stay — which is a correct page, not a
  broken one."
  [{:keys [poster loop-src loop-type]}]
  (let [base (class-name :plate-media)]
    (cond
      loop-src
      [:video {:class (str base " " (class-name "plate-media--motion"))
               :poster poster
               ;; Lowercase HTML attribute spellings, not React's camelCase:
               ;; this library emits plain hiccup for SSR, and an HTML
               ;; boolean attribute is true by presence.
               :autoplay true :muted true :loop true :playsinline true
               :preload "metadata"
               :aria-hidden "true"}
       [:source {:src loop-src :type (or loop-type "video/webm")}]]

      poster
      [:img {:class base :src poster :alt "" :aria-hidden "true"
             :decoding "async" :loading "eager"}])))

(defn backdrop
  "Stage with a backdrop plate behind `content`.

  opts:
    :backdrop    backdrop id (or an already-fetched backdrop map) — required
    :assets-base URL prefix the catalog's posters are served under, e.g.
                 \"/assets\". With it, the tier-1 poster is resolved from the
                 catalog manifest — no per-page URL bookkeeping.
    :poster      explicit poster URL; wins over :assets-base
    :loop-src    URL of a looping video (tier 2 delivery), optional
    :loop-type   MIME type for :loop-src, default \"video/webm\"
    :scrim?      add the legibility veil element (opacity comes from tokens)
    :id :class :attrs   passed through to the stage element

  With neither :assets-base nor :poster the stage still paints: tier 0 needs
  no assets. That is the intended default for a page that has not decided how
  it serves static files yet.

  An unknown backdrop id throws here rather than rendering an empty stage:
  a page with no backdrop looks like a styling bug and gets debugged for an
  hour; an exception names the typo."
  [opts & content]
  (let [{:keys [backdrop poster assets-base loop-src loop-type scrim? id class attrs]} opts
        b      (byoubu/fetch (if (map? backdrop) (:byoubu/id backdrop) backdrop))
        poster (or poster
                   (when assets-base (byoubu/poster-url (:byoubu/id b) assets-base)))
        media  (media-element {:poster poster :loop-src loop-src :loop-type loop-type})]
    [:div (merge {:class (cond-> (class-name :stage) class (str " " class))
                  :data-byoubu (name (:byoubu/id b))}
                 (when id {:id id})
                 attrs)
     [:div {:class (class-name :plate)
            :style (plate-style b)
            :aria-hidden "true"}
      media
      (when scrim? [:div {:class (class-name :scrim)}])]
     (into [:div {:class (class-name :content)}] content)]))
