(ns byoubu.facts
  "Legibility facts derived from a backdrop's palette.

  This is the namespace that makes the catalog worth more than a folder of
  videos. A distributed background asset says nothing about what may be put
  on top of it; every consumer re-guesses the text color, the accent, and
  how opaque the panels need to be, and gets it wrong at least once. Here
  those answers are *computed* from the palette — deterministically, with no
  renderer and no browser — and the computation is testable, so a backdrop
  that would make body text unreadable fails a test instead of shipping.

  Everything is a pure function of the backdrop map, so facts cannot drift
  from the palette they describe: there is no stored copy to forget to
  update."
  (:require [byoubu.color :as color]))

(def ink-candidates
  "The two inks a backdrop may ask content to use. Not pure #fff / #000:
  the light ink is very slightly cool and the dark ink very slightly warm,
  which is what keeps large text off a backdrop from vibrating."
  {:light "#f4f2fa"
   :dark  "#12100f"})

(defn declared-content-color
  "The weighted mix named by `:byoubu/content-band` — what the author claims
  a reader sees behind body content."
  [backdrop]
  (color/mix (for [[k w] (:byoubu/content-band backdrop)]
               [(get-in backdrop [:byoubu/palette k]) w])))

(defn tier-colors
  "tier -> content-band color, for every tier that has been *measured*, plus
  `:declared` as the fallback.

  Measurement matters because the declared weighting was wrong. The first
  four entries were authored as ground-dominated mixes; sampling the rendered
  poster showed the content band is mostly *sky*, three to nineteen times
  brighter than declared, and one backdrop (`:cobalt-dune`) sat at 3.97:1 —
  under AA — while its declared facts claimed 15.42:1. Facts derived from an
  authored guess are a guess with a number printed on it."
  [backdrop]
  (let [m (:byoubu/measured backdrop)]
    (cond-> {:declared (declared-content-color backdrop)}
      (get-in m [:plate :content-color])  (assoc :plate (get-in m [:plate :content-color]))
      (get-in m [:poster :content-color]) (assoc :poster (get-in m [:poster :content-color])))))

(defn content-color
  "The single color that best represents what a reader sees behind body
  content: the measured poster band if there is one, else the measured plate
  band, else the declared mix."
  [backdrop]
  (let [t (tier-colors backdrop)]
    (or (:poster t) (:plate t) (:declared t))))

(defn- best-ink
  "The ink that maximizes the *minimum* contrast across every tier, as
  [key hex min-ratio].

  Worst case rather than representative case, because a client does not
  choose its tier: a cold load gets the gradient plate, a warm one gets the
  poster, and the same text has to be readable on both. Picking ink from one
  tier and shipping the other is how a page ends up legible in review and
  not in production."
  [colors]
  (->> ink-candidates
       (map (fn [[k hex]]
              [k hex (reduce min (map #(or (color/contrast-ratio hex %) 0.0) colors))]))
       (sort-by (fn [[_ _ r]] (- r)))
       first))

(def ^:private texture->surface
  "How much material content needs between itself and the backdrop. A busier
  backdrop needs a thicker glass surface to stay readable — this is the
  token liquid-glass-ui already understands, not a new vocabulary."
  {:calm     :thin
   :moderate :regular
   :busy     :thick})

(defn derive-facts
  "Backdrop -> the facts a UI needs to place content on it:

    :byoubu.facts/content-color    weighted mix behind body content
    :byoubu.facts/luminance        its WCAG relative luminance, 0.0-1.0
    :byoubu.facts/appearance       :dark | :light — which HIG appearance
                                   content should resolve to
    :byoubu.facts/ink              recommended text color
    :byoubu.facts/contrast         WCAG ratio of that ink on that mix
    :byoubu.facts/accent           the palette's accent hex (the backdrop's
                                   own chromatic note, for :hig/color :tint)
    :byoubu.facts/accent-hue       its hue in degrees, for queries
    :byoubu.facts/glass-surface    :thin | :regular | :thick

  `:contrast` is the WORST ratio across every measured tier plus the declared
  fallback, not the ratio on the representative color — see `best-ink`.
  `:tier-contrasts` reports them individually so a caller can see which tier
  is the tight one.

  `:appearance` follows the ink, not the luminance threshold directly: the
  question a consumer actually has is \"which appearance keeps my text
  readable\", and that is exactly the ink comparison."
  [backdrop]
  (let [tiers (tier-colors backdrop)
        bg (content-color backdrop)
        [ink-key ink ratio] (best-ink (vals tiers))
        accent (get-in backdrop [:byoubu/palette (:byoubu/accent backdrop)])]
    {:byoubu.facts/content-color bg
     :byoubu.facts/luminance     (color/relative-luminance bg)
     :byoubu.facts/tier-contrasts
     (into {} (for [[t c] tiers] [t (color/contrast-ratio ink c)]))
     ;; light ink => the surrounding UI is a dark appearance, and vice versa.
     :byoubu.facts/appearance    (if (= ink-key :light) :dark :light)
     :byoubu.facts/ink           ink
     :byoubu.facts/contrast      ratio
     :byoubu.facts/accent        accent
     :byoubu.facts/accent-hue    (color/hue accent)
     :byoubu.facts/glass-surface (get texture->surface
                                      (:byoubu/texture backdrop) :regular)}))

(def wcag-aa-body
  "WCAG 2.x AA for body-size text. `byoubu.spec/problems` refuses to accept a
  backdrop whose derived ink does not clear this against its own content
  band — the catalog cannot contain a backdrop that is unreadable by its own
  recommendation."
  4.5)

(defn readable?
  "Does the derived ink clear AA body contrast on this backdrop?"
  [backdrop]
  (>= (or (:byoubu.facts/contrast (derive-facts backdrop)) 0.0) wcag-aa-body))
