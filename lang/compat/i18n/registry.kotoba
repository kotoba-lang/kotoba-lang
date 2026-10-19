(ns i18n.registry
  "Language metadata: display name, native name, text direction, tier.

  This is a curated *default* table (tier 1 = 25 major languages + a tier-2
  sample), not an attempt at the full 200+ language catalog that a service
  like etzhayyim's `i18n.etzhayyim.com` (`GetLanguageRegistry`) exposes. Apps
  that need the full list should fetch it from that service at runtime and
  call `load!` to extend/replace this table — see `i18n.tm-import/registry->i18n`
  for the shape conversion. Keeping a real default here means the library
  still works fully offline/zero-dep out of the box.

  Tier meaning (mirrors the etzhayyim registry's `tierLimit`): tier 1 = top
  25 languages by combined speaker count / web UI coverage; tier 2+ =
  everything else, roughly ordered by same."
  (:require [clojure.string :as str]))

(def ^:private tier-1
  ;; [code english-name native-name dir]
  [[:en "English"    "English"    :ltr]
   [:zh "Chinese"    "中文"        :ltr]
   [:hi "Hindi"      "हिन्दी"      :ltr]
   [:es "Spanish"    "Español"    :ltr]
   [:fr "French"     "Français"   :ltr]
   [:ar "Arabic"     "العربية"     :rtl]
   [:bn "Bengali"    "বাংলা"       :ltr]
   [:pt "Portuguese" "Português"  :ltr]
   [:ru "Russian"    "Русский"    :ltr]
   [:ja "Japanese"   "日本語"      :ltr]
   [:de "German"     "Deutsch"    :ltr]
   [:ko "Korean"     "한국어"      :ltr]
   [:vi "Vietnamese" "Tiếng Việt" :ltr]
   [:tr "Turkish"    "Türkçe"     :ltr]
   [:it "Italian"    "Italiano"   :ltr]
   [:th "Thai"       "ไทย"        :ltr]
   [:fa "Persian"    "فارسی"       :rtl]
   [:pl "Polish"     "Polski"     :ltr]
   [:nl "Dutch"      "Nederlands" :ltr]
   [:uk "Ukrainian"  "Українська" :ltr]
   [:he "Hebrew"     "עברית"       :rtl]
   [:id "Indonesian" "Bahasa Indonesia" :ltr]
   [:sv "Swedish"    "Svenska"    :ltr]
   [:el "Greek"      "Ελληνικά"    :ltr]
   [:ro "Romanian"   "Română"     :ltr]])

(def ^:private tier-2
  [[:ur "Urdu"       "اردو"        :rtl]
   [:yi "Yiddish"    "ייִדיש"       :rtl]
   [:ps "Pashto"     "پښتو"        :rtl]
   [:sd "Sindhi"     "سنڌي"        :rtl]
   [:hu "Hungarian"  "Magyar"     :ltr]
   [:fi "Finnish"    "Suomi"      :ltr]
   [:da "Danish"     "Dansk"      :ltr]
   [:sk "Slovak"     "Slovenčina" :ltr]
   [:cs "Czech"      "Čeština"    :ltr]
   [:bg "Bulgarian"  "Български"  :ltr]
   [:hr "Croatian"   "Hrvatski"   :ltr]
   [:sr "Serbian"    "Српски"     :ltr]
   [:ms "Malay"      "Bahasa Melayu" :ltr]])

(defn- rows->entries [rows tier]
  (into {}
        (map (fn [[code en native dir]]
               [code {:i18n.lang/code code
                      :i18n.lang/name en
                      :i18n.lang/native-name native
                      :i18n.lang/dir dir
                      :i18n.lang/tier tier}]))
        rows))

(defonce ^{:doc "code (keyword) -> {:i18n.lang/code :i18n.lang/name
  :i18n.lang/native-name :i18n.lang/dir :i18n.lang/tier}"}
  table
  (atom (merge (rows->entries tier-1 1)
               (rows->entries tier-2 2))))

(defn load!
  "Merge additional/overriding entries into the table. `entries` is a seq of
  the same map shape as `table`'s values (see docstring), or a map keyed by
  code. Used to extend the default table from a live language-registry
  service at app startup."
  [entries]
  (let [by-code (if (map? entries)
                  entries
                  (into {} (map (juxt :i18n.lang/code identity)) entries))]
    (swap! table merge by-code))
  nil)

(defn info
  "Metadata map for `code`, or nil if unknown."
  [code]
  (get @table code))

(defn dir
  "`:ltr` or `:rtl` for `code`. Unknown codes default to `:ltr` (the common
  case, and the safe default for an unrecognized/new locale)."
  [code]
  (get-in @table [code :i18n.lang/dir] :ltr))

(defn rtl?
  [code]
  (= :rtl (dir code)))

(defn tier
  "Tier number for `code`, or nil if unknown."
  [code]
  (get-in @table [code :i18n.lang/tier]))

(defn all
  "All registered language entries, sorted by tier then code."
  []
  (->> (vals @table)
       (sort-by (juxt :i18n.lang/tier (comp name :i18n.lang/code)))))

(defn search
  "Case-insensitive substring match against code/name/native-name — mirrors
  etzhayyim.i18n's `GetLanguageRegistry {search: ...}` query shape."
  [q]
  (let [needle (str/lower-case q)]
    (filter (fn [{:i18n.lang/keys [code] disp-name :i18n.lang/name native-name :i18n.lang/native-name}]
              (some #(str/includes? (str/lower-case (str %)) needle)
                    [(name code) disp-name native-name]))
            (all))))

