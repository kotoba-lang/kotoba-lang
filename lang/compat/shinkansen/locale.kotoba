(ns shinkansen.locale
  "Cookie-based, path-independent locale negotiation — the framework default.

  Owner decision (measured live on app-kotoba.cloud): locale selection by
  URL path (`/ja/about`) creates dead links and IA fragmentation — 22
  locales × path copies per page. The shinkansen default is instead:

    one document per language-agnostic route; the locale is negotiated
    by cookie (with Accept-Language fallback); the language switch
    writes the cookie client-side.

  This matches the content-addressed document model: each locale variant
  of a document is still its own CID (identity is content), but the ENTRY
  route never forks per locale — the host/edge negotiates. This namespace
  is the pure contract for that negotiation; apps must not re-derive it
  (app-kotoba.cloud's kb_locale cookie + /js/language-selector.js is the
  same pattern this replaces with a framework default).

  Pure .cljc, no js/ (dual-render per shitsuke). Fail-closed: a cookie
  value outside :supported is never trusted."
  (:require [clojure.string :as str]
            [shinkansen.publish :as publish]))

(def ^:const defaults
  "The framework-default negotiation configuration. :cookie-attrs are the
  ONE place the Set-Cookie attributes are SERIALIZED (`set-cookie-header`)
  — apps must not re-derive the serialization (the drift this prevents:
  one app with Missing SameSite, another with a different max-age, a third
  session-scoped). The VALUES are the app's: the default is host-only (no
  :domain) because a cookie is the wrong place for authority (SPEC §1.8);
  a locale is a PREFERENCE, and a product whose readers cross docs. /
  blog. / console. may set :domain to the registrable parent so one choice
  covers the name-origin family (app-kotoba-cloud, owner-measured
  2026-09-16: host-only flipped the language on every host change). What
  may never span hosts is a credential — that rule is not this map's."
  {:cookie-name "shinkansen_locale"
   :cookie-attrs {:path "/" :same-site "Lax" :secure true :max-age 31536000}})

(defn- locale-name
  "The wire form of a locale: :ja → \"ja\", \"ja\" → \"ja\". (str :ja) is
  \":ja\" and would never match a cookie — always go through here."
  [l]
  (if (keyword? l) (name l) (str l)))

(defn- supported-set
  [supported]
  (set (map locale-name supported)))

