(ns kotoba.site.locales
  (:require [kotoba.lang.text :as str]
            [clojure.walk :as walk]))

;; ADR-2609091700, manifest/public-site-locales.edn in the superproject.
(def required
  [{:tag "en" :name "English" :label "English"}
   {:tag "zh-Hans" :name "Simplified Chinese" :label "简体中文"}
   {:tag "hi" :name "Hindi" :label "हिन्दी"}
   {:tag "es" :name "Spanish" :label "Español"}
   {:tag "ar" :name "Modern Standard Arabic" :label "العربية الفصحى" :dir "rtl"}
   {:tag "fr" :name "French" :label "Français"}
   {:tag "bn" :name "Bengali" :label "বাংলা"}
   {:tag "pt" :name "Portuguese (region-neutral)" :label "Português"}
   {:tag "id" :name "Indonesian" :label "Bahasa Indonesia"}
   {:tag "ur" :name "Urdu" :label "اردو" :dir "rtl"}
   {:tag "ru" :name "Russian" :label "Русский"}
   {:tag "de" :name "German" :label "Deutsch"}
   {:tag "ja" :name "Japanese" :label "日本語"}
   {:tag "ko" :name "Korean" :label "한국어"}
   {:tag "pcm" :name "Nigerian Pidgin (not English)" :label "Naijá"}
   {:tag "arz" :name "Egyptian Arabic (not Modern Standard Arabic)" :label "العربية المصرية" :dir "rtl"}
   {:tag "mr" :name "Marathi" :label "मराठी"}])

(def protected-tags #{:script :style :pre :code :kbd :samp :hiccup/raw})
(def text-attributes #{:title :alt :aria-label :placeholder :data-count-template :data-verifying :data-success :data-error})

(defn mark-language [node tag]
  (let [label (or (:label (first (filter #(= tag (:tag %)) required))) tag)]
    (walk/postwalk
     (fn [x]
       (cond
         (and (vector? x) (map? (second x)) (:data-language-selector-current (second x)))
         [(first x) (second x) label]
         (and (map? x) (:data-language-selector-opener x))
         (assoc x :aria-label (str "Language: " label))
         (and (map? x) (:hreflang x))
         (cond-> (dissoc x :aria-current :data-current)
           (= tag (:hreflang x)) (assoc :aria-current "page" :data-current true))
         :else x)) node)))

(defn translatable? [s]
  (and (string? s) (re-find #"[A-Za-z]" s)
       (not (contains? #{"Kotoba" "Kotobase" "Kotoba Cloud" "Murakumo" "Itonami"
                         "GitHub" "Kotoba Labs Inc." "Ryo Awai" "WebAssembly"
                         "CID" "SHA-256" "JSON" "EDN" "DID" "GPU" "CPU"} (str/trim s)))
       (not (re-matches #"[0-9,.]+\s*(?:ms|ns|s|B|KiB|MiB|GiB|%)" (str/trim s)))
       (not (re-matches #"\s*(?:https?://\S+|[^\s]+@[^\s]+|[\w./:-]+\.(?:edn|json|cljs|cljc|clj|md|kotoba|wasm))\s*" s))))

(defn map-text
  "Map text nodes and accessible labels, never markup/code/attributes carrying
  data. A translate=no island preserves native language names and identifiers."
  [node f]
  (cond
    (string? node) (if (translatable? node) (f node) node)
    (vector? node)
    (let [[tag & more] node
          attrs (when (map? (first more)) (first more))
          children (if attrs (rest more) more)]
      (if (or (protected-tags tag) (= "no" (:translate attrs)))
        node
        (into (cond-> [tag]
                attrs (conj (reduce-kv (fn [m k v]
                                        (assoc m k (if (and (text-attributes k) (translatable? v)) (f v) v)))
                                      {} attrs)))
              (map #(map-text % f) children))))
    (seq? node) (doall (map #(map-text % f) node))
    :else node))

(defn rebase-links
  "Resolve original relative assets against their English page. Translate only
  links to pages actually published in the same locale, not external docs."
  [node resolve-link]
  (cond
    (vector? node)
    (let [[tag & more] node attrs (when (map? (first more)) (first more))
          children (if attrs (rest more) more)]
      (if (or (protected-tags tag) (= "no" (:translate attrs))) node
          (into (cond-> [tag]
                  attrs (conj (reduce-kv (fn [m k v]
                                          (assoc m k (if (and (#{:href :src} k) (string? v))
                                                       (resolve-link v) v))) {} attrs)))
                (map #(rebase-links % resolve-link) children))))
    (seq? node) (doall (map #(rebase-links % resolve-link) node))
    :else node))
