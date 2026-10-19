(ns byoubu.catalog
  "The backdrop catalog — the SSoT of this library.

  A backdrop is *data*, not a file. Each entry carries four things:

    :byoubu/palette      the colors the backdrop is made of
    :byoubu/content-band what a reader actually sees behind body content
    :byoubu/scene        the render spec, in the vocabulary the existing
                         kami stack already speaks (sky / atmosphere /
                         terrain biome / camera / grade)
    :byoubu/seed         + :byoubu/generator, so the scene is reproducible

  The consequence of the scene being a spec rather than an mp4 is that a
  backdrop can be re-derived: recolored, re-seeded, re-rendered at another
  resolution, or forked into a sibling. A distributed video can only be
  cropped.

  Nothing here depends on a renderer. `byoubu.facts` computes legibility
  facts from the palette alone, and `byoubu.plate` derives a CSS-gradient
  approximation that works with no assets at all — so a consumer that only
  wants \"a backdrop and text that stays readable on it\" never pulls in the
  3D stack. See docs/adr/0001-byoubu.md for the tier split.

  IDs are stable and immutable once published: consumers pin them. A
  changed look is a new id, not an edited entry."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Scene vocabulary note
;;
;; :byoubu/scene keys are plain EDN in the vocabulary of kotoba-lang/sky,
;; kotoba-lang/atmosphere, kotoba-lang/terrain (`:biome` values come from
;; kami-terrain-scene's biomes.edn — :desert is a real preset there) and
;; kotoba-lang/postfx. This library does NOT depend on those repos: it
;; describes scenes in their language and lets the renderer — which does
;; depend on them, behind the :render alias — bind the description. Keeping
;; the catalog dependency-free is what lets a plain web app read it.

(def catalog
  "id -> backdrop. See the ns docstring for the contract each entry keeps."
  {:purple-desert
   {:byoubu/id      :purple-desert
    :byoubu/title   "Purple Desert"
    :byoubu/summary "Violet astronomical twilight over dune ridges; the last
                     afterglow sits on the horizon and the sand reads as
                     silhouette."
    :byoubu/tags    #{:night :desert :dune :starfield :twilight :cool}
    :byoubu/seed    8213
    :byoubu/texture :calm
    :byoubu/accent  :sky-horizon
    :byoubu/palette {:sky-zenith   "#07060f"
                     :sky-mid      "#221a3d"
                     :sky-horizon  "#a887cf"
                     :haze         "#5d4b86"
                     :ridge-far    "#2a2340"
                     :ridge-near   "#151122"
                     :dune-lit     "#332a4a"
                     :dune-shadow  "#0d0a15"
                     :star         "#e9e4ff"}
    :byoubu/content-band [[:sky-mid 0.30] [:dune-shadow 0.45] [:ridge-near 0.25]]
    :byoubu/measured
    {:method "mean sRGB over the content band — full width, 30%–75% of frame
              height — rasterized and sampled in Chrome"
     :date   "2026-08-02"
     :plate  {:content-color "#4d3d6c" :luminance 0.06}
     :poster {:content-color "#725b94" :luminance 0.132}}
    :byoubu/scene   {:sky        {:model :twilight-scatter
                                  :sun-elevation-deg -8.5
                                  :turbidity 2.1
                                  :stars {:density 0.55 :magnitude-limit 5.2}}
                     :atmosphere {:haze-density 0.22 :wind {:speed 1.4 :bearing-deg 285}}
                     :terrain    {:biome :desert :ridge-count 3
                                  :dune-wavelength 190.0 :dune-amplitude 0.42}
                     :camera     {:height 34.0 :pitch-deg -3.5 :fov-deg 52.0}
                     :grade      {:lift 0.02 :gamma 0.96 :saturation 0.92
                                  :vignette 0.28}}}

   :cobalt-dune
   {:byoubu/id      :cobalt-dune
    :byoubu/title   "Cobalt Dune"
    :byoubu/summary "The blue hour, one step earlier than Purple Desert: more
                     light left in the sky, ridges still separated from it."
    :byoubu/tags    #{:dusk :desert :dune :cool :blue-hour}
    :byoubu/seed    4471
    :byoubu/texture :calm
    :byoubu/accent  :sky-horizon
    ;; Darker than the first draft: measured against the rendered poster, the
    ;; original #123055 / #79b8e0 sky put the content band at 3.97:1, under AA.
    ;; The blue hour is bright, and a backdrop being true to life does not
    ;; excuse text you cannot read on it.
    :byoubu/palette {:sky-zenith   "#040814"
                     :sky-mid      "#0c2038"
                     :sky-horizon  "#43718f"
                     :haze         "#2b4c6b"
                     :ridge-far    "#1d3a5c"
                     :ridge-near   "#0e1b2e"
                     :dune-lit     "#23415f"
                     :dune-shadow  "#080f1a"
                     :star         "#dbeeff"}
    :byoubu/content-band [[:sky-mid 0.28] [:dune-shadow 0.42] [:ridge-near 0.30]]
    :byoubu/measured
    {:method "mean sRGB over the content band — full width, 30%–75% of frame
              height — rasterized and sampled in Chrome"
     :date   "2026-08-02"
     :plate  {:content-color "#213d57" :luminance 0.0435}
     :poster {:content-color "#2e516c" :luminance 0.0755}}
    :byoubu/scene   {:sky        {:model :twilight-scatter
                                  :sun-elevation-deg -4.0
                                  :turbidity 2.6
                                  :stars {:density 0.18 :magnitude-limit 3.4}}
                     :atmosphere {:haze-density 0.34 :wind {:speed 2.2 :bearing-deg 300}}
                     :terrain    {:biome :desert :ridge-count 4
                                  :dune-wavelength 240.0 :dune-amplitude 0.36}
                     :camera     {:height 28.0 :pitch-deg -2.0 :fov-deg 55.0}
                     :grade      {:lift 0.03 :gamma 0.98 :saturation 0.95
                                  :vignette 0.24}}}

   :ember-mesa
   {:byoubu/id      :ember-mesa
    :byoubu/title   "Ember Mesa"
    :byoubu/summary "Warm dusk over flat-topped rock. Higher contrast and a
                     busier horizon than the dune backdrops — content wants a
                     thicker glass surface over it."
    :byoubu/tags    #{:dusk :desert :mesa :warm}
    :byoubu/seed    9024
    :byoubu/texture :busy
    :byoubu/accent  :sky-horizon
    :byoubu/palette {:sky-zenith   "#1a0d18"
                     :sky-mid      "#4a1c22"
                     :sky-horizon  "#e08a4a"
                     :haze         "#9a4f36"
                     :ridge-far    "#4a2320"
                     :ridge-near   "#241012"
                     :dune-lit     "#5a2b22"
                     :dune-shadow  "#140809"
                     :star         "#ffe6c9"}
    :byoubu/content-band [[:sky-mid 0.26] [:dune-shadow 0.44] [:ridge-near 0.30]]
    :byoubu/measured
    {:method "mean sRGB over the content band — full width, 30%–75% of frame
              height — rasterized and sampled in Chrome"
     :date   "2026-08-02"
     :plate  {:content-color "#773d2e" :luminance 0.0746}
     :poster {:content-color "#9a5637" :luminance 0.138}}
    :byoubu/scene   {:sky        {:model :twilight-scatter
                                  :sun-elevation-deg -1.5
                                  :turbidity 4.4
                                  :stars {:density 0.06 :magnitude-limit 2.0}}
                     :atmosphere {:haze-density 0.48 :wind {:speed 3.1 :bearing-deg 250}}
                     :terrain    {:biome :desert :ridge-count 5
                                  :dune-wavelength 120.0 :dune-amplitude 0.58}
                     :camera     {:height 41.0 :pitch-deg -5.0 :fov-deg 48.0}
                     :grade      {:lift 0.01 :gamma 0.93 :saturation 1.08
                                  :vignette 0.34}}}

   :salt-flat
   {:byoubu/id      :salt-flat
    :byoubu/title   "Salt Flat"
    :byoubu/summary "Overcast noon on a dry lakebed — the light backdrop of
                     the set. Proves the facts layer is derived and not
                     hardcoded to dark UI: content over this one resolves to
                     the light appearance with dark ink."
    :byoubu/tags    #{:day :desert :flat :light :overcast}
    :byoubu/seed    1307
    :byoubu/texture :moderate
    :byoubu/accent  :haze
    :byoubu/palette {:sky-zenith   "#cfd8e4"
                     :sky-mid      "#e2e7ee"
                     :sky-horizon  "#f4f2ee"
                     :haze         "#8ea3bd"
                     :ridge-far    "#c3c7cc"
                     :ridge-near   "#d7d5cf"
                     :dune-lit     "#efece5"
                     :dune-shadow  "#c8c4ba"
                     :star         "#ffffff"}
    :byoubu/content-band [[:sky-mid 0.22] [:dune-lit 0.48] [:dune-shadow 0.30]]
    :byoubu/measured
    {:method "mean sRGB over the content band — full width, 30%–75% of frame
              height — rasterized and sampled in Chrome"
     :date   "2026-08-02"
     :plate  {:content-color "#d6dbe2" :luminance 0.7045}
     :poster {:content-color "#c7ced5" :luminance 0.6109}}
    :byoubu/scene   {:sky        {:model :overcast
                                  :sun-elevation-deg 61.0
                                  :turbidity 7.5
                                  :stars {:density 0.0 :magnitude-limit 0.0}}
                     :atmosphere {:haze-density 0.62 :wind {:speed 4.6 :bearing-deg 190}}
                     :terrain    {:biome :desert :ridge-count 2
                                  :dune-wavelength 420.0 :dune-amplitude 0.08}
                     :camera     {:height 12.0 :pitch-deg -1.0 :fov-deg 58.0}
                     :grade      {:lift 0.06 :gamma 1.04 :saturation 0.82
                                  :vignette 0.12}}}})

(def generator
  "Which repos a scene spec is written against, per tier.

  `:tier-1` names what actually ran; the shas it ran at live in
  `byoubu.poster/generated-by`, written by the renderer in the same pass that
  wrote the SVGs, so the pin can never be a claim about a render that did not
  happen. `:tier-2` is still a plan — nothing has been through a GPU, and the
  key is nil rather than a sha nobody verified."
  {:tier-1 {:stack [:kotoba-lang/terrain]
            :pin   :see-byoubu.poster/generated-by}
   :tier-2 {:stack [:kotoba-lang/sky :kotoba-lang/atmosphere
                    :kotoba-lang/terrain :kotoba-lang/postfx :kotoba-lang/webgpu]
            :pin   nil}})

(defn ids
  "All backdrop ids, sorted, so callers get a stable order."
  []
  (vec (sort (keys catalog))))

(defn lookup
  "The backdrop for `id`, or nil. Prefer `fetch` when absence is a bug."
  [id]
  (get catalog id))

(defn fetch
  "The backdrop for `id`, throwing if unknown — a typo in a backdrop id
  should fail where it is written, not render an empty plate."
  [id]
  (or (lookup id)
      (throw (ex-info (str "unknown byoubu backdrop: " (pr-str id)
                           " (known: " (str/join ", " (map pr-str (ids))) ")")
                      {:byoubu/id id :known (ids)}))))

(defn by-tag
  "Backdrop ids carrying `tag`, sorted."
  [tag]
  (vec (sort (keep (fn [[id b]] (when (contains? (:byoubu/tags b) tag) id))
                   catalog))))

(defn palette-color
  "Resolve a palette key within a backdrop to its hex string."
  [backdrop k]
  (get-in backdrop [:byoubu/palette k]))
