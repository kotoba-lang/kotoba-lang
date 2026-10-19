(ns kotoba-ui.shell
  "Pure-hiccup HIG layout scaffolds — the page/layout layer this stack was
  missing (consumers hand-wrote 400+ lines of ad-hoc `.layout`/`.hero` CSS
  before it existed).

  Same dual-render contract as the rest of the stack: every component is
  plain hiccup that renders via shitsuke.hiccup/->html (SSR) or reagent
  (browser). Each component takes an optional opts map first, then children.
  All visual values come from `--hig-*` custom properties (see
  kotoba-ui.shell.style) — zero hardcoded colors/sizes — and all shell CSS
  lives inside the `kotoba.hig` cascade layer, so unlayered app CSS always
  wins.

  `page` is the full-document scaffold: it stamps `data-appearance` and
  embeds kotoba-ui.theme/theme-css, so `(->html (page {...} body))` (or
  kotoba-ui.core/->page) is a complete refined page from one call.

  Every scaffold's opts map also accepts the shared root-element opts —
  `:id`, `:class` (string or vector; APPENDED to the component's own
  `kotoba-shell__*` class, never replacing it) and `:attrs` (map of any
  other attributes: data-*, aria-*, :role, ...). `:attrs` cannot clobber
  the component's own generated keys (:class/:style/aria-*) — on conflict
  the component wins; layout overrides belong in app CSS, not
  `:attrs :style`. For `page` these land on `<body>`. So: need an id /
  class / data-attr on a scaffold? Pass these — don't mirror shell CSS in
  app CSS."
  (:require [clojure.string :as str]
            [kotoba-ui.theme :as theme]
            [kotoba-ui.shell.style :as style]))

(def class-name
  "Stable `kotoba-shell__*` class for a component or component--modifier
  (re-exported from kotoba-ui.shell.style)."
  style/class-name)

(def shell-css
  "All kotoba-shell__* structural rules wrapped in `@layer kotoba.hig {...}`
  (re-exported from kotoba-ui.shell.style; already included in
  kotoba-ui.theme/theme-css)."
  style/shell-css)

;; ---------------------------------------------------------------------------
;; small shared helpers

(defn- split-opts
  "[opts children] from a variadic arg list whose first element may be an
  opts map. Hiccup children are never maps, so map? is unambiguous."
  [args]
  (if (map? (first args))
    [(first args) (rest args)]
    [nil args]))

(defn- cls
  "Join a required kotoba-shell class with an optional consumer :class opt
  (same helper shape as liquid-glass.components)."
  [base extra]
  (cond
    (not (seq extra)) base
    (not (seq base))  extra
    :else             (str base " " extra)))

(defn- class-opt->str
  "Consumer :class opt (string, keyword, or vector of those) -> a
  space-joined class string (nil-safe: nil in, nil out; nils in a vector
  are skipped)."
  [c]
  (cond
    (nil? c)        nil
    (string? c)     c
    (keyword? c)    (name c)
    (sequential? c) (->> c (keep class-opt->str) (remove str/blank?) (str/join " "))
    :else           (str c)))

(defn- with-root-attrs
  "Merge the shared root-element opts (`:id` / `:class` / `:attrs`, see the
  ns docstring) into a component's own generated attr map `base`.

  Merge semantics: consumer `:class` is appended to the component's own
  class; `:id` is assoc'd; `:attrs` is merged in with `base` winning on
  conflict, so `:attrs` can never clobber the component's generated
  :class/:style (nor the top-level :id/:class opts)."
  [base {:keys [id class attrs]}]
  (cond-> (if (seq attrs) (merge attrs base) base)
    (some? class) (update :class cls (class-opt->str class))
    (some? id)    (assoc :id id)))

(defn- css-value
  "Keyword or string opt value -> CSS string (:center -> \"center\")."
  [v]
  (if (keyword? v) (name v) (str v)))

(defn- spacing-var
  "Spacing token keyword -> its `--hig-spacing-*` var reference
  (:6 -> \"var(--hig-spacing-6)\")."
  [k]
  (str "var(--hig-spacing-" (name k) ")"))

;; ---------------------------------------------------------------------------
;; scaffolds

(defn stack
  "Flex column (row with `:direction :horizontal`). opts: :gap (spacing
  token keyword, default :4 via the stylesheet), :align (align-items),
  :justify (justify-content), plus the shared root opts :id / :class /
  :attrs (ns docstring)."
  [& args]
  (let [[opts children] (split-opts args)
        {:keys [direction gap align justify]} opts
        style (merge (when gap     {:gap (spacing-var gap)})
                     (when align   {:align-items (css-value align)})
                     (when justify {:justify-content (css-value justify)}))]
    (into [:div (with-root-attrs
                  (cond-> {:class (str (class-name :stack)
                                       (when (= direction :horizontal)
                                         (str " " (class-name "stack--horizontal"))))}
                    (seq style) (assoc :style style))
                  opts)]
          children)))

(defn spacer
  "Flex-grow filler — pushes siblings apart inside a stack/toolbar row.
  Optional opts map: the shared root opts :id / :class / :attrs
  (ns docstring)."
  ([] (spacer nil))
  ([opts]
   [:div (with-root-attrs {:class (class-name :spacer) :aria-hidden true}
           opts)]))

