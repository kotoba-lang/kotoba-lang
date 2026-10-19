(ns shitsuke.components
  "Pure-hiccup UI primitives (.cljc, no reagent import).

  Each fn returns plain hiccup data (the kami-mangaka dual-render contract): the
  SAME data is rendered live by reagent in the browser and to HTML by
  shitsuke.hiccup/->html for SSR. Styling is via stable `shitsuke__<component>`
  classes (shitsuke.style/class-name) + token CSS vars (var(--shitsuke-...)),
  so the visual lives in the shadow-css :pages build, not inline.

  `act` is the portable interaction attribute: a keyword like :slides/new-deck.
  On :cljs the caller wraps it in :on-click (rf/dispatch [act ...]); on SSR the
  caller emits `data-act=\"<act>\"` and a thin enhancer dispatches on click
  (mirrors mangaka reader's wire-lang-switch! pattern)."
  (:require [shitsuke.style :as s]))

(defn- act->str
  "Render an `act` value to the data-act attribute string, preserving the
  keyword namespace: :cart/add → \"cart/add\", :new-deck → \"new-deck\".
  Strings pass through; nil stays nil (attribute dropped)."
  [a]
  (cond
    (nil? a) nil
    (string? a) a
    (keyword? a) (if-let [ns (namespace a)]
                   (str ns "/" (name a))
                   (name a))
    :else (str a)))

(defn button
  "Plain button. `label` may be string or hiccup. opts: :class, :act, :disabled,
  :title, :type."
  ([label]
   (button label nil))
  ([label opts]
   (let [{:keys [act disabled title type class]} opts]
     [:button {:class (str (s/class-name :button) (when class (str " " class)))
               :type (or type "button")
               :disabled (when disabled true)
               :title title
               :data-act (some-> act act->str)}
      label])))

(defn icon-button
  "Button whose label is an icon glyph (string/hiccup). Same opts as button."
  ([icon]
   (icon-button icon nil))
  ([icon opts]
   (button icon (assoc opts :class (str (s/class-name :icon-button)
                                        (when-let [c (:class opts)] (str " " c)))))))

(defn field
  "Label + control row. `control` is hiccup (input/textarea/select)."
  ([label-text control]
   (field label-text control nil))
  ([label-text control opts]
   (let [{:keys [for-id class]} opts]
     [:div {:class (str (s/class-name :field) (when class (str " " class)))}
      (when label-text [:label {:for for-id} label-text])
      control])))

(defn- control-attrs
  "Shared attrs builder for the native text controls (`input`/`textarea`).

  - A caller `:on-input` is re-attached as `:on-change` when no `:on-change`
    is given (kept as-is when the caller wired both — an explicit `:on-change`
    always wins, never clobbered). React's `onChange` on text controls fires
    on the native `input` event, so the caller-visible semantics
    (per-keystroke, `(.. e -target -value)`) are identical — but the rename
    matters under reagent: reagent's async-rendering-safe controlled-input
    path (reagent.impl.input/input-render-setup) only engages when the props
    carry BOTH `value` and `onChange`. With `:value` + `:on-input` the control
    is a plain React controlled input under reagent's rAF-batched re-rendering:
    after every keystroke React restores the DOM to the last-*rendered* (stale)
    value, so any keystroke landing before the next render is typed into a
    reverted field and everything but the last keystroke is lost. Reproduced
    against reagent 1.2.0/React 18; root-caused downstream in
    kotoba-lang/liquid-glass-ui PR #3 (net-babiniku text fields).
  - Every other caller opt passes through untouched (`:on-key-down`,
    `:disabled`, `:aria-label`, `:maxLength`, ...); `:class` is appended to
    the base `shitsuke__<component>` class; `:act` maps to `:data-act`.
  - Pure data in → pure data out: equal opts produce `=` hiccup.

  `base` is the control-specific leading attrs (:id/:class/:type/:rows),
  passed pre-built so the emitted attribute order — and therefore the SSR
  HTML string — stays stable."
  [opts base]
  (let [{:keys [value placeholder on-input on-change act]} opts]
    (merge (assoc base
                  :value (or value "")
                  :placeholder placeholder
                  :on-change (or on-change on-input)
                  :on-input (when on-change on-input)
                  :data-act (some-> act act->str))
           (dissoc opts :id :class :value :placeholder :type :rows
                   :on-input :on-change :act))))

(defn input
  "Text input. opts: :id, :value, :placeholder, :type, :on-input (cljs — see
  `control-attrs`: attached to the hiccup as :on-change so reagent's
  async-safe controlled-input path engages; an explicit :on-change wins),
  :act (ssr), :class, plus full attr passthrough (:disabled, :aria-*, ...)."
  ([opts]
   (let [{:keys [id type class]} opts]
     [:input (control-attrs opts
                            {:id id
                             :class (str (s/class-name :input)
                                         (when class (str " " class)))
                             :type (or type "text")})])))

