(ns shitsuke.hiccup
  "Hiccup → HTML string renderer (.cljc, babashka-safe).

  This is the SSR twin of the reagent view contract: the SAME hiccup data that
  reagent renders live in the browser is rendered to an HTML string here for the
  static build (the kami-mangaka-reader dual-render pattern). It unifies the two
  near-identical emitters that previously lived in kami.mangaka.hiccup and
  slides.hiccup.

  Tag/attribute/style primitives and the RAWTEXT (script/style) breakout-guard
  are delegated to kotoba-lang/html (html.core) — the standalone substrate repo
  this implementation was originally extracted into (see that repo's README) —
  instead of duplicating them here, so fixes land once. The tree-walk itself
  stays local: html.core's own ->html additionally pretty-prints block-only
  element children (adds newlines/indentation), which would be a breaking
  output-format change for this namespace's many downstream consumers that
  depend on ->html's exact compact-string contract, so shitsuke.hiccup keeps
  its own compact (no added whitespace) walk and only reuses html.core's
  primitives + guard logic.

  Supported:
    [:tag attrs? & children]            vector node
    keyword tags with .class/#id sugar  :div.a.b#id
    attribute maps                      :class as string/vec, boolean attrs
    strings/numbers                     strings escaped, numbers str'd
    nil / seqs                          nil skipped, seqs flattened
    :key                                dropped (reagent-only; not HTML)
    [:hiccup/raw \"<svg/>\"]            trusted markup, not escaped
    <script>/<style> children           RAWTEXT semantics: emitted verbatim
                                         (not HTML-escaped), [:hiccup/raw ...]
                                         children unwrapped to their payload,
                                         and content containing a
                                         case-insensitive \"</tag\" breakout
                                         sequence is rejected."
  (:require [clojure.string :as str]
            [html.core :as html]))

(def esc
  "Escape &, <, >, \" for safe inclusion in HTML text/attribute context.
  Delegates to kotoba-lang/html (html.core/esc)."
  html/esc)

(def ^:private void-tags html/void-tags)
(def ^:private raw-text-tags html/raw-text-tags)
(def ^:private parse-tag html/parse-tag)
(def ^:private class-str html/class-str)
(def ^:private render-attrs html/render-attrs)

(declare ->html)

(defn- raw-text-content
  "Flatten <script>/<style> children to their verbatim RAWTEXT payload,
  unwrapping [:hiccup/raw ...] children to their string content -- the
  long-standing convention wrapped-content callers (css.core/style-node,
  kototama/web, etc.) already rely on."
  [children]
  (apply str (map (fn [c]
                     (if (and (vector? c) (= :hiccup/raw (first c)))
                       (str (second c))
                       (str c)))
                   children)))

(defn- assert-no-rawtext-breakout!
  "HTML5 RAWTEXT parsing: a <script>/<style> element terminates at the FIRST
  literal, case-insensitive \"</tag\" sequence in its content, regardless of
  surrounding quotes/strings/comments in the raw text -- emitting that
  sequence verbatim lets a raw payload break out of the element and inject
  markup after it (a script-context XSS vector)."
  [tag content]
  (when (re-find (re-pattern (str "(?i)</" tag)) content)
    (throw (ex-info (str "shitsuke.hiccup: raw-text content for <" tag "> must not contain \"</" tag "\" "
                          "case-insensitively -- that sequence terminates the element early "
                          "per HTML5's RAWTEXT rule and can break out into injected markup")
                     {:tag tag}))))

(defn- render-node [node sb]
  (cond
    (nil? node) sb
    (string? node) (conj! sb (esc node))
    (number? node) (conj! sb (str node))
    (and (vector? node) (= :hiccup/raw (first node)))
    (conj! sb (str (second node)))
    ;; vector of nodes: [[:p "a"] [:p "b"]] — first child is itself a vector.
    (and (vector? node) (not (empty? node)) (vector? (first node)))
    (reduce (fn [s c] (render-node c s)) sb node)
    (vector? node)
    (let [[t & body] node
          [tag base] (parse-tag t)
          [attrs children] (if (map? (first body))
                             [(first body) (rest body)]
                             [{} body])
          ;; tag-sugar classes/id + attr :class merge (space-joined, both win)
          attrs (merge-with (fn [a b] (str (class-str a) " " (class-str b))) base attrs)
          ;; <textarea> special case: real HTML has no value attribute on
          ;; textarea — the pre-filled text is the element *content*. The live
          ;; (reagent/React) side of the dual-render contract needs :value as
          ;; an attribute (value-as-child is read only at mount), so the SSR
          ;; twin translates: render :value as escaped content, emit no value=.
          textarea-value (when (= tag "textarea") (:value attrs))
          attrs (cond-> attrs (= tag "textarea") (dissoc :value))
          ;; :key is the same shape of translation as :value above, in the
          ;; other direction. reagent/React requires it on elements produced
          ;; from a seq (without it, every such render logs a warning), so it
          ;; belongs in the shared hiccup. HTML has no `key` attribute, so the
          ;; SSR twin must drop it rather than emit `key="0"` — measured
          ;; 2026-08-04 in kobo's served console, where every output span
          ;; carried one.
          attrs (dissoc attrs :key)]
      (conj! sb (str "<" tag (render-attrs attrs) ">"))
      (when-not (contains? void-tags tag)
        (if (contains? raw-text-tags tag)
          (let [content (raw-text-content children)]
            (assert-no-rawtext-breakout! tag content)
            (conj! sb content))
          (do
            (when (some? textarea-value)
              (conj! sb (esc textarea-value)))
            (reduce (fn [s c] (render-node c s)) sb children)))
        (conj! sb (str "</" tag ">")))
      sb)
    (seq? node) (reduce (fn [s c] (render-node c s)) sb node)
    :else (conj! sb (esc node))))

(defn ->html
  "Render a hiccup node (or seq of nodes) to an HTML string."
  [node]
  (str/join (persistent! (render-node node (transient [])))))