(defn- parse-q
  "Pure, engine-neutral numeric parse of a regex-validated q string
  ([0-9.]+): integer part + fractional part summed via an explicit digit
  map. No Double/parseDouble (clj-only), no js/ (cljs-only), and no char
  arithmetic (nbb's `int` on a char is not the code point) — dual-render."
  [s]
  (let [d {\0 0 \1 1 \2 2 \3 3 \4 4 \5 5 \6 6 \7 7 \8 8 \9 9}
        [int-part frac-part] (str/split s #"\." 2)
        digits (fn [str']
                 (reduce (fn [acc ch] (+ (* acc 10) (get d ch 0))) 0 str'))]
    (+ (digits (or int-part "0"))
       (/ (digits (or frac-part "0"))
          (Math/pow 10 (count (or frac-part "0")))))))

(defn- parse-accept-language
  "\"en;q=0.3,ja;q=0.9\" → [[\"ja\" 0.9] [\"en\" 0.3]] — q-descending, ties
  keep header order (HTTP precedence). Malformed tags/weights are skipped,
  not fatal."
  [al]
  (when (string? al)
    (->> (str/split al #",")
         (map #(str/split % #";" 2))
         (keep (fn [[tag params]]
                 (when-let [t (some-> tag str/trim str/lower-case not-empty)]
                   (let [q (if-let [m (and params (re-find #"q\s*=\s*([0-9.]+)" params))]
                             (parse-q (second m))
                             1.0)]
                     ;; q=0 is "not acceptable" (RFC 9110 §12.4.2): the tag is
                     ;; dropped. Until 2026-09-16 it was promoted to 1.0.
                     (when (pos? q) [t q])))))
         (sort-by second >))))

(defn negotiate
  "Resolve the locale for one request.

    (negotiate {:supported [:en :ja] :default :en
                :cookie-value \"ja\"
                :accept-language \"en;q=0.3,ja;q=0.9\"})
    → {:locale :ja :source :cookie}

  Precedence: :explicit > cookie > Accept-Language q-values > :hint > :default.

    :explicit   the switch the reader just used (a `?lang=` query, a legacy
                locale path) — highest, and still fail-closed against
                :supported: an unknown value is ignored, never persisted
    :hint       a locale the ENVIRONMENT suggests (Cloudflare cf.country →
                locale) — below every stated preference, above the default
    :normalize  (fn [string] → locale-or-nil): the app's alias table (zh →
                :zh-Hans, he-IL → :he, ar_MA → :ar-MA). Applied to the
                explicit value, the cookie value and every Accept-Language
                tag. Default: exact match against :supported, case-
                insensitive for the header

  The three seams are what cloud-kotoba/app-kotoba-cloud had re-derived in
  its own locale.cljk (2026-09-16); they are here so it can require this.

  No value is trusted blindly: whatever :normalize answers must be in
  :supported or it is ignored (fail-closed) — a stale/forged cookie or a
  crafted ?lang= cannot pin a locale the host does not serve. The result
  records its :source so the host can decide whether to (re)write the
  cookie."
  [{:keys [supported default cookie-value accept-language explicit hint normalize]}]
  (let [sup (set supported)
        exact (fn [v] (when (string? v)
                        (let [v (str/trim v)]
                          (some (fn [l] (when (= (locale-name l) v) l)) supported))))
        norm (fn [v] (let [l (if normalize (normalize v) (exact v))]
                       (when (contains? sup l) l)))
        header-hit (fn [t] (let [l (if normalize
                                     (normalize t)
                                     (some (fn [l] (when (= (str/lower-case (locale-name l)) t) l)) supported))]
                             (when (contains? sup l) l)))]
    (or (when-let [l (norm explicit)] {:locale l :source :explicit})
        (when-let [l (norm cookie-value)] {:locale l :source :cookie})
        (when-let [l (->> (parse-accept-language accept-language)
                          (keep (fn [[t _]] (header-hit t)))
                          first)]
          {:locale l :source :accept-language})
        (when (contains? sup hint) {:locale hint :source :hint})
        {:locale default :source :default})))

(defn- attr-str
  "One attribute → its string, or nil when the attribute must be omitted:
  valued attrs (:path :domain :same-site :max-age) need a non-nil value;
  flag attrs (:secure :http-only) are emitted only when true — a false
  flag is an omission, never the literal \"false\"."
  [[k v]]
  (case k
    (:path :domain :same-site :max-age) (when (and (some? v) (not (false? v)))
                                          (str (case k
                                                 :path "Path=" :domain "Domain="
                                                 :same-site "SameSite=" :max-age "Max-Age=")
                                               v))
    (:secure :http-only) (when v (if (= k :secure) "Secure" "HttpOnly"))
    nil))

(defn set-cookie-header
  "Build the Set-Cookie header string from :cookie-attrs — the one place
  the attributes are serialized. Keys: :path :domain :same-site :max-age
  :secure :http-only. Booleans emit bare flags; nil/absent are omitted.
  Values are emitted as-is: this is a build/host seam, not a sanitizer —
  callers pass a value already negotiated through `negotiate` (which only
  returns a :supported locale)."
  [{:keys [name value cookie-attrs]}]
  (let [attrs (->> (seq (or cookie-attrs {}))
                   (keep attr-str)
                   (remove nil?))]
    (str name "=" value (when (seq attrs)
                          (str "; " (str/join "; " attrs))))))

(declare re-quote)

(defn substitute
  "Template substitution for build-time-generated documents — the SSR
  seam. Pairs with shitsuke's i18n table (the app-kotoba.cloud
  sign_in_i18n pattern: source string → localized string resolved at
  request time).

    (substitute html {:locale \"ja\" :strings {\"Sign in\" \"サインイン\"}})

  Replaces {{LOCALE}} tokens with the locale tag, then each :strings
  source with its localization. Unknown strings pass through unchanged
  (a missing translation must be visible as source text, never blank) —
  fail-open for content, exactly because locale SELECTION above is
  fail-closed. Pure; the same substitution runs in SSR (bb/nbb) and in
  a browser host.

  With `:exact? true` a source string is replaced only where it is the WHOLE
  of a text node (`>src<`), an attribute value (`=\"src\"`), or a script
  string literal (`'src'` / `\"src\"`), longest source first. Plain mode
  replaces every occurrence and so turns 送信 inside 送信中 into Send中 —
  measured 2026-09-16 on cloud-itonami-app, whose document has 550 text
  nodes and 1,297 script literals that are exactly such phrases. Exact mode
  is what a table extracted from a rendered document wants; plain mode
  stays for {{TOKEN}}-shaped templates."
  [html {:keys [locale strings exact?]}]
  (let [html (if (nil? html) "" (str html))
        html (if locale
               (str/replace html #"\{\{LOCALE\}\}" (str locale))
               html)]
    (cond
      (not strings) html
      exact?
      (reduce (fn [h [src localized]]
                (let [src (str src) localized (str localized)
                      quoted (re-quote src)]
                  ;; fn replacements throughout: a localized text may contain
                  ;; `$1` or `$&`, which string replacements read as groups
                  (-> h
                      (str/replace (re-pattern (str ">(\\s*)" quoted "(\\s*)<"))
                                   (fn [[_ a b]] (str ">" a localized b "<")))
                      (str/replace (re-pattern (str "=\"" quoted "\""))
                                   (fn [_] (str "=\"" localized "\"")))
                      (str/replace (re-pattern (str "'" quoted "'"))
                                   (fn [_] (str "'" (str/replace localized "'" "\\'") "'")))
                      (str/replace (re-pattern (str "\"" quoted "\""))
                                   (fn [_] (str "\"" (str/replace localized "\"" "\\\"") "\""))))))
              html
              (sort-by (fn [[src _]] (- (count (str src)))) strings))
      :else
      (reduce (fn [h [src localized]]
                (str/replace h (str src) (str localized)))
              html strings))))

(defn- re-quote
  "Regex-quote a literal (dual-render: no Pattern/quote on cljs)."
  [s]
  ;; a fn replacement, not "\\$0": $0 is a group on the JVM and plain text in
  ;; JS, so the string form would work on one engine and not the other
  (str/replace (str s) #"[.*+?^${}()|\[\]\\/]" (fn [m] (str "\\" m))))

(defn document-variants
  "The content-addressing side of locale support: ONE route → N locale
  documents, each its own CID.

    (document-variants {:base-name \"top\"
                        :locales [:en :ja]
                        :html-fn (fn [locale] ...)
                        :cid-fn (fn [entry-name html] ...)})

  → [{:locale :en :cid \"...\" :entry-name \"top\" :manifest {...}} ...]

  Each variant runs through the SAME self-contained rules as
  publish/manifest — a locale variant that references external assets is
  refused exactly like any other document (:self-contained false, with
  the refusing manifest on the variant). The CID is injected (:cid-fn);
  shinkansen does not hash — publish-document.cljk and state.cljc own
  that.

  THE RULE (owner decision): identity stays per-locale-CID; the
  name/route is locale-independent; negotiation happens at the edge/host
  with the cookie. There is NO path fork like /ja/ — a locale variant is
  never a new entry route, only a new content address the host serves
  after negotiation."
  [{:keys [base-name locales html-fn cid-fn]}]
  (when (and (string? base-name) (seq locales) (fn? html-fn) (fn? cid-fn))
    (mapv (fn [locale]
            (let [entry-name (str base-name)
                  html (html-fn locale)
                  cid (cid-fn entry-name html)
                  m (publish/manifest {:file (str entry-name "." (str locale) ".html")
                                       :html html
                                       :entry-name entry-name})]
              {:locale locale
               :cid cid
               :entry-name entry-name
               :self-contained (boolean (:ok m))
               :manifest (assoc m :cid cid)}))
          locales)))

;; ---------- the browser side (2026-09-16, owner: 「言語切り替え … も統合」) ----------

(def actions
  "The event a language switch names: locale/set {locale}. The runtime
   answers it by writing the negotiation cookie (`defaults`) and following
   the control's href or reloading — the document is re-negotiated by the
   host/edge, never re-rendered client-side. Merged into every declaration
   by shinkansen.interaction/framework-actions."
  {:events {:locale/set {:validate (fn [[_ {:keys [locale]}]]
                                     (boolean (some-> locale str str/trim not-empty)))}}})

(defn- cookie-attrs-js
  "The `defaults` :cookie-attrs as the JS the runtime appends to the cookie
   string — the one serialization, derived, not retyped. Secure is emitted
   only on https (a Secure cookie on http://localhost is silently dropped,
   which reads as 'the switch does nothing')."
  []
  (let [{:keys [path same-site max-age secure]} (:cookie-attrs defaults)]
    (str "'; Path=" path "; SameSite=" same-site "; Max-Age=" max-age "'"
         (when secure "+(location.protocol==='https:'?'; Secure':'')"))))

(def placeholder-re
  "A `{key}` placeholder in a message pattern: letters, digits, `_`, `-`."
  #"\{([A-Za-z0-9_-]+)\}")

(defn format-message
  "Message formatting for strings a script builds at runtime — the
  browser-side half of `substitute`. A template literal
  (`` `${bot.name} に頼む` ``) cannot be substituted at render time
  because the source text never exists as one literal; written as
  `shinkansen.locale.format('{name} に頼む', {name})` the pattern IS an ordinary quoted
  script literal, so the render-time table translates it (`:exact?`)
  and this fills the holes afterwards. `{key}` → the param; a key the
  params do not carry stays as written (a hole you can see, never a
  silent blank); nil → the empty string; numbers are printed as numbers
  (the browser runtime formats them with Intl.NumberFormat for <html lang>).

    (format-message \"{phase} {seconds}秒\" {:phase \"応答中\" :seconds 12})
    → \"応答中 12秒\""
  [pattern params]
  (let [params (or params {})
        lookup (fn [k] (cond (contains? params (keyword k)) (get params (keyword k))
                             (contains? params k) (get params k)
                             :else ::missing))]
    (str/replace (str pattern) placeholder-re
                 (fn [[whole k]]
                   (let [v (lookup k)]
                     (cond (= v ::missing) whole
                           (nil? v) ""
                           :else (str v)))))))

(def runtime-fragment
  "The `shinkansen.locale` object the interaction runtime installs:
     get()                 → the cookie's locale or null
     set(locale, {href})   → writes the cookie, then navigates to href
                             (the control's own) or reloads; returns locale
     lang()                → <html lang> (the negotiated document locale)
     format(pattern, params) → `format-message` above, numbers through
                             Intl.NumberFormat(lang())"
  (str
   "var LK=" (pr-str (:cookie-name defaults)) ";"
   "function lget(){var m=document.cookie.match(new RegExp('(?:^|; )'+LK+'=([^;]*)'));return m?decodeURIComponent(m[1]):null;}"
   "function lset(locale,opts){locale=String(locale||'').trim();if(!locale)return null;"
   "document.cookie=LK+'='+encodeURIComponent(locale)+" (cookie-attrs-js) ";"
   "var href=opts&&opts.href;if(opts&&opts.navigate===false)return locale;"
   "if(href&&href!=='#')location.assign(href);else location.reload();return locale;}"
   "function llang(){return document.documentElement.getAttribute('lang')||'';}"
   "function lfmt(pattern,params){params=params||{};return String(pattern).replace(/\\{([A-Za-z0-9_-]+)\\}/g,function(whole,k){"
   "if(!Object.prototype.hasOwnProperty.call(params,k))return whole;var v=params[k];if(v==null)return '';"
   "if(typeof v==='number'&&isFinite(v)){try{return new Intl.NumberFormat(llang()||undefined).format(v);}catch(e){return String(v);}}return String(v);});}"
   "var locale={get:lget,set:lset,lang:llang,format:lfmt};"))
