(ns kami.html
  "HTML as data — hiccup in its original sense. `[:tag {attrs} & children]` compiles to HTML, so a page
   / component is composable data you fork and diff. The markup half of the web pair with kami.css.
   `.cljc`. Unlike the generic kami.xml emitter this knows HTML: void elements have no close tag,
   boolean attributes render bare, and text/inline children stay on the same line.

     [:a {:href \"/x\"} \"link\"]          → <a href=\"/x\">link</a>
     [:input {:type \"checkbox\" :checked true :disabled false}] → <input type=\"checkbox\" checked>
     [:br] [:img {:src \"a.png\"}]         → <br>  <img src=\"a.png\">     (void — no close tag)
     [:p \"Some \" [:strong \"bold\"] \" text\"] → <p>Some <strong>bold</strong> text</p>  (inline)
   Text/attribute values are HTML-escaped. (html form…) renders fragments; (html5 body…) prepends the
   <!DOCTYPE html>."
  (:require [clojure.string :as str]))

(def ^:private void
  #{:area :base :br :col :embed :hr :img :input :link :meta :param :source :track :wbr})

;; raw-text elements: their content is NOT HTML (browsers don't decode entities inside them), so the JS
;; / CSS must be emitted verbatim — escaping `a < b` to `a &lt; b` would corrupt it.
(def ^:private raw-text #{:script :style})

(defn- esc [s] (-> (str s) (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))
(defn- esc-attr [s] (str/replace (esc s) "\"" "&quot;"))
(defn- attrs [m]
  (apply str (for [[k v] m :when (and (some? v) (not (false? v)))]   ;; nil / false attrs are dropped
               (if (true? v)
                 (str " " (name k))                                   ;; boolean attribute → bare name
                 (str " " (name k) "=\"" (esc-attr v) "\"")))))

(declare elem)
(defn- block [children] (str/join "\n" (map #(str "  " (str/replace (elem %) "\n" "\n  ")) children)))

(defn- elem
  "Compile one hiccup node (element vector or text string) to HTML."
  [form]
  (if-not (vector? form)
    (esc form)
    (let [[tag & more] form
          attr     (when (map? (first more)) (first more))
          children (if attr (rest more) more)
          open     (str "<" (name tag) (attrs attr) ">")]
      (cond
        (void tag)              open                                        ;; <br>, <img …> — no close
        (raw-text tag)          (str open (apply str children) "</" (name tag) ">")  ;; verbatim JS/CSS
        (empty? children)       (str open "</" (name tag) ">")
        (some string? children) (str open (apply str (map elem children)) "</" (name tag) ">")  ;; inline
        :else                   (str open "\n" (block children) "\n</" (name tag) ">")))))       ;; block

(defn html
  "Render hiccup forms to an HTML string."
  [& forms] (str/join "\n" (map elem forms)))

(defn html5
  "Render a full document, prepending <!DOCTYPE html>."
  [& body] (str "<!DOCTYPE html>\n" (apply html body)))