(defn section
  "Semantic content section: hairline top border, content-margin padding,
  readable max-width column centered by default. opts: :title (rendered as a
  `.hig-title2` heading), :wide (true opts out of the readable max-width),
  plus the shared root opts :id / :class / :attrs (ns docstring)."
  [& args]
  (let [[opts children] (split-opts args)
        {:keys [title wide]} opts]
    (into [:section (with-root-attrs
                      {:class (str (class-name :section)
                                   (when wide
                                     (str " " (class-name "section--wide"))))}
                      opts)
           (when title
             [:h2 {:class (str (class-name :section-title) " hig-title2")} title])]
          children)))

(defn hero
  "Page-entrance block: `.hig-large-title` title, `.hig-title3`
  secondary-label tagline, centered action row, generous 4pt-grid padding
  and a subtle radial accent wash (color-mix over the tint token — follows
  the theme accent, no hardcoded gradient). opts: :title, :tagline,
  :actions (seq of hiccup, typically kotoba-ui buttons), plus the shared
  root opts :id / :class / :attrs (ns docstring)."
  [opts]
  (let [{:keys [title tagline actions]} opts]
    [:header (with-root-attrs {:class (class-name :hero)} opts)
     [:h1 {:class (str (class-name :hero-title) " hig-large-title")} title]
     (when tagline
       [:p {:class (str (class-name :hero-tagline) " hig-title3")} tagline])
     (when (seq actions)
       (into [:div {:class (class-name :hero-actions)}] actions))]))

(defn grid
  "Responsive card grid: `repeat(auto-fill, minmax(min, 1fr))` columns.
  opts: :min (CSS length string, default 260px via the stylesheet), :gap
  (spacing token keyword), plus the shared root opts :id / :class / :attrs
  (ns docstring). Children get `min-width: 0` from the stylesheet — long
  content can't blow a track out."
  [& args]
  (let [[opts cards] (split-opts args)
        {min-w :min :keys [gap]} opts
        style (merge (when min-w
                       {:grid-template-columns
                        (str "repeat(auto-fill, minmax(" min-w ", 1fr))")})
                     (when gap {:gap (spacing-var gap)}))]
    (into [:div (with-root-attrs
                  (cond-> {:class (class-name :grid)}
                    (seq style) (assoc :style style))
                  opts)]
          cards)))

(defn app-shell
  "Whole-app frame. opts: :nav (typically a kotoba-ui nav-bar/toolbar —
  rendered sticky at the top), :sidebar (optional; fixed-width desktop
  column that collapses to single-column under the breakpoint), plus the
  shared root opts :id / :class / :attrs (ns docstring).
  `content` renders in a `<main>` with `min-width: 0` +
  `overflow-wrap: break-word` (the grid-item overflow guard — this exact
  bug shipped in production)."
  [& args]
  (let [[opts content] (split-opts args)
        {:keys [nav sidebar]} opts]
    [:div (with-root-attrs
            {:class (str (class-name :app)
                         (when sidebar (str " " (class-name "app--with-sidebar"))))}
            opts)
     (when nav [:div {:class (class-name :app-nav)} nav])
     [:div {:class (class-name :app-body)}
      (when sidebar [:aside {:class (class-name :app-sidebar)} sidebar])
      (into [:main {:class (class-name :app-main)}] content)]]))

(defn page
  "The full document hiccup: `[:html ...]` with charset/viewport meta,
  title, optional description meta, theme-color metas (browser chrome
  matches the theme's page background; per-scheme media-gated when the
  appearance is :auto), the theme's complete CSS bundle
  (kotoba-ui.theme/theme-css) inlined, and `data-appearance` stamped when
  the theme forces an appearance. opts: :title, :description, :lang
  (default \"en\"), :theme (a kotoba-ui.theme map), :head (extra head
  hiccup), plus the shared root opts :id / :class / :attrs (ns docstring) —
  for `page` they land on the `<body>` element (the `<html>` root keeps
  only :lang + data-appearance). Render with `(->html (page ...))` — or use
  kotoba-ui.core/->page, which prepends the doctype."
  [opts & body]
  (let [{:keys [title description lang theme head]} opts
        body-attrs (not-empty (with-root-attrs {} opts))
        appearance (theme/appearance-attr theme)
        {:keys [light dark]} (theme/theme-colors theme)]
    [:html {:lang (or lang "en")
            :data-appearance appearance}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, viewport-fit=cover"}]
      [:title (or title "")]
      (when description [:meta {:name "description" :content description}])
      ;; theme-color: browser chrome (status bar / tab strip) matches the
      ;; page background. Forced appearance -> one meta; :auto -> one per
      ;; color scheme, media-gated, so the chrome follows the OS setting.
      (when (not= appearance "dark")
        [:meta (cond-> {:name "theme-color" :content light}
                 (nil? appearance)
                 (assoc :media "(prefers-color-scheme: light)"))])
      (when (not= appearance "light")
        [:meta (cond-> {:name "theme-color" :content dark}
                 (nil? appearance)
                 (assoc :media "(prefers-color-scheme: dark)"))])
      [:style [:hiccup/raw (theme/theme-css theme)]]
      head]
     (into (if body-attrs [:body body-attrs] [:body]) body)]))
