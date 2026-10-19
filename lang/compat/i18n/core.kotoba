(ns i18n.core
  "Runtime message registry + lookup. Zero third-party deps, portable .cljc
  (JVM, ClojureScript, SCI/babashka).

  A catalog is a flat map of namespaced-keyword keys to either:
    - a plain string template with {param} placeholders, e.g. \"Hello, {name}!\"
    - a select map for plural/gender-style branching (Fluent-inspired, the
      same idea paraglide v2's `match`/`when` syntax borrows from Fluent):
      {:select :count :one \"{count} message\" :other \"{count} messages\"}

  Catalogs are registered per locale at runtime with `register!`, so new
  locales can be added without recompiling (unlike the compile-time-only
  `i18n.messages/defmessages` accessors, which are fixed to the keys present
  in the source-locale catalog at build time)."
  (:require [clojure.string :as str]
            [i18n.plural :as plural]))

(defonce ^{:doc "locale (keyword, e.g. :en) -> catalog map"}
  catalogs (atom {}))

(defonce ^{:doc "Current active locale keyword."}
  *locale (atom :en))

(defonce ^{:doc "Source/fallback locale keyword. Missing keys and missing
  locales fall back to this catalog."}
  *source-locale (atom :en))

(defn register!
  "Merge `catalog` (flat keyword->string|select-map) into the registry under
  `locale`. Repeated calls merge (later keys win), so a catalog can be
  assembled from more than one source (e.g. static resource + TM-service
  export)."
  [locale catalog]
  (swap! catalogs update locale merge catalog)
  nil)

(defn set-source-locale!
  "Set the fallback locale used when the active locale is missing a key."
  [locale]
  (reset! *source-locale locale))

(defn locale
  "Current active locale keyword."
  []
  @*locale)

(defn set-locale!
  "Switch the active locale. Does not require the locale's catalog to already
  be registered (useful when a catalog is fetched asynchronously after the
  switch is requested; lookups fall back to the source locale until it lands)."
  [locale]
  (reset! *locale locale)
  nil)

(defn locales
  "Set of locales with at least one registered key."
  []
  (set (keys @catalogs)))

(defn- interpolate [s params]
  (if (and params (str/index-of s "{"))
    (str/replace s #"\{([^}]+)\}"
                 (fn [[_ k]]
                   (str (get params (keyword k) (get params k (str "{" k "}"))))))
    s))

(defn- select-plural [{:keys [select] :as m} params]
  (let [n        (get params select (get params (name select)))
        category (when (number? n) (plural/category (locale) n))
        template (or (get m category) (get m :other))]
    (if template
      (interpolate template params)
      (str "{missing-select:" select "}"))))

(defn- lookup [loc k]
  (get-in @catalogs [loc k]))

(defn t
  "Translate message key `k` (a namespaced keyword, e.g. :auth/welcome) under
  the current locale, falling back to the source locale, falling back to the
  key itself rendered as a placeholder string (never throws — a missing
  translation should degrade visibly, not crash the view).

  `params` (optional) is a map used for {placeholder} interpolation and, for
  select-map entries, to pick the plural/selector category."
  ([k] (t k nil))
  ([k params]
   (let [loc     (locale)
         src     @*source-locale
         entry   (or (lookup loc k) (lookup src k))]
     (cond
       (nil? entry)   (str "{missing:" k "}")
       (map? entry)   (select-plural entry params)
       (string? entry) (interpolate entry params)
       :else           (str entry)))))

(defn clear!
  "Reset the registry. Test/dev helper — mirrors shitsuke.re-frame/clear!."
  []
  (reset! catalogs {})
  (reset! *locale :en)
  (reset! *source-locale :en)
  nil)
