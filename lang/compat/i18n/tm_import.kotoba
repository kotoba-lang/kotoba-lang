(ns i18n.tm-import
  "Bridge to the etzhayyim translation-memory service (`i18n.etzhayyim.com`,
  XRPC `I18nCommandService`/`I18nQueryService`). That service is the runtime
  source of truth for LLM-translated + human-approved strings; this
  namespace only does the pure data-shape conversion between its flat
  dotted-key JSON and this library's namespaced-keyword catalogs. It does
  NOT perform HTTP itself — callers already have a parsed JSON body (from
  whatever HTTP client their host provides) and pass it in as plain
  Clojure/ClojureScript data.

  Round-trip: `RegisterProject`/`ExportMessages` speak flat dotted keys
  (\"app.title\" \"auth.welcome\"); `i18n.core/register!` wants namespaced
  keywords (:app/title :auth/welcome). The split point is the LAST dot, so
  deeper keys like \"todo.priority.low\" become :todo.priority/low."
  (:require [clojure.string :as str]))

(defn- flat-key->kw [s]
  (let [idx (str/last-index-of s ".")]
    (if idx
      (keyword (subs s 0 idx) (subs s (inc idx)))
      (keyword s))))

(defn- kw->flat-key [k]
  (if-let [ns (namespace k)]
    (str ns "." (name k))
    (name k)))

(defn export-messages->catalog
  "`ExportMessages` response body (flat string->string map, e.g.
  {\"app.title\" \"Welcome\"}) -> a catalog map ready for
  `(i18n.core/register! locale catalog)`. Values that look like a select-map
  export (see below) are left as plain strings — the TM service does not
  currently emit plural/select branches, only flat strings; select-maps are
  an authoring-side (`i18n.messages`) concept."
  [flat-body]
  (into {} (map (fn [[k v]] [(flat-key->kw k) v])) flat-body))

(defn catalog->register-project
  "Catalog map -> the flat `{key: string}` `messages` payload `RegisterProject`
  expects. Select-map entries (`{:select ...}`) are NOT flattened to a
  single string here (the TM service has no concept of them) — callers
  authoring select-map messages should keep the human/UI-facing default
  string separate from any select variants exported for TM translation, or
  filter them out before calling this."
  [catalog]
  (into {}
        (keep (fn [[k v]] (when (string? v) [(kw->flat-key k) v])))
        catalog))

(defn language-registry->entries
  "`GetLanguageRegistry` response body (`{:languages [{:code :name :dir ...}
  ...]}`, string or keyword keys) -> entries for `(i18n.registry/load! ...)`.
  `nativeName` falls back to `name` when the service doesn't provide one."
  [registry-body]
  (let [languages (or (get registry-body :languages) (get registry-body "languages"))]
    (for [lang languages
          :let [code (or (get lang :code) (get lang "code"))
                nm   (or (get lang :name) (get lang "name"))
                native (or (get lang :nativeName) (get lang "nativeName") nm)
                dir  (or (get lang :dir) (get lang "dir") "ltr")
                tier (or (get lang :tier) (get lang "tier"))]]
      (cond-> {:i18n.lang/code (keyword code)
               :i18n.lang/name nm
               :i18n.lang/native-name native
               :i18n.lang/dir (keyword dir)}
        tier (assoc :i18n.lang/tier tier)))))
