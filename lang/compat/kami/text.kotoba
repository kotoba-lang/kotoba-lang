(ns kami.text
  "Text as GPU quads — a 7-segment glyph font rendered through the SAME kami.sprite-gpu instanced
   rect pipeline (no font atlas, no texture, no new shader). Each glyph declares which of 7 segments
   are lit; each lit segment is one rect quad. Covers the numeric fx (floating +N / combo xN / score)
   as data, so 2D text renders on the GPU like everything else. `.cljc`.

      aaa        segments: a top · b top-right · c bottom-right · d bottom
     f   b                 e bottom-left · f top-left · g middle
      ggg
     e   c
      ddd"
  (:require [clojure.string :as str]))

(defn- rgba [fill] (let [v (vec (or fill [1 1 1]))] (if (= 4 (count v)) v (conj v 1.0))))

(defn- seg-rects
  "The 7 segments' [centre-offset half-size] in a glyph box centred at 0 (half-width hw, half-height
   hh, thickness th). Screen y grows downward, so the top segment is at -hh."
  [hw hh th]
  (let [q (/ hh 2.0)]
    {:a [[0 (- hh)] [hw th]]  :d [[0 hh] [hw th]]  :g [[0 0] [hw th]]
     :f [[(- hw) (- q)] [th q]] :b [[hw (- q)] [th q]]
     :e [[(- hw) q] [th q]]     :c [[hw q] [th q]]}))

(def glyphs
  "char → lit 7-segments (digits + the symbols the fx use)."
  {\0 [:a :b :c :d :e :f] \1 [:b :c]          \2 [:a :b :g :e :d]
   \3 [:a :b :g :c :d]    \4 [:f :g :b :c]     \5 [:a :f :g :c :d]
   \6 [:a :f :g :e :c :d] \7 [:a :b :c]        \8 [:a :b :c :d :e :f :g]
   \9 [:a :b :c :d :f :g] \- [:g] \+ [:g] \= [:a :d] \space []})

(defn glyph->quads
  "One char centred at [cx cy], glyph half-size [hw hh], thickness th, colour → rect quad instances."
  [ch [cx cy] [hw hh] th color]
  (let [segs (seg-rects hw hh th)
        col  (rgba color)
        rect (fn [[dx dy] [sw sh]] {:pos [(+ cx dx) (+ cy dy)] :size [sw sh] :rot 0.0 :shape 1 :color col})
        base (mapv (fn [s] (let [[p sz] (segs s)] (rect p sz))) (get glyphs ch []))]
    (cond-> base
      (= ch \+) (conj (rect [0 0] [th hh])))))   ;; + = middle (g) crossed by a vertical bar

(defn text->quads
  "A string at left-centre [x y], glyph half-size [hw hh], colour → rect quad instances laid out
   left→right. Reuses kami.sprite-gpu's instanced rect pipeline (shape 1 = box SDF)."
  [s [x y] [hw hh] color]
  (let [th  (max 1.0 (* hw 0.30))
        adv (* hw 2.7)]
    (into [] (mapcat (fn [i ch] (glyph->quads (first (str/upper-case (str ch)))
                                              [(+ x (* i adv) hw) y] [hw hh] th color))
                     (range) (seq s)))))
