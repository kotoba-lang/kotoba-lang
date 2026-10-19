(ns byoubu.spec
  "Structural + legibility validation for catalog entries.

  `problems` returns a vector of human-readable strings rather than throwing,
  so a test can report every fault in a new backdrop at once instead of one
  per run. The legibility check is part of validation, not a separate lint:
  a backdrop whose own recommended ink fails AA on its own content band is
  malformed in the same sense as one missing a palette key."
  (:require [byoubu.color :as color]
            [byoubu.facts :as facts]
            [clojure.string :as str]))

(def required-palette-keys
  "Every backdrop names the same nine roles. A fixed vocabulary is what lets
  `byoubu.plate` build a plate for any entry without special-casing, and
  what lets two backdrops be compared."
  [:sky-zenith :sky-mid :sky-horizon :haze
   :ridge-far :ridge-near :dune-lit :dune-shadow :star])

(def required-scene-keys [:sky :atmosphere :terrain :camera :grade])

(def textures #{:calm :moderate :busy})

(defn- missing [m ks] (remove #(contains? m %) ks))

(defn problems
  "Vector of problem descriptions for one backdrop; empty means valid."
  [backdrop]
  (let [id      (:byoubu/id backdrop)
        palette (:byoubu/palette backdrop)
        band    (:byoubu/content-band backdrop)
        scene   (:byoubu/scene backdrop)
        pfx     (str (pr-str id) ": ")]
    (vec
     (concat
      (for [k [:byoubu/id :byoubu/title :byoubu/summary :byoubu/tags
               :byoubu/seed :byoubu/texture :byoubu/accent
               :byoubu/palette :byoubu/content-band :byoubu/scene]
            :when (nil? (get backdrop k))]
        (str pfx "missing " k))

      (when-not (keyword? id) [(str pfx "id must be a keyword")])
      (when-not (int? (:byoubu/seed backdrop))
        [(str pfx "seed must be an integer — a backdrop nobody can re-render "
              "is an asset, not a spec")])
      (when-not (contains? textures (:byoubu/texture backdrop))
        [(str pfx "texture must be one of " (str/join "/" (sort textures)))])

      (for [k (missing palette required-palette-keys)]
        (str pfx "palette missing " k))
      (for [[k v] palette
            :when (nil? (color/hex->rgb v))]
        (str pfx "palette " k " is not a hex color: " (pr-str v)))

      (when-not (contains? palette (:byoubu/accent backdrop))
        [(str pfx "accent " (pr-str (:byoubu/accent backdrop))
              " is not a palette key")])

      (for [[k _] band
            :when (not (contains? palette k))]
        (str pfx "content-band references unknown palette key " k))
      (let [total (reduce + 0.0 (map second band))
            drift (- total 1.0)
            drift (if (neg? drift) (- drift) drift)]
        (when (> drift 0.001)
          [(str pfx "content-band weights sum to " total ", not 1.0")]))

      (for [k (missing scene required-scene-keys)]
        (str pfx "scene missing " k))

      ;; Measured tiers, when present, must be well-formed hex.
      (for [[tier m] (select-keys (:byoubu/measured backdrop) [:plate :poster])
            :when (nil? (color/hex->rgb (:content-color m)))]
        (str pfx "measured " tier " content-color is not a hex color: "
             (pr-str (:content-color m))))

      ;; Legibility is structural here, not advisory — and it is checked on
      ;; EVERY tier, because a client does not choose which one it gets.
      (let [f (facts/derive-facts backdrop)
            ink (:byoubu.facts/ink f)]
        (for [[tier c] (facts/tier-colors backdrop)
              :let [r (color/contrast-ratio ink c)]
              :when (< (or r 0.0) facts/wcag-aa-body)]
          (str pfx "recommended ink " ink " on the " (name tier)
               " content band " c " has contrast " r
               ", below AA body " facts/wcag-aa-body)))))))

(defn valid? [backdrop] (empty? (problems backdrop)))
