(ns slides.web.enhance
  "Live-host hiccup enhancer for the dual-render contract (portable .cljc).

  slides.web.views emits pure hiccup with stable :data-field attributes and
  NO host callbacks, so the SSR HTML equals the live DOM. Button-like acts
  are driven by the browser adapter's single delegated click listener — but
  reagent-controlled text inputs (they carry :value) additionally need a real
  React `onChange` prop: reagent's async-rendering-safe controlled-input path
  only engages when the props carry BOTH value and onChange (the shitsuke
  :on-input→:on-change contract, see shitsuke.components/control-attrs).
  A DOM-level delegated listener cannot provide that prop, so the live host
  walks the view hiccup ONCE per render and attaches :on-change to every
  control that declares :data-field.

  <select data-field> is additionally converted from SSR form (`selected`
  attributes on options) to React-controlled form (:value on the select,
  options stripped of :selected) so the pick follows app-db across re-renders
  and undo/redo.

  Pure data in → data out: the handler is built by the caller-supplied
  `(on-field field-string)` factory, so the walk itself is host-independent
  and JVM-testable. The SSR path never calls this namespace — fn-valued
  attrs never reach shitsuke.hiccup/->html."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

(defn tag-name
  "Base element name of a hiccup tag keyword (strips #id/.class sugar):
  :select#shape-kind → \"select\"."
  [k]
  (first (str/split (name k) #"[#.]")))

(defn- element-with-attrs?
  "A hiccup element vector whose second slot is an attrs map (and not a
  MapEntry, which is also a two-slot vector)."
  [node]
  (and (vector? node)
       (not (map-entry? node))
       (keyword? (first node))
       (map? (second node))))

(def text-control-tags #{"input" "textarea"})

(defn- option? [node]
  (and (element-with-attrs? node) (= "option" (tag-name (first node)))))

(defn selected-option-value
  "First :selected option's :value (as a string) in a children seq/tree, or
  nil. Children may nest inside seqs (e.g. views/select-options returns a
  cons)."
  [children]
  (some (fn [child]
          (cond
            (option? child) (when (:selected (second child))
                              (str (:value (second child) "")))
            (seq? child) (selected-option-value child)
            :else nil))
        children))

(defn strip-selected
  "Remove :selected from option attrs throughout a children seq/tree (React
  controls the pick via the select's :value instead)."
  [children]
  (map (fn [child]
         (cond
           (option? child) (update child 1 dissoc :selected)
           (seq? child) (strip-selected child)
           :else child))
       children))

(defn- enhance-element [node on-field]
  (let [tag (tag-name (first node))
        attrs (second node)
        field (:data-field attrs)]
    (cond
      (and field (contains? text-control-tags tag))
      (assoc node 1 (assoc attrs :on-change (on-field field)))

      (and field (= "select" tag))
      (let [children (drop 2 node)
            value (or (selected-option-value children) "")]
        (into [(first node) (assoc attrs
                                   :value value
                                   :on-change (on-field field))]
              (strip-selected children)))

      :else node)))

(defn enhance
  "Walk a view hiccup tree, attaching a live :on-change handler — built by
  `(on-field field-string)` — to every form control carrying :data-field.
  Everything else passes through untouched."
  [tree on-field]
  (walk/postwalk
   (fn [node]
     (if (element-with-attrs? node)
       (enhance-element node on-field)
       node))
   tree))
