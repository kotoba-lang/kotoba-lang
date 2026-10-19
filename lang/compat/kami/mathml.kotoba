(ns kami.mathml
  "MathML as data — 'hiccup for equations'. MathML is the XML markup for mathematics, so a formula maps
   onto EDN directly — an equation is composable data you fork and diff. A fresh math-notation domain in
   the kami.* family, built on kami.xml. `.cljc`.

   Math is a tree of presentation elements; write them as hiccup, or use the convenience builders:
     [:mi \"x\"] [:mn 2] [:mo \"=\"]          → <mi>x</mi> <mn>2</mn> <mo>=</mo>  (identifier / number / operator)
     (row a b c)      → <mrow> … </mrow>     (frac n d) → <mfrac>n d</mfrac>
     (sup b e) (sub b i) (sqrt x)            → <msup>/<msub>/<msqrt>
   Top level wraps body in <math xmlns…>:
     (mathml (row [:mi \"x\"] [:mo \"=\"] (frac (row [:mo \"-\"] [:mi \"b\"]) [:mn 2])))
     (mathml {:display :block} …)"
  (:require [kami.xml :as xml]))

(defn row  "An <mrow> grouping." [& xs] (into [:mrow] xs))
(defn frac "An <mfrac> numerator/denominator." [num den] [:mfrac num den])
(defn sup  "An <msup> base/superscript." [base exp] [:msup base exp])
(defn sub  "An <msub> base/subscript." [base idx] [:msub base idx])
(defn sqrt "An <msqrt> radical." [x] [:msqrt x])

(defn mathml
  "Wrap math hiccup in a <math> element with the MathML namespace. An optional leading {:display …} map
   sets the display attribute (:block / :inline)."
  [& body]
  (let [opts (when (map? (first body)) (first body))
        body (if opts (rest body) body)
        attr (cond-> {:xmlns "http://www.w3.org/1998/Math/MathML"}
               (:display opts) (assoc :display (name (:display opts))))]
    (xml/xml (into [:math attr] body))))
