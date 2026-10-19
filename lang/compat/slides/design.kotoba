(ns slides.design
  "Reusable EDN design system for slides decks.")

(def default-design
  {:slides/theme
   {:slides/format "office-style"
    :slides/colors {:office-style.color/dk1 "17202A"
                    :office-style.color/lt1 "FFFFFF"
                    :office-style.color/dk2 "334155"
                    :office-style.color/lt2 "F7F8FB"
                    :office-style.color/accent1 "496B9A"
                    :office-style.color/accent2 "7C9A4B"
                    :office-style.color/accent3 "B46A55"
                    :office-style.color/accent4 "5C6F7E"
                    :office-style.color/accent5 "8A6F3D"
                    :office-style.color/accent6 "6A5A8E"
                    :office-style.color/hlink "315D8C"
                    :office-style.color/folHlink "6A5A8E"}
    :slides/fonts {:office-style.font/majorFont "Aptos Display"
                   :office-style.font/minorFont "Aptos"}}
   :slides/master
   {:slides/id "kotoba-clean"
    :slides/background "FFFFFF"
    :slides/footer {:slides/enabled true
                    :slides/text ""
                    :slides/x 0.7 :slides/y 5.28 :slides/w 8.6 :slides/h 0.18
                    :slides/font-size 7
                    :slides/color "526170"}}
   :slides/guides
   {:slides/margin {:slides/x 0.65 :slides/y 0.55 :slides/right 0.65 :slides/bottom 0.48}
    :slides/columns 12
    :slides/gutter 0.14
    :slides/baseline 0.18}
   :slides/text-styles
   {:eyebrow {:slides/font-size 10 :slides/color "657F3D" :slides/bold true}
    :title {:slides/font-size 38 :slides/color "17202A" :slides/bold true}
    :subtitle {:slides/font-size 20 :slides/color "526170"}
    :body {:slides/font-size 16 :slides/color "334155"}
    :caption {:slides/font-size 9 :slides/color "526170"}}
   :slides/components
   {:eyebrow {:slides/shape :text :slides/text-style :eyebrow
              :slides/x 0.75 :slides/y 0.58 :slides/w 8.5 :slides/h 0.28}
    :title {:slides/shape :text :slides/text-style :title
            :slides/x 0.72 :slides/y 0.86 :slides/w 8.8 :slides/h 0.82}
    :subtitle {:slides/shape :text :slides/text-style :subtitle
               :slides/x 0.74 :slides/y 1.72 :slides/w 7.8 :slides/h 0.5}
    :body {:slides/shape :text :slides/text-style :body
           :slides/x 0.78 :slides/y 2.36 :slides/w 5.8 :slides/h 1.6}
    :panel {:slides/shape :rect
            :slides/x 0.68 :slides/y 2.28 :slides/w 8.65 :slides/h 2.58
            :slides/fill "F7F8FB"
            :slides/line "D8DEE8"}
    :accent-bar {:slides/shape :rect
                 :slides/x 0.66 :slides/y 0.54 :slides/w 0.06 :slides/h 4.55
                 :slides/fill "496B9A"
                 :slides/line "496B9A"}}})

(defn deep-merge
  [& maps]
  (letfn [(mrg [a b]
            (cond
              (nil? b) a
              (and (map? a) (map? b))
              (merge-with mrg a b)
              :else b))]
    (reduce mrg {} (filter some? maps))))

(defn- map-overrides [m ks]
  (->> ks
       (keep (fn [k]
               (when (map? (get m k))
                 [k (get m k)])))
       (into {})))

(defn deck-design [deck]
  (let [ks [:slides/theme :slides/master :slides/guides
            :slides/text-styles :slides/components]]
    (deep-merge default-design
                (map-overrides deck ks)
                (when (map? (:slides/design deck))
                  (:slides/design deck)))))

(defn theme [deck]
  (:slides/theme (deck-design deck)))

(defn colors [deck]
  (get-in (deck-design deck) [:slides/theme :slides/colors]))

(defn fonts [deck]
  (get-in (deck-design deck) [:slides/theme :slides/fonts]))

(defn master [deck]
  (:slides/master (deck-design deck)))

(defn masters
  "The deck's :slides/masters list -- additional named masters beyond the
  single default one (deck-design's :slides/master), for decks whose
  sections use visually distinct masters (different background/footer)."
  [deck]
  (:slides/masters deck))

(defn master-by-ref
  "The named master matching `ref` (a :slides/id in (masters deck)), falling
  back to the deck's single default master when `ref` is nil or doesn't
  match any entry."
  [deck ref]
  (or (and ref (some #(when (= ref (:slides/id %)) %) (masters deck)))
      (master deck)))

(defn master-for-slide
  "The effective master for `slide`: its own :slides/master-ref if set,
  else the deck's single default master -- unchanged behavior for every
  deck that doesn't use multiple masters."
  [deck slide]
  (master-by-ref deck (:slides/master-ref slide)))

(defn layouts
  "The deck's :slides/layouts list -- named layout templates (each with
  its own :slides/layout-type, e.g. \"title\"/\"obj\"/\"twoObj\"/\"blank\",
  and optionally :slides/placeholders, positioned <p:ph> shape templates)
  a slide can select via :slides/layout-ref. Every master always has at
  least its own implicit default (blank) layout even when a deck defines
  none of these -- unchanged behavior for the common case."
  [deck]
  (:slides/layouts deck))

(defn layout-by-ref
  "The named layout matching `ref` (a :slides/id in (layouts deck)), or nil
  when `ref` is nil/doesn't match (the caller then falls back to the
  implicit default blank layout)."
  [deck ref]
  (and ref (some #(when (= ref (:slides/id %)) %) (layouts deck))))

(defn layout-for-slide [deck slide]
  (layout-by-ref deck (:slides/layout-ref slide)))

(defn guides [deck]
  (:slides/guides (deck-design deck)))

(defn text-style [deck id]
  (get-in (deck-design deck) [:slides/text-styles id]))

(defn component [deck id]
  (get-in (deck-design deck) [:slides/components id]))

(defn resolve-shape [deck shape]
  (let [shape (if (map? shape) shape {})
        component-defaults (when-let [id (:slides/component shape)]
                             (component deck id))
        styled (deep-merge component-defaults shape)
        text-defaults (when-let [id (:slides/text-style styled)]
                        (text-style deck id))]
    (deep-merge text-defaults styled)))