(defn textarea
  "Textarea. Same opts contract as `input` (plus :rows, default 6; no :type).
  :value rides as an *attribute* (not element content) so reagent keeps the
  control following app state — value-as-child is read by React only at mount
  and the field silently stops being controlled. For SSR,
  shitsuke.hiccup/->html special-cases <textarea>: a :value attribute renders
  as escaped element content (real HTML has no value attribute on textarea)."
  ([opts]
   (let [{:keys [id rows class]} opts]
     [:textarea (control-attrs opts
                               {:id id
                                :class (str (s/class-name :textarea)
                                            (when class (str " " class)))
                                :rows (or rows 6)})])))

(defn select
  "`options` is a vec of [value label] pairs. opts: :id, :value, :on-change,
  :act, :attrs.

  `:attrs` is merged onto the `<select>` with the component's own generated
  attrs winning on conflict — the same contract `kotoba-ui.shell` and
  `liquid-glass.components/list-row` use. It exists because a `<select>` with
  no visible `<label>` needs `aria-label` (or `aria-labelledby`, `title`,
  `required`, `disabled`, `name`, `form`…) and this fn previously read four
  keys and dropped everything else *silently*: consuming code could pass
  `:aria-label` and render an unlabeled control while reading as accessible.
  That is the same defect `list-row` was reported for; fixing it here fixes
  `liquid-glass.components/menu-select` too, which forwards its opts."
  ([options] (select options nil))
  ([options opts]
   (let [{:keys [id value on-change act attrs]} opts
         base {:id id
               :class (s/class-name :select)
               :on-change on-change
               :data-act (some-> act act->str)}]
     [:select (if (seq attrs) (merge attrs base) base)
      (for [[v l] options]
        [:option {:value v :selected (= (str v) (str value))} l])])))

(defn card
  "Box container. `body` is hiccup or seq. opts: :class, :id."
  ([body]
   (card body nil))
  ([body opts]
   (let [{:keys [class id]} opts]
     [:section {:id id
                :class (str (s/class-name :card) (when class (str " " class)))}
      body])))

(defn toolbar
  "Horizontal action row. `actions` is a seq of hiccup (typically buttons)."
  ([actions]
   (toolbar actions nil))
  ([actions opts]
   [:header {:class (str (s/class-name :toolbar) (when-let [c (:class opts)] (str " " c)))}
    (into [:nav] actions)]))

(defn mode-tabs
  "Tab strip. `tabs` is [id label] pairs; `current` is the active id."
  ([tabs current]
   (mode-tabs tabs current nil))
  ([tabs current opts]
   [:nav {:class (s/class-name :mode-tabs)}
    (for [[id label] tabs]
      [:button {:key (name id)
                :type "button"
                :class (str (s/class-name :tab)
                            (when (= id current) (str " " (s/class-name :tab--active))))
                :data-act (some-> id act->str)}
       label])]))

(defn thumb
  "Selectable thumbnail. `active?` toggles the active class. `body` is hiccup."
  ([body active?]
   (thumb body active? nil))
  ([body active? opts]
   [:button {:type "button"
             :class (str (s/class-name :thumb) (when active? (str " " (s/class-name :thumb--active))))
             :data-act (some-> (:act opts) act->str)}
    body]))

(defn pane
  "Visibility-toggled pane. `hidden?` hides it. `body` is hiccup."
  ([hidden? body]
   [:section {:class (s/class-name :pane)
              :hidden (when hidden? true)}
    body]))
